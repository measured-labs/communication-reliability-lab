package lab.reliability.analyzer

import kotlin.math.ceil

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
)

data class FaultCountCheck(val service: String, val kind: String, val injected: Long, val counted: Long) {
    val match get() = injected == counted
}

data class Quantiles(val count: Int, val p50: Double?, val p95: Double?, val p99: Double?, val max: Double?) {
    companion object {
        /** nearest-rank: 오름차순 정렬 뒤 ceil(p·n)번째 값. 0건이면 null. */
        fun of(values: List<Double>): Quantiles {
            if (values.isEmpty()) return Quantiles(0, null, null, null, null)
            val sorted = values.sorted()
            fun rank(p: Double) = sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)]
            return Quantiles(sorted.size, rank(0.50), rank(0.95), rank(0.99), sorted.last())
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
    val layers: Map<String, LayerTally>,
    val faultCounts: List<FaultCountCheck>,
    val labels: Map<String, LabelSummary>,
    val elapsedMs: Map<String, Quantiles>,
) {
    val faultCountsMatch get() = faultCounts.all { it.match }
}

fun reconcile(inputs: RunInputs): Reconciliation {
    val byId = inputs.answers.groupBy { it.requestId }
    val rowsById = inputs.rows.mapValues { (_, rows) -> rows.groupBy({ it.requestId }, { it.status }) }
    val groups = byId.map { (id, answers) ->
        RequestGroup(id, answers, Layer.entries.associateWith { rowsById.getValue(it)[id].orEmpty() })
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
