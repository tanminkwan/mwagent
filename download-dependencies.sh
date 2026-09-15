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

echo "[1/8] Downloading Apache HttpClient 4.5.13..."
curl -L -o httpclient-4.5.13.jar \
  "https://repo1.maven.org/maven2/org/apache/httpcomponents/httpclient/4.5.13/httpclient-4.5.13.jar"
curl -L -o httpcore-4.4.13.jar \
  "https://repo1.maven.org/maven2/org/apache/httpcomponents/httpcore/4.4.13/httpcore-4.4.13.jar"
curl -L -o commons-logging-1.2.jar \
  "https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar"

echo "[2/8] Downloading Apache Kafka Client 3.1.0..."
curl -L -o kafka-clients-3.1.0.jar \
  "https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/3.1.0/kafka-clients-3.1.0.jar"

echo "[3/8] Downloading BouncyCastle 1.70..."
curl -L -o bcprov-jdk15on-1.70.jar \
  "https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.70/bcprov-jdk15on-1.70.jar"

echo "[4/8] Downloading JSON Simple 1.1.1..."
curl -L -o json-simple-1.1.1.jar \
  "https://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar"

echo "[5/8] Downloading Apache Commons Codec 1.11..."
curl -L -o commons-codec-1.11.jar \
  "https://repo1.maven.org/maven2/commons-codec/commons-codec/1.11/commons-codec-1.11.jar"

echo "[6/8] Downloading SLF4J 1.7.30..."
curl -L -o slf4j-api-1.7.30.jar \
  "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.30/slf4j-api-1.7.30.jar"
curl -L -o slf4j-simple-1.7.30.jar \
  "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.30/slf4j-simple-1.7.30.jar"

echo "[7/8] Downloading Eclipse Paho MQTT v5 Client 1.2.5..."
curl -L -o org.eclipse.paho.mqttv5.client-1.2.5.jar \
  "https://repo1.maven.org/maven2/org/eclipse/paho/org.eclipse.paho.mqttv5.client/1.2.5/org.eclipse.paho.mqttv5.client-1.2.5.jar"

echo "[8/8] Downloading additional dependencies..."
# Kafka 의존성
curl -L -o lz4-java-1.8.0.jar \
  "https://repo1.maven.org/maven2/org/lz4/lz4-java/1.8.0/lz4-java-1.8.0.jar"
curl -L -o snappy-java-1.1.8.4.jar \
  "https://repo1.maven.org/maven2/org/xerial/snappy/snappy-java/1.1.8.4/snappy-java-1.1.8.4.jar"
curl -L -o zstd-jni-1.5.2-1.jar \
  "https://repo1.maven.org/maven2/com/github/luben/zstd-jni/1.5.2-1/zstd-jni-1.5.2-1.jar"

cd ..

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
