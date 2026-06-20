import React, { useCallback, useEffect, useMemo, useState } from "react";
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
import type { AdminSessionResult } from "./adminSession";
import { AdminAuthScreen } from "./components/AdminAuthScreen";
import { AdminShell } from "./components/AdminShell";
import { ManagerApprovalList } from "./components/ManagerApprovalList";
import { ManagerReviewModal } from "./components/ManagerReviewModal";
import { useAdminIdleSession } from "./hooks/useAdminIdleSession";
import { useManagerDocumentPreviews } from "./hooks/useManagerDocumentPreviews";

type ManagerDocumentKey = "idCard" | "license" | "criminalRecord";
type ManagerDocumentStorageKey = ManagerDocumentKey | "healthCertificate";
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

const DOCUMENT_STORAGE_KEY_CANDIDATES: Record<ManagerDocumentKey, ManagerDocumentStorageKey[]> = {
  idCard: ["idCard"],
  license: ["license", "healthCertificate"],
  criminalRecord: ["criminalRecord"],
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

function maskEmail(email: string): string {
  if (!email) {
    return "-";
  }

  const [localPart, domainPart] = email.split("@");
  if (!localPart || !domainPart) {
    return email;
  }

  if (localPart.length <= 2) {
    return `${localPart[0] || "*"}*@${domainPart}`;
  }

  return `${localPart.slice(0, 2)}***@${domainPart}`;
}

function maskPhone(phone: string): string {
  if (!phone) {
    return "-";
  }

  const digitsOnly = phone.replace(/\D/g, "");
  if (digitsOnly.length < 7) {
    return phone;
  }

  return `${digitsOnly.slice(0, 3)}-****-${digitsOnly.slice(-4)}`;
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
      const parsed = DOCUMENT_STORAGE_KEY_CANDIDATES[key]
        .map((storageKey) => parseStoredDocumentFile((metadataMap as Record<string, unknown>)[storageKey]))
        .find((value) => value !== null);
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
      const parsed = DOCUMENT_STORAGE_KEY_CANDIDATES[key]
        .map((storageKey) => parseStoredDocumentFile((pathMap as Record<string, unknown>)[storageKey]))
        .find((value) => value !== null);
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
      data.managerHealthCertificateFilePath,
      data.healthCertificateFilePath,
      data.managerHealthCertificateStoragePath,
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

function getDocumentFolderPaths(managerId: string, documentKey: ManagerDocumentKey): string[] {
  return DOCUMENT_STORAGE_KEY_CANDIDATES[documentKey].map(
    (storageKey) => `manager-documents/${managerId}/${storageKey}`,
  );
}

function getFirebaseStorageConsoleFolderUrl(
  managerId: string,
  documentKey: ManagerDocumentKey,
  explicitPath?: string,
): string {
  const folderPath = explicitPath && explicitPath.includes("/")
    ? explicitPath.split("/").slice(0, -1).join("/")
    : getDocumentFolderPaths(managerId, documentKey)[0];
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
  const folderPaths = getDocumentFolderPaths(managerId, documentKey);
  const listResults = await Promise.all(folderPaths.map(async (folderPath) => {
    const folderRef = ref(storage, folderPath);
    const listResult = await listAll(folderRef);
    return {
      folderPath,
      items: listResult.items,
    };
  }));
  const allItems = listResults.flatMap((result) => result.items);

  if (!allItems.length) {
    return createPreview("missing", {
      message: `${DOCUMENT_LABEL_MAP[documentKey]} 파일이 아직 업로드되지 않았습니다. ${folderPaths.join(", ")} 경로를 확인해주세요.`,
    });
  }

  const itemsWithMetadata = await Promise.all(allItems.map(async (item) => ({
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
      return `저장된 경로(${explicitPath})에 파일이 없습니다. 기본 경로 ${getDocumentFolderPaths(managerId, documentKey).join(", ")}도 함께 확인해주세요.`;
    }
    return `${DOCUMENT_LABEL_MAP[documentKey]} 파일을 찾지 못했습니다. ${getDocumentFolderPaths(managerId, documentKey).join(", ")} 경로를 확인해주세요.`;
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

function getUploadedDocumentCount(manager: Manager): number {
  return DOCUMENTS.filter((documentInfo) => Boolean(manager.documentFiles[documentInfo.key]?.fullPath)).length;
}

function getUploadedDocumentLabels(manager: Manager): string[] {
  return DOCUMENTS
    .filter((documentInfo) => Boolean(manager.documentFiles[documentInfo.key]?.fullPath))
    .map((documentInfo) => documentInfo.label);
}

function summarizeManagerText(value: string, maxLength = 72): string {
  const normalized = value.replace(/\s+/g, " ").trim();
  if (!normalized) {
    return "";
  }
  if (normalized.length <= maxLength) {
    return normalized;
  }
  return `${normalized.slice(0, maxLength - 1)}…`;
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
  const CHECKED_STATUS: ChecklistStatus = "확인 완료";
  const UNCHECKED_STATUS: ChecklistStatus = "미확인";
  const [selectedManagerId, setSelectedManagerId] = useState("");
  const [activeDoc, setActiveDoc] = useState<ManagerDocumentKey>("idCard");
  const [docStatus, setDocStatus] = useState<Record<ManagerDocumentKey, ChecklistStatus>>(INITIAL_DOC_STATUS);
  const [rejectReason, setRejectReason] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const selectedManager = useMemo(
    () => managers.find((manager) => manager.id === selectedManagerId) || null,
    [managers, selectedManagerId],
  );
  const documentKeys = useMemo<ManagerDocumentKey[]>(
    () => DOCUMENTS.map((documentInfo) => documentInfo.key),
    [],
  );
  const createIdlePreviewState = useCallback(
    () => buildPreviewState("idle"),
    [],
  );
  const createLoadingPreviewState = useCallback(
    () => buildPreviewState("loading"),
    [],
  );
  const createErrorPreview = useCallback(
    (key: ManagerDocumentKey) => createPreview("error", {
      message: `${DOCUMENT_LABEL_MAP[key]} 미리보기를 불러오지 못했습니다.`,
    }),
    [],
  );
  const documentPreviews = useManagerDocumentPreviews({
    selectedManager,
    documentKeys,
    getManagerKey: (manager) => manager.id,
    createIdleState: createIdlePreviewState,
    createLoadingState: createLoadingPreviewState,
    createErrorPreview,
    resolvePreview: resolveDocumentPreview,
  });

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

  const allDocsChecked = Object.values(docStatus).every((status) => status === CHECKED_STATUS);
  const hasDocumentSummary = Boolean(selectedManager?.documentSummary.trim());
  const checkedCount = Object.values(docStatus).filter((status) => status === CHECKED_STATUS).length;
  const totalManagers = managers.length;
  const summaryReadyCount = useMemo(
    () => managers.filter((manager) => Boolean(manager.documentSummary.trim())).length,
    [managers],
  );
  const fullyUploadedCount = useMemo(
    () => managers.filter((manager) => getUploadedDocumentCount(manager) === DOCUMENTS.length).length,
    [managers],
  );
  const reviewNoteCount = useMemo(
    () => managers.filter((manager) => Boolean(manager.reviewNote.trim())).length,
    [managers],
  );
  const selectedManagerUploadedCount = selectedManager ? getUploadedDocumentCount(selectedManager) : 0;
  const selectedManagerMissingCount = DOCUMENTS.length - selectedManagerUploadedCount;

  function openManagerReview(manager: Manager) {
    setSelectedManagerId(manager.id);
    setActiveDoc("idCard");
    setDocStatus(INITIAL_DOC_STATUS);
    setRejectReason(manager.reviewNote || "");
    setIsSubmitting(false);
  }

  function closeModal() {
    setSelectedManagerId("");
    setActiveDoc("idCard");
    setDocStatus(INITIAL_DOC_STATUS);
    setRejectReason("");
    setIsSubmitting(false);
  }

  function handleToggleDocStatus(key: ManagerDocumentKey) {
    setDocStatus((prev) => ({
      ...prev,
      [key]: prev[key] === UNCHECKED_STATUS ? CHECKED_STATUS : UNCHECKED_STATUS,
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
      window.alert("확인할 체크리스트를 모두 완료해 주세요.");
      return;
    }
    if (nextStatus === "REJECTED" && !reviewNote) {
      window.alert("반려 사유를 입력해 주세요.");
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

      window.alert(
        nextStatus === "APPROVED"
          ? "매니저 서류를 승인했습니다."
          : "매니저 서류를 반려했습니다.",
      );
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
        <h1 className="text-base font-semibold text-gray-900">매니저 서류 확인</h1>
        <p className="mt-1 text-xs text-gray-500">
          제출된 서류 요약과 Storage 원본을 함께 확인하고 승인 또는 반려를 진행합니다.
        </p>
      </header>

      <div className="grid gap-3 lg:grid-cols-4">
        <div className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-gray-400">전체 대상</p>
          <p className="mt-2 text-2xl font-semibold text-gray-900">{totalManagers}</p>
          <p className="mt-1 text-xs text-gray-500">현재 심사 목록에 있는 매니저 수</p>
        </div>
        <div className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-gray-400">요약 제출</p>
          <p className="mt-2 text-2xl font-semibold text-gray-900">{summaryReadyCount}</p>
          <p className="mt-1 text-xs text-gray-500">서류 요약까지 입력을 마친 계정</p>
        </div>
        <div className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-gray-400">원본 3종 완료</p>
          <p className="mt-2 text-2xl font-semibold text-blue-700">{fullyUploadedCount}</p>
          <p className="mt-1 text-xs text-gray-500">신분증, 자격증, 범죄경력 조회서 업로드 완료</p>
        </div>
        <div className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
          <p className="text-[11px] font-medium uppercase tracking-[0.18em] text-gray-400">검토 메모 있음</p>
          <p className="mt-2 text-2xl font-semibold text-amber-600">{reviewNoteCount}</p>
          <p className="mt-1 text-xs text-gray-500">보완 이력이나 운영 메모가 남아 있는 계정</p>
        </div>
      </div>

      <ManagerApprovalList
        managers={managers}
        statusBadgeClass={statusBadgeClass}
        totalDocumentCount={DOCUMENTS.length}
        onOpenManagerReview={openManagerReview}
        getUploadedDocumentCount={getUploadedDocumentCount}
        getUploadedDocumentLabels={getUploadedDocumentLabels}
        summarizeManagerText={summarizeManagerText}
        maskEmail={maskEmail}
        maskPhone={maskPhone}
      />

      {selectedManager && (
        <ManagerReviewModal
          selectedManager={selectedManager}
          activeDoc={activeDoc}
          setActiveDoc={setActiveDoc}
          docStatus={docStatus}
          rejectReason={rejectReason}
          setRejectReason={setRejectReason}
          isSubmitting={isSubmitting}
          allDocsChecked={allDocsChecked}
          hasDocumentSummary={hasDocumentSummary}
          checkedCount={checkedCount}
          totalDocumentCount={DOCUMENTS.length}
          selectedManagerUploadedCount={selectedManagerUploadedCount}
          selectedManagerMissingCount={selectedManagerMissingCount}
          documentPreviews={documentPreviews}
          documents={DOCUMENTS}
          statusBadgeClass={statusBadgeClass}
          previewBadgeClass={previewBadgeClass}
          previewBadgeLabel={previewBadgeLabel}
          documentLabelMap={DOCUMENT_LABEL_MAP}
          onClose={closeModal}
          onToggleDocStatus={handleToggleDocStatus}
          onSaveReview={saveReview}
          isImageDocument={(preview) => isImageDocument(preview as DocumentPreview)}
          isPdfDocument={(preview) => isPdfDocument(preview as DocumentPreview)}
          getDocumentFolderPaths={getDocumentFolderPaths}
          getFirebaseStorageConsoleFolderUrl={getFirebaseStorageConsoleFolderUrl}
        />
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

  const resetAdminShellState = useCallback(() => {
    setManagerSnapshot([]);
    setManagerLoadError("");
    setCurrentMenu("dashboard");
  }, []);

  const clearAdminSession = useCallback((message = "") => {
    setIsLoggedIn(false);
    setAdminName("");
    resetAdminShellState();
    setAuthError(message);
  }, [resetAdminShellState]);

  useEffect(() => {
    let active = true;

    const unsubscribe = onAuthStateChanged(auth, async (user) => {
      if (!active) {
        return;
      }

      setIsCheckingSession(true);

      if (!user) {
        clearAdminSession(authError);
        setIsCheckingSession(false);
        return;
      }

      try {
        const session = await resolveAdminSession(user);
        if (!active) {
          return;
        }

        if (!session.isAdmin) {
          clearAdminSession(session.message);
          await signOut(auth);
          return;
        }

        setIsLoggedIn(true);
        setAdminName(session.adminName);
        setAuthError("");
        setManagerLoadError("");
      } catch (error) {
        console.error("Admin session validation failed:", error);
        clearAdminSession("관리자 세션을 확인하지 못했습니다.");
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
  }, [authError, clearAdminSession]);

  const handleIdleLogout = useCallback(() => {
    void signOut(auth)
      .catch((error) => {
        console.error("Admin idle logout failed:", error);
      })
      .finally(() => {
        clearAdminSession("보안을 위해 15분 동안 활동이 없어 자동 로그아웃되었습니다.");
      });
  }, [clearAdminSession]);

  useAdminIdleSession(isLoggedIn, handleIdleLogout);

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
      clearAdminSession("");
    }
  }

  if (isCheckingSession || !isLoggedIn) {
    return (
      <AdminAuthScreen
        isCheckingSession={isCheckingSession}
        email={email}
        password={password}
        authError={authError}
        onEmailChange={setEmail}
        onPasswordChange={setPassword}
        onSubmit={handleLogin}
      />
    );
  }

  return (
    <AdminShell
      adminName={adminName}
      currentMenu={currentMenu}
      managerLoadError={managerLoadError}
      onMenuChange={setCurrentMenu}
      onLogout={() => {
        void handleLogout();
      }}
    >
      {currentMenu === "dashboard" && <Dashboard managers={managerSnapshot} />}
      {currentMenu === "approval" && (
        <ManagerApproval adminName={adminName} managers={managerSnapshot} />
      )}
    </AdminShell>
  );
}

export default App;
