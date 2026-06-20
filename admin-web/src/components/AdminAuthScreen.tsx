import type { FormEvent } from "react";

type AdminAuthScreenProps = {
  isCheckingSession: boolean;
  email: string;
  password: string;
  authError: string;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
};

export function AdminAuthScreen({
  isCheckingSession,
  email,
  password,
  authError,
  onEmailChange,
  onPasswordChange,
  onSubmit,
}: AdminAuthScreenProps) {
  if (isCheckingSession) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4 font-sans text-sm antialiased">
        <div className="rounded-lg border border-gray-200 bg-white px-6 py-5 text-center shadow-sm">
          <p className="text-sm font-semibold text-gray-900">관리자 세션 확인 중</p>
          <p className="mt-2 text-xs text-gray-500">Firebase 인증 상태를 확인하고 있습니다.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4 font-sans text-sm antialiased">
      <div className="w-full max-w-sm rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h1 className="mb-1 text-base font-semibold text-gray-900">관리자 로그인</h1>
        <p className="mb-4 text-xs text-gray-500">
          관리자 계정으로 로그인한 뒤 승인 대시보드에 접근하세요.
        </p>

        <form onSubmit={onSubmit} className="space-y-3">
          <div>
            <label className="mb-1.5 block text-xs font-medium text-gray-700">이메일</label>
            <input
              type="email"
              value={email}
              onChange={(event) => onEmailChange(event.target.value)}
              required
              className="w-full rounded-md border border-gray-300 px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
              placeholder="admin@bodeul.app"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-xs font-medium text-gray-700">비밀번호</label>
            <input
              type="password"
              value={password}
              onChange={(event) => onPasswordChange(event.target.value)}
              required
              className="w-full rounded-md border border-gray-300 px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
              placeholder="bodeul1234"
            />
          </div>

          {authError && (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
              {authError}
            </div>
          )}

          <button
            type="submit"
            className="w-full rounded-md bg-blue-600 py-2 text-sm font-semibold text-white transition hover:bg-blue-700"
          >
            로그인
          </button>
        </form>
      </div>
    </div>
  );
}
