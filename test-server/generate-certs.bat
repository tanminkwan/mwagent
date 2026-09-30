@echo off
REM Certificate generation script for mTLS testing (Windows)
REM Requires OpenSSL and Java keytool in PATH
REM
REM Certificate Subject Format:
REM   CN={hostname}_{username}_J, OU=agent, O=MWM Project, C=KR
REM   - CN: Agent ID (hostname_username_J)
REM   - OU: usertype (agent) - identifies this as an agent certificate

setlocal enabledelayedexpansion

set CERTS_DIR=certs
set DAYS_VALID=365

echo ==========================================
echo Generating certificates for mTLS testing
echo ==========================================

REM Create certs directory
if exist "%CERTS_DIR%" rmdir /s /q "%CERTS_DIR%"
mkdir "%CERTS_DIR%"
cd "%CERTS_DIR%"

REM ==================== 1. Create CA (Certificate Authority) ====================
echo.
echo [1/4] Creating CA (Certificate Authority)...

openssl genrsa -out ca.key 4096
if errorlevel 1 goto :error

openssl req -x509 -new -nodes -key ca.key -sha256 -days %DAYS_VALID% -out ca.crt -subj "//CN=MWM Test CA\OU=CA\O=MWM Project\C=KR"
if errorlevel 1 goto :error

echo    * CA certificate created: ca.crt

REM ==================== 2. Create Server Certificate ====================
echo.
echo [2/4] Creating server certificate...

openssl genrsa -out server.key 2048
if errorlevel 1 goto :error

openssl req -new -key server.key -out server.csr -subj "//CN=localhost\OU=server\O=MWM Project\C=KR"
if errorlevel 1 goto :error

REM Create server extensions file
(
echo authorityKeyIdentifier=keyid,issuer
echo basicConstraints=CA:FALSE
echo keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
echo subjectAltName = @alt_names
echo.
echo [alt_names]
echo DNS.1 = localhost
echo DNS.2 = 127.0.0.1
echo IP.1 = 127.0.0.1
) > server-ext.cnf

openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days %DAYS_VALID% -sha256 -extfile server-ext.cnf
if errorlevel 1 goto :error

echo    * Server certificate created: server.crt

REM ==================== 3. Create Agent Client Certificates ====================
echo.
echo [3/4] Creating agent client certificates...

REM Agent test001: hostname=testserver01, username=appuser
REM Subject: CN=testserver01_appuser_J, OU=agent, O=MWM Project, C=KR
set AGENT_ID=agent-test001
set AGENT_CN=testserver01_appuser_J
echo    Creating certificate for %AGENT_ID% (CN=%AGENT_CN%)...

openssl genrsa -out %AGENT_ID%.key 2048
if errorlevel 1 goto :error

openssl req -new -key %AGENT_ID%.key -out %AGENT_ID%.csr -subj "//CN=%AGENT_CN%\OU=agent\O=MWM Project\C=KR"
if errorlevel 1 goto :error

openssl x509 -req -in %AGENT_ID%.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out %AGENT_ID%.crt -days %DAYS_VALID% -sha256
if errorlevel 1 goto :error

openssl pkcs12 -export -in %AGENT_ID%.crt -inkey %AGENT_ID%.key -out %AGENT_ID%.p12 -name %AGENT_ID% -password pass:agent-password -legacy
if errorlevel 1 goto :error

echo    * %AGENT_ID% certificate created
echo      Subject: CN=%AGENT_CN%, OU=agent, O=MWM Project, C=KR

REM Agent test002: hostname=testserver02, username=svcuser
set AGENT_ID=agent-test002
set AGENT_CN=testserver02_svcuser_J
echo    Creating certificate for %AGENT_ID% (CN=%AGENT_CN%)...

openssl genrsa -out %AGENT_ID%.key 2048
if errorlevel 1 goto :error

openssl req -new -key %AGENT_ID%.key -out %AGENT_ID%.csr -subj "//CN=%AGENT_CN%\OU=agent\O=MWM Project\C=KR"
if errorlevel 1 goto :error

openssl x509 -req -in %AGENT_ID%.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out %AGENT_ID%.crt -days %DAYS_VALID% -sha256
if errorlevel 1 goto :error

openssl pkcs12 -export -in %AGENT_ID%.crt -inkey %AGENT_ID%.key -out %AGENT_ID%.p12 -name %AGENT_ID% -password pass:agent-password -legacy
if errorlevel 1 goto :error

echo    * %AGENT_ID% certificate created
echo      Subject: CN=%AGENT_CN%, OU=agent, O=MWM Project, C=KR

REM Agent test003: hostname=testserver03, username=testuser (for expired refresh token testing)
set AGENT_ID=agent-test003
set AGENT_CN=testserver03_testuser_J
echo    Creating certificate for %AGENT_ID% (CN=%AGENT_CN%)...

openssl genrsa -out %AGENT_ID%.key 2048
if errorlevel 1 goto :error

openssl req -new -key %AGENT_ID%.key -out %AGENT_ID%.csr -subj "//CN=%AGENT_CN%\OU=agent\O=MWM Project\C=KR"
if errorlevel 1 goto :error

openssl x509 -req -in %AGENT_ID%.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out %AGENT_ID%.crt -days %DAYS_VALID% -sha256
if errorlevel 1 goto :error

openssl pkcs12 -export -in %AGENT_ID%.crt -inkey %AGENT_ID%.key -out %AGENT_ID%.p12 -name %AGENT_ID% -password pass:agent-password -legacy
if errorlevel 1 goto :error

echo    * %AGENT_ID% certificate created
echo      Subject: CN=%AGENT_CN%, OU=agent, O=MWM Project, C=KR

REM ==================== 4. Create Java Truststore ====================
echo.
echo [4/4] Creating Java truststore...

keytool -import -trustcacerts -alias mwm-ca -file ca.crt -keystore truststore.jks -storepass truststore-password -noprompt
if errorlevel 1 goto :error

echo    * Truststore created: truststore.jks

REM ==================== Cleanup ====================
echo.
echo Cleaning up temporary files...
del /Q *.csr *.srl server-ext.cnf 2>nul

REM ==================== Summary ====================
echo.
echo ==========================================
echo Certificate generation complete!
echo ==========================================
echo.
echo Generated files:
echo   CA:
echo     - ca.crt (CA certificate)
echo     - ca.key (CA private key)
echo.
echo   Server:
echo     - server.crt (Server certificate, OU=server)
echo     - server.key (Server private key)
echo.
echo   Agent Clients (OU=agent):
echo     - agent-test001.p12 (CN=testserver01_appuser_J, password: agent-password)
echo     - agent-test002.p12 (CN=testserver02_svcuser_J, password: agent-password)
echo     - agent-test003.p12 (CN=testserver03_testuser_J, password: agent-password)
echo.
echo   Java Truststore:
echo     - truststore.jks (password: truststore-password)
echo.
echo Certificate Subject Format:
echo   CN={hostname}_{username}_J, OU=agent, O=MWM Project, C=KR
echo.
echo ==========================================

REM Verify certificates
echo.
echo Verifying certificates...
echo.
echo CA certificate:
openssl x509 -in ca.crt -noout -subject -issuer

echo.
echo Server certificate:
openssl x509 -in server.crt -noout -subject -issuer

echo.
echo Agent test001 certificate:
openssl x509 -in agent-test001.crt -noout -subject -issuer

echo.
echo Agent test002 certificate:
openssl x509 -in agent-test002.crt -noout -subject -issuer

echo.
echo Agent test003 certificate:
openssl x509 -in agent-test003.crt -noout -subject -issuer

cd ..
goto :end

:error
echo.
echo ERROR: Certificate generation failed!
echo Make sure OpenSSL and Java keytool are installed and in PATH.
cd ..
exit /b 1

:end
endlocal
