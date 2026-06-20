type ManagerDocumentKey = "idCard" | "license" | "criminalRecord";
type ReviewStatus = "APPROVED" | "REJECTED";
type DocumentPreview = {
  status: string;
  fileName: string;
  contentType: string;
  downloadUrl: string;
  fullPath: string;
  uploadedAtLabel: string;
  message: string;
};

type ManagerReviewModalProps = {
  selectedManager: {
    id: string;
    name: string;
    email: string;
    phone: string;
    date: string;
    status: string;
    documentSummary: string;
    reviewNote: string;
  };
  activeDoc: ManagerDocumentKey;
  setActiveDoc: (key: ManagerDocumentKey) => void;
  docStatus: Record<ManagerDocumentKey, string>;
  rejectReason: string;
  setRejectReason: (value: string) => void;
  isSubmitting: boolean;
  allDocsChecked: boolean;
  hasDocumentSummary: boolean;
  checkedCount: number;
  totalDocumentCount: number;
  selectedManagerUploadedCount: number;
  selectedManagerMissingCount: number;
  documentPreviews: Record<ManagerDocumentKey, DocumentPreview>;
  documents: Array<{ key: ManagerDocumentKey; label: string; helper: string }>;
  statusBadgeClass: Record<string, string>;
  previewBadgeClass: Record<string, string>;
  previewBadgeLabel: Record<string, string>;
  documentLabelMap: Record<ManagerDocumentKey, string>;
  onClose: () => void;
  onToggleDocStatus: (key: ManagerDocumentKey) => void;
  onSaveReview: (status: ReviewStatus) => Promise<void>;
  isImageDocument: (preview: DocumentPreview) => boolean;
  isPdfDocument: (preview: DocumentPreview) => boolean;
  getDocumentFolderPaths: (managerId: string, documentKey: ManagerDocumentKey) => string[];
  getFirebaseStorageConsoleFolderUrl: (
    managerId: string,
    documentKey: ManagerDocumentKey,
    explicitPath?: string,
  ) => string;
};

export function ManagerReviewModal({
  selectedManager,
  activeDoc,
  setActiveDoc,
  docStatus,
  rejectReason,
  setRejectReason,
  isSubmitting,
  allDocsChecked,
  hasDocumentSummary,
  checkedCount,
  totalDocumentCount,
  selectedManagerUploadedCount,
  selectedManagerMissingCount,
  documentPreviews,
  documents,
  statusBadgeClass,
  previewBadgeClass,
  previewBadgeLabel,
  documentLabelMap,
  onClose,
  onToggleDocStatus,
  onSaveReview,
  isImageDocument,
  isPdfDocument,
  getDocumentFolderPaths,
  getFirebaseStorageConsoleFolderUrl,
}: ManagerReviewModalProps) {
  const activePreview = documentPreviews[activeDoc];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-6">
      <div className="max-h-[90vh] w-full max-w-[1280px] overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">{selectedManager.name} 서류 심사</h2>
            <p className="mt-1 text-xs text-gray-500">
              제출 요약과 Storage 원본을 함께 확인하고 현재 상태를 저장하세요.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-xs text-gray-600 hover:bg-gray-100"
          >
            닫기
          </button>
        </div>

        <div className="grid gap-3 border-b border-gray-100 bg-gray-50 px-6 py-4 md:grid-cols-4">
          <div className="rounded-xl border border-gray-200 bg-white px-4 py-3 shadow-sm">
            <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-gray-400">현재 상태</p>
            <span className={`mt-2 inline-flex rounded-full px-2 py-1 text-xs font-medium ${statusBadgeClass[selectedManager.status]}`}>
              {selectedManager.status}
            </span>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white px-4 py-3 shadow-sm">
            <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-gray-400">원본 파일</p>
            <p className="mt-2 text-lg font-semibold text-gray-900">{selectedManagerUploadedCount}/{totalDocumentCount}</p>
            <p className="mt-1 text-xs text-gray-500">
              {selectedManagerMissingCount === 0 ? "필수 파일 업로드 완료" : `${selectedManagerMissingCount}개 파일이 더 필요합니다.`}
            </p>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white px-4 py-3 shadow-sm">
            <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-gray-400">체크리스트 진행</p>
            <p className="mt-2 text-lg font-semibold text-gray-900">{checkedCount}/{totalDocumentCount}</p>
            <p className="mt-1 text-xs text-gray-500">실제 원본을 확인한 항목 수</p>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white px-4 py-3 shadow-sm">
            <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-gray-400">제출 상태</p>
            <p className="mt-2 text-lg font-semibold text-gray-900">{hasDocumentSummary ? "요약 제출됨" : "요약 미제출"}</p>
            <p className="mt-1 text-xs text-gray-500">가입일 {selectedManager.date || "-"}</p>
          </div>
        </div>

        <div className="grid gap-6 px-6 py-5 lg:grid-cols-[280px_minmax(0,1fr)_320px]">
          <section className="space-y-4 self-start rounded-2xl border border-gray-200 bg-gray-50 p-4">
            <h3 className="text-sm font-semibold text-gray-900">제출 정보</h3>
            <div className="space-y-2 text-xs text-gray-600">
              <p><span className="font-medium text-gray-800">이메일</span> {selectedManager.email || "-"}</p>
              <p><span className="font-medium text-gray-800">전화번호</span> {selectedManager.phone || "-"}</p>
              <p><span className="font-medium text-gray-800">가입일</span> {selectedManager.date || "-"}</p>
              <p><span className="font-medium text-gray-800">현재 상태</span> {selectedManager.status}</p>
            </div>
            <div className="rounded-xl border border-gray-200 bg-white p-4 text-xs text-gray-700 shadow-sm">
              <p className="mb-1 font-medium text-gray-900">제출 요약</p>
              <p className="whitespace-pre-wrap leading-5">
                {selectedManager.documentSummary || "제출된 서류 요약이 없습니다."}
              </p>
            </div>
            {selectedManager.reviewNote && (
              <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs text-amber-800">
                <p className="mb-1 font-medium">최근 검토 메모</p>
                <p className="whitespace-pre-wrap leading-5">{selectedManager.reviewNote}</p>
              </div>
            )}
          </section>

          <section className="space-y-4">
            <div className="grid gap-3 md:grid-cols-3">
              {documents.map((documentInfo) => {
                const preview = documentPreviews[documentInfo.key];
                const isActive = activeDoc === documentInfo.key;
                return (
                  <button
                    key={documentInfo.key}
                    type="button"
                    onClick={() => setActiveDoc(documentInfo.key)}
                    className={`rounded-xl border px-4 py-3 text-left text-xs transition ${
                      isActive
                        ? "border-blue-600 bg-blue-50 text-blue-700 shadow-sm"
                        : "border-gray-200 bg-white text-gray-700 hover:border-gray-300"
                    }`}
                  >
                    <p className="font-semibold">{documentInfo.label}</p>
                    <p className="mt-1 text-[11px] leading-4 text-gray-500">{documentInfo.helper}</p>
                    <p className="mt-2 truncate text-[11px] text-gray-500">
                      {preview.fileName || "원본 파일 없음"}
                    </p>
                    <span className={`mt-2 inline-flex rounded-full px-2 py-1 text-[11px] font-medium ${previewBadgeClass[preview.status]}`}>
                      {previewBadgeLabel[preview.status]}
                    </span>
                  </button>
                );
              })}
            </div>

            <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
              <div className="mb-4 flex flex-col gap-3 border-b border-gray-100 pb-4 md:flex-row md:items-start md:justify-between">
                <div>
                  <h3 className="text-base font-semibold text-gray-900">{documentLabelMap[activeDoc]} 원본</h3>
                  <p className="mt-1 text-xs text-gray-500">
                    경로 규약: <span className="font-mono">{getDocumentFolderPaths(selectedManager.id, activeDoc).join(" 또는 ")}/파일명</span>
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`rounded-full px-2 py-1 text-[11px] font-medium ${previewBadgeClass[activePreview.status]}`}>
                    {previewBadgeLabel[activePreview.status]}
                  </span>
                  <a
                    href={getFirebaseStorageConsoleFolderUrl(selectedManager.id, activeDoc, activePreview.fullPath)}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex rounded-md border border-gray-200 bg-white px-3 py-2 text-xs font-medium text-gray-600 hover:bg-gray-100"
                  >
                    Storage 폴더 열기
                  </a>
                </div>
              </div>

              {activePreview.status === "loading" && (
                <div className="rounded-lg border border-blue-100 bg-blue-50 px-4 py-10 text-center text-sm text-blue-700">
                  Storage 원본을 불러오는 중입니다.
                </div>
              )}

              {activePreview.status === "missing" && (
                <div className="space-y-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                  <p>{activePreview.message}</p>
                  <a
                    href={getFirebaseStorageConsoleFolderUrl(selectedManager.id, activeDoc, activePreview.fullPath)}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex rounded-md border border-amber-300 bg-white px-3 py-2 text-xs font-medium text-amber-700 hover:bg-amber-100"
                  >
                    Firebase 콘솔에서 폴더 열기
                  </a>
                </div>
              )}

              {activePreview.status === "error" && (
                <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                  <p>{activePreview.message}</p>
                  {activePreview.fullPath && (
                    <p className="text-xs text-red-600">
                      저장 경로: <span className="font-mono">{activePreview.fullPath}</span>
                    </p>
                  )}
                </div>
              )}

              {activePreview.status === "ready" && (
                <div className="space-y-4">
                  <div className="grid gap-2 rounded-xl border border-gray-200 bg-gray-50 p-4 text-xs text-gray-600 md:grid-cols-2">
                    <p><span className="font-medium text-gray-800">파일명</span> {activePreview.fileName || "-"}</p>
                    <p><span className="font-medium text-gray-800">형식</span> {activePreview.contentType || "-"}</p>
                    <p><span className="font-medium text-gray-800">업로드 시각</span> {activePreview.uploadedAtLabel || "-"}</p>
                    <p className="truncate">
                      <span className="font-medium text-gray-800">저장 경로</span> {activePreview.fullPath}
                    </p>
                  </div>

                  {isImageDocument(activePreview) && (
                    <div className="overflow-hidden rounded-xl border border-gray-200 bg-gray-50">
                      <img
                        src={activePreview.downloadUrl}
                        alt={`${documentLabelMap[activeDoc]} 미리보기`}
                        className="h-[420px] w-full object-contain"
                      />
                    </div>
                  )}

                  {!isImageDocument(activePreview) && isPdfDocument(activePreview) && (
                    <div className="overflow-hidden rounded-xl border border-gray-200 bg-gray-50">
                      <iframe
                        title={`${documentLabelMap[activeDoc]} PDF 미리보기`}
                        src={`${activePreview.downloadUrl}#toolbar=0`}
                        className="h-[420px] w-full"
                      />
                    </div>
                  )}

                  {!isImageDocument(activePreview) && !isPdfDocument(activePreview) && (
                    <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 text-sm text-gray-600">
                      현재 형식은 인라인 미리보기를 지원하지 않습니다. 원본 열기로 확인하세요.
                    </div>
                  )}

                  <a
                    href={activePreview.downloadUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-xs font-medium text-blue-700 hover:bg-blue-100"
                  >
                    원본 열기
                  </a>
                </div>
              )}
            </div>
          </section>

          <section className="space-y-4 self-start rounded-2xl border border-gray-200 bg-gray-50 p-4 lg:sticky lg:top-5">
            <div>
              <h3 className="text-sm font-semibold text-gray-900">검토 체크리스트</h3>
              <p className="mt-1 text-xs text-gray-500">
                실제 원본을 확인한 항목만 체크하세요.
              </p>
            </div>

            <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
              <p className="text-[11px] font-medium uppercase tracking-[0.16em] text-gray-400">검토 진행</p>
              <p className="mt-2 text-lg font-semibold text-gray-900">{checkedCount}/{totalDocumentCount}</p>
              <p className="mt-1 text-xs text-gray-500">
                {allDocsChecked ? "세 항목 모두 확인 완료" : "확인한 원본 파일만 체크해 주세요."}
              </p>
            </div>

            <div className="space-y-2">
              {documents.map((documentInfo) => (
                <label
                  key={documentInfo.key}
                  className="flex cursor-pointer items-start gap-3 rounded-xl border border-gray-200 bg-white px-3 py-3 shadow-sm"
                >
                  <input
                    type="checkbox"
                    checked={docStatus[documentInfo.key] === "확인 완료"}
                    onChange={() => onToggleDocStatus(documentInfo.key)}
                    className="mt-0.5 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-600"
                  />
                  <div>
                    <p className="text-sm font-medium text-gray-900">{documentInfo.label}</p>
                    <p className="mt-1 text-xs text-gray-500">{documentInfo.helper}</p>
                  </div>
                </label>
              ))}
            </div>

            <div>
              <label className="mb-1.5 block text-xs font-medium text-gray-700">
                반려 사유
              </label>
              <textarea
                value={rejectReason}
                onChange={(event) => setRejectReason(event.target.value)}
                rows={4}
                placeholder="예: 범죄경력 조회 파일이 누락되어 보완이 필요합니다."
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
              />
            </div>

            {!hasDocumentSummary && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
                매니저가 제출한 서류 요약이 없어 현재 상태에서는 승인 또는 반려를 저장할 수 없습니다.
              </div>
            )}

            <div className="grid gap-2 sm:grid-cols-2">
              <button
                type="button"
                onClick={() => void onSaveReview("REJECTED")}
                disabled={isSubmitting || !hasDocumentSummary}
                className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-red-300"
              >
                반려
              </button>
              <button
                type="button"
                onClick={() => void onSaveReview("APPROVED")}
                disabled={isSubmitting || !allDocsChecked || !hasDocumentSummary}
                className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300"
              >
                최종 승인
              </button>
            </div>

            <p className="text-[11px] leading-5 text-gray-500">
              승인 전에는 세 개 문서의 원본 상태와 제출 요약을 함께 확인하세요. 요약이 없으면 심사 결과를 저장하지 않습니다.
            </p>
          </section>
        </div>
      </div>
    </div>
  );
}
