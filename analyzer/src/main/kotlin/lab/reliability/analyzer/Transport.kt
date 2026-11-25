package lab.reliability.analyzer

import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path

// task 05의 전송 대조: 고른 전송(떠 있는 객체의 /settings) · 서비스가 전송마다 센 호출 수(transport-counts.json) · 시각 기록의 transport 칸을 견준다 —
// gRPC를 골랐는데 HTTP로 불렸다면(또는 그 반대) 실행이 불일치로 끝나게. gRPC면 받은 기한이 기록에 있고, 기한 전파를 켰으면 결제의 기한이
// 재고의 기한을 넘지 않는지, 처리 코드가 취소를 보지 않는 조건에서 멈춘 기록이 없는지도 본다. 형식과 식의 정본: docs/measurement.md 11절.
// 명령: analyzer transport --run <실행 폴더> [--expect <기대 파일>]

private const val TRANSPORT_USAGE = "usage: analyzer transport --run <실행 폴더> [--expect <기대 파일>]"

/** 결제가 받은 기한이 재고가 받은 기한보다 이만큼 넘게 늦으면 "재고의 기한을 넘었다"로 센다(ms — 전달 시간과 시계의 틈을 품는 여유). */
const val DEADLINE_SLACK_MS = 50.0

val TRANSPORTS = listOf("http", "grpc")

private val transportSummaryKeys = listOf(
    "counts.order.outbound.grpc", "counts.inventory.inbound.grpc", "counts.payment.inbound.grpc",
    "counts.inventory.inbound.http", "counts.payment.inbound.http", "counts.payment.cancelledBeforeHandler.grpc",
    "deadline.paymentAfterInventoryOverSlack",
)

/** settings-<서비스>.json에서 이 분석이 읽는 것. */
data class TransportSetting(val chosen: String, val deadlinePropagation: Boolean, val observeCancellation: Boolean)

fun runTransport(args: Array<String>): Int {
    val options = try {
        parseTransportArgs(args)
    } catch (e: InputException) {
        System.err.println(e.message)
        System.err.println(TRANSPORT_USAGE)
        return EXIT_INPUT
    }
    val run = Path.of(options.getValue("--run"))
    val out = listOf(run.resolve("report-transport.json"), run.resolve("metrics-transport.tsv"))
    return try {
        out.forEach(Files::deleteIfExists)
        val expect = options["--expect"]?.let { readTimingExpect(Path.of(it)) }
        val (report, alwaysDifferences) = transportReport(run)
        val metrics = flatten(report)
        val differences = alwaysDifferences.toMutableList()
        expect?.forEach { (key, range) ->
            val v = metrics[key]
            if (v !is Number) {
                differences += "$key: 기대 ${num(range.first)}~${num(range.second)}, 실제 ${v ?: "없음"}"
            } else if (v.toDouble() < range.first || v.toDouble() > range.second) {
                differences += "$key: 기대 ${num(range.first)}~${num(range.second)}, 실제 ${num(v.toDouble())}"
            }
        }
        val full = linkedMapOf<String, Any?>("run" to run.fileName.toString(), "expect" to options["--expect"], "differences" to differences) + report
        json.writerWithDefaultPrettyPrinter().writeValue(run.resolve("report-transport.json").toFile(), full)
        Files.writeString(run.resolve("metrics-transport.tsv"), metrics.entries.joinToString("") { "${it.key}\t${it.value ?: ""}\n" })
        println("${run.fileName}: 전송 대조 — chosen=${report["chosen"]}, " + transportSummaryKeys.joinToString(", ") { k -> "$k=${metrics[k] ?: "없음"}" } +
            if (differences.isEmpty()) "" else ", 불일치 ${differences.size}건")
        differences.forEach { System.err.println("불일치: $it") }
        if (differences.isEmpty()) EXIT_OK else EXIT_MISMATCH
    } catch (e: InputException) {
        System.err.println("입력 오류: ${e.message}")
        out.forEach { runCatching { Files.deleteIfExists(it) } }
        EXIT_INPUT
    }
}

private fun parseTransportArgs(args: Array<String>): Map<String, String> {
    val known = setOf("--run", "--expect")
    if (args.size % 2 != 0) throw InputException("인자는 '--이름 값' 짝이어야 한다")
    val options = linkedMapOf<String, String>()
    args.toList().chunked(2).forEach { (k, v) ->
        if (k !in known) throw InputException("모르는 인자 $k")
        if (k in options) throw InputException("인자 $k 가 두 번 나왔다")
        options[k] = v
    }
    val run = options["--run"] ?: throw InputException("--run 이 없다")
    if (run.isBlank() || Path.of(run).fileName == null) throw InputException("--run '$run' 에 실행 폴더 이름이 없다")
    return options
}

private fun JsonNode.bool(name: String, where: String): Boolean {
    val v = get(name)
    if (v == null || !v.isBoolean) throw InputException("$where: '$name'이 불 값이 아니다")
    return v.booleanValue()
}

internal fun readTransportSetting(path: Path): TransportSetting {
    val node = readJson(path)
    val t = node.get("transport")
    if (t == null || !t.isObject) throw InputException("$path: transport가 객체가 아니다(task 05 앞의 실행이거나 읽지 못함)")
    val chosen = t.get("chosen")
    if (chosen == null || !chosen.isString || chosen.stringValue() !in TRANSPORTS) throw InputException("$path: transport.chosen이 http|grpc가 아니다")
    val g = node.get("grpc")
    if (g == null || !g.isObject) throw InputException("$path: grpc가 객체가 아니다")
    return TransportSetting(chosen.stringValue(), g.bool("deadlinePropagation", "$path grpc"), g.bool("observeCancellation", "$path grpc"))
}

/**
 * transport-counts.json: {"order":{"inbound":{"http":n,"grpc":n},"outbound":{...},"grpcCancelledBeforeHandler":n},"inventory":…,"payment":…}
 * → 서비스 → 방향 → 전송 → 수. 처리 코드에 닿기 전에 취소된 gRPC 호출 수는 방향 "cancelledBeforeHandler"의 "grpc"로 둔다.
 */
internal fun readTransportCounts(path: Path): Map<String, Map<String, Map<String, Long>>> {
    val node = readJson(path)
    if (!node.isObject) throw InputException("$path: JSON 객체가 아니다")
    return Layer.entries.associate { layer ->
        val svc = node.get(layer.label) ?: throw InputException("$path: '${layer.label}'이 없다")
        val dropped = svc.get("grpcCancelledBeforeHandler")
        if (dropped == null || !dropped.isIntegralNumber || !dropped.canConvertToLong() || dropped.longValue() < 0) {
            throw InputException("$path: ${layer.label}.grpcCancelledBeforeHandler 가 0 이상의 정수가 아니다")
        }
        layer.label to listOf("inbound", "outbound").associateWith { dir ->
            val d = svc.get(dir) ?: throw InputException("$path: ${layer.label}.$dir 가 없다")
            TRANSPORTS.associateWith { t ->
                val v = d.get(t)
                if (v == null || !v.isIntegralNumber || !v.canConvertToLong() || v.longValue() < 0) throw InputException("$path: ${layer.label}.$dir.$t 가 0 이상의 정수가 아니다")
                v.longValue()
            }
        } + ("cancelledBeforeHandler" to mapOf("grpc" to dropped.longValue()))
    }
}

fun transportReport(run: Path): Pair<Map<String, Any?>, List<String>> {
    val settings = Layer.entries.associate { it.label to readTransportSetting(run.resolve("settings-${it.label}.json")) }
    val counts = readTransportCounts(run.resolve("transport-counts.json"))
    val timings = Layer.entries.associate { layer ->
        val path = run.resolve("timing").resolve("timings-${layer.label}.json")
        val list = readTimings(path, layer != Layer.PAYMENT)
        list.forEachIndexed { i, t -> if (t.transport == null) throw InputException("$path[$i]: transport 칸이 없다(task 05 앞의 기록은 이 분석을 하지 않는다)") }
        layer.label to list
    }
    val differences = mutableListOf<String>()

    // ① 세 서비스가 고른 전송이 같다
    val chosenSet = settings.values.map { it.chosen }.toSet()
    if (chosenSet.size != 1) differences += "transport.chosen: 세 서비스가 같아야 한다, 실제 ${settings.mapValues { it.value.chosen }}"
    val chosen = settings.getValue("order").chosen
    val other = TRANSPORTS.first { it != chosen }

    // ② 고르지 않은 전송으로 센 호출은 0 — 주문의 들어온 호출은 늘 HTTP(부하 도구 → 주문)
    fun c(svc: String, dir: String, t: String) = counts.getValue(svc).getValue(dir).getValue(t)
    for ((svc, dir) in listOf("order" to "outbound", "inventory" to "inbound", "inventory" to "outbound", "payment" to "inbound")) {
        val n = c(svc, dir, other)
        if (n != 0L) differences += "counts.$svc.$dir.$other: 늘 0(고른 전송 $chosen), 실제 $n"
    }
    if (c("order", "inbound", "grpc") != 0L) differences += "counts.order.inbound.grpc: 늘 0(부하 도구와 주문 사이는 HTTP), 실제 ${c("order", "inbound", "grpc")}"
    if (c("payment", "outbound", "http") + c("payment", "outbound", "grpc") != 0L) differences += "counts.payment.outbound: 늘 0(맨 아래 층), 실제 ${counts.getValue("payment").getValue("outbound")}"

    // ③ 시각 기록의 transport — 주문은 http, 재고·결제는 고른 전송
    val wrongRecords = linkedMapOf(
        "order" to timings.getValue("order").count { it.transport != "http" },
        "inventory" to timings.getValue("inventory").count { it.transport != chosen },
        "payment" to timings.getValue("payment").count { it.transport != chosen },
    )
    wrongRecords.forEach { (svc, n) -> if (n != 0) differences += "records.$svc.wrongTransport: 늘 0, 실제 $n" }

    // ④ 센 수 = 기록의 수(들어온 것), 센 수 = 기록의 call 수(나간 것)
    val inboundOf = mapOf("order" to "http", "inventory" to chosen, "payment" to chosen)
    val records = timings.mapValues { it.value.size.toLong() }
    val calls = timings.mapValues { (_, ts) -> ts.count { it.callOutcome != null }.toLong() }
    // gRPC로 받은 서비스: 센 수 = 기록 수 + 처리 코드에 닿기 전에 취소된 수(grpc-java가 처리 코드를 부르지 않는다 — 기록이 없다)
    for (svc in listOf("order", "inventory", "payment")) {
        val t = inboundOf.getValue(svc)
        val dropped = if (t == "grpc") c(svc, "cancelledBeforeHandler", "grpc") else 0L
        if (c(svc, "inbound", t) != records.getValue(svc) + dropped) {
            differences += "counts.$svc.inbound.$t: 시각 기록 수 ${records.getValue(svc)} + 처리 코드에 닿기 전에 취소된 수 $dropped 와 같아야 한다, 실제 ${c(svc, "inbound", t)}"
        }
        if (t != "grpc" && c(svc, "cancelledBeforeHandler", "grpc") != 0L) {
            differences += "counts.$svc.cancelledBeforeHandler.grpc: gRPC로 받지 않는 서비스에서 늘 0, 실제 ${c(svc, "cancelledBeforeHandler", "grpc")}"
        }
    }
    for (svc in listOf("order", "inventory")) {
        if (c(svc, "outbound", chosen) != calls.getValue(svc)) differences += "counts.$svc.outbound.$chosen: 기록의 call 수 ${calls.getValue(svc)}와 같아야 한다, 실제 ${c(svc, "outbound", chosen)}"
    }

    // ⑤ 기한·취소의 칸: HTTP면 없어야 하고, gRPC면 받은 기한이 모두 있어야 한다(위 층은 늘 기한을 건다)
    val grpcLayers = listOf("inventory", "payment")
    val fieldsOnHttp = timings.mapValues { (svc, ts) ->
        ts.count { (svc == "order" || chosen == "http") && (it.deadlineUs != null || it.cancelledUs != null || it.stopped != null) }
    }
    fieldsOnHttp.forEach { (svc, n) -> if (n != 0) differences += "records.$svc.grpcFieldsOnHttp: 늘 0, 실제 $n" }
    val missingDeadline = grpcLayers.associateWith { svc -> if (chosen == "grpc") timings.getValue(svc).count { it.deadlineUs == null } else 0 }
    missingDeadline.forEach { (svc, n) -> if (n != 0) differences += "deadline.$svc.missing: 늘 0(gRPC), 실제 $n" }

    // ⑥ 기한 전파: 결제가 받은 기한 − 재고가 받은 기한. 전파를 켰으면(G2·G3) 여유를 넘는 것이 없어야 한다
    val invById = timings.getValue("inventory").associateBy { it.requestId }
    val diffs = timings.getValue("payment").mapNotNull { p ->
        val i = invById[p.requestId] ?: return@mapNotNull null
        if (p.deadlineUs == null || i.deadlineUs == null) null else (p.deadlineUs - i.deadlineUs) / 1000.0
    }
    val overSlack = diffs.count { it > DEADLINE_SLACK_MS }
    if (settings.getValue("inventory").deadlinePropagation && overSlack != 0) {
        differences += "deadline.paymentAfterInventoryOverSlack: 기한 전파를 켰으면 늘 0, 실제 $overSlack"
    }

    // ⑦ 처리 코드가 취소를 보지 않는 서비스에는 멈춘 기록이 없다
    val stopped = timings.mapValues { (_, ts) -> ts.count { it.stopped != null } }
    stopped.forEach { (svc, n) -> if (!settings.getValue(svc).observeCancellation && n != 0) differences += "records.$svc.stopped: 취소를 보지 않는 조건에서 늘 0, 실제 $n" }

    val report = linkedMapOf<String, Any?>(
        "chosen" to chosen,
        "settings" to settings.mapValues { (_, s) -> linkedMapOf("deadlinePropagation" to s.deadlinePropagation, "observeCancellation" to s.observeCancellation) },
        "counts" to counts,
        "records" to timings.keys.associateWith { svc ->
            linkedMapOf(
                "count" to records.getValue(svc),
                "calls" to calls.getValue(svc),
                "wrongTransport" to wrongRecords.getValue(svc),
                "grpcFieldsOnHttp" to fieldsOnHttp.getValue(svc),
                "stopped" to stopped.getValue(svc),
                "cancelled" to timings.getValue(svc).count { it.cancelledUs != null },
            )
        },
        "deadline" to linkedMapOf(
            "inventoryMissing" to missingDeadline.getValue("inventory"),
            "paymentMissing" to missingDeadline.getValue("payment"),
            "paymentAfterInventoryMs" to quantiles(diffs),
            "paymentAfterInventoryOverSlack" to overSlack,
        ),
    )
    return report to differences
}
