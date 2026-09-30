## 변경 내용

## 이유

## 확인

- [ ] `mvn -B verify` 통과 (JDK 8 / 17)
- [ ] 동작 변경·버그 수정에 테스트 추가 (보안 수정은 테스트 먼저)
- [ ] Java 9+ API 를 쓰지 않음, 새 의존성은 Java 8 지원 버전 (CONTRIBUTING.md)
- [ ] 의존성을 바꿨다면 `pom.xml`, `download-dependencies.*`, `lib/SHA256SUMS`, `THIRD_PARTY_LICENSES.md`, SBOM 을 함께 갱신
- [ ] 비밀값·내부 주소·실제 식별자를 넣지 않음
