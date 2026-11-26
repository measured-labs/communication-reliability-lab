package lab.reliability.analyzer

import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.writeText

// docs/measurement.md 12-1 — 실행 기록(config.env)의 시계 어긋남 칸을 손으로 쓴다.
// 이 파일은 분석기의 구현을 보지 않고 문서(12절)만으로 쓴다. 칸의 이름은 문서의 글자 그대로 적는다
// (구현의 상수를 쓰지 않는다 — 이름이 문서와 다르면 테스트가 드러내게).

const val CLOCK_START = "clock_offset_services_minus_load_ms_start"
const val CLOCK_OLD = "clock_offset_services_minus_load_ms"
const val CLOCK_END = "clock_offset_services_minus_load_ms_end"
const val CLOCK_DRIFT = "clock_offset_drift_max_ms"

/** 시계 칸이 아닌, 실행 스크립트가 늘 남기는 줄(12-1: 다른 줄은 보지 않는다). */
val OTHER_CONFIG_LINES = listOf(
    "scenario=cal-test",
    "repeat=1",
    "git_commit=da77c88",
    "git_dirty=no",
    "services_host=.164",
    "clock_offset_error_ms_start=0.450",
    "clock_offset_error_ms_end=0.512",
)

/** `<이름>=<값>` 줄들로 config.env 를 쓴다. */
fun writeConfig(dir: Path, lines: List<String>) {
    dir.resolve("config.env").writeText(lines.joinToString("\n", postfix = "\n"))
}

/** 시계 칸(앞·뒤·문턱 — null 이면 그 줄이 없다)과 다른 줄로 config.env 를 쓴다. */
fun writeClock(dir: Path, start: String?, end: String? = null, drift: String? = null, old: String? = null) {
    val lines = OTHER_CONFIG_LINES.toMutableList()
    start?.let { lines += "$CLOCK_START=$it" }
    old?.let { lines += "$CLOCK_OLD=$it" }
    end?.let { lines += "$CLOCK_END=$it" }
    drift?.let { lines += "$CLOCK_DRIFT=$it" }
    writeConfig(dir, lines)
}

/**
 * 입력 오류 테스트의 기준 시계 기록 — 앞 −0.300, 뒤 0.300(차 0.6 ≤ 문턱 5), 평균 0 → 옮기는 양 0.
 * 옮기는 양이 0이라 기준 기록(goodRetryRecord 등)의 지표가 그대로다 — 이 기록으로 종료 0인 것을 먼저 본다.
 */
val BASE_CLOCK_LINES: List<String> = OTHER_CONFIG_LINES + listOf(
    "$CLOCK_START=-0.300",
    "$CLOCK_END=0.300",
    "$CLOCK_DRIFT=5",
)

/** 이름 → 값(문자열 그대로, 빈 값은 "")으로 편 metrics*.tsv. */
fun readTsv(path: Path): Map<String, String> = path.readLines().filter { it.isNotEmpty() }.associate {
    val i = it.indexOf('\t')
    check(i > 0) { "탭이 없는 줄: $it" }
    it.substring(0, i) to it.substring(i + 1)
}

/** 보고의 clock 칸 넷을 수로(null 은 null) — offsetStartMs, offsetEndMs, offsetMs, shiftMs 차례. */
fun Map<String, Any?>.clockNumbers(): List<Double?> {
    @Suppress("UNCHECKED_CAST")
    val c = this["clock"] as? Map<String, Any?> ?: error("보고에 clock 칸이 없다: ${this.keys}")
    return listOf("offsetStartMs", "offsetEndMs", "offsetMs", "shiftMs").map {
        require(it in c) { "clock 칸에 $it 이 없다: $c" }
        (c[it] as Number?)?.toDouble()
    }
}
