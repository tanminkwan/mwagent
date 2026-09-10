# 테스트 커버리지 빠른 시작 가이드

## 🚀 5분 안에 커버리지 확인하기

### Step 1: 테스트 실행
```bash
# Maven 사용
mvn clean test

# 또는 Gradle 사용
gradle clean test
```

### Step 2: 리포트 열기
```bash
# Maven (Windows)
start target/site/jacoco/index.html

# Gradle (Windows)
start build/reports/jacoco/test/html/index.html
```

### Step 3: 리포트 분석
1. **전체 커버리지** 확인 (Line Coverage %)
2. **패키지별 커버리지** 확인
3. **빨간색 클래스** = 테스트 없음
4. **녹색 클래스** = 테스트 완료

---

## 📊 현재 커버리지 요약

### 테스트 파일 현황
```
총 소스 파일: 40개
총 테스트 파일: 9개
커버리지 비율: ~22%
```

### 테스트 완료 (9개)
- ✅ `AgentStatusTest.java` - AgentStatus enum (NEW!)
- ✅ `RegistrationRequestTest.java` - 등록 요청 VO (NEW!)
- ✅ `RegistrationResponseTest.java` - 등록 응답 VO (NEW!)
- ✅ `RegistrationServiceTest.java` - 등록 서비스 (NEW!)
- ✅ `CommandVOTest.java` - CommandVO
- ✅ `ResultVOTest.java` - ResultVO
- ✅ `CommonTest.java` - Common 유틸리티
- ✅ `OrderTest.java` - Order 추상 클래스
- ✅ `AgentFuncFactoryTest.java` - Factory

### 테스트 필요 (31개)
**우선순위 HIGH** (보안 취약점)
- 🔴 `ExeShell.java` - Command Injection 위험
- 🔴 `ExeScript.java`
- 🔴 `ExeText.java`
- 🔴 `DownloadFile.java` - Path Traversal 위험
- 🔴 `ReadFile.java` - Path Traversal 위험

**우선순위 MEDIUM** (핵심 로직)
- 🟡 `BootstrapService.java`
- 🟡 `AgentStatusService.java`
- 🟡 `PreWork.java`
- 🟡 `FirstWork.java`
- 🟡 `MainWork.java`

---

## 🎯 빠르게 커버리지 높이기

### 방법 1: 간단한 VO 클래스 테스트 (10분)
**효과**: 커버리지 +5%

```java
// src/test/java/mwagent/vo/RawCommandsVOTest.java
@Test
void setAndGetCommands_ShouldWork() {
    RawCommandsVO vo = new RawCommandsVO();
    JSONArray commands = new JSONArray();
    commands.add("test");

    vo.setCommands(commands);
    vo.setReturnCode(1);

    assertThat(vo.getCommands()).isEqualTo(commands);
    assertThat(vo.getReturnCode()).isEqualTo(1);
}
```

### 방법 2: Registration Service 테스트 완성 (30분)
**효과**: 커버리지 +10%

```java
@Test
void register_WithValidRequest_ShouldSucceed() {
    // Given
    RegistrationRequest request = new RegistrationRequest(...);

    // When
    RegistrationResponse response = service.register(request);

    // Then
    assertThat(response.isSuccess()).isTrue();
}
```

### 방법 3: 보안 취약점 클래스 테스트 (1시간)
**효과**: 커버리지 +15%, 보안 검증

```java
@Test
void executeShell_WithMaliciousInput_ShouldFail() {
    // Given
    String maliciousCommand = "ls; rm -rf /";

    // When & Then
    assertThatThrownBy(() -> exeShell.execute(maliciousCommand))
        .isInstanceOf(SecurityException.class);
}
```

---

## 📈 단계별 목표

### Week 1 (현재)
- [x] JaCoCo 설정 완료
- [x] Registration 모듈 테스트 (4개)
- [ ] VO 클래스 테스트 (2개)
- **목표: 30% 커버리지**

### Week 2
- [ ] Order 구현체 테스트 (5개)
- [ ] Core 클래스 테스트 (3개)
- **목표: 45% 커버리지**

### Week 3-4
- [ ] AgentFunction 테스트 (6개)
- [ ] 통합 테스트
- **목표: 60% 커버리지**

### Month 2-3
- [ ] Kafka 테스트
- [ ] 엣지 케이스 테스트
- **목표: 70% 커버리지**

---

## 🛠 테스트 작성 템플릿

### VO 클래스 테스트 템플릿
```java
package mwagent.vo;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MyVOTest {

    @Test
    void constructor_ShouldSetAllFields() {
        // Given
        String field1 = "value1";

        // When
        MyVO vo = new MyVO(field1);

        // Then
        assertThat(vo.getField1()).isEqualTo(field1);
    }

    @Test
    void setters_ShouldUpdateFields() {
        // Given
        MyVO vo = new MyVO();

        // When
        vo.setField1("newValue");

        // Then
        assertThat(vo.getField1()).isEqualTo("newValue");
    }
}
```

### Service 클래스 테스트 템플릿
```java
package mwagent.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private Dependency dependency;

    private MyService service;

    @BeforeEach
    void setUp() {
        service = new MyService(dependency);
    }

    @Test
    void methodName_WithValidInput_ShouldSucceed() {
        // Given
        when(dependency.method()).thenReturn("result");

        // When
        String result = service.doSomething();

        // Then
        assertThat(result).isEqualTo("result");
        verify(dependency).method();
    }
}
```

---

## 💡 유용한 팁

### 1. 커버리지가 낮은 클래스 빠르게 찾기
리포트에서 **빨간색** 클래스를 클릭하면 어느 라인이 테스트되지 않았는지 보여줍니다.

### 2. 테스트하기 어려운 코드는?
- Static method 호출 → 인터페이스로 추상화
- Singleton 사용 → 의존성 주입
- Thread/Sleep → 테스트에서 제외

### 3. 빠른 피드백 루프
```bash
# 파일 저장하면 자동으로 테스트 실행 (Gradle)
gradle test --continuous

# 특정 테스트만 실행
mvn test -Dtest=AgentStatusTest
gradle test --tests AgentStatusTest
```

### 4. 커버리지 리포트 항상 최신으로
```bash
# Maven
mvn clean test  # 항상 clean과 함께

# Gradle
gradle clean test  # 항상 clean과 함께
```

---

## 📚 추가 자료

- [COVERAGE.md](./COVERAGE.md) - 상세 커버리지 가이드
- [TESTING.md](./TESTING.md) - 테스트 작성 가이드
- [REFACTORING_PLAN.md](./REFACTORING_PLAN.md) - Phase 5 테스트 계획

---

**시작하세요!**
```bash
mvn clean test
start target/site/jacoco/index.html
```
