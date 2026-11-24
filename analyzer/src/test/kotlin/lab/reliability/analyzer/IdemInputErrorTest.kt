package lab.reliability.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// docs/measurement.md 9-3 — 멱등 키 분석의 입력 오류(종료 코드 1, report-idem.json·metrics-idem.tsv 를 쓰지 않고 있던 것은 지운다).
// 틀린 이유로 통과하지 않게: 오류 테스트마다 기준 기록(goodIdemRecord — 종료 0, 기준_… 테스트가 지킨다)에서 칸 하나만 바꾼다.
// 오류 메시지의 꼴은 문서에 없다 — 메시지에 틀린 파일이나 칸의 이름이 드는지만 본다(낱말 검사).
class IdemInputErrorTest {
    @TempDir
    lateinit var root: Path

    private fun good() = goodIdemRecord(root)

    /** 정답 줄 하나(requestId·copy 로 고른다)의 칸을 바꾼다. 값이 null 이면 그 칸을 뺀다. 값은 JSON 그대로. */
    private fun IdemRecord.changeAnswer(id: String, copy: Int, vararg change: Pair<String, String?>): IdemRecord {
        val text = answerText()
        var found = false
        val lines = text.trimEnd('\n').split("\n").map { line ->
            val m = RunRecord.MAPPER.readValue(line, LinkedHashMap::class.java)
            if (m["requestId"] != id || (m["copy"] as Number).toInt() != copy) line else {
                found = true
                val f = LinkedHashMap<String, String?>()
                m.forEach { (k, v) -> f[k as String] = RunRecord.MAPPER.writeValueAsString(v) }
                change.forEach { (k, v) -> f[k] = v }
                f.filterValues { it != null }.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
            }
        }
        check(found) { "정답 줄 $id/$copy 를 찾지 못했다" }
        base.answersText = lines.joinToString("\n", postfix = "\n")
        return this
    }

    /** 지금까지 넣은 정답 줄을 파일 꼴로(RunRecord 가 쓰는 것과 같게) 얻는다. */
    private fun IdemRecord.answerText(): String {
        write()
        return dir.resolve("answers.jsonl").toFile().readText()
    }

    /** 다시 보낸 줄 하나(requestId 로 고른다)의 칸을 바꾼다. */
    private fun IdemRecord.changeResend(id: String, vararg change: Pair<String, String?>): IdemRecord {
        val list = resendLines!!
        val i = list.indexOfFirst { it.contains("\"requestId\":\"$id\"") }
        check(i >= 0) { "다시 보낸 줄 $id 가 없다" }
        @Suppress("UNCHECKED_CAST")
        val m = RunRecord.MAPPER.readValue(list[i], LinkedHashMap::class.java) as Map<String, Any?>
        val f = LinkedHashMap<String, String?>()
        m.forEach { (k, v) -> f[k] = RunRecord.MAPPER.writeValueAsString(v) }
        change.forEach { (k, v) -> f[k] = v }
        list[i] = f.filterValues { it != null }.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
        return this
    }

    /** 주문의 키 표를 a 의 줄만 [aLine]으로 바꿔 쓴다(나머지 b~f 는 기준 기록 그대로). */
    private fun IdemRecord.orderKeysWithA(aLine: String): IdemRecord {
        keyCsvText["order"] = (
            listOf("key,request_id,amount,state,response_status", aLine) +
                listOf("b", "c", "d", "e", "f").map { "$it,$it,${amountFor(it)},COMPLETED,201" }
            ).joinToString("\n", postfix = "\n")
        return this
    }

    private val aAmount = amountFor("a")

    private fun assertMentions(out: String, word: String) =
        assertTrue(out.contains(word), "입력 오류 메시지에 '$word' 가 없다: $out")

    // ---- 기준 ----

    @Test
    fun 기준_기록은_통과한다() {
        val r = good()
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 기준_정답_줄을_칸으로_다시_써도_통과한다() {
        // changeAnswer 가 바꾸지 않은 줄을 같은 뜻으로 다시 쓰는지(도구의 확인)
        val r = good().changeAnswer("a", 1)
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 기준_다시_보낸_줄을_칸으로_다시_써도_통과한다() {
        val r = good().changeResend("c")
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 입력_오류면_있던_보고와_tsv를_지운다() {
        val r = good()
        r.dir.resolve("report-idem.json").writeText("{}")
        r.dir.resolve("metrics-idem.tsv").writeText("x\t1\n")
        r.changeAnswer("a", 1, "key" to null)
        r.assertInputError()
    }

    // ---- 정답 줄의 task 04 칸 ----

    @Test
    fun key가_없으면_입력_오류다() {
        assertMentions(good().changeAnswer("a", 1, "key" to null).assertInputError(), "key")
    }

    @Test
    fun key가_빈_문자열이면_입력_오류다() {
        good().changeAnswer("a", 1, "key" to "\"\"").assertInputError()
    }

    @Test
    fun key가_문자열이_아니면_입력_오류다() {
        good().changeAnswer("a", 1, "key" to "7").assertInputError()
    }

    @Test
    fun copy가_없으면_입력_오류다() {
        assertMentions(good().changeAnswer("a", 1, "copy" to null).assertInputError(), "copy")
    }

    @Test
    fun copy가_0이면_입력_오류다() {
        good().changeAnswer("a", 1, "copy" to "0").assertInputError()
    }

    @Test
    fun copy가_문자열이면_입력_오류다() {
        good().changeAnswer("a", 1, "copy" to "\"1\"").assertInputError()
    }

    @Test
    fun copy가_소수면_입력_오류다() {
        good().changeAnswer("a", 1, "copy" to "1.0").assertInputError()
    }

    @Test
    fun copy_2는_받는다() {
        // 경계의 안쪽: 한 줄뿐인 requestId 의 copy 가 2여도 꼴은 맞다(정수 ≥ 1)
        val r = good().changeAnswer("a", 1, "copy" to "2")
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun attemptFault가_없으면_입력_오류다() {
        assertMentions(good().changeAnswer("a", 1, "attemptFault" to null).assertInputError(), "attemptFault")
    }

    @Test
    fun dropFault가_없으면_입력_오류다() {
        assertMentions(good().changeAnswer("a", 1, "dropFault" to null).assertInputError(), "dropFault")
    }

    @Test
    fun dropFault가_문자열이_아니면_입력_오류다() {
        good().changeAnswer("a", 1, "dropFault" to "null").assertInputError()
    }

    @Test
    fun replayed가_없으면_입력_오류다() {
        assertMentions(good().changeAnswer("a", 1, "replayed" to null).assertInputError(), "replayed")
    }

    @Test
    fun replayed가_불이_아니면_입력_오류다() {
        good().changeAnswer("a", 1, "replayed" to "\"false\"").assertInputError()
    }

    @Test
    fun sentAtMs가_없으면_입력_오류다() {
        good().changeAnswer("a", 1, "sentAtMs" to null).assertInputError()
    }

    @Test
    fun 정답이_0줄이면_입력_오류다() {
        val r = good()
        r.base.answersText = ""
        r.assertInputError()
    }

    // ---- dropFault 의 문법(9-1, docs/idempotency.md 4절) ----

    @Test
    fun dropFault의_n이_0이면_입력_오류다() {
        good().changeAnswer("c", 1, "dropFault" to "\"payment:drop-after-commit=0\"").assertInputError()
    }

    @Test
    fun dropFault의_종류가_틀리면_입력_오류다() {
        good().changeAnswer("c", 1, "dropFault" to "\"payment:drop-before-commit=1\"").assertInputError()
    }

    @Test
    fun dropFault의_서비스가_셋_밖이면_입력_오류다() {
        good().changeAnswer("c", 1, "dropFault" to "\"shipping:drop-after-commit=1\"").assertInputError()
    }

    @Test
    fun dropFault에_한_서비스를_두_번_가리키면_입력_오류다() {
        good().changeAnswer("c", 1, "dropFault" to "\"payment:drop-after-commit=1,payment:drop-after-commit=2\"").assertInputError()
    }

    @Test
    fun dropFault의_등호_앞뒤에_공백이_있으면_입력_오류다() {
        good().changeAnswer("c", 1, "dropFault" to "\"payment:drop-after-commit = 1\"").assertInputError()
    }

    @Test
    fun dropFault의_지시와_쌍점_앞뒤의_공백은_받는다() {
        // 경계의 안쪽 — 다시 보낸 줄도 같은 값으로(첫 보낸 것의 지시로 다시 센다)
        val v = "\" payment : drop-after-commit=1 \""
        val r = good().changeAnswer("c", 1, "dropFault" to v).changeResend("c", "dropFault" to v)
        assertEquals(0, r.runIdem(), r.lastOutput)
        assertEquals(1L, r.report().long("dropFaults", "payment", "expected"))
    }

    @Test
    fun 다시_보낸_줄의_dropFault_문법이_틀려도_입력_오류다() {
        good().changeResend("c", "dropFault" to "\"payment:drop-after-commit=x\"").assertInputError()
    }

    @Test
    fun attemptFault에_outage가_있으면_입력_오류다() {
        good().changeAnswer("a", 1, "attemptFault" to "\"payment:outage=1-2\"").assertInputError()
    }

    @Test
    fun attemptFault의_문법이_틀리면_입력_오류다() {
        good().changeAnswer("a", 1, "attemptFault" to "\"payment:fail-first=0\"").assertInputError()
    }

    // ---- resends.jsonl ----

    @Test
    fun 다시_보낸_줄의_type이_resend가_아니면_입력_오류다() {
        assertMentions(good().changeResend("c", "type" to "\"answer\"").assertInputError(), "resends.jsonl")
    }

    @Test
    fun 다시_보낸_줄의_requestId가_정답에_없으면_입력_오류다() {
        // z: 정답에 없는 requestId — 시각 기록은 없다(그것만 바꾼다)
        val r = good()
        r.resendLines!! += r.answerLine("resend", "z", R + 300, 201)
        r.assertInputError()
    }

    @Test
    fun 다시_보낸_줄의_status_0은_받는다() {
        // 9-3 "status가 0이나 100~599가 아님" — 정답 줄(1절)과 같이 0(응답 없음)은 받는다
        val r = good().changeResend("c", "status" to "0")
        assertEquals(0, r.runIdem(), r.lastOutput)
        assertEquals(1L, r.report().long("reconcile", "completed"))
    }

    @Test
    fun 다시_보낸_줄의_status가_99면_입력_오류다() {
        good().changeResend("c", "status" to "99").assertInputError()
    }

    @Test
    fun 다시_보낸_줄의_status가_600이면_입력_오류다() {
        good().changeResend("c", "status" to "600").assertInputError()
    }

    @Test
    fun 다시_보낸_줄의_status가_100과_599는_받는다() {
        val r = good().changeResend("c", "status" to "599").changeResend("f", "status" to "100")
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 다시_보낸_줄의_elapsedMs가_음수면_입력_오류다() {
        good().changeResend("c", "elapsedMs" to "-1").assertInputError()
    }

    @Test
    fun 다시_보낸_줄의_elapsedMs가_수가_아니면_입력_오류다() {
        good().changeResend("c", "elapsedMs" to "\"42\"").assertInputError()
    }

    @Test
    fun 다시_보낸_줄의_sentAtMs가_음수면_입력_오류다() {
        good().changeResend("c", "sentAtMs" to "-1").assertInputError()
    }

    @Test
    fun 다시_보낸_줄의_task_04_칸이_없으면_입력_오류다() {
        good().changeResend("c", "copy" to null).assertInputError()
    }

    @Test
    fun 다시_보낸_줄이_JSON_객체가_아니면_입력_오류다() {
        val r = good()
        r.resendLines!! += "[1,2]"
        r.assertInputError()
    }

    @Test
    fun resends에_CR이_있으면_입력_오류다() {
        val r = good()
        val lines = r.resendLines!!.toList()
        r.resendLines = null
        r.resendsText = lines.joinToString("") { "$it\r\n" }
        r.assertInputError()
    }

    // ---- 키 표 CSV ----

    @Test
    fun 키_표가_없으면_입력_오류다() {
        val r = good()
        r.omit += "db/idempotency-inventory.csv"
        assertMentions(r.assertInputError(), "idempotency-inventory.csv")
    }

    @Test
    fun 키_표의_머리줄이_다르면_입력_오류다() {
        val r = good()
        r.orderKeysWithA("a,a,$aAmount,COMPLETED,201")
        r.keyCsvText["order"] = r.keyCsvText.getValue("order").replaceFirst("response_status", "status")
        r.assertInputError()
    }

    @Test
    fun 키_표가_머리줄뿐이면_받는다() {
        // 경계의 안쪽: 행 0개 — 키가 꺼진 층(그 층의 센 수도 0)
        val r = good()
        r.enabled["payment"] = false
        r.keyCsvText["payment"] = "key,request_id,amount,state,response_status\n"
        r.counts("payment", "new", 0).counts("payment", "replayed", 0)
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 기준_키_표를_파일로_다시_써도_통과한다() {
        val r = good().orderKeysWithA("a,a,$aAmount,COMPLETED,201")
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 키_표의_네_상태와_빈_response_status는_받는다() {
        // 경계의 안쪽: IN_PROGRESS 는 응답 칸이 비어 있다
        for ((i, line) in listOf("a,a,$aAmount,IN_PROGRESS,", "a,a,$aAmount,UNKNOWN,504", "a,a,$aAmount,FAILED,502").withIndex()) {
            val r = goodIdemRecord(root.resolve("ok$i")).orderKeysWithA(line)
            assertEquals(0, r.runIdem(), "$line: ${r.lastOutput}")
        }
    }

    @Test
    fun 키_표의_state가_모르는_낱말이면_입력_오류다() {
        good().orderKeysWithA("a,a,$aAmount,DONE,201").assertInputError()
    }

    @Test
    fun 키_표의_state는_대소문자를_가린다() {
        good().orderKeysWithA("a,a,$aAmount,completed,201").assertInputError()
    }

    @Test
    fun 키_표의_response_status가_두_자리면_입력_오류다() {
        good().orderKeysWithA("a,a,$aAmount,COMPLETED,20").assertInputError()
    }

    @Test
    fun 키_표의_response_status가_네_자리면_입력_오류다() {
        good().orderKeysWithA("a,a,$aAmount,COMPLETED,2010").assertInputError()
    }

    @Test
    fun 키_표의_response_status가_정수가_아니면_입력_오류다() {
        good().orderKeysWithA("a,a,$aAmount,COMPLETED,abc").assertInputError()
    }

    @Test
    fun 키_표의_칸_수가_다르면_입력_오류다() {
        val r = good()
        r.orderKeysWithA("a,a,$aAmount,COMPLETED")
        r.assertInputError()
    }

    @Test
    fun 키_표에_따옴표가_있으면_입력_오류다() {
        val r = good()
        r.orderKeysWithA("\"a\",a,$aAmount,COMPLETED,201")
        r.assertInputError()
    }

    @Test
    fun 키_표에_CR이_있으면_입력_오류다() {
        val r = good()
        r.orderKeysWithA("a,a,$aAmount,COMPLETED,201\r")
        r.assertInputError()
    }

    // ---- 센 수·끊은 수·넣은 실패 ----

    @Test
    fun 센_수_파일이_없으면_입력_오류다() {
        val r = good()
        r.omit += "idempotency-counts.json"
        assertMentions(r.assertInputError(), "idempotency-counts.json")
    }

    @Test
    fun 센_수에_종류가_빠지면_입력_오류다() {
        val r = good()
        r.countsText = LAYERS.joinToString(",", "{", "}") { s ->
            "\"$s\":" + IDEM_KINDS.filter { it != "missing-key" }.joinToString(",", "{", "}") { k -> "\"$k\":0" }
        }
        r.assertInputError()
    }

    @Test
    fun 센_수에_여분의_종류가_있으면_입력_오류다() {
        val r = good()
        r.countsText = """{"order":{"new":6,"replayed":2,"in-progress":1,"mismatch":1,"resumed":1,"missing-key":0,"waited":0},""" +
            """"inventory":{"new":6,"replayed":0,"in-progress":0,"mismatch":0,"resumed":1,"missing-key":0},""" +
            """"payment":{"new":6,"replayed":1,"in-progress":0,"mismatch":0,"resumed":0,"missing-key":0}}"""
        r.assertInputError()
    }

    @Test
    fun 센_수를_파일로_다시_써도_통과한다() {
        // 위 여분 테스트의 기준(같은 값, 여분 없음)
        val r = good()
        r.countsText = """{"order":{"new":6,"replayed":2,"in-progress":1,"mismatch":1,"resumed":1,"missing-key":0},""" +
            """"inventory":{"new":6,"replayed":0,"in-progress":0,"mismatch":0,"resumed":1,"missing-key":0},""" +
            """"payment":{"new":6,"replayed":1,"in-progress":0,"mismatch":0,"resumed":0,"missing-key":0}}"""
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 센_수에_여분의_서비스가_있으면_입력_오류다() {
        val r = good()
        r.countsText = """{"order":{"new":6,"replayed":2,"in-progress":1,"mismatch":1,"resumed":1,"missing-key":0},""" +
            """"inventory":{"new":6,"replayed":0,"in-progress":0,"mismatch":0,"resumed":1,"missing-key":0},""" +
            """"payment":{"new":6,"replayed":1,"in-progress":0,"mismatch":0,"resumed":0,"missing-key":0},""" +
            """"shipping":{"new":0,"replayed":0,"in-progress":0,"mismatch":0,"resumed":0,"missing-key":0}}"""
        r.assertInputError()
    }

    @Test
    fun 센_수가_문자열이면_입력_오류다() {
        val r = good()
        r.countsText = """{"order":{"new":"6","replayed":2,"in-progress":1,"mismatch":1,"resumed":1,"missing-key":0},""" +
            """"inventory":{"new":6,"replayed":0,"in-progress":0,"mismatch":0,"resumed":1,"missing-key":0},""" +
            """"payment":{"new":6,"replayed":1,"in-progress":0,"mismatch":0,"resumed":0,"missing-key":0}}"""
        r.assertInputError()
    }

    @Test
    fun 센_수가_음수면_입력_오류다() {
        good().counts("inventory", "replayed", -1).assertInputError()
    }

    @Test
    fun 끊은_수_파일이_없으면_입력_오류다() {
        val r = good()
        r.omit += "drop-faults.json"
        assertMentions(r.assertInputError(), "drop-faults.json")
    }

    @Test
    fun 끊은_수에_서비스가_빠지면_입력_오류다() {
        val r = good()
        r.dropsText = """{"order":{"drop-after-commit":1},"payment":{"drop-after-commit":1}}"""
        r.assertInputError()
    }

    @Test
    fun 끊은_수에_여분의_키가_있으면_입력_오류다() {
        val r = good()
        r.dropsText = """{"order":{"drop-after-commit":1,"drop-before-commit":0},"inventory":{"drop-after-commit":0},"payment":{"drop-after-commit":1}}"""
        r.assertInputError()
    }

    @Test
    fun 끊은_수가_소수면_입력_오류다() {
        val r = good()
        r.dropsText = """{"order":{"drop-after-commit":1.0},"inventory":{"drop-after-commit":0},"payment":{"drop-after-commit":1}}"""
        r.assertInputError()
    }

    @Test
    fun 끊은_수를_파일로_다시_써도_통과한다() {
        val r = good()
        r.dropsText = """{"order":{"drop-after-commit":1},"inventory":{"drop-after-commit":0},"payment":{"drop-after-commit":1}}"""
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 넣은_실패_파일이_없으면_입력_오류다() {
        val r = good()
        r.omit += "attempt-faults.json"
        r.assertInputError()
    }

    // ---- 설정 ----

    @Test
    fun 결제의_설정_파일이_없으면_입력_오류다() {
        // 9-2: 세 서비스 모두 idempotency.enabled 를 읽는다
        val r = good()
        r.omit += "settings-payment.json"
        assertMentions(r.assertInputError(), "settings-payment.json")
    }

    @Test
    fun enabled가_문자열이면_입력_오류다() {
        val r = good()
        r.settingsText["inventory"] = """{"idempotency":{"enabled":"true"}}"""
        r.assertInputError()
    }

    @Test
    fun enabled가_없으면_입력_오류다() {
        val r = good()
        r.settingsText["order"] = """{"idempotency":{}}"""
        r.assertInputError()
    }

    @Test
    fun 설정은_enabled만_읽는다() {
        // 경계의 안쪽: 다른 칸이 없어도(retry·nextLayer 없음) 받는다
        val r = good()
        r.settingsText["order"] = """{"idempotency":{"enabled":true}}"""
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    // ---- 시각 기록 ----

    @Test
    fun 정답에_없는_requestId의_시각_기록은_입력_오류다() {
        good().timing("payment", "zz", us(T + 900), us(T + 901), 201).assertInputError()
    }

    @Test
    fun 한_층에_한_requestId의_시각_기록이_여럿이어도_받는다() {
        // 기준 기록의 c 는 세 층에 둘씩 — 경계의 안쪽을 따로 적는다
        val r = good().timing("payment", "a", us(T + 50), us(T + 51), 201)
        assertEquals(0, r.runIdem(), r.lastOutput)
    }

    @Test
    fun 결제의_시각_기록에_call_칸이_있으면_입력_오류다() {
        good().timing("payment", "a", us(T + 50), us(T + 60), 201, us(T + 51), us(T + 59), "Completed").assertInputError()
    }

    @Test
    fun callOutcome의_꼴이_틀리면_입력_오류다() {
        good().timing("order", "a", us(T + 50), us(T + 60), 201, us(T + 51), us(T + 59), "Unknown:").assertInputError()
    }

    @Test
    fun 시각_기록의_차례가_틀리면_입력_오류다() {
        good().timing("order", "a", us(T + 50), us(T + 40), 201).assertInputError()
    }

    // ---- 인자 ----

    @Test
    fun run이_없으면_입력_오류다() {
        val (code, _) = analyzerCli("idem")
        assertEquals(1, code)
    }

    @Test
    fun 모르는_인자는_입력_오류다() {
        good().assertInputError("--planned", "9")
    }

    @Test
    fun expect의_짝이_없으면_입력_오류다() {
        good().assertInputError("--expect")
    }

    @Test
    fun 같은_인자가_두_번이면_입력_오류다() {
        val r = good()
        r.write()
        val (code, _) = analyzerCli("idem", "--run", r.dir.toString(), "--run", r.dir.toString())
        assertEquals(1, code)
    }

    @Test
    fun 기대_파일이_없으면_입력_오류다() {
        good().assertInputError("--expect", root.resolve("nope.json").toString())
    }

    @Test
    fun 기대_파일의_값이_min_max가_아니면_입력_오류다() {
        val e = writeExpect(root, """{"clients.completed":5}""", "expect.idem.json")
        good().assertInputError("--expect", e.toString())
    }

    @Test
    fun 기대_파일의_min이_max보다_크면_입력_오류다() {
        val e = writeExpect(root, """{"clients.completed":{"min":6,"max":5}}""", "expect.idem.json")
        good().assertInputError("--expect", e.toString())
    }

    @Test
    fun 기대_파일이_빈_객체면_입력_오류다() {
        val e = writeExpect(root, """{}""", "expect.idem.json")
        good().assertInputError("--expect", e.toString())
    }

    @Test
    fun 기준_기대_파일은_받는다() {
        // 위 기대 파일 오류들의 기준: 같은 이름, 맞는 꼴
        val e = writeExpect(root, """{"clients.completed":{"min":5,"max":5}}""", "expect.idem.json")
        val r = good()
        assertEquals(0, r.runIdem("--expect", e.toString()), r.lastOutput)
    }
}
