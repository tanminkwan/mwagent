# Dependencies - Required Libraries

MwManger Agent는 JDK 1.8 (Java 8) 이상에서 실행 가능하도록 설계되었습니다.

## JDK 요구사항

- **최소 버전**: JDK 1.8 (Java 8)
- **권장 버전**: JDK 1.8 또는 JDK 11
- **테스트 완료**: JDK 1.8, JDK 11

## 필수 라이브러리 목록

### 1. Apache HttpClient (HTTP/HTTPS 통신)

**목적**: Leebalso 서버와의 HTTP/HTTPS 통신

```xml
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
    <version>4.5.13</version>
</dependency>
```

- **버전**: 4.5.13 (JDK 1.8 호환)
- **사용 위치**:
  - `Common.java` - HTTP GET/POST 요청
  - `Common.java` - 파일 다운로드
- **주요 기능**:
  - TLS 1.2 지원
  - SSL 인증서 검증
  - Bearer Token 인증

### 2. Apache Kafka Client (Kafka 통신)

**목적**: Kafka를 통한 실시간 명령 수신 및 결과 전송

```xml
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>3.1.0</version>
</dependency>
```

- **버전**: 3.1.0 (JDK 1.8 호환)
- **사용 위치**:
  - `MwConsumerThread.java` - 명령 수신
  - `MwProducer.java` - 결과 전송
  - `MwHealthCheckThread.java` - 헬스 체크
- **주요 기능**:
  - Consumer Group 관리
  - 자동 커밋
  - 비동기 메시지 전송

**참고**: Kafka 3.1.0은 JDK 1.8과 호환됩니다 (공식 지원 버전)

### 3. BouncyCastle (암호화 및 TLS 지원)

**목적**: AIX 환경에서 TLS 1.2 지원

```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk15on</artifactId>
    <version>1.70</version>
</dependency>
```

- **버전**: 1.70 (JDK 1.8 호환)
- **사용 위치**:
  - `Common.java` - AIX에서 TLS 1.2 Security Provider
  - `SSLCertiFunc.java` - SSL 인증서 처리
- **주요 기능**:
  - TLS 1.2 프로토콜 지원
  - X.509 인증서 처리
  - 암호화 알고리즘 제공

**중요**: AIX 시스템에서는 반드시 필요합니다. IBM JDK에서 TLS 1.2 지원이 제한적이기 때문입니다.

### 4. JSON Simple (JSON 처리)

**목적**: JSON 파싱 및 생성

```xml
<dependency>
    <groupId>com.googlecode.json-simple</groupId>
    <artifactId>json-simple</artifactId>
    <version>1.1.1</version>
</dependency>
```

- **버전**: 1.1.1 (JDK 1.8 호환)
- **사용 위치**:
  - 모든 Order 클래스 - 명령 파싱
  - `Common.java` - HTTP 응답 파싱
  - 모든 결과 전송 - JSON 생성
- **주요 기능**:
  - JSONObject, JSONArray 처리
  - 경량 라이브러리

**대안**: Gson (2.8.9) 또는 Jackson (2.13.x)도 사용 가능하나, 현재 코드는 JSON Simple 기준

### 5. Apache Commons Codec (인코딩 유틸리티)

**목적**: 문자열 인코딩 및 비교

```xml
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
    <version>1.11</version>
</dependency>
```

- **버전**: 1.11 (JDK 1.8 호환)
- **사용 위치**:
  - `MwConsumerThread.java` - 문자열 비교 (StringUtils)
- **주요 기능**:
  - Base64 인코딩/디코딩
  - 문자열 유틸리티

### 6. SLF4J (로깅 - Kafka 의존성)

**목적**: Kafka 클라이언트의 로깅 요구사항 충족

```xml
<!-- SLF4J API -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>1.7.30</version>
</dependency>

<!-- SLF4J Simple Implementation -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>1.7.30</version>
</dependency>
```

- **버전**: 1.7.30 (JDK 1.8 호환, Kafka 3.1.0 호환)
- **사용 위치**: Kafka 클라이언트 내부
- **참고**: 애플리케이션은 `java.util.logging`을 사용하지만, Kafka는 SLF4J 필요

## 라이브러리 총 목록

### 런타임 의존성

| 라이브러리 | GroupId | ArtifactId | 버전 | JDK 1.8 호환 | 필수 여부 |
|-----------|---------|------------|------|-------------|----------|
| Apache HttpClient | org.apache.httpcomponents | httpclient | 4.5.13 | ✓ | 필수 |
| Apache Kafka Client | org.apache.kafka | kafka-clients | 3.1.0 | ✓ | 필수 |
| BouncyCastle | org.bouncycastle | bcprov-jdk15on | 1.70 | ✓ | AIX 필수 |
| JSON Simple | com.googlecode.json-simple | json-simple | 1.1.1 | ✓ | 필수 |
| Apache Commons Codec | commons-codec | commons-codec | 1.11 | ✓ | 필수 |
| SLF4J API | org.slf4j | slf4j-api | 1.7.30 | ✓ | 필수 |
| SLF4J Simple | org.slf4j | slf4j-simple | 1.7.30 | ✓ | 필수 |

### 테스트 의존성

| 라이브러리 | GroupId | ArtifactId | 버전 | JDK 1.8 호환 | 용도 |
|-----------|---------|------------|------|-------------|------|
| JUnit Jupiter | org.junit.jupiter | junit-jupiter | 5.8.2 | ✓ | 테스트 프레임워크 |
| Mockito Core | org.mockito | mockito-core | 3.12.4 | ✓ | Mocking |
| Mockito JUnit Jupiter | org.mockito | mockito-junit-jupiter | 3.12.4 | ✓ | Mockito-JUnit 통합 |
| AssertJ | org.assertj | assertj-core | 3.21.0 | ✓ | Fluent assertions |

## 빌드 방법

### Maven 사용

```bash
# 의존성 다운로드
mvn clean install

# 실행 가능한 JAR 생성 (모든 의존성 포함)
mvn clean package

# 생성된 파일
target/mwagent-0000.0008.0005-jar-with-dependencies.jar
```

### Gradle 사용

```bash
# 의존성 다운로드
gradle build

# Fat JAR 생성 (모든 의존성 포함)
gradle fatJar

# 생성된 파일
build/libs/mwagent-all-0000.0008.0005.jar
```

## 실행 방법

### Fat JAR 실행

```bash
# Maven으로 빌드한 경우
java -jar target/mwagent-0000.0008.0005-jar-with-dependencies.jar

# Gradle으로 빌드한 경우
java -jar build/libs/mwagent-all-0000.0008.0005.jar
```

### 수동 classpath 설정

```bash
java -cp ".:lib/*" mwagent.MwAgent
```

## 라이브러리 다운로드 (수동)

빌드 도구 없이 수동으로 설치하는 경우:

1. **Maven Central에서 다운로드**:
   - https://repo1.maven.org/maven2/

2. **필요한 JAR 파일**:
   ```
   httpclient-4.5.14.jar
   httpcore-4.4.16.jar (httpclient 의존성)
   kafka-clients-2.8.2.jar
   bcprov-jdk15on-1.70.jar
   json-simple-1.1.1.jar
   commons-codec-1.15.jar
   slf4j-api-1.7.36.jar
   slf4j-simple-1.7.36.jar
   ```

3. **lib 디렉토리에 배치**:
   ```
   mwagent/
   ├── lib/
   │   ├── httpclient-4.5.14.jar
   │   ├── httpcore-4.4.16.jar
   │   ├── kafka-clients-2.8.2.jar
   │   ├── bcprov-jdk15on-1.70.jar
   │   ├── json-simple-1.1.1.jar
   │   ├── commons-codec-1.15.jar
   │   ├── slf4j-api-1.7.36.jar
   │   └── slf4j-simple-1.7.36.jar
   ```

## JDK 1.8 호환성 확인

모든 라이브러리는 다음 조건을 충족합니다:

- ✓ Java 8 (JDK 1.8) bytecode 호환
- ✓ Java 8 API만 사용
- ✓ 안정적이고 검증된 버전
- ✓ 보안 업데이트 포함

## 라이선스

| 라이브러리 | 라이선스 |
|-----------|---------|
| Apache HttpClient | Apache License 2.0 |
| Apache Kafka Client | Apache License 2.0 |
| BouncyCastle | MIT License |
| JSON Simple | Apache License 2.0 |
| Apache Commons Codec | Apache License 2.0 |
| SLF4J | MIT License |

## 업그레이드 고려사항

### Kafka 버전 업그레이드

- Kafka 2.8.2 → 3.0.x: JDK 1.8 호환 유지
- Kafka 3.0.x → 3.1+: JDK 11 이상 필요

### HttpClient 버전 업그레이드

- HttpClient 4.5.x는 마지막 JDK 1.8 호환 버전
- HttpClient 5.x는 JDK 1.8 지원하지만 대규모 API 변경

### JSON Simple 대안

JSON Simple은 더 이상 활발히 유지보수되지 않으므로, 향후 다음 라이브러리로 마이그레이션 고려:
- **Gson**: 2.8.9 (Google, 경량)
- **Jackson**: 2.13.x (기능 풍부, 고성능)

---

**Last Updated**: 2025-01-23
**JDK Compatibility**: 1.8+
