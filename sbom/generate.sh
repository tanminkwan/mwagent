#!/usr/bin/env bash
# CycloneDX 1.6 SBOM 생성 (PLAN-001 WS-5-3, mwm-app 과 같은 형식)
#   출력: sbom/mwagent.cdx.json  (런타임 의존성 = lib/ 로 배포되는 jar)
#
# cyclonedx-maven-plugin 결과를 다음과 같이 보정한다:
#   - 제품 버전: pom.xml 이 아니라 Version.java (버전의 단일 소스, CLAUDE.md)
#   - Paho: EPL-2.0 과 EDL-1.0(= SPDX BSD-3-Clause) 이중 라이선스
#   - 재현성: metadata.timestamp 제거 (같은 입력이면 같은 파일)
#
# 사용: sbom/generate.sh [mvn 경로]   (기본: PATH 의 mvn). python3 필요

set -euo pipefail
cd "$(dirname "$0")/.."

MVN="${1:-mvn}"
"$MVN" -B -q org.cyclonedx:cyclonedx-maven-plugin:makeBom

VERSION=$(sed -n 's/.*VERSION = "\([^"]*\)".*/\1/p' src/main/java/mwagent/common/Version.java)
[ -n "$VERSION" ] || { echo "ERROR: VERSION not found in Version.java"; exit 1; }

python3 - "$VERSION" <<'PY'
import json, sys
version = sys.argv[1]
path = "sbom/mwagent.cdx.json"
with open(path, encoding="utf-8") as f:
    bom = json.load(f)

meta = bom["metadata"]
meta.pop("timestamp", None)
comp = meta["component"]
old = comp["version"]
comp["version"] = version
for key in ("purl", "bom-ref"):
    if key in comp:
        comp[key] = comp[key].replace("@" + old, "@" + version)
for dep in bom.get("dependencies", []):
    if dep.get("ref", "").endswith("@" + old + "?type=jar") or dep.get("ref", "").endswith("@" + old):
        dep["ref"] = dep["ref"].replace("@" + old, "@" + version)

for c in bom.get("components", []):
    if c.get("name") == "org.eclipse.paho.mqttv5.client":
        c["licenses"] = [{"expression": "EPL-2.0 OR BSD-3-Clause"}]

with open(path, "w", encoding="utf-8") as f:
    json.dump(bom, f, indent=2, ensure_ascii=False)
    f.write("\n")
print("SBOM written: %s (mwagent %s, %d components)" % (path, version, len(bom.get("components", []))))
PY
