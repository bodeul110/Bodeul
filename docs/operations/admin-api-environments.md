# 관리자 API 환경 문서 이전 안내

기준일: 2026-07-17

이 문서는 과거 Vite 관리자 웹이 Node `bodeul-api`를 호출하던 환경변수와 CORS 기준을 설명했다. 해당 Node API와 메인 저장소 관리자 웹 중복본은 실제 Next.js 대체 검증 후 제거했다.

현재 기준은 다음 문서에서 관리한다.

- [관리자 웹 환경 기준](admin-web-environments.md)
- [관리자 웹 구조](../architecture/admin-web-architecture.md)
- [PostgreSQL API 경계](../architecture/postgres-api-boundary.md)
- [Issue 159 Node API 종료 기록](../reports/issue-159-node-api-retirement-audit-2026-07-16.md)

새 관리자 요청은 same-origin Next.js Route Handler를 사용하므로 과거 `VITE_BODEUL_API_BASE_URL`, `VITE_BODEUL_DATA_BACKEND`와 Node CORS allow-list를 다시 도입하지 않는다.
