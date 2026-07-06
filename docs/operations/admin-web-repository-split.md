# 관리자 웹 레포 분리 준비 계획

기준일: 2026-07-06

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

멘토 의견에 따라 `admin-web`을 별도 레포로 분리할지 검토하고, 분리한다면 어떤 선행 조건을 만족해야 하는지 정한다.

## 선택한 방식

즉시 별도 레포를 만들지 않고, 현재 저장소에서 관리자 웹의 데이터 계약, 배포 경계, 소유권을 먼저 고정한다. 이후 build/deploy workflow와 GitHub Environment를 분리하고, production 배포 기준까지 확정한 다음 실제 레포 분리를 진행한다.

## 대안

- 지금 바로 `admin-web`을 별도 레포로 이동한다.
- 관리자 웹을 계속 단일 저장소 하위 디렉터리로 유지한다.
- Firebase Rules, Functions, 관리자 웹까지 모두 별도 레포로 나눈다.

## 선택 이유

관리자 웹은 운영 백오피스 성격이 강하므로 장기적으로 별도 레포 분리가 타당하다. 그러나 현재는 Firestore 문서 계약, Storage 경로, Functions callable, Rules, App Check, Hosting 설정이 Android 앱과 함께 변하고 있다. 먼저 계약과 배포 경계를 고정해야 분리 후 두 레포의 변경이 서로 깨지지 않는다.

## 리스크

- 분리 후 데이터 계약 변경이 두 레포에 동시에 반영되지 않으면 관리자 웹이 깨질 수 있다.
- Firebase Rules와 Functions 소유권이 불명확하면 배포 책임이 흐려질 수 있다.
- secret과 GitHub Environment를 잘못 나누면 preview/live 배포가 섞일 수 있다.
- 팀 규모가 작은 상태에서 이슈/PR이 두 레포로 나뉘면 추적 비용이 늘 수 있다.

## 현재 판단

장기 방향은 `admin-web` 별도 레포 분리다. 2026-07-06 현재 preview 배포와 GitHub Environment 경계는 대부분 고정됐지만, production Firebase 프로젝트와 live 배포 기준이 아직 비어 있으므로 실제 파일 이동은 보류한다. 실제 이동 전에 아래 조건을 만족해야 한다.

1. [관리자 웹 데이터 계약](../architecture/admin-web-data-contract.md)이 최신 코드와 맞는다.
2. `admin-web` 변경만 감지하는 build workflow가 현재 저장소에서 먼저 안정적으로 돈다.
3. Firebase Hosting preview 배포 권한과 secret 소유권이 [관리자 웹 GitHub Environment 기준](admin-web-environments.md)으로 분리된다.
4. `admin-web-preview`, `admin-web-production` GitHub Environment를 둔다.
5. production Firebase 프로젝트, Hosting site, App Check 기준을 확정한다.
6. Rules/Functions/Firebase Hosting 설정을 어느 레포가 소유할지 결정한다.
7. 분리 후 데이터 계약 변경을 어떻게 양쪽 이슈/PR로 연결할지 정한다.

## 소유권 초안

| 범위 | 분리 전 소유 위치 | 분리 후 후보 소유 위치 | 비고 |
| --- | --- | --- | --- |
| Android 앱 | `Bodeul/app` | `Bodeul/app` | 현재 저장소 유지 |
| 관리자 웹 UI | `Bodeul/admin-web` | `bodeul-admin-web` | 분리 대상 |
| 관리자 웹 build workflow | `Bodeul/.github/workflows` | `bodeul-admin-web/.github/workflows` | 분리 전 현재 저장소에서 전용 workflow 검증 |
| Firebase Hosting 설정 | `Bodeul/firebase.json` | `bodeul-admin-web/firebase.json` 후보 | 관리자 웹 Hosting 설정만 분리하고, Rules/Functions 설정은 본 저장소에 유지 |
| Firestore Rules | `Bodeul/firestore.rules` | `Bodeul` 유지 후보 | Android와 관리자 웹 권한이 함께 걸려 있음 |
| Storage Rules | `Bodeul/storage.rules` | `Bodeul` 유지 후보 | 앱 업로드와 관리자 웹 미리보기가 같은 규칙 사용 |
| Functions | `Bodeul/functions` | `Bodeul` 유지 후보 | Android와 관리자 웹이 같이 호출할 수 있음 |
| 운영 문서 | `Bodeul/docs` | 주 문서는 `Bodeul`, 웹 전용 문서는 `bodeul-admin-web` 후보 | 계약 문서는 양쪽에서 링크 |
| Firebase project secret | GitHub repo secrets/vars | 각 레포 Environment secrets/vars | preview는 WIF 전용으로 분리 완료, production은 값 확정 필요 |

## 단계별 진행 계획

| 단계 | 작업 | 산출물 | GitHub 상태 |
| --- | --- | --- | --- |
| 1 | 관리자 웹 데이터 계약 문서화 | `docs/architecture/admin-web-data-contract.md` | 완료 |
| 2 | 분리 준비 계획 문서화 | `docs/operations/admin-web-repository-split.md` | 완료 |
| 3 | 현재 저장소에 admin-web 전용 build workflow 추가 | `.github/workflows/admin-web.yml` | 완료 |
| 4 | preview/live 배포 권한과 secret 목록 정리 | `docs/operations/admin-web-environments.md` | 완료 |
| 5 | Firebase Hosting preview 배포 workflow와 WIF 전용 인증 추가 | `.github/workflows/admin-web-preview-deploy.yml` | 완료 |
| 6 | 별도 레포 생성 여부 최종 결정 | 후속 이슈 | 진행 가능 |
| 7 | 실제 레포 분리 | `bodeul-admin-web` 후보 | 대기 |

## 분리 전 체크리스트

- [x] `npm --prefix admin-web run build`가 `Admin Web Build` workflow에서 통과한다.
- [x] `npm --prefix admin-web run lint`가 `Admin Web Build` workflow에서 통과한다.
- [x] `admin-web`이 읽고 쓰는 Firestore 필드가 문서화돼 있다.
- [x] `admin-web`이 읽는 Storage 경로가 문서화돼 있다.
- [x] callable Functions 사용 여부가 문서화돼 있다.
- [x] Firebase Web config를 환경 변수로 주입한다.
- [x] Firebase Hosting preview 배포는 WIF 전용 수동 workflow로 검증할 수 있다.
- [x] preview 배포용 Firebase refresh token fallback은 제거돼 있다.
- [ ] App Check site key와 debug token 운영 방식을 production 적용 기준과 연결한다.
- [ ] Firebase Hosting live 배포 권한과 workflow 기준을 확정한다.
- [ ] production Firebase 프로젝트와 Hosting site를 확정한다.
- [ ] Firestore/Storage Rules 변경 시 관리자 웹 영향 검토 절차를 PR 템플릿 또는 체크리스트에 연결한다.
- [ ] 분리 후 공통 데이터 계약 변경을 추적할 이슈 템플릿 또는 라벨이 있다.

## 2026-07-06 진행 판단

현재 상태에서는 #74 안에서 실제 레포 이동을 바로 실행하지 않는다. 대신 별도 레포를 만들 수 있는 기준과 남은 차단 조건을 정리하는 단계까지 진행한다.

- 진행 가능: preview build/deploy 경계 검증 결과 반영, 소유권 후보 확정, 실제 분리 전 체크리스트 정리
- 보류: `bodeul-admin-web` 저장소 생성, 파일 히스토리를 보존한 `admin-web` 이동, production live workflow 추가
- 선행 조건: `admin-web-production` Environment 값, production Hosting site, App Check site key, Auth authorized domain 확정

## 분리 후 이슈 연결 규칙 초안

- 데이터 계약 변경은 원 저장소 이슈와 관리자 웹 저장소 이슈를 서로 링크한다.
- Rules/Functions 변경 PR에는 관리자 웹 영향 여부를 PR 본문에 적는다.
- 관리자 웹 PR에는 사용한 Firestore/Storage/Functions 계약 버전을 적는다.
- 운영 배포 PR은 `admin-web-preview`와 `admin-web-production` 중 어느 환경을 건드리는지 명시한다.

## 이번 단계에서 하지 않는 일

- 새 GitHub 저장소 생성
- 파일 히스토리를 보존한 실제 `admin-web` 이동
- Firebase Console 설정 변경
- GitHub Environment secret 생성 또는 수정
- Hosting live 자동 배포 변경

## 관련 이슈

- [#74 관리자 웹 레포 분리 기준 검토](https://github.com/bodeul110/Bodeul/issues/74)
