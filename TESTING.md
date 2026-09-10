# Testing Guide for MwManger Agent

MwManger 프로젝트의 테스트 가이드입니다.

## 목차
- [테스트 환경 설정](#테스트-환경-설정)
- [테스트 실행](#테스트-실행)
- [테스트 작성 가이드](#테스트-작성-가이드)
- [CI/CD 통합](#cicd-통합)

## JAR 실행 테스트

### agent.properties 설정

JAR 파일을 직접 실행해서 테스트하려면 프로젝트 루트에 `agent.properties` 파일이 필요합니다.

```bash
# 테스트용 설정 파일 복사
cp test-server/test-agent.properties agent.properties
```

> **참고**: `agent.properties`는 `.gitignore`에 포함되어 있어 커밋되지 않습니다.

### 테스트용 설정 파일 위치

| 파일 | 위치 | 용도 |
|------|------|------|
| `test-agent.properties` | `src/test/resources/` | 단위 테스트용 |
| `test-agent.properties` | `test-server/` | mTLS 통합 테스트용 |
| `agent.properties` | `deploy/` | 배포 템플릿 |

### JAR 실행 테스트 방법

```bash
# 1. 빌드
mvn clean package -DskipTests
# 또는
./build-offline.bat  # Windows
./build-offline.sh   # Linux/Mac

# 2. agent.properties 준비
cp test-server/test-agent.properties agent.properties

# 3. JAR 실행 (lib 폴더 필요)
java -cp "build/mwagent.jar;lib/*" mwagent.MwAgent

# 4. 버전 확인만 하려면
java -cp "build/mwagent.jar;lib/*" mwagent.common.Version
```

---

## 테스트 환경 설정

### 필수 요구사항

- JDK 1.8 이상
- Maven 3.6+ 또는 Gradle 6+

### 테스트 의존성

프로젝트는 다음 테스트 라이브러리를 사용합니다:

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| JUnit 5 | 5.8.2 | 테스트 프레임워크 |
| Mockito | 3.12.4 | Mock 객체 생성 |
| AssertJ | 3.21.0 | Fluent assertions |

의존성은 `pom.xml` 및 `build.gradle`에 이미 설정되어 있습니다.

## 테스트 실행

### Maven으로 실행

```bash
# 모든 테스트 실행
mvn test

# 특정 테스트 클래스 실행
mvn test -Dtest=CommandVOTest

# 특정 메서드만 실행
mvn test -Dtest=CommandVOTest#testCommandVOGettersAndSetters

# 특정 패키지의 테스트 실행
mvn test -Dtest=mwagent.vo.*

# 테스트 건너뛰고 빌드
mvn package -DskipTests

# 상세 로그와 함께 테스트 실행
mvn test -X
```

### Gradle로 실행

```bash
# 모든 테스트 실행
gradle test

# 특정 테스트 클래스 실행
gradle test --tests CommandVOTest

# 특정 메서드만 실행
gradle test --tests CommandVOTest.testCommandVOGettersAndSetters

# 특정 패키지의 테스트 실행
gradle test --tests "mwagent.vo.*"

# 테스트 건너뛰고 빌드
gradle build -x test

# 테스트 리포트 생성
gradle test jacocoTestReport
```

### IDE에서 실행

#### IntelliJ IDEA
1. 테스트 파일을 열고 클래스명 옆의 녹색 화살표 클릭
2. 또는 `Ctrl+Shift+F10` (Windows/Linux) / `Cmd+Shift+R` (Mac)

#### Eclipse
1. 테스트 파일을 우클릭
2. `Run As > JUnit Test` 선택

#### VS Code
1. Java Test Runner 확장 설치
2. 테스트 파일에서 `Run Test` 링크 클릭

## 테스트 리포트

### Maven Surefire Reports

Maven은 테스트 결과를 다음 위치에 저장합니다:

```
target/surefire-reports/
├── TEST-mwagent.vo.CommandVOTest.xml
├── TEST-mwagent.vo.ResultVOTest.xml
├── mwagent.vo.CommandVOTest.txt
└── ...
```

### Gradle Test Reports

Gradle은 HTML 형식의 리포트를 생성합니다:

```
build/reports/tests/test/
├── index.html
├── classes/
└── packages/
```

브라우저에서 확인:
```bash
# Windows
start build/reports/tests/test/index.html

# Linux/Mac
open build/reports/tests/test/index.html
```

## 테스트 구조

```
src/test/
├── java/mwagent/
│   ├── vo/                         # Value Object 테스트
│   │   ├── CommandVOTest.java     # CommandVO 테스트
│   │   └── ResultVOTest.java       # ResultVO 테스트
│   ├── common/                     # 공통 유틸리티 테스트
│   │   └── CommonTest.java         # Common 클래스 테스트
│   ├── order/                      # Order 클래스 테스트
│   │   └── OrderTest.java          # 추상 Order 클래스 테스트
│   ├── agentfunction/              # AgentFunc 테스트
│   │   └── AgentFuncFactoryTest.java
│   └── README_TESTS.md             # 테스트 상세 문서
└── resources/
    └── test-agent.properties       # 테스트용 설정 파일
```

## 테스트 작성 가이드

### 1. 테스트 네이밍 컨벤션

```java
@Test
void test<MethodName><Scenario>() {
    // 테스트 내용
}
```

**예시:**
- `testCommandVOGettersAndSetters()` - getter/setter 테스트
- `testEscapeEmptyString()` - 빈 문자열 escape 테스트
- `testGetAgentFuncUnknownType()` - 알 수 없는 타입 처리 테스트

### 2. Given-When-Then 패턴

모든 테스트는 Given-When-Then 구조를 따릅니다:

```java
@Test
void testExample() {
    // Given - 테스트 준비 (데이터, Mock 설정)
    CommandVO command = new CommandVO();
    command.setCommandId("CMD-123");

    // When - 테스트 실행 (메서드 호출)
    String result = command.getCommandId();

    // Then - 결과 검증 (assertion)
    assertThat(result).isEqualTo("CMD-123");
}
```

### 3. AssertJ를 이용한 Assertion

```java
// 값 검증
assertThat(actual).isEqualTo(expected);
assertThat(actual).isNotNull();
assertThat(actual).isNotEqualTo(other);

// 문자열 검증
assertThat(text).contains("substring");
assertThat(text).startsWith("prefix");
assertThat(text).endsWith("suffix");
assertThat(text).matches("[0-9A-F]{64}");
assertThat(text).isEqualToIgnoringCase("HELLO");

// 숫자 검증
assertThat(number).isPositive();
assertThat(number).isGreaterThan(10);
assertThat(number).isBetween(1, 100);

// Boolean 검증
assertThat(flag).isTrue();
assertThat(flag).isFalse();

// 컬렉션 검증
assertThat(list).hasSize(3);
assertThat(list).contains(item);
assertThat(list).containsExactly(item1, item2);
assertThat(list).isEmpty();
assertThat(list).isNotEmpty();

// 예외 검증
assertThatThrownBy(() -> methodThatThrows())
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage("error message");
```

### 4. Mockito를 이용한 Mocking

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private ExternalService externalService;

    @InjectMocks
    private MyService myService;

    @Test
    void testWithMock() {
        // Given - Mock 동작 정의
        when(externalService.getData()).thenReturn("mocked data");

        // When
        String result = myService.processData();

        // Then
        assertThat(result).isEqualTo("processed: mocked data");

        // Mock 호출 검증
        verify(externalService).getData();
        verify(externalService, times(1)).getData();
        verify(externalService, never()).deleteData();
    }
}
```

### 5. 테스트 격리

각 테스트는 독립적이어야 합니다:

```java
class IsolatedTest {

    private CommandVO command;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 실행
        command = new CommandVO();
    }

    @AfterEach
    void tearDown() {
        // 각 테스트 후에 실행 (정리 작업)
        command = null;
    }

    @Test
    void testOne() {
        // 독립적인 테스트 1
    }

    @Test
    void testTwo() {
        // 독립적인 테스트 2
    }
}
```

## 현재 테스트 커버리지

### 테스트 완료된 컴포넌트

| 컴포넌트 | 테스트 클래스 | 커버리지 | 설명 |
|---------|-------------|---------|------|
| CommandVO | CommandVOTest | ✓ | getter/setter, toString, 기본값 |
| ResultVO | ResultVOTest | ✓ | getter/setter, toString, 기본값 |
| Common | CommonTest | ✓ | escape, fillResult, makeOneResultArray |
| Order | OrderTest | ✓ | replaceParam, getHash, convertCommand |
| AgentFuncFactory | AgentFuncFactoryTest | ✓ | 모든 Factory 메서드 |

### 테스트 필요 컴포넌트

향후 추가가 필요한 테스트:

- [ ] **PreWork** - 에이전트 등록 및 초기화 로직
- [ ] **MainWork** - 메인 루프 및 명령 처리
- [ ] **FirstWork** - Kafka 초기화 로직
- [ ] **OrderCaller** - 동적 클래스 로딩
- [ ] **개별 Order 구현체**:
  - [ ] ExeShell
  - [ ] ExeScript
  - [ ] ReadFile
  - [ ] DownloadFile
- [ ] **개별 AgentFunc 구현체**:
  - [ ] HelloFunc
  - [ ] JmxStatFunc
  - [ ] SSLCertiFunc
  - [ ] DownloadNUnzipFunc
- [ ] **Kafka 컴포넌트** (통합 테스트):
  - [ ] MwConsumerThread
  - [ ] MwProducer
  - [ ] MwHealthCheckThread
- [ ] **HTTP 통신** (통합 테스트):
  - [ ] httpPOST
  - [ ] httpGET
  - [ ] httpFileDownload

## CI/CD 통합

### GitHub Actions 예시

`.github/workflows/test.yml`:

```yaml
name: Run Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v2

    - name: Set up JDK 1.8
      uses: actions/setup-java@v2
      with:
        java-version: '8'
        distribution: 'adopt'

    - name: Run tests with Maven
      run: mvn test

    - name: Upload test results
      if: always()
      uses: actions/upload-artifact@v2
      with:
        name: test-results
        path: target/surefire-reports/
```

### Jenkins Pipeline 예시

```groovy
pipeline {
    agent any

    stages {
        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }

        stage('Publish Test Results') {
            steps {
                junit 'target/surefire-reports/*.xml'
            }
        }
    }
}
```

## 테스트 베스트 프랙티스

### DO ✓

1. **독립적인 테스트 작성** - 각 테스트는 다른 테스트에 의존하지 않음
2. **명확한 네이밍** - 테스트명으로 무엇을 테스트하는지 명확히 표현
3. **Given-When-Then 패턴 사용** - 테스트 구조를 명확히
4. **단일 책임** - 하나의 테스트는 하나의 동작만 검증
5. **Fast Tests** - 빠르게 실행되는 테스트 작성
6. **실패 메시지** - 실패 시 명확한 메시지 제공

### DON'T ✗

1. **외부 의존성** - 실제 DB, 네트워크, 파일시스템 사용 지양 (Mock 사용)
2. **테스트 간 의존성** - 테스트 실행 순서에 의존하지 않음
3. **하드코딩된 경로** - 절대 경로 대신 상대 경로 사용
4. **Sleep 사용** - Thread.sleep() 대신 적절한 동기화 사용
5. **너무 큰 테스트** - 복잡한 로직은 여러 작은 테스트로 분리

## 문제 해결

### 테스트 실패 시

1. **로그 확인**:
   ```bash
   mvn test -X  # Maven verbose 모드
   gradle test --info  # Gradle info 모드
   ```

2. **특정 테스트만 실행**:
   ```bash
   mvn test -Dtest=FailingTest
   ```

3. **디버그 모드로 실행**:
   - IDE에서 테스트를 디버그 모드로 실행
   - 브레이크포인트 설정

### 일반적인 문제

**문제**: `ClassNotFoundException`
```
해결: 의존성 확인 (mvn dependency:tree)
```

**문제**: 테스트가 실행되지 않음
```
해결: 테스트 메서드에 @Test 어노테이션 확인
```

**문제**: Mock이 작동하지 않음
```
해결: @ExtendWith(MockitoExtension.class) 어노테이션 확인
```

## 참고 자료

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)
- [Gradle Test Documentation](https://docs.gradle.org/current/userguide/java_testing.html)

---

**Last Updated**: 2025-01-23
**Test Framework**: JUnit 5.8.2
**Mocking Framework**: Mockito 3.12.4
