# MwManger Agent - Project Memory

## Critical Rules (MUST FOLLOW)

1. **DO NOT modify source code carelessly** - Always analyze thoroughly before making changes
2. **Two authentication modes must BOTH work**:
   - mTLS mode (`use_mtls=true`): Uses `/oauth2/token` endpoint with client certificate
   - Legacy mode (`use_mtls=false`): Uses `/api/v1/security/refresh` endpoint with refresh token
3. **Version managed in ONE place only**: `Version.java` (`VERSION` constant)
4. **Gradle doesn't work** in this environment (proxy/SSL issues) - Use Maven instead
5. **All logs go to file, not System.err** - This is a daemon process
6. **Git Push Protocol**: Always use **HTTPS** (`https://github.com/...`) for Git remote operations. SSH is not configured on this environment.

## Build System

### Maven (Primary - Use This)
```bash
# With proxy
HTTP_PROXY=http://70.10.15.10:8080 HTTPS_PROXY=http://70.10.15.10:8080 ./tools/apache-maven-3.9.6/bin/mvn clean test
```

### Offline Build (For deployment)
```bash
# From Git Bash on Windows - Output: build/mwagent.jar
/c/Windows/System32/cmd.exe //c "cd /d C:\GitHub\mwmanger && C:\GitHub\mwmanger\build-offline.bat"
```

### DO NOT DELETE
- `pom.xml` - Maven build file (required because Gradle doesn't work)
- `tools/apache-maven-3.9.6/` - Maven installation

## Test Execution

### Run all tests (no skips)
```bash
MTLS_INTEGRATION_TEST=true BIZ_SERVICE_INTEGRATION_TEST=true SSL_CERT_INTEGRATION_TEST=true \
HTTP_PROXY=http://70.10.15.10:8080 HTTPS_PROXY=http://70.10.15.10:8080 \
./tools/apache-maven-3.9.6/bin/mvn test
```

### Required test servers (run before integration tests)
1. `cd biz-service && python app.py` - Auth server on http://localhost:8080
2. `cd test-server && python mock_server.py --ssl` - mTLS server on https://localhost:8443

## Architecture

### Token Refresh Logic (Common.java)
- `updateToken()`: Main entry point
  - If `use_mtls=true`: calls `httpPOSTFormUrlEncoded("/oauth2/token", ...)` with `application/x-www-form-urlencoded`
  - If `use_mtls=false`: calls `httpPOST("/api/v1/security/refresh", ...)` with `application/json`

### HTTP Methods (Common.java)
- `httpPOST()`: Content-Type: application/json (for general API calls)
- `httpPOSTFormUrlEncoded()`: Content-Type: application/x-www-form-urlencoded (for OAuth2 token endpoint)

### MQTT 수신 상태 보고 — `X-Mqtt-Status` (AgentLifecycleManager.pollCommands)
- MQTT 는 **구독 전용**이다. 상태도 publish/LWT 로 보내지 않고, 주기적 명령 폴링 요청 `GET {get_command_uri}/{agent_id}` 에 헤더로 실어 보낸다
- **`mqtt_enabled=true` 일 때만** 전송한다. `false` 면 헤더를 보내지 않는다. mwm-app 은 헤더가 없으면 MQTT 비활성으로 보고 MQTT 집계에서 뺀다
- BOOT 조회와 `POST /api/v1/command/result` 에는 붙이지 않는다 (BOOT 시점엔 MQTT 기동 전)
- 형식 (mwm-app 과 합의해 확정, JSON 아님): `{state}[;since={epoch s}][;events={n}][;last_msg={epoch s}][;reason={text}]`

| state | 의미 | 필드 |
|-------|------|------|
| `connected` | 브로커 연결됨 | since(연결 시각), events, last_msg |
| `unstable` | 한 번 붙은 뒤 끊김 (Paho 자동 재접속 중) | since(끊김 시작), events, last_msg, reason |
| `never_connected` | 기동 후 한 번도 못 붙음 (60초마다 직접 재시도) | events, reason, since |
| `not_started` | 구독자 미기동 | reason = `mqtt_broker_address not set` / `start_failed` / `not_running` |

- 모든 필드는 선택값이다. 값을 모르면 생략한다 (예: 수신 이력이 없으면 `last_msg` 없음)
- `events` 는 마지막 복구 판정(재연결 후 60초 유지) 이후의 끊김·에러 횟수다. 복구되면 0 으로 리셋되므로 누적 카운터가 아니다
- `reason` 은 `;`·제어문자·비 ASCII 를 `_` 로 치환하고 최대 120자로 자른다. 값 안에 `=` 가 들어갈 수 있다
- 상태 값을 만들다 실패해도 폴링은 계속한다. 헤더만 생략한다. `MqttService.statusHeader()` 의 `not_started` 경로는 `MwMqttSubscriber` 를 건드리지 않는다 (paho jar 누락 시 `LinkageError` 방지)
- state 추가나 형식 변경은 mwm-app 파서(모르는 state 는 `unknown` 처리)와 맞춰야 한다

### Log Directory Handling (Config.java)
- If `log_dir` doesn't exist: Log error to current directory and terminate (return -3)
- Default: Current working directory

## Key Files

| File | Purpose |
|------|---------|
| `src/main/java/mwagent/common/Version.java` | **Single source of truth for version** |
| `src/main/java/mwagent/common/Config.java` | Configuration management |
| `src/main/java/mwagent/common/Common.java` | HTTP communication, token refresh |
| `agent.properties` | Runtime configuration |
| `test-server/test-agent.properties` | Test configuration |

## Agent Functions (`AgentFuncFactory`)

| functionType | 클래스 | 용도 |
|--------------|--------|------|
| `say_hello` | `HelloFunc` | 헬스 체크 |
| `get_server_stat` | `JmxStatFunc` | JMX 통계 |
| `get_ssl_certi` | `SSLCertiFunc` | SSL 인증서 조회 |
| `get_ssl_certifile` | `SSLCertiFileFunc` | 인증서 파일 읽기 |
| `download_n_unzip` | `DownloadNUnzipFunc` | 다운로드 + 압축 해제 |
| `set_properties` | `SetPropertiesFunc` | `agent.properties` 원격 변경/조회 |

### set_properties 규칙 (docs/SetProperties-Requirements.md)
- `additional_params`: `{"delete":["k1"], "upsert":[{"k2":"v2"}]}`
- **`token` 은 delete/upsert 불가** (에러) 이고 결과 JSON 에서도 **완전 제외**. 그 외 민감 항목은 모두 대상
- 검증 실패 시 **전체 거부** (부분 적용 없음), 빈 요청 `{}` 은 **조회** 동작
- 파일만 갱신하고 **재기동/hot reload 는 하지 않는다**
- 실패는 예외가 아니라 `isOk=false` ResultVO 로 반환할 것 —
  `ExeAgentFunc` 는 예외 시 `rtn=-1` 이라 `sendResults()` 가 호출되지 않는다

## Recent Fixes (2025-12)
1. OAuth2 token request now uses correct Content-Type (`application/x-www-form-urlencoded`)
2. Log directory existence check added
3. Version output at startup
4. Version management unified to Version.java (single source of truth)
5. JAR filename simplified to `mwagent.jar` (no version suffix)
6. All 215 tests passing
7. `set_properties` Agent Function 추가 (0000.0010.0001) — `agent.properties` 원격 변경/조회
8. 명령 폴링에 `X-Mqtt-Status` 헤더 추가 (0000.0010.0002) — `mqtt_enabled=true` 일 때만 MQTT 수신 상태 보고
