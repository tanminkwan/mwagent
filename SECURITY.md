# Security Policy

## 취약점 신고

**공개 이슈로 올리지 마세요.** GitHub 의 비공개 신고 기능을 씁니다:
저장소의 **Security** 탭 → **Report a vulnerability** (Private vulnerability reporting).

신고에 다음을 적어 주세요.

- 영향받는 버전 (`Version.java` 의 `VERSION`, 또는 시작 로그의 `MWM Agent version`)
- 재현 절차와 설정 (`agent.properties` 는 비밀값을 지우고)
- 예상 영향 (예: 원격 명령 실행, token 노출)

## 지원 버전

| 버전 | 보안 수정 |
|------|-----------|
| 최신 minor (`0000.0011.x`) | 지원 |
| 그 이전 | 지원하지 않음 — 최신 버전으로 올려 주세요 |

## 대응

| 단계 | 목표 |
|------|------|
| 접수 확인 | 7일 이내 |
| 영향 판단 결과 공유 | 14일 이내 |
| 수정 배포 | 심각도에 따라 협의 (Critical·High 는 우선 처리) |

수정이 배포되면 GitHub Security Advisory 로 공개하고, 신고자가 원하면 이름을 적습니다.

## 범위

agent 는 **서버가 보낸 명령을 실행하는 것이 본래 기능**입니다. 서버·MQTT 브로커를 신뢰하는 설계와
그에 따른 위험은 [위협 모델 (SPEC-001)](docs/SPEC_001_threat_model.md)에 있습니다.

- 범위 안: 신뢰 경계 밖의 입력(서버 응답의 파일명·압축 항목, 네트워크 구간, MQTT payload 형식 등)으로
  일어나는 문제, 비밀값 노출, 검증 우회
- 범위 밖: 서버나 브로커가 이미 침해된 상태에서 명령을 실행시키는 것 (SPEC-001 §3), `ssl_verify=false`(기본값)
  에서의 중간자 공격 — 운영에서는 `ssl_verify=true` 를 권장합니다
