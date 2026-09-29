#!/usr/bin/env bash
# lib/*.jar 무결성 검증 (lib/SHA256SUMS 기준)
#
#   0: 모두 일치, 또는 SHA-256 도구가 없어 검증을 건너뜀 (경고 출력)
#   1: 파일 누락 또는 해시 불일치
#
# AIX/HP-UX 등 sha256sum 이 없는 환경을 위해 shasum, openssl 순으로 대체한다.

SUMS="lib/SHA256SUMS"

if [ ! -f "$SUMS" ]; then
    echo "ERROR: $SUMS not found."
    exit 1
fi

hash_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    elif command -v openssl >/dev/null 2>&1; then
        openssl dgst -sha256 "$1" | awk '{print $NF}'
    else
        return 2
    fi
}

failed=0
while read -r sum name; do
    [ -z "$sum" ] && continue
    name="${name#\*}"
    name="${name%$'\r'}"
    file="lib/$name"
    if [ ! -f "$file" ]; then
        echo "ERROR: missing $file (run download-dependencies.sh on an online machine)"
        failed=1
        continue
    fi
    actual=$(hash_of "$file") || {
        echo "WARNING: no SHA-256 tool (sha256sum/shasum/openssl). Skipping lib verification."
        exit 0
    }
    if [ "$actual" != "$sum" ]; then
        echo "ERROR: checksum mismatch: $file"
        failed=1
    fi
done < "$SUMS"

if [ "$failed" -ne 0 ]; then
    exit 1
fi
echo "lib verification OK ($(grep -c . "$SUMS") files)"
