# TASK 1-0: 저장소 수록 범위 정리 (올릴 것 / 올리지 않을 것)

> 작성: 2026-09-29
> 상위 계획: [PLAN_001](../PLAN_001_opensource_readiness.md) WS-1
> 목적: 필요 없는 파일이 점검 대상(CVE·SAST·시크릿 스캔)에 들어가지 않게, 스캔보다 **먼저** 범위를 정한다.
> 이 표는 `.gitignore`, 공개본 제외 목록(`.publish/exclude.txt`, WS-1-6), CI 스캔 범위(WS-7)의 기준이 된다.

## 1. 분류 기준

| 분류 | 뜻 | git (비공개 원본) | 공개본 | 점검 |
|------|----|-------------------|--------|------|
| **A. 공개** | 제품 소스·빌드·사용 문서 | 추적 | 포함 | 전부 |
| **B. 내부 전용** | 개발 과정 기록, AI 도구 설정, 내부 진단 | 추적 | **제외** | 시크릿 스캔만 |
| **C. 추적 해제** | 저장소에 있으면 안 되는 것 (타사 바이너리, 개인 환경 파일, 임시 코드) | **해제** | 제외 | 대상 아님 |
| **D. 무시** | 실행 중 생기는 파일 | 무시 (이미 `.gitignore`) | 제외 | 대상 아님 |

## 2. 현재 추적 파일 분류 (추적 526개 기준, 2026-09-29)

### A. 공개

| 경로 | 비고 |
|------|------|
| `src/main/java/`, `src/test/java/`, `src/test/resources/` | 테스트 픽스처의 개발용 호스트명·비밀번호는 WS-1-2·1-3 에서 더미값으로 바꾼다 |
| `pom.xml` | 주 빌드 |
| `build-offline.sh/.bat`, `download-dependencies.sh/.bat`, `prepare-offline-deployment.sh/.bat`, `run-tests.sh/.bat` | 오프라인 빌드·배포 절차 |
| `agent.properties.example` | 설정 예시 |
| `LICENSE`, `README.md`, `.gitignore`, `.gitattributes` | |
| `DEPENDENCIES.md`, `TESTING.md`, `COVERAGE.md`, `COVERAGE_QUICKSTART.md` | 사용자·기여자용. 개발 PC 경로가 있으면 뺀다 |
| `docs/ExtractLog_Guide.md`, `docs/SetProperties-Requirements.md`, `docs/mTLS-JWT-Authentication-Flow.md`, `docs/Token-Validation-Architecture.md` | 기능·설계 문서. 예시값을 정리한 뒤 공개한다 |
| `test-server/` | mTLS 통합 테스트용 mock 서버. 인증서는 `generate-certs.*` 로 각자 만든다 (키 파일은 추적 안 함) |
| `biz-service/` | 토큰 검증 통합 테스트용 서버 |

### B. 내부 전용 (원본에만 두고 공개본에서 뺀다)

| 경로 | 이유 |
|------|------|
| `CLAUDE.md`, `.agent/` | AI 도구 설정. 사내 프록시 IP, 개발 PC 경로, 내부 작업 규칙이 있다 |
| `WORK_HISTORY.md`, `REFACTORING_PLAN.md` | 내부 작업 이력 |
| `docs/PROJECT_REPORT.md`, `docs/ai-autonomous-report.md`, `docs/AI-AUTONOMOUS-REQUIREMENTS.md`, `docs/templates/` | 내부 보고·AI 작업 지침 |
| `docs/PLAN_001_opensource_readiness.md`, `docs/PLAN-001/` | 내부 진단 기록 (app 과 같은 정책) |

### C. 추적 해제

| 경로 | 추적 파일 수 | 이유 |
|------|--------------|------|
| `temp_jdk/` | 320 | JDK 조각. `*.jar` 가 무시돼 **jar 없이 man·sample·설정만** 들어가 있어 쓸 수도 없다. 타사 바이너리 재배포 문제. 점검 오탐(gitleaks 1건)의 원인 |
| `tools/apache-maven-3.9.6/` | 29 | Maven 조각. 역시 jar·`bin/` 이 빠져 있어 동작하지 않는다 (이 환경에서 Maven 이 깨진 원인). 공식 배포본을 받도록 안내한다 |
| `test/demos/` | 6 | 수동 데모 코드. 개발 PC 절대 경로가 들어 있다. 빌드·테스트에 쓰이지 않는다 |
| `run.agent.sh` | 1 | 개발 PC 전용 경로(JDK, 홈 디렉터리)가 박혀 있다. 공개용은 경로를 인자로 받는 예시 스크립트로 새로 만든다 |

### D. 무시 (이미 `.gitignore` 처리, 확인만)

`agent.properties`, `*.log`, `nohup.out`, `run_reboot.log`, `.mqtt-persist/`, `build/`, `bin_test/`, `tmp/`, `.gradle/`.
TASK 1-1 에서 JWT 가 나온 로그 파일은 모두 여기에 속한다. `.mqtt-persist/`, `bin_test/` 는 `.gitignore` 에 **명시돼 있지 않다** (안의 파일이 `*.class` 등으로 우연히 무시될 뿐이다). 명시적으로 추가한다.

## 3. 결정이 필요한 항목

| # | 항목 | 선택지 | 권장 |
|---|------|--------|------|
| D-1 | `lib/*.jar` (14개, 21MB) | ① git 에 계속 둔다 ② git 에서 빼고 `download-dependencies.*` + `lib/SHA256SUMS` 로 받게 하며, 오프라인 반입용 묶음은 릴리스 산출물로 낸다 | ② — 타사 바이너리를 소스 저장소에 두지 않는다. CVE 스캔은 `pom.xml` 기준으로 한다. 단 오프라인 빌드 절차가 바뀌므로 확인이 필요하다 |
| D-2 | `build.gradle` | ① 유지 ② 삭제 (Maven 으로 일원화) | ② — 이 환경에서 동작하지 않고, 버전이 `Version.java` 와 어긋나 있다. 단 `prepare-offline-deployment.*` 가 참조하므로 함께 고친다 |
| D-3 | `ca-server/` | ① 공개 (A) ② 내부 전용 (B) ③ 추적 해제 | agent 빌드·테스트에 쓰이지 않는다(테스트 1곳에서 언급만). 공개하면 Python 의존성 CVE 점검 대상이 늘어난다. **사용 여부 확인 후 결정** |
| D-4 | `docs/CA-Server-Requirements.md` | ① 공개 ② 내부 전용 | 서버 팀용 요구사항이라 D-3 과 함께 결정 |
| D-5 | `test-server/`, `biz-service/` 를 A 로 둘 때의 점검 | Python 의존성(pip-audit)·CodeQL python 도 돌린다 | app 과 같은 방식(pip-audit, `==` 고정)을 따른다 |

### 결정 (2026-09-29, 사용자)

| # | 결정 | 반영 |
|---|------|------|
| D-1 | **`lib/*.jar` 를 git 에서 뺀다. 오프라인 빌드는 유지한다** | `lib/SHA256SUMS` 추가(현재 jar 13개가 Maven Central `.sha1` 과 일치함을 확인한 뒤 생성). `verify-lib.sh`(sha256sum→shasum→openssl, AIX/HP-UX 대비)·`verify-lib.ps1` 추가. `download-dependencies.*`(다운로드 후), `prepare-offline-deployment.*`(묶기 전), `build-offline.*`(컴파일 전)에서 검증한다 |
| D-2 | **`build.gradle` 삭제, Maven 일원화** | 삭제. `prepare-offline-deployment.*` 의 참조 제거. README·CLAUDE.md 의 Gradle 안내 제거 |
| D-3 | **`ca-server/` 는 내부 전용 (B)** | 원본에 두고 공개본에서 뺀다 |
| D-4 | **`docs/CA-Server-Requirements.md` 는 내부 전용 (B)** | D-3 과 같음 |
| D-5 | `test-server/`, `biz-service/` 는 공개 (A) | Python 의존성 점검 대상에 넣는다 (WS-2) |
| — | MQTT 브로커 비밀번호: **더미로 교체만** (키 회전 안 함) | 현재 파일 2곳을 `dummy-mqtt-credential` 로 교체. 옛 히스토리는 공개하지 않는다 (WS-1-5 전제) |

C 분류(`temp_jdk/`, `tools/`, `test/demos/`, `run.agent.sh`)와 `lib/*.jar` 는 `git rm --cached` 로 추적만 해제했다. 로컬 파일은 남아 있다. `.gitignore` 에 C 분류와 `.mqtt-persist/`, `bin_test/`, `mwagent-offline-deployment/` 를 추가했다.

검증 (이 환경, JDK 8):
- `verify-lib.sh`: 정상 13개 통과, jar 변조·누락 검출(exit 1), CRLF 로 바뀐 SHA256SUMS 도 통과
- `prepare-offline-deployment.sh` → 생성된 묶음 안에서 `build-offline.sh` → `build/mwagent.jar` 생성 확인
- `SetPropertiesFuncTest` 32개 포함 81개 테스트 통과 (간이 러너)
- `.bat` / `.ps1` 은 이 환경에서 실행하지 못했다. **Windows 에서 확인이 필요하다**

## 4. 추적 해제 시 주의 (app 의 교훈)

- `git rm --cached` 로 추적만 해제해도, **다른 클론(예: Windows 빌드 PC)이 그 커밋을 pull 하면 해당 파일이 그 PC 에서 지워진다.** 특히 `tools/apache-maven-3.9.6/conf/` 가 지워지면 그 PC 의 Maven 이 깨진다.
  - pull 하기 전에 해당 디렉터리를 백업하거나, 공식 Maven·JDK 를 저장소 밖에 설치하고 경로를 바꾼다.
  - CLAUDE.md 의 "DO NOT DELETE `tools/apache-maven-3.9.6/`" 규칙과 충돌하므로, 해제할 때 CLAUDE.md 도 "저장소 밖에 설치" 로 고친다.
- 추적 해제는 현재 파일에서만 없어진다. 히스토리에는 남으므로, 공개는 WS-1-5(새 저장소에 커밋 1개)로 한다.

## 5. 이 정리 후 스캔 범위

- **시크릿 스캔(gitleaks)**: A + B 전체 (내부 전용도 비밀은 없어야 한다)
- **의존성 CVE**: `pom.xml` (D-1 ②를 고르면 `lib/` 는 스캔 대상에서 빠진다), A 에 남는 Python 서버의 `requirements.txt`
- **SAST**: `src/main/java` (CodeQL java, SpotBugs). A 에 남는 Python 서버는 CodeQL python
- **denylist**: 공개본(A) 전체

TASK 1-1·2-1·3-1 의 기준선은 이 정리 **전에** 수행했다. C 로 분류된 경로에서 나온 결과(`temp_jdk` gitleaks 오탐 1건 등)는 정리 뒤 재스캔에서 빠진다.
