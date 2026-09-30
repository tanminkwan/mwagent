package mwagent.order;

import static mwagent.common.Config.getConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.FileWriter;
import java.util.logging.Level;
import java.util.UUID;
import org.json.simple.JSONObject;
import mwagent.common.Common;
import mwagent.vo.ResultVO;

/**
 * Executes a script provided as a raw text string.
 * It creates a temporary script file, executes it, and then deletes it.
 * 
 * Supported additional_params (String):
 * - The actual content of the script (e.g., shell script or batch file).
 * 
 * Example additional_params:
 * "#!/bin/bash\necho 'hello world'\nls -l"
 */
public class ExeText extends Order {
    public ExeText(JSONObject command) {
        super(command);
    }
    
    public int execute() {
        try {
            resultVo = runScript();
        } catch (Exception e) {
            getConfig().getLogger().log(Level.SEVERE, e.getMessage(), e);
        }
        return 1;
    }
    
    private ResultVO runScript() {
        String result = "";
        String tempDir = System.getProperty("java.io.tmpdir");
        if (!tempDir.endsWith(File.separator)) {
            tempDir += File.separator;
        }
        
        ResultVO rv = new ResultVO();
        rv.setOk(false);
        rv.setHostName(commandVo.getHostName());
		rv.setTargetFileName(commandVo.getTargetFileName());
        
        File tempScriptFile = null;
        
        try {
            // Get script content from the additionalParams
            String scriptContent = commandVo.getAdditionalParams();
            
            // Create a temporary script file with unique name
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String osName = System.getProperty("os.name").toLowerCase();
            
            String fileExtension;
            String[] executeCommand;
            
            // Determine OS and set appropriate file extension and execution command
            if (osName.contains("windows")) {
                fileExtension = ".bat";
                executeCommand = new String[]{Common.windowsCmd(), "/c"};
            } else if (osName.contains("aix")) {
                fileExtension = ".sh";
                executeCommand = new String[]{"/bin/ksh"};
            } else {
                // Linux and other Unix-like systems
                fileExtension = ".sh";
                executeCommand = new String[]{"/bin/bash"};
            }
            
            String tempScriptFileName = "script_" + uniqueId + fileExtension;
            tempScriptFile = new File(tempDir + tempScriptFileName);
            
            // Write the script content to the temporary file
            try (FileWriter writer = new FileWriter(tempScriptFile)) {
                // For Windows batch files, use Windows line endings
                if (osName.contains("windows")) {
                    scriptContent = scriptContent.replace("\n", "\r\n");
                }
                writer.write(scriptContent);
            }
            
            getConfig().getLogger().info("Created temporary script at: " + tempScriptFile.getAbsolutePath());
            
            // Make the script executable for Unix-like systems
            if (!osName.contains("windows")) {
                tempScriptFile.setExecutable(true);
            }
            
            // Create the command array with the script path as the last element
            String[] commandArray = new String[executeCommand.length + 1];
            System.arraycopy(executeCommand, 0, commandArray, 0, executeCommand.length);
            commandArray[executeCommand.length] = tempScriptFile.getAbsolutePath();
            
            // Execute the temporary script
            ProcessBuilder pb = new ProcessBuilder(commandArray);
            Process proc = pb.start();
            
            // Capture standard output
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF8"));
            
            // Capture error output
            BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream(), "UTF8"));
            
            String line;
            StringBuilder sb = new StringBuilder();
            String ls = System.getProperty("line.separator");
            
            // Read standard output
            while ((line = stdInput.readLine()) != null) {
                sb.append(line);
                sb.append(ls);
            }
            
            // Read error output
            while ((line = stdError.readLine()) != null) {
                sb.append("ERROR: ");
                sb.append(line);
                sb.append(ls);
            }
            
            // Wait for process to complete and get exit code
            int exitCode = proc.waitFor();
            result = sb.toString();
            sb.append("Exit Code: ").append(exitCode).append(ls);
            
            rv.setOk(exitCode == 0);
            getConfig().getLogger().info("ExeText result: " + result);
            
            // Clean up
            stdInput.close();
            stdError.close();
            
        } catch (UnsupportedEncodingException e) {
        	getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
            result = "Error:UnsupportedEncodingException";
        } catch (FileNotFoundException e) {
        	getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
            result = "Error:FileNotFoundException";
        } catch (IOException e) {
        	getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
            result = "Error:IOException";
        } catch (InterruptedException e) {
        	getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
            Thread.currentThread().interrupt();
            result = "Error:InterruptedException";
        } finally {
            // Clean up the temporary file
            if (tempScriptFile != null && tempScriptFile.exists()) {
                try {
                    tempScriptFile.delete();
                    getConfig().getLogger().info("Deleted temporary script: " + tempScriptFile.getAbsolutePath());
                } catch (Exception e) {
                	getConfig().getLogger().log(Level.WARNING, "Failed to delete temporary script: " + e.getMessage(), e);
                }
            }
        }
        
        rv.setResult(result);
        return rv;
    }
}
