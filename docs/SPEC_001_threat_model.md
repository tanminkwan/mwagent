# SPEC-001: mwagent 위협 모델

> 작성: 2026-09-30 (PLAN-001 WS-3-5)
> 대상: mwagent `main` (Kafka 제거 이후)

이 문서는 agent 가 **무엇을 믿고, 무엇을 막는지** 적는다. SAST 도구가 보고한 항목 중 "설계상 허용"으로
닫는 것들의 근거도 여기에 둔다.

## 1. agent 가 하는 일

- 중앙 서버(mwm-app)에 등록하고, 주기적으로 명령을 가져와(REST 폴링) 실행한 뒤 결과를 REST 로 보낸다.
- 선택적으로 MQTT 브로커를 구독해 명령을 즉시 받는다 (`mqtt_enabled=true`). MQTT 는 **수신 전용**이다.
- 명령에는 **셸 스크립트 실행, 파일 읽기, 파일 다운로드·압축 해제, 설정 파일 변경**이 포함된다.
  즉 서버는 agent 를 실행한 OS 계정 권한으로 임의 작업을 시킬 수 있다. 이것이 agent 의 본래 목적이다.

## 2. 보호 대상

| 자산 | 위치 | 노출 시 영향 |
|------|------|--------------|
| agent 를 실행한 OS 계정의 권한 | 호스트 | 명령 실행 = 그 계정으로 할 수 있는 모든 일 |
| refresh token / access token | `agent.properties`, 메모리 | 서버에 agent 로 위장 |
| mTLS 클라이언트 키 | `client.keystore.path` | 서버에 agent 로 위장 |
| MQTT 비밀번호 | `agent.properties` (`mqtt_credential`) | 브로커 접속 (구독 권한) |
| 명령 결과 | REST 전송, 로그 | 설정 파일·로그 원문 등 운영 정보 |

## 3. 신뢰 경계

| 대상 | 신뢰 | 근거 / 조건 |
|------|------|-------------|
| **mwm-app 서버** | **완전 신뢰** | 명령을 내리는 주체다. 서버가 침해되면 모든 agent 가 침해된다. agent 쪽에서 막을 수 없다 |
| 서버와의 네트워크 구간 | TLS 설정에 달림 | `ssl_verify=false`(기본)면 **중간자가 명령을 주입할 수 있다**. 운영에서는 `ssl_verify=true` 를 권장한다 (§5) |
| **MQTT 브로커** | **완전 신뢰** | `cmd/{agent_id}/req` 에 publish 할 수 있는 누구든 명령을 실행시킬 수 있다. 브로커 ACL 과 TLS(`ssl://`) 가 방어선이다 |
| 로컬 파일시스템 | agent 계정 권한 | `agent.properties`, keystore 는 agent 계정만 읽을 수 있어야 한다 (권한 600) |
| 명령 대상 원격 서버 (`get_ssl_certi`) | 신뢰 안 함 | 인증서만 읽는다. 데이터를 보내지 않는다 (§4.2) |

명령에는 서명이 없다. 서버 인증(TLS)과 브로커 ACL 이 "누가 명령을 보냈는가"를 보장하는 유일한 수단이다.

## 4. 설계상 허용하는 것

도구가 취약점으로 보고하지만 기능 자체이므로 고치지 않는 항목이다.

### 4.1 명령 실행 (SAST: `COMMAND_INJECTION`, PLAN C-7)

`ExeShell`, `ExeText`, `ExeScript`, `DownloadFile`(다운로드 후 실행)은 서버가 준 스크립트·명령을 실행한다.
서버를 완전 신뢰하므로(§3) 이것은 주입이 아니라 기능이다.

- 경계는 **OS 계정 권한**이다. agent 는 필요한 최소 권한 계정으로 실행해야 하며 root/Administrator 는 권장하지 않는다.
- `security.command_injection_check=true` 로 셸 메타문자를 막을 수 있지만, 이는 실수 방지용이지 보안 경계가 아니다.
- 예외: `DownloadFile.applyChmod` 는 인자 배열로 호출하고 mode 는 8진수만 받는다 (서버 값이라도 chmod 옵션·추가 경로로 해석되지 않게).

### 4.2 `get_ssl_certi` 의 trust-all (SAST: `WEAK_TRUST_MANAGER`, PLAN C-6)

`SSLCertiFunc` 는 지정한 서버의 **인증서를 조회**하는 기능이다. 만료·자체 서명 인증서도 조회해야 하므로 검증하지 않는다.

- 이 연결로는 핸드셰이크만 하고 데이터를 보내지 않는다. 결과는 인증서 메타데이터(주체, 발급자, 기간, 일련번호)뿐이다.
- 서버 API 통신(§5)과는 별개의 연결이며, 여기서의 trust-all 이 서버 통신에 영향을 주지 않는다.

### 4.3 파일 읽기 명령의 경로 (SAST: `PATH_TRAVERSAL_IN`)

`ReadPlainFile`, `ExtractLog`, `get_ssl_certifile` 은 서버가 지정한 경로의 파일을 읽는다. 허용 디렉터리 제한이 없다.

- 서버는 명령 실행(§4.1)으로 어차피 어떤 파일이든 읽을 수 있으므로, 여기에 제한을 두어도 보안 경계가 되지 않는다.
- `ReadFullPathFile` 의 허용 목록(`security.allowed_read_paths`)과 `security.path_traversal_check` 는 **실수 방지**용이다.
- 반면 **서버 응답에서 온 값**(다운로드 파일명, zip 항목 이름)은 신뢰하지 않고 검사한다 (§5). 서버가 아닌 다른 파일 제공자(presigned URL 의 저장소 등)가 개입할 수 있기 때문이다.

## 5. 구현된 통제

| 위협 | 통제 | 위치 |
|------|------|------|
| 서버 사칭·중간자 | `ssl_verify=true` 면 인증서 체인(`truststore.path` 또는 JVM cacerts)과 호스트명을 검증. 검증 모드에서 truststore 를 못 읽으면 trust-all 로 물러서지 않고 실패 | `TlsSupport` |
| 압축 파일로 대상 밖에 쓰기 (Zip Slip) | 항목 경로를 정규화해 대상 디렉터리 안인지 확인, 벗어나면 거부 | `SecurityValidator.resolveZipEntry` |
| 다운로드 파일명으로 경로 이탈 | 서버 응답(Content-Disposition, URL)에서 온 파일명에 경로가 있으면 거부 | `Common.httpFileDownload`, `DownloadNUnzipFunc`, `ApacheHttpClientAdapter` |
| 다운로드 URL 로 로컬 파일 복사 | `download_n_unzip` 은 http/https 만 허용 (`file:`, `jar:`, `ftp:` 거부) | `DownloadNUnzipFunc.isAllowedDownloadUrl` |
| 임의 클래스 실행 | `command_class` 는 `mwagent.order` 바로 아래의 구체 `Order` 하위 클래스만 실행. 초기화 전에 검사 | `OrderCaller.resolveOrderClass` |
| MQTT 로 큰 메시지 주입 | payload 1 MiB 상한, JSON 객체가 아니면 거부, `cmdId` 로 중복 실행 방지 | `MwMqttSubscriber.dispatch` |
| JNDI·ObjectName 조작 | `jmx_target`, `jmx_domain` 은 `[A-Za-z0-9_.-]` 만 허용 | `JmxStatFunc` |
| URL 경로 조작 | agent_id·버전·타입·파일명을 경로 세그먼트로 인코딩 | `Common.encodePathSegment` |
| 비밀값 로그 노출 | 응답 본문·명령 결과는 원문을 남기지 않음. 명령 JSON 은 token/password/credential/secret 값, URL 서명, JWT 를 가림 | `LogSafe` |
| 로그 위조 (CR/LF) | 파일 로그의 메시지 안 개행을 이스케이프 (모든 호출 지점에 일괄 적용) | `SafeLogFormatter` |
| 원격 설정 변경으로 token 조작 | `set_properties` 는 `token` 을 바꾸거나 조회할 수 없다 | `SetPropertiesFunc` |
| 로케일에 따른 비교 오류 | 대소문자 변환은 `Locale.ROOT` | 전체 |

## 6. 남은 위험과 권고

| 위험 | 권고 |
|------|------|
| `ssl_verify` 기본값이 `false` 라 기본 설치는 중간자에 취약하다 (기존 설치 호환 때문) | 운영에서는 `ssl_verify=true` 와 사설 CA truststore 를 쓴다 |
| MQTT 를 `tcp://` 로 쓰면 비밀번호와 명령이 평문이다 | `ssl://` 를 쓰고, 브로커 ACL 로 `cmd/{agent_id}/req` publish 권한을 서버에만 준다 |
| 명령 서명이 없다 | 서버·브로커 계정 관리가 곧 agent 보안이다 |
| 콘솔(stderr) 로그 핸들러는 `SafeLogFormatter` 를 쓰지 않는다 | 데몬 실행 시 stdout/stderr 를 파일로 남기지 않거나, 남긴다면 같은 주의가 필요하다 |
| 결과 전송은 재시도하지 않는다 (설계 결정) | 결과 유실 가능성을 운영 절차로 감수한다 |
| agent 계정 권한이 곧 피해 범위다 | 최소 권한 계정으로 실행하고 `agent.properties`·keystore 권한을 600 으로 둔다 |

## 7. 보고 경로

취약점은 공개 이슈가 아닌 비공개 경로로 보고한다 (SECURITY.md, PLAN-001 WS-6 에서 작성 예정).
