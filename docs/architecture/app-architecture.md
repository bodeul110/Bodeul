# Android 앱 구조 설명

기준일: 2026-06-25

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결론

Android 앱은 `Activity -> Coordinator -> Binder -> ScreenModel/Formatter -> Repository` 경계를 유지한다. 현재 화면과 기능 수가 많아졌기 때문에 Activity에 모든 로직을 넣는 방식보다 파일 수는 늘지만, 역할을 나누는 편이 유지보수에 맞다.

## 작업 목적

Activity, Coordinator, Binder, Repository, Mock 모드가 왜 있는지 설명한다.

## 선택한 방식

| 구성 | 역할 |
| --- | --- |
| Activity | Android 생명주기, 권한 요청, 화면 이동, 저장소 호출 연결 |
| Coordinator | 도메인 데이터를 화면 모델로 조합하고 상태별 표시 정책을 결정 |
| Binder | XML View에 ScreenModel 값을 바인딩하고 반복 카드 렌더링을 담당 |
| Formatter | 날짜, 상태, 금액, 안내 문구처럼 표현 문자열을 조합 |
| Repository | Firebase/Mock 데이터 접근 계약을 감춘다 |
| Mock 구현 | Firebase 설정이 없거나 데모/테스트가 필요한 환경에서 같은 화면 흐름을 유지 |
| Firebase 구현 | 실제 Auth, Firestore, Storage, FCM과 연결 |

## 대안

| 대안 | 장점 | 현재 판단 |
| --- | --- | --- |
| Activity 중심 구현 | 파일 수가 적고 처음에는 빠르다. | 예약, 리포트, 관리자, 매니저 기능이 커지면서 Activity가 너무 커진다. |
| MVVM + ViewModel/LiveData 전면 도입 | Android 표준 구조에 가깝다. | 현재 Java/XML 기반 코드가 이미 Coordinator/Binder 패턴으로 정리되어 있어 전면 전환 비용이 크다. |
| Compose 전환 | UI 상태 모델과 선언형 UI를 쓸 수 있다. | 현재 XML 화면과 기존 구현량이 많아 MVP 단계에서 전환 비용이 크다. |

## 선택 이유

- 현재 앱은 환자, 보호자, 매니저, 관리자 역할별 화면이 많다.
- Activity는 Android 프레임워크와 연결되는 지점만 맡기고, 화면 정책은 Coordinator/Binder로 밀어내는 편이 변경 범위를 줄인다.
- Repository 계약을 두면 Firebase 모드와 Mock 모드가 같은 화면 코드를 공유할 수 있다.
- Mock 모드는 `google-services.json`이 없는 CI/Dependabot 환경에서도 컴파일과 화면 흐름 검증을 가능하게 한다.

## Mock 모드가 필요한 이유

- Firebase 설정 파일 없이도 앱이 실행되고 화면 흐름을 확인할 수 있다.
- 디자인/멘토 시연에서 네트워크나 권한 상태에 덜 의존한다.
- Repository 계약이 유지되는지 테스트하기 쉽다.
- Firebase 전환 전에 화면과 도메인 흐름을 빠르게 검증할 수 있다.

## 리스크

- Coordinator, Binder, Model 파일이 많아져 처음 보는 사람에게 구조가 복잡해 보일 수 있다.
- 같은 역할 분리 기준을 지키지 않으면 화면마다 구조가 달라질 수 있다.
- Mock 데이터가 실제 Firestore 계약과 어긋나면 데모는 되지만 운영 검증이 약해질 수 있다.

## 보완 계획

- 새 화면은 Activity, Coordinator, Binder, Repository 역할을 PR에서 명시한다.
- Mock/Firebase 양쪽 계약이 바뀌면 같은 PR에서 함께 갱신한다.
- 화면 구조가 복잡해진 기능은 `docs/status/implementation-status.md`와 architecture 문서에 요약을 남긴다.

