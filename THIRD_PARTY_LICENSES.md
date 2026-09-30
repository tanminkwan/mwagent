# Third-party licenses

mwagent 가 배포하는 런타임 라이브러리(`lib/`)와 라이선스입니다. 테스트·빌드 도구(JUnit, Mockito, AssertJ,
Maven 플러그인)는 배포물에 들어가지 않으므로 목록에 없습니다.

- 기계 판독용 목록: [`sbom/mwagent.cdx.json`](sbom/mwagent.cdx.json) (CycloneDX 1.6, `sbom/generate.sh` 로 생성)
- 라이선스 전문: [`licenses/`](licenses/)
- Apache-2.0 구성요소의 NOTICE: [`NOTICE`](NOTICE)
- jar 목록과 해시: [`lib/SHA256SUMS`](lib/SHA256SUMS)

| 라이브러리 | 버전 | 라이선스 (SPDX) | 라이선스 전문 | 용도 |
|------------|------|-----------------|---------------|------|
| Apache HttpClient (`org.apache.httpcomponents:httpclient`) | 4.5.14 | Apache-2.0 | [Apache-2.0.txt](licenses/Apache-2.0.txt) | HTTP/HTTPS 통신 |
| Apache HttpCore (`org.apache.httpcomponents:httpcore`) | 4.4.16 | Apache-2.0 | [Apache-2.0.txt](licenses/Apache-2.0.txt) | HttpClient 코어 |
| Apache Commons Logging (`commons-logging:commons-logging`) | 1.2 | Apache-2.0 | [Apache-2.0.txt](licenses/Apache-2.0.txt) | HttpClient 로깅 |
| Apache Commons Codec (`commons-codec:commons-codec`) | 1.22.1 | Apache-2.0 | [Apache-2.0.txt](licenses/Apache-2.0.txt) | 인코딩 유틸리티 |
| JSON.simple (`com.googlecode.json-simple:json-simple`) | 1.1.1 | Apache-2.0 | [Apache-2.0.txt](licenses/Apache-2.0.txt) | JSON 처리 |
| Eclipse Paho MQTT v5 (`org.eclipse.paho:org.eclipse.paho.mqttv5.client`) | 1.2.5 | EPL-2.0 OR BSD-3-Clause (EDL-1.0) | [EPL-2.0.txt](licenses/EPL-2.0.txt), [EDL-1.0.txt](licenses/EDL-1.0.txt) | MQTT 명령 구독 |
| Bouncy Castle (`org.bouncycastle:bcprov-jdk18on`) | 1.86 | MIT (Bouncy Castle Licence) | [BouncyCastle-MIT.txt](licenses/BouncyCastle-MIT.txt) | TLS 1.2 provider (AIX) |

## 확인 방법 (2026-09-30)

- 각 jar 의 `META-INF/LICENSE*`, `META-INF/NOTICE*`, Paho 는 jar 안의 `about.html`, 그리고 Maven Central 의 pom `<licenses>`.
- Paho 는 EPL-2.0 과 EDL-1.0 중 하나를 고를 수 있는 이중 라이선스다. EDL-1.0 은 SPDX 로 BSD-3-Clause 에 해당한다.
- json-simple 1.1.1 jar 에는 라이선스 파일이 없고 pom 에 Apache-2.0 으로 적혀 있다.

## 의존성을 바꿀 때

`pom.xml`, `download-dependencies.*`, `lib/SHA256SUMS` 와 함께 이 표, `NOTICE`(Apache-2.0 구성요소), `licenses/`,
SBOM(`sbom/generate.sh`)을 갱신한다. CI `lib-integrity` 는 pom 과 `lib/SHA256SUMS` 가 어긋나면 실패한다.
