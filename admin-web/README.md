🏢 보들(Bodeul) 관리자 웹 앱 (Admin Dashboard)
보들 서비스의 전반적인 운영을 관리하고 매니저를 승인/관리하는 어드민 페이지입니다.

🚀 주요 기능
매니저 승인 관리: 신규 매니저의 서류(신분증, 자격증)를 검토하고 승인 또는 반려 처리합니다.

사용자 모니터링: 환자, 보호자, 매니저의 정보를 조회하고 관리합니다.

실시간 데이터 연동: Firebase Firestore를 통해 앱 사용자의 데이터를 실시간으로 동기화합니다.

🛠 기술 스택 (Tech Stack)
Frontend: React, TypeScript, Vite

Styling: Tailwind CSS

Backend/Infrastructure: Firebase (Firestore, Authentication, Storage)

IDE/Tools: Cursor AI

📦 프로젝트 시작하기
Bash
# 패키지 설치
npm install

# 로컬 서버 실행
npm run dev
📂 주요 폴더 구조
src/firebase.ts: Firebase 앱 초기화 및 서비스 설정

src/pages/: 각 기능별 페이지 컴포넌트

src/components/: 재사용 가능한 UI 컴포넌트

⚠️ 주의사항
보안: .env 파일에 Firebase API Key가 노출되지 않도록 주의하세요.

규칙: Firestore 보안 규칙에 따라 ADMIN 권한이 있는 계정으로 로그인해야 정상적인 데이터 수정이 가능합니다.