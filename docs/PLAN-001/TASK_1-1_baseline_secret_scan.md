# TASK 1-1: 비밀·식별 정보 기준선 스캔

> 수행: 2026-09-29
> 상위 계획: [PLAN_001](../PLAN_001_opensource_readiness.md) WS-1-1
> 기록 정책: 비밀값·IP·실제 식별자 원문은 적지 않는다. 위치와 판정만 적는다.

## 1. 도구와 범위

| 도구 | 버전 | 범위 | 명령 |
|------|------|------|------|
| gitleaks | v8.30.1 (Docker `zricethezav/gitleaks`) | git 히스토리 전체 (`--all` 기본) | `gitleaks git /repo --redact` |
| gitleaks | 같음 | 작업 디렉터리 (추적 안 되는 파일 포함) | `gitleaks dir /repo --redact` |
| denylist | `git grep` / `git log -G` | 추적 파일 + 히스토리. `temp_jdk/`, `tools/` 제외 | 패턴은 §4 |

원본 리포트는 저장소 밖(작업 PC 의 임시 디렉터리)에만 두었다. 저장소에 커밋하지 않는다.

## 2. gitleaks — git 히스토리 (27건)

| 판정 | 건수 | 내용 |
|------|------|------|
| **실제 비밀** | **2** | 로컬 개발 MQTT 브로커 비밀번호. 로컬 `agent.properties` 의 `mqtt_credential` 과 **값이 일치**한다. `docs/SetProperties-Requirements.md` 와 `SetPropertiesFuncTest.java` 의 예시값으로 들어갔다. 커밋 2be4134 (2026-09-18), **현재 파일에도 남아 있다** |
| 문서 예시 | 22 | `docs/*`, `test-server/*.md` 의 `bootstrap_token`, `access_token`, `refresh_token`, `curl -H Authorization`. JWT 는 모두 잘려 있거나(`...`) 디코드되지 않는 샘플이다 |
| 테스트 상수 | 1 | `BizServiceIntegrationTest` 의 만료 토큰 상수. 테스트 전용 |
| 오탐 | 1 | `temp_jdk/.../java.security` 의 `jdk.tls.keyLimits` 설정 |
| 합계 | 27 | |

판정 방법: 발견 라인을 로컬 실제 설정값(`agent.properties`)과 비교하고, JWT 는 payload 디코드를 시도했다. 비교 결과는 일치 여부만 출력하고 값은 출력하지 않았다.

## 3. gitleaks — 작업 디렉터리 (599건)

| 위치 | 건수 | 판정 |
|------|------|------|
| `nohup.out`, `mwagent.*.log` | 약 570 | **로그에 access/refresh token(JWT)이 남아 있다.** git 이 무시하는 파일이라 저장소에는 없다. 코드 결함 C-4(응답 본문 로깅)가 실제로 확인됐다 → WS-3-4 |
| `agent.properties` | 2 | 로컬 실제 설정. git 이 무시한다. 공개본에 들어가지 않게 export 검사 대상에 넣는다 |
| 그 밖 | 나머지 | §2 와 같은 문서 예시·테스트 상수 |

## 4. denylist

| 분류 | 현재 파일 | 히스토리 커밋 | 위치 (현재 파일) |
|------|-----------|---------------|------------------|
| 사내 프록시 IP | 2 | 6 | `CLAUDE.md`, `docs/PROJECT_REPORT.md` |
| 개발 PC 사용자명 | 3 | 6 | `run.agent.sh`, `test/demos/TestExtractLog.java`, `docs/SetProperties-Requirements.md` |
| 개발 PC 홈 경로 | 4 | 5 | 위 + `ExeShell.java` 주석(`/home/user` 예시 — 문제없음) |
| 개발 PC 호스트명 (agent_id) | 0 | 2 | 현재 파일에서는 제거됨(0f5c41c). 히스토리에 남음 |
| 개발용 서버 호스트명 | 4 | 2 | 테스트 3개, `docs/SetProperties-Requirements.md` |
| 사설 IP (10.x, 192.168.x) | 4 | 5 | 문서·mock 서버의 예시값. 실제 주소가 아님을 확인해야 한다 |
| 이메일 | 2 | 4 | 제품 도메인 예시 주소(문서), GitHub noreply. 개인 이메일 없음 |
| 회사 도메인 | 0 | 0 | — |
| Windows 개발 경로 | 문서 3곳 | — | `CLAUDE.md`, `README.md`, `docs/PROJECT_REPORT.md` (`C:\GitHub\...`) |
| 커밋 작성자 이메일 | — | 106 커밋 | 개인 메일 도메인. **새 공개 저장소로 올리면 해소된다** |

## 5. 결론과 후속 조치

1. **MQTT 브로커 비밀번호가 저장소에 들어갔다** (현재 파일 2곳, 히스토리 1커밋). 저장소는 비공개다.
   - 현재 파일의 값은 더미값으로 바꾼다 → WS-1-3
   - 브로커 비밀번호를 바꿀지(키 회전)는 **사용자가 결정한다**. 바꾸지 않는다면 app 처럼 옛 히스토리를 공개하지 않는 것이 전제가 된다 (WS-1-5)
2. 로그에 토큰이 남는 문제는 WS-3-4 에서 고친다. 이미 쌓인 로그 파일은 운영 환경에서도 같은 문제가 있으므로 릴리스 노트에 안내한다.
3. denylist 대상(사내 IP, 사용자명, 개발 경로, 개발용 호스트명)은 WS-1-2 에서 자리표시자로 바꾼다.
4. 개인키 블록·키 파일은 현재 파일과 히스토리 모두 **0건**이다.
5. 공개 방식은 WS-1-5 대로 새 저장소에 커밋 1개로 올린다. 히스토리의 작성자 이메일, agent_id, 사내 IP 가 함께 해소된다.
6. 이 스캔의 denylist 패턴은 `.publish/export.sh`(WS-1-6)의 denylist 검사에 그대로 옮긴다. 패턴 원문에 비밀이 들어가지 않도록 export 도구는 로컬 파일에서 패턴을 읽게 한다.
