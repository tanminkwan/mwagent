# Unit Tests for MwManger Agent

이 디렉토리는 MwManger Agent의 단위 테스트를 포함합니다.

## 테스트 구조

```
src/test/java/
├── mwagent/
│   ├── vo/                    # Value Object 테스트
│   │   ├── CommandVOTest.java
│   │   ├── ResultVOTest.java
│   │   ├── AgentStatusTest.java
│   │   ├── RegistrationRequestTest.java
│   │   └── RegistrationResponseTest.java
│   ├── common/                # 공통 유틸리티 테스트
│   │   ├── CommonTest.java
│   │   ├── CommonMtlsTest.java         # mTLS 클라이언트 테스트
│   │   ├── ConfigMtlsTest.java         # mTLS 설정 테스트
│   │   └── CascadingTokenRenewalTest.java # ★ 계단식 토큰 갱신 테스트
│   ├── lifecycle/             # Lifecycle 테스트
│   │   ├── LifecycleStateTest.java
│   │   ├── GracefulShutdownHandlerTest.java
│   │   └── AgentLifecycleManagerMtlsTest.java
│   ├── service/               # Service 테스트
│   │   ├── CommandExecutorServiceTest.java
│   │   └── registration/
│   │       ├── BootstrapServiceTest.java
│   │       └── RegistrationServiceTest.java
│   ├── integration/           # 통합 테스트
│   │   └── MtlsTokenRenewalIntegrationTest.java # ★ mTLS + 계단식 갱신 통합 테스트
│   ├── order/                 # Order 클래스 테스트
│   │   ├── OrderTest.java
│   │   └── ExeShellTest.java
│   └── agentfunction/         # AgentFunc 테스트
│       └── AgentFuncFactoryTest.java
└── README_TESTS.md
```

**★ = Phase 1.5에서 추가된 테스트**

## 테스트 실행 방법

### Maven 사용

```bash
# 모든 테스트 실행
mvn test

# 특정 테스트 클래스만 실행
mvn test -Dtest=CommandVOTest

# 특정 패키지의 테스트만 실행
mvn test -Dtest=mwagent.vo.*
```

### Gradle 사용

```bash
# 모든 테스트 실행
gradle test

# 특정 테스트 클래스만 실행
gradle test --tests CommandVOTest

# 특정 패키지의 테스트만 실행
gradle test --tests "mwagent.vo.*"
```

## 테스트 커버리지

### 현재 테스트된 컴포넌트

| 컴포넌트 | 테스트 클래스 | 설명 |
|---------|-------------|------|
| CommandVO | CommandVOTest | 명령 VO의 getter/setter 및 toString 테스트 |
| ResultVO | ResultVOTest | 결과 VO의 getter/setter 및 toString 테스트 |
| AgentStatus | AgentStatusTest | Agent 상태 enum 테스트 |
| Common | CommonTest | escape, fillResult, makeOneResultArray 메서드 테스트 |
| Common (mTLS) | CommonMtlsTest | mTLS 클라이언트 생성 테스트 |
| Config (mTLS) | ConfigMtlsTest | mTLS 설정 저장 테스트 |
| **Cascading Token** | **CascadingTokenRenewalTest** | **계단식 토큰 갱신 전략 테스트** |
| Order | OrderTest | 추상 Order 클래스의 공통 기능 테스트 (replaceParam, getHash) |
| AgentFuncFactory | AgentFuncFactoryTest | Factory 패턴 및 각 AgentFunc 생성 테스트 |
| LifecycleState | LifecycleStateTest | 상태 전이 테스트 |
| GracefulShutdown | GracefulShutdownHandlerTest | Graceful shutdown 테스트 |
| **mTLS Integration** | **MtlsTokenRenewalIntegrationTest** | **mTLS + 계단식 갱신 통합 테스트** |

### Phase 1.5 신규 테스트

| 테스트 클래스 | 테스트 케이스 | 설명 |
|-------------|-------------|------|
| CascadingTokenRenewalTest | 5개 | 계단식 토큰 갱신 단위 테스트 |
| MtlsTokenRenewalIntegrationTest | 8개 | mTLS + OAuth2 통합 테스트 |

**계단식 토큰 갱신 테스트 시나리오:**
1. `testUpdateTokenReturnsNegative401OnExpiredRefreshToken` - refresh_token 만료 시 -401 반환
2. `testFallbackReturnsErrorWhenMtlsDisabledAndRefreshTokenExpired` - mTLS 비활성화 시 fallback 불가
3. `testRenewAccessTokenWithMtlsReturnsNegativeWhenDisabled` - mTLS 비활성화 시 -1 반환
4. `testMtlsConfigurationStorage` - mTLS 설정 저장 검증
5. `testCascadingStrategyConceptualFlow` - 계단식 전략 흐름 검증

### 테스트 프레임워크

- **JUnit 5** (5.8.2): 테스트 프레임워크
- **Mockito** (3.12.4): Mocking 프레임워크
- **AssertJ** (3.21.0): Fluent assertion 라이브러리

## 테스트 작성 가이드라인

### 테스트 네이밍 컨벤션

```java
@Test
void test<MethodName><Scenario>() {
    // Given (준비)
    // When (실행)
    // Then (검증)
}
```

예시:
- `testCommandVOGettersAndSetters()`
- `testEscapeEmptyString()`
- `testGetAgentFuncUnknownType()`

### Given-When-Then 패턴 사용

모든 테스트는 Given-When-Then 패턴을 따릅니다:

```java
@Test
void testExample() {
    // Given - 테스트 준비
    CommandVO command = new CommandVO();
    command.setCommandId("CMD-123");

    // When - 테스트 실행
    String result = command.getCommandId();

    // Then - 결과 검증
    assertThat(result).isEqualTo("CMD-123");
}
```

### AssertJ 사용

AssertJ의 fluent API를 사용하여 가독성 높은 assertion 작성:

```java
// 기본 검증
assertThat(value).isEqualTo(expected);
assertThat(value).isNotNull();
assertThat(value).isTrue();

// 문자열 검증
assertThat(text).contains("substring");
assertThat(text).startsWith("prefix");
assertThat(text).matches("[0-9A-F]{64}");

// 컬렉션 검증
assertThat(list).hasSize(3);
assertThat(list).contains(item);
assertThat(list).isEmpty();
```

## Mock 객체 사용 예시

외부 의존성이 있는 경우 Mockito를 사용하여 Mock 객체 생성:

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private ExternalService externalService;

    @InjectMocks
    private MyService myService;

    @Test
    void testWithMock() {
        // Given
        when(externalService.getData()).thenReturn("mocked data");

        // When
        String result = myService.processData();

        // Then
        assertThat(result).isEqualTo("processed: mocked data");
        verify(externalService).getData();
    }
}
```

## 통합 테스트 vs 단위 테스트

현재 `src/test/java`는 **단위 테스트**만 포함합니다:

- **단위 테스트**: 개별 클래스/메서드의 독립적 테스트
- **통합 테스트**: 여러 컴포넌트 간의 상호작용 테스트 (별도 디렉토리 필요)

향후 통합 테스트는 다음과 같이 구성:
```
src/
├── test/          # 단위 테스트
└── integration/   # 통합 테스트
```

## 테스트 설정 파일

`src/test/resources/test-agent.properties` - 테스트용 설정 파일

## CI/CD 통합

테스트는 다음 상황에서 자동 실행되어야 합니다:

1. **로컬 개발**: 코드 커밋 전
2. **CI 파이프라인**: Pull Request 생성 시
3. **빌드 프로세스**: 배포 전 필수 실행

```bash
# CI/CD 스크립트 예시
mvn clean test
if [ $? -ne 0 ]; then
    echo "Tests failed!"
    exit 1
fi
```

## 테스트 리포트

Maven Surefire 플러그인은 다음 위치에 테스트 리포트 생성:
- `target/surefire-reports/` (Maven)
- `build/reports/tests/test/` (Gradle)

HTML 리포트를 브라우저에서 확인:
```bash
# Maven
open target/surefire-reports/index.html

# Gradle
open build/reports/tests/test/index.html
```

## 추가 테스트 필요 항목

다음 컴포넌트들은 향후 테스트 추가가 필요합니다:

- [ ] PreWork 클래스
- [ ] MainWork 클래스
- [ ] OrderCaller 클래스
- [ ] 개별 Order 구현체 (ExeShell, ReadFile 등)
- [ ] 개별 AgentFunc 구현체
- [ ] Kafka 관련 클래스 (통합 테스트)
- [x] ~~HTTP 통신 관련 클래스 (통합 테스트)~~ - MtlsTokenRenewalIntegrationTest로 커버
- [x] ~~mTLS 토큰 갱신~~ - Phase 1.5에서 완료
- [x] ~~계단식 토큰 갱신~~ - Phase 1.5에서 완료

## 통합 테스트 실행

통합 테스트는 환경 변수 설정이 필요합니다:

```bash
# 환경 변수 설정
export MTLS_INTEGRATION_TEST=true

# 테스트 서버 실행 (다른 터미널에서)
cd test-server
python mock_server.py --ssl

# 통합 테스트 실행
mvn test -Dtest=MtlsTokenRenewalIntegrationTest
```

### 계단식 토큰 갱신 통합 테스트

`MtlsTokenRenewalIntegrationTest`에서 다음 시나리오를 테스트합니다:

1. **유효한 refresh_token**: refresh_token으로 갱신 성공
2. **만료된 refresh_token + mTLS 활성화**: mTLS로 fallback하여 성공
3. **만료된 refresh_token + mTLS 비활성화**: -401 에러 반환

## 참고 자료

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ Documentation](https://assertj.github.io/doc/)

---

**Last Updated**: 2025-11-28
**Test Framework**: JUnit 5.8.2
**Total Tests**: 127 (1 skipped - integration test without env var)
**Phase**: 1.5 - mTLS & Cascading Token Renewal
