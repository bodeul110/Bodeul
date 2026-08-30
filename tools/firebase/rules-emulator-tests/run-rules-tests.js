const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  setLogLevel,
  serverTimestamp,
  updateDoc,
  where,
} = require("firebase/firestore");
const {
  deleteObject,
  getBytes,
  ref,
  uploadBytes,
} = require("firebase/storage");

const repoRoot = path.resolve(__dirname, "..", "..", "..");
const toolsRoot = path.resolve(__dirname, "..");
const projectId = process.env.RULES_TEST_PROJECT_ID || "bodeul-rules-test";
const insideEmulator = process.argv.includes("--inside-emulator");
const adminOnlyCollections = [
  "adminSettlementRecords",
  "adminEmergencyIssues",
  "adminActionNotifications",
  "adminAuditLogs",
  "adminActionDeliveries",
  "adminActionDeliveryJobs",
];

setLogLevel("silent");

const users = {
  admin: "admin-user",
  patient: "patient-user",
  guardian: "guardian-user",
  manager: "manager-user",
  otherManager: "other-manager-user",
  outsider: "outsider-user",
};

function firebaseCliScriptPath() {
  return path.join(
      toolsRoot,
      "node_modules",
      "firebase-tools",
      "lib",
      "bin",
      "firebase.js",
  );
}

function runInsideEmulator() {
  const firebaseCliScript = firebaseCliScriptPath();
  if (!fs.existsSync(firebaseCliScript)) {
    console.error("firebase-tools 실행 파일을 찾지 못했습니다. tools/firebase에서 npm install 또는 npm ci를 먼저 실행해 주세요.");
    process.exit(1);
  }

  const command = `"${process.execPath}" "${__filename}" --inside-emulator`;
  const args = [
        "emulators:exec",
        "--only",
        "firestore,storage",
        "--project",
        projectId,
        command,
  ];
  const result = spawnSync(
      process.execPath,
      [firebaseCliScript, ...args],
      {
        cwd: repoRoot,
        env: {
          ...process.env,
          RULES_TEST_PROJECT_ID: projectId,
        },
        stdio: "inherit",
      },
  );

  if (result.error) {
    console.error(result.error);
  }
  process.exit(result.status ?? 1);
}

function userDocument(role, name) {
  return {
    role,
    name,
    email: `${name}@bodeul.test`,
    phone: "01000000000",
  };
}

function managerDocumentSubmissionFiles({
  managerId = users.manager,
  documentKey = "license",
  fullPath = `manager-documents/${managerId}/${documentKey}/seed.png`,
  pathMap = fullPath,
  legacyPath = fullPath,
} = {}) {
  const result = {
    managerDocumentFiles: {
      [documentKey]: { fullPath, contentType: "image/png" },
    },
    managerDocumentFilePaths: {
      [documentKey]: pathMap,
    },
  };
  if (documentKey === "license") {
    result.managerLicenseStoragePath = legacyPath;
  }
  return result;
}

function legacyManagerDocumentFiles(managerId = users.manager) {
  const idCardPath = `manager-documents/${managerId}/idCard/legacy.jpg`;
  const criminalRecordPath = `manager-documents/${managerId}/criminalRecord/legacy.webp`;
  const healthCertificatePath =
    `manager-documents/${managerId}/healthCertificate/legacy.png`;
  return {
    managerDocumentFiles: {
      idCard: {fullPath: idCardPath, contentType: "image/jpeg"},
      criminalRecord: {fullPath: criminalRecordPath, contentType: "image/webp"},
      healthCertificate: {fullPath: healthCertificatePath, contentType: "image/png"},
    },
    managerDocumentFilePaths: {
      idCard: idCardPath,
      criminalRecord: criminalRecordPath,
      healthCertificate: healthCertificatePath,
    },
    managerIdCardStoragePath: idCardPath,
    managerCriminalRecordStoragePath: criminalRecordPath,
    managerHealthCertificateStoragePath: healthCertificatePath,
  };
}

function appointmentRequestDocument(overrides = {}) {
  return {
    patientUserId: users.patient,
    patientName: "환자",
    patientPhone: "01011112222",
    patientEmail: "patient@bodeul.test",
    guardianUserId: users.guardian,
    guardianName: "보호자",
    guardianPhone: "01033334444",
    guardianEmail: "guardian@bodeul.test",
    hospitalName: "보들병원",
    departmentName: "내과",
    appointmentAt: "2026-07-01 10:00",
    meetingPlace: "1층 로비",
    specialNotes: "",
    patientConditionSummary: "",
    medicationSummary: "",
    mobilitySupportCode: "NONE",
    tripTypeCode: "ROUND_TRIP",
    managerGenderPreferenceCode: "ANY",
    paymentMethodCode: "CARD",
    couponCode: "",
    basePrice: 100000,
    optionSurchargePrice: 0,
    couponDiscountPrice: 0,
    finalPrice: 100000,
    paymentStatusCode: "PENDING",
    paymentApprovalCode: "",
    paymentApprovedAt: 0,
    paymentProviderLabel: "",
    appointmentAtEpochMillis: 1782871200000,
    appointmentDateKey: "2026-07-01",
    reminderStages: [],
    status: "MATCHED",
    managerUserId: users.manager,
    requesterUserId: users.patient,
    requesterRole: "PATIENT",
    requesterName: "환자",
    requesterPhone: "01011112222",
    createdAt: 1,
    updatedAt: 1,
    ...overrides,
  };
}

function companionSessionDocument(overrides = {}) {
  return {
    appointmentRequestId: "request-main",
    patientUserId: users.patient,
    guardianUserId: users.guardian,
    managerUserId: users.manager,
    currentStepOrder: 1,
    currentStatus: "READY",
    guardianUpdate: "",
    locationSummary: "",
    fieldPhotoNote: "",
    medicationNote: "",
    pharmacySummary: "",
    prescriptionCollected: false,
    pharmacyCompleted: false,
    medicationGuidanceCompleted: false,
    liveLocationSharingActive: false,
    sharedLocationHistory: [],
    chatMessages: [],
    createdAt: 1,
    updatedAt: 1,
    ...overrides,
  };
}

async function seedFirestore(testEnv) {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await Promise.all([
      setDoc(doc(db, "users", users.admin), userDocument("ADMIN", "admin")),
      setDoc(doc(db, "users", users.patient), userDocument("PATIENT", "patient")),
      setDoc(doc(db, "users", users.guardian), userDocument("GUARDIAN", "guardian")),
      setDoc(doc(db, "users", users.manager), userDocument("MANAGER", "manager")),
      setDoc(doc(db, "users", users.otherManager), userDocument("MANAGER", "other-manager")),
      setDoc(doc(db, "users", users.outsider), userDocument("PATIENT", "outsider")),
      setDoc(doc(db, "appointmentRequests", "request-main"), appointmentRequestDocument()),
      setDoc(doc(db, "companionSessions", "session-main"), companionSessionDocument()),
      setDoc(doc(db, "sessionReports", "report-main"), {
        sessionId: "session-main",
        summary: "진료 리포트",
        createdAt: 1,
      }),
      setDoc(doc(db, "appointmentFollowUps", "request-main"), {
        requestId: "request-main",
        reviewRatingCode: "SATISFIED",
        updatedAt: 1,
      }),
      setDoc(doc(db, "supportInquiries", "inquiry-main"), {
        managerUserId: users.manager,
        title: "문의",
        body: "내용",
        status: "RECEIVED",
        createdAt: 1,
      }),
      setDoc(doc(db, "clientSupportRequests", "client-support-main"), {
        userId: users.patient,
        userRole: "PATIENT",
        title: "고객 문의",
        body: "내용",
        status: "RECEIVED",
        createdAt: 1,
      }),
      setDoc(doc(db, "appointmentReminderJobs", "job-main"), {
        appointmentRequestId: "request-main",
        state: "PENDING",
        createdAt: 1,
      }),
    ]);
  });
}

async function seedStorage(testEnv) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const storage = context.storage();
    await Promise.all([
      uploadBytes(
          ref(storage, "manager-documents/manager-user/idCard/seed.pdf"),
          new Uint8Array([1, 2, 3]),
          { contentType: "application/pdf" },
      ),
      uploadBytes(
          ref(storage, "companion-chat-attachments/session-main/seed.png"),
          new Uint8Array([1, 2, 3]),
          { contentType: "image/png" },
      ),
    ]);
  });
}

function firestoreFor(testEnv, uid) {
  return testEnv.authenticatedContext(uid).firestore();
}

function storageFor(testEnv, uid) {
  return testEnv.authenticatedContext(uid).storage();
}

function testCases(testEnv) {
  return [
    {
      name: "users 문서는 본인만 읽고 브라우저 ADMIN 접근과 역할 위조를 차단한다",
      run: async () => {
        await seedFirestore(testEnv);

        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.patient), "users", users.patient)));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.patient), "users", users.guardian)));
        await assertFails(getDocs(collection(firestoreFor(testEnv, users.admin), "users")));
        await assertFails(getDocs(collection(firestoreFor(testEnv, users.manager), "users")));
        await assertSucceeds(setDoc(
            doc(firestoreFor(testEnv, "new-patient-user"), "users", "new-patient-user"),
            userDocument("PATIENT", "new-patient"),
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, "new-admin-user"), "users", "new-admin-user"),
            userDocument("ADMIN", "new-admin"),
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, "forged-retention-manager"), "users", "forged-retention-manager"),
            {
              ...userDocument("MANAGER", "forged-retention-manager"),
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentOriginalsDeletedAt: serverTimestamp(),
            },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, "forged-approval-manager"), "users", "forged-approval-manager"),
            {
              ...userDocument("MANAGER", "forged-approval-manager"),
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentApprovalEvidence: { payloadHash: "forged" },
            },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, "forged-revision-manager"), "users", "forged-revision-manager"),
            {
              ...userDocument("MANAGER", "forged-revision-manager"),
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentReviewedSubmissionRevision: "forged-revision",
            },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, "forged-migration-manager"), "users", "forged-migration-manager"),
            {
              ...userDocument("MANAGER", "forged-migration-manager"),
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentEvidenceMigration: {migrationId: "forged"},
            },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, "forged-submission-manager"), "users", "forged-submission-manager"),
            {
              ...userDocument("MANAGER", "forged-submission-manager"),
              managerDocumentSummary: "생성 단계에서 주입한 제출",
              managerDocumentFiles: {
                idCard: { fullPath: "manager-documents/forged-submission-manager/idCard/forged.jpg" },
              },
              managerDocumentUpdatedAt: new Date("2100-01-01T00:00:00.000Z"),
            },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, "forged-patient-state"), "users", "forged-patient-state"),
            {
              ...userDocument("PATIENT", "forged-patient-state"),
              managerDocumentStatus: "NOT_SUBMITTED",
            },
        ));
        await assertSucceeds(setDoc(
            doc(firestoreFor(testEnv, "new-manager-user"), "users", "new-manager-user"),
            {
              ...userDocument("MANAGER", "new-manager"),
              managerDocumentStatus: "NOT_SUBMITTED",
            },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, "malicious-manager-user"), "users", "malicious-manager-user"),
            {
              ...userDocument("MANAGER", "malicious-manager"),
              managerDocumentStatus: "APPROVED",
              managerDocumentReviewNote: "자가 승인",
              managerDocumentReviewedByAdminUserId: "malicious-manager-user",
              managerDocumentHistory: [{ status: "APPROVED" }],
              managerDocumentLegalHoldUntil: 4_000_000_000_000,
            },
        ));
        await assertSucceeds(setDoc(
            doc(firestoreFor(testEnv, users.guardian), "users", users.guardian),
            {
              notificationTokens: ["guardian-device-token"],
              notificationTokenUpdatedAt: 2,
              notificationTokenPlatform: "android",
            },
            { merge: true },
        ));
        await assertSucceeds(updateDoc(
            doc(firestoreFor(testEnv, users.guardian), "users", users.guardian),
            {
              "notificationTokenEntries.guardian-device-token": {
                token: "guardian-device-token",
                platform: "android",
                updatedAtMillis: 2,
              },
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.patient), "users", users.patient),
            { role: "ADMIN" },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentStatus: "APPROVED" },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentStatus: "PENDING_REVIEW" },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentSummary: "파일 없는 단독 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentLegalHoldUntil: 4_000_000_000_000 },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentOriginalsDeletedAt: serverTimestamp() },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentApprovalEvidence: { payloadHash: "forged" } },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentReviewedSubmissionRevision: "forged-revision" },
        ));
        await testEnv.withSecurityRulesDisabled(async (context) => {
          await updateDoc(doc(context.firestore(), "users", users.manager), {
            managerDocumentOriginalsDeletedAt: 100,
            managerDocumentApprovalEvidence: { payloadHash: "server-evidence" },
            managerDocumentReviewedSubmissionRevision: "server-revision",
            managerDocumentEvidenceMigration: {
              migrationId: "server-migration",
              sourceKey: "healthCertificate",
              destinationKey: "nursingLicense",
            },
          });
        });
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentOriginalsDeletedAt: deleteField() },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentApprovalEvidence: deleteField() },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentReviewedSubmissionRevision: deleteField() },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentEvidenceMigration: {
                migrationId: "forged",
                sourceKey: "healthCertificate",
                destinationKey: "nursingLicense",
              },
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {managerDocumentEvidenceMigration: deleteField()},
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.admin), "users", users.manager),
            { managerDocumentLegalHoldUntil: 4_000_000_000_000 },
        ));
        const regexUid = "manager.*";
        await assertSucceeds(setDoc(
            doc(firestoreFor(testEnv, regexUid), "users", regexUid),
            {
              ...userDocument("MANAGER", "regex-manager"),
              managerDocumentStatus: "NOT_SUBMITTED",
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, regexUid), "users", regexUid),
            {
              ...managerDocumentSubmissionFiles({managerId: "manager-other"}),
              managerDocumentSummary: "정규식 UID 교차 경로 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              ...managerDocumentSubmissionFiles({managerId: users.otherManager}),
              managerDocumentSummary: "다른 매니저 경로 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        const wrongDocumentKeyPath =
          `manager-documents/${users.manager}/nursingLicense/license.jpg`;
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              ...managerDocumentSubmissionFiles({
                fullPath: wrongDocumentKeyPath,
                pathMap: wrongDocumentKeyPath,
                legacyPath: wrongDocumentKeyPath,
              }),
              managerDocumentSummary: "다른 문서 키 경로 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              ...managerDocumentSubmissionFiles({
                pathMap:
                  `manager-documents/${users.manager}/license/path-map-mismatch.jpg`,
              }),
              managerDocumentSummary: "경로 맵 불일치 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              ...managerDocumentSubmissionFiles({
                legacyPath:
                  `manager-documents/${users.manager}/license/legacy-mismatch.jpg`,
              }),
              managerDocumentSummary: "레거시 경로 불일치 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        const missingLegacyAlias = managerDocumentSubmissionFiles();
        delete missingLegacyAlias.managerLicenseStoragePath;
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              ...missingLegacyAlias,
              managerDocumentSummary: "레거시 경로 누락 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        const healthCertificatePath =
          `manager-documents/${users.manager}/healthCertificate/health.jpg`;
        const newHealthSubmission = managerDocumentSubmissionFiles();
        newHealthSubmission.managerDocumentFiles.healthCertificate = {
          fullPath: healthCertificatePath,
          contentType: "image/jpeg",
        };
        newHealthSubmission.managerDocumentFilePaths.healthCertificate =
          healthCertificatePath;
        newHealthSubmission.managerHealthCertificateStoragePath =
          healthCertificatePath;
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              ...newHealthSubmission,
              managerDocumentSummary: "레거시 건강진단서 신규 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        for (const legacyKey of ["idCard", "criminalRecord"]) {
          const legacyPath =
            `manager-documents/${users.manager}/${legacyKey}/new.jpg`;
          const sensitiveSubmission = managerDocumentSubmissionFiles();
          sensitiveSubmission.managerDocumentFiles[legacyKey] = {
            fullPath: legacyPath,
            contentType: "image/jpeg",
          };
          sensitiveSubmission.managerDocumentFilePaths[legacyKey] = legacyPath;
          await assertFails(updateDoc(
              doc(firestoreFor(testEnv, users.manager), "users", users.manager),
              {
                ...sensitiveSubmission,
                managerDocumentSummary: "민감 원본 신규 제출",
                managerDocumentStatus: "PENDING_REVIEW",
                managerDocumentUpdatedAt: serverTimestamp(),
              },
          ));
        }
        const bothCanonical = managerDocumentSubmissionFiles();
        const nursingPath =
          `manager-documents/${users.manager}/nursingLicense/nursing.png`;
        bothCanonical.managerDocumentFiles.nursingLicense = {
          fullPath: nursingPath,
          contentType: "image/png",
        };
        bothCanonical.managerDocumentFilePaths.nursingLicense = nursingPath;
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              ...bothCanonical,
              managerDocumentSummary: "자격 증빙 두 종류 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertSucceeds(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              ...managerDocumentSubmissionFiles(),
              managerDocumentSummary: "정상 경로 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        const nursingManager = "nursing-manager-user";
        await assertSucceeds(setDoc(
            doc(firestoreFor(testEnv, nursingManager), "users", nursingManager),
            {
              ...userDocument("MANAGER", "nursing-manager"),
              managerDocumentStatus: "NOT_SUBMITTED",
            },
        ));
        await assertSucceeds(updateDoc(
            doc(firestoreFor(testEnv, nursingManager), "users", nursingManager),
            {
              ...managerDocumentSubmissionFiles({
                managerId: nursingManager,
                documentKey: "nursingLicense",
              }),
              managerDocumentSummary: "간호사 면허 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await testEnv.withSecurityRulesDisabled(async (context) => {
          await updateDoc(doc(context.firestore(), "users", users.manager), {
            managerDocumentStatus: "APPROVED",
            managerDocumentSummary: "승인된 제출 요약",
            managerDocumentReviewNote: "관리자 확인 완료",
            managerDocumentReviewedAt: 100,
            managerDocumentReviewedByName: "관리자",
            managerDocumentReviewedByAdminUserId: users.admin,
            managerDocumentHistory: [{ status: "APPROVED", occurredAt: 100 }],
            managerDocumentLegalHoldUntil: 4_000_000_000_000,
            ...managerDocumentSubmissionFiles(),
            managerDocumentUpdatedAt: 100,
          });
        });
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentReviewNote: "",
              managerDocumentReviewedAt: 101,
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentSummary: "바꾼 제출 요약",
              managerDocumentStatus: "APPROVED",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              "managerDocumentFiles.license.fullPath":
                "manager-documents/manager-user/license/replaced.jpg",
              managerDocumentStatus: "APPROVED",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertSucceeds(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentSummary: "재제출 요약",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertSucceeds(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentSummary: "",
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentSummary: "",
              "managerDocumentFiles.license.fullPath":
                "manager-documents/manager-user/license/replaced.jpg",
              "managerDocumentFilePaths.license":
                "manager-documents/manager-user/license/replaced.jpg",
              managerLicenseStoragePath:
                "manager-documents/manager-user/license/replaced.jpg",
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        const draftManager = "draft-manager-user";
        await assertSucceeds(setDoc(
            doc(firestoreFor(testEnv, draftManager), "users", draftManager),
            {
              ...userDocument("MANAGER", "draft-manager"),
              managerDocumentStatus: "NOT_SUBMITTED",
            },
        ));
        await assertSucceeds(updateDoc(
            doc(firestoreFor(testEnv, draftManager), "users", draftManager),
            {
              ...managerDocumentSubmissionFiles({managerId: draftManager}),
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await testEnv.withSecurityRulesDisabled(async (context) => {
          await updateDoc(doc(context.firestore(), "users", users.manager), {
            managerDocumentStatus: "APPROVED",
            managerDocumentSummary: "승인된 제출 요약",
          });
        });
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              "managerDocumentFiles.license.fullPath":
                "manager-documents/manager-user/license/reviewed.jpg",
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentSummary: "클라이언트 시각 위조",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: new Date("2100-01-01T00:00:00.000Z"),
            },
        ));
        await testEnv.withSecurityRulesDisabled(async (context) => {
          await updateDoc(doc(context.firestore(), "users", users.manager), {
            managerDocumentStatus: "REJECTED",
            managerDocumentSummary: "같은 자료 재심사",
            "managerDocumentFiles.license.contentType": "application/pdf",
          });
        });
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await testEnv.withSecurityRulesDisabled(async (context) => {
          const canonical = managerDocumentSubmissionFiles();
          const legacy = legacyManagerDocumentFiles();
          await updateDoc(doc(context.firestore(), "users", users.manager), {
            managerDocumentStatus: "REJECTED",
            managerDocumentFiles: {
              ...canonical.managerDocumentFiles,
              ...legacy.managerDocumentFiles,
            },
            managerDocumentFilePaths: {
              ...canonical.managerDocumentFilePaths,
              ...legacy.managerDocumentFilePaths,
            },
            managerLicenseStoragePath: canonical.managerLicenseStoragePath,
            managerIdCardStoragePath: legacy.managerIdCardStoragePath,
            managerCriminalRecordStoragePath:
              legacy.managerCriminalRecordStoragePath,
            managerHealthCertificateStoragePath:
              legacy.managerHealthCertificateStoragePath,
          });
        });
        await assertSucceeds(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              "managerDocumentFiles.idCard.fullPath":
                "manager-documents/manager-user/idCard/changed.jpg",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              "managerDocumentFilePaths.healthCertificate": deleteField(),
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        for (const [replacementManager, documentKey, withAlias] of [
          ["legacy-health-to-nursing", "nursingLicense", true],
          ["legacy-health-to-license", "license", false],
        ]) {
          const legacyPath =
            `manager-documents/${replacementManager}/healthCertificate/legacy.png`;
          await testEnv.withSecurityRulesDisabled(async (context) => {
            const data = {
              ...userDocument("MANAGER", replacementManager),
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentFiles: {
                healthCertificate: {
                  fullPath: legacyPath,
                  contentType: "image/png",
                },
              },
              managerDocumentFilePaths: {healthCertificate: legacyPath},
            };
            if (withAlias) {
              data.managerHealthCertificateStoragePath = legacyPath;
            }
            await setDoc(
                doc(context.firestore(), "users", replacementManager),
                data,
            );
          });
          const replacement = managerDocumentSubmissionFiles({
            managerId: replacementManager,
            documentKey,
          });
          await assertSucceeds(updateDoc(
              doc(
                  firestoreFor(testEnv, replacementManager),
                  "users",
                  replacementManager,
              ),
              {
                ...replacement,
                managerHealthCertificateStoragePath: deleteField(),
                managerDocumentSummary: "자격 증빙 교체",
                managerDocumentStatus: "PENDING_REVIEW",
                managerDocumentUpdatedAt: serverTimestamp(),
              },
          ));
        }
        for (const [holdManager, holdFields, shouldSucceed] of [
          [
            "legacy-health-active-hold",
            {
              managerDocumentLegalHoldUntil: new Date("2100-01-01T00:00:00.000Z"),
              managerDocumentLegalHoldReason: "분쟁 보존",
              managerDocumentLegalHoldByAdminUserId: users.admin,
            },
            false,
          ],
          [
            "legacy-health-invalid-hold",
            {managerDocumentLegalHoldReason: "불완전 보존 설정"},
            false,
          ],
          [
            "legacy-health-invalid-hold-type",
            {
              managerDocumentLegalHoldUntil: new Date("2000-01-01T00:00:00.000Z"),
              managerDocumentLegalHoldReason: 123,
              managerDocumentLegalHoldByAdminUserId: users.admin,
            },
            false,
          ],
          [
            "legacy-health-empty-hold-reason",
            {
              managerDocumentLegalHoldUntil: new Date("2000-01-01T00:00:00.000Z"),
              managerDocumentLegalHoldReason: "",
              managerDocumentLegalHoldByAdminUserId: users.admin,
            },
            false,
          ],
          [
            "legacy-health-blank-hold-admin",
            {
              managerDocumentLegalHoldUntil: new Date("2000-01-01T00:00:00.000Z"),
              managerDocumentLegalHoldReason: "만료된 분쟁 보존",
              managerDocumentLegalHoldByAdminUserId: "   ",
            },
            false,
          ],
          [
            "legacy-health-expired-hold",
            {
              managerDocumentLegalHoldUntil: new Date("2000-01-01T00:00:00.000Z"),
              managerDocumentLegalHoldReason: "만료된 분쟁 보존",
              managerDocumentLegalHoldByAdminUserId: users.admin,
            },
            true,
          ],
        ]) {
          const legacyPath =
            `manager-documents/${holdManager}/healthCertificate/legacy.png`;
          await testEnv.withSecurityRulesDisabled(async (context) => {
            await setDoc(doc(context.firestore(), "users", holdManager), {
              ...userDocument("MANAGER", holdManager),
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentFiles: {
                healthCertificate: {
                  fullPath: legacyPath,
                  contentType: "image/png",
                },
              },
              managerDocumentFilePaths: {healthCertificate: legacyPath},
              ...holdFields,
            });
          });
          const operation = updateDoc(
              doc(firestoreFor(testEnv, holdManager), "users", holdManager),
              {
                ...managerDocumentSubmissionFiles({
                  managerId: holdManager,
                  documentKey: "nursingLicense",
                }),
                managerDocumentSummary: "보존 상태 교체",
                managerDocumentStatus: "PENDING_REVIEW",
                managerDocumentUpdatedAt: serverTimestamp(),
              },
          );
          if (shouldSucceed) {
            await assertSucceeds(operation);
          } else {
            await assertFails(operation);
          }
        }
        const partialRemovalManager = "legacy-health-partial";
        const partialLegacyPath =
          `manager-documents/${partialRemovalManager}/healthCertificate/legacy.png`;
        await testEnv.withSecurityRulesDisabled(async (context) => {
          await setDoc(doc(context.firestore(), "users", partialRemovalManager), {
            ...userDocument("MANAGER", partialRemovalManager),
            managerDocumentStatus: "NOT_SUBMITTED",
            managerDocumentFiles: {
              healthCertificate: {
                fullPath: partialLegacyPath,
                contentType: "image/png",
              },
            },
            managerDocumentFilePaths: {healthCertificate: partialLegacyPath},
          });
        });
        const partialReplacement = managerDocumentSubmissionFiles({
          managerId: partialRemovalManager,
          documentKey: "nursingLicense",
        });
        partialReplacement.managerDocumentFiles.healthCertificate = {
          fullPath: partialLegacyPath,
          contentType: "image/png",
        };
        await assertFails(updateDoc(
            doc(
                firestoreFor(testEnv, partialRemovalManager),
                "users",
                partialRemovalManager,
            ),
            {
              ...partialReplacement,
              managerDocumentSummary: "부분 제거 시도",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        const mismatchedHealthManager = "legacy-health-mismatch";
        await testEnv.withSecurityRulesDisabled(async (context) => {
          await setDoc(doc(context.firestore(), "users", mismatchedHealthManager), {
            ...userDocument("MANAGER", mismatchedHealthManager),
            managerDocumentStatus: "NOT_SUBMITTED",
            managerDocumentFiles: {
              healthCertificate: {
                fullPath:
                  `manager-documents/${mismatchedHealthManager}/healthCertificate/a.png`,
                contentType: "image/png",
              },
            },
            managerDocumentFilePaths: {
              healthCertificate:
                `manager-documents/${mismatchedHealthManager}/healthCertificate/b.png`,
            },
          });
        });
        await assertFails(updateDoc(
            doc(
                firestoreFor(testEnv, mismatchedHealthManager),
                "users",
                mismatchedHealthManager,
            ),
            {
              ...managerDocumentSubmissionFiles({
                managerId: mismatchedHealthManager,
                documentKey: "nursingLicense",
              }),
              managerDocumentSummary: "불일치 원본 교체",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
        await testEnv.withSecurityRulesDisabled(async (context) => {
          await updateDoc(doc(context.firestore(), "users", users.manager), {
            managerDocumentStatus: "NOT_SUBMITTED",
            managerDocumentSummary: "",
            managerDocumentFiles: {
              healthCertificate: {
                fullPath:
                  `manager-documents/${users.manager}/healthCertificate/legacy.jpg`,
                contentType: "image/jpeg",
              },
            },
            managerDocumentFilePaths: {
              healthCertificate:
                `manager-documents/${users.manager}/healthCertificate/legacy.jpg`,
            },
            managerLicenseStoragePath: deleteField(),
          });
        });
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            {
              managerDocumentSummary: "기존 오염 초안 제출",
              managerDocumentStatus: "PENDING_REVIEW",
              managerDocumentUpdatedAt: serverTimestamp(),
            },
        ));
      },
    },
    {
      name: "매니저 서류 삭제 claim은 클라이언트 재참조와 상태 변경 및 업로드를 잠근다",
      run: async () => {
        await seedFirestore(testEnv);

        const forgedManager = "forged-deletion-claim-manager";
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, forgedManager), "users", forgedManager),
            {
              ...userDocument("MANAGER", "forged-deletion-claim-manager"),
              managerDocumentStatus: "NOT_SUBMITTED",
              managerDocumentDeletionClaim: {
                version: 1,
                claimId: "f".repeat(64),
                operation: "REPLACEMENT",
                documentKey: "license",
                storagePath:
                  `manager-documents/${forgedManager}/license/forged.png`,
                state: "CLAIMED",
                claimedAt: "2026-08-31T00:00:00.000Z",
              },
            },
        ));

        const claimedPath =
          `manager-documents/${users.manager}/license/replaced.png`;
        const currentPath =
          `manager-documents/${users.manager}/nursingLicense/current.png`;
        await testEnv.withSecurityRulesDisabled(async (context) => {
          await updateDoc(doc(context.firestore(), "users", users.manager), {
            ...managerDocumentSubmissionFiles({
              documentKey: "nursingLicense",
              fullPath: currentPath,
            }),
            managerDocumentSummary: "현재 제출 자료",
            managerDocumentStatus: "NOT_SUBMITTED",
            managerDocumentUpdatedAt: 1,
            managerDocumentDeletionClaim: {
              version: 1,
              claimId: "a".repeat(64),
              operation: "REPLACEMENT",
              documentKey: "license",
              storagePath: claimedPath,
              state: "READY",
              claimedAt: "2026-08-31T00:00:00.000Z",
              objectGeneration: "7",
            },
          });
        });

        const managerReference =
          doc(firestoreFor(testEnv, users.manager), "users", users.manager);
        await assertFails(updateDoc(managerReference, {
          ...managerDocumentSubmissionFiles({fullPath: claimedPath}),
          managerDocumentSummary: "삭제 claim 경로 재참조",
          managerDocumentStatus: "PENDING_REVIEW",
          managerDocumentUpdatedAt: serverTimestamp(),
        }));
        await assertFails(updateDoc(managerReference, {
          managerDocumentStatus: "PENDING_REVIEW",
          managerDocumentUpdatedAt: serverTimestamp(),
        }));
        await assertFails(updateDoc(managerReference, {
          managerDocumentLegalHoldUntil: new Date("2100-01-01T00:00:00.000Z"),
          managerDocumentLegalHoldReason: "클라이언트 보존 설정",
          managerDocumentLegalHoldByAdminUserId: users.manager,
        }));
        await assertFails(updateDoc(managerReference, {
          "managerDocumentDeletionClaim.claimId": "b".repeat(64),
        }));
        await assertFails(updateDoc(managerReference, {
          managerDocumentDeletionClaim: deleteField(),
        }));
        await assertSucceeds(updateDoc(managerReference, {
          name: "claim 중 일반 프로필 수정",
        }));

        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.admin), "users", users.manager),
            {
              managerDocumentDeletionClaim: deleteField(),
              managerDocumentLegalHoldUntil:
                new Date("2100-01-01T00:00:00.000Z"),
              "managerDocumentFilePaths.license": claimedPath,
            },
        ));
        await assertFails(uploadBytes(
            ref(
                storageFor(testEnv, users.manager),
                `manager-documents/${users.manager}/license/claim-blocked.png`,
            ),
            new Uint8Array([1, 2, 3]),
            {contentType: "image/png"},
        ));
      },
    },
    {
      name: "appointmentRequests 비교 문서는 환자만 직접 읽고 관리자 브라우저 접근도 거부한다",
      run: async () => {
        await seedFirestore(testEnv);

        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.patient), "appointmentRequests", "request-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.admin), "appointmentRequests", "request-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.guardian), "appointmentRequests", "request-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.manager), "appointmentRequests", "request-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.outsider), "appointmentRequests", "request-main")));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.patient), "appointmentRequests", "request-created-by-patient"),
            appointmentRequestDocument({ status: "REQUESTED", managerUserId: null }),
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.admin), "appointmentRequests", "request-created-by-admin"),
            appointmentRequestDocument({ status: "REQUESTED", managerUserId: null }),
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.patient), "appointmentRequests", "request-main"),
            { status: "CANCELED", updatedAt: 2 },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.admin), "appointmentRequests", "request-main"),
            { managerUserId: users.otherManager, updatedAt: 3 },
        ));
        await assertFails(deleteDoc(
            doc(firestoreFor(testEnv, users.admin), "appointmentRequests", "request-main"),
        ));
      },
    },
    {
      name: "companionSessions 비교 문서는 환자만 직접 읽고 관리자 브라우저 접근도 거부한다",
      run: async () => {
        await seedFirestore(testEnv);

        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.patient), "companionSessions", "session-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.admin), "companionSessions", "session-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.manager), "companionSessions", "session-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.guardian), "companionSessions", "session-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.outsider), "companionSessions", "session-main")));
        await assertFails(getDocs(query(
            collection(firestoreFor(testEnv, users.patient), "companionSessions"),
            where("appointmentRequestId", "==", "request-main"),
        )));
        await assertSucceeds(getDocs(query(
            collection(firestoreFor(testEnv, users.patient), "companionSessions"),
            where("appointmentRequestId", "==", "request-main"),
            where("patientUserId", "==", users.patient),
        )));
        await assertFails(getDocs(query(
            collection(firestoreFor(testEnv, users.guardian), "companionSessions"),
            where("appointmentRequestId", "==", "request-main"),
            where("guardianUserId", "==", users.guardian),
        )));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.manager), "companionSessions", "session-created-by-manager"),
            companionSessionDocument(),
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.admin), "companionSessions", "session-created-by-admin"),
            companionSessionDocument(),
        ));

        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "companionSessions", "session-main"),
            { currentStatus: "IN_PROGRESS", updatedAt: 2 },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.admin), "companionSessions", "session-main"),
            { currentStatus: "IN_PROGRESS", updatedAt: 2 },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "companionSessions", "session-main"),
            {
              locationSummary: "병원 이동 중",
              sharedLatitude: 37.5665,
              sharedLongitude: 126.978,
              sharedLocationUpdatedAt: 2,
              sharedLocationHistory: [{ latitude: 37.5665, longitude: 126.978, capturedAt: 2 }],
              updatedAt: 2,
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.patient), "companionSessions", "session-main"),
            {
              chatMessages: [{ senderRole: "PATIENT", message: "확인했습니다." }],
              patientChatReadAt: 2,
              updatedAt: 2,
            },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.guardian), "companionSessions", "session-main"),
            { medicationNote: "허용되지 않는 수정", updatedAt: 3 },
        ));
        await assertFails(deleteDoc(
            doc(firestoreFor(testEnv, users.admin), "companionSessions", "session-main"),
        ));
      },
    },
    {
      name: "리포트와 후속 처리는 읽기만 허용하고 클라이언트 쓰기를 차단한다",
      run: async () => {
        await seedFirestore(testEnv);

        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.patient), "sessionReports", "report-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.admin), "sessionReports", "report-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.manager), "sessionReports", "report-main")));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.guardian), "sessionReports", "report-main")));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.manager), "sessionReports", "report-created-by-manager"),
            { sessionId: "session-main", summary: "매니저 작성", createdAt: 2 },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.admin), "sessionReports", "report-created-by-admin"),
            { sessionId: "session-main", summary: "관리자 작성", createdAt: 2 },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.admin), "sessionReports", "report-main"),
            { summary: "관리자 수정" },
        ));
        await assertSucceeds(getDoc(
            doc(firestoreFor(testEnv, users.patient), "appointmentFollowUps", "request-main"),
        ));
        await assertFails(getDoc(
            doc(firestoreFor(testEnv, users.admin), "appointmentFollowUps", "request-main"),
        ));
        await assertFails(getDoc(
            doc(firestoreFor(testEnv, users.manager), "appointmentFollowUps", "request-main"),
        ));
        await assertFails(getDoc(
            doc(firestoreFor(testEnv, users.guardian), "appointmentFollowUps", "request-main"),
        ));
        await assertFails(getDoc(
            doc(firestoreFor(testEnv, users.outsider), "appointmentFollowUps", "request-main"),
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.patient), "appointmentFollowUps", "request-main"),
            { reviewRatingCode: "VERY_SATISFIED", updatedAt: 2 },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.admin), "appointmentFollowUps", "request-created-by-admin"),
            { requestId: "request-main", reviewRatingCode: "SATISFIED", updatedAt: 2 },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.admin), "adminSettlementRecords", "request-main"),
            { status: "PENDING", createdAt: 1 },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.manager), "adminSettlementRecords", "request-main"),
            { status: "PENDING", createdAt: 1 },
        ));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.admin), "appointmentReminderJobs", "job-main")));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.admin), "appointmentReminderJobs", "job-created-by-admin"),
            { state: "PENDING" },
        ));
      },
    },
    {
      name: "관리자 전용 컬렉션은 브라우저에서 역할과 무관하게 접근할 수 없다",
      run: async () => {
        await seedFirestore(testEnv);

        for (const collectionName of adminOnlyCollections) {
          const adminDb = firestoreFor(testEnv, users.admin);
          const managerDb = firestoreFor(testEnv, users.manager);
          const documentId = `rules-test-${collectionName}`;
          const adminReference = doc(adminDb, collectionName, documentId);
          const managerReference = doc(managerDb, collectionName, documentId);

          await assertFails(setDoc(adminReference, {
            createdAt: 1,
            status: "PENDING",
          }));
          await assertFails(getDoc(adminReference));
          await assertFails(getDoc(managerReference));
          await assertFails(setDoc(managerReference, {
            createdAt: 2,
            status: "PENDING",
          }));
          await assertFails(updateDoc(adminReference, {
            status: "DONE",
          }));
          await assertFails(updateDoc(managerReference, {
            status: "DONE",
          }));
          await assertFails(deleteDoc(adminReference));
          await assertFails(deleteDoc(managerReference));
        }
      },
    },
    {
      name: "manager-documents Storage 경로는 매니저 본인만 읽고 브라우저 ADMIN 접근을 차단한다",
      run: async () => {
        await seedFirestore(testEnv);
        await seedStorage(testEnv);

        await assertSucceeds(getBytes(ref(
            storageFor(testEnv, users.manager),
            "manager-documents/manager-user/idCard/seed.pdf",
        )));
        await assertFails(getBytes(ref(
            storageFor(testEnv, users.admin),
            "manager-documents/manager-user/idCard/seed.pdf",
        )));
        await assertFails(getBytes(ref(
            storageFor(testEnv, users.patient),
            "manager-documents/manager-user/idCard/seed.pdf",
        )));
        await assertSucceeds(uploadBytes(
            ref(storageFor(testEnv, users.manager), "manager-documents/manager-user/license/1760000000000-upload.jpg"),
            new Uint8Array([4, 5, 6]),
            { contentType: "image/jpeg" },
        ));
        await assertSucceeds(uploadBytes(
            ref(storageFor(testEnv, users.manager), "manager-documents/manager-user/nursingLicense/1760000000000-upload.webp"),
            new Uint8Array([4, 5, 6]),
            { contentType: "image/webp" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.manager), "manager-documents/manager-user/license/1760000000001-upload.pdf"),
            new Uint8Array([4, 5, 6]),
            { contentType: "application/pdf" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.manager), "manager-documents/manager-user/license/too-large.png"),
            new Uint8Array(10 * 1024 * 1024 + 1),
            { contentType: "image/png" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.manager), "manager-documents/manager-user/idCard/seed.pdf"),
            new Uint8Array([7, 8, 9]),
            { contentType: "image/jpeg" },
        ));
        await assertFails(deleteObject(ref(
            storageFor(testEnv, users.manager),
            "manager-documents/manager-user/idCard/seed.pdf",
        )));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.otherManager), "manager-documents/manager-user/license/other.jpg"),
            new Uint8Array([4, 5, 6]),
            { contentType: "image/jpeg" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.manager), "manager-documents/manager-user/bankbook/upload.jpg"),
            new Uint8Array([4, 5, 6]),
            { contentType: "image/jpeg" },
        ));
        for (const legacyKey of ["idCard", "healthCertificate", "criminalRecord"]) {
          await assertFails(uploadBytes(
              ref(
                  storageFor(testEnv, users.manager),
                  `manager-documents/manager-user/${legacyKey}/new-upload.jpg`,
              ),
              new Uint8Array([4, 5, 6]),
              {contentType: "image/jpeg"},
          ));
        }
      },
    },
    {
      name: "companion-chat-attachments는 환자·관리자 읽기만 남기고 클라이언트 쓰기를 거부한다",
      run: async () => {
        await seedFirestore(testEnv);
        await seedStorage(testEnv);

        await assertSucceeds(getBytes(ref(
            storageFor(testEnv, users.patient),
            "companion-chat-attachments/session-main/seed.png",
        )));
        await assertFails(getBytes(ref(
            storageFor(testEnv, users.admin),
            "companion-chat-attachments/session-main/seed.png",
        )));
        await assertFails(getBytes(ref(
            storageFor(testEnv, users.manager),
            "companion-chat-attachments/session-main/seed.png",
        )));
        await assertFails(getBytes(ref(
            storageFor(testEnv, users.outsider),
            "companion-chat-attachments/session-main/seed.png",
        )));
        await assertFails(getBytes(ref(
            storageFor(testEnv, users.guardian),
            "companion-chat-attachments/session-main/seed.png",
        )));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.guardian), "companion-chat-attachments/session-main/guardian.png"),
            new Uint8Array([7, 8, 9]),
            { contentType: "image/png" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.outsider), "companion-chat-attachments/session-main/outsider.png"),
            new Uint8Array([7, 8, 9]),
            { contentType: "image/png" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.patient), "companion-chat-attachments/session-main/patient.png"),
            new Uint8Array([7, 8, 9]),
            { contentType: "image/png" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.manager), "companion-chat-attachments/session-main/manager.png"),
            new Uint8Array([7, 8, 9]),
            { contentType: "image/png" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.admin), "companion-chat-attachments/session-main/admin.png"),
            new Uint8Array([7, 8, 9]),
            { contentType: "image/png" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.patient), "companion-chat-attachments/session-main/bad.txt"),
            new Uint8Array([7, 8, 9]),
            { contentType: "text/plain" },
        ));
        await assertFails(uploadBytes(
            ref(
                storageFor(testEnv, users.patient),
                "companion-chat-attachments/session-core-only/direct.png",
            ),
            new Uint8Array([7, 8, 9]),
            { contentType: "image/png" },
        ));
      },
    },
  ];
}

async function runTests() {
  const firestoreRulesPath = path.join(repoRoot, "firestore.rules");
  const storageRulesPath = path.join(repoRoot, "storage.rules");
  const testEnv = await initializeTestEnvironment({
    projectId,
    firestore: {
      rules: fs.readFileSync(firestoreRulesPath, "utf8"),
    },
    storage: {
      rules: fs.readFileSync(storageRulesPath, "utf8"),
    },
  });

  const filter = String(process.env.RULES_TEST_FILTER || "").trim();
  const cases = testCases(testEnv).filter((testCase) =>
    !filter || testCase.name.includes(filter),
  );
  let failedCount = 0;

  try {
    for (const testCase of cases) {
      try {
        await testCase.run();
        console.log(`PASS ${testCase.name}`);
      } catch (error) {
        failedCount += 1;
        console.error(`FAIL ${testCase.name}`);
        console.error(error && error.stack ? error.stack : error);
      }
    }
  } finally {
    await testEnv.cleanup();
  }

  if (failedCount > 0) {
    console.error(`Rules emulator 테스트 실패: ${failedCount}/${cases.length}`);
    process.exit(1);
  }

  console.log(`Rules emulator 테스트 통과: ${cases.length}/${cases.length}`);
}

if (!insideEmulator && (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_STORAGE_EMULATOR_HOST)) {
  runInsideEmulator();
} else {
  runTests().catch((error) => {
    console.error(error && error.stack ? error.stack : error);
    process.exit(1);
  });
}
