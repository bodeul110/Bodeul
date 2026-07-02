import {useCallback, useMemo, useState} from "react";
import type {User as FirebaseUser} from "firebase/auth";

import {
  BodeulApiError,
  fetchAdminHospitalGuides,
  type BodeulDataBackend,
  type HospitalGuideItem,
} from "../bodeulApi";

type HospitalGuideApiPanelProps = {
  readonly currentUser: FirebaseUser | null;
  readonly dataBackend: BodeulDataBackend;
  readonly apiBaseUrl: string;
};

type LoadStatus = "idle" | "loading" | "ready" | "error";

export function HospitalGuideApiPanel({
  currentUser,
  dataBackend,
  apiBaseUrl,
}: HospitalGuideApiPanelProps) {
  const [items, setItems] = useState<readonly HospitalGuideItem[]>([]);
  const [status, setStatus] = useState<LoadStatus>("idle");
  const [message, setMessage] = useState("");
  const isApiMode = dataBackend === "api";
  const canLoad = Boolean(isApiMode && apiBaseUrl && currentUser);
  const statusLabel = useMemo(() => {
    if (!isApiMode) {
      return "Firebase 모드";
    }
    if (!apiBaseUrl) {
      return "API URL 미설정";
    }
    return "API 모드";
  }, [apiBaseUrl, isApiMode]);

  const loadHospitalGuides = useCallback(async () => {
    if (!isApiMode) {
      setItems([]);
      setStatus("idle");
      setMessage("현재는 Firebase 모드입니다. API 전환 검증은 VITE_BODEUL_DATA_BACKEND=api에서 실행합니다.");
      return;
    }

    if (!apiBaseUrl) {
      setItems([]);
      setStatus("error");
      setMessage("VITE_BODEUL_API_BASE_URL이 설정되지 않았습니다.");
      return;
    }

    if (!currentUser) {
      setItems([]);
      setStatus("idle");
      setMessage("관리자 세션을 확인한 뒤 API를 조회합니다.");
      return;
    }

    setStatus("loading");
    setMessage("");

    try {
      const payload = await fetchAdminHospitalGuides(currentUser, {baseUrl: apiBaseUrl, limit: 50});
      setItems(payload.items);
      setStatus("ready");
      setMessage(`PostgreSQL 병원 가이드 ${payload.items.length}건을 조회했습니다.`);
    } catch (error) {
      setItems([]);
      setStatus("error");
      setMessage(resolveApiErrorMessage(error));
    }
  }, [apiBaseUrl, currentUser, isApiMode]);
  const visibleMessage = message || resolveDefaultMessage(isApiMode, apiBaseUrl, currentUser);

  return (
    <div className="space-y-4">
      <header className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div>
          <h1 className="text-base font-semibold text-gray-900">병원 가이드 API 검증</h1>
          <p className="mt-1 text-xs text-gray-500">
            관리자 웹이 Firebase ID token으로 bodeul-api의 read API를 호출하는지 확인합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={() => {
            void loadHospitalGuides();
          }}
          disabled={!canLoad || status === "loading"}
          className="rounded-md border border-gray-300 bg-white px-3 py-2 text-xs font-medium text-gray-700 shadow-sm transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {status === "loading" ? "조회 중" : "조회"}
        </button>
      </header>

      <div className="grid gap-3 md:grid-cols-3">
        <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-gray-400">Backend</p>
          <p className="mt-2 text-sm font-semibold text-gray-900">{statusLabel}</p>
          <p className="mt-1 text-xs text-gray-500">`VITE_BODEUL_DATA_BACKEND` 기준</p>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-gray-400">API Base URL</p>
          <p className="mt-2 break-all text-sm font-semibold text-gray-900">{apiBaseUrl || "미설정"}</p>
          <p className="mt-1 text-xs text-gray-500">브라우저 호출 대상</p>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-gray-400">Result</p>
          <p className="mt-2 text-sm font-semibold text-gray-900">{items.length}건</p>
          <p className="mt-1 text-xs text-gray-500">`/admin/hospital-guides?limit=50` 응답</p>
        </div>
      </div>

      {visibleMessage && (
        <div className={`rounded-md border px-3 py-2 text-xs ${
          status === "error"
            ? "border-red-200 bg-red-50 text-red-700"
            : "border-blue-200 bg-blue-50 text-blue-700"
        }`}
        >
          {visibleMessage}
        </div>
      )}

      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-200 px-4 py-3">
          <h2 className="text-sm font-semibold text-gray-900">PostgreSQL 병원 가이드 조회 결과</h2>
          <p className="mt-1 text-xs text-gray-500">
            Firestore 화면 전환 전에 응답 수, 병원/진료과 이름, 단계 수, 갱신 시각만 먼저 비교합니다.
          </p>
        </div>
        {status === "loading" && (
          <p className="px-4 py-6 text-sm text-gray-500">병원 가이드를 불러오는 중입니다.</p>
        )}
        {status !== "loading" && items.length === 0 && (
          <p className="px-4 py-6 text-sm text-gray-500">
            표시할 병원 가이드가 없습니다.
          </p>
        )}
        {items.length > 0 && (
          <div className="divide-y divide-gray-100">
            {items.map((item) => (
              <article key={item.id} className="grid gap-3 px-4 py-4 lg:grid-cols-[1.2fr_0.8fr_1fr]">
                <div>
                  <p className="text-sm font-semibold text-gray-900">{item.hospitalName}</p>
                  <p className="mt-1 text-xs text-gray-500">{item.departmentName}</p>
                </div>
                <div>
                  <p className="text-xs font-medium text-gray-500">단계</p>
                  <p className="mt-1 text-sm text-gray-900">{item.steps.length}개</p>
                  {item.steps.length > 0 && (
                    <p className="mt-1 text-xs text-gray-500">{summarizeSteps(item.steps)}</p>
                  )}
                </div>
                <div>
                  <p className="text-xs font-medium text-gray-500">갱신 시각</p>
                  <p className="mt-1 text-sm text-gray-900">{formatApiDateTime(item.updatedAt)}</p>
                  <p className="mt-1 break-all text-[11px] text-gray-400">{item.id}</p>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function resolveDefaultMessage(isApiMode: boolean, apiBaseUrl: string, currentUser: FirebaseUser | null): string {
  if (!isApiMode) {
    return "현재는 Firebase 모드입니다. API 전환 검증은 VITE_BODEUL_DATA_BACKEND=api에서 실행합니다.";
  }

  if (!apiBaseUrl) {
    return "VITE_BODEUL_API_BASE_URL이 설정되지 않았습니다.";
  }

  if (!currentUser) {
    return "관리자 세션을 확인한 뒤 API를 조회합니다.";
  }

  return "조회 버튼을 눌러 bodeul-api 병원 가이드 응답을 확인합니다.";
}

function resolveApiErrorMessage(error: unknown): string {
  if (error instanceof BodeulApiError) {
    const statusText = error.statusCode ? `HTTP ${error.statusCode}` : "설정 오류";
    return `${statusText}: ${error.message}`;
  }

  return "병원 가이드 API 조회 중 알 수 없는 오류가 발생했습니다.";
}

function summarizeSteps(steps: readonly unknown[]): string {
  return steps
    .slice(0, 3)
    .map((step, index) => readStepTitle(step) || `단계 ${index + 1}`)
    .join(" · ");
}

function readStepTitle(step: unknown): string {
  if (!step || typeof step !== "object" || Array.isArray(step)) {
    return "";
  }

  const maybeTitle = (step as {readonly title?: unknown}).title;
  return typeof maybeTitle === "string" ? maybeTitle.trim() : "";
}

function formatApiDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value || "-";
  }

  return date.toLocaleString("ko-KR");
}
