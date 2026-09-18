package mwagent.agentfunction;

import java.lang.reflect.Constructor;
import java.util.logging.Level;

import static mwagent.common.Config.getConfig;

public class AgentFuncFactory {

	public static AgentFunc getAgentFunc(String functionType){
		if (getConfig() != null && getConfig().getLogger() != null) {
			getConfig().getLogger().info("functionType : " + functionType);
		}

		if (functionType == null || functionType.isEmpty()) {
			return null;
		}

		switch(functionType){

			case "say_hello" : return new HelloFunc();
			case "get_server_stat" : return new JmxStatFunc();
			case "get_ssl_certi" : return new SSLCertiFunc();
			case "get_ssl_certifile" : return new SSLCertiFileFunc();
			case "download_n_unzip" : return new  DownloadNUnzipFunc();
			case "set_properties" : return new SetPropertiesFunc();
			default:
		    	try {
		    		Class<?> agentFunc = Class.forName(functionType);
		    		Constructor<?> constructor = agentFunc.getConstructor();
		    		return (AgentFunc)constructor.newInstance();
		    	}catch (Exception e) {
		    		if (getConfig() != null && getConfig().getLogger() != null) {
		    			getConfig().getLogger().log(Level.WARNING, e.getMessage(), e);
		    		}
		    	}
		};
		return null;
	}
}
