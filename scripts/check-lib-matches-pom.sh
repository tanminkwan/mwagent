#!/usr/bin/env bash
# pom.xml 의 런타임 의존성과 lib/SHA256SUMS 의 jar 목록이 같은지 확인한다 (CI lib-integrity).
# 오프라인 빌드는 lib/ 를, Maven 빌드는 pom.xml 을 쓰므로 둘이 어긋나면 산출물이 달라진다.
#
#   0: 일치   1: 불일치 또는 실행 실패
#
# 사용: scripts/check-lib-matches-pom.sh [mvn 경로]   (기본: PATH 의 mvn)

set -euo pipefail

MVN="${1:-mvn}"
OUT="$(mktemp)"
trap 'rm -f "$OUT" "$OUT.pom" "$OUT.lib"' EXIT

"$MVN" -B -q dependency:list -DincludeScope=runtime -DexcludeTransitive=false \
    -DoutputFile="$OUT" -DappendOutput=false >/dev/null

# "   group:artifact:jar:version:scope" -> "artifact-version.jar"
grep -E '^\s+[^: ]+:[^: ]+:jar:[^: ]+:(compile|runtime)' "$OUT" \
    | awk -F: '{gsub(/^[ \t]+/, "", $1); print $2 "-" $4 ".jar"}' | sort -u > "$OUT.pom"

awk '{ f = $2; sub(/^\*/, "", f); sub(/\r$/, "", f); if (f != "") print f }' lib/SHA256SUMS | sort -u > "$OUT.lib"

if diff -u "$OUT.pom" "$OUT.lib" > /dev/null; then
    echo "pom.xml runtime dependencies match lib/SHA256SUMS ($(wc -l < "$OUT.lib") jars)"
    exit 0
fi

echo "ERROR: pom.xml runtime dependencies and lib/SHA256SUMS differ (- pom.xml, + lib/SHA256SUMS):"
diff -u "$OUT.pom" "$OUT.lib" | tail -n +3 | grep -E '^[-+]' || true
echo "Update pom.xml, download-dependencies.* and lib/SHA256SUMS together (CLAUDE.md)."
exit 1
