"""
CA Server - Certificate Authority for MwManger Agent

Provides:
- Certificate issuance (with admin approval)
- Certificate renewal (automatic via mTLS)
- Bootstrap token management
- Certificate revocation
"""

import os
import uuid
import secrets
from datetime import datetime, timedelta, timezone
from functools import wraps

from flask import Flask, request, jsonify, Response
from flask_restx import Api, Resource, fields, Namespace
from cryptography import x509
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.backends import default_backend
from dateutil import parser as date_parser

app = Flask(__name__)
app.config['SECRET_KEY'] = os.environ.get('SECRET_KEY', 'dev-secret-key')

# Swagger UI configuration
api = Api(
    app,
    version='1.0',
    title='CA Server API',
    description='Certificate Authority Server for MwManger Agent',
    doc='/swagger'
)

# Namespaces
ns_cert = Namespace('cert', description='Certificate operations')
ns_admin = Namespace('admin', description='Admin operations')
ns_ca = Namespace('ca', description='CA operations')

api.add_namespace(ns_cert, path='/api/v1/cert')
api.add_namespace(ns_admin, path='/api/v1/admin')
api.add_namespace(ns_ca, path='/ca')

# ============================================================================
# In-memory storage (replace with database in production)
# ============================================================================

# CA key and certificate (generated on startup if not exists)
CA_KEY = None
CA_CERT = None
CA_CERT_PEM = None

# Storage
certificate_requests = {}  # request_id -> request data
issued_certificates = {}   # serial_number -> certificate data
bootstrap_tokens = {}      # token -> token data
revoked_certificates = set()  # set of serial numbers

# ============================================================================
# CA initialization
# ============================================================================

def init_ca():
    """Initialize CA key and certificate"""
    global CA_KEY, CA_CERT, CA_CERT_PEM

    ca_dir = os.path.join(os.path.dirname(__file__), 'ca-data')
    os.makedirs(ca_dir, exist_ok=True)

    ca_key_path = os.path.join(ca_dir, 'ca.key')
    ca_cert_path = os.path.join(ca_dir, 'ca.crt')

    if os.path.exists(ca_key_path) and os.path.exists(ca_cert_path):
        # Load existing CA
        with open(ca_key_path, 'rb') as f:
            CA_KEY = serialization.load_pem_private_key(f.read(), password=None)
        with open(ca_cert_path, 'rb') as f:
            CA_CERT_PEM = f.read()
            CA_CERT = x509.load_pem_x509_certificate(CA_CERT_PEM)
        print("Loaded existing CA certificate")
    else:
        # Generate new CA
        CA_KEY = rsa.generate_private_key(
            public_exponent=65537,
            key_size=4096,
            backend=default_backend()
        )

        subject = issuer = x509.Name([
            x509.NameAttribute(NameOID.COUNTRY_NAME, "KR"),
            x509.NameAttribute(NameOID.ORGANIZATION_NAME, "MWM Project"),
            x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, "CA"),
            x509.NameAttribute(NameOID.COMMON_NAME, "MwAgent Root CA"),
        ])

        CA_CERT = (
            x509.CertificateBuilder()
            .subject_name(subject)
            .issuer_name(issuer)
            .public_key(CA_KEY.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(datetime.now(timezone.utc))
            .not_valid_after(datetime.now(timezone.utc) + timedelta(days=3650))  # 10 years
            .add_extension(
                x509.BasicConstraints(ca=True, path_length=0),
                critical=True
            )
            .add_extension(
                x509.KeyUsage(
                    digital_signature=False,
                    content_commitment=False,
                    key_encipherment=False,
                    data_encipherment=False,
                    key_agreement=False,
                    key_cert_sign=True,
                    crl_sign=True,
                    encipher_only=False,
                    decipher_only=False
                ),
                critical=True
            )
            .sign(CA_KEY, hashes.SHA256(), default_backend())
        )

        CA_CERT_PEM = CA_CERT.public_bytes(serialization.Encoding.PEM)

        # Save CA
        with open(ca_key_path, 'wb') as f:
            f.write(CA_KEY.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.PKCS8,
                encryption_algorithm=serialization.NoEncryption()
            ))
        with open(ca_cert_path, 'wb') as f:
            f.write(CA_CERT_PEM)

        print("Generated new CA certificate")

# ============================================================================
# Helper functions
# ============================================================================

def parse_csr(csr_pem):
    """Parse CSR from PEM format"""
    try:
        csr = x509.load_pem_x509_csr(csr_pem.encode())
        return csr
    except Exception as e:
        return None

def get_cn_from_csr(csr):
    """Extract CN from CSR subject"""
    for attr in csr.subject:
        if attr.oid == NameOID.COMMON_NAME:
            return attr.value
    return None

def get_ou_from_csr(csr):
    """Extract OU from CSR subject"""
    for attr in csr.subject:
        if attr.oid == NameOID.ORGANIZATIONAL_UNIT_NAME:
            return attr.value
    return None

def sign_certificate(csr, validity_days=90):
    """Sign CSR with CA key"""
    cert = (
        x509.CertificateBuilder()
        .subject_name(csr.subject)
        .issuer_name(CA_CERT.subject)
        .public_key(csr.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(datetime.now(timezone.utc))
        .not_valid_after(datetime.now(timezone.utc) + timedelta(days=validity_days))
        .add_extension(
            x509.BasicConstraints(ca=False, path_length=None),
            critical=True
        )
        .add_extension(
            x509.KeyUsage(
                digital_signature=True,
                content_commitment=False,
                key_encipherment=True,
                data_encipherment=False,
                key_agreement=False,
                key_cert_sign=False,
                crl_sign=False,
                encipher_only=False,
                decipher_only=False
            ),
            critical=True
        )
        .add_extension(
            x509.ExtendedKeyUsage([ExtendedKeyUsageOID.CLIENT_AUTH]),
            critical=False
        )
        .sign(CA_KEY, hashes.SHA256(), default_backend())
    )
    return cert

def get_client_cert_cn():
    """Get CN from client certificate (mTLS)"""
    # In production, this would be extracted from the SSL context
    # For testing, we use a header
    client_cert_dn = request.headers.get('X-Client-Cert-DN', '')
    if client_cert_dn:
        import re
        match = re.search(r'CN=([^,]+)', client_cert_dn)
        if match:
            return match.group(1)
    return None

def generate_request_id():
    """Generate unique request ID"""
    date_str = datetime.utcnow().strftime('%Y%m%d')
    return f"req-{date_str}-{secrets.token_hex(4)}"

def generate_bootstrap_token():
    """Generate bootstrap token"""
    return f"bt-{secrets.token_hex(8)}-{secrets.token_hex(8)}"

# ============================================================================
# API Models
# ============================================================================

issue_request_model = ns_cert.model('IssueRequest', {
    'csr': fields.String(required=True, description='PEM-encoded CSR'),
    'bootstrap_token': fields.String(required=True, description='Bootstrap token'),
    'agent_info': fields.Raw(description='Agent information')
})

renew_request_model = ns_cert.model('RenewRequest', {
    'csr': fields.String(required=True, description='PEM-encoded CSR')
})

bootstrap_token_request = ns_admin.model('BootstrapTokenRequest', {
    'expected_cn': fields.String(required=True, description='Expected CN'),
    'validity_hours': fields.Integer(default=24, description='Token validity in hours'),
    'allowed_ips': fields.List(fields.String, description='Allowed IP addresses'),
    'comment': fields.String(description='Comment')
})

approve_request_model = ns_admin.model('ApproveRequest', {
    'validity_days': fields.Integer(default=90, description='Certificate validity in days'),
    'comment': fields.String(description='Approval comment')
})

reject_request_model = ns_admin.model('RejectRequest', {
    'reason': fields.String(required=True, description='Rejection reason')
})

# ============================================================================
# Certificate API
# ============================================================================

@ns_cert.route('/issue')
class CertificateIssue(Resource):
    @ns_cert.expect(issue_request_model)
    @ns_cert.doc(description='Request new certificate (requires bootstrap token)')
    def post(self):
        """Request certificate issuance"""
        data = request.json

        # Validate CSR
        csr_pem = data.get('csr', '')
        csr = parse_csr(csr_pem)
        if not csr:
            return {'error': 'invalid_csr', 'message': 'Invalid CSR format'}, 400

        # Validate bootstrap token
        token = data.get('bootstrap_token', '')
        if token not in bootstrap_tokens:
            return {'error': 'invalid_token', 'message': 'Invalid bootstrap token'}, 401

        token_data = bootstrap_tokens[token]

        # Check token expiry
        if datetime.utcnow() > token_data['expires_at']:
            del bootstrap_tokens[token]
            return {'error': 'token_expired', 'message': 'Bootstrap token expired'}, 401

        # Check if token already used
        if token_data.get('used_at'):
            return {'error': 'token_used', 'message': 'Bootstrap token already used'}, 401

        # Extract CN from CSR
        cn = get_cn_from_csr(csr)
        ou = get_ou_from_csr(csr)

        if not cn:
            return {'error': 'invalid_subject', 'message': 'CSR must have CN'}, 422

        # Validate CN format: {hostname}_{username}_J
        if not cn.endswith('_J') or cn.count('_') < 2:
            return {'error': 'invalid_cn_format', 'message': 'CN must be in format: {hostname}_{username}_J'}, 422

        # Check expected CN if specified
        if token_data.get('expected_cn') and token_data['expected_cn'] != cn:
            return {'error': 'cn_mismatch', 'message': f"CN mismatch. Expected: {token_data['expected_cn']}"}, 422

        # Mark token as used
        bootstrap_tokens[token]['used_at'] = datetime.utcnow()

        # Create certificate request
        request_id = generate_request_id()
        agent_info = data.get('agent_info', {})

        # Parse CN to extract hostname and username
        parts = cn[:-2].rsplit('_', 1)  # Remove _J and split
        hostname = parts[0] if len(parts) > 1 else cn
        username = parts[1] if len(parts) > 1 else ''

        certificate_requests[request_id] = {
            'request_id': request_id,
            'csr_pem': csr_pem,
            'subject_cn': cn,
            'subject_ou': ou,
            'hostname': hostname,
            'username': username,
            'request_ip': request.remote_addr,
            'bootstrap_token': token,
            'status': 'pending_approval',
            'submitted_at': datetime.utcnow(),
            'agent_info': agent_info
        }

        return {
            'status': 'pending_approval',
            'request_id': request_id,
            'message': 'Certificate request submitted. Waiting for administrator approval.',
            'submitted_at': datetime.utcnow().isoformat() + 'Z'
        }, 202

@ns_cert.route('/status/<string:request_id>')
class CertificateStatus(Resource):
    @ns_cert.doc(description='Check certificate request status')
    def get(self, request_id):
        """Get certificate request status"""
        if request_id not in certificate_requests:
            return {'error': 'not_found', 'message': 'Request not found'}, 404

        req = certificate_requests[request_id]

        response = {
            'status': req['status'],
            'request_id': request_id,
            'submitted_at': req['submitted_at'].isoformat() + 'Z'
        }

        if req['status'] == 'approved':
            response['certificate'] = req.get('certificate_pem', '')
            response['ca_certificate'] = CA_CERT_PEM.decode()
            response['expires_at'] = req.get('expires_at', '').isoformat() + 'Z' if req.get('expires_at') else ''
            response['serial_number'] = req.get('serial_number', '')
            response['approved_at'] = req.get('approved_at', '').isoformat() + 'Z' if req.get('approved_at') else ''
            response['approved_by'] = req.get('approved_by', '')
        elif req['status'] == 'rejected':
            response['rejected_at'] = req.get('rejected_at', '').isoformat() + 'Z' if req.get('rejected_at') else ''
            response['rejected_by'] = req.get('rejected_by', '')
            response['reason'] = req.get('reject_reason', '')

        return response

@ns_cert.route('/renew')
class CertificateRenew(Resource):
    @ns_cert.expect(renew_request_model)
    @ns_cert.doc(description='Renew certificate (requires mTLS authentication)')
    def post(self):
        """Renew certificate via mTLS"""
        data = request.json

        # Get client certificate CN (from mTLS)
        client_cn = get_client_cert_cn()
        if not client_cn:
            return {'error': 'certificate_required', 'message': 'mTLS client certificate required'}, 401

        # Validate CSR
        csr_pem = data.get('csr', '')
        csr = parse_csr(csr_pem)
        if not csr:
            return {'error': 'invalid_csr', 'message': 'Invalid CSR format'}, 400

        # Extract CN from CSR
        csr_cn = get_cn_from_csr(csr)

        # Verify CN matches
        if client_cn != csr_cn:
            return {
                'error': 'cn_mismatch',
                'message': f'CSR CN ({csr_cn}) does not match client certificate CN ({client_cn})'
            }, 403

        # Sign certificate
        cert = sign_certificate(csr, validity_days=90)
        cert_pem = cert.public_bytes(serialization.Encoding.PEM).decode()
        serial = format(cert.serial_number, 'X')

        # Store issued certificate
        issued_certificates[serial] = {
            'serial_number': serial,
            'subject_cn': csr_cn,
            'certificate_pem': cert_pem,
            'issued_at': datetime.utcnow(),
            'expires_at': cert.not_valid_after_utc if hasattr(cert, 'not_valid_after_utc') else cert.not_valid_after,
            'issued_by': 'auto-renewal'
        }

        return {
            'status': 'approved',
            'certificate': cert_pem,
            'ca_certificate': CA_CERT_PEM.decode(),
            'expires_at': (datetime.utcnow() + timedelta(days=90)).isoformat() + 'Z',
            'serial_number': serial
        }

# ============================================================================
# Admin API
# ============================================================================

@ns_admin.route('/cert/pending')
class PendingRequests(Resource):
    @ns_admin.doc(description='List pending certificate requests')
    def get(self):
        """List pending certificate requests"""
        pending = [
            {
                'request_id': req['request_id'],
                'subject_cn': req['subject_cn'],
                'hostname': req['hostname'],
                'username': req['username'],
                'request_ip': req['request_ip'],
                'submitted_at': req['submitted_at'].isoformat() + 'Z',
                'agent_info': req.get('agent_info', {})
            }
            for req in certificate_requests.values()
            if req['status'] == 'pending_approval'
        ]
        return {'pending_requests': pending, 'total_count': len(pending)}

@ns_admin.route('/cert/approve/<string:request_id>')
class ApproveCertificate(Resource):
    @ns_admin.expect(approve_request_model)
    @ns_admin.doc(description='Approve certificate request')
    def post(self, request_id):
        """Approve certificate request"""
        if request_id not in certificate_requests:
            return {'error': 'not_found', 'message': 'Request not found'}, 404

        req = certificate_requests[request_id]

        if req['status'] != 'pending_approval':
            return {'error': 'invalid_status', 'message': f"Request is already {req['status']}"}, 400

        data = request.json or {}
        validity_days = data.get('validity_days', 90)

        # Parse and sign CSR
        csr = parse_csr(req['csr_pem'])
        cert = sign_certificate(csr, validity_days=validity_days)
        cert_pem = cert.public_bytes(serialization.Encoding.PEM).decode()
        serial = format(cert.serial_number, 'X')
        expires_at = cert.not_valid_after_utc if hasattr(cert, 'not_valid_after_utc') else cert.not_valid_after

        # Update request
        req['status'] = 'approved'
        req['certificate_pem'] = cert_pem
        req['serial_number'] = serial
        req['expires_at'] = expires_at
        req['approved_at'] = datetime.utcnow()
        req['approved_by'] = 'admin'  # In production, get from auth
        req['approval_comment'] = data.get('comment', '')

        # Store issued certificate
        issued_certificates[serial] = {
            'serial_number': serial,
            'subject_cn': req['subject_cn'],
            'subject_dn': str(csr.subject),
            'certificate_pem': cert_pem,
            'issued_at': datetime.utcnow(),
            'expires_at': expires_at,
            'request_id': request_id,
            'issued_by': 'admin'
        }

        return {
            'status': 'approved',
            'request_id': request_id,
            'certificate': cert_pem,
            'serial_number': serial,
            'expires_at': expires_at.isoformat() + 'Z',
            'approved_by': 'admin',
            'approved_at': datetime.utcnow().isoformat() + 'Z'
        }

@ns_admin.route('/cert/reject/<string:request_id>')
class RejectCertificate(Resource):
    @ns_admin.expect(reject_request_model)
    @ns_admin.doc(description='Reject certificate request')
    def post(self, request_id):
        """Reject certificate request"""
        if request_id not in certificate_requests:
            return {'error': 'not_found', 'message': 'Request not found'}, 404

        req = certificate_requests[request_id]

        if req['status'] != 'pending_approval':
            return {'error': 'invalid_status', 'message': f"Request is already {req['status']}"}, 400

        data = request.json or {}
        reason = data.get('reason', 'No reason provided')

        req['status'] = 'rejected'
        req['rejected_at'] = datetime.utcnow()
        req['rejected_by'] = 'admin'
        req['reject_reason'] = reason

        return {
            'status': 'rejected',
            'request_id': request_id,
            'rejected_by': 'admin',
            'rejected_at': datetime.utcnow().isoformat() + 'Z',
            'reason': reason
        }

@ns_admin.route('/bootstrap-token')
class BootstrapTokenCreate(Resource):
    @ns_admin.expect(bootstrap_token_request)
    @ns_admin.doc(description='Create bootstrap token')
    def post(self):
        """Create bootstrap token"""
        data = request.json

        token = generate_bootstrap_token()
        validity_hours = data.get('validity_hours', 24)

        bootstrap_tokens[token] = {
            'token': token,
            'expected_cn': data.get('expected_cn'),
            'allowed_ips': data.get('allowed_ips', []),
            'expires_at': datetime.utcnow() + timedelta(hours=validity_hours),
            'created_at': datetime.utcnow(),
            'created_by': 'admin',
            'comment': data.get('comment', ''),
            'used_at': None
        }

        return {
            'bootstrap_token': token,
            'expected_cn': data.get('expected_cn'),
            'expires_at': (datetime.utcnow() + timedelta(hours=validity_hours)).isoformat() + 'Z',
            'created_by': 'admin',
            'created_at': datetime.utcnow().isoformat() + 'Z'
        }

    @ns_admin.doc(description='List bootstrap tokens')
    def get(self):
        """List bootstrap tokens"""
        tokens = [
            {
                'token': t['token'],
                'expected_cn': t['expected_cn'],
                'expires_at': t['expires_at'].isoformat() + 'Z',
                'created_at': t['created_at'].isoformat() + 'Z',
                'used_at': t['used_at'].isoformat() + 'Z' if t['used_at'] else None,
                'is_valid': datetime.utcnow() < t['expires_at'] and not t['used_at']
            }
            for t in bootstrap_tokens.values()
        ]
        return {'tokens': tokens, 'total_count': len(tokens)}

@ns_admin.route('/bootstrap-token/<string:token>/download')
class BootstrapTokenDownload(Resource):
    @ns_admin.doc(description='Download bootstrap token as file. '
                              'NOTE: Swagger UI may show incorrect filename. '
                              'For correct filename (bootstrap.token), access URL directly in browser or use curl.')
    def get(self, token):
        """Download bootstrap token file

        Note: Swagger UI may download with incorrect filename.
        For correct filename, access the URL directly in browser or use:
        curl -o bootstrap.token "http://localhost:5000/api/v1/admin/bootstrap-token/{token}/download"
        """
        if token not in bootstrap_tokens:
            return {'error': 'not_found', 'message': 'Token not found'}, 404

        token_data = bootstrap_tokens[token]
        format_type = request.args.get('format', 'text')

        if format_type == 'json':
            import json
            content = json.dumps({
                'token': token,
                'ca_server_url': request.host_url.rstrip('/'),
                'expected_cn': token_data['expected_cn']
            }, indent=2)
            mimetype = 'application/json'
        else:
            content = token
            mimetype = 'text/plain'

        response = Response(content, mimetype=mimetype)
        response.headers['Content-Disposition'] = 'attachment; filename="bootstrap.token"'
        return response

@ns_admin.route('/cert/issued')
class IssuedCertificates(Resource):
    @ns_admin.doc(description='List issued certificates')
    def get(self):
        """List issued certificates"""
        status_filter = request.args.get('status', 'all')

        certs = []
        for cert in issued_certificates.values():
            cert_status = 'active'
            if cert['serial_number'] in revoked_certificates:
                cert_status = 'revoked'
            elif cert['expires_at'] < datetime.utcnow():
                cert_status = 'expired'

            if status_filter != 'all' and cert_status != status_filter:
                continue

            certs.append({
                'serial_number': cert['serial_number'],
                'subject_cn': cert['subject_cn'],
                'status': cert_status,
                'issued_at': cert['issued_at'].isoformat() + 'Z',
                'expires_at': cert['expires_at'].isoformat() + 'Z',
                'issued_by': cert.get('issued_by', '')
            })

        return {'certificates': certs, 'total_count': len(certs)}

# ============================================================================
# CA API
# ============================================================================

@ns_ca.route('/certificate')
class CACertificate(Resource):
    @ns_ca.doc(description='Download CA certificate')
    def get(self):
        """Get CA certificate"""
        return CA_CERT_PEM.decode(), 200, {'Content-Type': 'application/x-pem-file'}

# ============================================================================
# Main
# ============================================================================

if __name__ == '__main__':
    init_ca()
    print("\n" + "=" * 50)
    print("CA Server started")
    print("=" * 50)
    print(f"Swagger UI: http://localhost:5000/swagger")
    print("=" * 50 + "\n")
    app.run(host='0.0.0.0', port=5000, debug=True)
