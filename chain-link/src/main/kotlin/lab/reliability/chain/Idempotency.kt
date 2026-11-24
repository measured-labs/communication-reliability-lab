package lab.reliability.chain

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.sql.Types
import java.util.concurrent.atomic.AtomicLong

// task 04: 멱등 키 — 직접 짠 최소 코드(라이브러리 없음). 규칙의 정본: docs/idempotency.md.

/**
 * `chain.idempotency.*` — 기본은 꺼짐(chain-link.yml). 꺼져 있으면 키 헤더를 읽지 않고 표를 쓰지 않는다.
 * [inProgressAsUnknown](기본 false): 아래 층의 409(같은 키 처리 중)를 `Failed:HTTP_409`가 아니라 `Unknown:HTTP_409`(결과 모름)로 읽는다
 * — 재시도 감싸개는 `unknown` 갈래로 다시 시도하고, 그 결과로 끝나면 이 층의 키는 `UNKNOWN`(다음에 이어 한다). docs/idempotency.md 3-1절.
 * 키가 꺼져 있으면 켤 수 없다(아래 층이 409를 내지 않는다 — 설정이 뜻 없이 기록되지 않게 기동을 멈춘다).
 */
@ConfigurationProperties("chain.idempotency")
data class IdempotencySettings(val enabled: Boolean, val inProgressAsUnknown: Boolean = false) {
    init {
        require(enabled || !inProgressAsUnknown) { "chain.idempotency.in-progress-as-unknown 은 멱등 키가 켜져 있을 때만 켤 수 있다" }
    }
}

/** 키 행의 상태(`idempotency_keys.state`). */
enum class KeyState {
    /** 업무 행을 넣었고 아래 층을 부르는 중(주문·재고의 첫 커밋 뒤). */
    IN_PROGRESS,

    /** 이 층이 처리했다고 답했다(2xx) — 응답을 저장했다. */
    COMPLETED,

    /**
     * 아래 층 호출이 `Failed`로 끝났다(502) — 응답을 저장했다. 대개 아래 층이 처리하지 않은 것이지만, `Failed:HTTP_409`(아래 층이
     * 같은 키를 처리 중 — [IdempotencySettings.inProgressAsUnknown]이 꺼져 있을 때)는 아래 층이 끝내 처리할 수 있다(docs/idempotency.md 3절).
     */
    FAILED,

    /** 이 층도 아래 층의 결과를 몰랐다(504) — 같은 키가 다시 오면 같은 업무 행으로 아래 층을 다시 부른다. */
    UNKNOWN,
    ;

    companion object {
        /** 아래 층 호출의 결과로 정하는 이 층의 키 상태. */
        fun of(outcome: LayerOutcome): KeyState = when (outcome) {
            LayerOutcome.Completed -> COMPLETED
            is LayerOutcome.Failed -> FAILED
            is LayerOutcome.Unknown -> UNKNOWN
        }
    }
}

/** `idempotency_keys`의 행 하나. [rowId] = 같은 트랜잭션에 넣은 업무 행의 id. 응답 칸은 COMPLETED·FAILED·UNKNOWN에서 있다. */
data class KeyRow(
    val key: String,
    val requestId: String,
    val rowId: Long,
    val amount: Long,
    val state: KeyState,
    val responseStatus: Int?,
    val responseBody: String?,
)

/** 다시 온 키(처음 넣지 못한 키)의 갈래. */
sealed interface Repeat {
    /** 저장된 금액과 다르다 → 422, 처리하지 않는다. */
    data object Mismatch : Repeat

    /** 처음 요청이 아직 처리 중 → 409, 기다리지 않는다. */
    data object InProgress : Repeat

    /** 저장한 응답을 그대로 돌려준다(처리하지 않는다). */
    data class Replay(val status: Int, val body: String) : Repeat

    /** 결과를 몰랐던 키 — 이 요청이 그 키를 다시 잡았다. 같은 업무 행([rowId])으로 아래 층을 같은 키로 다시 부른다. */
    data class Resume(val rowId: Long) : Repeat
}

/** 순수한 규칙(docs/idempotency.md 3절): 차례대로 금액 → 상태. */
object IdempotencyRules {
    fun onRepeat(row: KeyRow, amount: Long): Repeat = when {
        row.amount != amount -> Repeat.Mismatch
        row.state == KeyState.COMPLETED || row.state == KeyState.FAILED -> Repeat.Replay(
            checkNotNull(row.responseStatus) { "키 '${row.key}'가 ${row.state}인데 저장한 응답 상태가 없다" },
            checkNotNull(row.responseBody) { "키 '${row.key}'가 ${row.state}인데 저장한 응답 본문이 없다" },
        )
        row.state == KeyState.UNKNOWN -> Repeat.Resume(row.rowId)
        else -> Repeat.InProgress
    }
}

/**
 * 이 서비스 DB의 `idempotency_keys`(각 서비스의 schema.sql). 업무 행과 같은 트랜잭션에 들도록 같은 [JdbcClient]를 쓴다 —
 * [insert]·[finish]는 서비스 저장소의 `@Transactional` 메서드 안에서 부른다.
 */
class IdempotencyKeys(private val jdbc: JdbcClient) {
    /** 넣었으면 true, 같은 키가 이미 있으면 false(`on conflict do nothing` — 앞의 트랜잭션이 아직 안 끝났으면 끝날 때까지 기다린다). */
    fun insert(
        key: String,
        requestId: String,
        rowId: Long,
        amount: Long,
        state: KeyState,
        responseStatus: Int? = null,
        responseBody: String? = null,
    ): Boolean =
        jdbc.sql(
            "insert into idempotency_keys (key, request_id, row_id, amount, state, response_status, response_body) " +
                "values (:key, :requestId, :rowId, :amount, :state, :status, :body) on conflict (key) do nothing",
        )
            .param("key", key)
            .param("requestId", requestId)
            .param("rowId", rowId)
            .param("amount", amount)
            .param("state", state.name)
            .param("status", responseStatus, Types.INTEGER)
            .param("body", responseBody, Types.VARCHAR)
            .update() == 1

    fun find(key: String): KeyRow? =
        jdbc.sql("select key, request_id, row_id, amount, state, response_status, response_body from idempotency_keys where key = :key")
            .param("key", key)
            .query { rs, _ ->
                KeyRow(
                    rs.getString("key"), rs.getString("request_id"), rs.getLong("row_id"), rs.getLong("amount"),
                    KeyState.valueOf(rs.getString("state")),
                    rs.getInt("response_status").takeUnless { rs.wasNull() }, rs.getString("response_body"),
                )
            }
            .optional()
            .orElse(null)

    /** UNKNOWN인 키를 IN_PROGRESS로 잡는다. 잡았으면 true(다른 요청이 먼저 잡았거나 상태가 바뀌었으면 false). */
    fun claim(key: String): Boolean =
        jdbc.sql("update idempotency_keys set state = 'IN_PROGRESS', updated_at = now() where key = :key and state = 'UNKNOWN'")
            .param("key", key)
            .update() == 1

    /** 결과를 쓴다 — 업무 행의 결과와 같은 트랜잭션에서 부른다. */
    fun finish(key: String, state: KeyState, responseStatus: Int, responseBody: String) {
        val updated = jdbc.sql(
            "update idempotency_keys set state = :state, response_status = :status, response_body = :body, updated_at = now() where key = :key",
        )
            .param("state", state.name)
            .param("status", responseStatus)
            .param("body", responseBody)
            .param("key", key)
            .update()
        check(updated == 1) { "키 '$key'가 없다" }
    }
}

/** 서비스가 센 수(`GET /idempotency-counts`). 종류: [KINDS]. */
class IdempotencyCounters {
    private val counts = KINDS.associateWith { AtomicLong() }

    fun add(kind: String) {
        counts.getValue(kind).incrementAndGet()
    }

    fun counts(): Map<String, Long> = KINDS.associateWith { counts.getValue(it).get() }

    fun reset() = counts.values.forEach { it.set(0) }

    companion object {
        const val NEW = "new"
        const val REPLAYED = "replayed"
        const val IN_PROGRESS = "in-progress"
        const val MISMATCH = "mismatch"
        const val RESUMED = "resumed"
        const val MISSING_KEY = "missing-key"
        val KINDS = listOf(NEW, REPLAYED, IN_PROGRESS, MISMATCH, RESUMED, MISSING_KEY)
    }
}

/** 세 서비스의 처리 코드가 쓰는 입구. [enabled]가 false면 처리 코드는 task 01~03의 경로를 그대로 탄다. */
class Idempotency(
    val enabled: Boolean,
    private val keys: IdempotencyKeys,
    val counters: IdempotencyCounters,
    /** [IdempotencySettings.inProgressAsUnknown] — `/settings`에 내려고 둔다(읽는 곳은 [HttpNextLayerCall]). */
    val inProgressAsUnknown: Boolean = false,
) {
    private val mapper = JsonMapper.builder().build()

    /** 저장하고 돌려줄 응답 본문(JSON 문자열) — 처음 응답과 되돌려 주는 응답이 같은 바이트가 되게 처리 코드가 이것으로 만든다. */
    fun json(body: Map<String, Any?>): String = mapper.writeValueAsString(body)

    /**
     * 처음 넣지 못한 키의 갈래를 정하고 센다. UNKNOWN이면 [IdempotencyKeys.claim]으로 이 요청이 잡는다 —
     * 잡지 못하면(다른 요청이 먼저 잡음) 다시 읽어 정한다(그때는 IN_PROGRESS라 409).
     */
    fun repeated(key: String, amount: Long): Repeat {
        repeat(2) {
            val row = keys.find(key) ?: error("키 '$key'를 넣지 못했는데 읽을 수 없다")
            val r = IdempotencyRules.onRepeat(row, amount)
            if (r is Repeat.Resume && !keys.claim(key)) return@repeat // 다시 읽는다
            counters.add(
                when (r) {
                    Repeat.Mismatch -> IdempotencyCounters.MISMATCH
                    Repeat.InProgress -> IdempotencyCounters.IN_PROGRESS
                    is Repeat.Replay -> IdempotencyCounters.REPLAYED
                    is Repeat.Resume -> IdempotencyCounters.RESUMED
                },
            )
            return r
        }
        counters.add(IdempotencyCounters.IN_PROGRESS)
        return Repeat.InProgress
    }

    /** 처리하지 않고 끝내는 갈래의 응답(Resume은 처리 코드가 이어서 처리하므로 여기 오지 않는다). */
    fun response(r: Repeat): ResponseEntity<*> = when (r) {
        Repeat.Mismatch -> ResponseEntity.status(422).body(mapOf("error" to "${ChainHeaders.IDEMPOTENCY_KEY} reused with a different amount"))
        Repeat.InProgress -> ResponseEntity.status(409).body(mapOf("error" to "a request with the same ${ChainHeaders.IDEMPOTENCY_KEY} is in progress"))
        is Repeat.Replay -> ResponseEntity.status(r.status).header(ChainHeaders.REPLAYED, "true").contentType(MediaType.APPLICATION_JSON).body(r.body)
        is Repeat.Resume -> error("이어 하기는 처리 코드가 한다")
    }

    /** 처음 처리한 요청의 응답 — 저장한 본문 그대로. */
    fun firstResponse(status: Int, body: String): ResponseEntity<*> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body)

    /** `/settings`의 `idempotency`. */
    fun settings(): Map<String, Any?> = if (!enabled) {
        mapOf("enabled" to false)
    } else {
        linkedMapOf(
            "enabled" to true,
            "header" to ChainHeaders.IDEMPOTENCY_KEY,
            "inProgress" to "reject-409",
            "mismatch" to "reject-422",
            "replayHeader" to ChainHeaders.REPLAYED,
            "inProgressAsUnknown" to inProgressAsUnknown,
        )
    }
}

@RestController
class IdempotencyCountController(private val idempotency: Idempotency) {
    /** 마지막 DELETE 뒤 센 수. 예: {"new":3,"replayed":1,"in-progress":0,"mismatch":0,"resumed":0,"missing-key":0} */
    @GetMapping("/idempotency-counts")
    fun counts() = idempotency.counters.counts()

    @DeleteMapping("/idempotency-counts")
    fun reset(): Map<String, Long> {
        idempotency.counters.reset()
        return idempotency.counters.counts()
    }
}
