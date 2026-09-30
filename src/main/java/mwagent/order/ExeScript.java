package mwagent.order;

import static mwagent.common.Config.getConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;

import org.json.simple.JSONObject;
import mwagent.common.SecurityValidator;
import mwagent.vo.ResultVO;

/**
 * Executes a local script file.
 * 
 * Supported additional_params (String):
 * - Space-separated arguments to be passed to the script.
 * 
 * Example additional_params:
 * "arg1 arg2 --verbose"
 */
public class ExeScript extends Order {

	public ExeScript(JSONObject command) {
		super(command);
	}

	public int execute() {

		try {

			resultVo = runScript();

		}catch (Exception e) {
			getConfig().getLogger().log(Level.SEVERE, e.getMessage(), e);
		}
		return 1;
	}

    private ResultVO runScript() {

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

    	String scriptPath = currentPath + targetFileName;
    	String[] commands;

    	if (additionalParams != null && additionalParams.length() > 0) {
    		String[] params = additionalParams.split("\\s+");
    		commands = new String[params.length + 1];
    		commands[0] = scriptPath;
    		System.arraycopy(params, 0, commands, 1, params.length);
    	} else {
    		commands = new String[]{scriptPath};
    	}

    	try {

    		ProcessBuilder pb = new ProcessBuilder(commands);
    		Process proc = pb.start();

    		BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF8"));

    		String line = null;
    		String ls = System.getProperty("line.separator");
    		StringBuilder sb = new StringBuilder();

    		while((line=in.readLine())!=null){
    			sb.append(line);
    			sb.append(ls);
    		}

    		result = sb.toString();
        	rv.setOk(true);
        	getConfig().getLogger().info("ExeScript result : "+result);
    		in.close();

    	}catch(UnsupportedEncodingException e){

    		getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
    		result = "Error:UnsupportedEncodingException";

    	}catch(FileNotFoundException e){

    		getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
    		result = "Error:FileNotFoundException " + scriptPath;

    	}catch(IOException e){

    		getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
    		result = "Error:IOException";

    	}

    	rv.setResult(result);

    	return rv;

    }

}
