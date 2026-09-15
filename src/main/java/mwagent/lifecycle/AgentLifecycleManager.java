package mwagent.lifecycle;

import static mwagent.common.Config.getConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import mwagent.PreWork;
import mwagent.common.Common;
import mwagent.service.CommandExecutorService;
import mwagent.service.KafkaService;
import mwagent.mqtt.MqttService;
import mwagent.service.registration.BootstrapService;
import mwagent.vo.MwResponseVO;
import mwagent.vo.RawCommandsVO;

/**
 * Agent 전체 생명주기를 관리하는 매니저
 *
 * Phase 1: Bootstrap (등록 및 승인)
 * Phase 2: Initialization (Kafka, Executor 초기화)
 * Phase 3: Runtime (명령 polling 및 실행)
 * Phase 4: Shutdown (Graceful 종료)
 */
public class AgentLifecycleManager implements AgentLifecycle {

    private final Logger logger;
    private LifecycleState state;

    // Services
    private final BootstrapService bootstrapService;
    private final KafkaService kafkaService;
    private final MqttService mqttService;
    private final CommandExecutorService commandExecutor;
    private final GracefulShutdownHandler shutdownHandler;

    // Runtime state
    private Thread runtimeThread;
    private volatile boolean running;

    public AgentLifecycleManager() {
        this(new BootstrapService(), new KafkaService(), new CommandExecutorService(), new MqttService());
    }

    /**
     * Constructor for dependency injection (테스트 용이성)
     */
    public AgentLifecycleManager(BootstrapService bootstrapService,
                                 KafkaService kafkaService,
                                 CommandExecutorService commandExecutor) {
        this(bootstrapService, kafkaService, commandExecutor, new MqttService());
    }

    /**
     * Constructor for dependency injection (MQTT 포함)
     */
    public AgentLifecycleManager(BootstrapService bootstrapService,
                                 KafkaService kafkaService,
                                 CommandExecutorService commandExecutor,
                                 MqttService mqttService) {
        this.logger = getConfig().getLogger();
        this.state = LifecycleState.CREATED;
        this.bootstrapService = bootstrapService;
        this.kafkaService = kafkaService;
        this.commandExecutor = commandExecutor;
        this.mqttService = mqttService;
        this.shutdownHandler = new GracefulShutdownHandler();
        this.running = false;
    }

    @Override
    public void start() throws Exception {
        if (!state.canTransitionTo(LifecycleState.STARTING)) {
            throw new IllegalStateException("Cannot start from state: " + state);
        }

        logger.info("========================================");
        logger.info("Starting MwAgent Lifecycle");
        logger.info("========================================");
        state = LifecycleState.STARTING;

        try {
            // Phase 1: Bootstrap (등록 및 승인)
            logger.info("Phase 1: Bootstrap - Agent registration");
            RawCommandsVO bootCommands = bootstrapService.executeBootstrapProcess();

            if (bootCommands.getReturnCode() < 0) {
                throw new Exception("Bootstrap failed with return code: " + bootCommands.getReturnCode());
            }

            // Phase 2: Initialization (FirstWork 로직)
            logger.info("Phase 2: Initialization - Processing BOOT commands");
            processBootCommands(bootCommands.getCommands());

            // Start Kafka Service
            if (kafkaService.isConfigured()) {
                kafkaService.start();
                shutdownHandler.registerService(kafkaService);
            } else {
                logger.info("Kafka not configured, skipping Kafka service");
            }

            // Start MQTT Service (Kafka 와 병행. 한쪽 실패가 다른 쪽을 막지 않는다)
            configureMqttFromProperties();
            if (mqttService.isConfigured()) {
                try {
                    mqttService.start();
                    shutdownHandler.registerService(mqttService);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "MQTT service failed to start. Continuing without MQTT.", e);
                }
            } else if (!getConfig().isMqtt_enabled()) {
                logger.info("MQTT disabled (mqtt_enabled=false), subscriber not started");
            } else {
                logger.info("MQTT broker address not configured, subscriber not started");
            }

            // Start Command Executor
            commandExecutor.start();
            shutdownHandler.registerService(commandExecutor);

            // Phase 3: Runtime (MainWork 로직)
            logger.info("Phase 3: Runtime - Starting command polling");
            running = true;
            runtimeThread = new Thread(this::runtimeLoop, "AgentRuntimeLoop");
            runtimeThread.start();

            state = LifecycleState.RUNNING;
            logger.info("========================================");
            logger.info("MwAgent started successfully");
            logger.info("========================================");

        } catch (Exception e) {
            state = LifecycleState.FAILED;
            logger.log(Level.SEVERE, "Failed to start Agent", e);
            throw e;
        }
    }

    @Override
    public void stop() throws Exception {
        if (!state.canTransitionTo(LifecycleState.STOPPING)) {
            logger.warning("Cannot stop from state: " + state);
            return;
        }

        logger.info("Stopping Agent...");
        state = LifecycleState.STOPPING;

        try {
            // Stop runtime loop
            running = false;
            if (runtimeThread != null && runtimeThread.isAlive()) {
                runtimeThread.interrupt();
                runtimeThread.join(5000); // Wait max 5 seconds
            }

            // Graceful shutdown of all services
            shutdownHandler.shutdown();

            state = LifecycleState.STOPPED;
            logger.info("Agent stopped successfully");

        } catch (Exception e) {
            state = LifecycleState.FAILED;
            logger.log(Level.SEVERE, "Error stopping Agent", e);
            throw e;
        }
    }

    @Override
    public LifecycleState getState() {
        return state;
    }

    /**
     * Agent 종료를 대기합니다.
     * main() 스레드를 블로킹하여 Agent가 계속 실행되도록 함
     */
    public void awaitTermination() {
        if (runtimeThread != null) {
            try {
                runtimeThread.join();
            } catch (InterruptedException e) {
                logger.info("Main thread interrupted");
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * agent.properties 의 MQTT 설정을 MqttService 에 적용한다.
     *
     * MQTT 는 Kafka 와 달리 설정 소스가 agent.properties 하나뿐이다.
     * BOOT 명령에는 mqtt_broker_address 가 내려오지 않으므로(서버 스펙에 없다)
     * BOOT 명령 유무와 무관하게 동작해야 하며, mqtt_credential 도 properties
     * 전용이라 주소와 자격증명을 같은 소스에서 읽는 편이 설정이 갈리지 않는다.
     * mqtt_enabled=false 면 아무것도 하지 않는다.
     */
    private void configureMqttFromProperties() {
        if (!getConfig().isMqtt_enabled()) {
            return;
        }

        String mqttBroker = getConfig().getMqtt_broker_address();

        if (mqttBroker != null && !mqttBroker.isEmpty()) {
            logger.info("Configuring MQTT broker: " + mqttBroker);
            mqttService.setBrokerAddress(mqttBroker);
        }
    }

    /**
     * BOOT 명령 처리 (FirstWork 로직)
     */
    private void processBootCommands(JSONArray commands) {
        if (commands == null || commands.isEmpty()) {
            logger.info("No BOOT commands to process");
            return;
        }

        for (Object c : commands) {
            JSONObject command = (JSONObject) c;
            logger.info("Processing BOOT command: " + command.toJSONString());

            String commandClass = (String) command.get("command_class");

            if ("BOOT".equals(commandClass)) {
                String kafkaBroker = (String) command.get("kafka_broker_address");

                if (kafkaBroker != null && !kafkaBroker.isEmpty()) {
                    logger.info("Configuring Kafka broker: " + kafkaBroker);
                    kafkaService.setBrokerAddress(kafkaBroker);
                }
            }

            // Initialize mTLS client if enabled, otherwise use refresh token
            if (getConfig().isUseMtls()) {
                logger.info("mTLS enabled - initializing mTLS client");
                Common.createMtlsClient();
            } else {
                logger.info("mTLS disabled - using refresh token method");
                Common.applyRefreshToken();
            }
        }
    }

    /**
     * Runtime loop (MainWork 로직)
     */
    private void runtimeLoop() {
        logger.info("Runtime loop started");

        while (running) {
            try {
                // Poll commands from server
                RawCommandsVO rcv = pollCommands();

                // Handle token expiration - use cascading fallback strategy
                if (rcv.getReturnCode() == 0) {
                    logger.info("Access token expired, starting cascading token renewal...");

                    // Cascading strategy: refresh_token -> mTLS (if enabled)
                    int result = Common.renewAccessTokenWithFallback();

                    if (result < 0) {
                        logger.severe("All token renewal methods failed with code: " + result);
                    }

                    continue;
                }

                // Execute commands
                if (rcv.getReturnCode() > 0 && rcv.getCommands() != null) {
                    for (Object commandObj : rcv.getCommands()) {
                        JSONObject command = (JSONObject) commandObj;
                        commandExecutor.executeCommand(command);
                    }
                }

                // Sleep before next poll
                Thread.sleep(getConfig().getCommand_check_cycle() * 1000);

            } catch (InterruptedException e) {
                logger.info("Runtime loop interrupted");
                break;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in runtime loop", e);
                // Continue running despite errors
            }
        }

        logger.info("Runtime loop terminated");
    }

    /**
     * 서버에서 명령 조회 (MainWork.fetchPendingCommands() 로직)
     */
    private RawCommandsVO pollCommands() {
        RawCommandsVO rcv = new RawCommandsVO();
        rcv.setReturnCode(1);

        String path = getConfig().getGet_command_uri() + "/" + getConfig().getAgent_id();
        logger.fine("Polling commands: " + path);

        MwResponseVO mrvo = Common.httpGET(path, getConfig().getAccess_token());

        // Access Token Expired
        if (mrvo.getStatusCode() == 401) {
            rcv.setReturnCode(0);

        } else if (mrvo.getStatusCode() >= 200 && mrvo.getStatusCode() < 300) {
            long rtn = (Long) mrvo.getResponse().get("return_code");

            if (rtn < 0) {
                rcv.setReturnCode(rtn);
            } else {
                JSONArray commands = (JSONArray) mrvo.getResponse().get("data");
                rcv.setCommands(commands);
            }

        } else if (mrvo.getStatusCode() < 0) {
            rcv.setReturnCode(mrvo.getStatusCode());
        } else {
            rcv.setReturnCode(-10);
        }

        return rcv;
    }

    /**
     * Runtime 상태 확인 (테스트용)
     */
    public boolean isRuntimeLoopRunning() {
        return running && runtimeThread != null && runtimeThread.isAlive();
    }
}
