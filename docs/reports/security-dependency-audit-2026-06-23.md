# 보안 의존성 점검 기록 (2026-06-23)

## 목적

GitHub Dependabot 보안 알림과 `npm audit` 결과를 기준으로, 안전하게 적용 가능한 npm lockfile 보안 패치를 반영하고 남는 항목의 보류 사유를 정리한다.

## 변경 범위

- `admin-web/package-lock.json`
  - `npm audit fix --package-lock-only`로 lockfile만 갱신했다.
  - `package.json`의 직접 의존성 선언은 변경하지 않았다.
  - 주요 취약 패키지 확인 결과:
    - `@babel/core` 7.29.7
    - `@grpc/grpc-js` 1.9.16
    - `brace-expansion` 5.0.6
    - `protobufjs` 7.6.4
    - `vite` 8.1.0
- `functions/package-lock.json`
  - `npm audit fix --package-lock-only`로 `firebase-admin@14` 강제 업데이트 없이 가능한 transitive 보안 패치를 반영했다.
  - `package.json`의 `firebase-admin` 13.8.0, `firebase-functions` 7.2.5 선언은 변경하지 않았다.
  - 주요 취약 패키지 확인 결과:
    - `@grpc/grpc-js` 1.14.4
    - `form-data` 2.5.6
    - `protobufjs` 7.6.4
    - `uuid` 11.1.1

## 검증 결과

- `npm --prefix admin-web ci`: 통과, 취약점 0개
- `npm --prefix admin-web run build`: 통과
- `npm --prefix functions ci`: 통과
- `npm --prefix functions audit --audit-level=low`: 실패, moderate 9개 잔여

## 남은 범위

`functions`의 잔여 moderate 9개는 `firebase-admin@13.x`가 포함하는 Google Cloud 의존성 계층에서 발생한다. `npm audit fix --force`는 `firebase-admin` 또는 `firebase-functions`의 major 변경을 요구하므로 이번 작업 범위에서는 적용하지 않았다.

2026-06-23 기준 `firebase-functions@latest`는 7.2.5이며 peer dependency가 `firebase-admin` `^11.10.0 || ^12.0.0 || ^13.0.0`까지만 허용한다. 따라서 `firebase-admin@14`는 현재 조합에서 안전하게 병합할 수 없고, `firebase-functions`가 `firebase-admin@14`를 지원하는 버전을 낸 뒤 다시 처리한다.
