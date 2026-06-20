type ManagerListItem = {
  id: string;
  name: string;
  email: string;
  phone: string;
  date: string;
  status: string;
  documentSummary: string;
  reviewNote: string;
  documentFiles: Record<string, { fullPath: string } | undefined>;
};

type ManagerApprovalListProps<TManager extends ManagerListItem> = {
  managers: TManager[];
  statusBadgeClass: Record<string, string>;
  totalDocumentCount: number;
  onOpenManagerReview: (manager: TManager) => void;
  getUploadedDocumentCount: (manager: TManager) => number;
  getUploadedDocumentLabels: (manager: TManager) => string[];
  summarizeManagerText: (value: string, maxLength?: number) => string;
  maskEmail: (email: string) => string;
  maskPhone: (phone: string) => string;
};

export function ManagerApprovalList<TManager extends ManagerListItem>({
  managers,
  statusBadgeClass,
  totalDocumentCount,
  onOpenManagerReview,
  getUploadedDocumentCount,
  getUploadedDocumentLabels,
  summarizeManagerText,
  maskEmail,
  maskPhone,
}: ManagerApprovalListProps<TManager>) {
  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        <thead className="bg-gray-50 text-xs font-semibold text-gray-600">
          <tr>
            <th className="px-4 py-3 text-left">매니저</th>
            <th className="px-4 py-3 text-left">연락처</th>
            <th className="px-4 py-3 text-left">서류 요약</th>
            <th className="px-4 py-3 text-left">원본 파일</th>
            <th className="px-4 py-3 text-left">상태</th>
            <th className="px-4 py-3 text-left">관리</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {managers.map((manager) => {
            const uploadedDocumentCount = getUploadedDocumentCount(manager);
            const uploadedDocumentLabels = getUploadedDocumentLabels(manager);
            const missingDocumentCount = totalDocumentCount - uploadedDocumentCount;
            const summarySnippet = summarizeManagerText(manager.documentSummary, 84);

            return (
              <tr key={manager.id} className="align-top hover:bg-gray-50">
                <td className="px-4 py-3">
                  <p className="font-semibold text-gray-900">{manager.name}</p>
                  <p className="mt-1 text-xs text-gray-500">가입일 {manager.date || "-"}</p>
                </td>
                <td className="px-4 py-3 text-gray-600">
                  <p>{maskEmail(manager.email)}</p>
                  <p className="mt-1 text-xs text-gray-500">{maskPhone(manager.phone)}</p>
                </td>
                <td className="px-4 py-3 text-gray-600">
                  {summarySnippet ? (
                    <>
                      <p className="leading-5 text-gray-700">{summarySnippet}</p>
                      {manager.reviewNote && (
                        <p className="mt-2 text-xs text-amber-700">최근 보완 메모 있음</p>
                      )}
                    </>
                  ) : (
                    <span className="inline-flex rounded-full bg-amber-100 px-2 py-1 text-xs font-medium text-amber-700">
                      요약 미제출
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-gray-600">
                  <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${
                    missingDocumentCount === 0
                      ? "bg-green-100 text-green-700"
                      : "bg-amber-100 text-amber-700"
                  }`}>
                    원본 {uploadedDocumentCount}/{totalDocumentCount}
                  </span>
                  <p className="mt-2 text-xs leading-5 text-gray-500">
                    {uploadedDocumentLabels.length
                      ? uploadedDocumentLabels.join(", ")
                      : "아직 업로드한 원본 파일이 없습니다."}
                  </p>
                </td>
                <td className="px-4 py-3">
                  <span className={`rounded-full px-2 py-1 text-xs font-medium ${statusBadgeClass[manager.status]}`}>
                    {manager.status}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <button
                    type="button"
                    onClick={() => onOpenManagerReview(manager)}
                    className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 transition hover:bg-gray-100"
                  >
                    상세 보기
                  </button>
                </td>
              </tr>
            );
          })}
          {!managers.length && (
            <tr>
              <td colSpan={6} className="px-4 py-10 text-center text-sm text-gray-500">
                검토할 매니저가 없습니다.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
