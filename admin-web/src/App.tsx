import React, { useEffect, useState } from 'react';
import { doc, updateDoc, collection, query, where, onSnapshot } from "firebase/firestore";
import { db } from "../firebase";
import { getAuth, signInWithEmailAndPassword, type UserCredential } from "firebase/auth";
import { getDoc, DocumentSnapshot } from "firebase/firestore";

// currentMenu 전환 화면 컴포넌트는 App 내부에서 새로 생성되지 않도록 모듈 상단에 선언
const Dashboard = () => (
  <div>
    <header className="mb-3 text-base font-semibold text-gray-900">
      대시보드 요약
    </header>
    <div className="grid grid-cols-3 gap-3">
      <div className="bg-white p-3 rounded-lg shadow-sm border border-gray-100">
        <p className="text-xs text-gray-500">오늘 예약</p>
        <p className="mt-1 text-lg font-semibold">12건</p>
      </div>
      <div className="bg-white p-3 rounded-lg shadow-sm border border-gray-100">
        <p className="text-xs text-gray-500">신규 가입 매니저</p>
        <p className="mt-1 text-lg font-semibold text-blue-600">5명</p>
      </div>
      <div className="bg-white p-3 rounded-lg shadow-sm border border-gray-100">
        <p className="text-xs text-gray-500">대기 중인 문의</p>
        <p className="mt-1 text-lg font-semibold text-red-500">3건</p>
      </div>
    </div>
  </div>
);

type ManagerDocumentKey = 'idCard' | 'license' | 'criminalRecord';

type ManagerDocumentStatus = Record<ManagerDocumentKey, '미확인' | '확인 완료'>;

type ManagerStatus = '대기' | '검토중' | '승인됨' | '반려';

type Manager = {
  id: string;
  name: string;
  email: string;
  phone: string;
  date: string;
  status: ManagerStatus;
};

function ManagerApproval() {
  const [managers, setManagers] = useState<Manager[]>([]);
  const [selectedManager, setSelectedManager] = useState<Manager | null>(null);
  const [activeDoc, setActiveDoc] = useState<ManagerDocumentKey>('idCard');
  const [docStatus, setDocStatus] = useState<ManagerDocumentStatus>({
    idCard: '미확인',
    license: '미확인',
    criminalRecord: '미확인',
  });
  const [showRejectReason, setShowRejectReason] = useState(false);
  const [rejectReason, setRejectReason] = useState('');

  // 1. 실제 Firestore 데이터 로드 (실시간)
  useEffect(() => {
    // role이 MANAGER인 사용자를 모두 가져옵니다.
    const q = query(
      collection(db, "users"),
      where("role", "==", "MANAGER")
    );
  
    const unsubscribe = onSnapshot(q, (querySnapshot) => {
      if (querySnapshot.empty) {
        console.log("데이터가 비어있습니다. 필드명을 다시 확인하세요.");
        return;
      }
  
      const managerList: Manager[] = querySnapshot.docs.map((doc) => {
        const data = doc.data();
        
        // 상태 값을 한국어로 매핑
        let displayStatus: ManagerStatus = '대기';
        if (data.managerDocumentStatus === 'APPROVED') displayStatus = '승인됨';
        if (data.managerDocumentStatus === 'REJECTED') displayStatus = '반려';
  
        return {
          id: doc.id,
          name: data.name || '이름 없음',
          email: data.email || '',
          phone: data.phone || '',
          // 보내주신 데이터가 1776962919488 형태이므로 아래와 같이 변환합니다.
          date: data.createdAt ? new Date(data.createdAt).toLocaleDateString() : '',
          status: displayStatus,
        };
      });
  
      console.log("가져온 매니저 목록:", managerList);
      setManagers(managerList);
    }, (error) => {
      console.error("Firestore 에러 발생:", error);
    });
  
    return () => unsubscribe();
  }, []);

  const statusBadgeClass: Record<ManagerStatus, string> = {
    대기: 'bg-gray-100 text-gray-700',
    검토중: 'bg-blue-100 text-blue-700',
    승인됨: 'bg-green-100 text-green-700',
    반려: 'bg-red-100 text-red-700',
  };

  const closeModal = () => {
    setSelectedManager(null);
    setActiveDoc('idCard');
    setDocStatus({
      idCard: '미확인',
      license: '미확인',
      criminalRecord: '미확인',
    });
    setShowRejectReason(false);
    setRejectReason('');
  };

  const handleToggleDocStatus = (key: ManagerDocumentKey) => {
    setDocStatus((prev) => ({
      ...prev,
      [key]: prev[key] === '미확인' ? '확인 완료' : '미확인',
    }));

    // 하나라도 확인이 눌린 시점에 상태를 '검토중'으로 변경 (중복 검토 방지 용도)
    if (selectedManager) {
      setManagers((prev) =>
        prev.map((m) =>
          m.id === selectedManager.id && m.status === '대기'
            ? { ...m, status: '검토중' }
            : m,
        ),
      );
      setSelectedManager((prev) =>
        prev && prev.status === '대기' ? { ...prev, status: '검토중' } : prev,
      );
    }
  };

  const allDocsChecked =
    docStatus.idCard === '확인 완료' &&
    docStatus.license === '확인 완료' &&
    docStatus.criminalRecord === '확인 완료';

    const handleApprove = async () => {
      if (!selectedManager) return;
      
      const ok = window.confirm(
        `"${selectedManager.name}" 신청을 정말로 승인하시겠습니까?`,
      );
      if (!ok) return;
    
      try {
        // 1. 파이어베이스 문서 참조 (users 컬렉션의 매니저 ID)
        const userRef = doc(db, "users", selectedManager.id);
    
        // 2. 실제 파이어베이스 데이터 업데이트
        await updateDoc(userRef, {
          managerDocumentStatus: 'APPROVED', // 실제 데이터베이스의 상태값 변경
          updatedAt: Date.now(),             // 수정 시간 기록
          managerDocumentReviewedAt: Date.now(),
          managerDocumentReviewedByName: '관리자'
        });
    
        // 3. 기존의 화면 UI 업데이트 로직
        setManagers((prev) =>
          prev.map((m) =>
            m.id === selectedManager.id ? { ...m, status: '승인됨' } : m,
          ),
        );
    
        alert('해당 매니저가 승인되었습니다.');
        closeModal();
      } catch (error) {
        console.error("승인 처리 중 오류 발생:", error);
        alert("승인 처리에 실패했습니다. 콘솔 로그를 확인해주세요.");
      }
    };

  const handleReject = () => {
    if (!selectedManager) return;

    if (!showRejectReason) {
      setShowRejectReason(true);
      return;
    }

    if (!rejectReason.trim()) {
      alert('반려 사유를 입력해주세요.');
      return;
    }

    const ok = window.confirm(
      `"${selectedManager.name}" 신청을 정말로 반려하시겠습니까?`,
    );
    if (!ok) return;

    // TODO: 실제 반려 API 연동 (rejectReason 포함)
    setManagers((prev) =>
      prev.map((m) =>
        m.id === selectedManager.id ? { ...m, status: '반려' } : m,
      ),
    );
    alert('반려 처리가 완료되었습니다.');
    closeModal();
  };

  const documents: { key: ManagerDocumentKey; label: string; placeholderAlt: string }[] =
    [
      { key: 'idCard', label: '신분증', placeholderAlt: '신분증 이미지' },
      {
        key: 'license',
        label: '요양보호사/간호사 자격증',
        placeholderAlt: '자격증 이미지',
      },
      {
        key: 'criminalRecord',
        label: '범죄경력회보서',
        placeholderAlt: '범죄경력회보서 이미지',
      },
    ];

  return (
    <div className="relative">
      <header className="mb-3">
        <h1 className="text-base font-semibold text-gray-900">매니저 가입 승인</h1>
        <p className="text-xs text-gray-500 mt-1">
          매니저 신청자의 서류를 고밀도로 검토합니다.
        </p>
      </header>
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden text-left">
        <table className="w-full">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-3 py-2 text-xs font-semibold text-left text-gray-500">
                신청자명
              </th>
              <th className="px-3 py-2 text-xs font-semibold text-left text-gray-500">
                이메일
              </th>
              <th className="px-3 py-2 text-xs font-semibold text-left text-gray-500">
                전화번호
              </th>
              <th className="px-3 py-2 text-xs font-semibold text-left text-gray-500">
                신청일
              </th>
              <th className="px-3 py-2 text-xs font-semibold text-left text-gray-500">
                상태
              </th>
              <th className="px-3 py-2 text-xs font-semibold text-right text-gray-500">
                서류
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {managers.map((m) => (
              <tr key={m.id} className="hover:bg-gray-50">
                <td className="px-3 py-2 text-sm text-gray-900">{m.name}</td>
                <td className="px-3 py-2 text-sm text-gray-900">{m.email}</td>
                <td className="px-3 py-2 text-sm text-gray-900">{m.phone}</td>
                <td className="px-3 py-2 text-xs text-gray-500">{m.date}</td>
                <td className="px-3 py-2">
                  <span
                    className={`inline-flex px-2.5 py-0.5 rounded-full text-[11px] font-semibold ${
                      statusBadgeClass[m.status]
                    }`}
                  >
                    {m.status}
                  </span>
                </td>
                <td className="px-3 py-2 text-right">
                  <button
                    type="button"
                    onClick={() => setSelectedManager(m)}
                    className="inline-flex items-center px-2.5 py-1 rounded-md text-xs font-semibold bg-blue-600 text-white hover:bg-blue-700 transition"
                  >
                    서류 확인
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 서류 확인 모달 */}
      {selectedManager && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/50">
          <div className="w-full max-w-6xl max-h-[90vh] mx-4 bg-white rounded-2xl shadow-xl overflow-hidden flex flex-col text-sm">
            {/* 헤더: 신청자 기본 정보 */}
            <div className="flex items-center justify-between border-b px-4 py-3">
              <div>
                <h2 className="text-base font-semibold text-gray-900">
                  서류 확인 - {selectedManager.name}
                </h2>
                <p className="mt-1 text-xs text-gray-500">
                  이메일 {selectedManager.email} · 전화번호 {selectedManager.phone}
                </p>
              </div>
              <button
                type="button"
                onClick={closeModal}
                className="text-gray-400 hover:text-gray-600 transition"
                aria-label="닫기"
              >
                ✕
              </button>
            </div>

            <div className="flex flex-col md:flex-row flex-1 min-h-0">
              {/* 좌측: 서류 탭 / 목록 */}
              <div className="md:w-60 border-b md:border-b-0 md:border-r bg-gray-50">
                <ul className="flex md:flex-col text-sm">
                  {documents.map((doc) => (
                    <li key={doc.key} className="flex-1">
                      <button
                        type="button"
                        onClick={() => setActiveDoc(doc.key)}
                        className={`w-full flex items-center justify-between px-3 py-2.5 text-sm border-b md:border-b-0 md:border-b-transparent md:border-l-4 ${
                          activeDoc === doc.key
                            ? 'bg-white md:bg-blue-50 text-blue-700 md:border-blue-600'
                            : 'text-gray-700 hover:bg-gray-100 md:hover:bg-gray-100 md:border-transparent'
                        }`}
                      >
                        <span className="whitespace-nowrap">{doc.label}</span>
                        <span
                          className={`ml-2 inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                            docStatus[doc.key] === '확인 완료'
                              ? 'bg-green-100 text-green-700'
                              : 'bg-gray-100 text-gray-600'
                          }`}
                        >
                          {docStatus[doc.key]}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>

              {/* 우측: 서류 이미지 영역 */}
              <div className="flex-1 px-5 py-4 min-w-0 flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-gray-900">
                    {
                      documents.find((d) => d.key === activeDoc)?.label ??
                      '서류 상세'
                    }
                  </h3>
                  <button
                    type="button"
                    onClick={() => handleToggleDocStatus(activeDoc)}
                    className={`inline-flex items-center px-3 py-1.5 rounded-full text-xs font-semibold border transition ${
                      docStatus[activeDoc] === '확인 완료'
                        ? 'bg-green-50 text-green-700 border-green-200'
                        : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                    }`}
                  >
                    {docStatus[activeDoc] === '확인 완료' ? '확인 취소' : '확인 완료 표시'}
                  </button>
                </div>

                <div className="flex-1 rounded-xl border border-dashed border-gray-300 bg-gray-50 flex items-center justify-center overflow-hidden">
                  {/* TODO: 추후 Firebase Storage 이미지 URL을 연결 */}
                  <div className="text-center px-4">
                    <div className="text-4xl mb-3">📄</div>
                    <p className="text-sm font-medium text-gray-800">
                      서류 이미지 플레이스홀더
                    </p>
                    <p className="mt-1 text-xs text-gray-500">
                      나중에 Firebase Storage에 업로드된 이미지를 이 영역에 표시합니다.
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* 하단: 승인/반려 및 반려 사유 */}
            <div className="border-t px-5 py-3 space-y-2">
              {showRejectReason && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    반려 사유
                  </label>
                  <textarea
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    rows={3}
                    className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
                    placeholder="예) 자격증 사진이 흐릿합니다, 범죄경력회보서 날짜가 만료되었습니다 등"
                  />
                </div>
              )}

              <div className="flex items-center justify-between">
                <div className="text-xs text-gray-500">
                  최종 승인/반려 전, 각 서류를 모두 확인했는지 다시 한 번 검토해주세요.
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={handleReject}
                    className="px-4 py-2 rounded-lg border border-red-200 text-sm font-semibold text-red-600 bg-red-50 hover:bg-red-100 transition"
                  >
                    {showRejectReason ? '반려 확정' : '반려'}
                  </button>
                  <button
                    type="button"
                    onClick={handleApprove}
                    disabled={!allDocsChecked}
                    className={`px-4 py-2 rounded-lg text-sm font-semibold transition ${
                      allDocsChecked
                        ? 'bg-blue-600 text-white hover:bg-blue-700'
                        : 'bg-blue-400 text-white cursor-not-allowed disabled:opacity-50'
                    }`}
                  >
                    최종 승인
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function App() {
  // 현재 어떤 메뉴를 보고 있는지 저장하는 상태 (기본값: 대시보드)
  const [currentMenu, setCurrentMenu] = useState('dashboard');

  // 로그인 여부를 관리하는 상태 (기본값: 로그아웃)
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  // 로그인 폼 입력값
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const STORAGE_KEY = 'isLoggedIn';

  // 앱이 처음 로드될 때 localStorage에 저장된 로그인 상태를 복구
  useEffect(() => {
    try {
      const stored = window.localStorage.getItem(STORAGE_KEY);
      if (stored === 'true') {
        // 린터 경고 방지를 위해 effect 실행 직후가 아닌 비동기 틱에 상태를 반영
        Promise.resolve().then(() => setIsLoggedIn(true));
      }
    } catch {
      // localStorage 접근이 실패해도 로그인 UI는 정상 동작해야 함
    }
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
  
    const auth = getAuth();
  
    try {
      // 1. UserCredential 타입을 명시하여 인증 정보 처리
      const userCredential: UserCredential = await signInWithEmailAndPassword(auth, email, password);
      const user = userCredential.user;
  
      // 2. DocumentSnapshot 타입을 사용하여 Firestore 문서 처리
      const userDocRef = doc(db, "users", user.uid);
      const userDoc: DocumentSnapshot = await getDoc(userDocRef);
  
      if (userDoc.exists()) {
        const userData = userDoc.data();
        
        // role 필드가 존재하고 ADMIN인지 확인 (userData는 기본적으로 DocumentData 타입)
        if (userData && userData.role === 'ADMIN') {
          setIsLoggedIn(true);
          window.localStorage.setItem(STORAGE_KEY, 'true');
          alert('관리자로 로그인되었습니다.');
        } else {
          await auth.signOut();
          alert('접근 권한이 없습니다. 관리자 계정으로 로그인해주세요.');
        }
      } else {
        alert('사용자 정보가 존재하지 않습니다.');
      }
    } catch (error) {
      // 3. 에러 객체를 FirebaseError로 타입 캐스팅하여 처리
      const firebaseError = error as { code: string | undefined };
      console.error("Login Error Code:", firebaseError.code);
      
      alert('아이디 또는 비밀번호가 틀렸습니다.');
    }
  };

  if (!isLoggedIn) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4 font-sans antialiased text-sm">
        <div className="w-full max-w-sm bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h1 className="text-base font-semibold text-gray-900 mb-1">관리자 로그인</h1>
          <p className="text-xs text-gray-500 mb-4">
            로그인하여 대시보드에 접근하세요.
          </p>

          <form onSubmit={handleLogin} className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1.5">
                이메일
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                className="w-full px-2.5 py-1.5 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
                placeholder="admin@bodeul.app"
              />
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1.5">
                비밀번호
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                className="w-full px-2.5 py-1.5 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
                placeholder="bodeul1234"
              />
            </div>

            <button
              type="submit"
              className="w-full bg-blue-600 text-white py-2 rounded-md text-sm font-semibold hover:bg-blue-700 transition"
            >
              로그인
            </button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen bg-gray-50 font-sans antialiased text-sm">
      {/* 사이드바 */}
      <aside className="w-56 bg-slate-900 text-white p-4 shadow-lg">
        <h2 className="text-sm font-semibold mb-4 text-blue-400 tracking-tight">
          bodeul Admin
        </h2>
        <nav className="space-y-1 text-xs">
          <div
            onClick={() => setCurrentMenu('dashboard')}
            className={`px-3 py-2 rounded-md cursor-pointer transition ${
              currentMenu === 'dashboard'
                ? 'bg-blue-600'
                : 'hover:bg-slate-800'
            }`}
          >
            📊 대시보드
          </div>
          <div
            onClick={() => setCurrentMenu('approval')}
            className={`px-3 py-2 rounded-md cursor-pointer transition ${
              currentMenu === 'approval'
                ? 'bg-blue-600'
                : 'hover:bg-slate-800'
            }`}
          >
            📄 매니저 승인
          </div>
        </nav>
      </aside>

      {/* 메인 영역: currentMenu 값에 따라 다른 화면을 보여줌 */}
      <main className="flex-1 p-6">
        <header className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">
              {currentMenu === 'dashboard' ? '대시보드' : '매니저 승인'}
            </h2>
          </div>

          <button
            type="button"
            onClick={() => {
              setIsLoggedIn(false);
              try {
                window.localStorage.setItem(STORAGE_KEY, 'false');
              } catch {
                // ignore
              }
            }}
            className="bg-white border border-gray-300 px-3 py-1.5 rounded-md text-xs shadow-sm hover:bg-gray-50"
          >
            로그아웃
          </button>
        </header>

        {currentMenu === 'dashboard' && <Dashboard />}
        {currentMenu === 'approval' && <ManagerApproval />}
      </main>
    </div>
  );
}

export default App;
