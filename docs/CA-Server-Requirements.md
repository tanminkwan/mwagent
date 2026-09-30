# CA Server 요구사항 정의서

> **목적**: MwManger Agent, Auth Server, Biz Service가 정상적으로 동작하기 위해 CA Server가 갖추어야 할 스펙 정의
>
> **대상 독자**: CA Server 개발팀

---

## 1. 개요

### 1.1 CA Server의 역할

CA Server는 PKI (Public Key Infrastructure) 기반의 인증서 발급 및 관리를 담당합니다.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            CA Server 역할                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  1. Agent 클라이언트 인증서 발급 (최초 등록 - 관리자 승인)                     │
│  2. Agent 클라이언트 인증서 갱신 (자동 승인)                                  │
│  3. Server 인증서 발급 (Auth Server, Biz Service용)                          │
│  4. 인증서 폐기 (CRL/OCSP)                                                   │
│  5. 인증서 상태 조회                                                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 시스템 구성

```
                                    ┌─────────────────┐
                                    │    CA Server    │
                                    │  (인증서 발급)   │
                                    └────────┬────────┘
                                             │
              ┌──────────────────────────────┼──────────────────────────────┐
              │                              │                              │
              ▼                              ▼                              ▼
    ┌─────────────────┐           ┌─────────────────┐           ┌─────────────────┐
    │   MwManger      │           │   Auth Server   │           │   Biz Service   │
    │   Agent         │           │  (토큰 발급)     │           │  (업무 서비스)   │
    │                 │           │                 │           │                 │
    │ • 클라이언트    │           │ • 서버 인증서   │           │ • 서버 인증서   │
    │   인증서 필요   │           │ • CA 인증서     │           │ • CA 인증서     │
    └─────────────────┘           └─────────────────┘           └─────────────────┘
```

### 1.3 핵심 보안 원칙

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PKI 보안 원칙                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  ✅ CA Server가 하는 것:                                                     │
│     - CSR(Certificate Signing Request) 수신                                 │
│     - CSR 검증 및 승인                                                       │
│     - 인증서 서명 (CA Private Key 사용)                                      │
│     - 서명된 인증서 반환                                                     │
│                                                                             │
│  ❌ CA Server가 하지 않는 것:                                                │
│     - Private Key 생성 (Agent/Server가 직접 생성)                            │
│     - Private Key 전송/수신 (절대 금지)                                      │
│     - Private Key 저장 (자신의 CA Key 제외)                                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 인증서 스펙

### 2.1 CA 인증서 (Root CA)

| 항목 | 값 |
|------|-----|
| **Subject DN** | `CN=MwAgent Root CA, OU=CA, O=MWM Project, C=KR` |
| **Key Algorithm** | RSA 4096bit 또는 ECDSA P-384 |
| **Signature Algorithm** | SHA256withRSA 또는 SHA256withECDSA |
| **Validity** | 10년 이상 |
| **Key Usage** | Certificate Sign, CRL Sign |
| **Basic Constraints** | CA:TRUE, pathLenConstraint:0 |

### 2.2 Agent 클라이언트 인증서

| 항목 | 값 |
|------|-----|
| **Subject DN 형식** | `CN={hostname}_{username}_J, OU=agent, O=MWM Project, C=KR` |
| **Key Algorithm** | RSA 2048bit 이상 |
| **Signature Algorithm** | SHA256withRSA |
| **Validity** | 7일 ~ 90일 (운영 정책에 따름) |
| **Key Usage** | Digital Signature, Key Encipherment |
| **Extended Key Usage** | Client Authentication (OID: 1.3.6.1.5.5.7.3.2) |
| **파일 형식** | PKCS#12 (.p12) - Agent가 생성한 Private Key + CA 서명 인증서 |

**Subject DN 상세:**

| 필드 | 형식 | 예시 | 용도 |
|------|------|------|------|
| CN | `{hostname}_{username}_J` | `prodserver01_appuser_J` | Agent 고유 식별자 |
| OU | 고정값 `agent` | `agent` | 사용자 유형 (mTLS 인증 시 검증) |
| O | 조직명 | `MWM Project` | 조직 식별 |
| C | 국가코드 | `KR` | 국가 |

### 2.3 Server 인증서 (Auth Server, Biz Service용)

| 항목 | 값 |
|------|-----|
| **Subject DN 형식** | `CN={hostname}, OU={service_type}, O=MWM Project, C=KR` |
| **SAN (Subject Alternative Name)** | DNS:{hostname}, DNS:{fqdn}, IP:{ip_address} |
| **Key Algorithm** | RSA 2048bit 이상 |
| **Signature Algorithm** | SHA256withRSA |
| **Validity** | 1년 |
| **Key Usage** | Digital Signature, Key Encipherment |
| **Extended Key Usage** | Server Authentication (OID: 1.3.6.1.5.5.7.3.1) |

**OU 값:**

| 서버 유형 | OU 값 |
|----------|-------|
| Auth Server | `auth` |
| Biz Service | `service` |

---

## 3. API 명세

### 3.1 인증서 발급 요청 (최초 등록)

Agent가 최초로 인증서를 요청할 때 사용합니다. **관리자 승인이 필요**합니다.

**Endpoint**: `POST /api/v1/cert/issue`

**인증 방식**: Bootstrap Token (1회용 등록 코드)

**Request Headers**:
```http
Content-Type: application/json
```

**Request Body**:
```json
{
    "csr": "-----BEGIN CERTIFICATE REQUEST-----\nMIIC...(base64 encoded)...\n-----END CERTIFICATE REQUEST-----",
    "bootstrap_token": "bt-abc123-xyz789-def456",
    "agent_info": {
        "hostname": "prodserver01",
        "username": "appuser",
        "os_type": "Linux",
        "os_version": "RHEL 8.5",
        "agent_version": "0000.0009.0010"
    }
}
```

**Request Fields**:

| 필드 | 필수 | 설명 |
|------|------|------|
| `csr` | Y | PEM 형식의 CSR (Agent가 생성) |
| `bootstrap_token` | Y | 관리자가 사전 발급한 1회용 토큰 |
| `agent_info.hostname` | Y | Agent가 실행되는 서버 hostname |
| `agent_info.username` | Y | Agent를 실행하는 OS 사용자 |
| `agent_info.os_type` | N | OS 종류 |
| `agent_info.os_version` | N | OS 버전 |
| `agent_info.agent_version` | N | Agent 버전 |

**Response (승인 대기 - HTTP 202)**:
```json
{
    "status": "pending_approval",
    "request_id": "req-20251205-001",
    "message": "Certificate request submitted. Waiting for administrator approval.",
    "submitted_at": "2025-12-05T10:30:00Z"
}
```

**Response (즉시 승인 - HTTP 200)**:
```json
{
    "status": "approved",
    "request_id": "req-20251205-001",
    "certificate": "-----BEGIN CERTIFICATE-----\nMIID...\n-----END CERTIFICATE-----",
    "ca_certificate": "-----BEGIN CERTIFICATE-----\nMIIE...\n-----END CERTIFICATE-----",
    "expires_at": "2026-03-05T10:30:00Z",
    "serial_number": "1A2B3C4D5E6F"
}
```

**Error Responses**:

| HTTP Status | Error Code | 설명 |
|-------------|------------|------|
| 400 | `invalid_csr` | CSR 형식 오류 또는 파싱 실패 |
| 401 | `invalid_token` | Bootstrap Token 무효 또는 만료 |
| 409 | `duplicate_request` | 동일 CN으로 이미 대기 중인 요청 존재 |
| 422 | `invalid_subject` | CSR Subject DN 형식 오류 |

### 3.2 인증서 발급 상태 조회

Agent가 승인 대기 상태를 polling할 때 사용합니다.

**Endpoint**: `GET /api/v1/cert/status/{request_id}`

**인증 방식**: 없음 (request_id가 secret 역할)

**Response (대기 중 - HTTP 200)**:
```json
{
    "status": "pending_approval",
    "request_id": "req-20251205-001",
    "submitted_at": "2025-12-05T10:30:00Z",
    "message": "Waiting for administrator approval."
}
```

**Response (승인됨 - HTTP 200)**:
```json
{
    "status": "approved",
    "request_id": "req-20251205-001",
    "certificate": "-----BEGIN CERTIFICATE-----\nMIID...\n-----END CERTIFICATE-----",
    "ca_certificate": "-----BEGIN CERTIFICATE-----\nMIIE...\n-----END CERTIFICATE-----",
    "expires_at": "2026-03-05T10:30:00Z",
    "serial_number": "1A2B3C4D5E6F",
    "approved_at": "2025-12-05T10:45:00Z",
    "approved_by": "admin@mwm.example.com"
}
```

**Response (거부됨 - HTTP 200)**:
```json
{
    "status": "rejected",
    "request_id": "req-20251205-001",
    "rejected_at": "2025-12-05T10:45:00Z",
    "rejected_by": "admin@mwm.example.com",
    "reason": "Unknown hostname. Please verify server registration."
}
```

**Error Responses**:

| HTTP Status | Error Code | 설명 |
|-------------|------------|------|
| 404 | `not_found` | 해당 request_id 없음 |

### 3.3 인증서 갱신

기존 유효한 인증서를 가진 Agent가 만료 전 갱신할 때 사용합니다. **mTLS 인증으로 자동 승인**됩니다.

**Endpoint**: `POST /api/v1/cert/renew`

**인증 방식**: mTLS (기존 유효한 클라이언트 인증서)

**Request Headers**:
```http
Content-Type: application/json
```

**Request Body**:
```json
{
    "csr": "-----BEGIN CERTIFICATE REQUEST-----\nMIIC...(base64 encoded)...\n-----END CERTIFICATE REQUEST-----"
}
```

**검증 로직**:
1. mTLS 클라이언트 인증서의 CN 추출
2. CSR의 Subject CN과 비교
3. 일치하면 자동 승인, 불일치하면 거부

**Response (성공 - HTTP 200)**:
```json
{
    "status": "approved",
    "certificate": "-----BEGIN CERTIFICATE-----\nMIID...\n-----END CERTIFICATE-----",
    "ca_certificate": "-----BEGIN CERTIFICATE-----\nMIIE...\n-----END CERTIFICATE-----",
    "expires_at": "2026-03-12T10:30:00Z",
    "serial_number": "2B3C4D5E6F7A",
    "previous_serial": "1A2B3C4D5E6F"
}
```

**Error Responses**:

| HTTP Status | Error Code | 설명 |
|-------------|------------|------|
| 400 | `invalid_csr` | CSR 형식 오류 |
| 401 | `certificate_required` | mTLS 인증서 없음 |
| 403 | `cn_mismatch` | 기존 인증서 CN과 CSR Subject CN 불일치 |
| 403 | `certificate_expired` | 기존 인증서 이미 만료됨 (최초 발급으로 진행 필요) |
| 403 | `certificate_revoked` | 기존 인증서 폐기됨 |

### 3.4 인증서 폐기

관리자가 특정 인증서를 폐기할 때 사용합니다.

**Endpoint**: `POST /api/v1/cert/revoke`

**인증 방식**: 관리자 인증 (Basic Auth 또는 OAuth2)

**Request Body**:
```json
{
    "serial_number": "1A2B3C4D5E6F",
    "reason": "key_compromise",
    "revoked_by": "admin@mwm.example.com"
}
```

**Revocation Reasons**:

| Reason Code | 설명 |
|-------------|------|
| `key_compromise` | Private Key 유출 의심 |
| `ca_compromise` | CA 손상 |
| `affiliation_changed` | 소속 변경 |
| `superseded` | 새 인증서로 대체 |
| `cessation_of_operation` | 운영 중단 |
| `certificate_hold` | 일시 정지 |
| `unspecified` | 기타 |

**Response (성공 - HTTP 200)**:
```json
{
    "status": "revoked",
    "serial_number": "1A2B3C4D5E6F",
    "revoked_at": "2025-12-05T11:00:00Z",
    "reason": "key_compromise"
}
```

### 3.5 인증서 유효성 검증 (OCSP)

실시간 인증서 유효성 검증을 위한 OCSP Responder입니다.

**Endpoint**: `POST /ocsp` 또는 `GET /ocsp/{base64_encoded_request}`

**표준**: RFC 6960 (OCSP)

**Response**: DER 인코딩된 OCSP Response

### 3.6 CRL (Certificate Revocation List) 배포

**Endpoint**: `GET /crl/ca.crl`

**Response**: DER 또는 PEM 인코딩된 CRL

**갱신 주기**: 1시간 ~ 24시간 (운영 정책에 따름)

### 3.7 CA 인증서 다운로드

**Endpoint**: `GET /ca/certificate`

**Response**:
```
-----BEGIN CERTIFICATE-----
MIIE...
-----END CERTIFICATE-----
```

---

## 4. 관리자 기능

### 4.1 대기 중인 요청 목록 조회

**Endpoint**: `GET /api/v1/admin/cert/pending`

**인증 방식**: 관리자 인증

**Response**:
```json
{
    "pending_requests": [
        {
            "request_id": "req-20251205-001",
            "subject_cn": "prodserver01_appuser_J",
            "hostname": "prodserver01",
            "username": "appuser",
            "request_ip": "10.0.1.50",
            "submitted_at": "2025-12-05T10:30:00Z",
            "agent_info": {
                "os_type": "Linux",
                "os_version": "RHEL 8.5",
                "agent_version": "0000.0009.0010"
            }
        },
        {
            "request_id": "req-20251205-002",
            "subject_cn": "prodserver02_svcuser_J",
            "hostname": "prodserver02",
            "username": "svcuser",
            "request_ip": "10.0.1.51",
            "submitted_at": "2025-12-05T10:31:00Z",
            "agent_info": {
                "os_type": "AIX",
                "os_version": "7.2",
                "agent_version": "0000.0009.0010"
            }
        }
    ],
    "total_count": 2
}
```

### 4.2 인증서 발급 승인

**Endpoint**: `POST /api/v1/admin/cert/approve/{request_id}`

**인증 방식**: 관리자 인증

**Request Body** (옵션):
```json
{
    "validity_days": 90,
    "allowed_ips": ["10.0.1.50", "10.0.1.51"],
    "comment": "Approved after verification with server team"
}
```

**Response**:
```json
{
    "status": "approved",
    "request_id": "req-20251205-001",
    "certificate": "-----BEGIN CERTIFICATE-----\nMIID...\n-----END CERTIFICATE-----",
    "serial_number": "1A2B3C4D5E6F",
    "expires_at": "2026-03-05T10:30:00Z",
    "approved_by": "admin@mwm.example.com",
    "approved_at": "2025-12-05T10:45:00Z"
}
```

### 4.3 인증서 발급 거부

**Endpoint**: `POST /api/v1/admin/cert/reject/{request_id}`

**인증 방식**: 관리자 인증

**Request Body**:
```json
{
    "reason": "Unknown hostname. Please verify server registration."
}
```

**Response**:
```json
{
    "status": "rejected",
    "request_id": "req-20251205-001",
    "rejected_by": "admin@mwm.example.com",
    "rejected_at": "2025-12-05T10:45:00Z",
    "reason": "Unknown hostname. Please verify server registration."
}
```

### 4.4 Bootstrap Token 발급

Agent 최초 등록 시 사용할 1회용 토큰을 발급합니다.

**Endpoint**: `POST /api/v1/admin/bootstrap-token`

**인증 방식**: 관리자 인증

**Request Body**:
```json
{
    "expected_cn": "prodserver01_appuser_J",
    "validity_hours": 24,
    "allowed_ips": ["10.0.1.50"],
    "comment": "For new production server deployment"
}
```

**Response**:
```json
{
    "bootstrap_token": "bt-abc123-xyz789-def456",
    "expected_cn": "prodserver01_appuser_J",
    "expires_at": "2025-12-06T10:30:00Z",
    "created_by": "admin@mwm.example.com",
    "created_at": "2025-12-05T10:30:00Z"
}
```

### 4.5 Bootstrap Token 파일 다운로드

관리자가 Agent 배포 시 사용할 Token 파일을 다운로드합니다.

**Endpoint**: `GET /api/v1/admin/bootstrap-token/{token}/download`

**인증 방식**: 관리자 인증

**Query Parameters**:

| 파라미터 | 설명 |
|---------|------|
| `format` | `text` (기본값) 또는 `json` |

**Response (format=text)**:
```
bt-abc123-xyz789-def456
```

**Response (format=json)**:
```json
{
    "token": "bt-abc123-xyz789-def456",
    "ca_server_url": "https://ca-server:8443",
    "expected_cn": "prodserver01_appuser_J"
}
```

**파일명**: `bootstrap.token`

**사용 방법**:
1. 관리자가 Token 파일 다운로드
2. Agent 서버의 `{cert_dir}/bootstrap.token`에 배치
3. Agent 시작 시 자동으로 Token 읽어서 CSR 요청
4. 인증서 발급 완료 후 Agent가 Token 파일 자동 삭제

### 4.6 발급된 인증서 목록 조회

**Endpoint**: `GET /api/v1/admin/cert/issued`

**Query Parameters**:

| 파라미터 | 설명 |
|---------|------|
| `status` | `active`, `expired`, `revoked` |
| `cn` | CN으로 필터링 |
| `page`, `size` | 페이징 |

**Response**:
```json
{
    "certificates": [
        {
            "serial_number": "1A2B3C4D5E6F",
            "subject_cn": "prodserver01_appuser_J",
            "status": "active",
            "issued_at": "2025-12-05T10:45:00Z",
            "expires_at": "2026-03-05T10:45:00Z",
            "issued_by": "admin@mwm.example.com"
        }
    ],
    "total_count": 1,
    "page": 1,
    "size": 20
}
```

---

## 5. 데이터 모델

### 5.1 인증서 요청 (CertificateRequest)

```sql
CREATE TABLE certificate_request (
    request_id          VARCHAR(50) PRIMARY KEY,
    csr                 TEXT NOT NULL,
    subject_cn          VARCHAR(200) NOT NULL,
    hostname            VARCHAR(100),
    username            VARCHAR(100),
    request_ip          VARCHAR(45),
    bootstrap_token     VARCHAR(100),
    status              VARCHAR(20) NOT NULL,  -- pending, approved, rejected
    submitted_at        TIMESTAMP NOT NULL,
    approved_at         TIMESTAMP,
    approved_by         VARCHAR(100),
    rejected_at         TIMESTAMP,
    rejected_by         VARCHAR(100),
    reject_reason       TEXT,
    agent_os_type       VARCHAR(50),
    agent_os_version    VARCHAR(50),
    agent_version       VARCHAR(20)
);
```

### 5.2 발급된 인증서 (IssuedCertificate)

```sql
CREATE TABLE issued_certificate (
    serial_number       VARCHAR(50) PRIMARY KEY,
    subject_dn          VARCHAR(500) NOT NULL,
    subject_cn          VARCHAR(200) NOT NULL,
    certificate_pem     TEXT NOT NULL,
    issued_at           TIMESTAMP NOT NULL,
    expires_at          TIMESTAMP NOT NULL,
    revoked_at          TIMESTAMP,
    revoke_reason       VARCHAR(50),
    request_id          VARCHAR(50) REFERENCES certificate_request(request_id),
    previous_serial     VARCHAR(50),  -- 갱신 시 이전 인증서
    issued_by           VARCHAR(100)
);
```

### 5.3 Bootstrap Token

```sql
CREATE TABLE bootstrap_token (
    token               VARCHAR(100) PRIMARY KEY,
    expected_cn         VARCHAR(200),
    allowed_ips         VARCHAR(500),  -- JSON array
    expires_at          TIMESTAMP NOT NULL,
    used_at             TIMESTAMP,
    created_by          VARCHAR(100),
    created_at          TIMESTAMP NOT NULL
);
```

---

## 6. 보안 요구사항

### 6.1 CA Private Key 보호

| 항목 | 요구사항 |
|------|----------|
| **저장** | HSM (Hardware Security Module) 권장, 최소 암호화된 파일 |
| **접근** | 최소 권한 원칙, 관리자만 접근 |
| **백업** | 오프라인 백업, 분리 보관 |
| **감사** | 모든 서명 작업 로깅 |

### 6.2 API 보안

| 항목 | 요구사항 |
|------|----------|
| **전송 암호화** | TLS 1.2 이상 필수 |
| **관리자 인증** | MFA 권장 |
| **Rate Limiting** | 인증서 발급 요청 제한 |
| **감사 로그** | 모든 API 호출 로깅 |

### 6.3 인증서 발급 정책

| 항목 | 요구사항 |
|------|----------|
| **CN 형식 검증** | `{hostname}_{username}_J` 형식 강제 |
| **OU 검증** | Agent는 `OU=agent` 필수 |
| **중복 방지** | 동일 CN 인증서 중복 발급 방지 |
| **IP 검증** | 요청 IP 기록 및 검증 (옵션) |

---

## 7. 연동 시나리오

### 7.1 Agent 최초 등록

```mermaid
sequenceDiagram
    autonumber
    participant Admin as 관리자
    participant CA as CA Server
    participant Agent as Agent 서버

    rect rgb(240, 248, 255)
        Note over Admin,CA: Bootstrap Token 발급
        Admin->>CA: POST /admin/bootstrap-token
        CA-->>Admin: Token: bt-abc123-xyz789
        Admin->>CA: GET /admin/bootstrap-token/{token}/download
        CA-->>Admin: bootstrap.token 파일
    end

    rect rgb(255, 248, 240)
        Note over Admin,Agent: Token 파일 배포
        Admin->>Agent: bootstrap.token 파일 배포<br/>(SCP, Ansible 등)
        Note right of Agent: ./certs/bootstrap.token
    end

    rect rgb(240, 255, 240)
        Note over Agent,CA: 인증서 발급 요청
        Agent->>Agent: Agent 시작<br/>bootstrap.token 발견
        Agent->>Agent: Keypair 생성<br/>CSR 생성
        Agent->>CA: POST /cert/issue<br/>(CSR + Bootstrap Token)
        CA-->>Agent: status: pending_approval<br/>request_id: req-001
    end

    rect rgb(255, 255, 240)
        Note over Admin,CA: 관리자 승인
        CA->>Admin: 승인 요청 알림<br/>(이메일/Slack)
        Admin->>Admin: CN, IP 검토
        Admin->>CA: POST /admin/cert/approve/req-001
        CA-->>Admin: 승인 완료
    end

    rect rgb(240, 255, 240)
        Note over Agent,CA: 인증서 수신 및 저장
        Agent->>CA: GET /cert/status/req-001 (polling)
        CA-->>Agent: status: approved<br/>certificate: (PEM)
        Agent->>Agent: agent.p12 저장
        Agent->>Agent: bootstrap.token 삭제
        Agent->>Agent: 정상 시작
    end
```

**인증서 디렉토리 구조 (Agent 서버)**:

```
./certs/
├── bootstrap.token     # (1) 최초: 관리자가 배포
│                       # (2) 인증서 발급 후 자동 삭제
├── agent.p12           # 인증서 발급 후 생성
└── truststore.jks      # CA 인증서 (관리자가 사전 배포)
```

### 7.2 Agent 인증서 갱신

```mermaid
sequenceDiagram
    autonumber
    participant Agent as Agent 서버
    participant CA as CA Server

    rect rgb(255, 248, 240)
        Note over Agent: 만료 임박 감지
        Agent->>Agent: 인증서 만료 임박 감지<br/>(예: 7일 이내)
    end

    rect rgb(240, 255, 240)
        Note over Agent: 새 인증서 준비
        Agent->>Agent: 새 Keypair 생성
        Agent->>Agent: 새 CSR 생성
    end

    rect rgb(240, 248, 255)
        Note over Agent,CA: 갱신 요청 (mTLS 인증)
        Agent->>CA: POST /cert/renew (mTLS)<br/>(기존 인증서로 인증)
        CA->>CA: mTLS 인증서 CN 확인
        CA->>CA: CSR Subject CN 비교
        CA->>CA: 일치 → 자동 승인
        CA-->>Agent: 새 인증서 발급
    end

    rect rgb(240, 255, 240)
        Note over Agent: 인증서 교체
        Agent->>Agent: Keystore 교체<br/>(새 Private Key + 새 인증서)
    end
```

**갱신 시 핵심 검증:**
- mTLS로 기존 인증서 유효성 확인
- CSR의 Subject CN이 기존 인증서 CN과 일치해야 함
- 관리자 승인 없이 자동 처리

### 7.3 Auth Server / Biz Service의 CA 인증서 사용

```mermaid
sequenceDiagram
    autonumber
    participant Auth as Auth Server
    participant CA as CA Server
    participant Agent as Agent

    rect rgb(240, 248, 255)
        Note over Auth,CA: CA 인증서 다운로드
        Auth->>CA: GET /ca/certificate
        CA-->>Auth: CA 인증서 (PEM)
        Auth->>Auth: Truststore에 CA 인증서 저장
    end

    rect rgb(240, 255, 240)
        Note over Agent,Auth: Agent mTLS 연결
        Agent->>Auth: mTLS 연결 요청<br/>(클라이언트 인증서 제시)
        Auth->>Auth: Agent 인증서 검증<br/>(CA 인증서로 서명 확인)
        Auth-->>Agent: 연결 승인
    end
```

---

## 8. 구현 체크리스트

### 8.1 필수 기능

- [ ] **인증서 발급 API** (`POST /api/v1/cert/issue`)
- [ ] **인증서 상태 조회 API** (`GET /api/v1/cert/status/{request_id}`)
- [ ] **인증서 갱신 API** (`POST /api/v1/cert/renew`)
- [ ] **인증서 폐기 API** (`POST /api/v1/cert/revoke`)
- [ ] **CA 인증서 다운로드** (`GET /ca/certificate`)
- [ ] **관리자 승인/거부 UI 및 API**
- [ ] **Bootstrap Token 발급 API**

### 8.2 권장 기능

- [ ] **OCSP Responder** (실시간 유효성 검증)
- [ ] **CRL 배포** (폐기 목록)
- [ ] **발급 이력 조회 API**
- [ ] **인증서 만료 알림** (이메일/Slack)
- [ ] **감사 로그**

### 8.3 보안 기능

- [ ] **CA Private Key HSM 저장**
- [ ] **관리자 MFA 인증**
- [ ] **API Rate Limiting**
- [ ] **TLS 1.2+ 강제**

---

## 9. 참고 자료

- RFC 5280: X.509 PKI Certificate and CRL Profile
- RFC 6960: OCSP (Online Certificate Status Protocol)
- RFC 2986: PKCS #10 - Certification Request Syntax
- RFC 7292: PKCS #12 - Personal Information Exchange Syntax

---

**문서 버전**: 1.0
**최종 수정일**: 2025-12-05
**작성자**: MwManger Development Team
