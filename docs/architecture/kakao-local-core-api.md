# Kakao Local Core API 경계

기준일: 2026-07-16

## 작업 목적

Android APK에 Kakao Local REST API 키를 넣지 않고, 병원·약국 검색의 인증, 캐시, 호출 제한과 오류 계약을 Spring Core API에서 통제한다.

## 선택한 방식

- Android는 Firebase ID token과 함께 `GET /api/places/search`를 호출한다.
- Core API는 PostgreSQL `app_users.role`까지 확인된 사용자만 요청을 허용한다.
- 서버가 Kakao Local 키워드 검색 API를 호출하고 `HP8` 병원 또는 `PM9` 약국 결과만 반환한다.
- Kakao REST API 키는 Google Secret Manager에서 Cloud Run 환경 변수로 주입한다.
- preview는 Cloud Run 최대 인스턴스가 1개이므로 서버 메모리에서 결과를 6시간, 최대 1,000개 캐시하고 사용자별 분당 60회로 제한한다.
- Core API가 실패하면 Android는 Kakao를 직접 호출하지 않고 기존 로컬 병원 목록 또는 기본 지도 안내로 복구한다.

## API 계약

| 항목 | 값 |
| --- | --- |
| 메서드와 경로 | `GET /api/places/search` |
| 인증 | `Authorization: Bearer <Firebase ID token>` |
| 역할 | `PATIENT`, `GUARDIAN`, `MANAGER`, `ADMIN` 중 PostgreSQL에 등록된 사용자 |
| `query` | 공백 제거 후 1~100자 |
| `category` | `HOSPITAL` 또는 `PHARMACY` |
| 최대 결과 | 15개 |
| 응답 캐시 | 클라이언트 응답은 `no-store`, Kakao 결과는 서버에서 6시간 캐시 |

요청 예시:

```http
GET /api/places/search?query=서울대병원&category=HOSPITAL
Authorization: Bearer <Firebase ID token>
```

응답 예시:

```json
{
  "places": [
    {
      "name": "서울대학교병원",
      "latitude": 37.5796,
      "longitude": 126.999
    }
  ]
}
```

## 오류 계약

| HTTP | 오류 코드 | 의미 |
| ---: | --- | --- |
| 400 | `invalid_place_search_request` | 검색어 또는 범주가 잘못됨 |
| 401 | `missing_authorization`, `invalid_firebase_token` | Firebase 인증 실패 |
| 403 | `role_not_found` | PostgreSQL 서비스 역할 미등록 |
| 429 | `place_search_rate_limit_exceeded` | 사용자별 서버 요청 제한 초과 |
| 429 | `kakao_local_quota_exceeded` | Kakao Local 쿼터 또는 초당 제한 초과 |
| 502 | `kakao_local_response_invalid` | Kakao 요청 거절 또는 응답 파싱 실패 |
| 503 | `kakao_local_not_configured` | 서버 REST API 키 미설정 |
| 503 | `kakao_local_credentials_invalid` | Kakao 서버 인증 설정 오류 |
| 503 | `kakao_local_unavailable` | timeout 또는 Kakao 장애 |

오류 응답과 로그에는 Kakao REST API 키, Firebase ID token, Kakao 원본 오류 본문을 넣지 않는다.

## 검토한 대안

| 대안 | 장점 | 현재 제외 이유 |
| --- | --- | --- |
| Android 직접 호출 유지 | 서버 비용과 구현이 가장 단순함 | APK에서 키를 추출할 수 있고 사용자별 호출 제한과 공용 캐시를 적용하기 어려움 |
| Firebase Functions proxy | 기존 Firebase 운영 경로를 재사용함 | 사용자 서비스의 최종 HTTP 계약을 Spring Core API로 모으기로 한 구조와 중복됨 |
| Kakao 응답을 PostgreSQL에 영구 저장 | 인스턴스 재시작 후에도 캐시가 유지됨 | 장소 검색 결과의 신선도와 삭제 정책이 추가되고 현재 MVP 호출량에는 과함 |

## 선택 이유

현재 MVP 규모에서는 Cloud Run 1개 preview 인스턴스와 짧은 서버 캐시만으로 키 비노출, 중복 호출 감소, 사용자별 제한을 함께 검증할 수 있다. 별도 Redis나 API Gateway를 먼저 도입하면 운영 대상만 늘어나므로 실제 트래픽이 확인되기 전에는 추가하지 않는다.

## 리스크와 전환 조건

- Cloud Run을 2개 이상으로 확장하면 인메모리 캐시와 rate limit은 인스턴스별로 분리된다. production 확장 전 Redis, API Gateway 또는 Cloud Armor 기반의 공용 제한을 검토한다.
- Kakao 호출 허용 IP 제한은 고정 outbound IP가 있어야 한다. Cloud Run에 Serverless VPC Access와 Cloud NAT를 붙이기 전에는 적용할 수 없으므로 production 네트워크 설계에서 다시 결정한다.
- Android의 직접 호출과 `kakaoRestApiKey` 리소스는 CodeQL 검토 후 제거했다. Core API 성공률과 로컬 목록 fallback 동작은 실기기에서 확인한다.
- Kakao 쿼터 사용량은 Kakao Developers 앱 관리 페이지에서 확인하며, 429 발생 건수는 원본 응답 없이 오류 코드 기준으로 집계한다.

## 공식 근거

- [Kakao Local 키워드 장소 검색](https://developers.kakao.com/docs/ko/local/dev-guide#search-by-keyword)
- [Kakao REST API 오류와 429](https://developers.kakao.com/docs/en/rest-api/reference#response-code)
- [Kakao API 보안 권장 사항](https://developers.kakao.com/docs/ko/getting-started/security-guideline)
- [Kakao API 쿼터](https://developers.kakao.com/docs/ko/getting-started/quota)
