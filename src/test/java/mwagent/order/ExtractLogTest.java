package mwagent.order;

import static mwagent.common.Config.getConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mwagent.vo.ResultVO;

class ExtractLogTest {

    private ExtractLog extractLog;
    private JSONObject testCommand;
    private String dummyLogPath;

    @BeforeEach
    void setUp() {
        getConfig().setLogger(Logger.getLogger("TestLogger"));
        getConfig().setOs("LINUX");
        getConfig().setHostName("test-host");

        // get absolute path for test_dummy.log
        File f = new File("tmp/test_dummy.log");
        dummyLogPath = f.getAbsolutePath();

        testCommand = new JSONObject();
        testCommand.put("command_id", "CMD-LOG-001");
        testCommand.put("repetition_seq", 1L);
        testCommand.put("host_name", "test-host");
        testCommand.put("user_name", "test-user");
        testCommand.put("target_file_path", f.getParent() + File.separator);
        testCommand.put("target_file_name", f.getName());
        testCommand.put("result_hash", "");
        testCommand.put("result_receiver", "SERVER");
        testCommand.put("target_object", "mwagent.order.ExtractLog");
    }

    @Test
    void testExtractLogWithDummyLog() throws Exception {
        // Construct additional_params JSON
        JSONObject params = new JSONObject();
        params.put("file", dummyLogPath);
        params.put("targetDate", "20260827");
        params.put("startTime", "235000"); // 23:50:00
        params.put("endTime", "000500");   // 00:05:00 next day
        
        JSONArray dateRegexArr = new JSONArray();
        
        // Format A regex
        JSONObject regex1 = new JSONObject();
        regex1.put("regex", "^\\[(\\d{4}\\.\\d{2}\\.\\d{2}) (\\d{2}:\\d{2}:\\d{2})\\](?: \\[[\\w-]+\\])*");
        regex1.put("dateFormat", "yyyy.MM.dd");
        regex1.put("timeFormat", "HH:mm:ss");
        
        // Format B regex
        JSONObject regex2 = new JSONObject();
        regex2.put("regex", "^(\\d{2}:\\d{2}:\\d{2})\\.\\d{3}(?: \\[[\\w-]+\\])*");
        regex2.put("timeFormat", "HH:mm:ss");

        dateRegexArr.add(regex1);
        dateRegexArr.add(regex2);
        
        params.put("dateRegex", dateRegexArr);
        
        JSONArray keywords = new JSONArray();
        keywords.add("Exception");
        params.put("keywords", keywords);
        
        testCommand.put("additional_params", params.toJSONString());

        // Execute ExtractLog
        extractLog = new ExtractLog(testCommand);
        int exitCode = extractLog.execute();

        // Assert
        assertThat(exitCode).isEqualTo(1);
        
        ResultVO resultVo = extractLog.getResultVo();
        assertThat(resultVo.isOk()).isTrue();
        
        String resultText = resultVo.getResult();
        System.out.println("Result Text:\n" + resultText);
        
        // Parse result JSON
        JSONParser parser = new JSONParser();
        JSONArray resultArray = (JSONArray) parser.parse(resultText);
        
        // We expect to find specific errors in the 23:50 to 00:05 time range
        // [2026.08.27 23:59:40] [ERROR] [APP-002] Format A Exception -> 1
        // 23:59:55.789 [ERROR] [APP-003] Format B Exception again -> 1
        // 00:00:15.123 [ERROR] [APP-004] Format B midnight rollover Exception -> 1
        // 00:02:00.999 [ERROR] [APP-005] Final Format B Exception -> 1
        
        assertThat(resultArray.size()).isEqualTo(4);
    }
}
