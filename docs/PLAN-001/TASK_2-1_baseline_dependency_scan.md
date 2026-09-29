# TASK 2-1: 의존성 CVE 기준선 스캔

> 수행: 2026-09-29
> 상위 계획: [PLAN_001](../PLAN_001_opensource_readiness.md) WS-2-1

## 1. 도구와 범위

| 도구 | 버전 | 대상 |
|------|------|------|
| Trivy | latest (Docker `aquasec/trivy`, 2026-09-29 DB) | `pom.xml` (전이 의존성 포함), `lib/*.jar` |
| Grype | latest (Docker `anchore/grype`, 2026-09-29 DB) | `lib/*.jar` |
| OWASP Dependency-Check | 미실행 | NVD API 키가 없으면 매우 느리다. CI(WS-7)에서 키를 등록한 뒤 돌린다 |

두 스캐너의 결과는 **junit 1건을 빼고 모두 일치**했다. junit 은 `pom.xml` 의 전이 의존성이라 Trivy 만 봤다.

## 2. 결과: HIGH 5, MEDIUM 11 (CRITICAL 0)

| 라이브러리 | 현재 | CVE | 심각도 | 수정 버전 | 비고 |
|------------|------|-----|--------|-----------|------|
| kafka-clients | 3.1.0 | CVE-2026-35554 | HIGH | 3.9.2 / 4.0.2 / 4.1.2 | |
| | | CVE-2024-31141 | MEDIUM | 3.7.1 | |
| | | CVE-2025-27817 | MEDIUM | 3.9.1 | |
| | | CVE-2026-33558 | MEDIUM | 3.9.2 / 4.0.1 | |
| lz4-java (`org.lz4`) | 1.8.0 | CVE-2025-12183 | HIGH | 1.8.1 (`at.yawk.lz4` 좌표) | 원 프로젝트가 좌표를 옮겼다 |
| | | CVE-2025-66566 | HIGH | `org.lz4` 좌표로는 없음 | `at.yawk.lz4` 에서 확인 필요 |
| | | CVE-2026-59949 | MEDIUM | `org.lz4` 좌표로는 없음 | 같음 |
| snappy-java | 1.1.8.4 | CVE-2023-34455 | HIGH | 1.1.10.1 | |
| | | CVE-2023-43642 | HIGH | 1.1.10.4 | |
| | | CVE-2023-34453 | MEDIUM | 1.1.10.1 | |
| | | CVE-2023-34454 | MEDIUM | 1.1.10.1 | |
| bcprov-jdk15on | 1.70 | CVE-2023-33201 | MEDIUM | jdk15on 계열에는 없음 | `bcprov-jdk18on` 으로 옮겨야 한다 |
| | | CVE-2024-29857 | MEDIUM | 1.78 | |
| | | CVE-2024-30171 | MEDIUM | 1.78 | |
| | | CVE-2024-34447 | MEDIUM | 1.78 | |
| junit | 4.10 | CVE-2020-15250 | MEDIUM | 4.13.1 | **json-simple 1.1.1 이 compile scope 로 끌어온다.** `lib/` 에는 없다. Maven 빌드 산출물에 들어가는지 확인하고 `<exclusion>` 한다 |

취약점이 없는 것: httpclient 4.5.13, httpcore 4.4.13, commons-codec 1.11, commons-logging 1.2, json-simple 1.1.1, paho mqttv5 1.2.5, slf4j 1.7.30, zstd-jni 1.5.2-1.

PLAN_001 3.2 표의 추정치와 비교:
- 추정한 kafka CVE-2023-25194 는 나오지 않았다 (connect 모듈 대상).
- 새로 나온 것: kafka CVE-2026-35554·CVE-2026-33558, lz4 3건, bcprov CVE-2024-34447, junit(전이).

## 3. Java 8 에서 가능한 조치 (WS-2-2 입력)

| 라이브러리 | 목표 | Java 8 | 근거 |
|------------|------|--------|------|
| kafka-clients | **3.9.2** | 가능 (3.x 는 Java 8 지원, 4.x 부터 Java 11) | kafka 의 4건을 모두 해결한다 |
| lz4-java | kafka 3.9.2 가 끌어오는 `at.yawk.lz4:lz4-java` 1.10.1 | **확인 필요** | kafka 3.9.2 pom 기준. 남은 lz4 CVE 2건이 1.10.1 에서 해결됐는지 재스캔한다 |
| snappy-java | 1.1.10.5 (kafka 3.9.2 기준) | 가능 | HIGH 2건 포함 4건 해결 |
| zstd-jni | 1.5.6-4 (kafka 3.9.2 기준) | 확인 필요 (네이티브 라이브러리 포함) | 현재 취약점은 없다. kafka 와 버전을 맞춘다 |
| bcprov | `bcprov-jdk18on` 1.86 (최신) | 가능 (jdk18on = Java 1.8 이상) | 4건 해결. 패키지명은 같아 코드 변경은 없을 것으로 본다 |
| junit(전이) | json-simple 에서 `<exclusion>` | 해당 없음 | 런타임에 쓰지 않는다 |

`lib/` 을 교체할 때는 kafka 가 끌어오는 압축 라이브러리 3종을 함께 바꿔야 한다. 오프라인 반입물 목록(`lib/README.md`, `download-dependencies.*`)도 함께 고친다.

## 4. 후속 조치

1. WS-2-2 에서 §3 대로 올리고, JDK 8·17 에서 전체 테스트를 돌린다. Kafka 경로는 통합 테스트가 없으므로 수동 확인 절차를 적는다.
2. 올린 뒤 Trivy·Grype 를 다시 돌려 **0건**인지 확인한다. 남으면 WS-2-6 suppression 으로 사유를 기록한다.
3. OWASP Dependency-Check 는 CI 에서 NVD API 키로 돌린다 (WS-7).
4. 이 결과는 2026-09-29 DB 기준이다. 공개 직전에 다시 스캔한다.
