@echo off
REM =========================================
REM  오프라인 배포 패키지 준비 스크립트
REM =========================================

set DEPLOY_DIR=mwagent-offline-deployment
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%

echo =========================================
echo   Preparing Offline Deployment Package
echo =========================================
echo.

REM 1. 배포 디렉토리 생성
echo [1/5] Creating deployment directory...
if exist %DEPLOY_DIR% rmdir /s /q %DEPLOY_DIR%
mkdir %DEPLOY_DIR%
mkdir %DEPLOY_DIR%\lib
mkdir %DEPLOY_DIR%\src

REM 2. 소스 코드 복사
echo [2/5] Copying source code...
xcopy /E /I /Q src %DEPLOY_DIR%\src

REM 3. 라이브러리 복사
echo [3/5] Copying library files (12 JARs)...
copy lib\*.jar %DEPLOY_DIR%\lib\ >nul

REM 4. 빌드 스크립트 복사
echo [4/5] Copying build scripts...
copy build-offline.sh %DEPLOY_DIR%\ >nul
copy build-offline.bat %DEPLOY_DIR%\ >nul
copy build.gradle %DEPLOY_DIR%\ >nul

REM 5. 문서 복사
echo [5/5] Copying documentation...
copy README.md %DEPLOY_DIR%\ >nul
copy lib\README.md %DEPLOY_DIR%\lib\ >nul
echo.

REM 검증
echo =========================================
echo   Verification
echo =========================================
echo Source files:
dir /s /b %DEPLOY_DIR%\src\main\java\*.java | find /c ".java"

echo.
echo Library files:
dir /b %DEPLOY_DIR%\lib\*.jar | find /c ".jar"

echo.
echo =========================================
echo   Package Ready
echo =========================================
echo.
echo Deployment package created: %DEPLOY_DIR%\
echo.
echo Next steps:
echo 1. Copy '%DEPLOY_DIR%' directory to USB/Network drive
echo 2. Transfer to offline environment
echo 3. Run 'build-offline.bat' to build
echo 4. Create 'agent.properties' configuration file
echo 5. Run the agent
echo.
echo =========================================

pause
