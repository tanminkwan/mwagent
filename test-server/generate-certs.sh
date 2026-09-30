#!/bin/bash
# Certificate generation script for mTLS testing
# Creates CA, server certificate, and agent client certificates

set -e

CERTS_DIR="./certs"
DAYS_VALID=365

echo "=========================================="
echo "Generating certificates for mTLS testing"
echo "=========================================="

# Create certs directory
mkdir -p "$CERTS_DIR"
cd "$CERTS_DIR"

# ==================== 1. Create CA (Certificate Authority) ====================
echo ""
echo "[1/4] Creating CA (Certificate Authority)..."

openssl genrsa -out ca.key 4096

openssl req -x509 -new -nodes -key ca.key -sha256 -days $DAYS_VALID -out ca.crt \
    -subj "/CN=MWM Test CA/OU=CA/O=MWM Project/C=KR"

echo "   ✓ CA certificate created: ca.crt"

# ==================== 2. Create Server Certificate ====================
echo ""
echo "[2/4] Creating server certificate..."

openssl genrsa -out server.key 2048

openssl req -new -key server.key -out server.csr \
    -subj "/CN=localhost/OU=Server/O=MWM Project/C=KR"

# Create server extensions file for SAN (Subject Alternative Names)
cat > server-ext.cnf << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = 127.0.0.1
IP.1 = 127.0.0.1
EOF

openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out server.crt -days $DAYS_VALID -sha256 -extfile server-ext.cnf

echo "   ✓ Server certificate created: server.crt"

# ==================== 3. Create Agent Client Certificates ====================
echo ""
echo "[3/4] Creating agent client certificates..."

# Function to create agent certificate
# Subject format: CN={agent_id}, OU=agent, O=MWM Project, C=KR
#   - CN: Agent ID (hostname_username_J)
#   - OU: usertype (agent) - identifies this as an agent certificate
create_agent_cert() {
    local AGENT_ID=$1
    local HOSTNAME=$2
    local USERNAME=$3
    local PASSWORD=${4:-agent-password}

    # CN format: hostname_username_J (matches agent_id format)
    local CN="${HOSTNAME}_${USERNAME}_J"

    echo "   Creating certificate for ${AGENT_ID} (CN=${CN})..."

    openssl genrsa -out ${AGENT_ID}.key 2048

    # Subject: CN=hostname_username_J, OU=agent, O=MWM Project, C=KR
    openssl req -new -key ${AGENT_ID}.key -out ${AGENT_ID}.csr \
        -subj "/CN=${CN}/OU=agent/O=MWM Project/C=KR"

    openssl x509 -req -in ${AGENT_ID}.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
        -out ${AGENT_ID}.crt -days $DAYS_VALID -sha256

    # Convert to PKCS12 format for Java (use -legacy for Java 1.8 compatibility)
    openssl pkcs12 -export -in ${AGENT_ID}.crt -inkey ${AGENT_ID}.key \
        -out ${AGENT_ID}.p12 -name ${AGENT_ID} -password pass:${PASSWORD} -legacy

    echo "   ✓ ${AGENT_ID} certificate created: ${AGENT_ID}.p12"
    echo "     Subject: CN=${CN}, OU=agent, O=MWM Project, C=KR"
}

# Agent test001: hostname=testserver01, username=appuser
create_agent_cert "agent-test001" "testserver01" "appuser"

# Agent test002: hostname=testserver02, username=svcuser
create_agent_cert "agent-test002" "testserver02" "svcuser"

# Agent test003: for testing expired refresh token scenario
create_agent_cert "agent-test003" "testserver03" "testuser"

# ==================== 4. Create Java Truststore ====================
echo ""
echo "[4/4] Creating Java truststore..."

# Import CA certificate into truststore
keytool -import -trustcacerts -alias testca -file ca.crt \
    -keystore truststore.jks -storepass truststore-password -noprompt

echo "   ✓ Truststore created: truststore.jks"

# ==================== Cleanup ====================
echo ""
echo "Cleaning up temporary files..."
rm -f *.csr *.srl server-ext.cnf

# ==================== Summary ====================
echo ""
echo "=========================================="
echo "Certificate generation complete!"
echo "=========================================="
echo ""
echo "Generated files:"
echo "  CA:"
echo "    - ca.crt (CA certificate)"
echo "    - ca.key (CA private key)"
echo ""
echo "  Server:"
echo "    - server.crt (Server certificate)"
echo "    - server.key (Server private key)"
echo ""
echo "  Agent Clients:"
echo "    - agent-test001.p12 (password: agent-password)"
echo "    - agent-test002.p12 (password: agent-password)"
echo ""
echo "  Java Truststore:"
echo "    - truststore.jks (password: truststore-password)"
echo ""
echo "To start the mock server with mTLS:"
echo "  python3 mock_server.py --ssl"
echo ""
echo "=========================================="

# Verify certificates
echo ""
echo "Verifying certificates..."
echo ""
echo "Server certificate:"
openssl x509 -in server.crt -noout -subject -issuer

echo ""
echo "Agent test001 certificate:"
openssl x509 -in agent-test001.crt -noout -subject -issuer

echo ""
echo "Agent test002 certificate:"
openssl x509 -in agent-test002.crt -noout -subject -issuer

cd ..
