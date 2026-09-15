# MwManger - Leebalso Agent

MwManger는 Leebalso(리발소) 프로젝트의 에이전트 프로그램으로, 각 서버에서 데몬으로 실행되면서 중앙 Leebalso 서버로부터 명령을 전달받아 수행하는 Java 기반 원격 관리 에이전트입니다.

## 목차
- [프로젝트 개요](#프로젝트-개요)
- [주요 특징](#주요-특징)
- [시스템 요구사항](#시스템-요구사항)
- [필수 라이브러리](#필수-라이브러리)
- [빌드 방법](#빌드-방법)
- [버전 관리](#버전-관리)
- [시스템 아키텍처](#시스템-아키텍처)
- [프로젝트 구조](#프로젝트-구조)
- [설정 방법](#설정-방법)
- [실행 방법](#실행-방법)
- [실행 흐름](#실행-흐름)
- [명령 처리 방식](#명령-처리-방식)
- [통신 방식](#통신-방식)
- [지원 운영체제](#지원-운영체제)

## 프로젝트 개요

MwManger는 분산 환경의 서버 관리를 자동화하기 위한 에이전트 프로그램입니다. 중앙 Leebalso 서버의 지시에 따라 다양한 작업을 수행하며, 실시간 명령 수신 및 결과 전송을 지원합니다.

**버전**: 0000.0009.0006
**타입**: JAVAAGENT

## 주요 특징

### Core Features
- **생명주기 관리**: 체계적인 lifecycle 기반 아키텍처 (Bootstrap → Initialization → Runtime → Shutdown)
- **Graceful Shutdown**: 실행 중인 작업 완료 대기, 자원 정리, 로그 flush
- **자동 에이전트 등록**: 최초 실행 시 중앙 서버에 자동 등록
- **다중 통신 채널**: HTTP/HTTPS, Apache Kafka, MQTT v5 를 통한 명령 수신
- **실시간 명령 수신 (MQTT)**: 폴링 주기를 기다리지 않는 즉시 수신. 기본 비활성이며 결과 전송은 REST 유지
- **비동기 명령 처리**: ThreadPool을 이용한 동시 다발적 명령 처리

### Security & Reliability
- **보안 통신**: TLS 1.2 지원, JWT 기반 인증(Access Token, Refresh Token)
- **mTLS 인증**: 클라이언트 인증서 기반 상호 인증 (RFC 8705)
- **계단식 토큰 갱신**: refresh_token → mTLS 자동 fallback
- **Command Injection 방어**: SecurityValidator를 통한 입력 검증
- **Path Traversal 방어**: 경로 탐색 공격 차단
- **토큰 로깅 마스킹**: 민감 정보 보호 (끝 10자리만 표시)
- **상태 관리**: Type-safe state transitions (CREATED → STARTING → RUNNING → STOPPING → STOPPED)
- **에러 처리**: 포괄적인 예외 처리 및 복구 메커니즘

### Extensibility
- **확장 가능한 구조**: 플러그인 방식의 Order 및 AgentFunction 추가 가능
- **의존성 주입 (DI)**: ApplicationContext 기반 DI 컨테이너 (Phase 3)
- **인터페이스 추상화**: ConfigurationProvider, HttpClient 인터페이스
- **서비스 분리**: 각 기능이 독립적인 서비스로 분리 (KafkaService, MqttService, CommandExecutorService)
- **테스트 용이성**: Mock 객체 주입으로 단위 테스트 지원
- **크로스 플랫폼**: Windows, Linux, AIX, HP-UX 지원

## 시스템 요구사항

- **JDK**: 1.8 (Java 8) 이상
- **메모리**: 최소 256MB
- **디스크**: 최소 100MB
- **네트워크**: Leebalso 서버 및 Kafka 브로커 접근 가능

## 필수 라이브러리

프로젝트는 다음 외부 라이브러리에 의존합니다 (JDK 1.8 호환):

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| Apache HttpClient | 4.5.13 | HTTP/HTTPS 통신 |
| Apache Kafka Client | 3.1.0 | Kafka 메시징 |
| Eclipse Paho MQTT v5 | 1.2.5 | MQTT 명령 구독 |
| BouncyCastle | 1.70 | TLS 1.2 지원 (AIX) |
| JSON Simple | 1.1.1 | JSON 처리 |
| Apache Commons Codec | 1.11 | 인코딩 유틸리티 |
| SLF4J | 1.7.30 | 로깅 (Kafka 의존성) |

자세한 의존성 정보는 [DEPENDENCIES.md](DEPENDENCIES.md) 참조

## 빌드 방법

### 오프라인 빌드 (인터넷 차단 환경)

**1단계: 의존성 다운로드 (인터넷 연결된 환경에서)**

```bash
# Linux/Mac
./download-dependencies.sh

# Windows
download-dependencies.bat
```

**2단계: 오프라인 환경으로 전체 프로젝트 복사**

다음 디렉토리/파일들을 복사:
- `src/` - 소스 코드
- `lib/` - 다운로드된 JAR 파일들 (12개)
- `build-offline.sh` 또는 `build-offline.bat`

**3단계: 오프라인 빌드 실행**

```bash
# Linux/Mac
./build-offline.sh

# Windows
build-offline.bat

# 생성된 파일
build/jar/mwagent-0000.0009.0006.jar
```

자세한 내용은 [lib/README.md](lib/README.md) 참조

### Maven 사용 (온라인 환경)

```bash
# 의존성 포함 실행 가능 JAR 생성
mvn clean package

# 테스트 실행
mvn test

# 생성된 파일
target/mwagent-0000.0009.0006-jar-with-dependencies.jar
```

### Gradle 사용 (온라인 환경)

```bash
# Fat JAR 생성
gradle fatJar

# 테스트 실행
gradle test

# 생성된 파일
build/libs/mwagent-all-0000.0009.0006.jar
```

## 버전 관리

프로젝트는 **단일 소스 버전 관리 시스템**을 사용합니다.

### 버전 관리 원칙

**Version.java**가 유일한 버전 소스(Single Source of Truth)입니다:

```
src/main/java/mwagent/common/Version.java
    │
    └─→ Config.java (Version.VERSION 참조)
            ↓
        런타임에 버전 제공
```

### 버전 변경 방법

버전을 변경하려면 **Version.java 파일 한 곳만 수정**하면 됩니다:

```java
// src/main/java/mwagent/common/Version.java
public static final String VERSION = "0000.0010.0000";  // 여기만 수정!
```

그 후 빌드하면 자동으로 반영됩니다:

```bash
# Windows (Git Bash)
/c/Windows/System32/cmd.exe //c "cd /d C:\GitHub\mwmanger && build-offline.bat"

# Linux/Mac
./build-offline.sh
```

### JAR 파일명

빌드 결과물은 버전 없이 `mwagent.jar`로 생성됩니다:
- `build/mwagent.jar`

### 버전 형식

```
0000.0009.0001
  │    │    └─ Patch (버그 수정)
  │    └────── Minor (기능 추가)
  └─────────── Major (큰 변경)
```

## 시스템 아키텍처

### Phase 1: Lifecycle-Based Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Leebalso Server                       │
│  (Central Command Server + Kafka Broker)                │
└────────────────┬────────────────────┬───────────────────┘
                 │                    │
         HTTP/HTTPS (Polling)    Kafka (Push)
                 │                    │
┌────────────────┴────────────────────┴───────────────────┐
│                 MwManger Agent                           │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │  AgentLifecycleManager                             │ │
│  │  ┌──────────┐  ┌──────────┐  ┌────────────────┐  │ │
│  │  │Bootstrap │→ │   Init   │→ │   Runtime      │  │ │
│  │  │(등록/승인)│  │(Kafka 등) │  │(명령 처리)     │  │ │
│  │  └──────────┘  └──────────┘  └────────────────┘  │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Services                                          │ │
│  │  ┌─────────────────┐  ┌──────────────────────┐   │ │
│  │  │  KafkaService   │  │CommandExecutorService│   │ │
│  │  │  - Consumer     │  │  - ThreadPool        │   │ │
│  │  │  - Producer     │  │  - Order Execution   │   │ │
│  │  │  - HealthCheck  │  │  - Async Processing  │   │ │
│  │  └─────────────────┘  └──────────────────────┘   │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Order Processors                                  │ │
│  │  ┌────────────┐  ┌────────────┐  ┌───────────┐   │ │
│  │  │ExeShell    │  │ReadFile    │  │ExeAgentFunc│  │ │
│  │  │DownloadFile│  │ExeScript   │  │ExeText    │   │ │
│  │  └────────────┘  └────────────┘  └───────────┘   │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │  GracefulShutdownHandler                           │ │
│  │  - Service shutdown (LIFO)                         │ │
│  │  - Resource cleanup                                │ │
│  │  - Log flush                                       │ │
│  └────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### Lifecycle States

```
CREATED → STARTING → RUNNING → STOPPING → STOPPED
                        ↓
                     FAILED
```

## 프로젝트 구조

표준 Maven/Gradle 프로젝트 구조를 따릅니다:

```
mwagent/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── mwagent/
│   │           ├── MwAgent.java                 # 메인 진입점 (Lifecycle 기반)
│   │           ├── PreWork.java                 # Bootstrap 위임 (Legacy)
│   │           ├── FirstWork.java               # Kafka 초기화 (Legacy)
│   │           ├── MainWork.java                # 명령 polling (Legacy)
│   │           ├── OrderCallerThread.java       # 명령 실행 스레드
│   │           ├── ShutdownThread.java          # 종료 처리 (Legacy)
│   │           │
│   │           ├── lifecycle/                   # ★ Phase 1: Lifecycle Framework
│   │           │   ├── AgentLifecycle.java      # Lifecycle 인터페이스
│   │           │   ├── LifecycleState.java      # 상태 enum
│   │           │   ├── AgentLifecycleManager.java # 전체 생명주기 관리
│   │           │   └── GracefulShutdownHandler.java # Graceful shutdown
│   │           │
│   │           ├── service/                     # ★ Phase 1: Service Layer
│   │           │   ├── KafkaService.java        # Kafka 통신 관리
│   │           │   ├── CommandExecutorService.java # 명령 실행 관리
│   │           │   └── registration/            # 등록 서비스
│   │           │       ├── BootstrapService.java
│   │           │       ├── RegistrationService.java
│   │           │       └── AgentStatusService.java
│   │           │
│   │           ├── common/
│   │           │   ├── Config.java              # 설정 관리 (Singleton)
│   │           │   ├── Common.java              # HTTP 통신 유틸리티
│   │           │   └── SecurityValidator.java   # ★ 보안 검증 유틸리티
│   │           │
│   │           ├── order/                       # 명령 실행 모듈
│   │           │   ├── Order.java               # 추상 Order 클래스
│   │           │   ├── OrderCaller.java         # Order 동적 로딩
│   │           │   ├── ExeShell.java            # 쉘 스크립트 실행
│   │           │   ├── ExeScript.java           # 스크립트 실행
│   │           │   ├── ExeText.java             # 텍스트 실행
│   │           │   ├── ExeAgentFunc.java        # Agent Function 실행
│   │           │   ├── ReadFile.java            # 파일 읽기 (추상)
│   │           │   ├── ReadPlainFile.java       # 일반 파일 읽기
│   │           │   ├── ReadFullPathFile.java   # 전체 경로 파일 읽기
│   │           │   ├── DownloadFile.java        # 파일 다운로드
│   │           │   └── GetRefreshToken.java     # Refresh Token 갱신
│   │           │
│   │           ├── agentfunction/               # 에이전트 기능 모듈
│   │           │   ├── AgentFunc.java           # AgentFunc 인터페이스
│   │           │   ├── AgentFuncFactory.java    # Factory 패턴
│   │           │   ├── HelloFunc.java           # Hello World 예제
│   │           │   ├── JmxStatFunc.java         # JMX 통계 수집
│   │           │   ├── SSLCertiFunc.java        # SSL 인증서 정보
│   │           │   ├── SSLCertiFileFunc.java    # SSL 인증서 파일
│   │           │   └── DownloadNUnzipFunc.java  # 파일 다운로드 및 압축해제
│   │           │
│   │           ├── kafka/
│   │           │   ├── MwConsumerThread.java    # Kafka Consumer
│   │           │   ├── MwProducer.java          # Kafka Producer
│   │           │   └── MwHealthCheckThread.java # Kafka 상태 모니터링
│   │           │
│   │           ├── mqtt/                        # ★ MQTT 명령 구독 (기본 비활성)
│   │           │   ├── MqttService.java         # 수명주기 및 연결 감시
│   │           │   └── MwMqttSubscriber.java    # 명령 구독 (구독 전용)
│   │           │
│   │           └── vo/
│   │               ├── CommandVO.java           # 명령 VO
│   │               ├── ResultVO.java            # 결과 VO
│   │               ├── RawCommandsVO.java       # 원시 명령 VO
│   │               ├── MwResponseVO.java        # HTTP 응답 VO
│   │               ├── AgentStatus.java         # ★ Agent 상태 enum
│   │               ├── RegistrationRequest.java # ★ 등록 요청 VO
│   │               └── RegistrationResponse.java# ★ 등록 응답 VO
│   │
│   └── test/
│       ├── java/
│       │   └── mwagent/                        # JUnit 5 단위 테스트 (106개)
│       │       ├── lifecycle/                   # ★ Lifecycle 테스트
│       │       │   ├── LifecycleStateTest.java
│       │       │   └── GracefulShutdownHandlerTest.java
│       │       ├── service/                     # ★ Service 테스트
│       │       │   ├── CommandExecutorServiceTest.java
│       │       │   └── registration/
│       │       │       ├── BootstrapServiceTest.java
│       │       │       └── RegistrationServiceTest.java
│       │       ├── mqtt/                        # ★ MQTT 테스트 (32개)
│       │       │   ├── MqttServiceTest.java
│       │       │   └── MwMqttSubscriberTest.java
│       │       ├── agentfunction/
│       │       │   └── AgentFuncFactoryTest.java
│       │       ├── common/
│       │       │   ├── CommonTest.java
│       │       │   └── SecurityValidatorTest.java  # ★ 보안 검증 테스트
│       │       ├── order/
│       │       │   ├── OrderTest.java
│       │       │   └── ExeShellTest.java
│       │       ├── vo/
│       │       │   ├── CommandVOTest.java
│       │       │   ├── ResultVOTest.java
│       │       │   ├── AgentStatusTest.java
│       │       │   ├── RegistrationRequestTest.java
│       │       │   └── RegistrationResponseTest.java
│       │       └── PreWorkTest.java
│       │
│       └── resources/
│           └── test-agent.properties           # 테스트용 설정
│
├── tools/                                       # ★ 빌드 도구
│   └── apache-maven-3.9.6/                     # 오프라인 Maven
│
├── pom.xml                                      # Maven 빌드 파일
├── build.gradle                                 # Gradle 빌드 파일
├── README.md                                    # 프로젝트 문서
├── TESTING.md                                   # 테스트 가이드
├── DEPENDENCIES.md                              # 의존성 정보
├── COVERAGE.md                                  # ★ 테스트 커버리지 가이드
├── COVERAGE_QUICKSTART.md                       # ★ 커버리지 빠른 시작
└── WORK_HISTORY.md                              # 작업 이력
```

**★ = Phase 1 리팩토링에서 추가/수정된 항목**

## 설정 방법

### agent.properties 파일 생성

에이전트 루트 디렉토리에 `agent.properties` 파일을 생성합니다:

```properties
# 서버 설정
server_url=https://leebalso-server.example.com
get_command_uri=/api/v1/command/getCommands
post_agent_uri=/api/v1/agent

# 인증 토큰 (Refresh Token)
token=YOUR_REFRESH_TOKEN_HERE

# mTLS 설정 (선택 사항 - refresh_token 만료 시 fallback으로 사용)
use_mtls=false
client.keystore.path=/path/to/agent.p12
client.keystore.password=your-keystore-password
truststore.path=/path/to/truststore.jks
truststore.password=your-truststore-password

# 명령 확인 주기 (초 단위)
command_check_cycle=60

# Kafka 브로커 주소 (선택 사항, BOOT 명령으로 동적 설정 가능)
kafka_broker_address=kafka-broker.example.com:9092

# MQTT 설정 (선택 사항, 기본 비활성)
# Kafka 와 달리 BOOT 명령으로는 주입되지 않는다. 이 파일이 유일한 설정 소스다.
mqtt_enabled=false
mqtt_broker_address=tcp://mqtt-broker.example.com:1883
mqtt_credential=YOUR_MQTT_PASSWORD

# 호스트 및 사용자 식별 환경 변수
host_name_var=HOSTNAME
user_name_var=USER

# 로그 설정
log_dir=/var/log/mwagent
log_level=INFO

# 보안 설정 (선택 사항)
security.path_traversal_check=true
security.allowed_read_paths=/sw/webtob,/usr/local/tomcat/logs
security.command_injection_check=false
```

### 설정 항목 설명

- **server_url**: Leebalso 중앙 서버 URL
- **get_command_uri**: 명령 조회 API 엔드포인트
- **post_agent_uri**: 에이전트 등록 API 엔드포인트
- **token**: 인증용 Refresh Token (최초 발급 필요)
- **command_check_cycle**: 명령 폴링 주기 (초)
- **kafka_broker_address**: Kafka 브로커 주소 (옵션)
- **host_name_var**: 호스트명을 가져올 환경 변수명
- **user_name_var**: 사용자명을 가져올 환경 변수명
- **log_dir**: 로그 파일 저장 경로
- **log_level**: 로그 레벨 (SEVERE, WARNING, INFO, FINE, FINEST)

#### MQTT 설정 (선택 사항)

- **mqtt_enabled**: MQTT 구독자 활성화 여부 (`true`/`false`, **기본값: `false`**)
  - `false` 면 구독자 스레드도, Paho 클라이언트도 만들지 않습니다 (Paho 클래스가 로드되지 않음)
  - `true`/`false` 이외의 값은 `false` 로 처리됩니다
- **mqtt_broker_address**: MQTT 브로커 주소. 스킴을 생략하면 `tcp://` 로 간주합니다 (TLS 는 `ssl://host:8883`)
  - Kafka 와 달리 **BOOT 명령으로 주입되지 않습니다.** `agent.properties` 가 유일한 설정 소스입니다
- **mqtt_credential**: MQTT 접속 비밀번호. username 은 `agent_id` 가 그대로 쓰입니다

#### mTLS 설정 (선택 사항)
- **use_mtls**: mTLS 활성화 여부 (`true`/`false`)
- **client.keystore.path**: 클라이언트 인증서 keystore 경로 (PKCS12)
- **client.keystore.password**: keystore 비밀번호
- **truststore.path**: 서버 CA 인증서 truststore 경로 (JKS)
- **truststore.password**: truststore 비밀번호

#### 보안 설정 (선택 사항)
- **security.path_traversal_check**: 경로 탐색 공격 방어 (`true`/`false`, 기본값: `true`)
- **security.allowed_read_paths**: `ReadFullPathFile` 명령이 읽을 수 있는 디렉토리 추가 (콤마 구분, 기본값: 없음)
  - 기본 허용 목록(에이전트 작업 디렉토리, 임시 디렉토리, `/var/log`, `/opt`, `C:\logs`, `C:\Program Files`)에 **추가**됩니다
  - `security.path_traversal_check=true` 인 경우에만 의미가 있습니다
  - 허용되지 않은 경로를 요청하면 로그에 `Security: Path traversal or unauthorized path detected` 가 기록되고 파일을 읽지 않습니다
- **security.command_injection_check**: 명령 주입 공격 방어 (`true`/`false`, 기본값: `false`)
  - 주의: 활성화 시 특수 문자(`;`, `|`, `` ` ``, `$()` 등)를 포함한 파라미터가 차단됩니다

## 실행 방법

### Fat JAR 실행 (권장)

Maven 또는 Gradle로 빌드한 경우:

```bash
# Maven으로 빌드한 경우
java -jar target/mwagent-0000.0009.0001-jar-with-dependencies.jar

# Gradle로 빌드한 경우
java -jar build/libs/mwagent-all-0000.0009.0001.jar
```

### Classpath 직접 지정

```bash
java -cp "build/jar/mwagent-0000.0009.0001.jar:lib/*" mwagent.MwAgent
```

### 백그라운드 실행

```bash
# Fat JAR 실행
nohup java -jar mwagent-all.jar > /dev/null 2>&1 &

# Classpath 지정 실행
nohup java -cp "build/jar/mwagent.jar:lib/*" mwagent.MwAgent > /dev/null 2>&1 &
```

### 서비스 등록 (systemd 예제)

`/etc/systemd/system/mwagent.service`:

```ini
[Unit]
Description=MwManger Agent Service
After=network.target

[Service]
Type=simple
User=mwagent
WorkingDirectory=/opt/mwagent
# Fat JAR 실행 (권장)
ExecStart=/usr/bin/java -jar /opt/mwagent/mwagent-all.jar
# 또는 Classpath 지정
# ExecStart=/usr/bin/java -cp ".:lib/*" mwagent.MwAgent
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable mwagent
sudo systemctl start mwagent
sudo systemctl status mwagent
```

## 실행 흐름

### Lifecycle-Based Execution Flow (Phase 1)

```
┌─────────────────────────────────────────────────┐
│  1. Config Initialization                       │
│     - Load agent.properties                     │
│     - Setup logger                              │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│  2. Bootstrap Phase                             │
│     - Check agent registration status           │
│     - Register if needed                        │
│     - Wait for approval                         │
│     - Receive BOOT commands                     │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│  3. Initialization Phase                        │
│     - Process BOOT commands                     │
│     - Start KafkaService (if configured)        │
│       • Consumer Thread                         │
│       • HealthCheck Thread                      │
│       • Producer Initialization                 │
│     - Start MqttService (if mqtt_enabled=true)  │
│       • Subscribe cmd/{agent_id}/req (QoS1)     │
│       • Watch Thread (60s connection check)     │
│       • Connect failure is not fatal - retries  │
│     - Start CommandExecutorService              │
│     - Register services with ShutdownHandler    │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────┐
│  4. Runtime Phase (Main Loop)                   │
│     - Poll commands from server (HTTP GET)      │
│       (MQTT commands arrive out-of-band,        │
│        handled by Paho callback threads)        │
│     - Handle token expiration (auto-refresh)    │
│     - Execute commands asynchronously           │
│       • Submit to CommandExecutorService        │
│       • OrderCallerThread processes in pool     │
│     - Sleep (command_check_cycle seconds)       │
│     - Repeat until shutdown signal              │
└─────────────────┬───────────────────────────────┘
                  │
                  ↓ (Ctrl+C or SIGTERM)
┌─────────────────────────────────────────────────┐
│  5. Shutdown Phase (Graceful)                   │
│     - Stop runtime loop                         │
│     - GracefulShutdownHandler.shutdown()        │
│       • Stop CommandExecutorService (LIFO)      │
│         - Wait for running tasks (30s timeout)  │
│       • Stop MqttService                        │
│         - Stop Watch Thread                     │
│         - Disconnect & Close Paho client        │
│       • Stop KafkaService                       │
│         - Close Consumer                        │
│         - Close HealthCheck                     │
│         - Flush & Close Producer                │
│     - Flush logs                                │
└─────────────────────────────────────────────────┘
```

### State Transitions

```
Agent Lifecycle States:
CREATED ─(start)→ STARTING ─(initialized)→ RUNNING ─(stop)→ STOPPING ─(cleaned up)→ STOPPED
                      │                        │
                      │                        │
                      └────────(error)─────────┴──────────→ FAILED
```

## 명령 처리 방식

### Order 클래스 구조

모든 명령은 `Order` 추상 클래스를 상속하며, 다음 메서드를 구현합니다:

- **convertCommand(JSONObject)**: JSON 명령을 CommandVO로 변환
- **execute()**: 명령 실행 로직 (추상 메서드)
- **sendResults()**: 결과를 서버 또는 Kafka로 전송

### 명령 타입 (Order 구현체)

| 클래스 | 설명 |
|--------|------|
| **ExeShell** | 쉘 스크립트 실행 (bash, ksh, cmd) |
| **ExeScript** | 스크립트 파일 실행 |
| **ExeText** | 텍스트 형식 명령 실행 |
| **ExeAgentFunc** | AgentFunction 호출 (플러그인 방식) |
| **ReadFile** | 파일 내용 읽기 |
| **ReadPlainFile** | 일반 파일 읽기 |
| **ReadFullPathFile** | 전체 경로로 파일 읽기 |
| **DownloadFile** | 서버에서 파일 다운로드 |
| **GetRefreshToken** | Refresh Token 갱신 |

#### ReadPlainFile / ReadFullPathFile 의 additional_params

| 클래스 | 서버 Command Type 라벨 | additional_params 에 넣는 값 |
|--------|------------------------|------------------------------|
| **ReadPlainFile** | 파일 Read | 확장자만 (점 없이). 예: `conf` → `<target_file_path><target_file_name>.conf` 를 읽음 |
| **ReadFullPathFile** | 파일 Read(파일 이름 후 지정) | 읽을 파일의 **절대경로 전체**. 예: `/sw/webtob/config/rewrite_https.conf` |

`ReadFullPathFile` 은 `target_file_path`/`target_file_name` 을 읽기 경로로 사용하지 않고 결과 식별용으로만 사용합니다.
읽을 경로는 `security.allowed_read_paths` 로 허용된 디렉토리 하위여야 합니다.

### Agent Function (플러그인)

`ExeAgentFunc` Order를 통해 호출되는 확장 기능:

| 함수 | 설명 |
|------|------|
| **HelloFunc** | 테스트용 Hello World |
| **JmxStatFunc** | JMX 통계 정보 수집 |
| **SSLCertiFunc** | SSL 인증서 정보 조회 |
| **SSLCertiFileFunc** | SSL 인증서 파일 읽기 |
| **DownloadNUnzipFunc** | 파일 다운로드 및 압축 해제 |
| **SuckSyperFunc** | Syper 시스템 데이터 수집 |

### 명령 JSON 형식 예제

```json
{
  "command_id": "CMD-12345",
  "command_class": "ExeShell",
  "repetition_seq": 1,
  "target_file_name": "backup.sh",
  "target_file_path": "/scripts/",
  "additional_params": "-v --force",
  "result_receiver": "SERVER",
  "target_object": "t_backup_results",
  "result_hash": ""
}
```

### 파라미터 치환 기능

명령의 `target_file_path`, `target_file_name`, `additional_params`에서 환경 변수를 참조할 수 있습니다:

```
<<JAVA_HOME>>/bin/java
→ /usr/lib/jvm/java-11/bin/java
```

## 통신 방식

### 1. HTTP/HTTPS 통신

**인증 방식**: Bearer Token (JWT)

#### Authentication (인증) - mTLS

mTLS(Mutual TLS)를 통해 에이전트의 신원을 인증서로 증명합니다:

- 클라이언트 인증서(PKCS12)와 개인키를 사용
- 서버 CA 인증서(truststore)로 서버 검증
- RFC 8705 (OAuth 2.0 Mutual-TLS Client Authentication) 표준 준수

#### Authorization (인가) - OAuth2

OAuth2 토큰을 통해 API 접근 권한을 관리합니다:

- **Refresh Token**: 장기 유효, `agent.properties`에 저장
- **Access Token**: 단기 유효 (OAuth2 `access_token`)
- 표준 OAuth2 엔드포인트 사용: `/oauth2/token`

#### 계단식 토큰 갱신 (Cascading Token Renewal)

access_token 만료 시 다단계 갱신 전략을 사용합니다:

```
access_token 만료 (401 응답)
        ↓
1. refresh_token grant 시도 (/oauth2/token)
        ↓
   성공 → 새 access_token 사용
        ↓ (실패 - 401: refresh_token 만료)
2. mTLS client_credentials grant 시도 (mTLS 활성화 시)
        ↓
   성공 → 새 access_token 사용
        ↓ (실패)
   에러 로그 및 재시도
```

이 전략은 `Common.renewAccessTokenWithFallback()` 메서드에서 구현됩니다.

#### API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/oauth2/token` | OAuth2 토큰 발급/갱신 (RFC 6749) |
| POST | `/api/v1/agent` | 에이전트 등록 |
| GET | `/api/v1/command/getCommands/{agent_id}` | 명령 조회 (폴링) |
| GET | `/api/v1/command/getCommands/{agent_id}/{version}/{type}/BOOT` | 시작 알림 및 BOOT 명령 |
| GET | `/api/v1/agent/getRefreshToken/{agent_id}` | Refresh Token 조회 |
| POST | `/api/v1/command/result` | 명령 실행 결과 전송 |

#### OAuth2 Grant Types

| Grant Type | 용도 | 인증 방식 |
|------------|------|----------|
| `refresh_token` | access_token 갱신 | refresh_token 파라미터 |
| `client_credentials` | mTLS 기반 토큰 발급 | 클라이언트 인증서 (mTLS) |

#### TLS 설정

- TLS 1.2 프로토콜 사용
- 모든 인증서 신뢰 (Self-signed 포함)
- 호스트명 검증 비활성화
- AIX에서는 BouncyCastle Security Provider 사용

### 2. Kafka 통신

#### Consumer (명령 수신)

- **Topic**: `t_{agent_id}` (예: `t_server01_user01_J`)
- **Group ID**: `g_{agent_id}`
- 메시지 형식: JSON (command 객체)

#### Producer (결과 전송)

- **Topic**: 명령의 `target_object` 필드 값
- **Key**: `agent_id`
- 메시지 형식: JSON (result 객체)

#### Health Check

- **Topic**: `t_agent_health`
- 에이전트 상태 모니터링용

### 3. MQTT 통신 (선택 사항, 기본 비활성)

MQTT 는 **실시간 명령 수령만** 담당합니다. 폴링 주기(`command_check_cycle`)를 기다리지
않고 명령을 즉시 받기 위한 보조 채널이며, REST API 를 대체하지 않습니다.
결과 전송·토큰 갱신·명령 폴링은 그대로 HTTP/HTTPS 가 담당합니다.

#### Subscriber (명령 수신)

- **Topic**: `cmd/{agent_id}/req`, `cmd/broadcast/req`
- **clientId / username**: `agent_id` (브로커 ACL 의 `%u` 치환이 그대로 성립)
- **QoS**: 1 (at-least-once). 중복 수신은 정상이므로 `cmdId` 기준 LRU 캐시(1000건)로 멱등 처리
- **세션**: `cleanStart=false`, `sessionExpiryInterval=86400` — 에이전트가 꺼져 있던 동안의
  명령도 브로커가 큐잉해 둡니다 (브로커에 세션 persistence 설정이 되어 있어야 유효)
- 메시지 형식: JSON (command 객체). Kafka 경로와 동일한 `command_class` 규약을 따릅니다

#### 발행하지 않습니다

구독 전용입니다. 결과·상태·LWT 를 일절 발행하지 않으므로 브로커 ACL 에는
`cmd/{agent_id}/req` 와 `cmd/broadcast/req` **구독 권한만** 있으면 됩니다.

#### 연결 감시

- 브로커 접속이 끊기면 **최초 1줄**만 로그에 남기고, 이후 **1시간 간격**으로 1줄씩 보고합니다
  (재접속 실패마다 찍으면 로그가 폭증하므로 시간 기준으로 억제)
- 감시 스레드가 **60초 주기**로 연결 상태를 직접 확인합니다
- 브로커가 죽은 상태에서 에이전트가 기동되어도 포기하지 않고 60초마다 재시도하며,
  브로커가 살아나면 **에이전트 재기동 없이** 자동으로 접속·재구독합니다
- 접속이 60초 이상 유지되면 복구로 판정해 `MQTT recovered` 를 남깁니다
- MQTT 기동 실패는 에이전트 기동을 막지 않습니다 (Kafka 와도 서로 독립)

### 결과 전송 방식 선택

명령의 `result_receiver` 필드로 제어합니다. **명령을 어떤 채널로 받았는지와 무관하게**
이 필드가 결과 경로를 결정하며, 값이 없으면 `SERVER` 로 간주합니다.

- **SERVER**: HTTP POST `/api/v1/command/result` 로만 전송 (기본값)
- **KAFKA**: Kafka로만 전송
- **SERVER_N_KAFKA**: 양쪽 모두 전송

MQTT 로 결과를 보내는 경로는 존재하지 않습니다. 따라서 MQTT 로 받은 명령의 결과도
기본적으로 REST 로 나갑니다.

## 지원 운영체제

| OS | 쉘 | 확인됨 |
|----|-----|--------|
| **Windows** | cmd | ✓ |
| **Linux** | bash | ✓ |
| **AIX** | ksh + BouncyCastle | ✓ |
| **HP-UX** | ksh | ✓ |

## Agent ID 생성 규칙

```
{hostname}_{username}_J
```

예: `server01_deploy_J` (J는 JAVAAGENT를 의미)

## 로그

로그 파일은 `log_dir` 설정 경로에 생성됩니다:

```
mwagent.0.0.log
mwagent.0.1.log
...
```

- 로그 파일 크기: 1MB
- 최대 파일 수: 10개 (순환)

## 테스트

### 단위 테스트 실행

프로젝트는 JUnit 5 기반의 단위 테스트를 포함합니다:

```bash
# Maven으로 테스트 실행
mvn test

# Gradle로 테스트 실행
gradle test

# 오프라인 환경에서 Maven 테스트
./tools/apache-maven-3.9.6/bin/mvn test
```

### 테스트 결과 (Phase 1)

```
Total Tests: 106
✓ Success: 106 (100%)
✗ Failures: 0
✗ Errors: 0

Test Breakdown:
- Lifecycle Tests: 14 (LifecycleState, GracefulShutdownHandler)
- Service Tests: 13 (CommandExecutor, Bootstrap, Registration)
- VO Tests: 42 (AgentStatus, Commands, Results, etc.)
- Order Tests: 15 (ExeShell, Order base class)
- Utility Tests: 17 (Common, AgentFuncFactory)
- Integration Tests: 5 (PreWork)
```

### 테스트 커버리지

자세한 정보는 다음 문서 참조:
- [COVERAGE.md](COVERAGE.md) - 포괄적인 커버리지 가이드
- [COVERAGE_QUICKSTART.md](COVERAGE_QUICKSTART.md) - 빠른 시작 가이드
- [src/test/java/mwagent/README_TESTS.md](src/test/java/mwagent/README_TESTS.md) - 테스트 상세

### 테스트 프레임워크

- JUnit 5 (5.8.2)
- Mockito (3.12.4)
- AssertJ (3.21.0)

## 보안 고려사항

### 인증서 및 토큰 보호

1. **Refresh Token 보호**: `agent.properties` 파일 권한을 600으로 설정
2. **mTLS 인증서 보호**: keystore 파일 권한을 600으로 설정
3. **비밀번호 관리**: keystore/truststore 비밀번호를 환경변수로 관리 권장

### 통신 보안

4. **HTTPS 사용**: 중앙 서버와의 통신 시 HTTPS 필수 (mTLS 사용 시)
5. **Kafka 보안**: 필요 시 SASL/SSL 설정 추가
6. **TLS 버전**: TLS 1.2 이상 사용

### 입력 검증 (Phase 2 구현)

7. **Command Injection 방어**: SecurityValidator로 위험 문자 차단 (`;`, `|`, `` ` ``, `$()`, `&`, `<`, `>`, `\n`)
   - 설정: `security.command_injection_check=true` (기본값: false)
8. **Path Traversal 방어**: `../` 패턴 및 허용되지 않은 경로 차단
   - 설정: `security.path_traversal_check=true` (기본값: true)
9. **Filename 검증**: 경로 구분자 포함 파일명 거부
10. **토큰 마스킹**: 로그에 토큰 출력 시 끝 10자리만 표시

### 운영 보안

11. **명령 검증**: 악의적인 명령 실행 방지를 위한 화이트리스트 관리 권장
12. **인증서 갱신**: mTLS 인증서 만료 전 갱신 계획 수립

## 확장 방법

### 새로운 Order 추가

1. `order/` 디렉토리에 `Order` 클래스 상속
2. `execute()` 메서드 구현
3. 서버에서 해당 클래스명으로 명령 전송

예:
```java
package mwagent.order;

public class CustomOrder extends Order {
    public CustomOrder(JSONObject command) {
        super(command);
    }

    public int execute() {
        // 구현
        return 1;
    }
}
```

### 새로운 AgentFunc 추가

1. `agentfunction/` 디렉토리에 `AgentFunc` 인터페이스 구현
2. `AgentFuncFactory`에 case 추가
3. `ExeAgentFunc` Order로 호출

### 새로운 Service 추가

1. `service/` 디렉토리에 `AgentLifecycle` 인터페이스 구현
2. `start()`, `stop()`, `getState()` 메서드 구현
3. `AgentLifecycleManager`에 서비스 등록
4. 자동으로 graceful shutdown 지원

## Refactoring History

### Phase 1: Lifecycle Management (2025-11-21)

**완료 항목:**
- ✅ Lifecycle framework (AgentLifecycle, LifecycleState)
- ✅ KafkaService (Consumer/Producer/HealthCheck)
- ✅ CommandExecutorService (ThreadPool management)
- ✅ GracefulShutdownHandler (LIFO shutdown, 30s timeout)
- ✅ AgentLifecycleManager (Bootstrap → Init → Runtime → Shutdown)
- ✅ 22개 신규 테스트 추가 (106개 전부 통과)

**개선 사항:**
- 생명주기 기반 아키텍처로 전환
- Graceful shutdown 구현 (자원 정리, 로그 flush)
- 모든 서비스에 DI 지원으로 테스트 가능
- 에러 처리 및 상태 관리 개선

### Phase 1.5: mTLS & Cascading Token Renewal (2025-11-28)

**완료 항목:**
- ✅ mTLS 클라이언트 인증 지원 (`Common.createMtlsClient()`)
- ✅ OAuth2 표준 토큰 엔드포인트 마이그레이션 (RFC 6749, RFC 8705)
- ✅ 계단식 토큰 갱신 전략 (`Common.renewAccessTokenWithFallback()`)
- ✅ Mock 서버 refresh_token 만료 시나리오 지원
- ✅ 127개 테스트 통과 (21개 신규 추가)

**개선 사항:**
- Authentication(인증): mTLS 클라이언트 인증서 기반
- Authorization(인가): OAuth2 access_token 기반
- refresh_token 만료 시 mTLS로 자동 fallback
- 테스트 서버에 토큰 만료 시뮬레이션 API 추가

### Phase 2: Security Hardening (2025-12-02)

**완료 항목:**
- ✅ SecurityValidator 클래스 추가 (보안 검증 유틸리티)
- ✅ Command Injection 방어 (ExeShell, ExeScript)
- ✅ Path Traversal 방어 (ReadFullPathFile, DownloadFile)
- ✅ 토큰 로깅 마스킹 (끝 10자리만 표시)
- ✅ 155개 테스트 통과 (33개 보안 테스트 추가)
- ✅ 보안 검증 설정 옵션화 (agent.properties에서 on/off 가능)

**보안 검증 항목:**
- 위험 문자 차단: `;`, `|`, `` ` ``, `$()`, `&`, `<`, `>`, `\n`, `\r`
- 경로 탐색 패턴 차단: `../`, `..\\`
- 파일명 검증: 경로 구분자 포함 거부
- 허용된 디렉토리만 접근 가능 (ReadFullPathFile)

**보안 설정 옵션:**
| 설정 | 기본값 | 설명 |
|------|--------|------|
| `security.path_traversal_check` | `true` | 경로 탐색 공격 방어 |
| `security.allowed_read_paths` | (없음) | ReadFullPathFile이 읽을 수 있는 디렉토리 추가 (콤마 구분, 기본 허용 목록에 추가됨) |
| `security.command_injection_check` | `false` | 명령 주입 공격 방어 (특수 문자 차단) |

### Phase 3: Dependency Injection Architecture (2025-12-03)

**완료 항목:**
- ✅ ConfigurationProvider 인터페이스 추가 (설정 추상화)
- ✅ HttpClient 인터페이스 추가 (HTTP 통신 추상화)
- ✅ ApacheHttpClientAdapter 구현 (HTTP/HTTPS/mTLS 지원)
- ✅ ApplicationContext DI 컨테이너 구현
- ✅ Config.java가 ConfigurationProvider 인터페이스 구현
- ✅ MockConfigurationProvider 테스트용 구현
- ✅ 187개 테스트 통과

**개선 사항:**
- 의존성 주입을 통한 모듈 분리
- 인터페이스 추상화로 테스트 용이성 향상
- ApplicationContext 싱글톤 패턴으로 서비스 관리

### Phase 4: mTLS Test Environment (2025-12-03)

**완료 항목:**
- ✅ 테스트 인증서 생성 스크립트 (CA, Server, Agent)
- ✅ Python OAuth2 인증 서버 (mock_server.py)
- ✅ mTLS + IP + Username 검증 로직 구현
- ✅ Certificate Subject에 usertype 추가 (OU=agent)
- ✅ Java/Python mTLS 테스트 클라이언트

**인증서 Subject 형식:**
```
CN={hostname}_{username}_J, OU=agent, O=Leebalso, C=KR
```

**JWT 토큰 클레임:**
```json
{
  "sub": "testserver01_appuser_J",
  "iss": "leebalso-auth-server",
  "usertype": "agent",
  "hostname": "testserver01",
  "username": "appuser",
  "client_ip": "127.0.0.1"
}
```

**검증 흐름:**
1. Certificate OU = agent (usertype 확인)
2. Agent 등록 및 활성 상태 확인
3. Client IP in allowed list (인증서 복사 공격 방어)

**테스트 서버 실행:**
```bash
# 인증서 생성
cd test-server && generate-certs.bat

# mTLS 서버 실행
python mock_server.py --ssl
```

### Phase 5: Biz Service & Integration Testing (2025-12-04)

**완료 항목:**
- ✅ Sample Biz Service 구현 (Flask + PyJWT)
  - JWT 토큰 검증 데코레이터 (`@require_token`, `@require_scope`)
  - OAuth2 Token Introspection 지원
  - API 엔드포인트: `/api/whoami`, `/api/commands`, `/api/results`, `/api/config`
- ✅ BizServiceIntegrationTest 구현 (E2E 흐름 테스트)
- ✅ SSLCertiFunc/SSLCertiFileFunc 테스트 구현
  - 로컬 SSL 서버를 통한 인증서 검사 테스트
  - SNI 기반 인증서 필터링 검증
- ✅ mTLS-JWT 인증 흐름 문서화
- ✅ 토큰 검증 아키텍처 문서화

**새로운 테스트 서버:**
```bash
# Biz Service 실행
cd biz-service && python app.py

# SSL 모드 Mock Server
cd test-server && python mock_server.py --ssl
```

**문서:**
- [docs/mTLS-JWT-Authentication-Flow.md](docs/mTLS-JWT-Authentication-Flow.md)
- [docs/Token-Validation-Architecture.md](docs/Token-Validation-Architecture.md)
- [biz-service/README.md](biz-service/README.md)

### Phase 7: MQTT Command Subscription (2026-09-15)

**완료 항목:**
- ✅ MQTT v5 명령 구독 구현 (Eclipse Paho 1.2.5, JDK 1.8 호환)
  - `MwMqttSubscriber`: `cmd/{agent_id}/req` 구독, QoS1, `cmdId` 기준 멱등 처리
  - `MqttService`: 수명주기 관리 및 연결 감시
  - Kafka 와 병행 동작하며 한쪽 실패가 다른 쪽을 막지 않음
- ✅ 기본 비활성 (`mqtt_enabled=false`). 켜지 않으면 Paho 클래스조차 로드되지 않음
- ✅ 구독 전용 — 결과·상태·LWT 를 발행하지 않으므로 브로커 publish 권한 불필요
- ✅ 연결 감시 개선
  - 60초 주기로 `isConnected()` 직접 확인 (Paho 는 재접속 실패 시 콜백을 호출하지 않음)
  - 끊김 지속 시 1시간 간격 보고 보장, 끊김 사유를 `reasonString`/`cause`/`rc` 순으로 기록
  - 브로커가 죽은 상태에서 기동해도 재시도하며, 복구 시 에이전트 재기동 없이 자동 재구독
- ✅ MQTT 단위 테스트 32개 추가 (브로커 없이 실행 가능)

**설정:**
```properties
mqtt_enabled=true
mqtt_broker_address=tcp://mqtt-broker.example.com:1883
mqtt_credential=YOUR_MQTT_PASSWORD
```

**설계 원칙:** MQTT 는 실시간 명령 수령만 담당합니다. 결과 전송·토큰 갱신·명령 폴링은
계속 REST API 가 담당하므로 API 서버는 항상 필요합니다.

자세한 내용은 [WORK_HISTORY.md](WORK_HISTORY.md) 참조

## 문의 및 지원

프로젝트 관련 문의사항이나 이슈는 프로젝트 관리자에게 연락하시기 바랍니다.

---

**Last Updated**: 2026-09-15
**Version**: 0000.0010.0000
**Architecture**: Phase 7 - MQTT Command Subscription
**Test Coverage**: 284 tests (255 passing, 23 skipped, 3 aborted, 3 known failures in SecurityValidatorTest/ExtractLogTest)
