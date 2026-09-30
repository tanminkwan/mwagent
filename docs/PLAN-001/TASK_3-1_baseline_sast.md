# TASK 3-1: SAST 기준선 스캔

> 수행: 2026-09-29
> 상위 계획: [PLAN_001](../PLAN_001_opensource_readiness.md) WS-3-1
> 대상: `src/main/java` (커밋 3fb0adb 기준)

## 1. 도구와 범위

| 도구 | 버전 | 설정 | 실행 JDK |
|------|------|------|----------|
| CodeQL CLI | 2.27.1 (java-queries 1.11.11) | `java-security-extended.qls`, javac 로 실제 빌드를 추적 (build-mode manual) | 빌드: JDK 8 |
| SpotBugs + FindSecBugs | 4.8.6 + 1.13.0 | `-effort:max -low` (모든 등급) | JDK 17 (Docker `eclipse-temurin:17-jdk`) |

app 은 CodeQL 만 썼다. agent 는 CodeQL 이 Java 의 일부 패턴을 놓치므로 SpotBugs+FindSecBugs 를 함께 돌렸다.

## 2. CodeQL 결과 (10건)

| # | 심각도 | 룰 | 위치 | 판정 |
|---|--------|----|------|------|
| Q-1 | critical 9.8 | `java/concatenated-command-line` | `DownloadFile.applyChmod()` | **수정 대상.** `Runtime.exec(String)` 에 `chmod` 문자열을 이어 붙인다. 셸을 거치지는 않지만 공백으로 인자를 나누므로 경로·mode 값으로 **인자 주입**이 가능하고, 공백이 든 경로는 오동작한다. `ProcessBuilder` 인자 배열이나 `Files.setPosixFilePermissions` 로 바꾼다 |
| Q-2 | high 7.5 | `java/zipslip` | `DownloadFile.unzipFile()` | **수정 대상** (PLAN C-3 확인) |
| Q-3 | high 7.5 | `java/zipslip` | `DownloadNUnzipFunc` | **수정 대상** (PLAN C-3 확인) |
| Q-4 | high 7.5 | `java/insecure-trustmanager` | `SSLCertiFunc` | **설계상 허용 후보** (PLAN C-6). 원격 인증서를 조회하는 기능이다. 조회 전용 연결에서만 쓰는지 확인한 뒤 사유를 적고 dismiss 한다 |
| Q-5 | high 7.5 | `java/sensitive-log` | `Common.java` refresh_token 로그 | 오탐. `maskToken()` 으로 가린다 |
| Q-6 | high 7.5 | `java/sensitive-log` | `Common.java` OAuth2 수신 로그 | 오탐. token 종류·만료만 남긴다 |
| Q-7 | high 7.5 | `java/sensitive-log` | `Common.java` mTLS 수신 로그 | 오탐. 같은 이유 |
| Q-8 | high 7.5 | `java/sensitive-log` | `Order.java` `resultVo` FINE 로그 | **수정 대상** (PLAN C-5). 명령 결과(설정 파일 원문 등)가 로그에 남는다 |
| Q-9 | medium 5.4 | `java/relative-path-command` | `DownloadFile` Windows `cmd` 호출 | 수정 권장. `cmd` 를 절대 경로(`%SystemRoot%\System32\cmd.exe`)로 부른다 |
| Q-10 | medium 5.4 | `java/relative-path-command` | 같음 | 같음 |

요약: critical 1, high 7 (수정 3, 허용 후보 1, 오탐 3), medium 2.

## 3. SpotBugs + FindSecBugs 결과 (327건)

| 분류 | 건수 | 판정 |
|------|------|------|
| `CRLF_INJECTION_LOGS` | 176 | 로그 주입. 외부 값(명령 파라미터, 서버 응답)을 그대로 로그에 쓴다. 공통 마스킹·이스케이프 도우미(app 의 `log_safe` 대응)로 한 번에 처리한다 (WS-3-4) |
| `PATH_TRAVERSAL_IN` | 30 | 대부분 설정 경로·명령 파라미터 파일 읽기. 명령 파일 읽기 기능의 허용 디렉터리 검사가 있는 경로와 없는 경로를 구분해 판정한다 |
| `PATH_TRAVERSAL_OUT` | 1 | **수정 대상.** `Common.httpFileDownload()` 가 서버 응답(URL·헤더)에서 얻은 파일명을 저장 경로에 그대로 붙인다. `../` 가 오면 지정 디렉터리 밖에 쓴다 |
| `COMMAND_INJECTION` | 7 | Q-1 과 같은 곳, 그리고 명령 실행 기능 자체(`ExeScript`, `ExeText`, `DownloadFile`). 실행 기능은 설계상 허용(PLAN C-7), `applyChmod` 는 수정 |
| `WEAK_TRUST_MANAGER` | 3 | `SSLCertiFunc` (Q-4 와 같음) |
| `INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE` | 8 | 예외 메시지를 결과로 돌려준다. 서버로만 가므로 영향이 작다. 스택트레이스 원문이 가는지 건별로 확인한다 |
| `HTTP_PARAMETER_POLLUTION` | 4 | URL 에 agent_id·명령 값을 인코딩 없이 붙인다. URL 인코딩을 적용한다 |
| `HARD_CODE_PASSWORD` | 2 | 오탐. 설정 기본값이 빈 문자열이다 |
| `LDAP_INJECTION` | 1 | `JmxStatFunc` 의 JNDI lookup 이름에 명령 파라미터를 붙인다. 앞부분이 고정돼 있어 영향은 제한적이지만 값 검증을 추가한다 |
| `URLCONNECTION_SSRF_FD` | 1 | `DownloadNUnzipFunc` 의 다운로드 URL 이 명령에서 온다. 서버 지정 기능이라 허용 후보. 허용 스킴(https)만 받도록 제한을 검토한다 |
| `IMPROPER_UNICODE` | 9 | 대소문자 비교. 영향 낮음 |
| `RE_POSSIBLE_UNINTENDED_PATTERN` (rank 2) | 1 | **수정 대상 (기능 결함).** `SSLCertiFunc.matchesDomain()` 이 `split(".")` 를 쓴다. 정규식이라 항상 빈 배열이 나와, 와일드카드 인증서의 세그먼트 수 검사가 늘 참이 된다. `split("\\.")` 로 고친다 |
| `NP_NULL_PARAM_DEREF` (rank 8) | 1 | `SSLCertiFunc` 파라미터 null 처리. 수정 |
| 그 밖 (BAD_PRACTICE, STYLE, I18N, PERFORMANCE, MALICIOUS_CODE 등) | 84 | 품질 항목. 보안 영향 없음. 필요하면 WS-3-7 에서 정리한다 |

## 4. 도구가 잡지 못한 것 (수동 진단, PLAN 3.1)

두 도구 모두 아래 항목을 보고하지 않았다. **도구 결과 0건을 "안전"으로 보면 안 되는 사례**라 따로 적는다.

| PLAN # | 내용 | 못 잡은 이유 (추정) |
|--------|------|---------------------|
| C-1 | `Common`, `ApacheHttpClientAdapter` 의 HTTPS 가 모든 인증서를 신뢰하고 `NoopHostnameVerifier` 로 호스트명 검증을 끈다 | Apache HttpClient 의 `TrustStrategy` 람다와 `NoopHostnameVerifier` 는 CodeQL `insecure-trustmanager` 의 모델 대상이 아니다 |
| C-2 | mTLS client 의 `NoopHostnameVerifier` | 같음 |
| C-4 | `httpPOST*()` 가 token 응답 본문 전체를 INFO 로 로깅 (TASK 1-1 에서 로그 파일에 JWT 570여 건 확인) | 응답 본문 변수는 "민감" 이름 휴리스틱에 걸리지 않는다 |

CI 에 넣을 때는 C-1 을 막는 **자체 검사**(예: `NoopHostnameVerifier`, `loadTrustMaterial(null,` 사용 금지 grep)를 추가한다 (WS-7).

## 5. 수정 대상 요약 (WS-3-2~3-7 입력)

| 우선순위 | 항목 | 출처 |
|----------|------|------|
| 1 | TLS 검증 복구 | C-1, C-2 (수동) |
| 2 | Zip Slip | Q-2, Q-3 |
| 3 | 다운로드 파일명 경로 이탈 | SpotBugs `PATH_TRAVERSAL_OUT` |
| 4 | 토큰·명령 결과 로깅 제거, 로그 이스케이프 | C-4, Q-8, `CRLF_INJECTION_LOGS` |
| 5 | `applyChmod` 인자 주입 | Q-1 |
| 6 | `matchesDomain` 정규식 결함, null 처리 | SpotBugs rank 2·8 |
| 7 | Windows `cmd` 절대 경로, URL 인코딩, JNDI 이름 검증 | Q-9·10, `HTTP_PARAMETER_POLLUTION`, `LDAP_INJECTION` |
| 허용 | 명령 실행 기능, `SSLCertiFunc` trust-all | C-6, C-7 → 위협 모델 문서(WS-3-5)에 근거를 적고 dismiss |

원본 결과(SARIF, SpotBugs XML)는 저장소 밖에 두었다. CI 도입(WS-7) 뒤에는 GitHub code scanning 에서 추적한다.
