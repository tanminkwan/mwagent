#!/usr/bin/env bash
# 오프라인 빌드 스크립트
# Maven/Gradle 없이 javac만으로 빌드합니다.

set -e

PROJECT_NAME="mwagent"
MAIN_CLASS="mwagent.MwAgent"

echo "========================================="
echo "  MwManger Offline Build"
echo "========================================="
echo "Project: $PROJECT_NAME"
echo "Main Class: $MAIN_CLASS"
echo ""

# 1. Clean
echo "[1/5] Cleaning build directory..."
rm -rf build/classes
mkdir -p build/classes

# 2. 라이브러리 검증 + Classpath 설정
# lib/*.jar 는 git 에 없다. 온라인 PC 에서 download-dependencies.sh 로 받아 함께 반입한다.
echo "[2/5] Verifying libraries and setting up classpath..."
if ! ls lib/*.jar >/dev/null 2>&1; then
    echo "ERROR: lib/*.jar not found. Run ./download-dependencies.sh on an online machine"
    echo "       (or use the offline deployment package) and copy lib/ here."
    exit 1
fi
bash ./verify-lib.sh || exit 1

CLASSPATH=""
for jar in lib/*.jar; do
    if [ -f "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done
echo "Classpath: $CLASSPATH"

# 3. 컴파일
echo "[3/5] Compiling Java sources..."
find src/main/java -name "*.java" > sources.txt
javac -encoding UTF-8 \
      -source 1.8 \
      -target 1.8 \
      -d build/classes \
      -cp "$CLASSPATH" \
      @sources.txt

if [ $? -ne 0 ]; then
    echo "ERROR: Compilation failed!"
    exit 1
fi

rm sources.txt
echo "Compilation successful!"

# 4. Manifest 생성
echo "[4/5] Creating manifest..."
cat > build/MANIFEST.MF << EOF
Manifest-Version: 1.0
Main-Class: $MAIN_CLASS
Class-Path: $(cd lib && ls *.jar | tr '\n' ' ')
EOF

# 5. JAR 패키징
echo "[5/5] Creating JAR package..."
cd build/classes
jar cvfm "../$PROJECT_NAME.jar" ../MANIFEST.MF .
cd ../..
rm build/MANIFEST.MF

echo ""
echo "========================================="
echo "  Build Complete!"
echo "========================================="
echo ""
echo "Generated files:"
echo "  - build/$PROJECT_NAME.jar"
echo ""
echo "To run:"
echo "  java -jar build/$PROJECT_NAME.jar"
echo ""
echo "Note: lib/*.jar files must be in the same directory"
