package mwagent;

import static mwagent.common.Config.getConfig;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import mwagent.common.LogSafe;
import mwagent.common.Common;

/**
 * Agent 초기화 단계 담당 (BOOT 명령 처리 후 refresh token 적용)
 */
public class InitializationPhase {

	/**
	 * 초기 부트 명령 실행. kafka_broker_address 가 내려와도 무시한다 (Kafka 기능 제거)
	 *
	 * @param commands 초기 명령 목록
	 * @return 실행 결과 코드 (1: 성공)
	 */
	public long execute(JSONArray commands) {

		for(Object c : commands){

			JSONObject command = (JSONObject)c;
			getConfig().getLogger().info(LogSafe.safe(command.toJSONString(), 1000));

			Common.applyRefreshToken();

		}

		return 1;

	}

}
