# MwManager 리팩토링 마스터 플랜

**작성일**: 2025-11-18
**버전**: 0000.0009.0001
**브랜치**: refactoring_major_202511

## 📋 전체 개요

**목표**: MwAgent를 mTLS 기반의 안전하고 확장 가능한 아키텍처로 전환

**기간**: 8-10주
**우선순위**: 보안 > 아키텍처 > 코드 품질

---

## Phase 1: 인증 서버 API 스펙 정의 (Python)
**기간**: 1주
**담당**: Python 서버 팀과 협업
**상태**: Pending

### 1.1 인증 서버 기능 정의

#### 필수 API 엔드포인트

```yaml
# 1. Certificate 기반 인증
POST /api/v1/auth/certificate
Request:
  - mTLS certificate (자동 검증)
  - Body:
      agent_id: string
      agent_version: string
      hostname: string
      os: string
Response:
  - access_token: string (JWT)
  - expires_in: int (seconds, 예: 3600)
  - token_type: "Bearer"

# 2. Agent 등록
POST /api/v1/agents/register
Request:
  - mTLS certificate
  - Body:
      agent_id: string
      csr: string (Certificate Signing Request)
      hostname: string
      user: string
      os: string
Response:
  - status: "pending" | "approved" | "rejected"
  - certificate: string (if approved)
  - message: string

# 3. Certificate 갱신
POST /api/v1/agents/renew-certificate
Request:
  - mTLS certificate (현재 인증서)
  - Body:
      agent_id: string
      csr: string (새 CSR)
Response:
  - certificate: string
  - expires_at: datetime

# 4. 명령 조회 (기존 유지)
GET /api/v1/commands/{agent_id}
Headers:
  - Authorization: Bearer {access_token}
Response:
  - commands: array

# 5. 결과 전송 (기존 유지)
POST /api/v1/commands/results
Headers:
  - Authorization: Bearer {access_token}
Body:
  - command_id: string
  - result: object

# 6. Health Check
GET /api/v1/health
Response:
  - status: "ok"
```

### 1.2 Python 인증 서버 요구사항

```python
# 필요한 기능
1. CA (Certificate Authority) 구현
   - Certificate 발급
   - Certificate 서명
   - Certificate 폐기 (CRL)
   - 만료 관리

2. mTLS 검증
   - Client certificate 검증
   - Certificate chain 검증
   - Revocation check

3. JWT Token 관리
   - Access token 발급
   - Token 검증
   - Token 만료 관리

4. Agent 관리
   - Agent 등록/승인
   - Agent 상태 관리
   - Agent 통계

5. 보안
   - Rate limiting
   - Audit logging
   - Intrusion detection
```

### 1.3 Certificate 구조

```
Root CA (Self-signed)
  └── Intermediate CA
       ├── Server Certificate
       └── Agent Certificates
           ├── agent-server01-user01-J
           ├── agent-server02-user01-J
           └── ...
```

---

## Phase 2: Critical 보안 취약점 수정
**기간**: 1-2주
**우선순위**: CRITICAL
**상태**: Pending

### 2.1 Command Injection 수정

**파일**: `ExeShell.java`, `ExeScript.java`, `ExeText.java`

#### 현재 문제
```java
// ExeShell.java:50 - 위험!
String command_t = currentPath + commandVo.getTargetFileName();
if(commandVo.getAdditionalParams().length() > 0){
    command_t += " " + commandVo.getAdditionalParams();
}
rt.exec(command);  // 검증 없이 실행
```

#### 수정 방안
```java
// 1. Command Whitelist
public class CommandValidator {
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
        "/bin/bash",
        "/bin/sh",
        "/usr/bin/python",
        "cmd.exe"
        // ... whitelist
    );

    public static boolean isAllowed(String command) {
        return ALLOWED_COMMANDS.stream()
            .anyMatch(cmd -> command.startsWith(cmd));
    }
}

// 2. ProcessBuilder 사용 (Arguments 분리)
public class ExeShell extends Order {
    public int execute() {
        // Whitelist 검증
        if (!CommandValidator.isAllowed(commandVo.getTargetFileName())) {
            throw new SecurityException("Command not in whitelist");
        }

        // ProcessBuilder로 안전하게 실행
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(
            commandVo.getTargetFileName(),
            parseArguments(commandVo.getAdditionalParams())
        );

        Process process = pb.start();
        int exitCode = process.waitFor();

        return exitCode;
    }
}
```

### 2.2 Path Traversal 수정

**파일**: `DownloadFile.java`, `ReadFile.java`

```java
public class PathValidator {
    private static final String BASE_DIR = System.getProperty("user.dir");

    public static String validatePath(String path) throws SecurityException {
        try {
            File file = new File(BASE_DIR, path);
            String canonicalPath = file.getCanonicalPath();

            if (!canonicalPath.startsWith(new File(BASE_DIR).getCanonicalPath())) {
                throw new SecurityException("Path traversal detected: " + path);
            }

            return canonicalPath;
        } catch (IOException e) {
            throw new SecurityException("Invalid path: " + path, e);
        }
    }
}
```

### 2.3 로깅에서 토큰 제거

**파일**: `Common.java`, `Config.java`

```java
// 삭제
config.getLogger().fine("refresh_token :"+refresh_token);  // 삭제!
config.getLogger().fine("access_token :"+access_token);    // 삭제!

// 대체
config.getLogger().fine("Token updated successfully");
```

### 2.4 동시성 버그 수정

**파일**: `MwConsumerThread.java:83`

```java
// 현재 (버그!)
}while (!StringUtils.equals(message, FIN_MESSAGE) || stopRequested==true );

// 수정
}while (!StringUtils.equals(message, FIN_MESSAGE) && !stopRequested);
```

**파일**: `SuckSyperFunc.java:63`

```java
// 현재 (버그!)
if(!conn.equals(null)){  // NPE 발생!

// 수정
if(conn != null){
    try {
        conn.close();
    } catch (SQLException e) {
        logger.log(Level.WARNING, "Failed to close connection", e);
    }
}
```

---

## Phase 3: 아키텍처 리팩토링
**기간**: 3-4주
**우선순위**: HIGH
**상태**: Pending

### 3.1 의존성 주입 (DI) 도입

#### 새로운 구조

```
src/main/java/mwagent/
├── application/
│   ├── AgentApplication.java      # Main entry point
│   └── ApplicationContext.java    # DI Container (수동)
├── core/
│   ├── domain/
│   │   ├── Command.java
│   │   ├── Agent.java
│   │   └── CommandResult.java
│   └── service/
│       ├── CommandExecutionService.java
│       ├── AuthenticationService.java
│       └── CertificateService.java
├── infrastructure/
│   ├── config/
│   │   ├── ConfigurationProvider.java (interface)
│   │   └── PropertiesConfiguration.java
│   ├── http/
│   │   ├── HttpClient.java (interface)
│   │   └── ApacheHttpClientAdapter.java
│   ├── messaging/
│   │   ├── MessageConsumer.java (interface)
│   │   ├── MessageProducer.java (interface)
│   │   └── KafkaMessageAdapter.java
│   └── security/
│       ├── CertificateManager.java
│       └── MtlsConfiguration.java
└── command/
    ├── CommandHandler.java (interface)
    ├── ShellCommandHandler.java
    ├── ScriptCommandHandler.java
    └── FileCommandHandler.java
```

#### ApplicationContext.java (간단한 DI Container)

```java
public class ApplicationContext {
    private static ApplicationContext instance;
    private final Map<Class<?>, Object> beans = new ConcurrentHashMap<>();

    private ApplicationContext() {
        initialize();
    }

    public static ApplicationContext getInstance() {
        if (instance == null) {
            synchronized (ApplicationContext.class) {
                if (instance == null) {
                    instance = new ApplicationContext();
                }
            }
        }
        return instance;
    }

    private void initialize() {
        // 1. Configuration
        ConfigurationProvider config = new PropertiesConfiguration("agent.properties");
        register(ConfigurationProvider.class, config);

        // 2. Certificate Manager
        CertificateManager certManager = new CertificateManager(config);
        register(CertificateManager.class, certManager);

        // 3. HTTP Client
        HttpClient httpClient = new ApacheHttpClientAdapter(config, certManager);
        register(HttpClient.class, httpClient);

        // 4. Authentication Service
        AuthenticationService authService = new AuthenticationService(httpClient, config);
        register(AuthenticationService.class, authService);

        // 5. Command Execution Service
        CommandExecutionService cmdService = new CommandExecutionService(config);
        register(CommandExecutionService.class, cmdService);

        // 6. Messaging
        if (config.getBoolean("kafka.enabled")) {
            MessageProducer producer = new KafkaMessageAdapter(config);
            MessageConsumer consumer = new KafkaMessageAdapter(config);
            register(MessageProducer.class, producer);
            register(MessageConsumer.class, consumer);
        }
    }

    public <T> void register(Class<T> type, T instance) {
        beans.put(type, instance);
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        Object bean = beans.get(type);
        if (bean == null) {
            throw new IllegalStateException("No bean registered for type: " + type);
        }
        return (T) bean;
    }
}
```

### 3.2 Config 분리

#### ConfigurationProvider 인터페이스

```java
public interface ConfigurationProvider {
    String getString(String key);
    String getString(String key, String defaultValue);
    int getInt(String key);
    int getInt(String key, int defaultValue);
    boolean getBoolean(String key);
    boolean getBoolean(String key, boolean defaultValue);

    // System info
    String getAgentId();
    String getHostname();
    String getUsername();
    String getOs();
    String getAgentVersion();

    // Server info
    String getServerUrl();
    String getCommandUri();
    String getResultUri();

    // Kafka info
    boolean isKafkaEnabled();
    String getKafkaBroker();

    // mTLS info
    boolean isMtlsEnabled();
    String getKeystorePath();
    String getKeystorePassword();
    String getTruststorePath();
    String getTruststorePassword();
}
```

### 3.3 HTTP Client 추상화

```java
public interface HttpClient {
    HttpResponse get(String path, Map<String, String> headers);
    HttpResponse post(String path, Map<String, String> headers, String body);
    void close();
}
```

---

## Phase 4: mTLS 구현 및 통합
**기간**: 2주
**우선순위**: HIGH
**상태**: Pending

### 4.1 CertificateManager

```java
public class CertificateManager {
    private final ConfigurationProvider config;
    private final Logger logger;
    private KeyStore keyStore;
    private KeyStore trustStore;

    public CertificateManager(ConfigurationProvider config) {
        this.config = config;
        this.logger = LoggerFactory.getLogger(CertificateManager.class);

        if (config.isMtlsEnabled()) {
            loadCertificates();
        }
    }

    public SSLContext createSslContext() {
        try {
            return SSLContexts.custom()
                .setProtocol("TLSv1.2")
                .loadKeyMaterial(keyStore, config.getKeystorePassword().toCharArray())
                .loadTrustMaterial(trustStore, null)
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL context", e);
        }
    }

    public boolean isCertificateExpiringSoon(int daysThreshold) {
        // Certificate 만료 체크 로직
    }

    public void renewCertificate(String newCertificatePem) throws Exception {
        // Certificate 갱신 로직
    }
}
```

### 4.2 AuthenticationService

```java
public class AuthenticationService {
    private final HttpClient httpClient;
    private final ConfigurationProvider config;
    private String accessToken;
    private long tokenExpiryTime;

    public synchronized boolean authenticate() {
        // mTLS로 /api/v1/auth/certificate 호출
        // access_token 획득
    }

    public synchronized void ensureValidToken() {
        if (!isTokenValid()) {
            authenticate();
        }
    }

    public boolean isTokenValid() {
        // 토큰 만료 5분 전에 갱신
    }

    public String getAccessToken() {
        ensureValidToken();
        return accessToken;
    }
}
```

### 4.3 배포 구조

```
/opt/mwagent/
├── mwagent-0000.0009.0001.jar
├── agent.properties              # mTLS 설정 포함
├── lib/
│   └── *.jar
└── certs/                         # NEW!
    ├── agent.jks                  # Keystore (private key + cert)
    └── truststore.jks             # Truststore (server CA cert)
```

### 4.4 agent.properties 설정

```properties
# Server Configuration
server_url=https://server.example.com:8443

# mTLS Configuration
mtls.enabled=true
mtls.keystore.path=/opt/mwagent/certs/agent.jks
mtls.keystore.password=${KEYSTORE_PASSWORD}
mtls.key.password=${KEY_PASSWORD}
mtls.truststore.path=/opt/mwagent/certs/truststore.jks
mtls.truststore.password=${TRUSTSTORE_PASSWORD}

# Token Configuration
token.refresh.before.expiry.seconds=300

# Kafka Configuration (optional)
kafka.enabled=true
kafka_broker_address=kafka.example.com:9092

# Logging
log_dir=./logs
log_level=INFO
```

---

## Phase 5: 테스트 커버리지 확대
**기간**: 2주
**우선순위**: MEDIUM
**상태**: Pending

### 5.1 Unit Tests

```java
// AuthenticationServiceTest.java
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {
    @Mock private HttpClient httpClient;
    @Mock private ConfigurationProvider config;
    private AuthenticationService authService;

    @Test
    void authenticate_Success() {
        // Test implementation
    }
}

// CertificateManagerTest.java
// CommandExecutionServiceTest.java
// PathValidatorTest.java
// CommandValidatorTest.java
```

### 5.2 Integration Tests

```java
@SpringBootTest
class MtlsIntegrationTest {
    @Test
    void authenticate_WithValidCertificate_Success() {
        // Test with actual certificates
    }
}
```

### 5.3 목표 커버리지

- Unit Test Coverage: > 70%
- Integration Test: 주요 시나리오 커버
- Security Test: 모든 보안 취약점 검증

---

## Phase 6: 코드 품질 개선
**기간**: 1-2주
**우선순위**: LOW
**상태**: Pending

### 6.1 Dead Code 제거
- [ ] SSLCertiFunc.java의 주석 처리된 코드 (113줄) 제거
- [ ] 모든 파일의 주석 처리된 코드 제거
- [ ] 사용하지 않는 import 정리

### 6.2 Naming Convention 개선
- [ ] `suckCommands()` → `fetchPendingCommands()`
- [ ] `PreWork` → `AgentRegistrationPhase`
- [ ] `FirstWork` → `InitializationPhase`
- [ ] `MainWork` → `CommandProcessingLoop`
- [ ] `SuckSyperFunc` → `DatabaseCollectorFunction`

### 6.3 JavaDoc 추가
- [ ] 모든 public API에 JavaDoc
- [ ] 보안 관련 코드에 상세 설명
- [ ] 복잡한 알고리즘 설명

### 6.4 Error Code 표준화

```java
public enum AgentErrorCode {
    // Authentication errors (1000-1999)
    AUTH_FAILED(1000, "Authentication failed"),
    AUTH_CERTIFICATE_INVALID(1001, "Certificate is invalid"),
    AUTH_CERTIFICATE_EXPIRED(1002, "Certificate has expired"),
    AUTH_TOKEN_EXPIRED(1003, "Access token has expired"),

    // Command execution errors (2000-2999)
    CMD_EXECUTION_FAILED(2000, "Command execution failed"),
    CMD_NOT_WHITELISTED(2001, "Command not in whitelist"),
    CMD_INVALID_PARAMS(2002, "Invalid command parameters"),

    // Network errors (3000-3999)
    NET_CONNECTION_FAILED(3000, "Network connection failed"),
    NET_TIMEOUT(3001, "Network timeout"),

    // File operation errors (4000-4999)
    FILE_NOT_FOUND(4000, "File not found"),
    FILE_PATH_TRAVERSAL(4001, "Path traversal detected"),
    FILE_PERMISSION_DENIED(4002, "Permission denied"),

    // Configuration errors (5000-5999)
    CONFIG_INVALID(5000, "Invalid configuration"),
    CONFIG_MISSING_REQUIRED(5001, "Missing required configuration");

    private final int code;
    private final String message;
}
```

### 6.5 Magic Numbers 제거

```java
// Before
Thread.sleep(500);
Thread.sleep(15000);

// After
private static final long THREAD_CLEANUP_DELAY_MS = 500;
private static final long SHUTDOWN_GRACE_PERIOD_MS = 15000;
Thread.sleep(THREAD_CLEANUP_DELAY_MS);
Thread.sleep(SHUTDOWN_GRACE_PERIOD_MS);
```

---

## 📊 타임라인

```
Week 1:    Phase 1 - 인증 서버 API 스펙 정의
Week 2-3:  Phase 2 - Critical 보안 수정
Week 4-7:  Phase 3 - 아키텍처 리팩토링
Week 8-9:  Phase 4 - mTLS 구현
Week 10:   Phase 5 - 테스트
Week 11:   Phase 6 - 코드 품질
Week 12:   통합 테스트 및 문서화
```

---

## 🎯 성공 기준

### Phase 2 완료 시
- [ ] Command injection 방어 구현
- [ ] Path traversal 방어 구현
- [ ] 동시성 버그 수정 완료
- [ ] 토큰 로깅 제거 완료
- [ ] 보안 테스트 통과

### Phase 3 완료 시
- [ ] Config 싱글톤 제거 완료
- [ ] DI 컨테이너 작동
- [ ] 모든 클래스 테스트 가능
- [ ] 순환 의존성 없음

### Phase 4 완료 시
- [ ] mTLS handshake 성공
- [ ] Access token 발급/갱신 작동
- [ ] Certificate 만료 체크 작동
- [ ] Python 서버와 통합 테스트 통과

### Phase 5 완료 시
- [ ] Unit test coverage > 70%
- [ ] Integration test 작성 완료
- [ ] 모든 테스트 통과

### Phase 6 완료 시
- [ ] Dead code 제거 완료
- [ ] Naming convention 개선 완료
- [ ] JavaDoc 추가 완료
- [ ] Error code 표준화 완료

---

## 🔄 마이그레이션 전략

### Backward Compatibility

```properties
# agent.properties
# Migration mode (both supported during transition)
auth.mode=mtls  # or "legacy" for backward compatibility
```

### Gradual Rollout
1. **Week 1-4**: 개발 환경에서 mTLS 테스트
2. **Week 5-6**: Staging 환경에 일부 Agent 전환
3. **Week 7-8**: Production 환경에 10% Agent 전환
4. **Week 9-10**: 모든 Agent 전환
5. **Week 11-12**: Legacy 인증 방식 제거

---

## 📝 주요 이슈 및 리스크

### 현재 확인된 Critical 이슈
1. **Command Injection** (ExeShell.java:50) - CRITICAL
2. **SSL Certificate Bypass** (Common.java:79) - CRITICAL
3. **Token Logging** (Common.java:268, 317) - HIGH
4. **Kafka Consumer Loop Bug** (MwConsumerThread.java:83) - HIGH
5. **Connection Leak** (SuckSyperFunc.java:63) - HIGH

### 리스크 및 완화 방안
1. **리스크**: Python 서버 개발 지연
   - **완화**: API 스펙 먼저 확정, Mock 서버로 개발 진행

2. **리스크**: mTLS 구현 복잡도
   - **완화**: Phase별 점진적 도입, 충분한 테스트 기간

3. **리스크**: 기존 Agent와의 호환성
   - **완화**: Migration mode 지원, Gradual rollout

4. **리스크**: Certificate 관리 운영 부담
   - **완화**: 자동 갱신 구현, 만료 모니터링

---

## 📚 참고 문서

- [CODE_ANALYSIS.md](./docs/CODE_ANALYSIS.md) - 상세 코드 분석 결과
- [SECURITY_AUDIT.md](./docs/SECURITY_AUDIT.md) - 보안 감사 결과
- [API_SPEC.md](./docs/API_SPEC.md) - Python 서버 API 스펙
- [MTLS_GUIDE.md](./docs/MTLS_GUIDE.md) - mTLS 구현 가이드
- [TESTING_STRATEGY.md](./docs/TESTING_STRATEGY.md) - 테스트 전략

---

**Last Updated**: 2025-11-18
**Status**: Planning Phase
**Next Review**: 2025-11-25
