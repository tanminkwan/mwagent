# TASK 3-5~3-7: 위협 모델, MQTT payload 검증, SAST 잔여 처리

> 수행: 2026-09-30
> 상위 계획: [PLAN_001](../PLAN_001_opensource_readiness.md) WS-3-5, 3-6, 3-7
> 기준선: [TASK_3-1](TASK_3-1_baseline_sast.md) (SpotBugs 327건)

## 1. 재스캔

도구와 설정은 TASK 3-1 과 같다 (SpotBugs 4.8.6 + FindSecBugs 1.13.0, `-effort:max -low`, JDK 17).
CodeQL 은 이번에 다시 돌리지 않았다 (CI 도입 시 WS-7 에서 돌린다).

| 시점 | 전체 | 보안 분류 | 품질 분류 |
|------|------|-----------|-----------|
| 기준선 (2026-09-29, 0f5c41c) | 327 | 243 | 84 |
| 2단계 수정 후 (c1673d2) | 291 | — | — |
| 이번 작업 후 | 285 | 221 | 64 |

보안 분류 숫자가 크게 줄지 않은 것은 도구가 **중앙 처리(포매터)와 입력 검증을 추적하지 못하기** 때문이다.
아래 §3 에 분류별 처리와 근거를 적었다. CI 에서는 이 표를 근거로 suppression 한다.

## 2. 이번에 고친 것

| 항목 | 내용 | 테스트 |
|------|------|--------|
| WS-3-6 명령 클래스 제한 | `OrderCaller.resolveOrderClass`: `mwagent.order` 바로 아래의 구체 `Order` 하위 클래스만 실행. `Class.forName(.., false, ..)` 로 초기화 전에 검사. REST 폴링·Executor·MQTT 세 경로 모두 여기를 지난다 | `OrderCallerTest` |
| WS-3-6 MQTT payload | 1 MiB 상한(파싱 전), JSON 객체가 아니면 거부. 필수 필드 `command_class` 검사와 `cmdId` 중복 방지는 기존대로 | `MwMqttSubscriberTest` |
| `CRLF_INJECTION_LOGS` | `SafeLogFormatter`: 파일 핸들러의 메시지 안 CR/LF 를 이스케이프. 호출 지점 164곳을 한 번에 처리. 스택트레이스는 그대로 여러 줄 | `SafeLogFormatterTest` |
| `URLCONNECTION_SSRF_FD` | `download_n_unzip` 은 http/https 만 허용. `java.net.URL` 은 `file:` 도 열어 로컬 파일을 target_directory 로 복사할 수 있었다 | `DownloadNUnzipFuncTest` |
| `IMPROPER_UNICODE` (실제 결함 부분) | `toLowerCase()`/`toUpperCase()` 9곳을 `Locale.ROOT` 로. 터키어 로케일 JVM 에서 `".ZIP"` → `".zıp"` 가 되어 압축 해제가 건너뛰어지고 와일드카드 인증서 비교가 틀렸다 | `SSLCertiFuncTest` (tr 로케일) |

E2E(로컬 mwm-app, 2026-09-30)에서 찾아 먼저 고친 것: HTTP 실패 원인 로그, 명령 원문 로그 마스킹 (c1673d2).

## 3. 남은 보안 분류 항목의 처리

| 분류 | 건수 | 처리 | 근거 |
|------|------|------|------|
| `CRLF_INJECTION_LOGS` | 164 | **완화됨 → suppress** | `SafeLogFormatter` 가 파일 로그 전체에 적용된다. 도구는 호출 지점만 본다. 콘솔 핸들러는 적용 대상이 아니다 (SPEC-001 §6) |
| `PATH_TRAVERSAL_IN` | 28 | **설계상 허용 / 검증됨 → suppress** | ① 서버가 지정한 경로 읽기(`ReadPlainFile`, `ExtractLog`, `SSLCertiFileFunc`, `ExeShell`): 서버 완전 신뢰, SPEC-001 §4.3. ② 설정 파일 경로(`agent.properties`, keystore, truststore, log_dir): 운영자가 정한 값. ③ `SecurityValidator` 내부: 검사 함수 자체. ④ 다운로드 저장 경로: `SecurityValidator` 로 검사 후 사용 |
| `COMMAND_INJECTION` | 7 | **설계상 허용 → suppress** | 명령 실행 기능 (SPEC-001 §4.1, PLAN C-7). `applyChmod` 는 인자 배열 + 8진수 검증 |
| `IMPROPER_UNICODE` | 9 | **오탐 → suppress** | `equalsIgnoreCase` 는 ASCII 상수(`"AIX"`, `"token"`) 비교이고, `toLowerCase` 는 `Locale.ROOT` 로 고쳤다. 도구는 호출 자체를 보고한다 |
| `HTTP_PARAMETER_POLLUTION` | 4 | **검증됨 → suppress** | URL 을 만드는 쪽에서 `Common.encodePathSegment` 로 인코딩한다 (SAST 7순위, 3e789ff). 보고 지점은 완성된 URL 을 받는 HTTP 함수다 |
| `WEAK_TRUST_MANAGER` | 3 | **설계상 허용 → suppress** | `SSLCertiFunc` 인증서 조회 (SPEC-001 §4.2, PLAN C-6) |
| `HARD_CODE_PASSWORD` | 2 | **오탐 → suppress** | 설정 기본값이 빈 문자열 |
| `LDAP_INJECTION` | 1 | **검증됨 → suppress** | `JmxStatFunc.isValidJmxName` 으로 `[A-Za-z0-9_.-]+` 만 허용한 뒤 lookup (3e789ff) |
| `PATH_TRAVERSAL_OUT` | 1 | **검증됨 → suppress** | `Common.httpFileDownload` 는 `SecurityValidator.isValidFilename` 통과 후에만 쓴다 (0945c9e) |
| `URLCONNECTION_SSRF_FD` | 1 | **검증됨 → suppress** | 스킴 제한(§2). 대상 호스트는 서버가 지정하는 기능이다 |
| `MODIFICATION_AFTER_VALIDATION` | 1 | **오탐 → suppress** | `LogSafe.safe` 는 검증이 아니라 출력용 변환이다 |

## 4. 품질 분류 (64건) — 이번에 하지 않음

`REC_CATCH_EXCEPTION` 10, `OS_OPEN_STREAM_EXCEPTION_PATH` 8(예외 경로의 스트림 미종료), `EI_EXPOSE_REP*` 13,
`RV_RETURN_VALUE_IGNORED_BAD_PRACTICE` 6(mkdirs 등 반환값), `DM_DEFAULT_ENCODING` 4, `DLS_DEAD_LOCAL_STORE` 4 등.
보안 영향이 없다. `DM_DEFAULT_ENCODING` 은 고치면 기존 설치의 `agent.properties`·스크립트 인코딩 해석이 바뀌므로
따로 판단해야 한다. `OS_OPEN_STREAM_EXCEPTION_PATH` 는 try-with-resources 정리 대상으로 남긴다.

## 5. 확인한 것 (문제 없음)

- MQTT `cmdId` 중복 방지: mwm-app 이 실제로 보내는 payload 에 `cmdId`(`{command_id}_{repetition_seq}`) 가 있다 (E2E 로그).
- MQTT 와 REST 경로는 같은 `OrderCaller` 를 거치므로 명령 검증이 같다.

## 6. 발견했지만 바꾸지 않은 것

- `Hennry` 로거가 부모(root) ConsoleHandler 로도 출력한다 (stderr). CLAUDE.md "로그는 파일로" 규칙과 어긋나지만,
  포그라운드 실행·서비스 관리자 로그에 기대는 운영이 있을 수 있어 사용자 판단으로 남긴다.
