const fs = require("fs");
const path = require("path");

const {BASELINE_USERS, MANAGED_COLLECTIONS} = require("./baseline-config");
const {resolveAppNavigationEvidencePath} = require("./app-navigation-evidence");
const {
  extractRelativeDocumentPath,
  getDocument,
  listCollectionDocuments,
  lookupAuthUserByEmail,
} = require("./firebase-toolkit");

const SAMPLE_REQUEST_REQUESTED_ID = "request-seed-requested";
const SAMPLE_REQUEST_PROGRESS_ID = "request-seed-progress";
const SAMPLE_REQUEST_COMPLETED_ID = "request-seed-completed";

async function loadOperationsSnapshot(context) {
  const [baselineStatuses, collectionEntries] = await Promise.all([
    loadBaselineStatuses(context),
    Promise.all(MANAGED_COLLECTIONS.map(async (collectionName) => {
      const documents = await listCollectionDocuments(context, collectionName);
      return [collectionName, documents];
    })),
  ]);

  const collections = {};
  const collectionCounts = {};
  for (const [collectionName, documents] of collectionEntries) {
    const normalizedDocuments = documents.map((document) => ({
      id: document.name.split("/").pop(),
      path: extractRelativeDocumentPath(document.name, context.projectId),
      data: fromFirestoreFields(document.fields || {}),
    }));
    collections[collectionName] = normalizedDocuments;
    collectionCounts[collectionName] = normalizedDocuments.length;
  }

  return {
    projectId: context.projectId,
    generatedAt: new Date().toISOString(),
    baselineStatuses,
    collectionCounts,
    collections,
  };
}

function buildRoleReadiness(snapshot) {
  const collections = snapshot.collections;
  const usersById = toDataMap(collections.users);
  const requestsById = toDataMap(collections.appointmentRequests);
  const sessionsByRequestId = new Map();
  const reportsBySessionId = new Map();
  const followUpsByRequestId = new Map();
  const settlementByRequestId = new Map();
  const emergencyByRequestId = new Map();
  const notificationsByRequestId = new Map();
  const notificationsByInquiryId = new Map();
  const deliveriesByRequestId = new Map();
  const deliveriesByInquiryId = new Map();
  const guides = Array.isArray(collections.hospitalGuides) ? collections.hospitalGuides : [];
  const supportInquiries = Array.isArray(collections.supportInquiries) ?
    collections.supportInquiries.map((document) => ({id: document.id, ...document.data})) :
    [];
  const auditLogs = Array.isArray(collections.adminAuditLogs) ?
    collections.adminAuditLogs.map((document) => ({id: document.id, ...document.data})) :
    [];

  for (const document of collections.companionSessions || []) {
    sessionsByRequestId.set(document.data.appointmentRequestId, {
      id: document.id,
      ...document.data,
    });
  }
  for (const document of collections.sessionReports || []) {
    reportsBySessionId.set(document.data.sessionId, {
      id: document.id,
      ...document.data,
    });
  }
  for (const document of collections.appointmentFollowUps || []) {
    followUpsByRequestId.set(document.data.requestId || document.id, {
      id: document.id,
      ...document.data,
    });
  }
  for (const document of collections.adminSettlementRecords || []) {
    settlementByRequestId.set(document.data.requestId || document.id, {
      id: document.id,
      ...document.data,
    });
  }
  for (const document of collections.adminEmergencyIssues || []) {
    emergencyByRequestId.set(document.data.requestId || document.id, {
      id: document.id,
      ...document.data,
    });
  }
  for (const document of collections.adminActionNotifications || []) {
    const notification = {id: document.id, ...document.data};
    pushMapList(notificationsByRequestId, notification.requestId, notification);
    pushMapList(notificationsByInquiryId, notification.inquiryId, notification);
  }
  for (const document of collections.adminActionDeliveries || []) {
    const delivery = {id: document.id, ...document.data};
    pushMapList(deliveriesByRequestId, delivery.requestId, delivery);
    pushMapList(deliveriesByInquiryId, delivery.inquiryId, delivery);
  }

  const baselineStatusByRole = new Map();
  for (const status of snapshot.baselineStatuses) {
    baselineStatusByRole.set(status.role, status);
  }

  const requests = (collections.appointmentRequests || []).map((document) => ({
    id: document.id,
    ...document.data,
  }));

  const patient = requireBaselineRole(baselineStatusByRole, "PATIENT");
  const guardian = requireBaselineRole(baselineStatusByRole, "GUARDIAN");
  const manager = requireBaselineRole(baselineStatusByRole, "MANAGER");
  const admin = requireBaselineRole(baselineStatusByRole, "ADMIN");

  const patientRequests = requests.filter((request) => request.patientUserId === patient.uid);
  const guardianRequests = requests.filter((request) => request.guardianUserId === guardian.uid);
  const managerRequests = requests.filter((request) => request.managerUserId === manager.uid);
  const managerSessions = (collections.companionSessions || [])
      .map((document) => ({id: document.id, ...document.data}))
      .filter((session) => session.managerUserId === manager.uid);
  const managerSupportInquiries = supportInquiries
      .filter((inquiry) => inquiry.managerUserId === manager.uid);

  const progressGuardianRequest = guardianRequests.find((request) =>
    ["MATCHED", "IN_PROGRESS"].includes(request.status) &&
    sessionsByRequestId.has(request.id) &&
    hasGuideForRequest(guides, request),
  );
  const completedGuardianRequest = guardianRequests.find((request) => {
    if (request.status !== "COMPLETED") {
      return false;
    }
    const session = sessionsByRequestId.get(request.id);
    return Boolean(session && reportsBySessionId.has(session.id));
  });
  const activeManagerSession = managerSessions.find((session) => {
    const request = requestsById.get(session.appointmentRequestId);
    return Boolean(
        request &&
        usersById.has(request.patientUserId) &&
        usersById.has(request.guardianUserId) &&
        hasGuideForRequest(guides, request),
    );
  });
  const completedManagerRequest = managerRequests.find((request) => {
    if (request.status !== "COMPLETED") {
      return false;
    }
    const session = sessionsByRequestId.get(request.id);
    return Boolean(session && reportsBySessionId.has(session.id));
  });
  const completedFollowUpRequest = requests.find((request) =>
    request.status === "COMPLETED" &&
    followUpsByRequestId.has(request.id) &&
    settlementByRequestId.has(request.id) &&
    emergencyByRequestId.has(request.id),
  );

  const roles = [
    createRoleReadiness("PATIENT", "환자", [
      createCheck(
          "account",
          "로그인과 프로필 문서",
          isBaselineStatusReady(patient) && usersById.get(patient.uid)?.role === "PATIENT",
          isBaselineStatusReady(patient) ?
            `${patient.email} / users 문서 준비됨` :
            "Auth 또는 users 문서가 비어 있습니다.",
      ),
      createCheck(
          "booking",
          "예약 목록 진입",
          patientRequests.length > 0,
          patientRequests.length > 0 ?
            `${patientRequests.length}건의 요청이 환자 계정과 연결되어 있습니다.` :
            "patientUserId가 연결된 요청이 없습니다.",
      ),
      createCheck(
          "follow_up",
          "종료 후속 카드 진입",
          patientRequests.some((request) =>
            request.status === "COMPLETED" && followUpsByRequestId.has(request.id),
          ),
          patientRequests.some((request) =>
            request.status === "COMPLETED" && followUpsByRequestId.has(request.id),
          ) ?
            "완료 요청과 후기/정산/SOS 후속 문서가 연결되어 있습니다." :
            "완료 요청 또는 appointmentFollowUps 문서가 부족합니다.",
      ),
      createCheck(
          "hospital_options",
          "병원 선택 목록",
          guides.length > 0,
          guides.length > 0 ?
            `${guides.length}건의 병원 가이드가 준비되어 있습니다.` :
            "hospitalGuides 문서가 없습니다.",
      ),
    ]),
    createRoleReadiness("GUARDIAN", "보호자", [
      createCheck(
          "account",
          "로그인과 프로필 문서",
          isBaselineStatusReady(guardian) && usersById.get(guardian.uid)?.role === "GUARDIAN",
          isBaselineStatusReady(guardian) ?
            `${guardian.email} / users 문서 준비됨` :
            "Auth 또는 users 문서가 비어 있습니다.",
      ),
      createCheck(
          "dashboard",
          "보호자 진행 현황 목록",
          guardianRequests.length > 0,
          guardianRequests.length > 0 ?
            `${guardianRequests.length}건의 요청이 보호자 계정에 연결되어 있습니다.` :
            "guardianUserId가 연결된 요청이 없습니다.",
      ),
      createCheck(
          "live_progress",
          "실시간 진행 카드",
          Boolean(progressGuardianRequest),
          progressGuardianRequest ?
            `${progressGuardianRequest.id} 요청에 세션과 병원 가이드가 연결되어 있습니다.` :
            "진행 중 또는 매칭된 요청에 세션/가이드가 연결되지 않았습니다.",
      ),
      createCheck(
          "completed_report",
          "완료 리포트 확인",
          Boolean(completedGuardianRequest),
          completedGuardianRequest ?
            `${completedGuardianRequest.id} 요청에 세션 리포트가 연결되어 있습니다.` :
            "완료 요청과 sessionReports 연결이 부족합니다.",
      ),
    ]),
    createRoleReadiness("MANAGER", "매니저", [
      createCheck(
          "account",
          "로그인과 서류 프로필",
          isBaselineStatusReady(manager) &&
            usersById.get(manager.uid)?.role === "MANAGER" &&
            Boolean(usersById.get(manager.uid)?.managerDocumentStatus),
          isBaselineStatusReady(manager) ?
            `managerDocumentStatus=${usersById.get(manager.uid)?.managerDocumentStatus || "없음"}` :
            "Auth 또는 users 문서가 비어 있습니다.",
      ),
      createCheck(
          "dashboard",
          "매니저 대시보드",
          Boolean(activeManagerSession),
          activeManagerSession ?
            `${activeManagerSession.id} 세션에 환자/보호자/가이드가 모두 연결되어 있습니다.` :
            "companionSessions 또는 연결된 appointmentRequests/users/hospitalGuides가 부족합니다.",
      ),
      createCheck(
          "history",
          "과거 이력/리포트",
          Boolean(completedManagerRequest),
          completedManagerRequest ?
            `${completedManagerRequest.id} 완료 요청과 리포트가 준비되어 있습니다.` :
            "completed 요청 또는 sessionReports 문서가 부족합니다.",
      ),
      createCheck(
          "support",
          "문의 내역",
          managerSupportInquiries.length > 0,
          managerSupportInquiries.length > 0 ?
            `${managerSupportInquiries.length}건의 지원 문의가 매니저 계정과 연결되어 있습니다.` :
            "supportInquiries 문서가 없습니다.",
      ),
    ]),
    createRoleReadiness("ADMIN", "관리자", [
      createCheck(
          "account",
          "로그인과 프로필 문서",
          isBaselineStatusReady(admin) && usersById.get(admin.uid)?.role === "ADMIN",
          isBaselineStatusReady(admin) ?
            `${admin.email} / users 문서 준비됨` :
            "Auth 또는 users 문서가 비어 있습니다.",
      ),
      createCheck(
          "dashboard",
          "운영 대시보드 기본 데이터",
          requests.length > 0 && guides.length > 0 && (collections.users || []).length >= 4,
          requests.length > 0 && guides.length > 0 ?
            `요청 ${requests.length}건, 사용자 ${(collections.users || []).length}건, 가이드 ${guides.length}건` :
            "appointmentRequests/users/hospitalGuides 기본 데이터가 부족합니다.",
      ),
      createCheck(
          "follow_up",
          "후속 처리 아티팩트",
          Boolean(completedFollowUpRequest),
          completedFollowUpRequest ?
            `${completedFollowUpRequest.id} 요청에 follow-up/정산/SOS 기록이 연결되어 있습니다.` :
            "completed 요청 또는 appointmentFollowUps/adminSettlementRecords/adminEmergencyIssues가 부족합니다.",
      ),
      createCheck(
          "action_center",
          "액션센터와 전달 기록",
          supportInquiries.length > 0 &&
            (collections.adminActionNotifications || []).length > 0 &&
            (collections.adminActionDeliveries || []).length > 0 &&
            auditLogs.length > 0,
          supportInquiries.length > 0 &&
            (collections.adminActionNotifications || []).length > 0 &&
            (collections.adminActionDeliveries || []).length > 0 &&
            auditLogs.length > 0 ?
            `문의 ${supportInquiries.length}건 / 알림 ${(collections.adminActionNotifications || []).length}건 / 전달 ${(collections.adminActionDeliveries || []).length}건 / 감사 ${auditLogs.length}건` :
            "supportInquiries/adminActionNotifications/adminActionDeliveries/adminAuditLogs가 부족합니다.",
      ),
    ]),
  ];

  const scenarios = [
    createScenarioCheck(
        "requested",
        "예약 대기 샘플",
        (() => {
          const request = requestsById.get(SAMPLE_REQUEST_REQUESTED_ID);
          const reminderJob = findById(collections.appointmentReminderJobs, "reminder-seed-requested-d3");
          return Boolean(
              request &&
              request.status === "REQUESTED" &&
              !request.managerUserId &&
              reminderJob &&
              reminderJob.data.appointmentRequestId === SAMPLE_REQUEST_REQUESTED_ID,
          );
        })(),
        (() => {
          const request = requestsById.get(SAMPLE_REQUEST_REQUESTED_ID);
          if (!request) {
            return "request-seed-requested 문서가 없습니다.";
          }
          return `status=${request.status}, reminder=${Boolean(findById(collections.appointmentReminderJobs, "reminder-seed-requested-d3"))}`;
        })(),
    ),
    createScenarioCheck(
        "progress",
        "진행 중 동행 샘플",
        (() => {
          const request = requestsById.get(SAMPLE_REQUEST_PROGRESS_ID);
          const session = sessionsByRequestId.get(SAMPLE_REQUEST_PROGRESS_ID);
          return Boolean(
              request &&
              request.status === "IN_PROGRESS" &&
              session &&
              hasGuideForRequest(guides, request),
          );
        })(),
        (() => {
          const request = requestsById.get(SAMPLE_REQUEST_PROGRESS_ID);
          const session = sessionsByRequestId.get(SAMPLE_REQUEST_PROGRESS_ID);
          if (!request) {
            return "request-seed-progress 문서가 없습니다.";
          }
          return `status=${request.status}, session=${session ? session.id : "없음"}, guide=${hasGuideForRequest(guides, request) ? "연결" : "없음"}`;
        })(),
    ),
    createScenarioCheck(
        "completed",
        "종료 후속 처리 샘플",
        (() => {
          const request = requestsById.get(SAMPLE_REQUEST_COMPLETED_ID);
          const session = sessionsByRequestId.get(SAMPLE_REQUEST_COMPLETED_ID);
          const report = session ? reportsBySessionId.get(session.id) : null;
          return Boolean(
              request &&
              request.status === "COMPLETED" &&
              session &&
              report &&
              followUpsByRequestId.has(SAMPLE_REQUEST_COMPLETED_ID) &&
              settlementByRequestId.has(SAMPLE_REQUEST_COMPLETED_ID) &&
              emergencyByRequestId.has(SAMPLE_REQUEST_COMPLETED_ID) &&
              (notificationsByRequestId.get(SAMPLE_REQUEST_COMPLETED_ID) || []).length > 0 &&
              (deliveriesByRequestId.get(SAMPLE_REQUEST_COMPLETED_ID) || []).length > 0,
          );
        })(),
        (() => {
          const session = sessionsByRequestId.get(SAMPLE_REQUEST_COMPLETED_ID);
          return [
            `report=${Boolean(session && reportsBySessionId.get(session.id))}`,
            `followUp=${followUpsByRequestId.has(SAMPLE_REQUEST_COMPLETED_ID)}`,
            `settlement=${settlementByRequestId.has(SAMPLE_REQUEST_COMPLETED_ID)}`,
            `emergency=${emergencyByRequestId.has(SAMPLE_REQUEST_COMPLETED_ID)}`,
            `notifications=${(notificationsByRequestId.get(SAMPLE_REQUEST_COMPLETED_ID) || []).length}`,
            `deliveries=${(deliveriesByRequestId.get(SAMPLE_REQUEST_COMPLETED_ID) || []).length}`,
          ].join(", ");
        })(),
    ),
  ];

  return {roles, scenarios};
}

function buildDiffSummary(baseSnapshot, currentSnapshot) {
  const collections = {};
  let totalAdded = 0;
  let totalRemoved = 0;
  let totalChanged = 0;

  for (const collectionName of MANAGED_COLLECTIONS) {
    const baseMap = toSnapshotDocumentMap(baseSnapshot?.collections?.[collectionName]);
    const currentMap = toSnapshotDocumentMap(currentSnapshot?.collections?.[collectionName]);

    const added = [];
    const removed = [];
    const changed = [];

    for (const [documentPath, currentDocument] of currentMap.entries()) {
      if (!baseMap.has(documentPath)) {
        added.push(documentPath);
        continue;
      }
      const baseDocument = baseMap.get(documentPath);
      if (baseDocument.normalizedFields !== currentDocument.normalizedFields) {
        changed.push(documentPath);
      }
    }

    for (const documentPath of baseMap.keys()) {
      if (!currentMap.has(documentPath)) {
        removed.push(documentPath);
      }
    }

    collections[collectionName] = {added, removed, changed};
    totalAdded += added.length;
    totalRemoved += removed.length;
    totalChanged += changed.length;
  }

  return {
    generatedAt: new Date().toISOString(),
    totalAdded,
    totalRemoved,
    totalChanged,
    collections,
  };
}

function renderOperationsReportHtml(snapshot, readiness, diffSummary, options = {}) {
  const reportPath = options.reportPath || "";
  const appEvidence = options.appEvidence || null;
  const roleSections = readiness.roles.map((role) => `
    <section class="panel">
      <div class="panel-header">
        <h2>${escapeHtml(role.label)}</h2>
        <span class="badge ${role.ready ? "ok" : "warn"}">${role.ready ? "준비됨" : "확인 필요"}</span>
      </div>
      <ul class="check-list">
        ${role.checks.map((check) => `
          <li class="check-item">
            <div class="check-head">
              <strong>${escapeHtml(check.label)}</strong>
              <span class="badge ${check.pass ? "ok" : "warn"}">${check.pass ? "통과" : "미달"}</span>
            </div>
            <div class="check-detail">${escapeHtml(check.detail)}</div>
          </li>
        `).join("")}
      </ul>
    </section>
  `).join("");

  const scenarioRows = readiness.scenarios.map((scenario) => `
    <tr>
      <td>${escapeHtml(scenario.label)}</td>
      <td><span class="badge ${scenario.pass ? "ok" : "warn"}">${scenario.pass ? "통과" : "미달"}</span></td>
      <td>${escapeHtml(scenario.detail)}</td>
    </tr>
  `).join("");

  const baselineRows = snapshot.baselineStatuses.map((status) => `
    <tr>
      <td>${escapeHtml(status.role)}</td>
      <td>${escapeHtml(status.email)}</td>
      <td>${escapeHtml(status.authStatus)}</td>
      <td>${escapeHtml(status.userDocumentStatus)}</td>
      <td>${escapeHtml(status.uid || "")}</td>
    </tr>
  `).join("");

  const collectionRows = MANAGED_COLLECTIONS.map((collectionName) => `
    <tr>
      <td>${escapeHtml(collectionName)}</td>
      <td>${snapshot.collectionCounts[collectionName] ?? 0}</td>
    </tr>
  `).join("");

  const diffSection = !diffSummary ? "" : `
    <section class="panel">
      <div class="panel-header">
        <h2>백업 대비 diff</h2>
        <span class="meta">추가 ${diffSummary.totalAdded} / 삭제 ${diffSummary.totalRemoved} / 변경 ${diffSummary.totalChanged}</span>
      </div>
      ${MANAGED_COLLECTIONS.map((collectionName) => {
        const collectionDiff = diffSummary.collections[collectionName];
        if (!collectionDiff) {
          return "";
        }
        if (collectionDiff.added.length === 0 &&
            collectionDiff.removed.length === 0 &&
            collectionDiff.changed.length === 0) {
          return "";
        }
        return `
          <div class="diff-block">
            <h3>${escapeHtml(collectionName)}</h3>
            <p>추가 ${collectionDiff.added.length} / 삭제 ${collectionDiff.removed.length} / 변경 ${collectionDiff.changed.length}</p>
          </div>
        `;
      }).join("")}
    </section>
  `;

  const appEvidenceSection = renderAppEvidenceSection(appEvidence, reportPath);

  return `<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <title>보들 Firebase 운영 리포트</title>
  <style>
    body {
      margin: 0;
      padding: 32px;
      font-family: "Segoe UI", "Noto Sans KR", sans-serif;
      background: #f4f6f8;
      color: #14202b;
    }
    .layout {
      max-width: 1120px;
      margin: 0 auto;
      display: grid;
      gap: 16px;
    }
    .hero, .panel {
      background: #ffffff;
      border: 1px solid #d8e0e7;
      border-radius: 8px;
      padding: 20px 22px;
    }
    .hero h1, .panel h2, .panel h3 {
      margin: 0;
    }
    .hero p {
      margin: 8px 0 0;
      color: #4d6275;
    }
    .grid {
      display: grid;
      gap: 16px;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    }
    .stat {
      background: #ffffff;
      border: 1px solid #d8e0e7;
      border-radius: 8px;
      padding: 16px;
    }
    .stat strong {
      display: block;
      font-size: 28px;
      margin-top: 8px;
    }
    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-bottom: 14px;
    }
    .meta {
      color: #5a6f80;
      font-size: 14px;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 68px;
      height: 28px;
      padding: 0 10px;
      font-size: 13px;
      border-radius: 6px;
      font-weight: 600;
    }
    .badge.ok {
      background: #ddf5e6;
      color: #155b2f;
    }
    .badge.warn {
      background: #fff1d6;
      color: #8a5a00;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
    }
    th, td {
      padding: 10px 8px;
      border-bottom: 1px solid #e6edf3;
      text-align: left;
      vertical-align: top;
    }
    th {
      color: #5a6f80;
      font-weight: 600;
    }
    .check-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 12px;
    }
    .check-item {
      border: 1px solid #e6edf3;
      border-radius: 8px;
      padding: 12px 14px;
    }
    .check-head {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      align-items: center;
    }
    .check-detail {
      margin-top: 8px;
      color: #4d6275;
      font-size: 14px;
      line-height: 1.5;
    }
    .diff-block + .diff-block {
      margin-top: 10px;
    }
    .evidence-grid {
      display: grid;
      gap: 16px;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    }
    .evidence-card {
      border: 1px solid #e6edf3;
      border-radius: 8px;
      overflow: hidden;
      background: #fbfcfd;
    }
    .evidence-media {
      aspect-ratio: 16 / 10;
      background: #e9eef3;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #5a6f80;
      font-size: 13px;
    }
    .evidence-media img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
    .evidence-body {
      padding: 12px 14px 14px;
      display: grid;
      gap: 8px;
    }
    .evidence-title {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      align-items: center;
    }
    .evidence-meta {
      color: #5a6f80;
      font-size: 13px;
      line-height: 1.5;
    }
    .text-dim {
      color: #5a6f80;
    }
  </style>
</head>
<body>
  <div class="layout">
    <section class="hero">
      <h1>보들 Firebase 운영 리포트</h1>
      <p>프로젝트 ${escapeHtml(snapshot.projectId)} / 생성 시각 ${escapeHtml(snapshot.generatedAt)}</p>
    </section>
    <section class="grid">
      <div class="stat">
        역할 준비도
        <strong>${readiness.roles.filter((role) => role.ready).length}/${readiness.roles.length}</strong>
      </div>
      <div class="stat">
        샘플 시나리오
        <strong>${readiness.scenarios.filter((scenario) => scenario.pass).length}/${readiness.scenarios.length}</strong>
      </div>
      <div class="stat">
        관리 컬렉션
        <strong>${MANAGED_COLLECTIONS.length}</strong>
      </div>
      <div class="stat">
        총 문서 수
        <strong>${MANAGED_COLLECTIONS.reduce((sum, collectionName) => sum + (snapshot.collectionCounts[collectionName] || 0), 0)}</strong>
      </div>
    </section>
    <section class="panel">
      <div class="panel-header">
        <h2>기준선 계정</h2>
      </div>
      <table>
        <thead>
          <tr>
            <th>역할</th>
            <th>이메일</th>
            <th>Auth</th>
            <th>users</th>
            <th>UID</th>
          </tr>
        </thead>
        <tbody>${baselineRows}</tbody>
      </table>
    </section>
    <section class="panel">
      <div class="panel-header">
        <h2>샘플 시나리오</h2>
      </div>
      <table>
        <thead>
          <tr>
            <th>시나리오</th>
            <th>상태</th>
            <th>상세</th>
          </tr>
        </thead>
        <tbody>${scenarioRows}</tbody>
      </table>
    </section>
    ${roleSections}
    <section class="panel">
      <div class="panel-header">
        <h2>컬렉션 문서 수</h2>
      </div>
      <table>
        <thead>
          <tr>
            <th>컬렉션</th>
            <th>문서 수</th>
          </tr>
        </thead>
        <tbody>${collectionRows}</tbody>
      </table>
    </section>
    ${appEvidenceSection}
    ${diffSection}
  </div>
</body>
</html>`;
}

function renderAppEvidenceSection(appEvidence, reportPath) {
  if (!appEvidence) {
    return `
      <section class="panel">
        <div class="panel-header">
          <h2>앱 화면 증적</h2>
          <span class="badge warn">없음</span>
        </div>
        <p class="text-dim">아직 연결된 앱 화면 증적 파일이 없습니다. 필요하면 \`capture:app\`으로 현재 화면을 캡처한 뒤 다시 리포트를 생성합니다.</p>
      </section>
    `;
  }

  const screenCards = appEvidence.screens.map((screen) => {
    const imagePath = resolveEvidenceAssetPath(appEvidence.filePath, screen.imagePath);
    const imageHref = imagePath && reportPath ? toRelativeHref(reportPath, imagePath) : "";
    const imageTag = imageHref ? `<img src="${imageHref}" alt="${escapeHtml(screen.title)}">` : "이미지 없음";
    return `
      <article class="evidence-card">
        <div class="evidence-media">${imageTag}</div>
        <div class="evidence-body">
          <div class="evidence-title">
            <strong>${escapeHtml(screen.title)}</strong>
            <span class="badge ${toEvidenceBadgeTone(screen.status)}">${toEvidenceStatusLabel(screen.status)}</span>
          </div>
          <div class="evidence-meta">
            <div>역할: ${escapeHtml(screen.role || "COMMON")}</div>
            <div>액티비티: ${escapeHtml(screen.activity || "기록 없음")}</div>
            <div>기록 시각: ${escapeHtml(screen.capturedAt || appEvidence.generatedAt || "기록 없음")}</div>
          </div>
          <div class="check-detail">${escapeHtml(screen.note || "비고 없음")}</div>
        </div>
      </article>
    `;
  }).join("");

  const deviceLabel = [
    appEvidence.device.manufacturer,
    appEvidence.device.model,
  ].filter(Boolean).join(" ");

  return `
    <section class="panel">
      <div class="panel-header">
        <h2>앱 화면 증적</h2>
        <span class="meta">통과 ${appEvidence.summary.passedCount} / 경고 ${appEvidence.summary.warningCount} / 실패 ${appEvidence.summary.failedCount}</span>
      </div>
      <p class="text-dim">
        소스 ${escapeHtml(appEvidence.source)} / 빌드 ${escapeHtml(appEvidence.buildVariant)} /
        디바이스 ${escapeHtml(deviceLabel || "기록 없음")} / Android ${escapeHtml(appEvidence.device.androidVersion || "기록 없음")}
      </p>
      <p class="text-dim">증적 파일: ${escapeHtml(appEvidence.filePath)}</p>
      <div class="evidence-grid">
        ${screenCards || `<div class="text-dim">기록된 화면 증적이 없습니다.</div>`}
      </div>
    </section>
  `;
}

function resolveEvidenceAssetPath(evidenceFilePath, assetPath) {
  if (!assetPath) {
    return "";
  }
  const resolvedPath = path.resolve(path.dirname(evidenceFilePath), assetPath);
  return fs.existsSync(resolvedPath) ? resolvedPath : "";
}

function toRelativeHref(reportPath, assetPath) {
  return encodeURI(path.relative(path.dirname(reportPath), assetPath).split(path.sep).join("/"));
}

function toEvidenceBadgeTone(status) {
  if (status === "failed") {
    return "warn";
  }
  return status === "warning" ? "warn" : "ok";
}

function toEvidenceStatusLabel(status) {
  if (status === "failed") {
    return "실패";
  }
  return status === "warning" ? "경고" : "통과";
}

function buildReportFileName() {
  const now = new Date();
  const token = [
    String(now.getFullYear()),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
    "-",
    String(now.getHours()).padStart(2, "0"),
    String(now.getMinutes()).padStart(2, "0"),
    String(now.getSeconds()).padStart(2, "0"),
  ].join("");
  return `firestore-operations-report-${token}.html`;
}

function writeReportFile(outputPath, html) {
  fs.mkdirSync(path.dirname(outputPath), {recursive: true});
  fs.writeFileSync(outputPath, html, "utf8");
}

function resolveReportOutputPath(outputPath) {
  if (outputPath) {
    return path.resolve(process.cwd(), outputPath);
  }
  return path.resolve(process.cwd(), "reports", buildReportFileName());
}

function loadBackupSnapshot(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function createRoleReadiness(role, label, checks) {
  return {
    role,
    label,
    ready: checks.every((check) => check.pass),
    checks,
  };
}

function createScenarioCheck(id, label, pass, detail) {
  return {id, label, pass, detail};
}

function createCheck(id, label, pass, detail) {
  return {id, label, pass, detail};
}

function requireBaselineRole(statusesByRole, role) {
  const status = statusesByRole.get(role);
  if (!status) {
    throw new Error(`${role} 기준선 계정을 찾지 못했습니다.`);
  }
  return status;
}

function isBaselineStatusReady(status) {
  return status.authStatus === "present" && status.userDocumentStatus === "present" && Boolean(status.uid);
}

async function loadBaselineStatuses(context) {
  const statuses = [];
  for (const baselineUser of BASELINE_USERS) {
    const authUser = await lookupAuthUserByEmail(context, baselineUser.email);
    if (!authUser) {
      statuses.push({
        role: baselineUser.role,
        email: baselineUser.email,
        uid: "",
        authStatus: "missing",
        userDocumentStatus: "missing",
        userDocument: null,
      });
      continue;
    }

    const userDocument = await getDocument(context, `users/${authUser.localId}`);
    statuses.push({
      role: baselineUser.role,
      email: baselineUser.email,
      uid: authUser.localId,
      authStatus: "present",
      userDocumentStatus: userDocument ? "present" : "missing",
      userDocument: userDocument ? fromFirestoreFields(userDocument.fields || {}) : null,
    });
  }
  return statuses;
}

function fromFirestoreFields(fields) {
  const data = {};
  for (const [key, value] of Object.entries(fields || {})) {
    data[key] = fromFirestoreValue(value);
  }
  return data;
}

function fromFirestoreValue(value) {
  if (!value || typeof value !== "object") {
    return value;
  }
  if (Object.prototype.hasOwnProperty.call(value, "stringValue")) {
    return value.stringValue;
  }
  if (Object.prototype.hasOwnProperty.call(value, "integerValue")) {
    return Number(value.integerValue);
  }
  if (Object.prototype.hasOwnProperty.call(value, "doubleValue")) {
    return Number(value.doubleValue);
  }
  if (Object.prototype.hasOwnProperty.call(value, "booleanValue")) {
    return Boolean(value.booleanValue);
  }
  if (Object.prototype.hasOwnProperty.call(value, "nullValue")) {
    return null;
  }
  if (Object.prototype.hasOwnProperty.call(value, "timestampValue")) {
    return value.timestampValue;
  }
  if (Object.prototype.hasOwnProperty.call(value, "mapValue")) {
    return fromFirestoreFields(value.mapValue?.fields || {});
  }
  if (Object.prototype.hasOwnProperty.call(value, "arrayValue")) {
    return (value.arrayValue?.values || []).map((item) => fromFirestoreValue(item));
  }
  return value;
}

function toDataMap(documents) {
  const documentMap = new Map();
  for (const document of documents || []) {
    documentMap.set(document.id, document.data);
  }
  return documentMap;
}

function findById(documents, documentId) {
  for (const document of documents || []) {
    if (document.id === documentId) {
      return document;
    }
  }
  return null;
}

function pushMapList(targetMap, key, value) {
  if (!key) {
    return;
  }
  if (!targetMap.has(key)) {
    targetMap.set(key, []);
  }
  targetMap.get(key).push(value);
}

function hasGuideForRequest(guides, request) {
  return (guides || []).some((guide) =>
    guide.data.hospitalName === request.hospitalName &&
      guide.data.departmentName === request.departmentName,
  );
}

function toSnapshotDocumentMap(documents) {
  const documentMap = new Map();
  for (const document of Array.isArray(documents) ? documents : []) {
    const documentPath = typeof document?.path === "string" ? document.path.trim() : "";
    if (!documentPath) {
      continue;
    }
    const fields = document.fields ? document.fields : toFirestoreFields(document.data || {});
    documentMap.set(documentPath, {
      normalizedFields: JSON.stringify(sortValue(fields)),
    });
  }
  return documentMap;
}

function toFirestoreFields(data) {
  const fields = {};
  for (const [key, value] of Object.entries(data)) {
    fields[key] = toFirestoreValue(value);
  }
  return fields;
}

function toFirestoreValue(value) {
  if (value === null) {
    return {nullValue: null};
  }
  if (Array.isArray(value)) {
    return {
      arrayValue: {
        values: value.map((item) => toFirestoreValue(item)),
      },
    };
  }
  if (typeof value === "number") {
    if (Number.isInteger(value)) {
      return {integerValue: String(value)};
    }
    return {doubleValue: value};
  }
  if (typeof value === "boolean") {
    return {booleanValue: value};
  }
  if (typeof value === "object") {
    return {
      mapValue: {
        fields: toFirestoreFields(value),
      },
    };
  }
  return {stringValue: String(value)};
}

function sortValue(value) {
  if (Array.isArray(value)) {
    return value.map((item) => sortValue(item));
  }
  if (!value || typeof value !== "object") {
    return value;
  }
  const sorted = {};
  for (const key of Object.keys(value).sort()) {
    sorted[key] = sortValue(value[key]);
  }
  return sorted;
}

function escapeHtml(value) {
  return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll("\"", "&quot;")
      .replaceAll("'", "&#39;");
}

module.exports = {
  buildDiffSummary,
  buildRoleReadiness,
  loadBackupSnapshot,
  loadOperationsSnapshot,
  renderOperationsReportHtml,
  resolveAppNavigationEvidencePath,
  resolveReportOutputPath,
  writeReportFile,
};
