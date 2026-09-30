# Dependency Libraries (lib/)

이 디렉토리는 오프라인 빌드 및 배포를 위한 의존성 라이브러리 JAR 파일들을 저장합니다.

## 개요

인터넷이 차단된 환경에서 빌드 및 배포하기 위한 의존성 라이브러리를 둡니다.

**JAR 파일은 git 에 들어 있지 않습니다.** 아래 스크립트로 받으면 `SHA256SUMS` 로 자동 검증됩니다.
버전을 바꿀 때는 `download-dependencies.*`, `pom.xml`, `SHA256SUMS` 를 함께 고칩니다.

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
| org.eclipse.paho.mqttv5.client-1.2.5.jar | 1.2.5 | https://repo1.maven.org/maven2/org/eclipse/paho/org.eclipse.paho.mqttv5.client/1.2.5/org.eclipse.paho.mqttv5.client-1.2.5.jar |
| bcprov-jdk18on-1.86.jar | 1.86 | https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.86/bcprov-jdk18on-1.86.jar |
| json-simple-1.1.1.jar | 1.1.1 | https://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar |
| commons-codec-1.11.jar | 1.11 | https://repo1.maven.org/maven2/commons-codec/commons-codec/1.11/commons-codec-1.11.jar |

## 필요한 JAR 파일 (총 7개)

1. **httpclient-4.5.13.jar** - HTTP/HTTPS 통신
2. **httpcore-4.4.13.jar** - HttpClient 코어
3. **commons-logging-1.2.jar** - HttpClient 로깅
4. **org.eclipse.paho.mqttv5.client-1.2.5.jar** - MQTT v5 클라이언트 (명령 구독)
5. **bcprov-jdk18on-1.86.jar** - BouncyCastle (TLS 1.2 지원, AIX)
6. **json-simple-1.1.1.jar** - JSON 처리
7. **commons-codec-1.11.jar** - 인코딩 유틸리티

`lib/SHA256SUMS` 에 없는 jar(옛 버전 등)가 남아 있으면 `verify-lib.*` 가 실패합니다.
빌드·배포 스크립트가 `lib/*.jar` 를 모두 classpath 에 넣기 때문입니다. `download-dependencies.*` 는 목록에 없는 jar 를 지웁니다.

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
├── lib/                    ← 모든 JAR 파일 (7개)
│   ├── httpclient-4.5.13.jar
│   ├── bcprov-jdk18on-1.86.jar
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
ls lib/*.jar | wc -l   # 7개여야 함

# Windows
dir /b lib\*.jar | find /c ".jar"   # 7개여야 함
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
- Apache License 2.0: HttpClient, HttpCore, Commons Logging, Commons Codec, JSON Simple
- Eclipse Public License 2.0 / EDL 1.0: Eclipse Paho MQTT
- MIT License (Bouncy Castle Licence): BouncyCastle

자세한 내용은 [DEPENDENCIES.md](../DEPENDENCIES.md) 참조

---

**Last Updated**: 2026-09-30
**Total JARs**: 7
