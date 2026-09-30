package mwagent;

import static mwagent.common.Config.getConfig;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import mwagent.common.LogSafe;
import mwagent.common.Common;
import mwagent.kafka.MwConsumerThread;
import mwagent.kafka.MwHealthCheckThread;
import mwagent.kafka.MwProducer;

/**
 * Agent 초기화 단계 담당
 * - Kafka 연결 설정
 * - Consumer/Producer 시작
 * - Health check 스레드 시작
 */
public class InitializationPhase {

	/**
	 * 초기 부트 명령 실행
	 * - Kafka broker 연결
	 * - Consumer, Producer, HealthCheck 스레드 시작
	 *
	 * @param commands 초기 명령 목록
	 * @return 실행 결과 코드 (1: 성공)
	 */
	public long execute(JSONArray commands) {

		for(Object c : commands){

			JSONObject command = (JSONObject)c;
			getConfig().getLogger().info(LogSafe.safe(command.toJSONString(), 1000));

			String command_class  = (String)command.get("command_class");

			if(command_class.equals("BOOT")){

				String kafka_broker_address = (String)command.get("kafka_broker_address");

				if(kafka_broker_address != null && !kafka_broker_address.isEmpty()){

					getConfig().getLogger().info("Kafka broker : "+ kafka_broker_address);
					getConfig().setKafka_broker_address(kafka_broker_address);

		            // Command consuming
		            MwConsumerThread consumerThread = new MwConsumerThread(getConfig().getKafka_broker_address(), "t_"+getConfig().getAgent_id());

		            consumerThread.setDaemon(true);
		            consumerThread.start();
		            consumerThread.setName("MwConsumer");

		            // Health checking
		            MwHealthCheckThread healthcheckThread = new MwHealthCheckThread(getConfig().getKafka_broker_address(), "t_agent_health");

		            healthcheckThread.setDaemon(true);
		            healthcheckThread.start();
		            healthcheckThread.setName("MwHealthCheck");

		            // Producer Activate
		            MwProducer.getInstance();

		        }

			}

			Common.applyRefreshToken();

		}

		return 1;

	}

}
