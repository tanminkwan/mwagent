#!/usr/bin/env bash
# 의존성 라이브러리 다운로드 스크립트
# 인터넷이 되는 환경에서 한번 실행하여 모든 JAR를 lib/에 다운로드합니다.

set -e

echo "========================================="
echo "  MwManger Dependency Downloader"
echo "========================================="
echo ""

# lib 디렉토리 생성
mkdir -p lib
cd lib

echo "[1/5] Downloading Apache HttpClient 4.5.14..."
curl -L -o httpclient-4.5.14.jar \
  "https://repo1.maven.org/maven2/org/apache/httpcomponents/httpclient/4.5.14/httpclient-4.5.14.jar"
curl -L -o httpcore-4.4.16.jar \
  "https://repo1.maven.org/maven2/org/apache/httpcomponents/httpcore/4.4.16/httpcore-4.4.16.jar"
curl -L -o commons-logging-1.2.jar \
  "https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar"

echo "[2/5] Downloading BouncyCastle 1.86..."
curl -L -o bcprov-jdk18on-1.86.jar \
  "https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.86/bcprov-jdk18on-1.86.jar"

echo "[3/5] Downloading JSON Simple 1.1.1..."
curl -L -o json-simple-1.1.1.jar \
  "https://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar"

echo "[4/5] Downloading Apache Commons Codec 1.22.1..."
curl -L -o commons-codec-1.22.1.jar \
  "https://repo1.maven.org/maven2/commons-codec/commons-codec/1.22.1/commons-codec-1.22.1.jar"

echo "[5/5] Downloading Eclipse Paho MQTT v5 Client 1.2.5..."
curl -L -o org.eclipse.paho.mqttv5.client-1.2.5.jar \
  "https://repo1.maven.org/maven2/org/eclipse/paho/org.eclipse.paho.mqttv5.client/1.2.5/org.eclipse.paho.mqttv5.client-1.2.5.jar"

cd ..

# SHA256SUMS 에 없는 옛 jar 제거 (버전을 올린 뒤 남아 있으면 classpath 에서 충돌한다)
for jar in lib/*.jar; do
    [ -f "$jar" ] || continue
    base="${jar#lib/}"
    if ! awk -v n="$base" '{ f = $2; sub(/^\*/, "", f); sub(/\r$/, "", f); if (f == n) found = 1 } END { exit !found }' lib/SHA256SUMS; then
        echo "Removing old jar not in lib/SHA256SUMS: $jar"
        rm -f "$jar"
    fi
done

# 무결성 검증 (lib/SHA256SUMS 와 비교. 하나라도 다르면 중단)
echo ""
echo "Verifying checksums (lib/SHA256SUMS)..."
if ! bash ./verify-lib.sh; then
    echo "ERROR: Checksum mismatch. Do not use these files."
    exit 1
fi

echo ""
echo "========================================="
echo "  다운로드 완료!"
echo "========================================="
echo ""
echo "다운로드된 JAR 파일:"
ls -lh lib/*.jar | awk '{print "  " $9 " (" $5 ")"}'
echo ""
echo "총 파일 수: $(ls lib/*.jar | wc -l)개"
echo ""
echo "다음 단계:"
echo "1. lib/ 디렉토리를 오프라인 환경으로 복사"
echo "2. ./build-offline.sh 실행하여 빌드"
