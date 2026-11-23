package lab.reliability.analyzer

/** 응답으로 가른 결과. 판정 규칙: docs/measurement.md "3. 판정". */
enum class Verdict(val label: String) {
    COMPLETED("completed"),
    FAILED("failed"),
    UNKNOWN("unknown"),
    ;

    companion object {
        /** 2xx = 완료, 0(응답 없음 — k6 타임아웃·연결 오류) 또는 504 = 결과 모름, 그 밖 = 실패. */
        fun of(status: Int): Verdict = when {
            status in 200..299 -> COMPLETED
            status == 0 || status == 504 -> UNKNOWN
            else -> FAILED
        }
    }
}

const val MIXED_LABEL = "(mixed)"
const val NO_ROW = "-"

/** 같은 requestId의 정답들과 세 층의 행. */
class RequestGroup(
    val requestId: String,
    val answers: List<Answer>,
    val statuses: Map<Layer, List<String>>,
) {
    /** 이 requestId로 보낸 금액들(같은 id를 여러 번 보내면 여럿일 수 있다) */
    val sentAmounts: Set<Long> = answers.map { it.amount }.toSet()

    val label: String = answers.map { it.fault }.distinct().singleOrNull() ?: MIXED_LABEL
    val verdicts: List<Verdict> = answers.map { Verdict.of(it.status) }
    val hasCompleted = Verdict.COMPLETED in verdicts
    val allFailed = verdicts.all { it == Verdict.FAILED }
    val unknownOnly = !hasCompleted && Verdict.UNKNOWN in verdicts

    fun processed(layer: Layer): Int = statuses.getValue(layer).count { it == layer.processedStatus }

    /** 예: "completed | COMPLETED | RESERVED | APPROVED". 여러 값은 정렬해 '+'로 잇는다. */
    val signature: String = (
        listOf(verdicts.map { it.label }.sorted().joinToString("+")) +
            Layer.entries.map { layer -> statuses.getValue(layer).sorted().joinToString("+").ifEmpty { NO_ROW } }
        ).joinToString(" | ")
}

data class LayerTally(
    val rows: Int,
    val processedRows: Int,
    val pendingRows: Int,
    val orphanRows: Int,
    val lost: Int,
    val doubled: Int,
    val failedButProcessed: Int,
    val unknownProcessed: Int,
    val unknownNotProcessed: Int,
    /** 처리 행 가운데 금액이 그 requestId로 보낸 금액이 아닌 행 수. 금액 칸이 없는 층(inventory)은 null */
    val amountMismatch: Int?,
)

data class FaultCountCheck(val service: String, val kind: String, val injected: Long, val counted: Long) {
    val match get() = injected == counted
}

data class Quantiles(val count: Int, val p50: Double?, val p95: Double?, val p99: Double?, val max: Double?) {
    companion object {
        /**
         * nearest-rank: 오름차순 정렬 뒤 ceil(p·n)번째 값. 0건이면 null.
         * 순위는 정수 산술로 구한다 — ceil(pct·n/100) = (pct·n + 99) / 100. 부동소수 곱(0.95·n)의 반올림 오차로 한 칸 밀리지 않게.
         */
        fun of(values: List<Double>): Quantiles {
            if (values.isEmpty()) return Quantiles(0, null, null, null, null)
            val sorted = values.sorted()
            val n = sorted.size.toLong()
            fun rank(pct: Long) = sorted[((pct * n + 99) / 100 - 1).toInt()]
            return Quantiles(sorted.size, rank(50), rank(95), rank(99), sorted.last())
        }
    }
}

data class LabelSummary(
    val answers: Int,
    val requestIds: Int,
    val verdicts: Map<String, Int>,
    val signatures: Map<String, Int>,
    val elapsedMs: Quantiles,
)

data class Reconciliation(
    val answers: Int,
    val requestIds: Int,
    val verdicts: Map<String, Int>,
    val lostTotal: Int,
    val doubledTotal: Int,
    /** 세 층의 처리 행 수가 서로 다른 requestId 수 */
    val layerMismatch: Int,
    /** 금액이 보낸 금액과 다른 처리 행 수(금액 칸이 있는 층의 합) */
    val amountMismatch: Int,
    val layers: Map<String, LayerTally>,
    val faultCounts: List<FaultCountCheck>,
    val labels: Map<String, LabelSummary>,
    val elapsedMs: Map<String, Quantiles>,
) {
    val faultCountsMatch get() = faultCounts.all { it.match }
    val orphanRows get() = layers.values.sumOf { it.orphanRows }
    val pendingRows get() = layers.values.sumOf { it.pendingRows }
}

fun reconcile(inputs: RunInputs): Reconciliation {
    val byId = inputs.answers.groupBy { it.requestId }
    val rowsById = inputs.rows.mapValues { (_, rows) -> rows.groupBy({ it.requestId }, { it.status }) }
    val groupById = HashMap<String, RequestGroup>()
    val groups = byId.map { (id, answers) ->
        RequestGroup(id, answers, Layer.entries.associateWith { rowsById.getValue(it)[id].orEmpty() }).also { groupById[id] = it }
    }

    val layers = Layer.entries.associate { layer ->
        val rows = inputs.rows.getValue(layer)
        layer.label to LayerTally(
            rows = rows.size,
            processedRows = rows.count { it.status == layer.processedStatus },
            pendingRows = rows.count { it.status == "PENDING" },
            orphanRows = rows.count { it.requestId !in byId },
            lost = groups.count { it.hasCompleted && it.processed(layer) == 0 },
            doubled = groups.count { it.processed(layer) >= 2 },
            failedButProcessed = groups.count { it.allFailed && it.processed(layer) >= 1 },
            unknownProcessed = groups.count { it.unknownOnly && it.processed(layer) >= 1 },
            unknownNotProcessed = groups.count { it.unknownOnly && it.processed(layer) == 0 },
            // 정답에 없는 행은 보낸 금액이 없으므로 여기에 들지 않는다(orphanRows로 센다).
            amountMismatch = if (!layer.hasAmount) {
                null
            } else {
                rows.count { row ->
                    val group = groupById[row.requestId]
                    row.status == layer.processedStatus && group != null && row.amount !in group.sentAmounts
                }
            },
        )
    }

    val injected = mutableMapOf<Pair<String, String>, Long>()
    inputs.answers.forEach { answer ->
        parseFault(answer.fault, answer.requestId).forEach { injected.merge(it, 1L, Long::plus) }
    }
    val faultCounts = Layer.entries.flatMap { layer ->
        FAULT_KINDS.map { kind ->
            FaultCountCheck(layer.label, kind, injected[layer.label to kind] ?: 0, inputs.faultCounts.getValue(layer.label).getValue(kind))
        }
    }

    val labels = groups.groupBy { it.label }.toSortedMap().mapValues { (_, gs) ->
        val answers = gs.flatMap { it.answers }
        LabelSummary(
            answers = answers.size,
            requestIds = gs.size,
            verdicts = countVerdicts(answers),
            signatures = gs.groupingBy { it.signature }.eachCount().toSortedMap(),
            elapsedMs = Quantiles.of(answers.map { it.elapsedMs }),
        )
    }

    return Reconciliation(
        answers = inputs.answers.size,
        requestIds = groups.size,
        verdicts = countVerdicts(inputs.answers),
        lostTotal = groups.count { g -> g.hasCompleted && Layer.entries.any { g.processed(it) == 0 } },
        doubledTotal = groups.count { g -> Layer.entries.any { g.processed(it) >= 2 } },
        layerMismatch = groups.count { g -> Layer.entries.map { g.processed(it) }.distinct().size > 1 },
        amountMismatch = layers.values.sumOf { it.amountMismatch ?: 0 },
        layers = layers,
        faultCounts = faultCounts,
        labels = labels,
        elapsedMs = mapOf("all" to Quantiles.of(inputs.answers.map { it.elapsedMs })) +
            Verdict.entries.associate { v ->
                v.label to Quantiles.of(inputs.answers.filter { Verdict.of(it.status) == v }.map { it.elapsedMs })
            },
    )
}

private fun countVerdicts(answers: List<Answer>): Map<String, Int> =
    Verdict.entries.associate { v -> v.label to answers.count { Verdict.of(it.status) == v } }
