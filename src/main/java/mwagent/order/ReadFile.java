package mwagent.order;

import static mwagent.common.Config.getConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

import org.json.simple.JSONObject;
import mwagent.vo.ResultVO;


public abstract class ReadFile extends Order {

	public ReadFile(JSONObject command) {
		super(command);
	}

	public int execute() {
		
		try {
			
			resultVo = getContent(getFileFullName());
			
		}catch (Exception e) {
			getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);    		
		}
		return 1;
	}

    private ResultVO getContent(String file_full_name) {
    	
    	String result = "";
    	String result_hash = "";
    	
    	ResultVO rv = new ResultVO();

    	rv.setOk(false);
		rv.setHostName(commandVo.getHostName());
		rv.setTargetFilePath(commandVo.getTargetFilePath());
		rv.setTargetFileName(getFileName());
    	
    	File f = new File(file_full_name);
    	try {
    		
    		BufferedReader in = new BufferedReader(
    			new InputStreamReader(
    					new FileInputStream(f), "UTF8"));
    		StringBuilder sb = new StringBuilder();
    		String line = null;
    		String ls = System.getProperty("line.separator");
    		
    		while((line=in.readLine())!=null){
    			sb.append(line);
    			sb.append(ls);
    		}
    		
    		result = sb.toString();
    		
    		result_hash = getHash(result);
			
    		//Hash값이 존재하는 경우 이전 결과와 동일한지 Check 
    		if(!commandVo.getResultHash().isEmpty()){
    			if(commandVo.getResultHash().equals(result_hash)){
    				result = "NO CHANGE";
    			}
    		}
    		
    		getConfig().getLogger().fine(result);
    		in.close();
    		rv.setOk(true);
    		
    	}catch(UnsupportedEncodingException e){
    		getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);    		
    		result = "Error:UnsupportedEncodingException";
    	}catch(FileNotFoundException e){
    		getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);    		
    		result = "Error:FileNotFoundException";
    	}catch(IOException e){
    		getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);    		
    		result = "Error:IOException";
    	}catch(NoSuchAlgorithmException e){
    		getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);    		
    		result = "Error:NoSuchAlgorithmException";
    	}
    	
    	rv.setResult(result);
    	rv.setResultHash(result_hash);    	
    	
    	return rv;
    	
    }
    
	protected abstract String getFileFullName();
	
	protected abstract String getFileName();	

}
