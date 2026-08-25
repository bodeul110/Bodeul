# Android 릴리스 서명 운영 계약

## 작업 목적

팀 소유 릴리스 키가 확정되기 전에도 서명되지 않은 Android 릴리스 산출물이 실수로 배포되는 경로를 차단한다.

## 선택한 방식

- 키 저장소 파일과 암호는 저장소 밖에서 관리한다.
- 키 저장소 경로와 alias는 `local.properties`, Gradle 속성 또는 환경변수로 전달할 수 있다.
- 키 저장소 암호와 key 암호는 환경변수로만 전달한다.
- 릴리스 산출물 작업은 `validateReleaseSigning`을 먼저 실행하고, 입력 누락·부분 입력·키 저장소 파일 부재 시 실패한다.
- 릴리스 서명 비밀값을 Gradle 구성 캐시에 남기지 않기 위해 릴리스 작업에는 `--no-configuration-cache`를 요구한다.

## 대안

- 임시 키를 자동 생성하면 바로 릴리스 빌드를 만들 수 있지만, 향후 앱 업데이트 신뢰 체인이 임시 키에 고정된다.
- 서명값이 없을 때 unsigned 산출물을 허용하면 로컬 확인은 편하지만, 배포 후보를 잘못 전달할 수 있다.
- 암호를 `local.properties`나 Gradle `-P` 속성으로 받으면 설정은 단순하지만 파일·명령 기록과 구성 캐시에 남을 가능성이 커진다.

## 선택 이유

현재 MVP 규모에서는 키 관리 서비스를 추가하기보다 팀 소유 키와 백업 주체를 먼저 확정하는 것이 적절하다. 다만 키 확정 전에도 릴리스 산출물 경계는 코드로 강제해 실수로 unsigned 빌드를 배포하는 위험을 줄인다.

## 입력 계약

| 값 | 입력 경로 | 비고 |
| --- | --- | --- |
| 키 저장소 경로 | `bodeulReleaseStoreFile` 또는 `BODEUL_RELEASE_STORE_FILE` | 절대 경로 권장 |
| key alias | `bodeulReleaseKeyAlias` 또는 `BODEUL_RELEASE_KEY_ALIAS` | 실제 키 생성 후 확정 |
| 키 저장소 암호 | `BODEUL_RELEASE_STORE_PASSWORD` | 환경변수만 허용 |
| key 암호 | `BODEUL_RELEASE_KEY_PASSWORD` | 환경변수만 허용 |

키 저장소 경로와 alias를 `local.properties`에 둘 수 있지만 암호는 기록하지 않는다. `*.jks`, `*.keystore`, `*.p12`, `*.pfx`는 Git 추적에서 제외한다.

## 실행 절차

입력값 검증:

```powershell
.\gradlew.bat :app:validateReleaseSigning --no-configuration-cache --console=plain
```

릴리스 후보 생성:

```powershell
.\gradlew.bat :app:bundleRelease --no-configuration-cache --console=plain
```

실제 값은 실행 전 현재 PowerShell 프로세스의 환경변수 또는 비밀 저장소가 주입한 환경변수로 설정한다. 암호를 명령 인자, 저장소 파일, Issue나 PR에 적지 않는다.

## 실패 조건

- 입력값 네 개 중 하나라도 비어 있음
- 키 저장소 경로가 존재하지 않거나 파일이 아님
- 릴리스 서명 구성이 적용되지 않음
- 릴리스 산출물 작업을 구성 캐시 활성 상태로 실행함
- 릴리스 산출물 작업에서 `-x` 또는 `--exclude-task`를 사용함

오류에는 누락된 입력 이름만 표시하고 입력값, 암호, 인증서 지문은 표시하지 않는다.

## 남은 범위

- 팀 소유 릴리스 키, alias, 암호와 백업 주체 확정
- Firebase와 Kakao에 릴리스 인증서 SHA-256 등록
- Google Play Console과 Firebase Play Integrity 연결
- ARM 실기기에서 릴리스 후보의 App Check `VALID` 확인
- 릴리스 실패와 App Check enforcement 롤백 기록

## 리스크

팀 소유 키를 분실하면 같은 application ID의 앱 업데이트를 이어가기 어렵다. 실제 키를 만들기 전에 보관 주체, 암호 복구 경로, 오프라인 백업 위치를 함께 확정해야 한다.
