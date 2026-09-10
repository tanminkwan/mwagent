# MwManger Agent - AI 자율 수행 작업 지침서

> **목적**: MwManger 프로젝트의 유지보수/개선 작업 시 AI가 참조하는 고정 지침
>
> **사용 방법**: 프로젝트별 요구사항 문서와 함께 이 문서를 AI에게 제공

---

## 1. 프로젝트 정보

| 항목 | 내용 |
|------|------|
| **프로젝트명** | MwManger Agent |
| **언어** | Java 1.8 |
| **유형** | 원격 서버 관리 에이전트 (데몬 프로세스) |
| **빌드 도구** | Gradle (Wrapper 사용) |

---

## 2. 소스 위치

| 항목 | 경로 |
|------|------|
| **메인 소스** | `src/main/java/mwagent/` |
| **테스트 소스** | `src/test/java/mwagent/` |
| **설정 파일** | `agent.properties` |

> AI는 작업 시작 시 소스 구조를 직접 분석한다.

---

## 3. 설정 파일 형식 (agent.properties)

```properties
# 서버 설정
server_url=https://server.example.com
get_command_uri=/api/v1/command/getCommands
post_agent_uri=/api/v1/agent

# 인증
token=REFRESH_TOKEN_VALUE
use_mtls=false

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

# mTLS 설정 (use_mtls=true 시)
keystore_path=/path/to/client.p12
keystore_password=changeit
truststore_path=/path/to/truststore.jks
truststore_password=changeit
```

---

## 4. API 스펙

### 4.1 Legacy 인증 API

```
POST /api/v1/security/refresh
Content-Type: application/json
Authorization: Bearer {refresh_token}

Request:
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

### 4.2 OAuth2 토큰 API (mTLS)

```
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

Request:
grant_type=client_credentials

Response:
{
    "access_token": "...",
    "refresh_token": "...",
    "token_type": "Bearer",
    "expires_in": 3600
}
```

### 4.3 명령 조회 API

```
GET /api/v1/command/getCommands
Authorization: Bearer {access_token}

Response:
{
    "commands": [
        {
            "command_id": "...",
            "command_type": "ExeShell",
            "command": "..."
        }
    ]
}
```

---

## 5. 빌드 및 테스트

### 5.1 Gradle 명령어

```bash
# Gradle Wrapper 생성 (최초 1회)
gradle wrapper

# 테스트 실행
./gradlew test

# 빌드
./gradlew clean build

# JAR 생성
./gradlew jar
```

### 5.2 통합 테스트 실행

```bash
# 테스트 서버 시작 (별도 터미널)
cd test-server && python mock_server.py --ssl &
cd biz-service && python app.py &
cd ca-server && python app.py &

# 환경변수 설정 후 테스트
MTLS_INTEGRATION_TEST=true \
BIZ_SERVICE_INTEGRATION_TEST=true \
SSL_CERT_INTEGRATION_TEST=true \
./gradlew test
```

---

## 6. 테스트 서버

| 서버 | 디렉토리 | 포트 | 용도 |
|------|----------|------|------|
| Mock Auth Server | `test-server/` | 8443 (HTTPS/mTLS) | OAuth2 토큰 발급, mTLS 인증서 검증 |
| Biz Service | `biz-service/` | 8080 (HTTP) | JWT 검증, 비즈니스 API |
| CA Server | `ca-server/` | 8444 (HTTPS) | 인증서 발급/갱신/폐기 |

### 테스트 인증서

```
test-server/certs/
├── ca.crt, ca.key           # Root CA
├── server.crt, server.key   # Mock Auth Server용
├── client.crt, client.key   # 에이전트 클라이언트용
└── client.p12               # PKCS12 형식 (Java용)
```

---

## 7. 핵심 규칙 (MUST FOLLOW)

1. **인증 모드 양립**: mTLS 모드와 Legacy 모드 둘 다 동작해야 함
2. **버전 단일 관리**: `Version.java`의 `VERSION` 상수만 수정
3. **파일 로그만 사용**: System.err 출력 금지 (데몬 프로세스)
4. **JDK 1.8 호환**: Java 8 문법만 사용
5. **하위 호환성 유지**: 기존 클래스명, 설정 키, API 엔드포인트 유지

---

## 8. 설계 원칙

| 원칙 | 적용 방법 |
|------|----------|
| **Test-Driven** | 기능 구현 전 테스트 코드 먼저 작성 |
| **SOLID** | 단일 책임, 인터페이스 분리, 의존성 역전 적용 |
| **Phase 분할** | 독립적으로 완료/검증 가능한 단위로 작업 분할 |
| **문서화** | AI가 컨텍스트를 빠르게 파악할 수 있는 CLAUDE.md 작성 |
| **설정의 외부화** | 설정값은 코드에 하드코딩하지 않고 외부 파일로 분리 |

---

## 9. Git 브랜치 전략

### 9.1 Phase별 브랜치

| Phase | 브랜치명 패턴 | 베이스 |
|-------|--------------|--------|
| Phase 0 | `main` | - (초기 커밋) |
| Phase N | `phaseN-[작업명]` | `phase(N-1)-[작업명]` |

### 9.2 작업 흐름

```bash
# Phase N 시작
git checkout phase(N-1)-xxx
git checkout -b phaseN-xxx

# 작업 수행...

# Phase N 완료
git add .
git commit -m "Phase N 완료: [내용 요약]"
git push -u origin phaseN-xxx
```

---

## 10. Phase 작업 흐름 (공통)

```
┌─────────────────────────────────────────────────────────────┐
│  각 Phase는 반드시 아래 순서를 따른다:                         │
│                                                             │
│  1. 브랜치 생성 (git checkout -b phaseN-xxx)                 │
│  2. Coding (기능 구현)                                       │
│  3. Test Code 작성 (단위 테스트)                              │
│  4. Test 실행 및 통과 확인 (./gradlew test)                   │
│  5. Test 결과 보고 (통과한 테스트 수)                          │
│  6. Git Commit (Phase 완료 커밋)                             │
│  7. 브랜치 Push (git push -u origin phaseN-xxx)              │
│  8. 다음 Phase 진행                                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 11. Phase 완료 조건

1. **테스트 100% 통과**: `./gradlew test` 성공
2. **회귀 버그 없음**: 이전 Phase 테스트도 모두 통과
3. **브랜치 Push**: `git push -u origin phaseN-xxx`
4. **테스트 보고**: 통과한 테스트 수 출력

---

## 12. 의사결정 가이드

| 상황 | 결정 기준 |
|------|----------|
| 인증 방식 선택 | `use_mtls` 설정값 따름, 미설정 시 Legacy 모드 |
| 토큰 저장 | 메모리에만 유지, 파일 저장 금지 |
| 명령 실행 | 허용 목록(whitelist) 기반 검증 |
| 경로 접근 | `allowed_paths` 설정 내 경로만 허용 |
| Kafka 사용 | `kafka_broker_address` 설정 시에만 활성화 |
| 클래스/메서드 명명 | 기존 코드 스타일 따름 |
| 예외 처리 | 로그 남기고 상위로 전파, 강제 종료 지양 |

---

## 13. 실행 방법

### 13.1 사전 준비 (사람이 수행)

```bash
# 1. 저장소 클론
git clone https://github.com/tanminkwan/mwmanger-auto.git
cd mwmanger-auto

# 2. 요구사항 문서 배치
cp [요구사항문서].md ./
cp AI-AUTONOMOUS-GUIDELINES.md ./
```

### 13.2 AI 자율 실행

```bash
claude --dangerously-skip-permissions "[요구사항문서].md와 AI-AUTONOMOUS-GUIDELINES.md를 읽고 요구사항에 따라 자율적으로 수행해."
```

---

**문서 버전**: 1.0
**작성일**: 2025-12-22
