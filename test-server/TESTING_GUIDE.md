# mTLS Token Renewal Testing Guide

이 가이드는 mTLS 기반 access token 재발급 기능을 테스트하는 방법을 설명합니다.

## Test Environment Setup

### 1. Certificate 생성

```bash
cd test-server

# Windows
generate-certs.bat

# Linux/Mac
chmod +x generate-certs.sh
./generate-certs.sh
```

생성된 파일 확인:
```
test-server/certs/
├── ca.crt                      # CA certificate
├── server.crt, server.key      # Server certificate
├── agent-test001.p12           # Agent client certificate
├── agent-test002.p12           # Agent client certificate
└── truststore.jks              # Java truststore
```

### 2. Python Dependencies 설치

```bash
cd test-server
pip install -r requirements.txt
```

### 3. Mock Server 실행

```bash
# mTLS 지원 모드로 실행
python mock_server.py --ssl

# 출력 예시:
# ==========================================
# Mock Server for mTLS Token Renewal Testing
# ==========================================
#
# Supported authentication methods:
# 1. Refresh Token (existing)
# 2. mTLS (new)
#
# Registered agents:
#    - agent-test001: refresh_token=refresh-token-test001
#    - agent-test002: refresh_token=refresh-token-test002
# ==========================================
#
# * Running on https://0.0.0.0:8443
```

## Test Scenarios

### Scenario 1: mTLS Token Renewal (신규 방식)

**Agent 설정:**
```properties
# test-agent.properties
use_mtls=true
client.keystore.path=./test-server/certs/agent-test001.p12
client.keystore.password=agent-password
truststore.path=./test-server/certs/truststore.jks
truststore.password=truststore-password
server_url=https://localhost:8443
```

**테스트 방법:**

1. Agent 빌드:
```bash
./gradlew clean build
```

2. Agent 실행:
```bash
# agent.properties를 test-agent.properties로 복사 또는 수정
cp test-server/test-agent.properties agent.properties

# Agent 실행
java -jar build/libs/mwagent-*.jar
```

3. 로그 확인:
```
[INFO] mTLS enabled - initializing mTLS client
[INFO] Creating mTLS client with client certificate...
[INFO] mTLS client created successfully
...
[INFO] Access token expired, refreshing...
[INFO] Using mTLS method to renew token
[INFO] Renewing access token with mTLS: https://localhost:8443/api/v1/security/token/renew
[INFO] Access token renewed successfully with mTLS
```

4. Mock 서버 로그 확인:
```
[mTLS] Access token renewed for agent: agent-test001 (from certificate CN)
```

### Scenario 2: Refresh Token Method (기존 방식)

**Agent 설정:**
```properties
# test-agent.properties
use_mtls=false
token=refresh-token-test001
server_url=https://localhost:8443
```

**테스트 방법:**

1. Agent 설정 변경:
```properties
use_mtls=false
```

2. Agent 실행

3. 로그 확인:
```
[INFO] mTLS disabled - using refresh token method
[INFO] Refresh token applied
...
[INFO] Access token expired, refreshing...
[INFO] Using refresh token method
[INFO] Access token updated
```

4. Mock 서버 로그:
```
[REFRESH TOKEN] Access token renewed for agent: agent-test001
```

### Scenario 3: cURL로 직접 테스트

**mTLS 엔드포인트:**
```bash
curl -X POST https://localhost:8443/api/v1/security/token/renew \
  --cert ./test-server/certs/agent-test001.crt \
  --key ./test-server/certs/agent-test001.key \
  --cacert ./test-server/certs/ca.crt \
  -v

# 응답:
# {
#   "access_token": "eyJ0eXAiOiJKV1Qi...",
#   "expires_in": 1800,
#   "token_type": "Bearer",
#   "method": "mTLS"
# }
```

**Refresh Token 엔드포인트:**
```bash
# 1. Access token 받기
TOKEN=$(curl -X POST http://localhost:8080/api/v1/security/refresh \
  -H "Authorization: Bearer refresh-token-test001" \
  | jq -r '.access_token')

# 2. Access token 테스트
curl -X GET http://localhost:8080/api/v1/agent/test \
  -H "Authorization: Bearer $TOKEN"

# 응답:
# {
#   "message": "Access token is valid",
#   "agent_id": "agent-test001"
# }
```

## Verification Points

### mTLS 방식 검증

✅ **Agent 로그에서 확인:**
- [ ] "mTLS enabled - initializing mTLS client"
- [ ] "mTLS client created successfully"
- [ ] "Using mTLS method to renew token"
- [ ] "Access token renewed successfully with mTLS"

✅ **Mock 서버 로그에서 확인:**
- [ ] `[mTLS] Access token renewed for agent: agent-test001`
- [ ] CN에서 agent_id 추출 성공

✅ **네트워크에서 확인:**
```bash
# mTLS 연결 확인 (openssl s_client)
openssl s_client -connect localhost:8443 \
  -cert ./test-server/certs/agent-test001.crt \
  -key ./test-server/certs/agent-test001.key \
  -CAfile ./test-server/certs/ca.crt
```

### Refresh Token 방식 검증

✅ **Agent 로그에서 확인:**
- [ ] "mTLS disabled - using refresh token method"
- [ ] "Using refresh token method"
- [ ] "Access token updated"

✅ **Mock 서버 로그에서 확인:**
- [ ] `[REFRESH TOKEN] Access token renewed for agent: agent-test001`

## Troubleshooting

### Issue 1: Certificate not found
```
[ERROR] Failed to create mTLS client
java.io.FileNotFoundException: ./test-server/certs/agent-test001.p12
```

**해결:**
```bash
cd test-server
./generate-certs.sh  # 또는 generate-certs.bat
```

### Issue 2: SSL handshake failed
```
[WARNING] HTTP execution failed: https://localhost:8443/api/v1/security/token/renew
javax.net.ssl.SSLHandshakeException: ...
```

**원인:** Truststore에 서버 CA가 없거나 경로 오류

**해결:**
```properties
# agent.properties 확인
truststore.path=./test-server/certs/truststore.jks
truststore.password=truststore-password
```

### Issue 3: Token renewal failed with code -5
```
[WARNING] Token renewal failed with code: -5
```

**원인:** 서버가 client certificate를 검증하지 못함

**해결:**
1. Mock 서버 로그 확인
2. Certificate CN 확인:
```bash
openssl x509 -in ./test-server/certs/agent-test001.crt -noout -subject
# Subject: CN = agent-test001, OU = Agents, O = MwAgent, C = KR
```
3. Agent ID가 CN과 일치하는지 확인

### Issue 4: Mock server connection refused
```
curl: (7) Failed to connect to localhost port 8443
```

**해결:**
```bash
# Mock 서버 실행 확인
python mock_server.py --ssl

# 포트 사용 중인지 확인 (Windows)
netstat -ano | findstr :8443

# 포트 사용 중인지 확인 (Linux/Mac)
lsof -i :8443
```

## Performance Testing

### Token Renewal 성능 비교

**Refresh Token 방식:**
```bash
time curl -X POST http://localhost:8080/api/v1/security/refresh \
  -H "Authorization: Bearer refresh-token-test001"
```

**mTLS 방식:**
```bash
time curl -X POST https://localhost:8443/api/v1/security/token/renew \
  --cert ./test-server/certs/agent-test001.crt \
  --key ./test-server/certs/agent-test001.key \
  --cacert ./test-server/certs/ca.crt
```

### Load Testing (Optional)

```bash
# Apache Bench로 부하 테스트
ab -n 1000 -c 10 \
  -E ./test-server/certs/agent-test001.pem \
  https://localhost:8443/api/v1/security/token/renew
```

## Configuration Matrix

| 설정 | Refresh Token | mTLS | 권장 용도 |
|------|---------------|------|-----------|
| `use_mtls=false`<br>`token=refresh-token-xxx` | ✅ | ❌ | 기존 환경, 빠른 배포 |
| `use_mtls=true`<br>`client.keystore.path=...` | ❌ | ✅ | 보안 강화, Certificate 관리 가능 |

## Next Steps

1. ✅ Mock 서버 테스트 완료
2. ⬜ 실제 서버에 mTLS 엔드포인트 구현
3. ⬜ Certificate 발급 프로세스 구축
4. ⬜ Production 배포 및 모니터링

## References

- [Python Mock Server](./mock_server.py)
- [Certificate Generation](./generate-certs.sh)
- [Mock Server README](./README.md)
- [Agent Configuration](./test-agent.properties)
