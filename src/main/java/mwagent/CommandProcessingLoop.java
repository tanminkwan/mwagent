package mwagent;

import static mwagent.common.Config.getConfig;

import java.util.logging.Level;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import mwagent.common.LogSafe;
import mwagent.common.Common;
import mwagent.vo.MwResponseVO;
import mwagent.vo.RawCommandsVO;

/**
 * Agent 메인 명령 처리 루프
 * - 서버에서 명령 조회
 * - 명령 실행 스레드 생성
 * - 토큰 갱신 처리
 */
public class CommandProcessingLoop {

	private final ExecutorService executorService = Executors.newCachedThreadPool();

	/**
	 * Agent 메인 루프 실행
	 * - 주기적으로 서버에서 명령 조회
	 * - 각 명령을 별도 스레드에서 실행
	 *
	 * @return 종료 코드 (-1: 에러로 인한 종료)
	 */
	public long execute(){

		RawCommandsVO rcv = new RawCommandsVO();

        while (true) {

            try {

		    	rcv = fetchPendingCommands();

		    	// Access Token Expired
	    		if(rcv.getReturnCode()==0){
	    			getConfig().getLogger().info("Access token expired (401). Attempting to update token...");
	    			Common.updateToken();

	    		}else if(rcv.getReturnCode()>0){

		    	    for(Object commandObj : rcv.getCommands()){

		    	        JSONObject command_ = (JSONObject) commandObj;

		    	        String command_class  = (String)command_.get("command_class");

		    	        if(command_class==null){
		    	        	getConfig().getLogger().warning("Command_class not found : "+LogSafe.safe(command_.toJSONString(), 1000));
		    	        	continue;
		    	        }

		    	        OrderCallerThread thread = new OrderCallerThread("mwagent.order."+command_class, command_);
		    	        executorService.submit(thread);
		    	    }

	    		}

	    	    Thread.sleep(getConfig().getCommand_check_cycle()*1000);

	    	} catch (InterruptedException e) {
	    		getConfig().getLogger().log(Level.SEVERE, "shutdown by Interrupted : " + e.getMessage(), e);
	    	    return -1;
	    	} catch (Exception e) {
	    		getConfig().getLogger().log(Level.SEVERE, e.getMessage(), e);
	    	    return -1;
	    	}

        }

	}

	/**
	 * 서버에서 대기 중인 명령 조회
	 *
	 * @return RawCommandsVO 명령 목록과 상태 코드
	 */
    private RawCommandsVO fetchPendingCommands() {

    	JSONArray commands = new JSONArray();

    	RawCommandsVO rcv = new RawCommandsVO();
    	rcv.setReturnCode(1);

        String path = getConfig().getGet_command_uri() + "/" + getConfig().getAgent_id();

		getConfig().getLogger().fine("getCommands : "+path);
		MwResponseVO mrvo = Common.httpGET(path, getConfig().getAccess_token());

	    // Access Token Expired
	    if(mrvo.getStatusCode()==401){

	    	rcv.setReturnCode(0);

	    }else if(mrvo.getStatusCode()>=200 && mrvo.getStatusCode()<300){

		    long rtn = (Long)mrvo.getResponse().get("return_code");

		    if (rtn < 0){
		    	// Agent 등록 필요 등
		    	rcv.setReturnCode(rtn);
		    }else{

		        commands = (JSONArray)mrvo.getResponse().get("data");
		        rcv.setCommands(commands);

		    }

        }else if(mrvo.getStatusCode()<0){
        	rcv.setReturnCode(mrvo.getStatusCode());
        }else{
        	rcv.setReturnCode(-10);
        }

		return rcv;

	}

}
