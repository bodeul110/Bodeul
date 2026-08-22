# 소셜 로그인 브랜드 자산

기준일: 2026-08-22

## 작업 목적

로그인 화면의 임시 `K`, `G` 문자 버튼을 실제 활성 인증 제공자의 공식 버튼으로 교체하고, 브랜드 오인과 OAuth 검수 위험을 줄인다.

## 선택한 방식

- Kakao와 Google이 배포한 완성형 PNG를 변형 없이 사용한다.
- 두 버튼은 56dp 높이 안에서 원본 비율을 유지하고 같은 252dp 터치 영역에 배치한다.
- Figma의 원형 문자 버튼보다 제공자 브랜드 가이드를 우선한다.
- Naver는 `naver_login_enabled=false`인 동안 계속 숨기고 이번 작업에서 자산을 추가하지 않는다.

## 대안과 선택 이유

- 문자 `K`, `G` 또는 직접 그린 벡터는 구현이 단순하지만 공식 심볼의 형태와 색을 보장하지 못해 제외했다.
- 앱 전용 커스텀 버튼은 문구를 한국어로 통일하기 쉽지만, 현재 MVP 규모에서는 공식 완성형 자산을 그대로 쓰는 편이 검수와 유지보수 위험이 작다.
- Kakao와 Google 버튼을 같은 모양으로 재구성하면 시각적 통일성은 높아지지만 각 제공자의 컨테이너, 심볼과 여백 규정을 동시에 충족하기 어렵다.

## 적용 자산

| 제공자 | 저장 위치 | 공식 원본 | SHA-256 |
| --- | --- | --- | --- |
| Kakao | `app/src/main/res/drawable-nodpi/kakao_login_button.png` | Kakao Login `large_narrow` | `D944D0E6DE28647F4C4CDD537FB0CCCF24423B3CAECD8374ADA19C40578CFE5A` |
| Google | `app/src/main/res/drawable-nodpi/google_sign_in_button.png` | Android + Web, Light, text, Pill, 4x | `2F30A746FD0ACC72417C460139F3A2AE9F93D3E31BB6F44181719556EC54A2D5` |

공식 출처:

- [Kakao 로그인 디자인 가이드](https://developers.kakao.com/docs/ko/kakaologin/design-guide)
- [Kakao 로그인 리소스](https://developers.kakao.com/tool/resource/login)
- [Google 로그인 브랜딩 가이드](https://developers.google.com/identity/branding-guidelines)

## 리스크와 검증

- 제공자가 버튼 자산이나 가이드를 바꾸면 원본과 해시를 다시 확인한다.
- Google 버튼은 공식 영문 완성형이므로 임의 번역이나 글꼴 교체를 하지 않는다.
- Android 빌드는 자산 포함과 리소스 참조만 증명한다. 실제 로그인, 키보드, 회전, 큰 글꼴과 접근성은 실기기에서 별도로 확인한다.
