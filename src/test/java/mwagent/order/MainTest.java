package mwagent.order;

import static mwagent.common.Config.getConfig;

import java.io.File;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import mwagent.vo.ResultVO;

public class MainTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting ExtractLog Test without Maven...");
        
        getConfig().setLogger(Logger.getLogger("TestLogger"));
        getConfig().setOs("LINUX");
        getConfig().setHostName("test-host");

        File f = new File("tmp/test_dummy.log");
        String dummyLogPath = f.getAbsolutePath();

        JSONObject testCommand = new JSONObject();
        testCommand.put("command_id", "CMD-LOG-001");
        testCommand.put("repetition_seq", 1L);
        testCommand.put("host_name", "test-host");
        testCommand.put("user_name", "test-user");
        testCommand.put("target_file_path", f.getParent() + File.separator);
        testCommand.put("target_file_name", f.getName());
        testCommand.put("result_hash", "");
        testCommand.put("result_receiver", "SERVER");
        testCommand.put("target_object", "mwagent.order.ExtractLog");

        JSONObject params = new JSONObject();
        params.put("file", dummyLogPath);
        params.put("targetDate", "20260827");
        params.put("startTime", "100000"); // 10:00:00
        params.put("endTime", "235959");   // 23:59:59
        
        JSONArray dateRegexArr = new JSONArray();
        
        JSONObject regex1 = new JSONObject();
        regex1.put("regex", "^\\[(\\d{4}\\.\\d{2}\\.\\d{2}) (\\d{2}:\\d{2}:\\d{2})\\](?:\\s*\\[[^\\]]*\\]){1,2}");
        regex1.put("dateFormat", "yyyy.MM.dd");
        regex1.put("timeFormat", "HH:mm:ss");
        
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

        System.out.println("Command created, executing ExtractLog...");
        ExtractLog extractLog = new ExtractLog(testCommand);
        extractLog.execute();

        ResultVO resultVo = extractLog.getResultVo();
        String resultText = resultVo.getResult();
        System.out.println("Result Text:\n" + resultText);
        
        JSONParser parser = new JSONParser();
        JSONArray resultArray = (JSONArray) parser.parse(resultText);
        System.out.println("Found " + resultArray.size() + " log blocks.");
        
        // Second call for the next day (rollover)
        System.out.println("\nExecuting second call for rollover date (20260828)...");
        params.put("targetDate", "20260828");
        params.put("startTime", "000000"); 
        params.put("endTime", "000500");
        testCommand.put("additional_params", params.toJSONString());
        
        ExtractLog extractLog2 = new ExtractLog(testCommand);
        extractLog2.execute();
        String resultText2 = extractLog2.getResultVo().getResult();
        System.out.println("Result Text (Second Call):\n" + resultText2);
        
        JSONArray resultArray2 = (JSONArray) parser.parse(resultText2);
        System.out.println("Found " + resultArray2.size() + " log blocks.");
        
        int total = resultArray.size() + resultArray2.size();
        if (total == 5) {
            System.out.println("SUCCESS! Expected 5 total blocks (3 from first day + 2 rollover) and got " + total);
        } else {
            System.out.println("FAILED! Expected 5 blocks but got " + total);
        }
    }
}
