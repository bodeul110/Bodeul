import React, { useEffect, useMemo, useState } from "react";
import {
  arrayUnion,
  collection,
  doc,
  getDoc,
  onSnapshot,
  query,
  serverTimestamp,
  updateDoc,
  where,
  type DocumentData,
} from "firebase/firestore";
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
  type User as FirebaseUser,
} from "firebase/auth";
import {
  getDownloadURL,
  getMetadata,
  listAll,
  ref,
} from "firebase/storage";
import { auth, db, firebaseConfig, storage } from "../firebase";

type AdminSessionResult = {
  isAdmin: boolean;
  adminName: string;
  message: string;
};

type ManagerDocumentKey = "idCard" | "license" | "criminalRecord";
type ChecklistStatus = "미확인" | "확인 완료";
type ManagerStatus = "대기" | "검토중" | "승인됨" | "반려";
type ReviewStatus = "APPROVED" | "REJECTED";
type MenuKey = "dashboard" | "approval";
type PreviewStatus = "idle" | "loading" | "ready" | "missing" | "error";

type StoredManagerDocumentFile = {
  fullPath: string;
  fileName: string;
  contentType: string;
  uploadedAtLabel: string;
};

type DocumentPreview = StoredManagerDocumentFile & {
  status: PreviewStatus;
  downloadUrl: string;
  message: string;
};

type Manager = {
  id: string;
  name: string;
  email: string;
  phone: string;
  date: string;
  status: ManagerStatus;
  documentSummary: string;
  reviewNote: string;
  documentFiles: Partial<Record<ManagerDocumentKey, StoredManagerDocumentFile>>;
};

const INITIAL_DOC_STATUS: Record<ManagerDocumentKey, ChecklistStatus> = {
  idCard: "미확인",
  license: "미확인",
  criminalRecord: "미확인",
};

const DOCUMENTS: { key: ManagerDocumentKey; label: string; helper: string }[] = [
  { key: "idCard", label: "신분증", helper: "신분증 원본과 이름, 생년월일을 대조합니다." },
  { key: "license", label: "자격증", helper: "요양보호사 또는 간호 관련 자격을 확인합니다." },
  { key: "criminalRecord", label: "범죄경력 조회", helper: "최신 발급본 기준으로 검토합니다." },
];

const DOCUMENT_LABEL_MAP: Record<ManagerDocumentKey, string> = {
  idCard: "신분증",
  license: "자격증",
  criminalRecord: "범죄경력 조회",
};

function createPreview(status: PreviewStatus, overrides: Partial<DocumentPreview> = {}): DocumentPreview {
  return {
    status,
    fileName: "",
    contentType: "",
    downloadUrl: "",
    fullPath: "",
    uploadedAtLabel: "",
    message: "",
    ...overrides,
  };
}

function buildPreviewState(status: PreviewStatus): Record<ManagerDocumentKey, DocumentPreview> {
  return {
    idCard: createPreview(status),
    license: createPreview(status),
    criminalRecord: createPreview(status),
  };
}

async function resolveAdminSession(user: FirebaseUser): Promise<AdminSessionResult> {
  const userDoc = await getDoc(doc(db, "users", user.uid));
  if (!userDoc.exists()) {
    return {
      isAdmin: false,
      adminName: "",
      message: "사용자 정보를 찾을 수 없습니다.",
    };
  }

  const userData = userDoc.data();
  if (!userData || userData.role !== "ADMIN") {
    return {
      isAdmin: false,
      adminName: "",
      message: "관리자 계정으로 로그인해주세요.",
    };
  }

  const adminName = typeof userData.name === "string" && userData.name.trim()
    ? userData.name.trim()
    : "관리자";

  return {
    isAdmin: true,
    adminName,
    message: "",
  };
}

function readText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function resolveDate(rawValue: unknown): Date | null {
  if (typeof rawValue === "number" && Number.isFinite(rawValue)) {
    return new Date(rawValue);
  }

  if (typeof rawValue === "string" && rawValue.trim()) {
    const parsed = new Date(rawValue);
    if (!Number.isNaN(parsed.getTime())) {
      return parsed;
    }
  }

  if (
    rawValue
    && typeof rawValue === "object"
    && "toDate" in rawValue
    && typeof (rawValue as { toDate: () => Date }).toDate === "function"
  ) {
    return (rawValue as { toDate: () => Date }).toDate();
  }

  return null;
}

function formatDate(rawValue: unknown): string {
  const date = resolveDate(rawValue);
  return date ? date.toLocaleDateString("ko-KR") : "";
}

function formatDateTime(rawValue: unknown): string {
  const date = resolveDate(rawValue);
  return date ? date.toLocaleString("ko-KR") : "";
}

function mapManagerStatus(rawStatus: unknown): ManagerStatus {
  if (rawStatus === "PENDING_REVIEW") {
    return "검토중";
  }
  if (rawStatus === "APPROVED") {
    return "승인됨";
  }
  if (rawStatus === "REJECTED") {
    return "반려";
  }
  return "대기";
}

function parseStoredDocumentFile(rawValue: unknown): StoredManagerDocumentFile | null {
  if (typeof rawValue === "string" && rawValue.trim()) {
    const trimmedPath = rawValue.trim();
    return {
      fullPath: trimmedPath,
      fileName: trimmedPath.split("/").pop() || "",
      contentType: "",
      uploadedAtLabel: "",
    };
  }

  if (!rawValue || typeof rawValue !== "object" || Array.isArray(rawValue)) {
    return null;
  }

  const record = rawValue as Record<string, unknown>;
  const fullPath = readText(record.fullPath || record.path || record.storagePath);
  if (!fullPath) {
    return null;
  }

  const fileName = readText(record.fileName) || fullPath.split("/").pop() || "";
  const contentType = readText(record.contentType || record.mimeType);
  const uploadedAtLabel = formatDateTime(
    record.uploadedAt || record.updatedAt || record.createdAt || record.timeCreated,
  );

  return {
    fullPath,
    fileName,
    contentType,
    uploadedAtLabel,
  };
}

function parseManagerDocumentFiles(data: DocumentData): Partial<Record<ManagerDocumentKey, StoredManagerDocumentFile>> {
  const files: Partial<Record<ManagerDocumentKey, StoredManagerDocumentFile>> = {};

  const metadataMap = data.managerDocumentFiles;
  if (metadataMap && typeof metadataMap === "object" && !Array.isArray(metadataMap)) {
    for (const key of Object.keys(DOCUMENT_LABEL_MAP) as ManagerDocumentKey[]) {
      const parsed = parseStoredDocumentFile((metadataMap as Record<string, unknown>)[key]);
      if (parsed) {
        files[key] = parsed;
      }
    }
  }

  const pathMap = data.managerDocumentFilePaths;
  if (pathMap && typeof pathMap === "object" && !Array.isArray(pathMap)) {
    for (const key of Object.keys(DOCUMENT_LABEL_MAP) as ManagerDocumentKey[]) {
      if (files[key]) {
        continue;
      }
      const parsed = parseStoredDocumentFile((pathMap as Record<string, unknown>)[key]);
      if (parsed) {
        files[key] = parsed;
      }
    }
  }

  const legacyFields: Record<ManagerDocumentKey, unknown[]> = {
    idCard: [
      data.managerIdCardFilePath,
      data.idCardFilePath,
      data.managerIdCardStoragePath,
    ],
    license: [
      data.managerLicenseFilePath,
      data.licenseFilePath,
      data.managerLicenseStoragePath,
    ],
    criminalRecord: [
      data.managerCriminalRecordFilePath,
      data.criminalRecordFilePath,
      data.managerCriminalRecordStoragePath,
    ],
  };

  for (const key of Object.keys(legacyFields) as ManagerDocumentKey[]) {
    if (files[key]) {
      continue;
    }
    const candidate = legacyFields[key]
      .map((value) => parseStoredDocumentFile(value))
      .find((value) => value !== null);
    if (candidate) {
      files[key] = candidate;
    }
  }

  return files;
}

function toManager(snapshotData: DocumentData, id: string): Manager {
  return {
    id,
    name: readText(snapshotData.name) || "이름 없음",
    email: readText(snapshotData.email),
    phone: readText(snapshotData.phone),
    date: formatDate(snapshotData.createdAt),
    status: mapManagerStatus(snapshotData.managerDocumentStatus),
    documentSummary: readText(snapshotData.managerDocumentSummary),
    reviewNote: readText(snapshotData.managerDocumentReviewNote),
    documentFiles: parseManagerDocumentFiles(snapshotData),
  };
}

function getDocumentFolderPath(managerId: string, documentKey: ManagerDocumentKey): string {
  return `manager-documents/${managerId}/${documentKey}`;
}

function getFirebaseStorageConsoleFolderUrl(managerId: string, documentKey: ManagerDocumentKey): string {
  const folderPath = getDocumentFolderPath(managerId, documentKey);
  return `https://console.firebase.google.com/project/${firebaseConfig.projectId}/storage/${firebaseConfig.storageBucket}/files/~2F${encodeURIComponent(folderPath).replace(/%2F/g, "~2F")}`;
}

function isImageDocument(preview: DocumentPreview): boolean {
  const normalizedType = preview.contentType.toLowerCase();
  if (normalizedType.startsWith("image/")) {
    return true;
  }

  const normalizedName = preview.fileName.toLowerCase();
  return [".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"].some((suffix) =>
    normalizedName.endsWith(suffix));
}

function isPdfDocument(preview: DocumentPreview): boolean {
  const normalizedType = preview.contentType.toLowerCase();
  if (normalizedType === "application/pdf") {
    return true;
  }
  return preview.fileName.toLowerCase().endsWith(".pdf");
}

async function buildPreviewFromReference(
  documentKey: ManagerDocumentKey,
  storagePath: string,
  fallback: StoredManagerDocumentFile | undefined,
): Promise<DocumentPreview> {
  const storageRef = ref(storage, storagePath);
  const [downloadUrl, metadata] = await Promise.all([
    getDownloadURL(storageRef),
    getMetadata(storageRef),
  ]);

  return createPreview("ready", {
    downloadUrl,
    fullPath: metadata.fullPath || fallback?.fullPath || storagePath,
    fileName: metadata.name || fallback?.fileName || storagePath.split("/").pop() || DOCUMENT_LABEL_MAP[documentKey],
    contentType: metadata.contentType || fallback?.contentType || "",
    uploadedAtLabel: formatDateTime(metadata.updated || metadata.timeCreated) || fallback?.uploadedAtLabel || "",
  });
}

async function buildPreviewFromFolder(
  managerId: string,
  documentKey: ManagerDocumentKey,
): Promise<DocumentPreview> {
  const folderPath = getDocumentFolderPath(managerId, documentKey);
  const folderRef = ref(storage, folderPath);
  const listResult = await listAll(folderRef);

  if (!listResult.items.length) {
    return createPreview("missing", {
      message: `${DOCUMENT_LABEL_MAP[documentKey]} 파일이 아직 업로드되지 않았습니다. ${folderPath}/ 아래에 파일을 올리면 관리자 웹에서 바로 확인할 수 있습니다.`,
    });
  }

  const itemsWithMetadata = await Promise.all(listResult.items.map(async (item) => ({
    item,
    metadata: await getMetadata(item),
  })));

  itemsWithMetadata.sort((left, right) => {
    const leftTime = new Date(left.metadata.updated || left.metadata.timeCreated || 0).getTime();
    const rightTime = new Date(right.metadata.updated || right.metadata.timeCreated || 0).getTime();
    return rightTime - leftTime;
  });

  return buildPreviewFromReference(documentKey, itemsWithMetadata[0].item.fullPath, undefined);
}

function resolveStorageErrorMessage(
  error: unknown,
  managerId: string,
  documentKey: ManagerDocumentKey,
  explicitPath: string,
): string {
  const errorCode = typeof error === "object" && error && "code" in error
    ? String((error as { code: unknown }).code)
    : "";

  if (errorCode === "storage/unauthorized") {
    return "Storage 읽기 권한이 없습니다. storage.rules 배포 상태를 확인해주세요.";
  }

  if (errorCode === "storage/object-not-found") {
    if (explicitPath) {
      return `저장된 경로(${explicitPath})에 파일이 없습니다. 기본 경로 ${getDocumentFolderPath(managerId, documentKey)}/도 함께 확인해주세요.`;
    }
    return `${DOCUMENT_LABEL_MAP[documentKey]} 파일을 찾지 못했습니다. ${getDocumentFolderPath(managerId, documentKey)}/ 경로를 확인해주세요.`;
  }

  return `${DOCUMENT_LABEL_MAP[documentKey]} 파일을 읽지 못했습니다. Storage 경로와 권한을 확인해주세요.`;
}

async function resolveDocumentPreview(
  manager: Manager,
  documentKey: ManagerDocumentKey,
): Promise<DocumentPreview> {
  const storedFile = manager.documentFiles[documentKey];

  if (storedFile?.fullPath) {
    try {
      return await buildPreviewFromReference(documentKey, storedFile.fullPath, storedFile);
    } catch (error) {
      const errorCode = typeof error === "object" && error && "code" in error
        ? String((error as { code: unknown }).code)
        : "";
      const message = errorCode === "storage/object-not-found"
        ? `저장된 메타데이터 경로(${storedFile.fullPath})의 파일을 찾지 못했습니다. 다른 파일로 대체하지 않고 검토를 중단합니다.`
        : resolveStorageErrorMessage(error, manager.id, documentKey, storedFile.fullPath);
      return createPreview("error", {
        message,
        fullPath: storedFile.fullPath,
        fileName: storedFile.fileName,
        contentType: storedFile.contentType,
        uploadedAtLabel: storedFile.uploadedAtLabel,
      });
    }
  }

  try {
    return await buildPreviewFromFolder(manager.id, documentKey);
  } catch (error) {
    return createPreview("error", {
      message: resolveStorageErrorMessage(error, manager.id, documentKey, storedFile?.fullPath || ""),
      fullPath: storedFile?.fullPath || "",
      fileName: storedFile?.fileName || "",
      contentType: storedFile?.contentType || "",
      uploadedAtLabel: storedFile?.uploadedAtLabel || "",
    });
  }
}

function Dashboard({ managers }: { managers: Manager[] }) {
  const pendingCount = useMemo(
    () => managers.filter((manager) => manager.status === "대기").length,
    [managers],
  );
  const reviewingCount = useMemo(
    () => managers.filter((manager) => manager.status === "검토중").length,
    [managers],
  );
  const approvedCount = useMemo(
    () => managers.filter((manager) => manager.status === "승인됨").length,
    [managers],
  );

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-base font-semibold text-gray-900">대시보드 요약</h1>
        <p className="mt-1 text-xs text-gray-500">
          관리자 웹에서 확인 중인 매니저 승인 현황입니다.
        </p>
      </header>

      <div className="grid gap-3 md:grid-cols-3">
        <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-gray-500">승인 대기</p>
          <p className="mt-2 text-2xl font-semibold text-gray-900">{pendingCount}</p>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-gray-500">검토 중</p>
          <p className="mt-2 text-2xl font-semibold text-blue-600">{reviewingCount}</p>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-gray-500">승인 완료</p>
          <p className="mt-2 text-2xl font-semibold text-green-600">{approvedCount}</p>
        </div>
      </div>
    </div>
  );
}

function ManagerApproval({
  adminName,
  managers,
}: {
  adminName: string;
  managers: Manager[];
}) {
  const [selectedManagerId, setSelectedManagerId] = useState("");
  const [activeDoc, setActiveDoc] = useState<ManagerDocumentKey>("idCard");
  const [docStatus, setDocStatus] = useState<Record<ManagerDocumentKey, ChecklistStatus>>(INITIAL_DOC_STATUS);
  const [documentPreviews, setDocumentPreviews] = useState<Record<ManagerDocumentKey, DocumentPreview>>(
    buildPreviewState("idle"),
  );
  const [rejectReason, setRejectReason] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const selectedManager = useMemo(
    () => managers.find((manager) => manager.id === selectedManagerId) || null,
    [managers, selectedManagerId],
  );

  const statusBadgeClass: Record<ManagerStatus, string> = {
    대기: "bg-gray-100 text-gray-700",
    검토중: "bg-blue-100 text-blue-700",
    승인됨: "bg-green-100 text-green-700",
    반려: "bg-red-100 text-red-700",
  };

  const previewBadgeClass: Record<PreviewStatus, string> = {
    idle: "bg-gray-100 text-gray-500",
    loading: "bg-blue-100 text-blue-700",
    ready: "bg-green-100 text-green-700",
    missing: "bg-amber-100 text-amber-700",
    error: "bg-red-100 text-red-700",
  };

  const previewBadgeLabel: Record<PreviewStatus, string> = {
    idle: "대기",
    loading: "불러오는 중",
    ready: "원본 확인 가능",
    missing: "파일 없음",
    error: "확인 실패",
  };

  const allDocsChecked = Object.values(docStatus).every((status) => status === "확인 완료");
  const hasDocumentSummary = Boolean(selectedManager?.documentSummary.trim());
  const activePreview = documentPreviews[activeDoc];

  useEffect(() => {
    let cancelled = false;

    if (!selectedManager) {
      return () => {
        cancelled = true;
      };
    }

    void Promise.allSettled(
      DOCUMENTS.map(async (documentInfo) => ({
        key: documentInfo.key,
        preview: await resolveDocumentPreview(selectedManager, documentInfo.key),
      })),
    ).then((results) => {
      if (cancelled) {
        return;
      }

      const nextState = buildPreviewState("idle");
      results.forEach((result, index) => {
        const key = DOCUMENTS[index].key;
        if (result.status === "fulfilled") {
          nextState[key] = result.value.preview;
          return;
        }
        nextState[key] = createPreview("error", {
          message: `${DOCUMENT_LABEL_MAP[key]} 미리보기를 불러오지 못했습니다.`,
        });
      });
      setDocumentPreviews(nextState);
    }).catch(() => {
      if (cancelled) {
        return;
      }
      const nextState = buildPreviewState("error");
      for (const key of Object.keys(nextState) as ManagerDocumentKey[]) {
        nextState[key] = createPreview("error", {
          message: `${DOCUMENT_LABEL_MAP[key]} 미리보기를 불러오지 못했습니다.`,
        });
      }
      setDocumentPreviews(nextState);
    });

    return () => {
      cancelled = true;
    };
  }, [selectedManager]);

  function openManagerReview(manager: Manager) {
    setSelectedManagerId(manager.id);
    setActiveDoc("idCard");
    setDocStatus(INITIAL_DOC_STATUS);
    setDocumentPreviews(buildPreviewState("loading"));
    setRejectReason(manager.reviewNote || "");
    setIsSubmitting(false);
  }

  function closeModal() {
    setSelectedManagerId("");
    setActiveDoc("idCard");
    setDocStatus(INITIAL_DOC_STATUS);
    setDocumentPreviews(buildPreviewState("idle"));
    setRejectReason("");
    setIsSubmitting(false);
  }

  function handleToggleDocStatus(key: ManagerDocumentKey) {
    setDocStatus((prev) => ({
      ...prev,
      [key]: prev[key] === "미확인" ? "확인 완료" : "미확인",
    }));
  }

  async function saveReview(nextStatus: ReviewStatus) {
    if (!selectedManager || isSubmitting) {
      return;
    }

    const reviewNote = nextStatus === "REJECTED" ? rejectReason.trim() : "";
    if (!selectedManager.documentSummary.trim()) {
      window.alert("매니저가 제출한 서류 요약이 없어 심사 결과를 저장할 수 없습니다.");
      return;
    }
    if (nextStatus === "APPROVED" && !allDocsChecked) {
      window.alert("승인 전 체크리스트를 모두 확인해주세요.");
      return;
    }
    if (nextStatus === "REJECTED" && !reviewNote) {
      window.alert("반려 사유를 입력해주세요.");
      return;
    }

    const actionLabel = nextStatus === "APPROVED" ? "승인" : "반려";
    const ok = window.confirm(`"${selectedManager.name}" 요청을 ${actionLabel}하시겠습니까?`);
    if (!ok) {
      return;
    }

    setIsSubmitting(true);
    try {
      await updateDoc(doc(db, "users", selectedManager.id), {
        managerDocumentStatus: nextStatus,
        managerDocumentReviewNote: reviewNote,
        managerDocumentReviewedAt: serverTimestamp(),
        managerDocumentReviewedByName: adminName,
        managerDocumentHistory: arrayUnion({
          eventType: nextStatus,
          happenedAt: Date.now(),
          actorName: adminName,
          summary: selectedManager.documentSummary,
          reviewNote,
        }),
      });

      window.alert(nextStatus === "APPROVED"
        ? "매니저 서류를 승인했습니다."
        : "매니저 서류를 반려했습니다.");
      closeModal();
    } catch (error) {
      console.error("Manager review save failed:", error);
      window.alert("심사 결과를 저장하지 못했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-base font-semibold text-gray-900">매니저 서류 승인</h1>
        <p className="mt-1 text-xs text-gray-500">
          제출된 서류 요약과 Storage 원본을 함께 검토하고 승인 또는 반려를 저장합니다.
        </p>
      </header>

      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50 text-xs font-semibold text-gray-600">
            <tr>
              <th className="px-4 py-3 text-left">이름</th>
              <th className="px-4 py-3 text-left">이메일</th>
              <th className="px-4 py-3 text-left">전화번호</th>
              <th className="px-4 py-3 text-left">가입일</th>
              <th className="px-4 py-3 text-left">상태</th>
              <th className="px-4 py-3 text-left">관리</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {managers.map((manager) => (
              <tr key={manager.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 text-gray-900">{manager.name}</td>
                <td className="px-4 py-3 text-gray-600">{manager.email || "-"}</td>
                <td className="px-4 py-3 text-gray-600">{manager.phone || "-"}</td>
                <td className="px-4 py-3 text-gray-600">{manager.date || "-"}</td>
                <td className="px-4 py-3">
                  <span className={`rounded-full px-2 py-1 text-xs font-medium ${statusBadgeClass[manager.status]}`}>
                    {manager.status}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <button
                    type="button"
                    onClick={() => openManagerReview(manager)}
                    className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 transition hover:bg-gray-100"
                  >
                    상세 보기
                  </button>
                </td>
              </tr>
            ))}
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

      {selectedManager && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4 py-6">
          <div className="max-h-[90vh] w-full max-w-6xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
              <div>
                <h2 className="text-lg font-semibold text-gray-900">{selectedManager.name} 서류 심사</h2>
                <p className="mt-1 text-xs text-gray-500">
                  제출 요약과 Storage 원본을 함께 확인한 뒤 상태를 저장하세요.
                </p>
              </div>
              <button
                type="button"
                onClick={closeModal}
                className="rounded-md border border-gray-300 px-3 py-1.5 text-xs text-gray-600 hover:bg-gray-100"
              >
                닫기
              </button>
            </div>

            <div className="grid gap-6 px-6 py-5 lg:grid-cols-[260px_minmax(0,1fr)_300px]">
              <section className="space-y-3 rounded-xl border border-gray-200 bg-gray-50 p-4">
                <h3 className="text-sm font-semibold text-gray-900">제출 정보</h3>
                <div className="space-y-2 text-xs text-gray-600">
                  <p><span className="font-medium text-gray-800">이메일</span> {selectedManager.email || "-"}</p>
                  <p><span className="font-medium text-gray-800">전화번호</span> {selectedManager.phone || "-"}</p>
                  <p><span className="font-medium text-gray-800">가입일</span> {selectedManager.date || "-"}</p>
                  <p><span className="font-medium text-gray-800">현재 상태</span> {selectedManager.status}</p>
                </div>
                <div className="rounded-lg border border-gray-200 bg-white p-3 text-xs text-gray-700">
                  <p className="mb-1 font-medium text-gray-900">제출 요약</p>
                  <p className="whitespace-pre-wrap leading-5">
                    {selectedManager.documentSummary || "제출된 서류 요약이 없습니다."}
                  </p>
                </div>
                {selectedManager.reviewNote && (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
                    <p className="mb-1 font-medium">최근 검토 메모</p>
                    <p className="whitespace-pre-wrap leading-5">{selectedManager.reviewNote}</p>
                  </div>
                )}
              </section>

              <section className="space-y-4">
                <div className="flex flex-wrap gap-2">
                  {DOCUMENTS.map((documentInfo) => {
                    const preview = documentPreviews[documentInfo.key];
                    const isActive = activeDoc === documentInfo.key;
                    return (
                      <button
                        key={documentInfo.key}
                        type="button"
                        onClick={() => setActiveDoc(documentInfo.key)}
                        className={`rounded-lg border px-3 py-2 text-left text-xs transition ${
                          isActive
                            ? "border-blue-600 bg-blue-50 text-blue-700"
                            : "border-gray-200 bg-white text-gray-700 hover:border-gray-300"
                        }`}
                      >
                        <p className="font-semibold">{documentInfo.label}</p>
                        <p className="mt-1 text-[11px] leading-4 text-gray-500">{documentInfo.helper}</p>
                        <span className={`mt-2 inline-flex rounded-full px-2 py-1 text-[11px] font-medium ${previewBadgeClass[preview.status]}`}>
                          {previewBadgeLabel[preview.status]}
                        </span>
                      </button>
                    );
                  })}
                </div>

                <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
                  <div className="mb-3 flex items-center justify-between">
                    <div>
                      <h3 className="text-sm font-semibold text-gray-900">{DOCUMENT_LABEL_MAP[activeDoc]} 원본</h3>
                      <p className="mt-1 text-xs text-gray-500">
                        경로 규약: <span className="font-mono">{getDocumentFolderPath(selectedManager.id, activeDoc)}/파일명</span>
                      </p>
                    </div>
                    <span className={`rounded-full px-2 py-1 text-[11px] font-medium ${previewBadgeClass[activePreview.status]}`}>
                      {previewBadgeLabel[activePreview.status]}
                    </span>
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
                        href={getFirebaseStorageConsoleFolderUrl(selectedManager.id, activeDoc)}
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
                      <div className="grid gap-2 rounded-lg border border-gray-200 bg-gray-50 p-3 text-xs text-gray-600 md:grid-cols-2">
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
                            alt={`${DOCUMENT_LABEL_MAP[activeDoc]} 미리보기`}
                            className="h-[420px] w-full object-contain"
                          />
                        </div>
                      )}

                      {!isImageDocument(activePreview) && isPdfDocument(activePreview) && (
                        <div className="overflow-hidden rounded-xl border border-gray-200 bg-gray-50">
                          <iframe
                            title={`${DOCUMENT_LABEL_MAP[activeDoc]} PDF 미리보기`}
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

              <section className="space-y-4 rounded-xl border border-gray-200 bg-gray-50 p-4">
                <div>
                  <h3 className="text-sm font-semibold text-gray-900">검토 체크리스트</h3>
                  <p className="mt-1 text-xs text-gray-500">
                    실제 원본을 확인한 항목만 체크하세요.
                  </p>
                </div>

                <div className="space-y-2">
                  {DOCUMENTS.map((documentInfo) => (
                    <label
                      key={documentInfo.key}
                      className="flex cursor-pointer items-start gap-3 rounded-lg border border-gray-200 bg-white px-3 py-2"
                    >
                      <input
                        type="checkbox"
                        checked={docStatus[documentInfo.key] === "확인 완료"}
                        onChange={() => handleToggleDocStatus(documentInfo.key)}
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

                <div className="grid gap-2">
                  <button
                    type="button"
                    onClick={() => void saveReview("REJECTED")}
                    disabled={isSubmitting || !hasDocumentSummary}
                    className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-red-300"
                  >
                    반려
                  </button>
                  <button
                    type="button"
                    onClick={() => void saveReview("APPROVED")}
                    disabled={isSubmitting || !allDocsChecked || !hasDocumentSummary}
                    className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300"
                  >
                    최종 승인
                  </button>
                </div>
              </section>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function App() {
  const [currentMenu, setCurrentMenu] = useState<MenuKey>("dashboard");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [isCheckingSession, setIsCheckingSession] = useState(true);
  const [authError, setAuthError] = useState("");
  const [adminName, setAdminName] = useState("");
  const [managerSnapshot, setManagerSnapshot] = useState<Manager[]>([]);
  const [managerLoadError, setManagerLoadError] = useState("");

  useEffect(() => {
    let active = true;

    const unsubscribe = onAuthStateChanged(auth, async (user) => {
      if (!active) {
        return;
      }

      setIsCheckingSession(true);

      if (!user) {
        setIsLoggedIn(false);
        setAdminName("");
        setManagerSnapshot([]);
        setManagerLoadError("");
        setIsCheckingSession(false);
        return;
      }

      try {
        const session = await resolveAdminSession(user);
        if (!active) {
          return;
        }

        if (!session.isAdmin) {
          setIsLoggedIn(false);
          setAdminName("");
          setManagerSnapshot([]);
          setManagerLoadError("");
          setAuthError(session.message);
          await signOut(auth);
          return;
        }

        setIsLoggedIn(true);
        setAdminName(session.adminName);
        setAuthError("");
        setManagerLoadError("");
      } catch (error) {
        console.error("Admin session validation failed:", error);
        setIsLoggedIn(false);
        setAdminName("");
        setManagerSnapshot([]);
        setManagerLoadError("");
        setAuthError("관리자 세션을 확인하지 못했습니다.");
      } finally {
        if (active) {
          setIsCheckingSession(false);
        }
      }
    });

    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  useEffect(() => {
    if (!isLoggedIn) {
      return;
    }

    const q = query(collection(db, "users"), where("role", "==", "MANAGER"));
    const unsubscribe = onSnapshot(q, (querySnapshot) => {
      setManagerLoadError("");
      if (querySnapshot.empty) {
        setManagerSnapshot([]);
        return;
      }

      const managerList = querySnapshot.docs
        .map((snapshot) => toManager(snapshot.data(), snapshot.id))
        .sort((left, right) => left.name.localeCompare(right.name, "ko-KR"));

      setManagerSnapshot(managerList);
    }, (error) => {
      console.error("Manager snapshot subscription failed:", error);
      setManagerSnapshot([]);
      setManagerLoadError("매니저 목록을 불러오지 못했습니다. Firestore 권한과 네트워크 상태를 확인해 주세요.");
    });

    return () => unsubscribe();
  }, [isLoggedIn]);

  async function handleLogin(event: React.FormEvent) {
    event.preventDefault();
    setAuthError("");

    try {
      await signInWithEmailAndPassword(auth, email.trim(), password);
      setPassword("");
    } catch (error) {
      console.error("Admin login failed:", error);
      setAuthError("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
  }

  async function handleLogout() {
    try {
      await signOut(auth);
    } catch (error) {
      console.error("Admin logout failed:", error);
    } finally {
      setIsLoggedIn(false);
      setAdminName("");
      setManagerSnapshot([]);
      setManagerLoadError("");
      setAuthError("");
      setCurrentMenu("dashboard");
    }
  }

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

  if (!isLoggedIn) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4 font-sans text-sm antialiased">
        <div className="w-full max-w-sm rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <h1 className="mb-1 text-base font-semibold text-gray-900">관리자 로그인</h1>
          <p className="mb-4 text-xs text-gray-500">
            관리자 계정으로 로그인한 뒤 승인 대시보드에 접근하세요.
          </p>

          <form onSubmit={handleLogin} className="space-y-3">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-gray-700">이메일</label>
              <input
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
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
                onChange={(event) => setPassword(event.target.value)}
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
            onClick={() => setCurrentMenu("dashboard")}
            className={`w-full rounded-md px-3 py-2 text-left transition ${
              currentMenu === "dashboard" ? "bg-blue-600" : "hover:bg-slate-800"
            }`}
          >
            대시보드
          </button>
          <button
            type="button"
            onClick={() => setCurrentMenu("approval")}
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
            onClick={() => void handleLogout()}
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

        {currentMenu === "dashboard" && <Dashboard managers={managerSnapshot} />}
        {currentMenu === "approval" && (
          <ManagerApproval adminName={adminName} managers={managerSnapshot} />
        )}
      </main>
    </div>
  );
}

export default App;
