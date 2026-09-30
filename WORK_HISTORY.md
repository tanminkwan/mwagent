# Work History - MwManger Agent

## 2026-09-30 - PLAN-001 오픈소스 공개 준비: 보안·의존성·CI (v0.11.0)

### 작업 브랜치
- `main` 브랜치에서 작업. 커밋 `0945c9e`(TLS·Zip Slip·로그), `3e789ff`(SAST 6·7순위), `935d45c`(ssl_verify 문서),
  `1ebb7bd`(Kafka 제거), `c1673d2`(E2E 발견 수정), `f3efe17`(위협 모델·SAST 분류), 이번 커밋(호환·CI·라이선스)

### 완료된 작업

#### 1. 보안 수정 (WS-3)
- ✅ HTTPS 검증 옵션 `ssl_verify` — 기본 false(기존 동작), true 면 `truststore.path` 또는 JVM cacerts + 호스트명 검증
- ✅ Zip Slip 차단, 서버 응답 파일명 검증, `download_n_unzip` http/https 한정, `applyChmod` 인자 배열화
- ✅ `command_class` 허용 목록(`OrderCaller.resolveOrderClass`), MQTT payload 1 MiB 상한
- ✅ 로그: 응답 본문·결과 원문 제거, `LogSafe` 마스킹, `SafeLogFormatter` CR/LF 이스케이프, HTTP 실패 원인 기록
- ✅ `SSLCertiFunc.matchesDomain` 결함(`split(".")`), `Locale.ROOT`, JMX 이름 검증, URL 경로 인코딩, cmd.exe 절대 경로
- ✅ 위협 모델 `docs/SPEC_001_threat_model.md`, SAST 분류 `docs/PLAN-001/TASK_3-7_sast_triage.md` (SpotBugs 327 → 285)

#### 2. 의존성 (WS-2)
- ✅ **Kafka 기능 제거** (사용자 결정, mwm-app 확인: app 에 Kafka 없음). 결과는 `result_receiver` 와 무관하게 항상 REST
- ✅ 런타임 jar 13 → 7. bcprov-jdk15on 1.70 → bcprov-jdk18on 1.86, json-simple 의 junit 제외. Trivy·Grype 0건
- ✅ `verify-lib` 이 SHA256SUMS 에 없는 옛 jar 를 잡고 `download-dependencies` 가 지운다

#### 3. Java 8/17 호환 (WS-4)
- ✅ 알려진 테스트 실패 3건 해결 → 0건 (ExtractLog·SecurityValidator 테스트를 스펙에 맞춤, `isValidAbsolutePath` 는 상대 경로 거부)
- ✅ JDK 9+ 는 `--release 8` (pom profile `release-8`, `build-offline.*`)
- ✅ JUnit 5.14.4, Mockito 4.11.0, AssertJ 3.27.7, Surefire 3.5.4, compiler 3.13.0, JaCoCo 0.8.12 (라인 커버리지 기준 0.47)
- ✅ JDK 8·17 에서 `mvn verify` 통과 (399 실행, 실패 0, 통합 테스트 26 skip)

#### 4. CI·라이선스·문서 (WS-5·6·7)
- ✅ `.github/workflows/ci.yml` (build-test 8/17, lib-integrity, secrets, deps, sast-spotbugs), `codeql.yml`, `dependabot.yml`
- ✅ 기준선: `config/spotbugs-exclude.xml`, `.gitleaksignore`. 규약은 mwm-app 과 맞춤 (SHA 고정 action, gitleaks v8.30.1 dir 모드)
- ✅ `NOTICE`, `THIRD_PARTY_LICENSES.md`, `licenses/`, `sbom/mwagent.cdx.json` (CycloneDX 1.6, `sbom/generate.sh`, 재현 가능)
- ✅ `SECURITY.md`, `CONTRIBUTING.md`(호환 정책), `CODE_OF_CONDUCT.md`(Contributor Covenant 2.1), 이슈·PR 템플릿
- ✅ `Version.java` - `0000.0010.0002` → `0000.0011.0000`

#### 5. mwm-app 연동 확인 (app 세션과 협업)
- ✅ 로컬 app 에 E2E: TLS 모드 4가지, command_sender × result_receiver 4조합 모두 REST 로 결과 도착, `X-Mqtt-Status` 정상
- ✅ app 의 `result_receiver` 선택지에 MQTT 가 있어 예전 agent 는 결과를 버렸음 → agent 는 항상 REST 로 보냄

### 남은 것
- LICENSE 저작권자 표기(app 과 결정), CODE_OF_CONDUCT 연락처, SECURITY 대응 기한 확정
- 저장소 설정(브랜치 보호, secret scanning), README 사내 전용 내용 정리(공개본 생성 WS-1 과 함께)
- TLS 1.3 허용 검토(AIX 확인 필요), OWASP Dependency-Check(NVD API 키), Windows 에서 `.bat`·`.ps1` 확인

## 2026-09-29 - MQTT 수신 상태 보고 (X-Mqtt-Status) (v0.10.2)

### 작업 브랜치
- `main` 브랜치에서 작업 (커밋 `c28bae7`, `5fc797b`, push 완료)

### 완료된 작업

#### 1. 명령 폴링에 `X-Mqtt-Status` 헤더 추가
- ✅ 주기적 명령 조회 `GET {get_command_uri}/{agent_id}` 에만 헤더를 싣는다
  - `mqtt_enabled=true` 일 때만 전송, `false` 면 헤더 자체가 없음
  - BOOT 조회와 `POST /api/v1/command/result` 에는 붙이지 않음 (BOOT 시점엔 MQTT 기동 전)
  - MQTT 는 구독 전용 유지 — 상태 보고도 기존 REST 폴링으로만 한다 (publish/LWT 없음)
- ✅ 형식: `{state}[;since={epoch s}][;events={n}][;last_msg={epoch s}][;reason={text}]`

| state | 의미 | 필드 |
|-------|------|------|
| `connected` | 브로커 연결됨 | since, events, last_msg(수신 이력 있을 때) |
| `unstable` | 한 번 붙은 뒤 끊김, Paho 자동 재접속 중 | since, events, last_msg, reason |
| `never_connected` | 기동 후 한 번도 못 붙음, 60초마다 재시도 | events, reason, since(첫 감시 주기 후) |
| `not_started` | 구독자 미기동 | reason = `mqtt_broker_address not set` / `start_failed` / `not_running` |

- ✅ `events` 는 마지막 복구 판정(연결 60초 유지) 이후 끊김·에러 횟수 — 복구 시 0 으로 리셋
- ✅ `reason` 은 `;`·제어문자·비 ASCII 를 `_` 로 치환, 최대 120자

#### 2. 변경 파일
- ✅ `MwMqttSubscriber.java` - `lastMessageAt` 기록(`messageArrived`), `statusHeader()`, `headerSafe()`
- ✅ `MqttService.java` - `statusHeader()` (paho jar 누락 시에도 `LinkageError` 없이 동작)
- ✅ `Common.java` - `httpGET(path, token, extraHeaders)` 오버로드 (기존 2인자 시그니처 유지)
- ✅ `AgentLifecycleManager.java` - `pollCommands()` 에 헤더 연결, 상태 생성 실패 시 헤더만 생략하고 폴링 계속
- ✅ `Version.java` - `0000.0010.0001` → `0000.0010.0002`
- ✅ `CLAUDE.md` - Recent Fixes 8번 항목 추가

#### 3. mwm-app 연동 확인 (app 세션과 협업)
- ✅ 헤더 형식(세미콜론 key=value) 확정, broadcast probe 기능은 이번에 하지 않음
- ✅ 로컬 개발 agent + 앱으로 실측 확인
  - `connected` 저장, MQTT 명령 수신 후 `last_msg` 반영
  - 브로커 중지(15:33:06) → `unstable;events=1;reason=rc=32109 Connection lost`
  - 브로커 재시작 → `connected`, `events=1` → 60초 뒤 0 으로 리셋
  - `mqtt_enabled=false` 재기동(16:53:28) → 헤더 없음 → 앱 MQTT 집계·필터에서 제외
  - `mqtt_enabled=true` 복구(16:56:05) → 다시 `connected` 로 복귀
- 앱 쪽 결과 기록: mwm-app 저장소 `docs/HOWTO_019` §9.3

### 테스트 결과
- 12개 테스트 추가 (`MwMqttSubscriberTest`, `MqttServiceTest`, 신규 `AgentLifecycleManagerMqttStatusTest`)
- temp_jdk 로 main/test 컴파일 확인, 리플렉션 러너로 MQTT 관련 3개 클래스 49개 통과
- 전체 `mvn test` 는 이 환경(리눅스, Maven 깨짐)에서 미실행 — Windows 에서 확인 필요

---

## 2025-12-05 - Phase 6: Code Quality Improvements (v0.9.9)

### 작업 브랜치
- `refactoring_major_202511` 브랜치에서 작업

### 완료된 작업

#### 1. Version 관리 단일화
- ✅ `Version.java` 생성 - 유일한 버전 소스
- ✅ `version.properties` 삭제
- ✅ `Config.java` - `Version.VERSION` 참조로 변경
- ✅ 빌드 스크립트 단순화 (버전 없는 JAR 파일명)

#### 2. 코드 정리
- ✅ Auto-generated TODO 주석 제거 (3개)
- ✅ 불필요한 코드 제거

#### 3. 빌드 시스템 개선
- ✅ JAR 파일명 단순화: `mwmanger.jar` (버전 제외)
- ✅ `build-offline.bat`, `build-offline.sh` 업데이트
- ✅ `pom.xml`, `build.gradle` 업데이트

#### 4. 문서 업데이트
- ✅ `README.md` - 버전 관리 섹션 업데이트
- ✅ `CLAUDE.md` - 프로젝트 메모리 추가
- ✅ `WORK_HISTORY.md` - 작업 이력 추가

### 테스트 결과
- 215개 테스트 실행
- 0개 실패
- 7개 스킵 (통합 테스트 - 외부 서버 필요)

---

## 2025-12-04 - Phase 5: Biz Service & Integration Testing (v0.9.6)

### 작업 브랜치
- `refactoring_major_202511` 브랜치에서 작업

### 완료된 작업

#### 1. Sample Biz Service 구현
- ✅ Flask 기반 Biz Service 생성 (`biz-service/`)
  - `app.py` - Flask API 엔드포인트
  - `token_validator.py` - JWT 토큰 검증 데코레이터
  - `config.py` - 설정 파일
- ✅ JWT 토큰 검증 구현 (PyJWT)
  - `@require_token` - Bearer 토큰 검증
  - `@require_scope` - scope 기반 권한 검증
- ✅ API 엔드포인트
  - `/api/whoami` - 에이전트 정보 반환
  - `/api/commands` - 명령 조회
  - `/api/results` - 결과 전송
  - `/api/config` - 설정 조회

#### 2. Integration Tests 구현
- ✅ `BizServiceIntegrationTest.java` - E2E 테스트
  - Auth Server → Biz Service 토큰 흐름 검증
  - 10개 테스트 케이스 (모두 통과)
- ✅ `SSLCertiFuncTest.java` - SSL 인증서 테스트
  - exeCommand를 통한 localhost SSL 테스트
  - SNI 기반 도메인 필터링 검증
  - 9개 테스트 케이스 (모두 통과)
- ✅ `SSLCertiFileFuncTest.java` - 인증서 파일 테스트
  - 로컬 .crt 파일 파싱 테스트
  - 6개 테스트 케이스 (모두 통과)

#### 3. 문서화
- ✅ `docs/mTLS-JWT-Authentication-Flow.md`
  - mTLS 인증서 구조 설명
  - Certificate DN → JWT Claims 매핑
  - 검증 흐름 다이어그램
- ✅ `docs/Token-Validation-Architecture.md`
  - Token Introspection vs Redis 방식 비교
  - mTLS 기반 서비스간 신뢰 구조
- ✅ `biz-service/README.md` - Biz Service 사용 가이드

#### 4. 테스트 환경 개선
- ✅ Mock Server SSL 모드 (`--ssl` 옵션)
- ✅ 테스트 환경 변수 기반 실행
  - `SSL_CERT_INTEGRATION_TEST=true`
  - `BIZ_SERVICE_INTEGRATION_TEST=true`

### 버전 업데이트
- **0000.0009.0005** → **0000.0009.0006**
- pom.xml, build.gradle 업데이트

### 테스트 결과
```
Total Tests: 200+
✓ Unit Tests: 187 (기존)
✓ BizServiceIntegrationTest: 10
✓ SSLCertiFuncTest: 9
✓ SSLCertiFileFuncTest: 6
```

### 새로운 파일
```
biz-service/
├── app.py
├── config.py
├── token_validator.py
├── requirements.txt
└── README.md

docs/
├── mTLS-JWT-Authentication-Flow.md
└── Token-Validation-Architecture.md

src/test/java/mwmanger/
├── integration/BizServiceIntegrationTest.java
└── agentfunction/
    ├── SSLCertiFuncTest.java
    └── SSLCertiFileFuncTest.java
```

---

## 2025-11-20 (이전 작업)

### 작업 브랜치
- `refactoring_major_202511` 브랜치에서 작업

### 완료된 작업

#### 1. 빌드 및 실행 문제 해결
- ✅ build-offline.sh 실행 후 JAR 실행 방법 파악
- ✅ RedHat Linux에서 classpath 이슈 해결
  - 문제: `java -cp ".:lib/*" mwmanger.MwAgent` 실패
  - 원인: `.`은 클래스 파일만 참조, JAR 파일은 명시 필요
  - 해결: `java -cp "build/jar/mwmanger-0000.0009.0001.jar:lib/*" mwmanger.MwAgent`

#### 2. 등록(Registration) 모듈화 완료 🎉
**목표**: 최초 실행 시 가입(register) 단계를 모듈화

**Before**: PreWork.java (150줄)
- 모든 로직이 한 클래스에 집중
- 테스트 불가능, 재사용 불가능
- Config 싱글톤에 강하게 결합

**After**: 모듈화된 구조 (6개 클래스)
- ✅ `AgentStatus.java` - 상태 enum (type-safe)
  - NOT_REGISTERED, PENDING_APPROVAL, APPROVED, etc.
  - 매직 넘버(-1, -2) 제거
- ✅ `RegistrationRequest.java` - 등록 요청 VO
- ✅ `RegistrationResponse.java` - 등록 응답 VO
- ✅ `RegistrationService.java` - Agent 등록 로직
- ✅ `AgentStatusService.java` - Agent 상태 확인
- ✅ `BootstrapService.java` - 전체 등록 프로세스 관리
- ✅ `PreWork.java` 리팩토링 (150줄 → 40줄, 73% 감소)

**구조**:
```
src/main/java/mwmanger/
├── service/registration/
│   ├── BootstrapService.java          # 전체 등록 프로세스 조율
│   ├── RegistrationService.java       # Agent 등록만 담당
│   └── AgentStatusService.java        # Agent 상태 확인만 담당
├── vo/
│   ├── AgentStatus.java               # 상태 enum
│   ├── RegistrationRequest.java       # 등록 요청
│   └── RegistrationResponse.java      # 등록 응답
└── PreWork.java (40줄)                 # 단순한 wrapper
```

**개선 효과**:
1. 단일 책임 원칙 (SRP) 준수
2. 테스트 용이성 (DI constructor 제공)
3. 재사용성 향상
4. 타입 안정성 (enum 사용)
5. 코드 가독성 대폭 향상

#### 3. 빌드 및 검증
- ✅ `build-offline.bat` 실행 성공
- ✅ 새로운 모듈 JAR에 포함 확인
  - mwmanger/service/registration/*.class
  - mwmanger/vo/AgentStatus.class
  - mwmanger/vo/RegistrationRequest.class
  - mwmanger/vo/RegistrationResponse.class

### 현재 상태

#### Git 상태
- 브랜치: `refactoring_major_202511`
- 변경된 파일:
  - Modified: `src/main/java/mwmanger/PreWork.java`
  - New: `src/main/java/mwmanger/service/registration/*.java` (3 files)
  - New: `src/main/java/mwmanger/vo/AgentStatus.java`
  - New: `src/main/java/mwmanger/vo/RegistrationRequest.java`
  - New: `src/main/java/mwmanger/vo/RegistrationResponse.java`
- 커밋 필요: Yes

### 다음 작업 (Phase 2: Critical 보안 취약점 수정)

REFACTORING_PLAN.md의 Phase 2를 진행해야 합니다:

#### 우선순위 CRITICAL
1. [ ] **Command Injection 수정** (ExeShell.java:50)
   - ProcessBuilder 사용으로 전환
   - Command Whitelist 구현
   - ExeScript.java, ExeText.java도 동일 적용

2. [ ] **Path Traversal 수정** (DownloadFile.java, ReadFile.java)
   - PathValidator 구현
   - Canonical path 검증

3. [ ] **토큰 로깅 제거** (Common.java:268, 317)
   - refresh_token, access_token 로깅 삭제
   - 민감 정보 노출 방지

4. [ ] **동시성 버그 수정**
   - MwConsumerThread.java:83 - 논리 연산자 수정
   - SuckSyperFunc.java:63 - null 체크 수정

#### 참고 문서
- `REFACTORING_PLAN.md` - 전체 리팩토링 계획
- Phase 2 상세 내용: REFACTORING_PLAN.md:132-257

---

## 2025-01-23 (이전 작업)

### 작업 브랜치
- `refectoring_202511` 브랜치에서 작업

### 완료된 작업

#### 1. JDK 1.8 호환성 설정
- ✅ `pom.xml` 생성 - Maven 빌드 설정 (JDK 1.8 타겟)
- ✅ `build.gradle` 생성 - Gradle 빌드 설정
- ✅ 모든 필수 라이브러리 의존성 정의
  - Apache HttpClient 4.5.14
  - Apache Kafka Client 2.8.2
  - BouncyCastle 1.70 (AIX용)
  - JSON Simple 1.1.1
  - Commons Codec 1.15
  - SLF4J 1.7.36

#### 2. 테스트 환경 구축
- ✅ `src/test/java/` 디렉토리 구조 생성
- ✅ JUnit 5, Mockito, AssertJ 의존성 추가
- ✅ 테스트 코드 작성:
  - `CommandVOTest.java` - CommandVO 테스트
  - `ResultVOTest.java` - ResultVO 테스트
  - `CommonTest.java` - Common 유틸리티 테스트
  - `OrderTest.java` - Order 추상 클래스 테스트
  - `AgentFuncFactoryTest.java` - Factory 패턴 테스트
- ✅ `test-agent.properties` - 테스트용 설정 파일

#### 3. 문서 작성
- ✅ `DEPENDENCIES.md` - 상세 의존성 문서
  - 각 라이브러리 용도 및 버전
  - JDK 1.8 호환성 확인
  - 빌드 및 실행 방법
- ✅ `TESTING.md` - 테스트 가이드
  - 테스트 실행 방법
  - 테스트 작성 가이드라인
  - CI/CD 통합 예시
- ✅ `README.md` 업데이트
  - 시스템 요구사항 추가
  - 필수 라이브러리 섹션 추가
  - 빌드 방법 추가
  - 테스트 섹션 추가
- ✅ `README_TESTS.md` - 테스트 상세 문서

#### 4. 빌드 및 테스트 스크립트
- ✅ `run-tests.bat` - Windows용 테스트 실행 스크립트
- ✅ `run-tests.sh` - Linux/Mac용 테스트 실행 스크립트

#### 5. 데모 및 예제
- ✅ `TestDataDemo.java` - 테스트 데이터 개념 설명 프로그램
- ✅ `DirectTest.java` - 한글 버전 데모
- ✅ `QuickTest.java` - 간단한 테스트 실행기

#### 6. Git 설정
- ✅ `.gitignore` 업데이트
  - Maven/Gradle 빌드 결과물 제외
  - IDE 설정 파일 제외
  - `.claude/` 디렉토리 제외

### 현재 상태

#### 커밋 상태
- 브랜치: `refectoring_202511`
- 상태: 변경사항 staged 안 됨
- Push 상태: 브랜치는 origin에 있음

#### 테스트 상태
- ✅ 테스트 코드 작성 완료
- ✅ 테스트 데이터 개념 이해
- ✅ 간단한 Java 테스트 실행 성공
- ⚠️ Maven/Gradle로 정식 테스트는 미실행 (도구 미설치)

### 다음 작업 TODO

#### 우선순위 높음
- [ ] 현재 변경사항 커밋
- [ ] 불필요한 데모 파일 정리 (TestDataDemo.java 등)
- [ ] Maven 설치 후 `mvn test` 실행해서 모든 테스트 검증

#### 우선순위 중간
- [ ] 추가 테스트 작성
  - PreWork 클래스 테스트
  - MainWork 클래스 테스트
  - 개별 Order 구현체 테스트
  - 개별 AgentFunc 구현체 테스트
- [ ] 통합 테스트 작성 (Kafka, HTTP)
- [ ] 테스트 커버리지 리포트 생성

#### 우선순위 낮음
- [ ] CI/CD 파이프라인 설정 (GitHub Actions)
- [ ] Jacoco 코드 커버리지 설정
- [ ] SonarQube 정적 분석 설정

### 주요 파일 목록

```
mwmanger/
├── pom.xml                          # Maven 빌드 설정
├── build.gradle                     # Gradle 빌드 설정
├── .gitignore                       # Git 제외 설정
├── README.md                        # 프로젝트 메인 문서
├── DEPENDENCIES.md                  # 의존성 상세 문서
├── TESTING.md                       # 테스트 가이드
├── WORK_HISTORY.md                  # 이 파일
├── run-tests.bat                    # Windows 테스트 스크립트
├── run-tests.sh                     # Linux/Mac 테스트 스크립트
└── src/
    └── test/
        ├── java/mwmanger/
        │   ├── vo/
        │   │   ├── CommandVOTest.java
        │   │   └── ResultVOTest.java
        │   ├── common/
        │   │   └── CommonTest.java
        │   ├── order/
        │   │   └── OrderTest.java
        │   ├── agentfunction/
        │   │   └── AgentFuncFactoryTest.java
        │   └── README_TESTS.md
        └── resources/
            └── test-agent.properties
```

### 중요 결정사항

1. **JDK 1.8 호환**: 모든 라이브러리 JDK 1.8 호환 버전 선택
2. **테스트 프레임워크**: JUnit 5 + Mockito + AssertJ 조합
3. **빌드 도구**: Maven과 Gradle 둘 다 지원
4. **테스트 데이터**: 간단하고 예측 가능한 값 사용 (예: "CMD-123", "server01")

### BouncyCastle 사용처

질문이 있었던 부분:
- `Common.java:70` - AIX에서만 TLS 1.2 Security Provider로 사용
- `SSLCertiFunc.java:132` - SSL 인증서 확인 시 AIX에서만 사용
- **결론**: AIX 환경에서만 필수, 다른 OS에서는 사용 안 함

### 학습한 내용

**테스트 데이터란?**
- 테스트할 때 사용하는 가짜 입력값
- 실제 프로덕션 데이터 대신 사용
- 간단하고 예측 가능하며 안전함
- 예시: "CMD-123" (간단) vs "CMD-2025-01-23-0001" (실제)

---

**Last Updated**: 2025-01-23
**Branch**: refectoring_202511
**Status**: Work in progress
