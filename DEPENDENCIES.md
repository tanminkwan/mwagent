# Dependencies - Required Libraries

MwManger Agent는 JDK 1.8 (Java 8) 이상에서 실행 가능하도록 설계되었습니다.

## JDK 요구사항

- **최소 버전**: JDK 1.8 (Java 8)
- **권장 버전**: JDK 1.8 또는 JDK 11
- **테스트 완료**: JDK 1.8, JDK 11

## 필수 라이브러리 목록

### 1. Apache HttpClient (HTTP/HTTPS 통신)

**목적**: 미들웨어관리소(MWM) 서버와의 HTTP/HTTPS 통신

```xml
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
    <version>4.5.14</version>
</dependency>
```

- **버전**: 4.5.14 (JDK 1.8 호환, httpcore 4.4.16)
- **사용 위치**:
  - `Common.java` - HTTP GET/POST 요청
  - `Common.java` - 파일 다운로드
- **주요 기능**:
  - TLS 1.2 지원
  - SSL 인증서 검증
  - Bearer Token 인증

### 2. Eclipse Paho MQTT v5 Client (MQTT 통신)

**목적**: MQTT를 통한 실시간 명령 수신 (REST 폴링과 병행 동작)

```xml
<dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.mqttv5.client</artifactId>
    <version>1.2.5</version>
</dependency>
```

- **버전**: 1.2.5 (클래스 major version 52 = JDK 1.8 호환)
- **v3 가 아니라 v5 인 이유**: 명령 자동 만료(Message Expiry Interval)와
  세션 수명 명시(Session Expiry Interval)가 MQTT 3.1.1 에는 없다
- **사용 위치**:
  - `mqtt/MwMqttSubscriber.java` - 명령 구독
  - `mqtt/MqttService.java` - 수명주기 관리
- **주요 기능**:
  - QoS1 구독 및 cmdId 기반 중복 제거
  - 자동 재접속 및 재구독
  - 구독 전용 (발행하지 않는다. 결과 전송은 REST API 담당)
- **비고**: `mqtt_enabled` 기본값은 `false` 이며, `agent.properties` 에서 명시적으로
  `true` 로 켜지 않으면 구독자를 기동하지 않는다 (Paho 클래스도 로딩되지 않는다)

### 3. BouncyCastle (암호화 및 TLS 지원)

**목적**: AIX 환경에서 TLS 1.2 지원

```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.86</version>
</dependency>
```

- **버전**: 1.86 (`jdk18on` = Java 1.8 이상. `jdk15on` 계열은 1.70 에서 끝나 보안 수정이 없다. 패키지명이 같아 코드 변경 없음)
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
    <!-- junit 4.10 을 compile scope 로 끌어온다 (CVE-2020-15250). 런타임에 쓰지 않으므로 뺀다 -->
    <exclusions>
        <exclusion>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
        </exclusion>
    </exclusions>
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
    <version>1.22.1</version>
</dependency>
```

- **버전**: 1.22.1 (JDK 1.8 호환)
- **사용 위치**:
  - `MwConsumerThread.java` - 문자열 비교 (StringUtils)
- **주요 기능**:
  - Base64 인코딩/디코딩
  - 문자열 유틸리티

## 라이브러리 총 목록

### 런타임 의존성

| 라이브러리 | GroupId | ArtifactId | 버전 | JDK 1.8 호환 | 필수 여부 |
|-----------|---------|------------|------|-------------|----------|
| Apache HttpClient | org.apache.httpcomponents | httpclient | 4.5.14 | ✓ | 필수 |
| Eclipse Paho MQTT v5 | org.eclipse.paho | org.eclipse.paho.mqttv5.client | 1.2.5 | ✓ | MQTT 사용 시 |
| BouncyCastle | org.bouncycastle | bcprov-jdk18on | 1.86 | ✓ | AIX 필수 |
| JSON Simple | com.googlecode.json-simple | json-simple | 1.1.1 | ✓ | 필수 |
| Apache Commons Codec | commons-codec | commons-codec | 1.22.1 | ✓ | 필수 |

### 테스트 의존성

| 라이브러리 | GroupId | ArtifactId | 버전 | JDK 1.8 호환 | 용도 |
|-----------|---------|------------|------|-------------|------|
| JUnit Jupiter | org.junit.jupiter | junit-jupiter | 5.8.2 | ✓ | 테스트 프레임워크 |
| Mockito Core | org.mockito | mockito-core | 3.12.4 | ✓ | Mocking |
| Mockito JUnit Jupiter | org.mockito | mockito-junit-jupiter | 3.12.4 | ✓ | Mockito-JUnit 통합 |
| AssertJ | org.assertj | assertj-core | 3.21.0 | ✓ | Fluent assertions |

## 빌드·실행 방법

Maven 만 씁니다 (Gradle 빌드는 제거됨). 산출물은 `build/mwagent.jar` 이고, 의존성은 jar 안에 넣지 않고 `lib/` 에 둡니다.
자세한 명령은 [README.md](README.md) 의 "빌드 방법" 을 참고하세요.

```bash
java -cp "build/mwagent.jar:lib/*" mwagent.MwAgent      # Windows 는 ; 로 구분
```

## 라이브러리 다운로드 (수동)

빌드 도구 없이 수동으로 설치하는 경우:

1. **Maven Central에서 다운로드**:
   - https://repo1.maven.org/maven2/

2. **필요한 JAR 파일과 URL**: [`lib/README.md`](lib/README.md) 의 표를 따릅니다.
   파일 목록과 해시는 `lib/SHA256SUMS` 가 기준이며, `download-dependencies.*` 가 받은 뒤 `verify-lib.*` 로 검증합니다.
   `SHA256SUMS` 에 없는 jar(옛 버전 등)가 `lib/` 에 남아 있으면 검증이 실패합니다.

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
| BouncyCastle | MIT License |
| JSON Simple | Apache License 2.0 |
| Apache Commons Codec | Apache License 2.0 |

## 업그레이드 고려사항

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
