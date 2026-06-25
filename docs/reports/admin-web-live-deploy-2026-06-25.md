# 관리자 웹 Firebase Hosting live 배포 판단 보고서

기준일: 2026-06-25

## 판단

`bodeul-dev` Firebase Hosting live 채널에 관리자 웹을 배포하는 것이 맞다고 판단했다.

## 근거

- `bodeul-dev.web.app` live 채널은 배포 전 `/`, `/index.html` 모두 `404 Site Not Found`를 반환했다.
- preview 채널 `admin-web-preview`는 같은 Firebase project `bodeul-dev`와 같은 Hosting site `bodeul-dev`에서 정상 동작했다.
- `admin-web/firebase.ts`는 `projectId: 'bodeul-dev'`, `authDomain: 'bodeul-dev.firebaseapp.com'`, `storageBucket: 'bodeul-dev.firebasestorage.app'`로 dev 프로젝트에 고정되어 있다.
- Firebase Hosting site 조회 결과 기본 site는 `bodeul-dev`, default URL은 `https://bodeul-dev.web.app`이다.
- Firebase Hosting channel 조회 결과 live 채널에는 배포 전 release가 없었고, preview 채널에는 검증된 release가 있었다.
- Firebase 공식 문서 기준 live 채널은 사이트의 Firebase 기본 도메인과 연결 도메인에 콘텐츠와 설정을 제공하며, preview 채널과 달리 만료되지 않는다.
- Firebase 공식 문서 기준 custom headers는 rewrite 전에 요청 경로 기준으로 적용된다. 따라서 HTML/SPA fallback 경로는 no-cache, Vite 해시 asset은 장기 캐시하는 현재 설정이 배포 후 응답과 일치해야 한다.

## 실행

```powershell
npm --prefix admin-web run build
firebase deploy --only hosting --project bodeul-dev --non-interactive
```

## 배포 결과

- Firebase project: `bodeul-dev`
- Hosting site: `bodeul-dev`
- Channel: `live`
- URL: <https://bodeul-dev.web.app>
- Release: `projects/bodeul-dev/sites/bodeul-dev/channels/live/releases/1782397567336000`
- Version: `projects/bodeul-dev/sites/bodeul-dev/versions/5af0791842dce48b`
- Release time: 2026-06-25 23:26:07 KST

## 검증

- `npm --prefix admin-web run build`: 통과
- `firebase deploy --only hosting --project bodeul-dev --non-interactive`: 통과
- `firebase hosting:channel:list --project bodeul-dev --json`: live release 생성 확인
- live URL HEAD 확인
  - `/`: `200`, `Cache-Control: no-cache`, `Content-Type: text/html; charset=utf-8`
  - `/index.html`: `200`, `Cache-Control: no-cache`, `Content-Type: text/html; charset=utf-8`
  - `/manager-review/deep-link-check`: `200`, `Cache-Control: no-cache`, `Content-Type: text/html; charset=utf-8`
  - `/assets/firebase-vendor-B0yAgkpf.js`: `200`, `Cache-Control: public,max-age=31536000,immutable`, `Content-Type: text/javascript; charset=utf-8`
- live URL GET 확인
  - `/`: `title = bodeul-admin`, `id="root"` 포함
  - `/manager-review/deep-link-check`: `title = bodeul-admin`, `id="root"` 포함

## 남은 범위

- 운영 도메인을 별도 custom domain으로 연결할지 결정한다.
- App Check site key를 운영 도메인 기준으로 연결하고 enforcement 전환 조건을 정리한다.
- GitHub Actions에서 preview/live Hosting 배포를 자동화할지 결정한다.

## 참고

- Firebase Hosting 채널/릴리스 관리 문서: <https://firebase.google.com/docs/hosting/manage-hosting-resources>
- Firebase Hosting 설정 문서: <https://firebase.google.com/docs/hosting/full-config>
