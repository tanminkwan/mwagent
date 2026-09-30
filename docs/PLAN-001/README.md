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
