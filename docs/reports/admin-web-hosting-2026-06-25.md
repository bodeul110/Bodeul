# 관리자 웹 Firebase Hosting 설정 보고서

기준일: 2026-06-25

## 구현한 내용

- 루트 `firebase.json`에 Firebase Hosting 설정을 추가했다.
- 배포 대상은 `admin-web/dist`로 고정했다.
- Vite SPA 라우팅을 위해 모든 경로를 `/index.html`로 rewrite하도록 설정했다.
- `/assets/**`는 Vite 해시 파일 기준 장기 캐시하고, HTML과 SPA fallback 경로는 no-cache로 설정했다.
- `admin-web/README.md`, `docs/operations/firebase/setup.md`, `docs/operations/infrastructure-operations-baseline.md`에 preview/deploy 절차를 추가했다.

## 변경된 범위

- `firebase.json`
- `admin-web/README.md`
- `docs/operations/firebase/setup.md`
- `docs/operations/infrastructure-operations-baseline.md`

## 검증

- `firebase.json` JSON 파싱
- `npm --prefix admin-web run lint`
- `npm --prefix admin-web run build`
- Firebase CLI `15.22.2` 설치 확인
- `firebase projects:list --json`로 `bodeul-dev` 접근 확인
- `firebase hosting:sites:list --project bodeul-dev --json`로 기본 Hosting site `bodeul-dev` 확인
- `firebase hosting:channel:deploy admin-web-preview --project bodeul-dev --expires 7d --non-interactive`로 preview channel 배포 확인
- preview URL HEAD 확인
  - `/`: `200`, `Cache-Control: no-cache`
  - `/index.html`: `200`, `Cache-Control: no-cache`
  - `/assets/firebase-vendor-B0yAgkpf.js`: `200`, `Cache-Control: public,max-age=31536000,immutable`

## 남은 범위

- 운영 도메인, Firebase Auth authorized domain, App Check site key 최종 연결 확인
- 운영 배포를 GitHub Actions로 자동화할지 여부 결정

## Preview 배포 결과

- Firebase project: `bodeul-dev`
- Hosting site: `bodeul-dev`
- Channel: `admin-web-preview`
- URL: <https://bodeul-dev--admin-web-preview-b1x2s7k5.web.app>
- 만료: 2026-07-02 23:14:31 KST

## 참고

- Firebase Hosting 설정 문서: <https://firebase.google.com/docs/hosting/full-config>
- Firebase Hosting preview channel 문서: <https://firebase.google.com/docs/hosting/manage-hosting-resources>
