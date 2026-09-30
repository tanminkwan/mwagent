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

REM 2. 라이브러리 검증 + Classpath 설정
REM lib\*.jar 는 git 에 없다. 온라인 PC 에서 download-dependencies.bat 로 받아 함께 반입한다.
echo [2/5] Verifying libraries and setting up classpath...
if not exist lib\*.jar (
    echo ERROR: lib\*.jar not found. Run download-dependencies.bat on an online machine
    echo        or use the offline deployment package, and copy lib\ here.
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File verify-lib.ps1
if errorlevel 1 (
    echo ERROR: lib verification failed.
    exit /b 1
)
set CLASSPATH=.
for %%f in (lib\*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
echo Classpath: %CLASSPATH%

REM 3. 소스 파일 목록 생성
echo [3/5] Compiling Java sources...
dir /s /b src\main\java\*.java > sources.txt

REM 4. 컴파일
REM JDK 9+ 는 --release 8 로 Java 9+ API 사용을 컴파일 단계에서 막는다. JDK 8 은 -source/-target 1.8
set JAVAC_VER=
for /f "tokens=2" %%v in ('javac -version 2^>^&1') do set JAVAC_VER=%%v
set RELEASE_OPTS=--release 8
if "!JAVAC_VER:~0,2!"=="1." set RELEASE_OPTS=-source 1.8 -target 1.8
echo javac !JAVAC_VER! (!RELEASE_OPTS!)
javac -encoding UTF-8 !RELEASE_OPTS! -d build\classes -cp "%CLASSPATH%" @sources.txt

if errorlevel 1 (
    echo ERROR: Compilation failed!
    del sources.txt
    exit /b 1
)

del sources.txt
echo Compilation successful!

REM 4. Manifest 생성
REM Class-Path 는 lib\*.jar 에서 동적 생성한다 (build-offline.sh 와 동일). 의존성을 추가해도 이 스크립트를 고칠 필요가 없다.
echo [4/5] Creating manifest...
set JARLIST=
for %%f in (lib\*.jar) do set "JARLIST=!JARLIST!%%~nxf "
(
echo Manifest-Version: 1.0
echo Main-Class: %MAIN_CLASS%
echo Class-Path: !JARLIST!
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
