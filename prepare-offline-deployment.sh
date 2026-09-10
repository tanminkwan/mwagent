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

# 3. 라이브러리 복사
echo "[3/5] Copying library files (12 JARs)..."
cp lib/*.jar "$DEPLOY_DIR/lib/"

# 4. 빌드 스크립트 복사
echo "[4/5] Copying build scripts..."
cp build-offline.sh "$DEPLOY_DIR/"
cp build-offline.bat "$DEPLOY_DIR/"
cp build.gradle "$DEPLOY_DIR/"
chmod +x "$DEPLOY_DIR/build-offline.sh"

# 5. 문서 복사
echo "[5/5] Copying documentation..."
cp README.md "$DEPLOY_DIR/"
cp lib/README.md "$DEPLOY_DIR/lib/"
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
