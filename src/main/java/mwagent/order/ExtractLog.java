package mwagent.order;

import static mwagent.common.Config.getConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ExtractLog extends Order {

    public ExtractLog(JSONObject command) {
        super(command);
    }

    @Override
    public int execute() {
        try {
            String additionalParamsStr = commandVo.getAdditionalParams();

            JSONParser parser = new JSONParser();
            JSONObject params = (JSONObject) parser.parse(additionalParamsStr);

            String fileParam = (String) params.get("file");
            if (fileParam != null && !fileParam.trim().isEmpty()) {
                java.io.File f = new java.io.File(fileParam);
                commandVo.setTargetFilePath(f.getParent() + java.io.File.separator);
                commandVo.setTargetFileName(f.getName());
            }
            String fileFullName = getFileFullName();

            String targetDate = (String) params.get("targetDate");
            String startTime = (String) params.get("startTime");
            String endTime = (String) params.get("endTime");
            Object dateRegex = params.get("dateRegex");
            String abbreviatePrefix = (String) params.get("abbreviatePrefix");
            String charsetParam = (String) params.get("charset");
            String charset = (charsetParam != null && !charsetParam.trim().isEmpty()) ? charsetParam.trim() : "UTF-8";
            
            JSONArray keywordsArray = (JSONArray) params.get("keywords");
            String[] keywords = new String[0];
            if (keywordsArray != null) {
                keywords = new String[keywordsArray.size()];
                for (int i = 0; i < keywordsArray.size(); i++) {
                    keywords[i] = (String) keywordsArray.get(i);
                }
            }

            List<BlockResult> blocks = extract(fileFullName, targetDate, startTime, endTime, dateRegex, abbreviatePrefix, charset, keywords);

            JSONArray resultArray = new JSONArray();
            for (BlockResult block : blocks) {
                JSONObject obj = new JSONObject();
                obj.put("text", block.text);
                obj.put("count", block.count);
                resultArray.add(obj);
            }

            String resultStr = resultArray.toString();
            String resultHash = getHash(resultStr);

            resultVo.setOk(true);
            resultVo.setHostName(commandVo.getHostName());
            resultVo.setTargetFilePath(commandVo.getTargetFilePath());
            resultVo.setTargetFileName(commandVo.getTargetFileName());
            
            if (!commandVo.getResultHash().isEmpty() && commandVo.getResultHash().equals(resultHash)) {
                resultVo.setResult("NO CHANGE");
            } else {
                resultVo.setResult(resultStr);
            }
            resultVo.setResultHash(resultHash);

        } catch (Exception e) {
            getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
            resultVo.setOk(false);
            resultVo.setHostName(commandVo.getHostName());
            resultVo.setTargetFilePath(commandVo.getTargetFilePath());
            resultVo.setTargetFileName(commandVo.getTargetFileName());
            resultVo.setResult("Error: " + e.getClass().getSimpleName());
        }

        return 1;
    }

    protected String getFileFullName() {
        String fileName = commandVo.getTargetFileName();
        String filePath = commandVo.getTargetFilePath();
        if (filePath != null && !filePath.endsWith("/") && !filePath.endsWith("\\")) {
            // Assume the separator is required if not present, though ReadPlainFile just concatenates.
            // But ReadPlainFile simply returns commandVo.getTargetFilePath() + file_name;
            return filePath + fileName;
        }
        return filePath + fileName;
    }

    public static class BlockResult {
        public final String text;
        public final int count;

        public BlockResult(String text, int count) {
            this.text = text;
            this.count = count;
        }
    }

    private static class Accumulator {
        String text;
        int count;

        Accumulator(String text) {
            this.text = text;
            this.count = 1;
        }
    }

    private static class RegexRule {
        Pattern pattern;
        DateTimeFormatter dateFormatter;
        DateTimeFormatter timeFormatter;
        RegexRule(Pattern pattern, DateTimeFormatter dateFormatter, DateTimeFormatter timeFormatter) {
            this.pattern = pattern;
            this.dateFormatter = dateFormatter;
            this.timeFormatter = timeFormatter;
        }
    }

    private List<BlockResult> extract(String filePath,
                                      String targetDateStr,
                                      String startTimeStr,
                                      String endTimeStr,
                                      Object dateRegexObj,
                                      String abbreviatePrefix,
                                      String charset,
                                      String... keywords) throws IOException {

        DateTimeFormatter paramFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        LocalDateTime startTime = LocalDateTime.parse(targetDateStr + startTimeStr, paramFormatter);
        LocalDateTime endTime = LocalDateTime.parse(targetDateStr + endTimeStr, paramFormatter);
        
        getConfig().getLogger().log(Level.INFO, "ExtractLog target file: " + filePath + ", range: " + startTime + " to " + endTime);

        List<RegexRule> rules = new ArrayList<>();
        if (dateRegexObj instanceof JSONArray) {
            JSONArray arr = (JSONArray) dateRegexObj;
            for (Object obj : arr) {
                JSONObject jsonRule = (JSONObject) obj;
                String regex = (String) jsonRule.get("regex");
                String dateFormat = (String) jsonRule.get("dateFormat");
                String timeFormat = (String) jsonRule.get("timeFormat");
                if (dateFormat == null || dateFormat.trim().isEmpty()) dateFormat = "yyyyMMdd";
                if (timeFormat == null || timeFormat.trim().isEmpty()) timeFormat = "HH:mm:ss";
                rules.add(new RegexRule(Pattern.compile(regex), DateTimeFormatter.ofPattern(dateFormat), DateTimeFormatter.ofPattern(timeFormat)));
            }
        }

        LocalDate baseDate = LocalDate.parse(targetDateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
        LocalDate currentDate = baseDate;
        LocalTime previousTime = null;

        Map<String, Accumulator> accByFirstLine = new LinkedHashMap<>();

        StringBuilder currentBlock = new StringBuilder();
        int currentBlockLineCount = 0;
        boolean inRange = false;
        String currentFirstLineContent = "";

        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(filePath), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LocalDateTime ts = null;
                Matcher m = null;

                for (RegexRule rule : rules) {
                    Matcher matcher = rule.pattern.matcher(line);
                    if (matcher.lookingAt()) {
                        try {
                            if (matcher.groupCount() >= 2) {
                                LocalDate d = LocalDate.parse(matcher.group(1), rule.dateFormatter);
                                LocalTime t = LocalTime.parse(matcher.group(2), rule.timeFormatter);
                                ts = LocalDateTime.of(d, t);
                            } else if (matcher.groupCount() == 1) {
                                LocalTime t = LocalTime.parse(matcher.group(1), rule.timeFormatter);
                                ts = LocalDateTime.of(currentDate, t);
                            }
                            if (ts != null) {
                                m = matcher;
                                break;
                            }
                        } catch (Exception e) {
                            // ignore and try next rule
                        }
                    }
                }

                if (ts != null) {
                    if (previousTime != null && ts.toLocalTime().isBefore(previousTime) && previousTime.getHour() >= 23 && ts.getHour() == 0) {
                        currentDate = currentDate.plusDays(1);
                        ts = LocalDateTime.of(currentDate, ts.toLocalTime());
                    } else if (!ts.toLocalDate().equals(currentDate)) {
                        currentDate = ts.toLocalDate();
                    }
                    previousTime = ts.toLocalTime();

                    flushBlock(accByFirstLine, currentBlock, currentFirstLineContent, inRange, keywords, abbreviatePrefix);

                    currentBlock.setLength(0);
                    currentBlock.append(line);
                    currentBlockLineCount = 1;
                    currentFirstLineContent = line.substring(m.end()).trim();
                    inRange = !ts.isBefore(startTime) && !ts.isAfter(endTime);
                } else {
                    if (currentBlockLineCount < 300) {
                        if (currentBlock.length() > 0) {
                            currentBlock.append(System.lineSeparator()).append(line);
                        }
                        currentBlockLineCount++;
                    }
                }
            }
            flushBlock(accByFirstLine, currentBlock, currentFirstLineContent, inRange, keywords, abbreviatePrefix);
        }

        List<BlockResult> results = new ArrayList<>();
        for (Accumulator acc : accByFirstLine.values()) {
            results.add(new BlockResult(acc.text, acc.count));
        }
        getConfig().getLogger().log(Level.INFO, "ExtractLog found " + results.size() + " unique blocks matching criteria.");
        return results;
    }

    private void flushBlock(Map<String, Accumulator> accByFirstLine,
                            StringBuilder block,
                            String firstLineContent,
                            boolean inRange,
                            String[] keywords,
                            String abbreviatePrefix) {
        if (block.length() == 0 || !inRange) {
            return;
        }
        String text = block.toString();
        if (!containsAnyKeyword(text, keywords)) {
            return;
        }
        if (abbreviatePrefix != null && !abbreviatePrefix.isEmpty()) {
            text = abbreviateText(text, abbreviatePrefix);
        }

        Accumulator acc = accByFirstLine.get(firstLineContent);
        if (acc == null) {
            accByFirstLine.put(firstLineContent, new Accumulator(text));
        } else {
            acc.count++;
        }
    }

    private String abbreviateText(String text, String prefix) {
        String[] lines = text.split("\\r?\\n");
        if (lines.length <= 3) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        int consecutiveCount = 0;
        String firstAbbrevLine = null;
        String lastAbbrevLine = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith(prefix)) {
                if (consecutiveCount == 0) {
                    firstAbbrevLine = line;
                }
                lastAbbrevLine = line;
                consecutiveCount++;
            } else {
                if (consecutiveCount > 0) {
                    flushAbbreviation(sb, firstAbbrevLine, lastAbbrevLine, consecutiveCount);
                    consecutiveCount = 0;
                }
                sb.append(line).append(System.lineSeparator());
            }
        }
        if (consecutiveCount > 0) {
            flushAbbreviation(sb, firstAbbrevLine, lastAbbrevLine, consecutiveCount);
        }

        String result = sb.toString();
        if (result.endsWith(System.lineSeparator()) && !text.endsWith(System.lineSeparator())) {
            result = result.substring(0, result.length() - System.lineSeparator().length());
        }
        return result;
    }

    private void flushAbbreviation(StringBuilder sb, String firstLine, String lastLine, int count) {
        if (count == 1) {
            sb.append(firstLine).append(System.lineSeparator());
        } else if (count == 2) {
            sb.append(firstLine).append(System.lineSeparator());
            sb.append(lastLine).append(System.lineSeparator());
        } else {
            sb.append(firstLine).append(System.lineSeparator());
            sb.append("...").append(System.lineSeparator());
            sb.append(lastLine).append(System.lineSeparator());
        }
    }

    private boolean containsAnyKeyword(String text, String[] keywords) {
        if (keywords == null || keywords.length == 0) {
            return true;
        }
        for (String kw : keywords) {
            if (kw == null || kw.isEmpty()) {
                continue;
            }
            Pattern p = Pattern.compile(kw, Pattern.CASE_INSENSITIVE);
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
