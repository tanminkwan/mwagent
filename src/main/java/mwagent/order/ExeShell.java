package mwagent.order;

import static mwagent.common.Config.getConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;

import org.json.simple.JSONObject;
import mwagent.common.Common;
import mwagent.common.SecurityValidator;
import mwagent.vo.ResultVO;


/**
 * Executes a shell command or script.
 * 
 * Supported additional_params (String):
 * - Space-separated arguments to be passed to the command or script.
 * 
 * Example additional_params:
 * "-al /home/user"
 */
public class ExeShell extends Order {

	public ExeShell(JSONObject command) {
		super(command);
	}

	public int execute() {

		try {

			resultVo = runShell();

		}catch (Exception e) {
			getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
		}
		return 1;
	}

    private ResultVO runShell() {

    	String result = "";
    	String baseDir = System.getProperty("user.dir");
    	String targetFilePath = commandVo.getTargetFilePath();
    	String targetFileName = commandVo.getTargetFileName();
    	String additionalParams = commandVo.getAdditionalParams();

    	ResultVO rv = new ResultVO();

    	rv.setOk(false);
		rv.setHostName(commandVo.getHostName());
		rv.setTargetFilePath(targetFilePath);
		rv.setTargetFileName(targetFileName);

		// Security validation: check path traversal (configurable, default ON)
		if (getConfig().isSecurityPathTraversalCheck()) {
			if (!SecurityValidator.isValidPath(baseDir, targetFilePath)) {
				getConfig().getLogger().severe("Security: Path traversal detected in targetFilePath: " + targetFilePath);
				rv.setResult("Error:SecurityException - Invalid path");
				return rv;
			}

			// Security validation: check filename
			if (!SecurityValidator.isValidFilename(targetFileName)) {
				getConfig().getLogger().severe("Security: Invalid filename: " + targetFileName);
				rv.setResult("Error:SecurityException - Invalid filename");
				return rv;
			}
		}

		// Security validation: check additional params for command injection (configurable, default OFF)
		if (getConfig().isSecurityCommandInjectionCheck()) {
			if (!SecurityValidator.isValidCommandParam(additionalParams)) {
				getConfig().getLogger().severe("Security: Command injection detected in params: " + additionalParams);
				rv.setResult("Error:SecurityException - Invalid parameters");
				return rv;
			}
		}

		String currentPath;
		if (getConfig().isSecurityPathTraversalCheck()) {
			try {
				currentPath = SecurityValidator.getValidatedPath(baseDir, targetFilePath);
				if (!currentPath.endsWith(File.separator)) {
					currentPath += File.separator;
				}
			} catch (SecurityException e) {
				getConfig().getLogger().severe("Security: " + e.getMessage());
				rv.setResult("Error:SecurityException - " + e.getMessage());
				return rv;
			}
		} else {
			currentPath = baseDir + File.separator + targetFilePath;
			if (!currentPath.endsWith(File.separator)) {
				currentPath += File.separator;
			}
		}

		rv.setTargetFilePath(currentPath);

    	String command_t = currentPath + targetFileName;
		
		// Add target existence check and logging
		getConfig().getLogger().info("Target verification - currentPath: [" + currentPath + "], targetFileName: [" + targetFileName + "]");
		File targetFileCheck = new File(currentPath, targetFileName);
		getConfig().getLogger().info("Target file absolute path: [" + targetFileCheck.getAbsolutePath() + "]");
		getConfig().getLogger().info("Target file exists: [" + targetFileCheck.exists() + "], isFile: [" + targetFileCheck.isFile() + "], canExecute: [" + targetFileCheck.canExecute() + "]");
		
    	String command_s = "";

    	if (getConfig().getOs().equals("WIN")){
    		command_s = "cmd /c start";
    	}else if (getConfig().getOs().equals("LINUX")){
    		command_s = "bash -c command";
    	}else if (getConfig().getOs().equals("AIX")){
    		command_s = "ksh -c command";
    	}else if (getConfig().getOs().equals("HPUX")){
    		command_s = "ksh -c command";
    	}

    	String[] command = null;

    	if (getConfig().getOs().equals("WIN")){

    		command_s += " " + command_t;

        	if(additionalParams.length() > 0){
        		command_s += " " + additionalParams;
        	}

        	command = command_s.split("\\s+");
        	command[0] = Common.windowsCmd(); // absolute path; kept as one element even if it has spaces

    	}else{

        	command = command_s.split("\\s+");

	    	if(additionalParams.length() > 0){
	    		command_t += " " + additionalParams;
	    	}

	    	command[command.length - 1] = command_t;

    	}

    	for(String s : command){
    		getConfig().getLogger().fine("commandline: "+s);
    	}

		StringBuilder cmdLog = new StringBuilder("Executing shell command: ");
		for (String s : command) cmdLog.append("[").append(s).append("] ");
		getConfig().getLogger().info(cmdLog.toString());

    	try {

    		Runtime rt = Runtime.getRuntime();
    		rt.exec(command);

    		result = "Command is executed.";
    		rv.setOk(true);

    	}catch(UnsupportedEncodingException e){
    		getConfig().getLogger().log(Level.SEVERE, e.getMessage(), e);
    		result = "Error:UnsupportedEncodingException";
    	}catch(FileNotFoundException e){
    		getConfig().getLogger().log(Level.SEVERE, e.getMessage(), e);
    		result = "Error:FileNotFoundException";
    	}catch(IOException e){
    		getConfig().getLogger().log(Level.SEVERE, e.getMessage(), e);
    		result = "Error:IOException";
    	}

    	rv.setResult(result);
    	return rv;
    }
}
