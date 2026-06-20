import type { ReactNode } from "react";

type MenuKey = "dashboard" | "approval";

type AdminShellProps = {
  adminName: string;
  currentMenu: MenuKey;
  managerLoadError: string;
  onMenuChange: (menu: MenuKey) => void;
  onLogout: () => void;
  children: ReactNode;
};

export function AdminShell({
  adminName,
  currentMenu,
  managerLoadError,
  onMenuChange,
  onLogout,
  children,
}: AdminShellProps) {
  return (
    <div className="flex min-h-screen bg-gray-50 font-sans text-sm antialiased">
      <aside className="w-56 bg-slate-900 p-4 text-white shadow-lg">
        <h2 className="mb-4 text-sm font-semibold tracking-tight text-blue-400">
          bodeul Admin
        </h2>
        <p className="mb-4 text-xs text-slate-300">{adminName}</p>
        <nav className="space-y-1 text-xs">
          <button
            type="button"
            onClick={() => onMenuChange("dashboard")}
            className={`w-full rounded-md px-3 py-2 text-left transition ${
              currentMenu === "dashboard" ? "bg-blue-600" : "hover:bg-slate-800"
            }`}
          >
            대시보드
          </button>
          <button
            type="button"
            onClick={() => onMenuChange("approval")}
            className={`w-full rounded-md px-3 py-2 text-left transition ${
              currentMenu === "approval" ? "bg-blue-600" : "hover:bg-slate-800"
            }`}
          >
            매니저 승인
          </button>
        </nav>
      </aside>

      <main className="flex-1 p-6">
        <header className="mb-4 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">
              {currentMenu === "dashboard" ? "대시보드" : "매니저 승인"}
            </h2>
          </div>

          <button
            type="button"
            onClick={onLogout}
            className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-xs shadow-sm hover:bg-gray-50"
          >
            로그아웃
          </button>
        </header>

        {managerLoadError && (
          <div className="mb-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
            {managerLoadError}
          </div>
        )}

        {children}
      </main>
    </div>
  );
}
