package mwagent.service.registration;

import static mwagent.common.Config.getConfig;

import java.util.logging.Logger;

import org.json.simple.JSONArray;

import mwagent.common.Common;
import mwagent.vo.AgentStatus;
import mwagent.vo.MwResponseVO;
import mwagent.vo.RawCommandsVO;

/**
 * Agent 상태 확인 및 BOOT 명령 조회를 담당하는 서비스
 * PreWork.noticeStart() 로직을 모듈화
 */
public class AgentStatusService {

    private final Logger logger;

    public AgentStatusService() {
        this.logger = getConfig().getLogger();
    }

    /**
     * Agent 시작을 서버에 알리고 상태 및 초기 명령을 조회
     *
     * @return RawCommandsVO (상태 코드 + 초기 명령 목록)
     */
    public RawCommandsVO noticeStartAndGetStatus() {

        JSONArray commands = new JSONArray();

        RawCommandsVO rcv = new RawCommandsVO();
        rcv.setReturnCode(1);

        // API 경로 구성
        String path = getConfig().getGet_command_uri()
                + "/" + Common.encodePathSegment(getConfig().getAgent_id())
                + "/" + Common.encodePathSegment(getConfig().getAgent_version())
                + "/" + Common.encodePathSegment(getConfig().getAgent_type())
                + "/BOOT";

        logger.fine("Noticing start: " + path);

        // HTTP GET 요청
        MwResponseVO mrvo = Common.httpGET(path, getConfig().getAccess_token());

        // 응답 처리
        if (mrvo.getStatusCode() >= 200 && mrvo.getStatusCode() < 300) {

            long rtn = (Long) mrvo.getResponse().get("return_code");

            if (rtn < 0) {
                // Agent 등록 필요 또는 승인 대기
                logger.info("Agent status: " + AgentStatus.fromReturnCode(rtn).getDescription());
                rcv.setReturnCode(rtn);
            } else {
                logger.info("Agent approved and ready");
            }

            commands = (JSONArray) mrvo.getResponse().get("data");
            rcv.setCommands(commands);

        } else {
            logger.severe("Notice start error: " + mrvo.getStatusCode());
            rcv.setReturnCode(-10);
        }

        return rcv;
    }

    /**
     * RawCommandsVO로부터 AgentStatus 추출
     *
     * @param rcv RawCommandsVO
     * @return AgentStatus
     */
    public AgentStatus getAgentStatus(RawCommandsVO rcv) {
        return AgentStatus.fromReturnCode(rcv.getReturnCode());
    }
}
