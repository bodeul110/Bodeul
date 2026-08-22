# Notion·Figma 문서 정합성 점검

기준일: 2026-08-22

## 작업 목적

연결된 Notion 기획 문서와 Figma 원본을 현재 저장소 구현에 대조하고, 서로 다른 시점의 자료가 현재 기준으로 오해되지 않도록 문서 책임을 정리한다.

## 확인한 자료

- Notion `부들보들` 워크스페이스의 MVP 통합본 v1.1, 화면별 상세 명세 v2.2, 동행 채팅 명세 v1.0, 출시 전 법률 체크리스트 v1.0
- Figma `보들 가이드`, file key `E2EaCod0aNjnI1uGXZQKok`, `Page 2(460:2)`
- 저장소의 Spring Core API, Flyway V1~V13, Android 데이터 경계와 2026-07 검증 기록

Notion과 Figma는 읽기 전용으로 확인했다. 공개 저장소에는 비공개 Notion URL·페이지 ID, 계정 정보와 개인정보를 옮기지 않았다.

## 확인 결과

### Figma

- 최상위 모바일 화면 38개와 중첩 로그인 화면을 확인했다.
- 예약·매칭·동행 가이드 1~13 흐름은 prototype으로 연결돼 있다.
- 채팅 시안 6개와 최종 리포트는 prototype 연결이 없다.
- 역할이 뒤섞인 홈 카드, 권한 필수 표기, 가이드 번호, 내비게이션 용어와 샘플 데이터에 불일치가 있다.
- 과거 기준 node `0:1`을 현재 페이지 `460:2`로 교체했다.

### Notion

- MVP 제품 의도는 예약 → 매니저 수락 → 동행 가이드 → 최종 리포트 흐름이다.
- 채팅은 시스템 진행 이벤트 중심이고 직접 대화는 보조 수단이라는 목표가 있다.
- Firestore 단일 원본, 채팅 제외, AI/STT 실제 처리처럼 현재 구현과 충돌하는 기술·상태 문구가 함께 남아 있다.
- 환자→보호자 정보공유 동의, 진료 녹음과 외부 AI 처리, 취소·환불·사고 대응은 출시 전 결정 항목이다.

### 현재 저장소

- 예약·세션·채팅·읽음·위치·리포트·후속 처리의 쓰기 원본은 PostgreSQL이다. 사용자 Core 요청은 Spring API를 거치고 현재 매칭 배정은 관리자 서버의 admin-only 함수를 사용한다.
- 매칭은 현재 관리자 runtime 전용 배정 함수로 수행하며 매니저 self-accept API가 없다.
- 채팅은 환자·보호자·매니저 직접 메시지를 저장하며 가이드 시스템 이벤트 메시지는 생성하지 않는다.
- 채팅·읽음·위치·첨부는 Core API와 PostgreSQL 경계로 전환했고 Realtime은 커밋 알림으로 사용한다.
- 위치 24시간, 첨부 30일, 채팅 본문 180일 기본 만료 계약이 있다.
- OCR, 진료 녹음, STT와 AI 요약 자동 생성은 미구현이다.
- 인증 프로필·지원·매니저 서류 심사 메타데이터는 Firestore, 매니저 서류와 세션 첨부 원본은 Firebase Storage에 남아 있다.

## 반영한 문서

- `docs/planning/source-of-truth.md`
- `docs/planning/notion-product-alignment.md`
- `docs/design/figma-current-screen-map.md`
- `docs/planning/mvp-scope.md`
- `docs/planning/screen-restructure-target.md`
- `docs/README.md`와 관련 카테고리·참조 색인

2026-05~07 디자인 감사 문서는 삭제하지 않고 당시 감사 이력으로 표시했다. 로컬 PDF·Figma export는 현재 기준에서 스냅샷으로 낮췄다.

## 선택한 방식

Notion은 제품 의도와 미결 정책, Figma는 화면 구조와 시각 위계, 저장소는 현재 구현과 기술 계약을 소유하도록 분리했다.

## 대안과 선택 이유

Notion이나 Figma 하나를 단일 원본으로 두는 방식은 탐색은 단순하지만, 현재 검증된 PostgreSQL 전환을 Firestore 기술안으로 되돌리거나 시안의 AI·OCR를 완료 기능으로 오해할 수 있다. 질문별 기준을 나누는 방식이 현재 프로젝트의 변경 속도와 검증 책임에 더 적합하다.

## 리스크

- 외부 문서와 저장소를 함께 갱신하지 않으면 다시 내용이 어긋날 수 있다.
- Figma의 13단계와 DB의 데이터 기반 가이드 단계가 계속 다른 이름을 사용할 수 있다.
- 매니저 self-accept와 시스템 이벤트 동행방은 제품 목표와 현재 구현이 다르므로 별도 결정 없이 완료로 표시하면 안 된다.

## 검증

- 문서 내부 상대 링크 검사
- `git diff --check`
- Notion 비공개 URL·페이지 ID와 계정 정보가 변경 파일에 포함되지 않았는지 검사
- 문서 전용 변경이므로 Android·Core API 빌드는 수행하지 않음
