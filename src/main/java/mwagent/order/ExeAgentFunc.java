package mwagent.order;

import static mwagent.common.Config.getConfig;

import java.util.logging.Level;

import org.json.simple.JSONObject;

import mwagent.common.LogSafe;
import mwagent.agentfunction.AgentFunc;
import mwagent.agentfunction.AgentFuncFactory;

public class ExeAgentFunc extends Order {

	public ExeAgentFunc(JSONObject command) {
		super(command);
	}

	public int execute() {

		int rtn = 1;
		
		getConfig().getLogger().info("commandVo : "+LogSafe.safe(commandVo.toString(), 1000));
		AgentFunc func = AgentFuncFactory.getAgentFunc(commandVo.getTargetFileName());
		
		try{
			
			resultVos = func.exeCommand(commandVo);
			
		}catch (Exception e) {
			getConfig().getLogger().log(Level.SEVERE, e.getMessage(), e);
    		rtn = -1;
		}				

		return rtn;
	}


}
