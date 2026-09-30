@echo off
REM 의존성 라이브러리 다운로드 스크립트 (Windows)
REM 인터넷이 되는 환경에서 한번 실행하여 모든 JAR를 lib/에 다운로드합니다.

echo =========================================
echo   MwManger Dependency Downloader
echo =========================================
echo.

REM lib 디렉토리 생성
if not exist lib mkdir lib
cd lib

echo [1/5] Downloading Apache HttpClient 4.5.14...
curl -L -o httpclient-4.5.14.jar "https://repo1.maven.org/maven2/org/apache/httpcomponents/httpclient/4.5.14/httpclient-4.5.14.jar"
curl -L -o httpcore-4.4.16.jar "https://repo1.maven.org/maven2/org/apache/httpcomponents/httpcore/4.4.16/httpcore-4.4.16.jar"
curl -L -o commons-logging-1.2.jar "https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar"

echo [2/5] Downloading BouncyCastle 1.86...
curl -L -o bcprov-jdk18on-1.86.jar "https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.86/bcprov-jdk18on-1.86.jar"

echo [3/5] Downloading JSON Simple 1.1.1...
curl -L -o json-simple-1.1.1.jar "https://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar"

echo [4/5] Downloading Apache Commons Codec 1.22.1...
curl -L -o commons-codec-1.22.1.jar "https://repo1.maven.org/maven2/commons-codec/commons-codec/1.22.1/commons-codec-1.22.1.jar"

echo [5/5] Downloading Eclipse Paho MQTT v5 Client 1.2.5...
curl -L -o org.eclipse.paho.mqttv5.client-1.2.5.jar "https://repo1.maven.org/maven2/org/eclipse/paho/org.eclipse.paho.mqttv5.client/1.2.5/org.eclipse.paho.mqttv5.client-1.2.5.jar"

cd ..

REM SHA256SUMS 에 없는 옛 jar 제거 (버전을 올린 뒤 남아 있으면 classpath 에서 충돌한다)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$l=@{}; Get-Content lib\SHA256SUMS | ForEach-Object { $p=$_.Trim() -split '\s+',2; if ($p.Count -eq 2) { $l[$p[1].TrimStart('*').ToLower()]=$true } }; Get-ChildItem lib -Filter *.jar | Where-Object { -not $l.ContainsKey($_.Name.ToLower()) } | ForEach-Object { Write-Host ('Removing old jar not in lib\SHA256SUMS: ' + $_.Name); Remove-Item $_.FullName }"

REM 무결성 검증 (lib\SHA256SUMS 와 비교. 하나라도 다르면 중단)
echo.
echo Verifying checksums (lib\SHA256SUMS)...
powershell -NoProfile -ExecutionPolicy Bypass -File verify-lib.ps1
if errorlevel 1 (
    echo ERROR: Checksum mismatch. Do not use these files.
    exit /b 1
)

echo.
echo =========================================
echo   다운로드 완료!
echo =========================================
echo.
echo 다운로드된 JAR 파일:
dir /b lib\*.jar
echo.
echo 다음 단계:
echo 1. lib\ 디렉토리를 오프라인 환경으로 복사
echo 2. build-offline.bat 실행하여 빌드
echo.
pause
