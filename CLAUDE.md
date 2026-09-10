# MwManger Agent - Project Memory

## Critical Rules (MUST FOLLOW)

1. **DO NOT modify source code carelessly** - Always analyze thoroughly before making changes
2. **Two authentication modes must BOTH work**:
   - mTLS mode (`use_mtls=true`): Uses `/oauth2/token` endpoint with client certificate
   - Legacy mode (`use_mtls=false`): Uses `/api/v1/security/refresh` endpoint with refresh token
3. **Version managed in ONE place only**: `Version.java` (`VERSION` constant)
4. **Gradle doesn't work** in this environment (proxy/SSL issues) - Use Maven instead
5. **All logs go to file, not System.err** - This is a daemon process
6. **Git Push Protocol**: Always use **HTTPS** (`https://github.com/...`) for Git remote operations. SSH is not configured on this environment.

## Build System

### Maven (Primary - Use This)
```bash
# With proxy
HTTP_PROXY=http://70.10.15.10:8080 HTTPS_PROXY=http://70.10.15.10:8080 ./tools/apache-maven-3.9.6/bin/mvn clean test
```

### Offline Build (For deployment)
```bash
# From Git Bash on Windows - Output: build/mwagent.jar
/c/Windows/System32/cmd.exe //c "cd /d C:\GitHub\mwmanger && C:\GitHub\mwmanger\build-offline.bat"
```

### DO NOT DELETE
- `pom.xml` - Maven build file (required because Gradle doesn't work)
- `tools/apache-maven-3.9.6/` - Maven installation

## Test Execution

### Run all tests (no skips)
```bash
MTLS_INTEGRATION_TEST=true BIZ_SERVICE_INTEGRATION_TEST=true SSL_CERT_INTEGRATION_TEST=true \
HTTP_PROXY=http://70.10.15.10:8080 HTTPS_PROXY=http://70.10.15.10:8080 \
./tools/apache-maven-3.9.6/bin/mvn test
```

### Required test servers (run before integration tests)
1. `cd biz-service && python app.py` - Auth server on http://localhost:8080
2. `cd test-server && python mock_server.py --ssl` - mTLS server on https://localhost:8443

## Architecture

### Token Refresh Logic (Common.java)
- `updateToken()`: Main entry point
  - If `use_mtls=true`: calls `httpPOSTFormUrlEncoded("/oauth2/token", ...)` with `application/x-www-form-urlencoded`
  - If `use_mtls=false`: calls `httpPOST("/api/v1/security/refresh", ...)` with `application/json`

### HTTP Methods (Common.java)
- `httpPOST()`: Content-Type: application/json (for general API calls)
- `httpPOSTFormUrlEncoded()`: Content-Type: application/x-www-form-urlencoded (for OAuth2 token endpoint)

### Log Directory Handling (Config.java)
- If `log_dir` doesn't exist: Log error to current directory and terminate (return -3)
- Default: Current working directory

## Key Files

| File | Purpose |
|------|---------|
| `src/main/java/mwagent/common/Version.java` | **Single source of truth for version** |
| `src/main/java/mwagent/common/Config.java` | Configuration management |
| `src/main/java/mwagent/common/Common.java` | HTTP communication, token refresh |
| `agent.properties` | Runtime configuration |
| `test-server/test-agent.properties` | Test configuration |

## Recent Fixes (2025-12)
1. OAuth2 token request now uses correct Content-Type (`application/x-www-form-urlencoded`)
2. Log directory existence check added
3. Version output at startup
4. Version management unified to Version.java (single source of truth)
5. JAR filename simplified to `mwagent.jar` (no version suffix)
6. All 215 tests passing
