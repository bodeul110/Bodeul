# 아키텍처 문서

앱, 관리자 웹, Firebase, 운영 도구의 구조와 데이터 계약을 설명한다.

## 핵심 문서

- [설계 판단 기록 규칙](decision-log.md)
  - 작업 목적, 선택 방식, 대안, 선택 이유, 리스크를 남기는 팀 운영 기준
- [현재 인프라 구성도](infra-overview.md)
  - Android, Next.js 관리자 서버, Spring Core API, Firebase와 Supabase PostgreSQL의 현재 흐름
- [Firebase 선택 근거](why-firebase.md)
  - Firebase 중심 BaaS 구조를 유지하는 이유와 대안, 전환 조건
- [Firestore 선택 근거](why-firestore.md)
  - Firestore를 MySQL/PostgreSQL/Supabase 대신 쓰는 이유와 보완 조건
- [PostgreSQL 운영 전환 결정](postgres-operational-transition.md)
  - 멘토 피드백 이후 Firebase 인프라는 유지하고 Supabase PostgreSQL로 운영 DB를 옮기는 결정
- [목표 인프라 구조](target-infrastructure.md)
  - Next.js 관리자 서버, Spring Core API, 공용 Supabase PostgreSQL, Firebase 유지 범위와 전환 순서
- [Kakao Local Core API 경계](kakao-local-core-api.md)
  - 병원·약국 검색 API 계약, 서버 캐시와 호출 제한, Android rollback 제거 조건
- [예약 Core API 전환 계약](appointment-core-api.md)
  - 예약 운영 테이블, API·인가·가격·버전 충돌과 Android cutover 경계
- [매칭·동행·리포트 PostgreSQL 전환 계약](companion-session-core-api.md)
  - 세션·리포트·후속 처리, 관리자 배정 함수와 단계별 cutover 경계
- [PostgreSQL API 경계 기준](postgres-api-boundary.md)
  - Supabase PostgreSQL을 앱/관리자 웹에서 직접 쓰지 않고 얇은 API 서버로 연결하는 기준
- [PostgreSQL schema 초안](postgres-schema-draft.sql)
  - Firestore 운영 데이터를 PostgreSQL로 옮기기 위한 초기 테이블 초안
- [Android 앱 구조 설명](app-architecture.md)
  - Activity, Coordinator, Binder, Repository, Mock/Firebase 모드 역할
- [관리자 웹 역할 설명](admin-web-architecture.md)
  - 관리자 웹을 서비스 신뢰성 운영 도구로 둔 이유
- [관리자 웹 데이터 계약](admin-web-data-contract.md)
  - 레포 분리 전 고정해야 하는 Auth, Firestore, Storage, Functions 계약
- [관리자 API 초기 응답 계약](admin-api-contract.md)
  - 종료된 Node prototype에서 Next.js로 이관한 초기 인증과 응답 계약 기록
- [인프라 리스크와 보완 계획](infra-risk-review.md)
  - 이중 데이터 원본, Realtime, App Check, 백업/복원, 보관·파기와 비용 리스크
- [멘토 Q&A 준비](mentor-qna.md)
  - Firebase, Firestore, 앱 구조, 관리자 웹, 보안/운영 예상 질문 답변
- [인프라 개요](infrastructure.md)
  - Android 앱, Next.js 관리자 서버, Spring Core API, Firebase, Supabase PostgreSQL과 CI 구성
- [시스템 아키텍처 다이어그램](system-architecture-diagram.md)
  - 현재 구현과 목표 인프라의 Android, 관리자 웹, Firebase, PostgreSQL, Kakao API 흐름
- [DB 선택 근거](database-selection.md)
  - Firestore 선택 이유와 MySQL/PostgreSQL/Supabase/Realtime Database 대안 비교
- [아키텍처 개요](overview.md)
  - 화면/도메인/데이터 역할 분리 기준
- [데이터 및 API 문서](data-api.md)
  - Firestore 문서 구조와 앱/웹 저장 계약

## 같이 볼 문서

- [Firebase 설정](../operations/firebase/setup.md)
- [Spring Core API 인프라 런북](../operations/core-api-infrastructure-runbook.md)
- [Firestore 보안 정리](../security/firestore-hardening.md)
- [현재 구현 상태](../status/implementation-status.md)
