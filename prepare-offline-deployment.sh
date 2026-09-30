#!/usr/bin/env bash
# =========================================
#  오프라인 배포 패키지 준비 스크립트
# =========================================

set -e

DEPLOY_DIR="mwagent-offline-deployment"
TIMESTAMP=$(date +%Y%m%d)

echo "========================================="
echo "  Preparing Offline Deployment Package"
echo "========================================="
echo ""

# 1. 배포 디렉토리 생성
echo "[1/5] Creating deployment directory..."
rm -rf "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR/lib"
mkdir -p "$DEPLOY_DIR/src"

# 2. 소스 코드 복사
echo "[2/5] Copying source code..."
cp -r src/* "$DEPLOY_DIR/src/"

# 3. 라이브러리 검증 후 복사 (lib/*.jar 는 git 에 없다. 먼저 ./download-dependencies.sh 실행)
echo "[3/5] Verifying and copying library files..."
bash ./verify-lib.sh || { echo "ERROR: lib verification failed. Run ./download-dependencies.sh first."; exit 1; }
cp lib/*.jar "$DEPLOY_DIR/lib/"
cp lib/SHA256SUMS "$DEPLOY_DIR/lib/"

# 4. 빌드 스크립트 복사
echo "[4/5] Copying build scripts..."
cp build-offline.sh "$DEPLOY_DIR/"
cp build-offline.bat "$DEPLOY_DIR/"
cp verify-lib.sh verify-lib.ps1 "$DEPLOY_DIR/"
chmod +x "$DEPLOY_DIR/build-offline.sh"

# 5. 문서 복사
echo "[5/5] Copying documentation..."
cp README.md "$DEPLOY_DIR/"
cp lib/README.md "$DEPLOY_DIR/lib/"
# 라이선스: 배포물에 lib/*.jar 가 들어가므로 함께 넣는다 (Apache-2.0 §4, MIT 고지 의무)
cp LICENSE NOTICE THIRD_PARTY_LICENSES.md "$DEPLOY_DIR/"
cp -r licenses "$DEPLOY_DIR/"
mkdir -p "$DEPLOY_DIR/sbom" && cp sbom/mwagent.cdx.json "$DEPLOY_DIR/sbom/"
echo ""

# 검증
echo "========================================="
echo "  Verification"
echo "========================================="
echo "Source files: $(find "$DEPLOY_DIR/src" -name "*.java" | wc -l)"
echo "Library files: $(ls "$DEPLOY_DIR/lib"/*.jar | wc -l)"
echo ""

echo "========================================="
echo "  Package Ready"
echo "========================================="
echo ""
echo "Deployment package created: $DEPLOY_DIR/"
echo ""
echo "Next steps:"
echo "1. Copy '$DEPLOY_DIR' directory to USB/Network drive"
echo "2. Transfer to offline environment"
echo "3. Run './build-offline.sh' to build"
echo "4. Create 'agent.properties' configuration file"
echo "5. Run the agent"
echo ""
echo "========================================="
