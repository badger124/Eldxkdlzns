# 자동 농사/상자 투입 모드 한글 안내서

## 1) 모드 개요
이 모드는 클라이언트에서 동작하는 자동화 보조 모드입니다.
핵심 목표는 **농사 자동 진행 + 인벤토리 가득 참 감지 + 등록 상자 자동 투입 + 농사 재개**입니다.

동작 핵심은 `ExampleModClient`가 입력/이벤트를 받아 `AutoFarmController` 상태머신으로 위임하는 구조입니다.

## 2) 키 바인딩
- `F1`: 자동 농사 시작 (마을 서버에서만 허용)
- `F2`: 자동 농사/투입 중지
- `F3`: 열린 상자 기준 투입 dry-run
- `F6`: 서버 타입 인식 테스트
- `F7`: 탭(raw) 문자열 콘솔 출력
- `F8`: 바라보는 저장 블록(상자/덫상자/배럴) 등록
- `F9`: 등록 상자 목록 출력
- `F10`: 등록 상자 전체 초기화 + 저장 파일 삭제
- `F11`: 인벤토리 상태 검사
- `F12`: 열린 컨테이너 슬롯 구조 검사

## 3) 상자 등록/저장 동작
- 등록 가능한 블록: 상자, 덫 상자, 배럴
- 최대 등록 개수: 10개
- 저장 위치: `config/farm_chests.txt`
- 게임 재시작 후에도 파일에서 다시 로드됩니다.

등록/목록/초기화 로직은 `ChestRegistry`가 담당합니다.

## 4) 서버 타입 감지 동작
`ServerDetector`는 두 경로를 조합해 서버 타입을 판별합니다.
1. 탭 리스트에서 내 닉네임이 포함된 채널 라인
2. 최근 수신 텍스트(짧은 시간 보관)

판별 타입:
- `TOWN`, `SPAWN`, `ISLAND`, `WILD`, `AFK`, `LOBBY`, `UNKNOWN`

자동 농사 시작(F1)은 `TOWN`에서만 허용됩니다.

## 5) 자동 농사/투입 상태 흐름
`AutoFarmController` 상태머신:
- `IDLE`
- `FARMING`
- `GOING_TO_CHEST`
- `OPENING_CHEST`
- `DEPOSITING`
- `WAIT_AFTER_DEPOSIT`

흐름:
1. 농사 시작 (`BaritoneBridge.startFarm`)
2. 인벤토리 가득 참 감지
3. 가장 가까운 사용 가능 상자로 이동 (`startGoalNear`)
4. 상자 열기 재시도
5. 농작물 quick-move 투입
6. 남은 작물이 있으면 다음 상자로 순환
7. 전부 투입되면 농사 재시작

## 6) 작물 판별/투입 정책
`CropHelper`가 아이템 ID/표시 이름(한글 포함)을 기준으로 작물을 판별합니다.
- 씨앗류는 재심기 가능성을 고려해 `hasDepositableCropItem`에서 제외합니다.
- 실제 상자 투입은 열린 컨테이너의 빈 슬롯/합치기 가능 슬롯을 검사한 뒤 진행합니다.

## 7) 설정 파일/외부 의존성 가정
- 상자 설정 파일: `config/farm_chests.txt`
- Baritone API는 컴파일 시점에 정적 링크하지 않고 리플렉션으로 접근합니다.
- 즉, Baritone 관련 클래스가 없는 환경에서는 농사/이동 시작이 실패할 수 있으며 콘솔에 실패 로그를 남깁니다.

## 8) 코드 구조(리팩터링 후)
- `ExampleModClient`: 키/이벤트 오케스트레이션
- `farm/AutoFarmController`: 자동화 상태머신
- `farm/ChestRegistry`: 상자 등록/저장/조회
- `farm/ServerDetector`: 서버 감지/최근 텍스트 관리
- `farm/CropHelper`: 작물 판별/인벤토리 체크
- `farm/BaritoneBridge`: Baritone 리플렉션 연동
