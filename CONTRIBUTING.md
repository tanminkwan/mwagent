# Contributing

## 먼저 알아 둘 것

- agent 는 **Java 8 에서 실행**되어야 합니다 (AIX, HP-UX 등 오래된 서버). 개발·CI 는 JDK 8 과 17 둘 다에서 합니다.
- 명령 실행이 본래 기능입니다. 보안 설계는 [위협 모델 (SPEC-001)](docs/SPEC_001_threat_model.md)을 먼저 읽어 주세요.
- 취약점은 이슈가 아니라 [SECURITY.md](SECURITY.md) 의 비공개 경로로 알려 주세요.

## 빌드와 테스트

```bash
mvn -B verify                                   # 빌드 + 단위 테스트 + 커버리지 기준 (JDK 8 / 17)
mvn -B compile spotbugs:check                   # SpotBugs + FindSecBugs (기준선: config/spotbugs-exclude.xml)
bash download-dependencies.sh && bash build-offline.sh   # 오프라인 빌드 경로 (lib/ 사용)
```

통합 테스트는 테스트 서버가 필요해 기본으로 건너뜁니다. 환경 변수로 켭니다
(`MTLS_INTEGRATION_TEST`, `BIZ_SERVICE_INTEGRATION_TEST`, `SSL_CERT_INTEGRATION_TEST`).
테스트 서버 실행은 [src/test/java/mwagent/README_TESTS.md](src/test/java/mwagent/README_TESTS.md) 를 보세요.

## Java 8 / 17 호환 규칙

- **Java 9+ API 를 쓰지 않습니다.** JDK 9 이상에서는 `--release 8` 로 빌드하므로(Maven profile `release-8`,
  `build-offline.*`) 쓰면 컴파일이 실패합니다. `List.of`, `var`, `String.isBlank`, `Optional.isEmpty` 등이 해당합니다.
- **의존성은 Java 8 을 지원하는 버전만** 씁니다. Java 11+ 가 필요한 major(Mockito 5, JUnit 6, maven-compiler-plugin 4 등)는
  `.github/dependabot.yml` 에서 막아 두었습니다.
- 새 의존성을 추가할 때:
  1. 그 버전의 최소 Java 버전을 확인합니다 (릴리스 노트, pom 의 `maven.compiler.release`/`target`).
  2. 라이선스를 확인하고 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md), `licenses/`, `NOTICE`(Apache-2.0) 를 갱신합니다.
  3. `pom.xml`, `download-dependencies.sh/.bat`, `lib/SHA256SUMS` 를 **함께** 바꿉니다 (CI `lib-integrity` 가 확인합니다).
  4. `sbom/generate.sh` 로 SBOM 을 다시 만듭니다.
- 대소문자 변환은 `toLowerCase(Locale.ROOT)` 처럼 로케일을 지정합니다.
- TLS 프로토콜은 `TLSv1.2` 로 고정되어 있습니다 (AIX IBM JDK 호환). 바꾸려면 AIX 에서 먼저 확인해야 합니다.

## 코드 규칙

- 로그는 파일로 남깁니다 (`getConfig().getLogger()`). `System.out/err`, `printStackTrace()` 는 쓰지 않습니다.
- 서버 응답·명령 값을 로그에 쓸 때는 `LogSafe.safe(...)` 를 거칩니다. 응답 본문·명령 결과 원문은 남기지 않습니다.
- 서버 응답에서 온 파일명·경로는 `SecurityValidator` 로 검사합니다.
- 실패는 가능하면 예외 대신 `isOk=false` 인 `ResultVO` 로 돌려줍니다 (예외면 결과가 서버로 가지 않습니다).
- 버전은 `src/main/java/mwagent/common/Version.java` 한 곳에서만 바꿉니다.

## 테스트

- 기능 변경·버그 수정에는 테스트를 함께 넣습니다. 보안 수정은 **테스트를 먼저** 씁니다.
- 커버리지는 줄이지 않습니다. `pom.xml` 의 JaCoCo `minimum` 은 올릴 수만 있습니다.
- SpotBugs 새 경고는 고치거나, 정말 설계상 허용이면 `config/spotbugs-exclude.xml` 에 사유와 함께 추가합니다.

## 커밋과 PR

- 작업마다 브랜치를 만들어 PR 로 보냅니다.
- 커밋 제목은 Conventional 접두어 + 영어 명령형입니다: `feat:`, `fix:`, `refactor:`, `build:`, `ci:`, `docs:`, `test:`, `chore:`
  (예: `fix: reject zip entries outside the target directory`)
- 본문에는 **왜** 바꿨는지와 **어떻게 확인했는지**(돌린 테스트, JDK 버전)를 적습니다.
- 비밀값·내부 주소·실제 식별자를 커밋하지 않습니다. CI 의 gitleaks 가 확인합니다.
