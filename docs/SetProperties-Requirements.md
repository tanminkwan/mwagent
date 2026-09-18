# set_properties Agent Function 요구사항 정의서

> **목적**: mw-app(중앙 서버)에서 원격으로 Agent 의 `agent.properties` 항목을 변경(삭제/추가/수정)할 수 있는 Agent Function `set_properties` 정의
>
> **대상 독자**: MwManger Agent 개발팀, mw-app 개발팀
>
> **문서 버전**: 1.2 (2026-09-18) — 구현 완료, 실제 시그니처/검증 규칙 반영
>
> **대상 Agent 버전**: 0000.0010.0001

---

## 1. 개요

### 1.1 배경

현재 Agent 의 런타임 설정은 Agent 가 기동된 디렉터리의 `agent.properties` 파일에서만 읽힌다.
설정 항목을 바꾸려면 운영자가 각 서버에 직접 접속해 파일을 편집해야 하며, Agent 가 수백 대 규모로
배포된 환경에서는 운영 부담이 크다.

Agent 는 이미 `agent.properties` 의 단일 항목을 원자적으로 갱신하는 내부 기능
(`Config.updatePropertyInFile()`)을 보유하고 있으나, `token` 갱신 용도로만 내부에서 호출되고 있으며
외부 명령으로 노출되어 있지 않다.

### 1.2 목표

| 항목 | 내용 |
|------|------|
| **기능명** | `set_properties` |
| **실행 방식** | Agent Function (`exe_agent_func` Order → `AgentFuncFactory`) |
| **제공 기능** | `agent.properties` 항목의 삭제(delete) / 추가·수정(upsert) / 조회(빈 요청) |
| **반환값** | 처리 후 `agent.properties` 전체 내용(단, `token` 제외)의 JSON |
| **핵심 제약** | `token` 항목은 조회·변경·삭제 모두 금지. `token` 이외 항목은 민감 항목 포함 전부 대상 |

### 1.3 범위

**포함 (In Scope)**

- `set_properties` Agent Function 신규 구현
- `agent.properties` 파일에 대한 다건 delete / upsert 처리
- 처리 전 검증(validation) 및 전부-적용-또는-전부-거부(all-or-nothing) 보장
- `token` 항목 보호 (요청에 포함 시 에러, 결과에서 마스킹이 아닌 **완전 제외**)
- `token` 이외의 민감 항목(`client.keystore.password`, `truststore.password`, `mqtt_credential` 등)도
  delete / upsert 및 결과 JSON 반환 **대상에 포함** — 상세는 [8.2 설계 결정](#82-설계-결정) 결정 4
- 빈 요청(`{}`)을 통한 전체 설정 **조회** — 별도 `get_properties` 함수를 만들지 않음
- 결과 JSON 생성 및 기존 결과 전송 경로(`/api/v1/command/result`)를 통한 회신

**제외 (Out of Scope)**

- 변경된 설정의 런타임 즉시 반영(hot reload) — 상세는 [8.2 설계 결정](#82-설계-결정) 결정 1
- Agent 재기동 — 본 기능은 **재기동을 수행하지 않는다**. 필요 시 mw-app 이 기존
  `exe_shell` / `exe_script` 등 **별도 명령**으로 수행한다 ([8.2 설계 결정](#82-설계-결정) 결정 5)
- `agent.properties` 이외 파일(예: keystore, truststore)의 변경
- 설정 변경 이력 관리 / 롤백 UI — mw-app 측 책임

### 1.4 용어

| 용어 | 설명 |
|------|------|
| **mw-app** | Agent 에 명령을 내리고 결과를 수집하는 중앙 관리 서버 |
| **Agent Function** | `AgentFunc` 인터페이스를 구현하고 `AgentFuncFactory` 가 이름으로 생성하는 Agent 내장 기능 |
| **add param** | 명령 JSON 의 `additional_params` 필드. `CommandVO.getAdditionalParamsJson()` 으로 파싱됨 |
| **item** | `agent.properties` 의 key (예: `log_level`, `mqtt_enabled`) |
| **upsert** | 존재하면 update, 존재하지 않으면 insert |

---

## 2. 기능 개요

### 2.1 실행 흐름

```
┌────────────┐                                            ┌──────────────────┐
│   mw-app   │                                            │  MwManger Agent  │
└─────┬──────┘                                            └────────┬─────────┘
      │                                                            │
      │ 1. 명령 생성                                                │
      │    target_file_name = "set_properties"                     │
      │    additional_params = {"delete":[...], "upsert":[...]}    │
      │                                                            │
      │───────────── GET /api/v1/command (또는 MQTT) ──────────────>│
      │                                                            │
      │                                        2. ExeAgentFunc     │
      │                                           └> AgentFuncFactory
      │                                              └> SetPropertiesFunc
      │                                                            │
      │                                        3. 검증             │
      │                                           - JSON 스키마    │
      │                                           - token 포함 여부│
      │                                           - delete/upsert  │
      │                                             키 충돌        │
      │                                                            │
      │                                        4. 파일 원자적 갱신 │
      │                                           agent.properties │
      │                                           (.tmp → ATOMIC_MOVE)
      │                                                            │
      │                                        5. 결과 JSON 생성   │
      │                                           (token 제외)     │
      │                                                            │
      │<──────── POST /api/v1/command/result ──────────────────────│
      │          { is_normal, result_text, ... }                   │
      │                                                            │
```

### 2.2 클래스 배치

| 구분 | 클래스 / 파일 | 비고 |
|------|--------------|------|
| 신규 | `mwagent.agentfunction.SetPropertiesFunc` | `AgentFunc` 구현체 |
| 수정 | `mwagent.agentfunction.AgentFuncFactory` | `case "set_properties"` 추가 |
| 수정 | `mwagent.common.Config` | 다건 delete/upsert 및 전체 읽기 메서드 추가 |
| 수정 | `mwagent.common.Version` | `VERSION` 상수 갱신 (단일 소스) |

---

## 3. 인터페이스 정의

### 3.1 요청 (Command)

mw-app 이 내려주는 명령 JSON 의 관련 필드는 다음과 같다.

| 필드 | 값 | 필수 | 설명 |
|------|-----|------|------|
| `target_file_name` | `"set_properties"` | Y | Agent Function 이름 |
| `additional_params` | JSON Object (아래 3.2) | Y | 변경 요청 내용 |
| `target_file_path` | (사용 안 함) | N | 결과의 `key_value2` 로 그대로 회신됨 |
| `result_receiver` | `SERVER` / `KAFKA` / `SERVER_N_KAFKA` | N | 기본값 `SERVER` |

> **주의**: `Order.convertCommand()` 는 `additional_params` 문자열에 `<<KEY>>` / `{{KEY}}`
> 패턴이 있으면 Agent 의 환경변수 값으로 치환한다. 설정값에 해당 패턴이 들어가면 의도치 않게
> 치환되므로 mw-app 은 이 패턴 사용을 피하거나 의도적으로만 사용해야 한다.

### 3.2 additional_params 스키마

```json
{
  "delete": ["item명", "item명2"],
  "upsert": [
    {"item명3": "value3"},
    {"item명4": "value4"}
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `delete` | String 배열 | N | 삭제할 item 명 목록. 미지정 시 빈 배열로 간주 |
| `upsert` | Object 배열 | N | 각 원소는 `{"item명": "value"}` 형태의 단일 key Object |

**요청 예시 1 — 삭제와 변경 동시 수행**

```json
{
  "delete": ["kafka_broker_address", "log_dir"],
  "upsert": [
    {"log_level": "INFO"},
    {"command_check_cycle": "30"},
    {"mqtt_enabled": "true"}
  ]
}
```

**요청 예시 2 — 삭제만 수행**

```json
{ "delete": ["security.allowed_read_paths"] }
```

**요청 예시 3 — 추가만 수행**

```json
{ "upsert": [{"security.command_injection_check": "true"}] }
```

**요청 예시 4 — 조회 전용 (빈 요청)**

```json
{}
```

`delete` / `upsert` 가 모두 없거나 모두 빈 배열이면 파일을 변경하지 않고 현재 전체 설정을 반환한다.
즉 **빈 요청은 조회(get) 동작**이며, 이를 위한 별도 함수는 제공하지 않는다.
`{"delete": [], "upsert": []}` 도 동일하게 동작한다.

### 3.3 응답 (Result)

Agent 는 `ResultVO` 한 건을 생성하여 기존 결과 전송 경로로 회신한다.

| ResultVO 필드 | 전송 JSON 필드 | 성공 시 | 실패 시 |
|---------------|----------------|---------|---------|
| `isOk` | `is_normal` | `true` | `false` |
| `result` | `result_text` | 처리 후 전체 properties JSON (token 제외) | 에러 메시지 문자열 |
| `targetFileName` | `key_value1` | `"set_properties"` | 동일 |
| `targetFilePath` | `key_value2` | 명령의 `target_file_path` 그대로 | 동일 |

**성공 응답 예시 (`result_text`)**

```json
{
  "server_url": "https://app.mwm.local:20443/",
  "get_command_uri": "/api/v1/command",
  "post_agent_uri": "/api/v1/agent/agent",
  "log_level": "INFO",
  "host_name_var": "HOSTNAME",
  "user_name_var": "USER",
  "command_check_cycle": "30",
  "security.path_traversal_check": "true",
  "security.allowed_read_paths": "/home/hennry/projects",
  "mqtt_enabled": "true",
  "mqtt_broker_address": "tcp://localhost:1883",
  "mqtt_credential": "BGL7DzZjt0GU1OcCMtZthtnY"
}
```

**실패 응답 예시 (`result_text`)**

```
Error:TOKEN_NOT_ALLOWED - 'token' cannot be deleted or upserted.
```

---

## 4. 기능 요구사항

### FR-1: 명령 수신 및 파싱

| ID | 요구사항 |
|----|----------|
| FR-1.1 | `AgentFuncFactory.getAgentFunc("set_properties")` 는 `SetPropertiesFunc` 인스턴스를 반환해야 한다. |
| FR-1.2 | `SetPropertiesFunc` 는 `AgentFunc` 인터페이스를 구현하고 `Common.makeOneResultArray()` 로 단일 결과를 반환해야 한다. |
| FR-1.3 | `additional_params` 는 `CommandVO.getAdditionalParamsJson()` 으로 파싱한다. 결과가 `null`(미지정 또는 JSON 파싱 실패)이면 에러로 처리한다. |
| FR-1.4 | 최상위 JSON 은 Object 여야 하며, `delete` / `upsert` 이외의 필드는 무시한다(에러 아님). 단, 무시된 필드는 로그에 WARNING 으로 남긴다. |
| FR-1.5 | **조회 모드**: `delete` 와 `upsert` 가 모두 없거나 모두 빈 배열이면 **파일을 변경하지 않고**(임시 파일 생성·교체도 수행하지 않는다) 성공으로 처리하고 현재 전체 properties JSON 을 반환한다. 별도의 조회 전용 함수(`get_properties`)는 제공하지 않는다. |
| FR-1.6 | 조회 모드에서도 `token` 은 결과에서 제외한다(FR-6.2 와 동일). |

### FR-2: 입력 검증 (파일 변경 전 수행)

검증은 **파일을 건드리기 전에 모두 수행**하며, 하나라도 실패하면 어떤 변경도 적용하지 않는다.

| ID | 요구사항 |
|----|----------|
| FR-2.1 | `delete` 가 존재하면 String 배열이어야 한다. 배열이 아니거나 원소가 String 이 아니면 에러(`INVALID_PARAMS`). |
| FR-2.2 | `upsert` 가 존재하면 Object 배열이어야 하며, 각 원소는 key 를 **1개만** 가지는 Object 여야 한다. 위반 시 에러(`INVALID_PARAMS`). |
| FR-2.3 | item 명은 trim 후 빈 문자열이 아니어야 한다. 위반 시 에러(`INVALID_PARAMS`). |
| FR-2.4 | item 명에 개행(`\r`, `\n`)이 포함되면 에러(`INVALID_PARAMS`). properties 파일 구조 파괴 방지. |
| FR-2.4.1 | item 명에 `=` `:` `#` `!` `\` 또는 공백류 문자가 포함되면 에러(`INVALID_PARAMS`). key 는 이스케이프 없이 기록되므로, 예를 들어 `a=b` 라는 item 명은 `a=b=value` 라인이 되어 `Properties.load()` 가 key `a` / value `b=value` 로 잘못 읽는다. |
| FR-2.5 | **`token` 보호**: `delete` 목록 또는 `upsert` 의 key 에 `token` 이 포함되면 **에러(`TOKEN_NOT_ALLOWED`)** 를 반환하고 어떤 변경도 적용하지 않는다. 비교는 trim 후 **대소문자 무시**로 수행한다. |
| FR-2.6 | `upsert` 의 value 는 `null` 이 아니어야 한다. `null` 이면 에러(`INVALID_PARAMS`). 빈 문자열(`""`)은 허용하며 `key=` 형태로 기록한다. |
| FR-2.7 | `upsert` 의 value 가 String 이 아닌 경우(숫자, boolean 등) 문자열로 변환하여 처리한다. |
| FR-2.8 | 동일 item 이 `delete` 와 `upsert` 양쪽에 존재하면 에러(`CONFLICTING_KEYS`). 처리 순서에 따라 결과가 달라지는 모호함 방지. |
| FR-2.9 | `delete` 내 중복 item, `upsert` 내 동일 key 중복은 에러(`DUPLICATED_KEYS`)로 처리한다. |

### FR-3: delete 처리

| ID | 요구사항 |
|----|----------|
| FR-3.1 | 지정된 item 의 `key=value` 라인을 `agent.properties` 에서 제거한다. |
| FR-3.2 | 해당 item 이 파일에 **존재하지 않으면 무시**한다(에러 아님). |
| FR-3.3 | 삭제 대상 라인이 backslash 연속 라인(continuation line)을 가진 경우, 이어지는 연속 라인까지 함께 제거한다. |
| FR-3.4 | 삭제되지 않은 라인(주석, 빈 줄, 다른 항목)은 **순서와 내용이 그대로 보존**되어야 한다. |
| FR-3.5 | 동일 item 이 파일에 여러 번 기록되어 있으면 **모든 라인을 제거**한다. |

### FR-4: upsert 처리

| ID | 요구사항 |
|----|----------|
| FR-4.1 | item 이 존재하면 해당 라인의 value 만 교체한다(update). 라인 위치는 유지한다. |
| FR-4.2 | item 이 존재하지 않으면 파일 **끝에 추가**한다(insert). |
| FR-4.3 | value 는 `Properties.load()` 가 원문 그대로 읽을 수 있도록 이스케이프한다. 기존 `Config.escapePropertyValue()` 규칙(백슬래시, 개행, 탭, 선행 공백 이스케이프 + 비 ASCII 는 `\uXXXX`)을 재사용한다. |
| FR-4.4 | 주석(`#`, `!`)으로 시작하는 라인은 key 로 인식하지 않으며, 주석 처리된 동명 항목이 있어도 새 항목을 추가한다. |
| FR-4.5 | 동일 item 이 파일에 여러 번 기록되어 있으면 **첫 번째 라인만 갱신하고 이후 중복 라인은 제거**한다. `Properties.load()` 는 뒤에 나오는 항목이 이기므로, 중복을 남기면 방금 쓴 값이 무시된다. |

### FR-5: 원자성 및 동시성

| ID | 요구사항 |
|----|----------|
| FR-5.1 | delete 와 upsert 는 **단일 파일 쓰기 트랜잭션**으로 처리한다. 요청 내 일부만 적용되는 상태는 존재해서는 안 된다. |
| FR-5.2 | 파일 쓰기는 기존 방식과 동일하게 임시 파일 작성 후 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` 로 교체한다. `AtomicMoveNotSupportedException` 시 `REPLACE_EXISTING` 로 fallback 한다. |
| FR-5.3 | `token` 갱신(`GetRefreshToken`, `Common.updateToken()`)과 동일한 락(`Config.PROPERTY_FILE_LOCK`)을 사용하여 read-modify-write 구간을 보호한다. |
| FR-5.4 | 파일 인코딩 처리는 기존과 동일하게 ISO-8859-1 로 읽고 쓴다(바이트 보존). |
| FR-5.5 | 파일 쓰기 실패(IOException) 시 원본 파일은 변경 전 상태로 유지되어야 하며, 임시 파일은 삭제되어야 한다. |

### FR-6: 결과 반환

| ID | 요구사항 |
|----|----------|
| FR-6.1 | 성공 시 갱신 **후**의 `agent.properties` 를 다시 읽어 전체 항목을 JSON Object 로 직렬화하여 `result` 에 담는다. |
| FR-6.2 | 결과 JSON 에서 `token` 항목은 **키 자체가 존재하지 않아야** 한다(마스킹이 아닌 완전 제외). |
| FR-6.3 | 결과 JSON 의 모든 value 는 String 타입으로 표현한다(`"command_check_cycle": "30"`). |
| FR-6.4 | 성공 시 `ResultVO.isOk = true`, 실패 시 `false` 로 설정한다. |
| FR-6.5 | 실패 시에도 **예외를 밖으로 던지지 않고** `isOk=false` 인 `ResultVO` 를 반환해야 한다. `ExeAgentFunc` 는 예외 발생 시 `rtn = -1` 을 반환하여 **결과를 서버로 전송하지 않으므로**, 예외를 던지면 mw-app 이 실패 사실을 알 수 없다. |
| FR-6.6 | 모든 처리 결과(성공/실패, 적용된 item 목록)는 Logger 에 기록한다. `System.err` / `System.out` 사용 금지. |
| FR-6.7 | 로그에는 `token` 값을 절대 기록하지 않는다. |

---

## 5. 에러 정의

실패 시 `result_text` 는 `Error:<CODE> - <message>` 형식의 단일 문자열로 한다.

| 코드 | 발생 조건 | 메시지 예시 |
|------|-----------|-------------|
| `INVALID_PARAMS` | additional_params 누락, JSON 파싱 실패, 스키마 위반, 빈 item 명, 개행·구분자 포함 item 명, null value | `Error:INVALID_PARAMS - 'upsert' element must be a JSON object with exactly one key.` |
| `TOKEN_NOT_ALLOWED` | `delete` 또는 `upsert` 에 `token` 포함 | `Error:TOKEN_NOT_ALLOWED - 'token' cannot be deleted or upserted.` |
| `CONFLICTING_KEYS` | 동일 item 이 `delete` 와 `upsert` 양쪽에 존재 | `Error:CONFLICTING_KEYS - 'log_level' appears in both delete and upsert.` |
| `DUPLICATED_KEYS` | `delete` 또는 `upsert` 내 동일 item 중복 | `Error:DUPLICATED_KEYS - 'log_level' is specified more than once.` |
| `FILE_WRITE_ERROR` | agent.properties 읽기/쓰기 실패 | `Error:FILE_WRITE_ERROR - Failed to update agent.properties: <원인>` |

> `mwagent.vo.AgentErrorCode` 에 대응 항목이 있는 경우(`CMD_INVALID_PARAMS(2002)`, `FILE_WRITE_ERROR(4004)`,
> `CONFIG_INVALID(5000)`) 로그 기록 시 함께 남긴다. `TOKEN_NOT_ALLOWED` 는 신규 코드가 필요하므로
> `AgentErrorCode` 에 `CONFIG_PROTECTED_KEY(5003, "Protected configuration key")` 추가를 권장한다.

---

## 6. 비기능 요구사항

### NFR-1: 보안

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| NFR-1.1 | `token`(refresh token)은 어떤 경로로도 mw-app 에 회신되지 않아야 한다. | CRITICAL |
| NFR-1.2 | `token` 은 원격 명령으로 변경/삭제될 수 없어야 한다. 탈취된 명령 채널로 인한 인증 우회 방지. | CRITICAL |
| NFR-1.3 | **보호 대상은 `token` 단 하나다.** `client.keystore.password`, `truststore.password`, `mqtt_credential`, `server_url` 등 다른 모든 항목은 delete / upsert 및 결과 JSON 반환의 대상에 포함된다. 본 기능은 mw-app 이 이미 신뢰된 채널(mTLS / JWT 인증)로 접속한다는 전제 하에 동작한다. | HIGH |
| NFR-1.4 | item 명 및 value 에 개행을 삽입하여 다른 항목을 주입하는 공격(properties injection)이 차단되어야 한다. | HIGH |

### NFR-2: 신뢰성

| ID | 요구사항 |
|----|----------|
| NFR-2.1 | 처리 중 프로세스가 비정상 종료되어도 `agent.properties` 가 손상(절단/반쯤 쓰인 상태)되어서는 안 된다. |
| NFR-2.2 | 동시에 수행되는 token 갱신과 경합해도 양쪽 변경이 유실되지 않아야 한다. |

### NFR-3: 호환성

| ID | 요구사항 |
|----|----------|
| NFR-3.1 | Java 8 문법·API 만 사용한다(`Files.readString`, `String.isBlank` 등 사용 금지). |
| NFR-3.2 | 기존 `Config.updatePropertyLegacy()` / `updatePropertyInFile()` 시그니처와 동작은 변경하지 않는다(token 갱신 경로 보존). |
| NFR-3.3 | mTLS 모드와 Legacy 모드 양쪽에서 동일하게 동작해야 한다. |
| NFR-3.4 | JSON 직렬화는 기존 의존성인 `org.json.simple` 을 사용한다(신규 라이브러리 추가 금지). |

### NFR-4: 성능

| ID | 요구사항 |
|----|----------|
| NFR-4.1 | 1회 요청에서 delete + upsert 항목이 100건 이하일 때 파일 쓰기는 1회만 발생해야 한다. |

---

## 7. 처리 규칙 요약

| 상황 | 동작 |
|------|------|
| delete 대상 item 이 존재함 | 삭제 |
| delete 대상 item 이 존재하지 않음 | **무시** (성공) |
| upsert 대상 item 이 존재함 | value 갱신 (위치 유지) |
| upsert 대상 item 이 존재하지 않음 | 파일 끝에 추가 |
| item 명이 `token` | **에러**, 전체 요청 거부 |
| item 명이 `token` 이외의 민감 항목 (`*.password`, `mqtt_credential` 등) | **정상 처리**, 결과 JSON 에도 값 포함 |
| delete / upsert 모두 비어 있음 (`{}` 포함) | 파일 변경 없이 현재 내용 반환 (**조회 모드**, 성공) |
| delete 와 upsert 에 동일 item | **에러**, 전체 요청 거부 |
| 검증 실패 항목이 1건이라도 존재 | **에러**, 전체 요청 거부 (부분 적용 없음) |

---

## 8. 제약 조건 및 설계 결정

### 8.1 기술 제약

| 제약 | 사유 |
|------|------|
| Gradle 사용 불가, Maven 사용 | 환경의 proxy/SSL 문제 |
| 버전은 `Version.java` 의 `VERSION` 상수에서만 관리 | 단일 소스 원칙 |
| 모든 로그는 파일로 기록 | daemon 프로세스 |
| `agent.properties` 는 Agent 작업 디렉터리 기준 상대 경로 고정 | `Config.setConfig()` 의 기존 동작과 일치 |

### 8.2 설계 결정

**결정 1 — 변경 사항의 런타임 반영은 하지 않는다 (재기동 필요)**

`set_properties` 는 **파일만** 갱신하고, 메모리상의 `Config` 싱글톤은 갱신하지 않는다.

- 사유 1: `Config.setConfig()` 는 설정 로드와 동시에 로거 재구성 및 `Common.updateToken()` 을 수행하며,
  실패 시 `System.exit(0)` 을 호출한다. 명령 처리 중 재호출하면 Agent 가 종료될 수 있다.
- 사유 2: `mqtt_*`, `kafka_broker_address`, `use_mtls` 등은 기동 시점에 스레드·커넥션을 구성하므로
  값만 바꿔도 실제 동작은 바뀌지 않는다. 부분 반영은 파일과 메모리가 불일치하는 혼란을 만든다.
- 결론: mw-app 은 설정 변경 후 필요 시 **별도 재기동 명령**을 내리는 것을 전제로 한다(결정 5).
  이 사실을 운영자에게 명시해야 한다.

**결정 2 — 실패 시 전체 거부 (all-or-nothing)**

부분 적용은 mw-app 이 Agent 의 실제 상태를 추적할 수 없게 만든다. 검증을 파일 접근 전에 모두 수행하여
"검증 통과 → 반드시 전체 적용" 을 보장한다.

**결정 3 — `token` 은 결과에서 마스킹이 아닌 완전 제외**

`"token": "***"` 형태로 남기면 mw-app 이 해당 키의 존재/부재를 설정 동기화 로직에 반영할 수 있어
혼란을 유발한다. 키 자체를 제외하여 "Agent 설정에 token 이라는 항목은 없다" 는 일관된 뷰를 제공한다.

**결정 4 — 보호 항목은 `token` 하나뿐이며, 나머지 민감 항목은 모두 대상에 포함한다**

`client.keystore.password`, `truststore.password`, `mqtt_credential` 등 민감 항목도
delete / upsert 가 가능하고 결과 JSON 에도 값이 그대로 포함된다.

- 사유 1: 이들 항목은 운영상 원격 변경 필요성이 실재한다(인증서 교체, MQTT 자격증명 로테이션 등).
  제외하면 기능의 효용이 크게 떨어진다.
- 사유 2: `token` 만 특별한 이유는 값이 아니라 **역할** 때문이다. `token`(refresh token)은 Agent 가
  mw-app 에 대해 자신을 증명하는 수단이므로, 명령 채널을 통해 이를 읽거나 바꿀 수 있으면
  명령 채널 탈취가 곧 인증 우회로 이어진다. 다른 민감 항목에는 이 순환 구조가 없다.
- 전제: mw-app ↔ Agent 구간은 mTLS 또는 JWT 로 보호된 신뢰 채널이며, mw-app 측에서 해당 결과를
  저장·표시할 때의 마스킹은 **mw-app 의 책임**이다.
- 부수 효과: `server_url` 을 잘못된 값으로 upsert 하면 재기동 후 Agent 가 mw-app 과 통신하지
  못하고 원격 복구가 불가능해진다. mw-app 은 해당 항목 변경 시 운영자 확인 절차를 두는 것을 권장한다.

**결정 5 — 재기동은 본 기능에 포함하지 않는다**

`set_properties` 는 파일 갱신과 결과 회신만 수행하고 종료한다. Agent 프로세스를 재기동하지 않으며,
재기동을 트리거하는 파라미터도 제공하지 않는다.

- 사유 1: 단일 책임. 설정 변경과 프로세스 생명주기 제어를 한 명령에 묶으면, 재기동 실패 시
  설정은 바뀌었는데 결과는 실패로 회신되는 등 결과 해석이 모호해진다.
- 사유 2: 재기동 시점에 결과 전송(`POST /api/v1/command/result`)이 유실될 수 있다.
- 사유 3: 재기동 방식은 환경별로 다르다(systemd, 스크립트, 수동). Agent 가 일반화할 수 없다.
- 운영 절차: mw-app 은 `set_properties` 성공 확인 → 필요 시 `exe_shell` / `exe_script` 로
  재기동 명령을 **별도 발행**한다.

---

## 9. 산출물 정의

### 9.1 소스코드

| 구분 | 대상 | 설명 |
|------|------|------|
| 신규 | `src/main/java/mwagent/agentfunction/SetPropertiesFunc.java` | 파싱·검증·결과 생성 |
| 수정 | `src/main/java/mwagent/agentfunction/AgentFuncFactory.java` | `case "set_properties"` 추가 |
| 수정 | `src/main/java/mwagent/common/Config.java` | 다건 delete/upsert 메서드, 전체 properties 조회 메서드 추가 |
| 수정 | `src/main/java/mwagent/vo/AgentErrorCode.java` | `CONFIG_PROTECTED_KEY` 추가 (권장) |
| 수정 | `src/main/java/mwagent/common/Version.java` | `VERSION` = `0000.0010.0001` |

**신규 메서드 시그니처 (Config) — 구현됨**

```java
/** agent.properties 에 delete/upsert 를 적용하고 결과 전체를 순서 보존 Map 으로 반환 */
public Map<String, String> applyAndReadProperties(List<String> deleteKeys,
                                                  Map<String, String> upsertPairs) throws IOException;

/** 위 메서드의 파일 명시 변형. 테스트가 임시 파일을 대상으로 호출한다 */
public static Map<String, String> applyAndReadProperties(File file, List<String> deleteKeys,
                                                         Map<String, String> upsertPairs) throws IOException;

/** 패키지 가시성, 단위 테스트 대상: 다건 delete/upsert 를 단일 원자적 쓰기로 적용 */
static void applyPropertiesInFile(File file, List<String> deleteKeys,
                                  Map<String, String> upsertPairs) throws IOException;

/** 패키지 가시성: 파일 전체를 순서 보존 Map 으로 읽는다. token 필터링은 호출자 책임 */
static Map<String, String> readAllProperties(File file) throws IOException;

/** 기존 updatePropertyInFile 과 공유하는 임시 파일 + ATOMIC_MOVE 쓰기 헬퍼 */
private static void writeAtomically(File file, String content, Charset cs) throws IOException;
```

쓰기와 읽기가 하나의 `applyAndReadProperties` 안에서 같은 `PROPERTY_FILE_LOCK` 으로 묶인다.
따로 두면 token 갱신이 그 사이에 끼어들어, 반환된 내용이 실제 파일과 달라질 수 있다.
빈 요청일 때는 `applyPropertiesInFile` 을 아예 호출하지 않으므로 임시 파일 생성도 없다(FR-1.5).

**테스트 seam (SetPropertiesFunc)**

```java
/** 테스트가 임시 파일로 우회할 수 있게 파일 접근을 한 겹 감싼 지점 */
Map<String, String> applyAndRead(List<String> deleteKeys, Map<String, String> upsertPairs) throws Exception;
```

### 9.2 테스트 코드

| 유형 | 대상 | 파일 |
|------|------|------|
| 단위 테스트 | `SetPropertiesFunc` 파싱·검증·결과 JSON (33건) | `src/test/java/mwagent/agentfunction/SetPropertiesFuncTest.java` |
| 단위 테스트 | `Config` 다건 delete/upsert 파일 조작 (18건) | `src/test/java/mwagent/common/ConfigApplyPropertiesTest.java` |
| 단위 테스트 | Factory 매핑 | `SetPropertiesFuncTest.factoryResolvesTheFunction()` |

### 9.3 문서

| 대상 | 갱신 내용 |
|------|-----------|
| `CLAUDE.md` | Recent Fixes 에 `set_properties` 추가 |
| `docs/PROJECT_REPORT.md` | Agent Function 목록에 `set_properties` 추가 |

---

## 10. 테스트 케이스

| ID | 시나리오 | 입력 | 기대 결과 |
|----|----------|------|-----------|
| TC-01 | 정상 upsert (기존 항목 수정) | `{"upsert":[{"log_level":"INFO"}]}` | `isOk=true`, 파일의 `log_level=INFO`, 다른 라인·주석 보존 |
| TC-02 | 정상 upsert (신규 항목 추가) | `{"upsert":[{"new_item":"v"}]}` | `isOk=true`, 파일 끝에 `new_item=v` 추가 |
| TC-03 | 정상 delete (존재하는 항목) | `{"delete":["log_level"]}` | `isOk=true`, 해당 라인 제거 |
| TC-04 | delete (존재하지 않는 항목) | `{"delete":["no_such_item"]}` | `isOk=true`, 파일 무변경 |
| TC-05 | delete + upsert 혼합 | `{"delete":["a"],"upsert":[{"b":"2"}]}` | `isOk=true`, 양쪽 모두 반영 |
| TC-06 | **token delete 시도** | `{"delete":["token"]}` | `isOk=false`, `TOKEN_NOT_ALLOWED`, **파일 무변경** |
| TC-07 | **token upsert 시도** | `{"upsert":[{"token":"x"}]}` | `isOk=false`, `TOKEN_NOT_ALLOWED`, **파일 무변경** |
| TC-08 | token 대소문자 변형 | `{"delete":["TOKEN"]}` / `{"delete":[" token "]}` | `isOk=false`, `TOKEN_NOT_ALLOWED` |
| TC-09 | token 과 정상 항목 혼합 | `{"upsert":[{"log_level":"FINE"},{"token":"x"}]}` | `isOk=false`, **log_level 도 변경되지 않음** (전체 거부) |
| TC-10 | delete/upsert 키 충돌 | `{"delete":["a"],"upsert":[{"a":"1"}]}` | `isOk=false`, `CONFLICTING_KEYS` |
| TC-11 | 중복 키 | `{"delete":["a","a"]}` | `isOk=false`, `DUPLICATED_KEYS` |
| TC-12 | additional_params 없음 | `null` | `isOk=false`, `INVALID_PARAMS` |
| TC-13 | 잘못된 JSON | `"{invalid"` | `isOk=false`, `INVALID_PARAMS` |
| TC-14 | upsert 원소가 다중 key | `{"upsert":[{"a":"1","b":"2"}]}` | `isOk=false`, `INVALID_PARAMS` |
| TC-15 | upsert value 가 null | `{"upsert":[{"a":null}]}` | `isOk=false`, `INVALID_PARAMS` |
| TC-16 | item 명에 개행 포함 | `{"upsert":[{"a\nb":"1"}]}` | `isOk=false`, `INVALID_PARAMS` |
| TC-17 | 빈 요청 = 조회 모드 | `{}` 또는 `{"delete":[],"upsert":[]}` | `isOk=true`, 파일 **최종수정시각 불변**, 전체 JSON 반환 |
| TC-18 | 결과 JSON 에 token 미포함 | 임의 정상 요청 + 조회 모드 요청 | 두 경우 모두 `result_text` 파싱 결과에 `token` 키 없음 |
| TC-23 | 민감 항목 upsert 허용 | `{"upsert":[{"mqtt_credential":"newSecret"}]}` | `isOk=true`, 파일 반영, 결과 JSON 에 `mqtt_credential=newSecret` 포함 |
| TC-24 | 민감 항목 delete 허용 | `{"delete":["client.keystore.password"]}` | `isOk=true`, 해당 라인 제거 |
| TC-25 | 민감 항목 결과 노출 | 조회 모드 요청 | 결과 JSON 에 `truststore.password` 등 값이 마스킹 없이 포함 |
| TC-26 | 재기동 미수행 | 임의 정상 요청 | 프로세스가 종료되지 않고 이후 명령을 계속 처리, `Config` 메모리 값은 기존 값 유지 |
| TC-27 | item 명에 구분자 포함 | `{"upsert":[{"a=b":"1"}]}`, `a:b`, `a b`, `#a`, `a\b` | `isOk=false`, `INVALID_PARAMS` (illegal character), 파일 무변경 |
| TC-28 | 파일 내 중복 item upsert | `log_level` 이 두 줄인 파일에 upsert | 첫 줄만 갱신되고 뒤쪽 중복 줄은 제거, `Properties.load()` 결과가 새 값 |
| TC-29 | 파일 내 중복 item delete | `log_level` 이 두 줄인 파일에 delete | 두 줄 모두 제거 |
| TC-30 | CRLF 파일 | CRLF 파일에 upsert + insert | 줄바꿈이 CRLF 로 유지 |
| TC-31 | 마지막 줄에 개행 없는 파일 | `a=1` (개행 없음) 에 `b` insert | `a=1\nb=2\n` |
| TC-32 | 임시 파일 잔여물 | 임의 정상 요청 | 디렉터리에 `.tmp` 파일이 남지 않음 |
| TC-33 | token 갱신 경로 공존 | `updatePropertyInFile(token)` 후 batch 적용 | 양쪽 변경이 모두 반영 |

### 10.1 실행 결과 (2026-09-18)

이 환경에서는 `tools/apache-maven-3.9.6` 이 기동되지 않아, 레포에 포함된
`temp_jdk/jdk8u482-b08` 로 컴파일하고 JUnit5 엔진을 직접 구동해 실행했다.

| 구분 | 결과 |
|------|------|
| 신규 테스트 (`SetPropertiesFuncTest` + `ConfigApplyPropertiesTest`) | **51건 전체 통과** |
| 전체 스위트 (39개 테스트 클래스) | 312건 통과 / 6건 실패 |
| HEAD 기준 baseline 전체 스위트 | 255건 통과 / **동일한 6건 실패** |

실패 6건(`SSLCertiFileFuncTest` 3건, `SecurityValidatorTest` 2건, `ExtractLogTest` 1건)은
본 변경 이전부터 실패하던 환경 의존 테스트로, baseline 과 동일하다.
Windows 환경에서 `mvn test` 로 재확인이 필요하다.
| TC-19 | 값에 비 ASCII / 특수문자 | `{"upsert":[{"a":"값=1\\n2"}]}` | 이스케이프되어 기록되고, 재로드 시 원문 복원 |
| TC-20 | 연속 라인(continuation) 항목 delete | `a=1\\`+`2` 형태 | 연속 라인까지 제거 |
| TC-21 | 주석·빈 줄 보존 | 주석 포함 파일에 upsert | 주석과 빈 줄의 위치·내용 그대로 유지 |
| TC-22 | Factory 매핑 | `getAgentFunc("set_properties")` | `SetPropertiesFunc` 인스턴스 반환 |

---

## 11. 결정 완료 사항

| ID | 내용 | 결정 (2026-09-18) | 반영 위치 |
|----|------|-------------------|-----------|
| D-1 | 민감 항목의 결과 JSON 포함 여부 | **`token` 을 제외한 민감 항목은 모두 포함**. 별도 제외/마스킹 목록을 두지 않는다. | NFR-1.3, 결정 4, TC-23~25 |
| D-2 | `token` 외 변경 금지 항목 | **없음**. 보호 대상은 `token` 하나뿐이며, `server_url` 등도 변경 가능. 잘못된 값으로 인한 통신 불가 위험은 mw-app 측 운영 절차로 관리한다. | FR-2.5, 결정 4 |
| D-3 | 설정 변경 후 재기동 | **본 기능에 포함하지 않음**. mw-app 이 `exe_shell` / `exe_script` 등 별도 명령으로 수행한다. | 1.3 범위, 결정 5, TC-26 |
| D-4 | 조회 전용 함수(`get_properties`) 신설 여부 | **신설하지 않음**. `set_properties` 에 빈 요청(`{}`)을 보내면 조회로 동작한다. | FR-1.5, FR-1.6, 3.2 요청 예시 4, TC-17 |

### 11.1 mw-app 측 유의 사항

| 항목 | 내용 |
|------|------|
| 민감값 취급 | 결과 JSON 에 패스워드·자격증명이 평문으로 담긴다. **저장·로그·화면 표시 시 마스킹은 mw-app 책임**이다. |
| 위험 항목 변경 | `server_url`, `use_mtls`, `client.keystore.path`, `truststore.path` 변경은 재기동 후 Agent 가 mw-app 에 접속하지 못하게 만들 수 있다. 운영자 확인 절차 권장. |
| 반영 시점 | 변경은 파일에만 적용된다. 실제 동작 반영은 Agent 재기동 이후다. |
| 재기동 절차 | `set_properties` 성공(`is_normal=true`) 확인 → 필요 시 재기동 명령 별도 발행. |
| 조회 활용 | 변경 전후 비교가 필요하면 빈 요청으로 현재 값을 먼저 조회한다. |

---

## 12. 참고

| 문서/파일 | 내용 |
|-----------|------|
| `src/main/java/mwagent/common/Config.java` | `updatePropertyInFile()`, `parsePropertyKey()`, `escapePropertyValue()` — 재사용 대상 |
| `src/main/java/mwagent/agentfunction/DownloadNUnzipFunc.java` | additional_params 파싱 및 ResultVO 반환 패턴 참고 |
| `src/main/java/mwagent/order/ExeAgentFunc.java` | 예외 발생 시 결과 미전송 동작 (FR-6.5 근거) |
| `src/test/java/mwagent/common/ConfigUpdatePropertyTest.java` | 기존 properties 파일 조작 테스트 패턴 |
| `agent.properties` | 현행 설정 항목 목록 |
