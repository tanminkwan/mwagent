# MwManger Agent 리팩토링 - AI 자율 수행용 요구사항 정의서

> **목적**: AI가 사람과의 상호작용 없이 자율적으로 프로젝트를 수행할 수 있도록 작성된 요구사항 정의서

---

## 0. 전제 조건 (AI 작업 시작 전 필요 사항)

### 0.1 유일한 입력: 레거시 소스코드

```
┌─────────────────────────────────────────────────────────────┐
│  AI에게 제공해야 하는 것: 레거시 소스코드 (이것만 있으면 됨)     │
└─────────────────────────────────────────────────────────────┘
```

| 항목 | 값 |
|------|-----|
| **GitHub Repository** | `mwmanger-auto` (이미 생성됨) |
| **레거시 소스 위치** | `./mwmanger-asis/` (로컬 파일 시스템 경로) |

**나머지는 AI가 자율적으로 수행:**

| 항목 | AI 자율 수행 방법 |
|------|------------------|
| Git 저장소 | `git init` 후 브랜치 생성 |
| 빌드 도구 (Gradle) | Gradle Wrapper 생성 (`gradle wrapper`) |
| build.gradle | 소스 분석 후 의존성 파악하여 생성 |
| 보안 취약점 위치 | 소스 코드 정적 분석으로 탐지 |
| 기존 설정 형식 | `Config.java` 분석 |
| 기존 API 스펙 | `Common.java` 분석 |
| 브랜치 전략 | 요구사항에 따라 결정 |
| Mock Auth Server | Python Flask로 구현 |
| Biz Service | Python Flask + JWT로 구현 |
| CA Server | Python Flask + OpenSSL로 구현 |
| 테스트 인증서 | OpenSSL 스크립트로 생성 |

### 0.2 AI 자율 분석 항목

AI는 레거시 소스를 분석하여 다음을 스스로 파악한다:

#### 소스 구조 (분석 대상)

```
src/main/java/mwagent/
├── MwAgent.java              # 메인 진입점
├── PreWork.java              # 에이전트 등록 로직
├── FirstWork.java            # Kafka 초기화
├── MainWork.java             # 명령 폴링 루프
├── common/
│   ├── Config.java           # 설정 관리 (싱글톤)
│   └── Common.java           # HTTP 통신, 토큰 관리
├── order/
│   ├── Order.java            # 추상 클래스
│   ├── ExeShell.java         # 쉘 실행
│   ├── ExeScript.java        # 스크립트 실행
│   ├── DownloadFile.java     # 파일 다운로드
│   └── ReadFullPathFile.java # 파일 읽기
├── agentfunction/
│   ├── AgentFunc.java        # 인터페이스
│   └── AgentFuncFactory.java # 팩토리
├── kafka/
│   ├── MwConsumerThread.java
│   ├── MwProducer.java
│   └── MwHealthCheckThread.java
└── vo/
    ├── CommandVO.java
    └── ResultVO.java
```

### 0.3 현재 보안 취약점 위치

| 취약점 | 파일 | 위치 | 현재 코드 패턴 |
|--------|------|------|---------------|
| Command Injection | `ExeShell.java` | execute() | `Runtime.exec(command)` 직접 호출 |
| Command Injection | `ExeScript.java` | execute() | 파라미터 검증 없음 |
| Path Traversal | `DownloadFile.java` | execute() | `../` 패턴 미검증 |
| Path Traversal | `ReadFullPathFile.java` | execute() | 경로 검증 없음 |
| Token Logging | `Common.java` | updateToken() | `logger.fine("token:" + token)` |
| 동시성 버그 | `MwConsumerThread.java` | run() | `\|\| stopRequested==true` (논리 오류) |

### 0.4 기존 설정 파일 형식 (agent.properties)

```properties
# 서버 설정
server_url=https://server.example.com
get_command_uri=/api/v1/command/getCommands
post_agent_uri=/api/v1/agent

# 인증
token=REFRESH_TOKEN_VALUE

# 폴링 주기
command_check_cycle=60

# Kafka (선택)
kafka_broker_address=kafka:9092

# 환경 변수
host_name_var=HOSTNAME
user_name_var=USER

# 로그
log_dir=/var/log/mwagent
log_level=INFO
```

### 0.5 기존 Legacy 인증 API

```
POST /api/v1/security/refresh
Content-Type: application/json
Authorization: Bearer {refresh_token}

Request Body:
{
    "agent_id": "hostname_username_J"
}

Response:
{
    "result_code": "OK",
    "access_token": "...",
    "refresh_token": "..."
}
```

### 0.6 Git 브랜치 전략

#### 초기 설정 (Phase 0)

Phase 시작 전, 레거시 소스를 main 브랜치에 커밋:

```bash
# 1. 빈 repo 클론
git clone https://github.com/tanminkwan/mwmanger-auto.git
cd mwmanger-auto

# 2. 레거시 소스 복사 (mwmanger-asis/ → src/main/java/mwagent/)
mkdir -p src/main/java
cp -r ./mwmanger-asis src/main/java/mwagent

# 3. 요구사항 문서 복사
cp /path/to/AI-AUTONOMOUS-REQUIREMENTS.md ./

# 4. 초기 커밋
git add .
git commit -m "Phase 0: Add legacy source code (as-is)"
git push origin main
```

#### Phase별 브랜치

각 Phase는 별도의 브랜치에서 작업 후 Push:

| Phase | 브랜치명 | 베이스 |
|-------|----------|--------|
| Phase 0 | `main` | - (레거시 소스 초기 커밋) |
| Phase 1 | `phase1-gradle-setup` | `main` |
| Phase 2 | `phase2-lifecycle` | `phase1-gradle-setup` |
| Phase 3 | `phase3-service-layer` | `phase2-lifecycle` |
| Phase 4 | `phase4-security` | `phase3-service-layer` |
| Phase 5 | `phase5-di-architecture` | `phase4-security` |
| Phase 6 | `phase6-test-servers` | `phase5-di-architecture` |
| Phase 7 | `phase7-mtls-oauth2` | `phase6-test-servers` |
| Phase 8 | `phase8-integration-test` | `phase7-mtls-oauth2` |
| Phase 9 | `phase9-documentation` | `phase8-integration-test` |

**브랜치 작업 흐름:**
```bash
# Phase N 시작
git checkout phase(N-1)-xxx   # 이전 Phase 브랜치에서
git checkout -b phaseN-xxx    # 새 브랜치 생성

# Phase N 작업 수행...

# Phase N 완료
git add .
git commit -m "Phase N 완료: [내용 요약]"
git push -u origin phaseN-xxx
```

**최종 머지 (Phase 9 완료 후):**
```bash
git checkout main
git merge phase9-documentation
git push origin main
```

### 0.7 빌드 명령어

```bash
# 테스트 실행
./gradlew test

# 빌드
./gradlew clean build

# JAR 생성
./gradlew jar
```

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **프로젝트명** | MwManger Agent 리팩토링 |
| **대상** | Java 1.8 기반 원격 서버 관리 에이전트 (데몬 프로세스) |
| **목표** | 보안 강화 + 아키텍처 개선 + 테스트 커버리지 확보 |
| **산출물** | 리팩토링된 소스코드, 테스트 코드, 테스트 서버, 기술 문서 |

---

## 2. 비즈니스 목표

1. **보안 강화**: 운영 환경에서 발생할 수 있는 보안 위협 차단
2. **인증 체계 현대화**: 토큰 탈취에 대비한 인증서 기반 상호 인증 도입
3. **유지보수성 향상**: 테스트 가능한 구조로 전환
4. **AI 협업 최적화**: 향후 AI 기반 개발이 용이한 구조 확립

---

## 3. 기능 요구사항

### FR-1: 인증 체계 이원화

| ID | 요구사항 |
|----|----------|
| FR-1.1 | mTLS(상호 인증서) 기반 인증을 지원해야 한다 |
| FR-1.2 | 기존 Refresh Token 기반 인증도 계속 지원해야 한다 (하위 호환) |
| FR-1.3 | 두 인증 방식은 설정(`use_mtls`)으로 전환 가능해야 한다 |
| FR-1.4 | OAuth2 표준 토큰 엔드포인트(`/oauth2/token`)를 사용해야 한다 |
| FR-1.5 | Access Token 만료 시 자동 갱신되어야 한다 |
| FR-1.6 | Refresh Token 만료 시 mTLS로 자동 fallback 되어야 한다 |

### FR-2: 생명주기 관리

| ID | 요구사항 |
|----|----------|
| FR-2.1 | 에이전트는 명확한 상태(생성/시작/실행/종료/실패)를 가져야 한다 |
| FR-2.2 | 종료 시 실행 중인 작업이 완료될 때까지 대기해야 한다 (Graceful Shutdown) |
| FR-2.3 | 종료 대기 시간은 최대 30초로 제한한다 |
| FR-2.4 | 종료 전 로그가 파일에 flush 되어야 한다 |

### FR-3: 서비스 분리

| ID | 요구사항 |
|----|----------|
| FR-3.1 | Kafka 관련 기능은 하나의 서비스로 통합 관리되어야 한다 |
| FR-3.2 | 명령 실행은 ThreadPool 기반 서비스로 관리되어야 한다 |
| FR-3.3 | 에이전트 등록 프로세스는 독립된 서비스로 분리되어야 한다 |
| FR-3.4 | 각 서비스는 독립적으로 시작/종료가 가능해야 한다 |

### FR-4: 테스트 환경

| ID | 요구사항 |
|----|----------|
| FR-4.1 | mTLS 테스트를 위한 인증서 생성 스크립트를 제공해야 한다 |
| FR-4.2 | OAuth2 토큰 발급을 테스트할 수 있는 Mock 서버를 제공해야 한다 |
| FR-4.3 | JWT 토큰 검증 예제 서비스를 제공해야 한다 |
| FR-4.4 | 인증서 발급 프로세스를 시뮬레이션할 CA 서버를 제공해야 한다 |

---

## 4. 비기능 요구사항

### NFR-1: 보안

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| NFR-1.1 | Command Injection 공격을 방어해야 한다 | CRITICAL |
| NFR-1.2 | Path Traversal 공격을 방어해야 한다 | CRITICAL |
| NFR-1.3 | 토큰은 로그에 마스킹되어 출력되어야 한다 | HIGH |
| NFR-1.4 | 보안 검증은 설정으로 on/off 가능해야 한다 | MEDIUM |
| NFR-1.5 | 인증서 복사 공격 방지를 위해 IP 검증이 가능해야 한다 | MEDIUM |

### NFR-2: 호환성

| ID | 요구사항 |
|----|----------|
| NFR-2.1 | JDK 1.8에서 동작해야 한다 |
| NFR-2.2 | Windows, Linux, AIX, HP-UX에서 동작해야 한다 |
| NFR-2.3 | 기존 API 시그니처는 유지되어야 한다 |
| NFR-2.4 | 기존 설정 파일 형식과 호환되어야 한다 |

### NFR-3: 테스트

| ID | 요구사항 |
|----|----------|
| NFR-3.1 | 단위 테스트 200개 이상을 작성해야 한다 |
| NFR-3.2 | 모든 테스트는 100% 통과해야 한다 |
| NFR-3.3 | 통합 테스트는 환경변수로 조건부 실행 가능해야 한다 |
| NFR-3.4 | 테스트 프레임워크는 JUnit 5를 사용해야 한다 |

### NFR-4: 아키텍처

| ID | 요구사항 |
|----|----------|
| NFR-4.1 | 의존성 주입(DI)을 통해 테스트 가능한 구조여야 한다 |
| NFR-4.2 | 주요 컴포넌트는 인터페이스로 추상화되어야 한다 |
| NFR-4.3 | 설정, HTTP 클라이언트는 Mock으로 대체 가능해야 한다 |
| NFR-4.4 | 버전은 단일 소스에서 관리되어야 한다 |

---

## 5. 제약 조건

### 5.1 기술 제약

| 제약 | 사유 |
|------|------|
| Gradle 사용 | Gradle Wrapper로 빌드 |
| JDK 1.8 호환 | 레거시 환경 지원 |
| System.err 출력 금지 | 데몬 프로세스이므로 파일 로그만 사용 |

### 5.2 필수 생성 파일

- `build.gradle` - Gradle 빌드 설정
- `settings.gradle` - 프로젝트 설정
- `gradlew`, `gradlew.bat` - Gradle Wrapper

### 5.3 하위 호환성

- 기존 `PreWork`, `FirstWork`, `MainWork` 클래스명 유지
- 기존 `agent.properties` 설정 키 유지
- 기존 API 엔드포인트 계속 지원 (`/api/v1/security/refresh`)

---

## 6. 설계 원칙

| 원칙 | 적용 방법 |
|------|----------|
| **Test-Driven** | 기능 구현 전 테스트 코드 먼저 작성 |
| **SOLID** | 단일 책임, 인터페이스 분리, 의존성 역전 적용 |
| **Phase 분할** | 독립적으로 완료/검증 가능한 단위로 작업 분할 |
| **문서화** | AI가 컨텍스트를 빠르게 파악할 수 있는 CLAUDE.md 작성 |

---

## 7. 산출물 정의

### 7.1 소스코드

| 영역 | 설명 |
|------|------|
| 생명주기 관리 | 상태 관리, Graceful Shutdown |
| 서비스 레이어 | Kafka, 명령실행, 등록 서비스 |
| 보안 검증 | Command Injection, Path Traversal 방어 |
| DI 인프라 | 인터페이스, 어댑터, DI 컨테이너 |
| mTLS 지원 | 인증서 기반 HTTP 클라이언트 |

### 7.2 테스트 코드

| 유형 | 대상 |
|------|------|
| 단위 테스트 | 모든 신규 클래스 |
| 통합 테스트 | mTLS 인증 흐름, 토큰 갱신 흐름 |
| 보안 테스트 | 취약점 방어 검증 |

### 7.3 테스트 서버 (Python Flask)

AI가 직접 구현해야 하는 가상 서버:

| 서버 | 디렉토리 | 포트 | 용도 |
|------|----------|------|------|
| Mock Auth Server | `test-server/` | 8443 (HTTPS/mTLS) | OAuth2 토큰 발급, mTLS 클라이언트 인증서 검증 |
| Biz Service | `biz-service/` | 8080 (HTTP) | JWT Access Token 검증, 비즈니스 API 예제 |
| CA Server | `ca-server/` | 8444 (HTTPS) | 인증서 발급/갱신/폐기 시뮬레이션 |

#### 테스트 서버 요구사항

**Mock Auth Server (`test-server/mock_server.py`)**
```
- POST /oauth2/token: client_credentials grant로 Access Token 발급
- POST /oauth2/token: refresh_token grant로 토큰 갱신
- mTLS 클라이언트 인증서 검증 (CN 추출)
- JWT 토큰 생성 (HS256)
```

**Biz Service (`biz-service/app.py`)**
```
- GET /api/v1/command/getCommands: JWT 검증 후 명령 목록 반환
- POST /api/v1/agent: 에이전트 등록
- POST /api/v1/security/refresh: Legacy 토큰 갱신 (하위 호환)
- Authorization: Bearer {token} 헤더 검증
```

**CA Server (`ca-server/app.py`)**
```
- POST /api/v1/certificate/issue: 신규 인증서 발급
- POST /api/v1/certificate/renew: 인증서 갱신
- GET /api/v1/certificate/status: 인증서 상태 조회
- Bootstrap Token 검증
```

#### 테스트 인증서 생성

`test-server/generate-certs.sh` 스크립트로 생성:
```
test-server/certs/
├── ca.crt, ca.key           # Root CA
├── server.crt, server.key   # Mock Auth Server용
├── client.crt, client.key   # 에이전트 클라이언트용
└── client.p12               # PKCS12 형식 (Java용)
```

### 7.4 문서

| 문서 | 내용 |
|------|------|
| CLAUDE.md | AI를 위한 프로젝트 핵심 규칙 |
| 인증 흐름 문서 | mTLS + JWT 흐름 (Mermaid 다이어그램) |
| 프로젝트 보고서 | 목적, 수행내용, 시사점 (Mermaid 다이어그램) |

---

## 8. 성공 기준

| 기준 | 측정 방법 |
|------|----------|
| 테스트 통과 | `./gradlew test` 실행 시 200개 이상 테스트 100% 통과 |
| 빌드 성공 | `./gradlew build` 실행 시 JAR 생성 |
| mTLS 동작 | Mock 서버와 mTLS 통신 성공 |
| Legacy 동작 | 기존 방식 인증 성공 |
| 보안 검증 | SecurityValidator 테스트 통과 |

---

## 9. Phase별 작업 순서

### 9.1 Phase 작업 흐름 (모든 Phase 공통)

```
┌─────────────────────────────────────────────────────────────┐
│  각 Phase는 반드시 아래 순서를 따른다:                         │
│                                                             │
│  1. 브랜치 생성 (git checkout -b phaseN-xxx)                 │
│  2. Coding (기능 구현)                                       │
│  3. Test Code 작성 (단위 테스트)                              │
│  4. Test 실행 및 통과 확인 (./gradlew test)                   │
│  5. Test 결과 보고 (통과한 테스트 수, 커버리지)                 │
│  6. Git Commit (Phase 완료 커밋)                             │
│  7. 브랜치 Push (git push -u origin phaseN-xxx)              │
│  8. 다음 Phase 진행                                          │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 Phase 목록

| Phase | 내용 | 예상 테스트 수 |
|-------|------|---------------|
| Phase 0 | 레거시 소스 초기 커밋 (main) | - |
| Phase 1 | 프로젝트 구조 및 Gradle 설정 | 10+ |
| Phase 2 | 생명주기 관리 프레임워크 | 30+ |
| Phase 3 | 서비스 레이어 분리 | 40+ |
| Phase 4 | 보안 검증 모듈 (Command Injection, Path Traversal) | 50+ |
| Phase 5 | DI 아키텍처 구현 | 30+ |
| Phase 6 | 테스트 서버 구현 (Auth, Biz, CA) | - |
| Phase 7 | mTLS 지원 및 OAuth2 토큰 관리 | 40+ |
| Phase 8 | 통합 테스트 (가상 서버 연동) | 30+ |
| Phase 9 | 문서화 및 최종 Push | - |

### 9.3 Phase 6: 테스트 서버 구현 상세

Phase 6에서는 Python Flask로 3개의 가상 서버를 구현:

```
┌─────────────────────────────────────────────────────────────┐
│  Phase 6 작업 순서:                                          │
│                                                             │
│  1. test-server/generate-certs.sh 작성 (인증서 생성)         │
│  2. Mock Auth Server 구현 (test-server/mock_server.py)      │
│  3. Biz Service 구현 (biz-service/app.py)                   │
│  4. CA Server 구현 (ca-server/app.py)                       │
│  5. 각 서버 수동 실행 테스트                                  │
│  6. Git Commit                                              │
└─────────────────────────────────────────────────────────────┘
```

### 9.4 Phase 8: 통합 테스트 상세

Phase 8에서는 가상 서버를 활용한 통합 테스트 작성:

```
┌─────────────────────────────────────────────────────────────┐
│  Phase 8 통합 테스트 시나리오:                                │
│                                                             │
│  1. mTLS 인증 흐름 테스트                                    │
│     - 클라이언트 인증서로 Auth Server 접속                    │
│     - Access Token 발급 확인                                 │
│                                                             │
│  2. 토큰 갱신 흐름 테스트                                     │
│     - Refresh Token으로 토큰 갱신                            │
│     - 만료 시 mTLS fallback 확인                             │
│                                                             │
│  3. Biz Service 연동 테스트                                  │
│     - JWT 토큰으로 API 호출                                  │
│     - 명령 조회/결과 전송 확인                                │
│                                                             │
│  4. CA Server 연동 테스트                                    │
│     - Bootstrap Token으로 인증서 발급                        │
│     - 인증서 갱신 흐름 확인                                   │
└─────────────────────────────────────────────────────────────┘
```

**통합 테스트 실행 방법:**
```bash
# 1. 테스트 서버 시작 (별도 터미널)
cd test-server && python mock_server.py --ssl &
cd biz-service && python app.py &
cd ca-server && python app.py &

# 2. 통합 테스트 실행
./gradlew test --tests "*IntegrationTest"
```

### 9.5 Phase 9: 문서화 및 완료

Phase 9에서 모든 문서 작성 후 프로젝트 완료:

```
┌─────────────────────────────────────────────────────────────┐
│  Phase 9 작업 순서:                                          │
│                                                             │
│  1. 전체 테스트 실행 (./gradlew test)                        │
│  2. 문서 작성 (아래 목록 참조)                                │
│  3. 최종 커밋 및 Push                                        │
│  4. main 브랜치 머지 (필요시)                                │
└─────────────────────────────────────────────────────────────┘
```

#### Phase 9 산출물: 문서 목록

| 문서 | 파일 | 내용 |
|------|------|------|
| **README** | `README.md` | 프로젝트 소개, 빠른 시작, 주요 기능 |
| **아키텍처** | `docs/ARCHITECTURE.md` | 시스템 구조, 컴포넌트 다이어그램, 패키지 구조 |
| **빌드 가이드** | `docs/BUILD_GUIDE.md` | 빌드 환경, 빌드 명령, JAR 생성 |
| **배포 가이드** | `docs/DEPLOYMENT_GUIDE.md` | 배포 절차, 설정 파일, 실행 방법 |
| **테스트 보고서** | `docs/TEST_REPORT.md` | 테스트 현황, 커버리지, 통합 테스트 결과 |
| **인증 흐름** | `docs/AUTHENTICATION_FLOW.md` | mTLS, OAuth2, 토큰 갱신 흐름 (Mermaid) |
| **API 문서** | `docs/API_REFERENCE.md` | 테스트 서버 API 엔드포인트 |

#### 각 문서 요구사항

**README.md**
```markdown
- 프로젝트 개요 (1-2 문단)
- 주요 기능 목록
- 빠른 시작 (Quick Start)
- 요구사항 (JDK 1.8, etc.)
- 빌드 및 실행 명령
- 프로젝트 구조 (tree)
- 라이선스
```

**docs/ARCHITECTURE.md**
```markdown
- 시스템 아키텍처 다이어그램 (Mermaid)
- 컴포넌트 설명 (lifecycle, service, infrastructure)
- 패키지 구조
- 의존성 주입(DI) 구조
- 데이터 흐름
```

**docs/BUILD_GUIDE.md**
```markdown
- 빌드 환경 요구사항
- Gradle 설정 설명
- 빌드 명령어
- JAR 파일 생성
- 테스트 실행 방법
```

**docs/DEPLOYMENT_GUIDE.md**
```markdown
- 배포 환경 요구사항
- agent.properties 설정 항목 설명
- 인증서 설치 방법 (mTLS)
- 실행 명령어 (Windows/Linux)
- 로그 설정
- 트러블슈팅
```

**docs/TEST_REPORT.md**
```markdown
- 테스트 요약 (총 테스트 수, 통과율)
- Phase별 테스트 현황
- 통합 테스트 결과
- 커버리지
- 실행 환경
```

**docs/AUTHENTICATION_FLOW.md**
```markdown
- mTLS 인증 흐름 (Mermaid sequence diagram)
- OAuth2 토큰 발급 흐름
- 토큰 갱신 흐름 (cascading)
- 인증서 발급 흐름 (CA Server)
```

**docs/API_REFERENCE.md**
```markdown
- Mock Auth Server API
- Biz Service API
- CA Server API
- 요청/응답 예시
```

### 9.6 Phase 완료 조건

각 Phase 완료 시 반드시 확인:

1. **테스트 100% 통과**: `./gradlew test` 성공
2. **회귀 버그 없음**: 이전 Phase 테스트도 모두 통과
3. **브랜치 생성**: `phaseN-xxx` 브랜치에서 작업
4. **커밋 메시지**: `Phase N 완료: [내용 요약]`
5. **브랜치 Push**: `git push -u origin phaseN-xxx`
6. **테스트 보고**: 콘솔에 통과한 테스트 수 출력

### 9.7 Phase 실패 시

테스트 실패 시:
1. 실패 원인 분석
2. 코드 수정
3. 테스트 재실행
4. 통과할 때까지 반복 (다음 Phase 진행 금지)

---

## 10. 의사결정 가이드

AI가 판단해야 할 상황에서의 기준:

| 상황 | 결정 기준 |
|------|----------|
| 클래스/메서드 명명 | 기존 코드 스타일 따름, 명확한 의도 표현 |
| 패키지 구조 | 기능별 분리 (lifecycle, service, infrastructure) |
| 예외 처리 | 로그 남기고 상위로 전파, 데몬이므로 강제 종료 지양 |
| 테스트 범위 | public 메서드 위주, 경계값/예외 케이스 포함 |
| 인터페이스 추출 | 외부 의존성(HTTP, 설정)은 인터페이스로 추상화 |
| 설정 기본값 | 보안 관련은 안전한 쪽(ON), 기능 제한은 느슨한 쪽(OFF) |

---

## 11. 실행 방법

### 11.1 사전 준비 (사람이 수행)

```bash
# 1. 빈 repo 클론
git clone https://github.com/tanminkwan/mwmanger-auto.git
cd mwmanger-auto

# 2. 이 문서와 레거시 소스 복사
cp /path/to/AI-AUTONOMOUS-REQUIREMENTS.md ./
cp -r /path/to/mwmanger-asis ./mwmanger-asis/
```

### 11.2 AI 자율 실행
- 아래 명령만 사람이 수행
```bash
claude --dangerously-skip-permissions "AI-AUTONOMOUS-REQUIREMENTS.md 문서를 읽고 요구사항에 따라 자율적으로 수행해."
```

### 11.3 완료 후

AI가 모든 작업 완료 후 자동으로 commit & push 수행

---

**문서 버전**: 1.1
**작성일**: 2025-12-18
