@echo off
REM 오프라인 빌드 스크립트 (Windows)
REM Maven/Gradle 없이 javac만으로 빌드합니다.

setlocal enabledelayedexpansion

set PROJECT_NAME=mwagent
set MAIN_CLASS=mwagent.MwAgent

echo =========================================
echo   MwManger Offline Build
echo =========================================
echo Project: %PROJECT_NAME%
echo Main Class: %MAIN_CLASS%
echo.

REM 1. Clean
echo [1/5] Cleaning build directory...
if exist build\classes rmdir /s /q build\classes
mkdir build\classes

REM 2. Classpath 설정
echo [2/5] Setting up classpath...
set CLASSPATH=.
for %%f in (lib\*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
echo Classpath: %CLASSPATH%

REM 3. 소스 파일 목록 생성
echo [3/5] Compiling Java sources...
dir /s /b src\main\java\*.java > sources.txt

REM 4. 컴파일
javac -encoding UTF-8 -source 1.8 -target 1.8 -d build\classes -cp "%CLASSPATH%" @sources.txt

if errorlevel 1 (
    echo ERROR: Compilation failed!
    del sources.txt
    exit /b 1
)

del sources.txt
echo Compilation successful!

REM 4. Manifest 생성
echo [4/5] Creating manifest...
(
echo Manifest-Version: 1.0
echo Main-Class: %MAIN_CLASS%
echo Class-Path: bcprov-jdk15on-1.70.jar commons-codec-1.11.jar commons-logging-1.2.jar httpclient-4.5.13.jar httpcore-4.4.13.jar json-simple-1.1.1.jar kafka-clients-3.1.0.jar lz4-java-1.8.0.jar slf4j-api-1.7.30.jar slf4j-simple-1.7.30.jar snappy-java-1.1.8.4.jar zstd-jni-1.5.2-1.jar
) > build\MANIFEST.MF

REM 5. JAR 패키징
echo [5/5] Creating JAR package...
cd build\classes
jar cvfm "..\%PROJECT_NAME%.jar" ..\MANIFEST.MF .
cd ..\..
del build\MANIFEST.MF

echo.
echo =========================================
echo   Build Complete!
echo =========================================
echo.
echo Generated files:
echo   - build\%PROJECT_NAME%.jar
echo.
echo To run:
echo   java -jar build\%PROJECT_NAME%.jar
echo.
echo Note: lib\*.jar files must be in the same directory
echo.
