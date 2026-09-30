# PLAN-001 작업 기록

상위 계획: [PLAN_001_opensource_readiness.md](../PLAN_001_opensource_readiness.md)

> 이 폴더는 **공개본에서 뺀다** (내부 진단 기록). 비밀값·IP·실제 식별자 원문은 적지 않는다.

| Task | 제목 | 상태 | 수행일 | 핵심 결과 |
|------|------|------|--------|-----------|
| [1-0](TASK_1-0_publish_scope.md) | 저장소 수록 범위 정리 | 완료 (Windows 스크립트 확인 대기) | 2026-09-29 | temp_jdk·tools·demos·run.agent.sh·lib/*.jar 추적 해제, build.gradle 삭제, lib/SHA256SUMS 검증 도입. 오프라인 빌드 유지 |
| [1-1](TASK_1-1_baseline_secret_scan.md) | 비밀·식별 정보 기준선 스캔 | 완료 | 2026-09-29 | 히스토리 27건 중 **실제 비밀 2건**(MQTT 브로커 비밀번호), 로그 파일에 JWT 다수, denylist 대상 8종 |
| [2-1](TASK_2-1_baseline_dependency_scan.md) | 의존성 CVE 기준선 스캔 | 완료 | 2026-09-29 | HIGH 5 / MEDIUM 11. kafka 3.9.2, bcprov-jdk18on 으로 Java 8 안에서 대부분 해결 가능 |
| [3-1](TASK_3-1_baseline_sast.md) | SAST 기준선 스캔 | 완료 | 2026-09-29 | CodeQL critical 1 / high 7, SpotBugs 327. TLS 전체 신뢰는 **도구가 못 잡아** 수동 진단으로 보완 |
| 3-2~3-4, Q-1 | 2단계 Critical/High 수정 | 완료 (Windows `mvn test` 대기) | 2026-09-30 | TLS 는 `ssl_verify` 옵션화(기본 no-verify 유지, true 면 truststore/cacerts + 호스트명 검증). Zip Slip 차단(`SecurityValidator.resolveZipEntry`), 서버 응답 파일명 검증, 응답·결과 원문 로그 제거(`LogSafe`), `applyChmod` 인자 배열화 + 8진수만 허용 |
| SAST 6·7 | SAST 우선순위 6·7 수정 | 완료 (Windows `mvn test` 대기) | 2026-09-30 | `matchesDomain` `split("\\.")`·대소문자·null 처리, SAN 도 와일드카드 매칭. Windows `cmd` 를 `%SystemRoot%\System32\cmd.exe` 로 호출. URL 경로에 넣는 agent_id·버전·타입·파일명 인코딩(일반 값은 그대로). JmxStatFunc 의 `jmx_target`·`jmx_domain` 검증, JNDI 환경 로그에서 비밀번호 제거. 로그와 중복되는 `printStackTrace` 제거 |
| 2-2 | 의존성 정리 | 완료 | 2026-09-30 | **Kafka 기능 제거** (사용자 결정). kafka-clients·slf4j·lz4·snappy·zstd 삭제, bcprov-jdk18on 1.86, json-simple junit exclusion. 런타임 jar 13 → 7. `verify-lib` 이 SHA256SUMS 에 없는 옛 jar 를 잡고 `download-dependencies` 가 지운다. 결과는 `result_receiver` 와 무관하게 항상 REST 로 보낸다(MQTT·모르는 값 포함, app 세션 확인). Trivy·Grype 재스캔 0건, JDK 8·17 `mvn test` 결과 동일 (기존 실패 3건만) |
| [3-5~3-7](TASK_3-7_sast_triage.md) | 위협 모델, MQTT payload 검증, SAST 잔여 처리 | 완료 | 2026-09-30 | 위협 모델 [SPEC-001](../SPEC_001_threat_model.md). `OrderCaller` 가 구체 `Order` 만 실행, MQTT payload 1 MiB 상한, `SafeLogFormatter` 로 CRLF 164건 일괄 완화, `download_n_unzip` http/https 한정, `Locale.ROOT`. SpotBugs 327 → 285, 남은 보안 분류는 분류별 suppress 근거 기록 |
| 4, 5, 6(일부), 7 | Java 8/17 호환, 라이선스·SBOM, 커뮤니티 문서, CI | 완료 (확인 대기 항목 있음) | 2026-09-30 | 테스트 실패 0건(ExtractLog·SecurityValidator 테스트를 스펙에 맞춤, `isValidAbsolutePath` 는 상대 경로 거부), `--release 8`(pom profile·offline 스크립트, JDK 17 에서 `List.of` 거부 확인), JUnit 5.14.4·Mockito 4.11·Surefire 3.5.4·JaCoCo 0.8.12(기준 0.47). CI: build-test(8/17)·lib-integrity·secrets(gitleaks dir, `.gitleaksignore`)·deps(Trivy)·sast-spotbugs(기준선 필터, 새 경고 탐지 확인)·CodeQL, Dependabot(Java 11+ major 무시). NOTICE·THIRD_PARTY_LICENSES.md·licenses/·SBOM(CycloneDX 1.6, 재현 가능), SECURITY.md·CONTRIBUTING.md·CODE_OF_CONDUCT.md·템플릿. 미결: LICENSE 저작권자(app 과 결정), CoC 연락처, SECURITY 대응 기한, 6-4 저장소 설정, 6-5 README 정리(WS-1 과 함께), 4-4 TLS 1.3, OWASP DC(NVD 키) |
| 1-0 (Windows) | Windows 스크립트 확인 | 부분 완료 | 2026-09-30 | `verify-lib.ps1` 과 `download-dependencies.bat` 안의 PowerShell 정리 명령을 PowerShell 7(Docker `mcr.microsoft.com/powershell`)에서 실행: 정상·옛 jar 남음·해시 불일치·파일 누락·CRLF SHA256SUMS 5가지 모두 기대대로. **cmd.exe 자체(`.bat` 의 따옴표·변수 확장)와 Windows PowerShell 5.1 은 Windows 에서 확인 필요** |
| 4-4 | TLS 1.3 허용 검토 | 하지 않음 (결정) | 2026-09-30 | 사용자 결정: `TLSv1.2` 고정은 특정 사용자 요구이므로 유지한다 |
