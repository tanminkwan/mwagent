# PLAN-001: mwagent 오픈소스 공개 준비

> 작성: 2026-09-29
> 대상 버전: 0000.0010.0002 이후
> 참고: mwm-app 저장소 `docs/PLAN_001_opensource_readiness.md`, `docs/PLAN-001/`
> 작업 기록 정책: 이 문서와 task 기록에는 비밀번호·토큰·이메일·사내 IP·실제 agent_id·호스트명 **원문을 적지 않는다**. 위치와 이력만 적는다.

---

## 1. 배경

mwm-app 은 공식 오픈소스로 등록해 기업이 안심하고 쓸 수 있도록 CVE 점검, 소스 취약점 점검(SAST), 시크릿 스캔, 공개 저장소 분리를 마쳤다.
mwagent 도 같은 수준을 갖춰야 한다.

다만 agent 는 app 과 조건이 다르다.

- 고객사 서버에 설치돼 **원격 명령을 실행**한다 (파일 읽기, 스크립트 실행, 다운로드 후 실행). 통신 채널이 뚫리면 곧바로 원격 코드 실행이 된다.
- **Java 8 과 Java 17 을 모두 지원해야 한다.** 이것이 최우선 제약이다. JDK 8 자체의 취약점은 감수한다.
- 인터넷이 막힌 환경에 `lib/*.jar` 를 들고 가서 오프라인 빌드한다.

## 2. 목표

1. 공개 저장소에 비밀·고객사·개인 식별 정보가 **0건**이다 (현재 파일과 공개 히스토리 모두).
2. 의존성 CVE 를 **Java 8 에서 쓸 수 있는 최신 버전**까지 올린다. 올릴 수 없는 것은 사유와 재검토 시점을 문서로 남긴다.
3. SAST(CodeQL java, SpotBugs+FindSecBugs)의 critical·high 는 0건이다. medium 은 수정하거나, 검증 후 사유를 적고 dismiss 한다.
4. **JDK 8 과 JDK 17 양쪽에서** 빌드와 전체 테스트가 CI 에서 통과한다.
5. 라이선스·NOTICE·SBOM·SECURITY.md 를 갖춘다. 이 부분은 app 도 아직 하지 않았으므로 app 과 함께 맞춘다.

## 3. 현황 진단 (2026-09-29 기준)

아래는 코드와 저장소를 직접 확인한 결과다. CVE 번호는 알려진 범위로 적은 **추정치**이며 WS-2-1 스캔으로 확정한다.

### 3.1 코드 보안 (SAST 수동 1차)

| # | 심각도 | 위치 | 내용 |
|---|--------|------|------|
| C-1 | **Critical** | `Common.java` HTTPS client, `ApacheHttpClientAdapter` | 서버 인증서를 **모두 신뢰**하고(`loadTrustMaterial(null, (c,a) -> true)`) 호스트명 검증도 끈다(`NoopHostnameVerifier`). 중간자가 refresh token 을 가로채고 명령을 주입할 수 있다 → 원격 코드 실행 |
| C-2 | High | `Common.createMtlsClient()` | mTLS 는 truststore 로 인증서를 검증하지만 호스트명 검증은 끈다 |
| C-3 | **High** | `DownloadFile.unzipFile()`, `DownloadNUnzipFunc` | **Zip Slip**. zip 항목의 `../` 를 검사하지 않아 대상 디렉터리 밖에 파일을 쓸 수 있다 |
| C-4 | High | `Common.httpPOST*()` | 응답 본문 전체를 INFO 로 로깅한다. token 갱신 응답의 access token 이 로그 파일에 남는다 |
| C-5 | Medium | `Common.httpGET()` | 명령 조회 응답 전체를 로깅한다. `additional_params` 에 민감값이 올 수 있다. 명령 결과(설정 파일 원문 등)의 로깅 여부도 점검한다 |
| C-6 | 설계상 허용 | `SSLCertiFunc` | 원격 서버 인증서를 **조회**하는 기능이라 trust-all 이 필요하다. 사유를 적고 dismiss 할 대상이다 |
| C-7 | 설계상 허용 | `ExeScript`, `ExeText`, `ExeShell`, `DownloadFile` (`ProcessBuilder`) | 명령 실행이 agent 의 본래 기능이다. 위협 모델 문서(서버·채널 신뢰, MQTT ACL)로 대체한다 |
| C-8 | 확인 필요 | MQTT `dispatch()` | MQTT 로 받은 payload 는 명령으로 실행된다. 입력 검증과 토픽 ACL 이 유일한 방어선이다 |

### 3.2 의존성 (`lib/`, `pom.xml`)

| 라이브러리 | 현재 | 알려진 문제 (추정) | Java 8 상한 목표 |
|------------|------|--------------------|------------------|
| bcprov-jdk15on | 1.70 | CVE-2023-33201, CVE-2024-29857/30171/30172. jdk15on 계열은 1.70 에서 종료 | `bcprov-jdk18on` 최신 (Java 8 지원) |
| kafka-clients | 3.1.0 | CVE-2023-25194, CVE-2024-31141, CVE-2025-27817 등 | **3.9.x** (4.x 는 Java 11 이상이라 불가) |
| snappy-java | 1.1.8.4 | CVE-2023-34453/34454/34455/43642 | 1.1.10.x |
| lz4-java | 1.8.0 | 최근 CVE 확인 필요 (배포 좌표 변경 여부 포함) | 스캔 결과로 결정 |
| zstd-jni | 1.5.2-1 | 확인 필요 | 스캔 결과로 결정 |
| httpclient / httpcore | 4.5.13 / 4.4.13 | 알려진 주요 CVE 없음 | 4.5.14 / 4.4.16 |
| commons-codec | 1.11 | 주요 CVE 없음 | 최신 1.x |
| slf4j-api / simple | 1.7.30 | 주요 CVE 없음 | 1.7.36 (2.x 도 Java 8 가능, 검토) |
| json-simple | 1.1.1 | CVE 없음. 2012년 이후 **유지보수 중단** | 유지하되 리스크로 기록 |
| paho mqttv5 client | 1.2.5 | 최신 릴리스. 활동이 적다 | 유지 |

테스트·빌드 도구의 Java 버전 상한:

| 도구 | 현재 | 제약 |
|------|------|------|
| mockito | 3.12.4 | 5.x 는 Java 11 이상 → **4.11.x 까지** |
| jacoco | 0.8.7 | Java 17 공식 지원은 0.8.8 부터 → 0.8.12 이상 |
| junit-jupiter | 5.8.2 | 5.x 는 Java 8 가능 → 5.10.x (JUnit 6 은 Java 17 이상이라 불가) |
| maven-surefire | 2.22.2 | 3.x 도 Java 8 가능 |

그 밖의 문제:
- `pom.xml` 과 `build.gradle` 의 version(0.9.27)이 `Version.java` 와 다르다
- `lib/*.jar` 에 해시가 없어 반입물 무결성을 검증할 수 없다
- `pom.xml`, `build.gradle`, `lib/` 의 버전을 한곳에서 맞추는 절차가 없다

### 3.3 Java 8 / 17 호환

- 소스에 `sun.*`, JAXB, Nashorn, attach API 등 JDK 내부 API·제거된 API 사용은 **없다** (grep 확인).
- 빌드 스크립트가 `-source 1.8 -target 1.8` 만 쓴다. JDK 9 이상에서 빌드하면 Java 9+ API 를 써도 컴파일이 통과해, Java 8 에서 런타임 오류가 난다. **`--release 8`** 이 필요하다.
- TLS 프로토콜을 `TLSv1.2` 로 고정했다. Java 17 에서도 동작하지만 TLS 1.3 을 쓰지 못한다.
- 로컬 환경에는 JDK 8 만 있다. Java 17 검증은 CI 에서만 가능하다.
- README 기준으로 알려진 테스트 실패 3건(SecurityValidatorTest, ExtractLogTest)이 있다. CI 게이트를 걸려면 먼저 해결해야 한다.

### 3.4 저장소 위생

| 항목 | 현황 |
|------|------|
| `temp_jdk/` | JDK 8 파일 320개가 git 에 추적되고 있다. 라이선스 재배포 조건과 저장소 크기 문제 |
| `tools/` | Maven 배포본이 추적되고 있다 (CLAUDE.md 상 로컬 삭제 금지. 공개본에서만 뺀다) |
| 사내 프록시 IP | CLAUDE.md, `docs/PROJECT_REPORT.md` 에 있다 |
| 개발 PC 경로·사용자명 | `run.agent.sh`, `test/demos/TestExtractLog.java`, `docs/SetProperties-Requirements.md`, CLAUDE.md, README |
| 개발 agent_id (호스트명 포함) | `WORK_HISTORY.md` 2026-09-29 항목 |
| 개발용 내부 호스트명 | 테스트 코드·문서의 `server_url` |
| 테스트 픽스처 비밀번호 | `test-server/test-agent.properties`(keystore·truststore 비밀번호, refresh token 자리값), `src/test/resources/test-agent.properties` — 테스트 전용 더미값인지 확인하고 명시한다 |
| 기본 비밀값 | `biz-service/config.py`, `ca-server/app.py` 의 기본 SECRET_KEY. 테스트 서버지만 "없으면 기동 실패"로 바꿀지 결정한다 |
| 개인키·인증서 | 현재 파일과 히스토리에 PEM 개인키 블록·키 파일은 **없다** (grep 1차). gitleaks 로 확정 |
| JWT | 히스토리에 나오는 JWT 는 `...` 로 잘린 샘플이다 (1차). gitleaks 로 확정 |
| LICENSE | MIT. 저작권자 표기를 확정해야 한다 |
| SECURITY.md, CONTRIBUTING, CODE_OF_CONDUCT, NOTICE, SBOM, CI | **없음** |

## 4. WBS

task 기록은 `docs/PLAN-001/TASK_<WS>-<n>_<주제>.md` 에 남기고, 목록은 `docs/PLAN-001/README.md` 표로 관리한다 (app 과 같은 형식).

### WS-1 비밀·식별 정보 정리와 공개 저장소 분리

| Task | 내용 |
|------|------|
| 1-1 | **기준선 스캔**: gitleaks(`--redact`)로 히스토리 전체를 본다. denylist(사내 도메인·IP·사용자명·호스트명·고객사명)로도 검색한다. gitleaks 는 호스트명을 못 잡는다 |
| 1-2 | 식별 정보를 자리표시자로 바꾼다: 개발 경로 → 환경변수, 사내 IP 는 문서에서 삭제, 실제 agent_id·호스트명 → 예시값. `run.agent.sh` 의 경로를 파라미터화한다 |
| 1-3 | 테스트 픽스처의 비밀번호·토큰이 **더미값임을 명시**하고, 실제 값처럼 보이는 것은 바꾼다. 인증서는 계속 `generate-certs.sh` 로 각자 만든다 |
| 1-4 | `temp_jdk/`, `tools/`, `release/*.jar` 를 공개본에서 뺀다. JDK·Maven 받는 방법은 README 에 안내한다. 로컬 원본의 `tools/` 는 지우지 않는다 |
| 1-5 | **공개 방식 결정**: app 처럼 원본은 비공개로 두고, 정리된 최종 상태만 새 공개 저장소에 커밋 1개로 올린다. 기존 저장소를 공개로 돌리면 PR 의 `refs/pull/*` 가 옛 커밋을 붙잡는다 |
| 1-6 | 공개 도구 `.publish/export.sh` 를 app 과 같은 구조로 만든다. 누출 검사 5종(개인키 블록, 로컬 실제 설정값, denylist, gitleaks, 제외 경로 참조)을 통과해야만 결과를 낸다. 가짜 데이터로 검사기 자체를 먼저 검증한다 |

### WS-2 의존성·CVE

| Task | 내용 |
|------|------|
| 2-1 | **기준선 스캔**: OWASP Dependency-Check(`pom.xml`)와 Trivy fs(`lib/*.jar`)를 둘 다 돌린다. 3.2 표의 추정 CVE 를 확정한다 |
| 2-2 | 3.2 표의 **Java 8 상한** 안에서 업그레이드한다. 업그레이드마다 JDK 8·17 에서 전체 테스트를 돌린다 |
| 2-3 | 버전을 한곳에 맞춘다: `pom.xml` 을 기준으로 하고 `build.gradle` 과 `lib/` 를 맞춘다. `download-dependencies.sh` 가 그 버전을 받게 한다 |
| 2-4 | `lib/SHA256SUMS` 를 만들고, 오프라인 빌드 스크립트가 빌드 전에 검증한다 |
| 2-5 | Dependabot(maven, github-actions)을 켠다. **Java 11 이상이 필요한 major**(kafka-clients 4, mockito 5, JUnit 6 등)는 `ignore` 에 사유와 함께 적는다 |
| 2-6 | Java 8 상한 때문에 **고칠 수 없는 CVE** 는 suppression 파일에 CVE 번호·사유·재검토일을 적는다. app 은 suppression 0건이지만 agent 는 이 예외가 생길 수 있다 |

### WS-3 SAST 와 코드 수정

| Task | 내용 |
|------|------|
| 3-1 | CodeQL(java-kotlin, actions, `security-extended`)을 Advanced setup 으로 켠다. SpotBugs+FindSecBugs 를 Maven 에 붙인다 |
| 3-2 | **C-1/C-2 TLS 검증 복구**. 기본값은 검증함으로 두고, 사설 CA 는 truststore 설정으로 지원한다. 이미 설치된 agent 가 자체 서명 인증서로 끊기지 않도록 설정 키·마이그레이션 안내·버전 공지를 함께 낸다 (호환성 영향이 가장 큰 항목). **착수할 때 app 세션에 연락한다** — app 의 nginx 인증서·CA 배포 방식(`certs/`, `init_certs.sh`)에 맞춘다 |
| 3-3 | **C-3 Zip Slip**. 정규화한 경로가 대상 디렉터리 안인지 검사하고, 벗어나면 거부한다. 두 unzip 구현을 하나로 합친다 |
| 3-4 | **C-4/C-5 로그 정리**. token·비밀번호·명령 결과 원문을 로그에 남기지 않는다. 마스킹 도우미(app 의 `log_safe` 대응)를 만든다 |
| 3-5 | C-6/C-7 은 위협 모델 문서(`docs/SPEC_0xx_threat_model.md`)에 근거를 적고, 도구 결과에서 사유와 함께 dismiss 한다 |
| 3-6 | C-8 MQTT payload 검증을 점검한다: 필수 필드, 알 수 없는 `command_class` 거부, 크기 제한. REST 경로와 같은 검증을 거치는지 확인한다 |
| 3-7 | 3-1 결과의 나머지 건을 처리한다. 건마다 **테스트를 먼저 쓴다**(TDD) |

### WS-4 Java 8 / 17 호환 보장

| Task | 내용 |
|------|------|
| 4-1 | CI 매트릭스: Temurin **JDK 8 과 JDK 17** 에서 빌드와 전체 테스트를 돌린다 |
| 4-2 | JDK 9 이상에서 빌드할 때는 `--release 8` 을 쓴다. Maven 은 JDK 버전별 profile 로 `maven.compiler.release=8` 을 켜고, 오프라인 빌드 스크립트는 javac 버전을 보고 옵션을 고른다 |
| 4-3 | 3.2 표의 테스트 도구 상한 안에서 올린다: mockito 4.11, jacoco 0.8.12+, junit 5.10, surefire 3.x |
| 4-4 | Java 17 런타임 점검: TLS 1.3 허용(`TLSv1.2` 고정 해제 검토), BouncyCastle provider 등록, 테스트의 리플렉션(자기 클래스만 대상이라 1차 문제없음) |
| 4-5 | 알려진 테스트 실패 3건을 해결해 CI 게이트를 걸 수 있게 한다 |
| 4-6 | **호환 정책을 문서화**한다: Java 9+ API 금지, 의존성은 Java 8 지원 버전만 허용, 새 의존성을 추가할 때의 확인 절차 |

### WS-5 라이선스·SBOM (app 과 함께)

| Task | 내용 |
|------|------|
| 5-1 | 의존성 라이선스를 조사한다. Apache-2.0(httpclient, kafka, commons, snappy, lz4, json-simple), MIT(slf4j), Bouncy Castle License, BSD(zstd-jni), **EPL-2.0/EDL-1.0(Paho)** 이 예상되며 조사로 확정한다 |
| 5-2 | `NOTICE` 와 `THIRD-PARTY-LICENSES` 를 만들고, `lib/` 반입물에 함께 넣는다 |
| 5-3 | CycloneDX SBOM 을 만든다(`cyclonedx-maven-plugin`). 릴리스 산출물에 첨부한다. app 도 CycloneDX 로 맞추기로 합의했다 (2026-09-29) |
| 5-4 | LICENSE 의 저작권자 표기를 app 과 맞춘다 |

### WS-6 저장소·커뮤니티 문서 (app 과 함께)

| Task | 내용 |
|------|------|
| 6-1 | `SECURITY.md`: 취약점 신고 절차, 지원 버전, 대응 기한. app 과 같은 신고 창구를 쓰기로 합의했다 (시기는 사용자가 정한다) |
| 6-2 | `CONTRIBUTING.md`: Java 8/17 호환 규칙, 테스트 필수, 커밋 규칙 |
| 6-3 | `CODE_OF_CONDUCT.md`, 이슈·PR 템플릿 |
| 6-4 | 공개 저장소 설정: 브랜치 보호, secret scanning, push protection, 릴리스 서명 여부 결정 |
| 6-5 | README 정리: 설치·빌드(JDK 8/17)·보안 설정(TLS, MQTT ACL) 안내. 사내 전용 내용은 뺀다 |

### WS-7 CI

`.github/workflows/` 에 다음을 둔다. Actions 는 SHA 로 고정한다.

| Job | 내용 | 실패 기준 |
|-----|------|-----------|
| build-test | JDK 8·17 매트릭스, `mvn verify` | 테스트 실패 1건 |
| secrets | gitleaks `--redact` | 1건 |
| deps | OWASP Dependency-Check + Trivy(`lib/`) | suppression 에 없는 CVE 1건 |
| sast | CodeQL(주 1회 cron + push), SpotBugs+FindSecBugs | 새 high 이상 1건 |
| lib-integrity | `lib/SHA256SUMS` 검증, `pom.xml` 과 `lib/` 버전 일치 | 불일치 1건 |
| coverage | jacoco 기준선 래칫 | 기준선보다 떨어지면 |

## 5. 단계

| 단계 | 내용 | 선행 |
|------|------|------|
| 1 | 기준선 스캔: WS-1-1, WS-2-1, WS-3-1 | 없음 |
| 2 | Critical/High 코드 수정: WS-3-2~3-4 + 회귀 테스트 | 1 |
| 3 | 의존성 업그레이드·Java 8/17 CI: WS-2-2~2-6, WS-4 전체 | 1 |
| 4 | 나머지 SAST·위협 모델: WS-3-5~3-7 | 2 |
| 5 | 라이선스·문서: WS-5, WS-6 (app 과 일정을 맞춘다) | 3 |
| 6 | 공개본 생성: WS-1-2~1-6, CI 전체 녹색 확인 후 공개 저장소에 올린다 | 1~5 |

## 6. 완료 기준

- [ ] gitleaks·denylist·개인키 검사가 공개본에서 0건 (검사기는 가짜 데이터로 먼저 검증)
- [ ] 의존성 CVE: suppression 파일에 사유·재검토일이 적힌 것 외에는 0건
- [ ] CodeQL·SpotBugs critical·high 0건, medium 은 수정 또는 사유 기록
- [ ] **JDK 8 과 JDK 17** 에서 빌드·전체 테스트 통과 (CI 기록)
- [ ] `--release 8` 빌드 확인, Java 9+ API 가 쓰이지 않음
- [ ] mTLS 모드와 Legacy 모드가 모두 동작 (CLAUDE.md 필수 규칙)
- [ ] `lib/SHA256SUMS`, NOTICE, THIRD-PARTY-LICENSES, SBOM, SECURITY.md 존재
- [ ] 기존 설치 agent 용 TLS 설정 마이그레이션 안내 문서화

## 7. 리스크

| 리스크 | 영향 | 대응 |
|--------|------|------|
| Java 8 상한 때문에 고칠 수 없는 CVE | 목표 2 미달 | suppression 에 사유·재검토일을 기록한다. 영향 경로가 없는지 분석을 붙인다 (예: kafka 미사용 설치에서는 해당 jar 를 빼는 옵션) |
| TLS 검증 복구가 기존 설치를 끊음 | 운영 장애 | 설정 키와 안내 문서, 단계적 기본값 전환. app 과 배포 일정을 맞춘다 |
| 로컬에서 Maven·JDK 17 사용 불가 | 검증 지연 | CI 를 기준으로 한다. 로컬은 temp_jdk javac 로 컴파일만 확인한다 |
| 옛 히스토리에 식별 정보가 남아 있음 | 공개 시 노출 | 기존 저장소를 공개하지 않고 새 저장소에 커밋 1개로 올린다 (WS-1-5) |
| json-simple·Paho 의 유지보수 저하 | 향후 CVE 대응 불가 | 리스크로 기록하고, 대체 라이브러리(Java 8 지원)를 조사해 둔다 |
| 명령 실행형 agent 의 본질적 위험 | 도입 기업의 우려 | 위협 모델 문서로 신뢰 경계(서버·TLS·MQTT ACL)와 운영 권고를 명시한다 |

## 8. 작업 방식

- 수정은 건마다 **테스트를 먼저 쓴다**(TDD). 보안 수정은 공격 입력이 담긴 회귀 테스트를 남긴다.
- 모든 변경은 JDK 8 과 JDK 17 양쪽에서 확인한다. 로컬에서 확인할 수 없으면 CI 결과로 확인한다고 적는다.
- task 기록에는 비밀·IP·실제 식별자 원문을 적지 않는다.
- `docs/PLAN-001/` 폴더는 공개본에서 뺀다. 이 계획서의 3장 같은 내부 진단 내용도 공개 전에 다시 검토한다.
- app 과 겹치는 항목(WS-5, WS-6, SECURITY 창구)은 app 세션과 합의한 뒤 진행한다.
