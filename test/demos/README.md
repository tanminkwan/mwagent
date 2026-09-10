# Test Demos

이 디렉토리는 MwManger 프로젝트의 간단한 테스트 데모 파일들을 포함합니다.

## 개요

Maven/Gradle 없이 직접 실행 가능한 간단한 테스트 프로그램들입니다. 테스트 데이터 개념 이해와 빠른 검증용으로 사용됩니다.

## 파일 목록

### 1. TestDataDemo.java
**목적**: 테스트 데이터 개념 설명 (영어)

테스트 데이터가 무엇인지, 어떻게 만들고 사용하는지를 보여주는 교육용 데모입니다.

**실행 방법**:
```bash
javac TestDataDemo.java
java TestDataDemo
```

**주요 내용**:
- 실제 데이터 vs 테스트 데이터 비교
- 테스트 데이터 생성 방법
- Given-When-Then 패턴 설명
- 다양한 시나리오 예제 (정상/빈값/null/특수문자)

---

### 2. DirectTest.java
**목적**: 테스트 데이터 개념 설명 (한글)

TestDataDemo의 한글 버전으로, 한국어로 테스트 데이터를 설명합니다.

**실행 방법**:
```bash
javac DirectTest.java
java DirectTest
```

**주요 내용**:
- 테스트 데이터란 무엇인가?
- 실제 명령 데이터 예시
- 특수문자 escape 테스트
- Given-When-Then 테스트 흐름

---

### 3. QuickTest.java
**목적**: VO 클래스 빠른 테스트

CommandVO와 ResultVO의 기본 동작을 빠르게 검증하는 독립 실행형 테스트입니다.

**실행 방법**:
```bash
# 프로젝트 루트에서 실행
javac -cp ".:vo/*" test/demos/QuickTest.java
java -cp ".:vo/*:test/demos" QuickTest

# 또는 Fat JAR 사용
javac -cp "target/mwagent-0000.0008.0005-jar-with-dependencies.jar" test/demos/QuickTest.java
java -cp "target/mwagent-0000.0008.0005-jar-with-dependencies.jar:test/demos" QuickTest
```

**테스트 항목**:
- ✅ CommandVO getter/setter
- ✅ CommandVO toString()
- ✅ CommandVO 기본값 확인
- ✅ ResultVO getter/setter
- ✅ ResultVO 기본값 확인

**출력 예시**:
```
========================================
  MwManger Agent - Quick Test
========================================

[TEST 1] CommandVO - 기본 동작
  ✓ PASS - 모든 getter/setter 정상 동작
    - CommandId: CMD-123
    - HostName: server01
    - FileName: test.sh

...

========================================
  Test Results
========================================
✓ Passed: 5
✗ Failed: 0
  Total:  5

🎉 ALL TESTS PASSED!
```

---

### 4. SimpleTest.java
**목적**: 간단한 수동 테스트 러너

VO 클래스와 Common 유틸리티를 테스트하는 assert 기반 테스트입니다.

**실행 방법**:
```bash
# 프로젝트 루트에서 실행 (assert 활성화 필요)
javac -cp ".:vo/*:common/*" test/demos/SimpleTest.java
java -ea -cp ".:vo/*:common/*:test/demos" SimpleTest

# 또는 Fat JAR 사용
javac -cp "target/mwagent-0000.0008.0005-jar-with-dependencies.jar" test/demos/SimpleTest.java
java -ea -cp "target/mwagent-0000.0008.0005-jar-with-dependencies.jar:test/demos" SimpleTest
```

**테스트 항목**:
- ✅ CommandVO getter/setter
- ✅ ResultVO getter/setter
- ✅ Common.escape() - 특수문자 처리
- ✅ Common.fillResult() - 결과 채우기

**출력 예시**:
```
========================================
  MwManger Agent - Simple Test Runner
========================================

[TEST] CommandVO getter/setter
  ✓ PASS: CommandVO getter/setter works correctly
[TEST] ResultVO getter/setter
  ✓ PASS: ResultVO getter/setter works correctly
[TEST] Common.escape()
  ✓ PASS: Common.escape() handles all special characters correctly
[TEST] Common.fillResult()
  ✓ PASS: Common.fillResult() fills result correctly

========================================
  Test Results
========================================
Passed: 4
Failed: 0
Total:  4

✓ ALL TESTS PASSED!
```

---

## 정식 테스트 vs 데모 테스트

### 정식 테스트 (src/test/java/)
- **프레임워크**: JUnit 5
- **빌드 도구**: Maven/Gradle
- **목적**: CI/CD, 자동화 테스트
- **실행**: `mvn test` or `gradle test`
- **커버리지**: 상세한 테스트 케이스와 검증

### 데모 테스트 (test/demos/)
- **프레임워크**: 없음 (순수 Java)
- **빌드 도구**: 불필요
- **목적**: 빠른 검증, 학습, 데모
- **실행**: `javac` + `java`
- **커버리지**: 핵심 기능만 간단히 검증

---

## 사용 시나리오

### 시나리오 1: 테스트 데이터 개념 이해
신입 개발자나 테스트를 처음 접하는 사람이 테스트 데이터가 무엇인지 이해하고 싶을 때:
```bash
java TestDataDemo     # 영어로 설명
java DirectTest       # 한글로 설명
```

### 시나리오 2: 빠른 동작 확인
VO 클래스를 수정한 후 빠르게 동작을 확인하고 싶을 때:
```bash
java -cp "...:test/demos" QuickTest
```

### 시나리오 3: 유틸리티 함수 검증
Common 클래스의 escape() 같은 유틸리티 함수를 테스트하고 싶을 때:
```bash
java -ea -cp "...:test/demos" SimpleTest
```

---

## 주의사항

1. **의존성**: 이 테스트들은 MwManger 프로젝트의 클래스들을 사용하므로, 클래스패스를 올바르게 설정해야 합니다.

2. **Assert 활성화**: SimpleTest.java는 assert 문을 사용하므로 `-ea` 플래그가 필요합니다.

3. **컴파일 순서**: 프로젝트를 먼저 빌드한 후 테스트를 실행하세요.

4. **정식 테스트와 혼동 금지**: 이 파일들은 데모 및 학습용입니다. 정식 CI/CD 파이프라인에서는 `src/test/java/`의 JUnit 테스트를 사용하세요.

---

## 빌드된 JAR 사용하기

프로젝트를 빌드한 후 Fat JAR를 사용하면 더 간단합니다:

```bash
# 1. 프로젝트 빌드
mvn clean package
# 또는
gradle fatJar

# 2. 테스트 컴파일
javac -cp "target/mwagent-0000.0008.0005-jar-with-dependencies.jar" test/demos/*.java

# 3. 테스트 실행
cd test/demos
java -cp "../../target/mwagent-0000.0008.0005-jar-with-dependencies.jar:." QuickTest
java -ea -cp "../../target/mwagent-0000.0008.0005-jar-with-dependencies.jar:." SimpleTest
java TestDataDemo
java DirectTest
```

---

## 관련 문서

- [프로젝트 README](../../README.md)
- [정식 테스트 가이드](../../TESTING.md)
- [정식 테스트 README](../../src/test/java/mwagent/README_TESTS.md)

---

**Last Updated**: 2025-01-23
**Version**: 0000.0008.0005
