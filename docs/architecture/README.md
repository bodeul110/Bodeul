# 아키텍처 문서

앱, 관리자 웹, Firebase, 운영 도구의 구조와 데이터 계약을 설명한다.

## 핵심 문서

- [설계 판단 기록 규칙](decision-log.md)
  - 작업 목적, 선택 방식, 대안, 선택 이유, 리스크를 남기는 팀 운영 기준
- [현재 인프라 구성도](infra-overview.md)
  - 다음 회의에서 한 장으로 설명할 Android, 관리자 웹, Firebase, 운영 도구 흐름
- [Firebase 선택 근거](why-firebase.md)
  - Firebase 중심 BaaS 구조를 유지하는 이유와 대안, 전환 조건
- [Firestore 선택 근거](why-firestore.md)
  - Firestore를 MySQL/PostgreSQL/Supabase 대신 쓰는 이유와 보완 조건
- [Android 앱 구조 설명](app-architecture.md)
  - Activity, Coordinator, Binder, Repository, Mock/Firebase 모드 역할
- [관리자 웹 역할 설명](admin-web-architecture.md)
  - 관리자 웹을 서비스 신뢰성 운영 도구로 둔 이유
- [관리자 웹 데이터 계약](admin-web-data-contract.md)
  - 레포 분리 전 고정해야 하는 Auth, Firestore, Storage, Functions 계약
- [인프라 리스크와 보완 계획](infra-risk-review.md)
  - Rules, App Check, 백업/복원, API Key, 비용, Hosting 리스크
- [멘토 Q&A 준비](mentor-qna.md)
  - Firebase, Firestore, 앱 구조, 관리자 웹, 보안/운영 예상 질문 답변
- [인프라 개요](infrastructure.md)
  - Android 앱, 관리자 웹, Firebase, 운영 도구, CI 구성
- [시스템 아키텍처 다이어그램](system-architecture-diagram.md)
  - Android, 관리자 웹, Firebase, Functions, FCM, Kakao API 흐름
- [DB 선택 근거](database-selection.md)
  - Firestore 선택 이유와 MySQL/PostgreSQL/Supabase/Realtime Database 대안 비교
- [아키텍처 개요](overview.md)
  - 화면/도메인/데이터 역할 분리 기준
- [데이터 및 API 문서](data-api.md)
  - Firestore 문서 구조와 앱/웹 저장 계약

## 같이 볼 문서

- [Firebase 설정](../operations/firebase/setup.md)
- [Firestore 보안 정리](../security/firestore-hardening.md)
- [현재 구현 상태](../status/implementation-status.md)
