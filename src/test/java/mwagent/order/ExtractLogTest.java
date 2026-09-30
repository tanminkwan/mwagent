package mwagent.order;

import static mwagent.common.Config.getConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mwagent.vo.ResultVO;

/**
 * ExtractLog 테스트
 *
 * 두 가지 로그 포맷(A: 날짜+시각, B: 시각만)이 섞이고 자정을 넘는 로그를 쓴다.
 * ExtractLog 는 하루 단위로만 조회한다 (docs/ExtractLog_Guide.md: startTime <= endTime).
 * 자정을 넘는 구간은 전날·다음날로 두 번 조회한다.
 */
class ExtractLogTest {

    // B 포맷은 날짜가 없어 앞선 A 포맷 날짜를 이어 쓰고, 23시 → 0시로 넘어가면 다음 날로 본다
    private static final String DUMMY_LOG = String.join("\n",
            "[2026.08.27 10:00:00][0] [INFO] Server started",
            "10:01:00.123 [ERROR] [APP-001] Format B Exception",
            "java.lang.IllegalArgumentException",
            "\tat com.example.MyClass.methodB(MyClass.java:10)",
            "[2026.08.27 23:59:40][1] [RDW-11] Format A Exception",
            "java.lang.NullPointerException",
            "\tat com.example.MyClass.methodA(MyClass.java:20)",
            "23:59:55.789 [ERROR] [APP-003] Format B Exception again",
            "java.lang.Exception",
            "\tat com.example.MyClass.methodC(MyClass.java:30)",
            "00:00:15.123 [ERROR] [APP-004] Format B midnight rollover Exception",
            "java.lang.RuntimeException",
            "\tat com.example.MyClass.methodD(MyClass.java:40)",
            "[2026.08.28 00:01:00][2] [INFO] Back to Format A on next day",
            "00:02:00.999 [ERROR] [APP-005] Final Format B Exception",
            "java.lang.IllegalStateException",
            "\tat com.example.MyClass.methodE(MyClass.java:50)",
            "");

    @TempDir
    Path tempDir;

    private String dummyLogPath;

    @BeforeEach
    void setUp() throws Exception {
        getConfig().setLogger(Logger.getLogger("TestLogger"));
        getConfig().setOs("LINUX");
        getConfig().setHostName("test-host");

        File f = tempDir.resolve("test_dummy.log").toFile();
        Files.write(f.toPath(), DUMMY_LOG.getBytes(StandardCharsets.UTF_8));
        dummyLogPath = f.getAbsolutePath();
    }

    @Test
    void extract_BeforeMidnight_ShouldFindFormatAAndB() throws Exception {
        JSONArray result = run("20260827", "235000", "235959");

        // [2026.08.27 23:59:40] Format A Exception, 23:59:55 Format B Exception again
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.toJSONString()).contains("Format A Exception").contains("Format B Exception again");
    }

    @Test
    void extract_AfterMidnight_ShouldFindRolloverLines() throws Exception {
        JSONArray result = run("20260828", "000000", "000500");

        // 00:00:15 (자정 넘김으로 08-28), 00:02:00. 00:01:00 INFO 줄은 키워드가 없어 빠진다
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.toJSONString()).contains("midnight rollover").contains("Final Format B");
    }

    @Test
    void extract_WithEndBeforeStart_ShouldFindNothing() throws Exception {
        // 하루 단위 조회라 23:50 ~ 00:05 는 빈 구간이다 (두 번 나눠 조회해야 한다)
        assertThat(run("20260827", "235000", "000500").size()).isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    private JSONArray run(String targetDate, String startTime, String endTime) throws Exception {
        JSONObject params = new JSONObject();
        params.put("file", dummyLogPath);
        params.put("targetDate", targetDate);
        params.put("startTime", startTime);
        params.put("endTime", endTime);

        JSONArray dateRegexArr = new JSONArray();

        JSONObject formatA = new JSONObject();
        formatA.put("regex", "^\\[(\\d{4}\\.\\d{2}\\.\\d{2}) (\\d{2}:\\d{2}:\\d{2})\\](?: \\[[\\w-]+\\])*");
        formatA.put("dateFormat", "yyyy.MM.dd");
        formatA.put("timeFormat", "HH:mm:ss");

        JSONObject formatB = new JSONObject();
        formatB.put("regex", "^(\\d{2}:\\d{2}:\\d{2})\\.\\d{3}(?: \\[[\\w-]+\\])*");
        formatB.put("timeFormat", "HH:mm:ss");

        dateRegexArr.add(formatA);
        dateRegexArr.add(formatB);
        params.put("dateRegex", dateRegexArr);

        JSONArray keywords = new JSONArray();
        keywords.add("Exception");
        params.put("keywords", keywords);

        JSONObject command = new JSONObject();
        command.put("command_id", "CMD-LOG-001");
        command.put("repetition_seq", 1L);
        command.put("host_name", "test-host");
        command.put("user_name", "test-user");
        command.put("target_file_path", tempDir.toString() + File.separator);
        command.put("target_file_name", "test_dummy.log");
        command.put("result_hash", "");
        command.put("result_receiver", "SERVER");
        command.put("target_object", "mwagent.order.ExtractLog");
        command.put("additional_params", params.toJSONString());

        ExtractLog extractLog = new ExtractLog(command);
        assertThat(extractLog.execute()).isEqualTo(1);

        ResultVO resultVo = extractLog.getResultVo();
        assertThat(resultVo.isOk()).isTrue();

        return (JSONArray) new JSONParser().parse(resultVo.getResult());
    }
}
