# Dependency Libraries (lib/)

이 디렉토리는 오프라인 빌드 및 배포를 위한 의존성 라이브러리 JAR 파일들을 저장합니다.

## 개요

인터넷이 차단된 환경에서 빌드 및 배포하기 위해 모든 의존성 라이브러리를 포함합니다.

## 라이브러리 다운로드

### 자동 다운로드 (권장)

인터넷이 연결된 환경에서 실행:

**Linux/Mac:**
```bash
./download-dependencies.sh
```

**Windows:**
```bash
download-dependencies.bat
```

### 수동 다운로드

필요한 경우 Maven Central에서 직접 다운로드:

| 파일명 | 버전 | 다운로드 URL |
|--------|------|-------------|
| httpclient-4.5.13.jar | 4.5.13 | https://repo1.maven.org/maven2/org/apache/httpcomponents/httpclient/4.5.13/httpclient-4.5.13.jar |
| httpcore-4.4.13.jar | 4.4.13 | https://repo1.maven.org/maven2/org/apache/httpcomponents/httpcore/4.4.13/httpcore-4.4.13.jar |
| commons-logging-1.2.jar | 1.2 | https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar |
| kafka-clients-3.1.0.jar | 3.1.0 | https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/3.1.0/kafka-clients-3.1.0.jar |
| org.eclipse.paho.mqttv5.client-1.2.5.jar | 1.2.5 | https://repo1.maven.org/maven2/org/eclipse/paho/org.eclipse.paho.mqttv5.client/1.2.5/org.eclipse.paho.mqttv5.client-1.2.5.jar |
| bcprov-jdk15on-1.70.jar | 1.70 | https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.70/bcprov-jdk15on-1.70.jar |
| json-simple-1.1.1.jar | 1.1.1 | https://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar |
| commons-codec-1.11.jar | 1.11 | https://repo1.maven.org/maven2/commons-codec/commons-codec/1.11/commons-codec-1.11.jar |
| slf4j-api-1.7.30.jar | 1.7.30 | https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.30/slf4j-api-1.7.30.jar |
| slf4j-simple-1.7.30.jar | 1.7.30 | https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.30/slf4j-simple-1.7.30.jar |
| lz4-java-1.8.0.jar | 1.8.0 | https://repo1.maven.org/maven2/org/lz4/lz4-java/1.8.0/lz4-java-1.8.0.jar |
| snappy-java-1.1.8.4.jar | 1.1.8.4 | https://repo1.maven.org/maven2/org/xerial/snappy/snappy-java/1.1.8.4/snappy-java-1.1.8.4.jar |
| zstd-jni-1.5.2-1.jar | 1.5.2-1 | https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.2-1/zstd-jni-1.5.2-1.jar |

## 필요한 JAR 파일 (총 13개)

### 핵심 의존성
1. **httpclient-4.5.13.jar** - HTTP/HTTPS 통신
2. **httpcore-4.4.13.jar** - HttpClient 코어
3. **commons-logging-1.2.jar** - HttpClient 로깅
4. **kafka-clients-3.1.0.jar** - Kafka 클라이언트
5. **org.eclipse.paho.mqttv5.client-1.2.5.jar** - MQTT v5 클라이언트 (명령 구독)
6. **bcprov-jdk15on-1.70.jar** - BouncyCastle (TLS 1.2 지원)
7. **json-simple-1.1.1.jar** - JSON 처리
8. **commons-codec-1.11.jar** - 인코딩 유틸리티
9. **slf4j-api-1.7.30.jar** - SLF4J API
10. **slf4j-simple-1.7.30.jar** - SLF4J 구현체

### Kafka 의존성
11. **lz4-java-1.8.0.jar** - LZ4 압축
12. **snappy-java-1.1.8.4.jar** - Snappy 압축
13. **zstd-jni-1.5.2-1.jar** - Zstandard 압축

## 사용 방법

### 오프라인 빌드

JAR 파일 다운로드 후 오프라인 환경으로 이동:

**Linux/Mac:**
```bash
./build-offline.sh
```

**Windows:**
```bash
build-offline.bat
```

### 직접 실행

```bash
# Windows
java -cp "lib/*;build/classes" mwagent.MwAgent

# Linux/Mac
java -cp "lib/*:build/classes" mwagent.MwAgent
```

## 배포 패키지 구성

오프라인 환경으로 배포 시 다음 파일들을 함께 복사:

```
mwagent/
├── lib/                    ← 모든 JAR 파일 (13개)
│   ├── httpclient-4.5.13.jar
│   ├── kafka-clients-3.1.0.jar
│   └── ...
├── build-offline.sh        ← 오프라인 빌드 스크립트
├── build-offline.bat
├── src/                    ← 소스 코드
└── agent.properties        ← 설정 파일
```

## 검증

다운로드 완료 후 파일 확인:

```bash
# Linux/Mac
ls -lh lib/*.jar | wc -l   # 13개여야 함

# Windows
dir /b lib\*.jar | find /c ".jar"   # 13개여야 함
```

## 문제 해결

### 다운로드 실패 시
- 인터넷 연결 확인
- 방화벽 설정 확인 (Maven Central 접근)
- 프록시 설정이 필요한 경우 curl 옵션 추가

### 버전 불일치 시
- 이 README의 버전과 download-dependencies 스크립트의 버전이 일치하는지 확인
- Maven Central에서 직접 다운로드

## 라이선스

각 라이브러리는 해당 라이선스를 따릅니다:
- Apache License 2.0: HttpClient, Kafka, Commons Codec
- Eclipse Public License 2.0 / EDL 1.0: Eclipse Paho MQTT
- MIT License: BouncyCastle, JSON Simple, SLF4J
- BSD License: LZ4, Snappy, Zstandard

자세한 내용은 [DEPENDENCIES.md](../DEPENDENCIES.md) 참조

---

**Last Updated**: 2025-01-23
**Total JARs**: 13
