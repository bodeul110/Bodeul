# 기획팀 전달용 계정·결제 준비표

기준일: 2026-07-19

목표 운영 전환일: 2026-12-15

## 이 문서에서 기획팀이 할 일

기획팀은 아래 네 가지만 준비하면 된다.

1. 모든 운영 계정에 사용할 **법적 운영주체와 사업자 정보**를 확정한다.
2. **사업용 카드·정산계좌·결제 책임자**를 지정한다.
3. 계정 잠금에 대비해 **실명 관리자 2명**을 지정한다.
4. 아래 표의 `유료 전환`과 `견적 계약` 항목을 정해진 시점에 승인한다.

실제 API key, 비밀번호, 카드번호, 사업자번호, 계정 ID는 이 문서나 GitHub에 적지 않는다. 기획팀은 계정을 만든 뒤 개발팀을 실명 계정으로 초대하며, 로그인 비밀번호를 전달하지 않는다.

## 상태 표기

| 상태 | 의미 |
| --- | --- |
| 완료 | 계정·프로젝트와 결제 연결이 준비됨 |
| 무료 유지 | 계정은 필요하지만 유료 결제는 하지 않음 |
| 결제 예정 | 운영 전 지정 등급으로 유료 전환 필요 |
| 견적 필요 | 사용량·계약 심사에 따라 비용이 정해짐 |
| 미준비 | 기획팀에서 새로 준비해야 함 |
| 확인 필요 | 기존 계정은 있으나 소유권 또는 요금제를 콘솔에서 최종 확인해야 함 |

## 계정에 기입할 명칭

| 서비스 | 기입할 명칭 |
| --- | --- |
| 기준 도메인 | `bodeul.app` 후보 |
| Cloudflare account | `BoDeul` |
| Cloud Identity organization | `BoDeul` |
| GitHub organization slug | `bodeul-team` |
| Google Cloud·Firebase 조직 표시명 | `BoDeul` |
| Google Play 개발자 표시명 | `보들` |
| Supabase organization | `BoDeul` |
| Vercel team slug | `bodeul` |
| Bitwarden organization | `BoDeul` |
| Kakao Developers Biz app | `보들` |
| Kakao Developers test app | `보들 Dev` |
| Kakao Business Channel·알림톡 발신 프로필 | `보들` |
| PortOne 상점명 | `보들` |
| Figma team | `BoDeul Design` |

`bodeul-team`, `bodeul`, `bodeul.app`은 최종 생성·구매 시 사용 가능 여부를 다시 확인한다. 표시명은 위 값으로 통일하되 법적 명칭 입력란에는 실제 사업자·법인 명칭을 사용한다.

## 기획팀 준비 계정

| 우선순위 | 서비스 | 현재 상태 | 목표·결제 | 기획팀 액션 | 완료 시한 |
| --- | --- | --- | --- | --- | --- |
| P0 | 법적 운영주체 | **미준비** | 등록·법무·세무 실비 | 사업자 형태, 대표자, 주소, 전화번호 확정 | 2026-08-15 |
| P0 | 기준 도메인 | **미구매** | 연 50,000 KRW 이내 | 상표·구매 가능 여부 확인 후 구매, 자동 갱신 설정 | 2026-09-15 |
| P0 | Cloudflare | **개인 계정 연결, 도메인 없음** | Free / 0 | 운영주체 account 생성, 실명 관리자 2명 초대 | 도메인 구매 직후 |
| P0 | Cloud Identity | **조직 경계 없음** | Free / 0 | 도메인 검증 후 조직과 실명 관리자 2명 생성 | 2026-10-16 |
| P0 | GitHub | **저장소 2개와 Project가 개인 소유** | Free organization / 0 | 조직 생성 후 소유권 이전 승인 | 2026-10-16 |
| P0 | Google Cloud·Firebase | **dev·production 결제 연결 완료** | Blaze 사용량 과금 / 정상 0~10,000 KRW·월 | 사업용 카드·결제 관리자 2명 지정, 조직으로 이관 | 2026-10-16 |
| P0 | Google Play Console | **미준비** | Organization / USD 25 1회 | D-U-N-S와 조직 증빙 준비 후 결제 | 2026-10-16 신청 |
| P0 | Supabase | **개인 조직, dev·production 2개, Free 기준** | Pro + Micro 2개 / USD 35·월 예상 | 관리자 2명 지정 후 결제 | 2026-11-16 |
| P0 | Vercel | **개인 team, 관리자 웹 존재, Pro 미확인** | Pro Developer 2석 / USD 40·월 | team 생성·프로젝트 이전·카드 등록 | 2026-11-16 |
| P0 | Bitwarden | **미준비** | Teams 운영자 2석 / USD 8·월 | 조직과 복구 절차 등록 | production 비밀값 입력 전 |
| P1 | Kakao Developers | **개발 연동 존재, production 소유권 미정** | Biz app·test app / 기본 무료 | Owner와 Editor 지정 | 2026-10-16 |
| P1 | Kakao Business | **미준비** | Business Channel / 기본 무료 | 사업자 명의 채널 생성·인증 | 알림톡 계약 전 |
| P1 | 카카오 알림톡 | **미계약** | 공식 딜러 / 발송량별 견적 | 월 예상 발송량 확정 후 2곳 이상 견적 | 2026-11-16 |
| P1 | PG·PortOne | **미계약** | PG 계약·PortOne / 별도 견적 | 결제 정책 확정 후 PG 2곳 이상 견적 | 2026-10-31 견적 |
| P2 | Figma | **연결됨, 소유권·요금제 확인 필요** | Starter / 0 | 팀 소유자 2명 지정, 유료 전환하지 않음 | 2026-10-16 |
| P2 | Google Workspace | **미준비, 초기 미구매** | 필요 시 Business Starter 2석 / 지역별 가격 | 공용 발신 mailbox 필요 시 별도 승인 | 필요 발생 시 |

## 결제 상태만 모아보기

### 이미 결제 연결됨

| 서비스 | 현재 결제 상태 | 기획팀이 추가로 할 일 |
| --- | --- | --- |
| Google Cloud·Firebase dev | 사용량 결제 연결 완료 | 사업용 카드와 결제 관리자 확인 |
| Google Cloud·Firebase production | 사용량 결제 연결 완료 | 사업용 카드와 결제 관리자 확인 |

GCP budget은 지출 차단 기능이 아니라 알림이다. 개발 10,000 KRW, production 30,000 KRW 알림을 유지하되 정상 운영비 목표는 합계 월 10,000 KRW 이내로 본다.

### 지금은 무료로 준비

| 서비스 | 목표 등급 | 결제 |
| --- | --- | ---: |
| Cloudflare DNS·Email Routing | Free | 0 |
| Cloud Identity | Free | 0 |
| GitHub organization | Free | 0 |
| Kakao Developers Biz/test app | 기본 등급 | 0, API 초과 사용 시 별도 |
| Kakao Business Channel | 기본 계정 | 0 |
| Figma | Starter | 0 |

### 운영 전에 결제

| 서비스 | 결제할 등급 | 예상 비용 | 결제 시점 |
| --- | --- | ---: | --- |
| Supabase | Pro + Micro 2개 | USD 35/월 예상 | 2026-11-16까지 |
| Vercel | Pro Developer 2석 | USD 40/월 | 2026-11-16까지 |
| Bitwarden | Teams 운영자 2석 | USD 8/월 | production 비밀값 입력 전 |
| Google Play | Organization·Full distribution | USD 25 1회 | D-U-N-S 준비 후 즉시 |
| 기준 도메인 | 도메인 1개 | 연 50,000 KRW 이내 | 최종 도메인 확정 직후 |

### 정책 확정 후 견적

| 서비스 | 결제 기준 | 견적 전에 기획팀이 정할 것 |
| --- | --- | --- |
| PG·PortOne | 결제 수수료, 보증보험, 환불·송금 수수료, 정산주기 | 기본요금, 추가요금, 취소·환불, 매니저 지급 구조 |
| 카카오 알림톡 | 공식 딜러별 발송 단가와 월 발송량 | 발송할 정보성 메시지 종류와 월 예상 건수 |
| 법무·세무·노무·보험 | 계약·자문·보험 견적 | 운영주체, 매니저 계약 관계, 사고 책임 범위 |

## 월 결제 승인안

계획 환율은 환율·해외결제 변동을 고려해 USD 1당 1,500 KRW로 계산한다.

| 고정비 | 계획 금액 |
| --- | ---: |
| Supabase | 52,500 KRW |
| Vercel | 60,000 KRW |
| Bitwarden | 12,000 KRW |
| 도메인 월 환산 | 최대 약 4,200 KRW |
| Google Cloud·Firebase 정상 목표 | 0~10,000 KRW |
| 예상 합계 | 약 128,700~138,700 KRW/월 |
| 월 승인 한도 | 150,000 KRW |

PG·알림톡·문자 발송비, 매니저 지급액과 환불 수수료는 거래량에 따라 달라지므로 위 고정비에서 제외한다. 해외 결제 세금까지 포함한 예상액이 140,000 KRW를 넘으면 Google Workspace, Figma Professional, GitHub Team 같은 선택 구독을 추가하지 않고 기획 책임자가 다시 승인한다.

## 기획팀이 준비해서 개발팀에 알려줄 값

실제 값은 GitHub 댓글이 아니라 승인된 비공개 채널이나 Bitwarden으로 전달한다.

- [ ] 법적 운영주체명
- [ ] 사업자등록·법인 증빙 보관 위치
- [ ] 대표 주소와 전화번호
- [ ] 사업용 결제카드 담당자
- [ ] PG 정산계좌 담당자
- [ ] 결제 책임자와 대체 승인자
- [ ] 실명 자산 관리자 2명
- [ ] 개인정보 보호책임자와 실무 담당자
- [ ] 최종 구매 도메인
- [ ] D-U-N-S 번호와 조직 검증 상태
- [ ] Supabase·Vercel·Bitwarden 유료 전환 승인일
- [ ] Google Play 등록비 결제 영수증 보관 위치
- [ ] PG 후보·수수료·정산주기 비교 결과
- [ ] 알림톡 공식 딜러·단가·월 발송 한도

## 생성 순서

1. 법적 운영주체, 사업용 카드·계좌와 관리자 2명을 먼저 정한다.
2. 도메인을 구매한다.
3. Cloudflare와 Cloud Identity를 조직 명의로 만든다.
4. GitHub, GCP/Firebase, Supabase, Vercel, Figma를 조직 소유로 이전한다.
5. D-U-N-S를 확인하고 Google Play Organization 계정을 결제한다.
6. Bitwarden을 만들고 production 복구 자료를 등록한다.
7. 가격·환불·정산 정책 확정 후 PG와 알림톡 견적을 승인한다.
8. 2026-11-16까지 Supabase와 Vercel을 유료 전환한다.

## 구매하지 않는 항목

| 항목 | 현재 결정 |
| --- | --- |
| GitHub Team | 저장소가 공개인 동안 구매하지 않음 |
| Google Workspace | 공용 발신 mailbox가 필요하기 전까지 구매하지 않음 |
| Figma Professional | Starter로 유지 |
| Supabase Team·PITR·custom domain | 초기 운영에서는 구매하지 않음 |
| Vercel Enterprise | 구매하지 않음 |
| Firebase Hosting | 관리자 웹은 Vercel을 사용하므로 구매하지 않음 |
| Oracle Cloud | Core API는 Cloud Run을 사용하므로 구매하지 않음 |

## 공식 가격 확인

- [Supabase 요금](https://supabase.com/pricing)
- [Vercel 요금](https://vercel.com/pricing)
- [GitHub 요금](https://github.com/pricing)
- [Bitwarden Business 요금](https://bitwarden.com/pricing/business/)
- [Cloud Identity 등급](https://docs.cloud.google.com/identity/docs/editions)
- [Cloudflare Email Routing 요금](https://developers.cloudflare.com/email-service/platform/pricing/)
- [Google Play 조직 계정 유형](https://support.google.com/googleplay/android-developer/answer/13634885)
- [Google Play 조직 계정 필수 정보와 D-U-N-S](https://support.google.com/googleplay/android-developer/answer/13628312)
- [카카오 알림톡](https://business.kakao.com/info/bizmessage/)
- [PortOne PG 계약 경계](https://developers.portone.io/opi/ko/support/pg-terms)

## 내부 참고

- [기획 정책 및 공용 계정 준비 기준](product-policy-and-shared-account-readiness.md)
- [Production 인프라 기본값](../operations/production-infrastructure-defaults.md)
- [2026년 Production 운영 전환 계획](../operations/production-transition-plan-2026.md)
