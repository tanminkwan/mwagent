#!/usr/bin/env python3
"""
OAuth2-compliant Mock Authorization Server with mTLS and IP+Username Validation
Supports both refresh_token and client_credentials grant types

Certificate Subject Format:
  CN={hostname}_{username}_J, OU=agent, O=MWM Project, C=KR
  - CN: Agent ID (hostname_username_J)
  - OU: usertype (agent) - identifies this as an agent certificate
"""

from flask import Flask, request, jsonify
import jwt
import datetime
import ssl
import re
import os
from functools import wraps

app = Flask(__name__)

# Configuration
SECRET_KEY = "test-secret-key-for-mock-server"
ACCESS_TOKEN_EXPIRY_MINUTES = 30
REFRESH_TOKEN_EXPIRY_DAYS = 30
ISSUER = "mwm-auth-server"
AUDIENCE = "https://api.mwagent.example.com"

# Mock database for agents and tokens
# Key is agent_id format: {hostname}_{username}_J
AGENTS_DB = {
    "testserver01_appuser_J": {
        "agent_id": "testserver01_appuser_J",
        "hostname": "testserver01",
        "username": "appuser",
        "allowed_ips": ["127.0.0.1", "10.0.1.100", "192.168.1.100"],  # Allowed client IPs
        "refresh_token": "refresh-token-test001",
        "status": "active",
        "scope": "agent:commands agent:results",
        "usertype": "agent",
        "refresh_token_expired": False
    },
    "testserver02_svcuser_J": {
        "agent_id": "testserver02_svcuser_J",
        "hostname": "testserver02",
        "username": "svcuser",
        "allowed_ips": ["127.0.0.1", "10.0.2.100"],
        "refresh_token": "refresh-token-test002",
        "status": "active",
        "scope": "agent:commands agent:results",
        "usertype": "agent",
        "refresh_token_expired": False
    },
    "testserver03_testuser_J": {
        "agent_id": "testserver03_testuser_J",
        "hostname": "testserver03",
        "username": "testuser",
        "allowed_ips": ["127.0.0.1", "10.0.3.100"],
        "refresh_token": "refresh-token-test003",
        "status": "active",
        "scope": "agent:commands agent:results",
        "usertype": "agent",
        "refresh_token_expired": True  # For testing mTLS fallback
    }
}


def get_client_ip():
    """Get client IP address from request"""
    # Check for X-Forwarded-For header (when behind proxy)
    if request.headers.get('X-Forwarded-For'):
        return request.headers.get('X-Forwarded-For').split(',')[0].strip()
    # Check for X-Real-IP header
    if request.headers.get('X-Real-IP'):
        return request.headers.get('X-Real-IP')
    # Fall back to remote_addr
    return request.remote_addr


def generate_access_token(agent_id, client_ip, scope="agent:commands", method="refresh_token"):
    """Generate OAuth2-compliant JWT access token with extended claims"""

    agent = AGENTS_DB.get(agent_id)
    if not agent:
        raise ValueError(f"Agent {agent_id} not found")

    payload = {
        # OAuth2 standard claims
        "sub": agent_id,                    # Subject (agent_id = hostname_username_J)
        "iss": ISSUER,                      # Issuer
        "aud": AUDIENCE,                    # Audience
        "exp": datetime.datetime.utcnow() + datetime.timedelta(minutes=ACCESS_TOKEN_EXPIRY_MINUTES),
        "iat": datetime.datetime.utcnow(),  # Issued at
        "scope": scope,                     # OAuth2 scope

        # Custom claims for agent identity
        "usertype": agent.get("usertype", "agent"),  # User type from OU
        "hostname": agent.get("hostname", ""),        # Hostname from CN
        "username": agent.get("username", ""),        # Username from CN
        "client_ip": client_ip,                       # Client IP at token issuance

        # Token metadata
        "client_auth_method": method,       # Authentication method used
        "token_type": "access_token"
    }
    return jwt.encode(payload, SECRET_KEY, algorithm="HS256")


def generate_refresh_token(agent_id):
    """Generate a new refresh token"""
    payload = {
        "sub": agent_id,
        "iss": ISSUER,
        "exp": datetime.datetime.utcnow() + datetime.timedelta(days=REFRESH_TOKEN_EXPIRY_DAYS),
        "iat": datetime.datetime.utcnow(),
        "token_type": "refresh_token"
    }
    return jwt.encode(payload, SECRET_KEY, algorithm="HS256")


def verify_access_token(token):
    """Verify JWT access token"""
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=["HS256"], audience=AUDIENCE)
        return payload
    except jwt.ExpiredSignatureError:
        return None
    except jwt.InvalidTokenError:
        return None


def extract_bearer_token(auth_header):
    """Extract token from Bearer authorization header"""
    if auth_header and auth_header.startswith("Bearer "):
        return auth_header[7:]
    return None


def parse_certificate_dn(cert_dn):
    """
    Parse certificate DN and extract components

    Expected DN format: CN=hostname_username_J, OU=agent, O=MWM Project, C=KR
    Returns dict with: cn, ou, o, c, hostname, username, agent_id
    """
    result = {
        "cn": None,
        "ou": None,
        "o": None,
        "c": None,
        "hostname": None,
        "username": None,
        "agent_id": None,
        "usertype": None
    }

    if not cert_dn:
        return result

    # Extract DN components
    cn_match = re.search(r'CN=([^,]+)', cert_dn)
    ou_match = re.search(r'OU=([^,]+)', cert_dn)
    o_match = re.search(r'O=([^,]+)', cert_dn)
    c_match = re.search(r'C=([^,]+)', cert_dn)

    if cn_match:
        result["cn"] = cn_match.group(1).strip()
        result["agent_id"] = result["cn"]

        # Parse CN format: hostname_username_J
        cn_parts = result["cn"].rsplit('_', 2)  # Split from right to handle underscores in hostname/username
        if len(cn_parts) >= 2:
            result["hostname"] = '_'.join(cn_parts[:-2]) if len(cn_parts) > 2 else cn_parts[0]
            result["username"] = cn_parts[-2] if len(cn_parts) > 2 else cn_parts[0]
            # More robust parsing
            cn_match_detailed = re.match(r'^(.+)_(.+)_J$', result["cn"])
            if cn_match_detailed:
                result["hostname"] = cn_match_detailed.group(1)
                result["username"] = cn_match_detailed.group(2)

    if ou_match:
        result["ou"] = ou_match.group(1).strip()
        result["usertype"] = result["ou"]  # OU = usertype (agent, admin, etc.)

    if o_match:
        result["o"] = o_match.group(1).strip()

    if c_match:
        result["c"] = c_match.group(1).strip()

    return result


def validate_agent_identity(cert_info, client_ip):
    """
    Validate agent identity by checking:
    1. Certificate OU = agent (usertype validation)
    2. Agent is registered and active
    3. Client IP is in allowed list (copy protection)

    Returns (is_valid, error_message, agent_data)
    """
    agent_id = cert_info.get("agent_id")
    usertype = cert_info.get("usertype")

    # 1. Validate usertype from OU
    if usertype != "agent":
        return False, f"Invalid certificate usertype: expected 'agent', got '{usertype}'", None

    # 2. Check agent registration
    agent = AGENTS_DB.get(agent_id)
    if not agent:
        return False, f"Agent '{agent_id}' not registered", None

    if agent.get("status") != "active":
        return False, f"Agent '{agent_id}' is not active", None

    # 3. Validate client IP (certificate copy protection)
    allowed_ips = agent.get("allowed_ips", [])
    if client_ip not in allowed_ips:
        print(f"[SECURITY] IP validation failed for {agent_id}: {client_ip} not in {allowed_ips}")
        return False, f"Client IP '{client_ip}' not authorized for agent '{agent_id}'", None

    # 4. Verify certificate hostname/username matches registered data
    cert_hostname = cert_info.get("hostname")
    cert_username = cert_info.get("username")

    if cert_hostname != agent.get("hostname"):
        return False, f"Certificate hostname mismatch: expected '{agent.get('hostname')}', got '{cert_hostname}'", None

    if cert_username != agent.get("username"):
        return False, f"Certificate username mismatch: expected '{agent.get('username')}', got '{cert_username}'", None

    return True, None, agent


def require_auth(f):
    """Decorator to require valid access token"""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        auth_header = request.headers.get('Authorization')
        token = extract_bearer_token(auth_header)

        if not token:
            return jsonify({
                "error": "invalid_token",
                "error_description": "No token provided"
            }), 401

        payload = verify_access_token(token)
        if not payload:
            return jsonify({
                "error": "invalid_token",
                "error_description": "Token is invalid or expired"
            }), 401

        request.agent_id = payload['sub']
        request.token_scope = payload.get('scope', '')
        request.token_payload = payload
        return f(*args, **kwargs)

    return decorated_function


# ==================== OAuth2 Token Endpoint (RFC 6749) ====================

@app.route('/oauth2/token', methods=['POST'])
def oauth2_token():
    """
    OAuth2 Token Endpoint (RFC 6749 Section 3.2)

    Supported grant types:
    - refresh_token: Refresh access token using refresh token
    - client_credentials: Issue access token using mTLS client certificate
    """

    grant_type = request.form.get('grant_type')

    if not grant_type:
        return jsonify({
            "error": "invalid_request",
            "error_description": "grant_type parameter is required"
        }), 400

    if grant_type == "refresh_token":
        return handle_refresh_token_grant()

    elif grant_type == "client_credentials":
        return handle_client_credentials_grant()

    else:
        return jsonify({
            "error": "unsupported_grant_type",
            "error_description": f"Grant type '{grant_type}' is not supported"
        }), 400


def handle_refresh_token_grant():
    """Handle OAuth2 Refresh Token Grant (RFC 6749 Section 6)"""

    refresh_token = request.form.get('refresh_token')

    if not refresh_token:
        return jsonify({
            "error": "invalid_request",
            "error_description": "refresh_token parameter is required"
        }), 400

    # Find agent by refresh token
    agent = None
    for agent_data in AGENTS_DB.values():
        if agent_data['refresh_token'] == refresh_token:
            agent = agent_data
            break

    if not agent:
        return jsonify({
            "error": "invalid_grant",
            "error_description": "Invalid refresh token"
        }), 401

    # Check if refresh token is marked as expired (for testing cascading fallback)
    if agent.get('refresh_token_expired', False):
        print(f"[OAuth2] refresh_token grant: EXPIRED for {agent['agent_id']} - should fallback to mTLS")
        return jsonify({
            "error": "invalid_grant",
            "error_description": "Refresh token has expired"
        }), 401

    client_ip = get_client_ip()

    # Generate new access token
    access_token = generate_access_token(
        agent['agent_id'],
        client_ip=client_ip,
        scope=agent['scope'],
        method="refresh_token"
    )

    print(f"[OAuth2] refresh_token grant: Access token issued for {agent['agent_id']} from IP {client_ip}")

    return jsonify({
        "access_token": access_token,
        "token_type": "Bearer",
        "expires_in": ACCESS_TOKEN_EXPIRY_MINUTES * 60,
        "scope": agent['scope']
    }), 200


def handle_client_credentials_grant():
    """
    Handle OAuth2 Client Credentials Grant with mTLS (RFC 8705)

    Validation flow:
    1. Extract client certificate from mTLS connection
    2. Parse certificate DN (CN, OU)
    3. Validate OU=agent (usertype check)
    4. Validate agent registration and status
    5. Validate client IP (certificate copy protection)
    6. Issue access token with full identity claims
    """

    client_ip = get_client_ip()

    # Extract client certificate DN from mTLS connection
    # Flask with ssl context puts this in environ
    client_cert_dn = request.environ.get('SSL_CLIENT_S_DN')

    # For testing without actual mTLS, check header
    if not client_cert_dn:
        client_cert_dn = request.headers.get('X-SSL-Client-DN')

    if not client_cert_dn:
        return jsonify({
            "error": "invalid_client",
            "error_description": "Client certificate required for mTLS authentication"
        }), 401

    # Parse certificate DN
    cert_info = parse_certificate_dn(client_cert_dn)
    print(f"[OAuth2] mTLS: Certificate DN parsed: {cert_info}")

    if not cert_info.get("agent_id"):
        return jsonify({
            "error": "invalid_client",
            "error_description": "Cannot extract agent ID from certificate CN"
        }), 401

    # Validate agent identity (OU, registration, IP)
    is_valid, error_msg, agent = validate_agent_identity(cert_info, client_ip)

    if not is_valid:
        print(f"[OAuth2] mTLS: Validation failed - {error_msg}")
        return jsonify({
            "error": "invalid_client",
            "error_description": error_msg
        }), 401

    # Generate access token with full identity claims
    access_token = generate_access_token(
        agent['agent_id'],
        client_ip=client_ip,
        scope=agent['scope'],
        method="client_credentials_mtls"
    )

    print(f"[OAuth2] client_credentials grant (mTLS): Access token issued for {agent['agent_id']}")
    print(f"         Hostname: {cert_info['hostname']}, Username: {cert_info['username']}, IP: {client_ip}")

    return jsonify({
        "access_token": access_token,
        "token_type": "Bearer",
        "expires_in": ACCESS_TOKEN_EXPIRY_MINUTES * 60,
        "scope": agent['scope']
    }), 200


# ==================== Legacy Endpoints (Backward Compatibility) ====================

@app.route('/api/v1/security/refresh', methods=['POST'])
def legacy_refresh_token():
    """
    DEPRECATED: Legacy refresh token endpoint
    Use /oauth2/token with grant_type=refresh_token instead
    """
    auth_header = request.headers.get('Authorization')
    refresh_token = extract_bearer_token(auth_header)

    if not refresh_token:
        return jsonify({"error": "No refresh token provided"}), 401

    agent = None
    for agent_data in AGENTS_DB.values():
        if agent_data['refresh_token'] == refresh_token:
            agent = agent_data
            break

    if not agent:
        return jsonify({"error": "Invalid refresh token"}), 401

    client_ip = get_client_ip()
    access_token = generate_access_token(agent['agent_id'], client_ip, agent['scope'], "refresh_token_legacy")

    print(f"[LEGACY] refresh_token: Access token renewed for agent: {agent['agent_id']}")

    return jsonify({
        "access_token": access_token,
        "expires_in": ACCESS_TOKEN_EXPIRY_MINUTES * 60,
        "token_type": "Bearer"
    }), 200


@app.route('/api/v1/security/token/renew', methods=['POST'])
def legacy_renew_token_mtls():
    """
    DEPRECATED: Legacy mTLS token renewal endpoint
    Use /oauth2/token with grant_type=client_credentials instead
    """
    client_ip = get_client_ip()
    client_cert_dn = request.environ.get('SSL_CLIENT_S_DN')

    if not client_cert_dn:
        client_cert_dn = request.headers.get('X-SSL-Client-DN')

    if not client_cert_dn:
        return jsonify({
            "error": "Client certificate required",
            "message": "mTLS authentication failed - no client certificate"
        }), 401

    cert_info = parse_certificate_dn(client_cert_dn)

    is_valid, error_msg, agent = validate_agent_identity(cert_info, client_ip)

    if not is_valid:
        return jsonify({
            "error": "Validation failed",
            "message": error_msg
        }), 403

    access_token = generate_access_token(agent['agent_id'], client_ip, agent['scope'], "mtls_legacy")

    print(f"[LEGACY] mTLS: Access token renewed for agent: {agent['agent_id']}")

    return jsonify({
        "access_token": access_token,
        "expires_in": ACCESS_TOKEN_EXPIRY_MINUTES * 60,
        "token_type": "Bearer",
        "method": "mTLS"
    }), 200


# ==================== Resource Server Endpoints ====================

@app.route('/api/v1/agent/test', methods=['GET'])
@require_auth
def test_endpoint():
    """Test endpoint to verify access token"""
    return jsonify({
        "message": "Access token is valid",
        "agent_id": request.agent_id,
        "scope": request.token_scope,
        "token_claims": {
            "usertype": request.token_payload.get("usertype"),
            "hostname": request.token_payload.get("hostname"),
            "username": request.token_payload.get("username"),
            "client_ip": request.token_payload.get("client_ip"),
            "auth_method": request.token_payload.get("client_auth_method")
        }
    }), 200


@app.route('/api/v1/commands/<agent_id>', methods=['GET'])
@require_auth
def get_commands(agent_id):
    """Mock command polling endpoint"""

    if request.agent_id != agent_id:
        return jsonify({
            "error": "forbidden",
            "error_description": "Token subject does not match requested agent ID"
        }), 403

    if "agent:commands" not in request.token_scope:
        return jsonify({
            "error": "insufficient_scope",
            "error_description": "Token does not have 'agent:commands' scope"
        }), 403

    return jsonify({
        "return_code": 1,
        "data": [],
        "agent_id": agent_id
    }), 200


@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({"status": "ok"}), 200


# ==================== Test Control Endpoints ====================

@app.route('/test/expire-refresh-token/<agent_id>', methods=['POST'])
def expire_refresh_token(agent_id):
    """TEST: Mark an agent's refresh token as expired"""
    if agent_id not in AGENTS_DB:
        return jsonify({"error": f"Agent {agent_id} not found"}), 404

    AGENTS_DB[agent_id]['refresh_token_expired'] = True
    print(f"[TEST] Agent {agent_id} refresh_token marked as EXPIRED")

    return jsonify({
        "message": f"Refresh token for {agent_id} marked as expired",
        "agent_id": agent_id,
        "refresh_token_expired": True
    }), 200


@app.route('/test/restore-refresh-token/<agent_id>', methods=['POST'])
def restore_refresh_token(agent_id):
    """TEST: Restore an agent's refresh token to valid state"""
    if agent_id not in AGENTS_DB:
        return jsonify({"error": f"Agent {agent_id} not found"}), 404

    AGENTS_DB[agent_id]['refresh_token_expired'] = False
    print(f"[TEST] Agent {agent_id} refresh_token RESTORED to valid")

    return jsonify({
        "message": f"Refresh token for {agent_id} restored to valid",
        "agent_id": agent_id,
        "refresh_token_expired": False
    }), 200


@app.route('/test/add-allowed-ip/<agent_id>', methods=['POST'])
def add_allowed_ip(agent_id):
    """TEST: Add an IP to agent's allowed list"""
    if agent_id not in AGENTS_DB:
        return jsonify({"error": f"Agent {agent_id} not found"}), 404

    ip = request.form.get('ip') or request.json.get('ip') if request.is_json else None
    if not ip:
        ip = get_client_ip()  # Add current client IP

    if ip not in AGENTS_DB[agent_id]['allowed_ips']:
        AGENTS_DB[agent_id]['allowed_ips'].append(ip)

    print(f"[TEST] Added IP {ip} to allowed list for {agent_id}")

    return jsonify({
        "message": f"IP {ip} added to allowed list",
        "agent_id": agent_id,
        "allowed_ips": AGENTS_DB[agent_id]['allowed_ips']
    }), 200


@app.route('/test/agent-status', methods=['GET'])
def get_agent_status():
    """TEST: Get status of all agents"""
    status = {}
    for agent_id, agent in AGENTS_DB.items():
        status[agent_id] = {
            "status": agent['status'],
            "hostname": agent.get('hostname'),
            "username": agent.get('username'),
            "usertype": agent.get('usertype'),
            "allowed_ips": agent.get('allowed_ips', []),
            "refresh_token_expired": agent.get('refresh_token_expired', False)
        }

    return jsonify(status), 200


@app.route('/test/decode-token', methods=['POST'])
def decode_token():
    """TEST: Decode and display JWT token contents"""
    auth_header = request.headers.get('Authorization')
    token = extract_bearer_token(auth_header)

    if not token:
        token = request.form.get('token') or (request.json.get('token') if request.is_json else None)

    if not token:
        return jsonify({"error": "No token provided"}), 400

    try:
        # Decode without verification to see contents
        payload = jwt.decode(token, SECRET_KEY, algorithms=["HS256"], audience=AUDIENCE)
        return jsonify({
            "valid": True,
            "payload": payload
        }), 200
    except jwt.ExpiredSignatureError:
        payload = jwt.decode(token, SECRET_KEY, algorithms=["HS256"], audience=AUDIENCE, options={"verify_exp": False})
        return jsonify({
            "valid": False,
            "reason": "Token expired",
            "payload": payload
        }), 200
    except jwt.InvalidTokenError as e:
        return jsonify({
            "valid": False,
            "reason": str(e)
        }), 200


# ==================== Server Configuration ====================

def get_client_cert_dn_from_socket():
    """
    Extract client certificate DN from SSL socket.
    Werkzeug doesn't expose SSL_CLIENT_S_DN, so we need to get it from the socket.
    """
    try:
        # Get the socket from the request context
        from flask import request
        sock = request.environ.get('werkzeug.socket')
        if sock and hasattr(sock, 'getpeercert'):
            cert = sock.getpeercert()
            if cert:
                # Build DN from certificate subject
                subject = cert.get('subject', ())
                dn_parts = []
                for rdn in subject:
                    for key, value in rdn:
                        dn_parts.append(f"{key}={value}")
                return ", ".join(dn_parts)
    except Exception as e:
        print(f"[DEBUG] Error extracting cert: {e}")
    return None


class SSLClientCertMiddleware:
    """
    WSGI middleware to extract client certificate and add to environ.
    """
    def __init__(self, app):
        self.app = app

    def __call__(self, environ, start_response):
        # Try to get client cert from the SSL socket
        sock = environ.get('werkzeug.socket')
        if sock and hasattr(sock, 'getpeercert'):
            try:
                cert = sock.getpeercert()
                if cert:
                    # Build DN string from certificate subject
                    subject = cert.get('subject', ())
                    dn_parts = []
                    for rdn in subject:
                        for key, value in rdn:
                            # Map OpenSSL key names to standard names
                            key_map = {
                                'commonName': 'CN',
                                'organizationalUnitName': 'OU',
                                'organizationName': 'O',
                                'countryName': 'C',
                                'stateOrProvinceName': 'ST',
                                'localityName': 'L'
                            }
                            short_key = key_map.get(key, key)
                            dn_parts.append(f"{short_key}={value}")
                    environ['SSL_CLIENT_S_DN'] = ", ".join(dn_parts)
                    print(f"[MIDDLEWARE] Client cert DN: {environ['SSL_CLIENT_S_DN']}")
            except Exception as e:
                print(f"[MIDDLEWARE] Error extracting cert: {e}")

        return self.app(environ, start_response)


def create_ssl_context():
    """Create SSL context for mTLS"""
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)

    # Server certificate and key
    context.load_cert_chain(
        certfile='./certs/server.crt',
        keyfile='./certs/server.key'
    )

    # Trust store for client certificates
    context.load_verify_locations(cafile='./certs/ca.crt')

    # Require client certificate (mTLS) - OPTIONAL to support both methods
    context.verify_mode = ssl.CERT_OPTIONAL

    return context


# Custom Request Handler to extract client certificate
from werkzeug.serving import WSGIRequestHandler

class SSLClientCertRequestHandler(WSGIRequestHandler):
    """Custom request handler that extracts SSL client certificate"""

    def make_environ(self):
        environ = super().make_environ()

        # Try to get client certificate from SSL socket
        if hasattr(self.request, 'getpeercert'):
            try:
                cert = self.request.getpeercert()
                if cert:
                    # Build DN string from certificate subject
                    subject = cert.get('subject', ())
                    dn_parts = []
                    for rdn in subject:
                        for key, value in rdn:
                            key_map = {
                                'commonName': 'CN',
                                'organizationalUnitName': 'OU',
                                'organizationName': 'O',
                                'countryName': 'C',
                                'stateOrProvinceName': 'ST',
                                'localityName': 'L'
                            }
                            short_key = key_map.get(key, key)
                            dn_parts.append(f"{short_key}={value}")
                    environ['SSL_CLIENT_S_DN'] = ", ".join(dn_parts)
                    print(f"[SSL] Client cert extracted: {environ['SSL_CLIENT_S_DN']}")
            except Exception as e:
                print(f"[SSL] Error extracting cert: {e}")

        return environ


if __name__ == '__main__':
    import sys

    print("=" * 70)
    print("OAuth2 Mock Authorization Server with mTLS + IP Validation")
    print("=" * 70)
    print("\nCertificate Subject Format:")
    print("  CN={hostname}_{username}_J, OU=agent, O=MWM Project, C=KR")
    print("\nJWT Token Claims:")
    print("  - sub: agent_id (hostname_username_J)")
    print("  - usertype: agent (from OU)")
    print("  - hostname: from CN")
    print("  - username: from CN")
    print("  - client_ip: client IP at token issuance")
    print("\nValidation Flow:")
    print("  1. Certificate OU = agent (usertype check)")
    print("  2. Agent is registered and active")
    print("  3. Client IP in allowed list (copy protection)")
    print("\n" + "=" * 70)
    print("\nOAuth2 Token Endpoint (RFC 6749):")
    print("  POST /oauth2/token")
    print("    - grant_type=refresh_token")
    print("    - grant_type=client_credentials (mTLS)")
    print("\nTest Endpoints:")
    print("  GET  /test/agent-status")
    print("  POST /test/expire-refresh-token/<agent_id>")
    print("  POST /test/restore-refresh-token/<agent_id>")
    print("  POST /test/add-allowed-ip/<agent_id>")
    print("  POST /test/decode-token")
    print("\nRegistered Agents:")
    for agent_id, agent in AGENTS_DB.items():
        print(f"  - {agent_id}")
        print(f"    hostname: {agent['hostname']}, username: {agent['username']}")
        print(f"    allowed_ips: {agent['allowed_ips']}")
        print(f"    refresh_token: {agent['refresh_token']}")
    print("=" * 70)

    use_ssl = len(sys.argv) > 1 and sys.argv[1] == '--ssl'

    if use_ssl:
        try:
            ssl_context = create_ssl_context()
            print("\n[INFO] Starting server with mTLS on https://0.0.0.0:8443")
            print("[INFO] Client certificates will be verified\n")
            # Use custom request handler to extract client cert
            app.run(
                host='0.0.0.0',
                port=8443,
                ssl_context=ssl_context,
                debug=True,
                request_handler=SSLClientCertRequestHandler
            )
        except FileNotFoundError as e:
            print(f"\n[ERROR] SSL certificates not found: {e}")
            print("[INFO] Run 'generate-certs.bat' to create certificates")
            sys.exit(1)
    else:
        print("\n[INFO] Starting server WITHOUT SSL on http://0.0.0.0:8080")
        print("[INFO] For mTLS testing, use: python mock_server.py --ssl\n")
        app.run(host='0.0.0.0', port=8080, debug=True)
