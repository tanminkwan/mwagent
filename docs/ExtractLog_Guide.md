# ExtractLog 기능 사용자 가이드

`ExtractLog`는 분산 서버 환경에서 특정 로그 파일 내의 지정된 시간대 및 키워드를 포함하는 로그 블록(스택트레이스 포함)을 추출하고, 동일한 메시지가 몇 번 발생했는지 집계해 주는 기능입니다.

## 1. 개요
* **클래스명**: `mwagent.order.ExtractLog`
* **주요 기능**:
  * 특정 시간대(`start` ~ `end`) 필터링
  * 원하는 에러 키워드 다중 필터링 지원 (정규식 지원)
  * Multi-line 로그(예: Java Exception StackTrace)를 하나의 블록으로 인식하여 추출
  * 중복된 로그 블록은 최초 1회만 본문 텍스트를 전송하고 **발생 횟수(count)**를 함께 반환하여 네트워크 페이로드 감소 및 분석 편의 제공

---

## 2. 파라미터 설정 가이드

서버(REST 폴링 또는 MQTT)를 통해 에이전트(Agent)로 `ExtractLog` 명령을 보낼 때, 아래와 같이 CommandVO 규격에 맞춰 파라미터를 전송해야 합니다.

| 파라미터명 | 설명 | 예시 |
|---|---|---|
| `target_object` | 실행할 클래스명 | `mwagent.order.ExtractLog` |
| `target_file_path` | 로그 파일이 위치한 디렉토리 절대 경로 | `/var/log/app/` |
| `target_file_name` | 타겟 로그 파일명 | `server.log` |
| `additional_params` | 추출 옵션 정보 (JSON 형태의 문자열로 인코딩하여 전송) | (하단 참조) |

### 2.1 `additional_params` 상세 스펙
`additional_params`는 JSON 형식의 데이터를 문자열(String)로 이스케이프 처리하여 전송해야 합니다. 파싱 전 순수 JSON 구조는 다음과 같습니다.

```json
{
  "file": "/var/log/app/server.log",
  "targetDate": "20260825",
  "startTime": "140439",
  "endTime": "140443",
  "dateRegex": [
    {
      "regex": "\\[(\\d{4}\\.\\d{2}\\.\\d{2}) \\[[\\w-]+\\] (\\d{2}:\\d{2}:\\d{2})\\]",
      "dateFormat": "yyyy.MM.dd",
      "timeFormat": "HH:mm:ss"
    },
    {
      "regex": "\\[(\\d{2}:\\d{2}:\\d{2})\\]",
      "timeFormat": "HH:mm:ss"
    }
  ],
  "keywords": ["Exception", "Fail"],
  "abbreviatePrefix": "\tat ",
  "charset": "EUC-KR"
}
```

* **`file`** (선택): 대상 로그 파일의 절대 경로와 파일명이 합쳐진 전체 경로입니다. 이 값이 존재하면 기존 `target_file_path` 및 `target_file_name` 설정값을 무시하고 이 경로의 파일을 읽어옵니다.
* **`targetDate`** (필수): 조회할 날짜 (형식: `yyyyMMdd`). **이 기능은 정해진 하루에 대한 로그만 수집하도록 설계되었습니다.** 자정을 넘어 교차 조회(예: 밤 11시 ~ 새벽 1시)가 필요한 경우, 어제 날짜와 오늘 날짜로 에이전트를 두 번 호출해야 합니다.
* **`startTime`** (필수): 조회 시작 시각 (형식: `HHmmss`). 반드시 `endTime`보다 작거나 같아야 합니다.
* **`endTime`** (필수): 조회 종료 시각 (형식: `HHmmss`)
* **`dateRegex`** (필수): 각 로그 라인의 선두에서 날짜와 시간을 추출하기 위한 **객체 배열(JSON Array of Objects)**입니다. 여러 포맷이 혼재된 로그 파일을 지원하기 위해 복수의 규칙을 정의할 수 있습니다.
  * 각 객체는 다음 세 가지 속성을 가질 수 있습니다.
    * **`regex` (필수)**: 날짜/시간을 캡처할 정규식. 노이즈(스레드 ID 등)를 건너뛰려면 해당 부분은 캡처 그룹 밖에 두세요.
    * **`dateFormat` (선택)**: 정규식 내 첫 번째 캡처 그룹(일자)을 파싱할 포맷 (기본값: `"yyyyMMdd"`)
    * **`timeFormat` (선택)**: 정규식 내 시간 캡처 그룹을 파싱할 포맷 (기본값: `"HH:mm:ss"`)
  * **전략 1 (일자와 시간이 모두 있는 경우)**: `regex` 내에 **반드시 2개의 캡처 그룹 `()`**을 만들어야 합니다. 1번 그룹은 `dateFormat`, 2번 그룹은 `timeFormat`으로 자동 매핑되어 파싱됩니다.
  * **전략 2 (시간만 있는 경우)**: `regex` 내에 **단 1개의 캡처 그룹 `()`**만 만들어야 합니다. 이 그룹은 무조건 `timeFormat`으로 매핑되어 파싱되며, 일자는 롤오버 로직을 거쳐 `targetDate`가 자동으로 결합됩니다.
  * **중복 카운트(Count)의 핵심 원리**: 에이전트는 중복 에러를 묶어서(`count`) 반환할 때, 첫 줄에서 **`regex`에 매칭된 전체 문자열(노이즈 포함)을 잘라낸 나머지 부분**을 기준으로 비교합니다. 따라서 잘라내고 싶은 노이즈는 반드시 정규식 패턴에 포함시켜야 합니다.
  * **주의사항**: 추출 목적 외의 노이즈 부분에는 절대 캡처 그룹 `()`을 씌우면 안 됩니다. (단순 그룹화가 필요하다면 `(?:...)`를 사용하세요.)
* **안전 장치 (Fail-Safe)**: 정규식 포맷 설정 오류로 인해 수많은 로그가 하나의 블록으로 무한정 뭉쳐지거나(Multi-line 폭주), 단일 예외 스택트레이스가 비정상적으로 길게 출력되는 경우를 대비하여 **하나의 로그 블록은 최대 300줄까지만 수집**됩니다. 300줄을 초과하는 본문 내용은 자동으로 무시되어 시스템 메모리 과부하를 방지합니다.
* **`keywords`** (선택): 필터링할 키워드 목록(배열)입니다.
  * 대소문자를 구분하지 않고 검색합니다.
  * 배열 내 하나의 문자열로 정규식 OR 조건을 넣는 것도 가능합니다. (예: `["Exception|Fail"]`)
  * 키워드 조건을 넣지 않거나 빈 배열(`[]`)을 넣으면 지정된 시간 범위 안의 **모든 로그**를 추출합니다.
* **`abbreviatePrefix`** (선택): 반복되는 로그 구문을 축약하기 위한 접두사 문자열입니다. (예: `"\tat "`) 스택트레이스 등 해당 문자열로 시작하는 라인이 연속으로 반복될 경우, 첫 라인과 마지막 라인만 표시하고 중간은 `"..."`으로 축약하여 로그의 길이를 줄입니다.
* **`charset`** (선택): 로그 파일을 읽을 때 사용할 인코딩 방식입니다. (예: `EUC-KR`, `MS949`, `UTF-8` 등). 입력하지 않으면 기본값인 `UTF-8`이 사용됩니다. AIX나 Windows 등에서 인코딩 불일치로 한글이 깨질 경우 이 값을 명시하여 해결할 수 있습니다.

---

## 3. Command Request JSON 예시

API 또는 MQTT 메시지로 명령을 발송할 때의 페이로드 예시입니다. `additional_params`의 겹따옴표(`"`)를 이스케이프(`\"`) 해야 함에 주의하세요.

```json
{
  "command_id": "CMD-LOG-1001",
  "target_object": "mwagent.order.ExtractLog", 
  "target_file_path": "/var/log/app/",
  "target_file_name": "server.log",
  "additional_params": "{\"file\":\"/var/log/app/server.log\",\"targetDate\":\"20260825\",\"startTime\":\"140439\",\"endTime\":\"140443\",\"dateRegex\":[{\"regex\":\"\\\\[(\\\\d{4}\\\\.\\\\d{2}\\\\.\\\\d{2}) \\\\[[\\\\w-]+\\\\] (\\\\d{2}:\\\\d{2}:\\\\d{2})\\\\]\",\"dateFormat\":\"yyyy.MM.dd\",\"timeFormat\":\"HH:mm:ss\"},{\"regex\":\"\\\\[(\\\\d{2}:\\\\d{2}:\\\\d{2})\\\\]\",\"timeFormat\":\"HH:mm:ss\"}],\"keywords\":[\"Exception\",\"Fail\"],\"abbreviatePrefix\":\"\\tat \",\"charset\":\"EUC-KR\"}",
  "result_receiver": "SERVER"
}
```

---

## 4. 실행 결과 (Result) 

명령이 정상적으로 수행되면, Agent는 `ResultVO` 객체를 통해 `result_text` 필드에 **JSON Array 문자열** 형식으로 추출 결과를 반환합니다.

### 4.1 반환값(`result_text`) 형식

```json
[
  {
    "text": "[2026.08.25 14:04:39] [ERROR] [Thread-1] ...\njava.lang.NullPointerException\n\tat com.example.MyService.doWork(MyService.java:42)",
    "count": 5
  },
  {
    "text": "[2026.08.25 14:04:41] [ERROR] [Thread-2] ...\njava.sql.SQLException: Access denied for user",
    "count": 2
  }
]
```

* **`text`**: 첫 번째 줄(타임스탬프)부터 스택트레이스 끝까지 이르는 하나의 "로그 블록" 전체 텍스트입니다. (최초 발생한 메시지의 텍스트가 저장됩니다)
* **`count`**: 해당 시간대 안에서 동일한 패턴(타임스탬프 이후 본문 내용 기준)의 에러가 발생한 총 횟수입니다.

> **💡 트러블슈팅 (Troubleshooting)**: 추출된 `text` 결과를 확인했을 때, **서로 다른 여러 개의 로그가 하나의 거대한 텍스트 블록으로 뭉쳐져서(Multi-line) 출력되고 `count`가 비정상적으로 낮게 나온다면?**
> 👉 이는 설정하신 `dateRegex`나 `dateFormat`이 실제 로그와 맞지 않아 날짜/시간 파싱(`Parsing`)에 실패했음을 의미합니다. 시스템은 파싱에 실패한 라인을 새로운 로그의 시작이 아닌 '이전 로그의 연속된 본문(예: 스택트레이스)'으로 간주하고 계속 이어 붙이게 됩니다. 이 경우 정규식 캡처 그룹과 포맷 문자열이 올바른지 다시 한번 확인하세요.

> **참고**: 조건에 만족하는 로그가 없거나 에러가 발생한 경우, 결과 배열이 빈 배열 `[]` 로 리턴되거나 "Error: ..." 형태의 문자열이 리턴될 수 있습니다. (예: 파일이 없을 경우 `Error: NoSuchFileException`)
