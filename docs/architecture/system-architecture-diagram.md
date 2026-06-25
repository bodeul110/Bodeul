# 시스템 아키텍처 다이어그램

기준일: 2026-06-25

아래 다이어그램은 Android 앱, 관리자 웹, Firebase Auth, Firestore, Storage, Functions, FCM, Kakao API가 현재 어떤 흐름으로 연결되는지 한 장으로 정리한 것이다.

```mermaid
flowchart LR
  subgraph Client["클라이언트"]
    Android["Android 앱\n환자/보호자/매니저"]
    AdminWeb["관리자 웹\nReact + Vite"]
  end

  subgraph Firebase["Firebase 프로젝트"]
    Auth["Firebase Auth"]
    Firestore["Cloud Firestore\nusers, appointmentRequests,\ncompanionSessions, reports, inquiries"]
    Storage["Firebase Storage\nmanager-documents,\ncompanion-chat-attachments"]
    Functions["Cloud Functions v2\nasia-northeast3"]
    FCM["Firebase Cloud Messaging"]
  end

  subgraph External["외부 연동"]
    KakaoLogin["Kakao Login API\nkapi.kakao.com"]
    KakaoLocal["Kakao Local REST API\ndapi.kakao.com"]
    KakaoAlimtalk["알림톡/관리자 푸시 대행사\n환경 변수로 연결"]
  end

  subgraph Ops["운영/검증"]
    Tools["tools/firebase\ncheck, seed, backup, restore,\nreport, preflight"]
    GitHub["GitHub Actions\npreflight"]
  end

  Android -->|"이메일/Google/Kakao 로그인"| Auth
  AdminWeb -->|"관리자 로그인"| Auth
  Android -->|"예약, 세션, 채팅, 리포트, 문의"| Firestore
  AdminWeb -->|"매니저 심사, 운영 대시보드"| Firestore
  Android -->|"매니저 서류, 채팅 첨부"| Storage
  AdminWeb -->|"서류 미리보기"| Storage

  Android -->|"카카오 access token 전달"| Functions
  Functions -->|"사용자 정보 조회"| KakaoLogin
  Functions -->|"custom token 발급"| Auth

  Android -->|"병원/약국 키워드 좌표 조회\n6시간 메모리 캐시"| KakaoLocal

  Firestore -->|"문서 변경 트리거"| Functions
  Functions -->|"안심 채팅, 위치 알림,\n문의 답변 알림"| FCM
  FCM --> Android
  Functions -->|"예약 리마인더,\n관리자 후속 알림"| KakaoAlimtalk

  Tools -->|"상태 점검/백업/복원/리포트"| Firestore
  Tools -->|"Storage 정합성 점검"| Storage
  GitHub -->|"CI preflight"| Tools
```

## 흐름 요약

- Android 앱은 Firebase 설정이 있으면 Firebase 구현을 사용하고, 설정이 없으면 Mock Repository로 전환한다.
- 관리자 웹은 Firebase Auth로 로그인한 뒤 `users/{uid}.role == ADMIN`인 계정만 운영 화면에 진입시킨다.
- Firestore Rules와 Storage Rules도 같은 `users/{uid}.role` 문서 값을 기준으로 역할 권한을 판단한다.
- Kakao 로그인은 앱이 Kakao access token을 Functions에 넘기고, Functions가 Kakao 사용자 정보를 확인한 뒤 Firebase custom token을 발급한다.
- 병원/약국 실좌표 검색은 현재 Android 앱이 Kakao Local REST API를 직접 호출하며, 같은 질의는 6시간 메모리 캐시로 중복 호출을 줄인다.
- 문의 답변, 안심 채팅, 위치 도착, 예약 리마인더, 관리자 후속 알림은 Firestore 문서 변경 또는 스케줄러를 통해 Functions가 처리한다.
