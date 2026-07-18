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
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  setLogLevel,
  updateDoc,
  where,
} = require("firebase/firestore");
const {
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
      name: "users 문서는 본인/관리자만 읽고 사용자가 ADMIN 역할로 가입할 수 없다",
      run: async () => {
        await seedFirestore(testEnv);

        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.patient), "users", users.patient)));
        await assertFails(getDoc(doc(firestoreFor(testEnv, users.patient), "users", users.guardian)));
        await assertSucceeds(getDocs(collection(firestoreFor(testEnv, users.admin), "users")));
        await assertFails(getDocs(collection(firestoreFor(testEnv, users.manager), "users")));
        await assertSucceeds(setDoc(
            doc(firestoreFor(testEnv, "new-patient-user"), "users", "new-patient-user"),
            userDocument("PATIENT", "new-patient"),
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, "new-admin-user"), "users", "new-admin-user"),
            userDocument("ADMIN", "new-admin"),
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
        await assertSucceeds(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentStatus: "PENDING" },
        ));
        await assertFails(updateDoc(
            doc(firestoreFor(testEnv, users.manager), "users", users.manager),
            { managerDocumentLegalHoldUntil: 4_000_000_000_000 },
        ));
        await assertSucceeds(updateDoc(
            doc(firestoreFor(testEnv, users.admin), "users", users.manager),
            { managerDocumentLegalHoldUntil: 4_000_000_000_000 },
        ));
      },
    },
    {
      name: "appointmentRequests는 참여자만 읽고 모든 클라이언트 쓰기를 거부한다",
      run: async () => {
        await seedFirestore(testEnv);

        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.patient), "appointmentRequests", "request-main")));
        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.guardian), "appointmentRequests", "request-main")));
        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.manager), "appointmentRequests", "request-main")));
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
      name: "companionSessions는 참여자만 읽고 모든 클라이언트 쓰기를 차단한다",
      run: async () => {
        await seedFirestore(testEnv);

        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.manager), "companionSessions", "session-main")));
        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.patient), "companionSessions", "session-main")));
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
        await assertSucceeds(getDocs(query(
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
        await assertSucceeds(setDoc(
            doc(firestoreFor(testEnv, users.admin), "adminSettlementRecords", "request-main"),
            { status: "PENDING", createdAt: 1 },
        ));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.manager), "adminSettlementRecords", "request-main"),
            { status: "PENDING", createdAt: 1 },
        ));
        await assertSucceeds(getDoc(doc(firestoreFor(testEnv, users.admin), "appointmentReminderJobs", "job-main")));
        await assertFails(setDoc(
            doc(firestoreFor(testEnv, users.admin), "appointmentReminderJobs", "job-created-by-admin"),
            { state: "PENDING" },
        ));
      },
    },
    {
      name: "관리자 전용 컬렉션은 관리자만 읽고 쓸 수 있다",
      run: async () => {
        await seedFirestore(testEnv);

        for (const collectionName of adminOnlyCollections) {
          const adminDb = firestoreFor(testEnv, users.admin);
          const managerDb = firestoreFor(testEnv, users.manager);
          const documentId = `rules-test-${collectionName}`;
          const adminReference = doc(adminDb, collectionName, documentId);
          const managerReference = doc(managerDb, collectionName, documentId);

          await assertSucceeds(setDoc(adminReference, {
            createdAt: 1,
            status: "PENDING",
          }));
          await assertSucceeds(getDoc(adminReference));
          await assertFails(getDoc(managerReference));
          await assertFails(setDoc(managerReference, {
            createdAt: 2,
            status: "PENDING",
          }));
          await assertSucceeds(updateDoc(adminReference, {
            status: "DONE",
          }));
          await assertFails(updateDoc(managerReference, {
            status: "DONE",
          }));
          await assertSucceeds(deleteDoc(adminReference));
          await assertFails(deleteDoc(managerReference));
        }
      },
    },
    {
      name: "manager-documents Storage 경로는 매니저 본인과 관리자 읽기만 허용한다",
      run: async () => {
        await seedFirestore(testEnv);
        await seedStorage(testEnv);

        await assertSucceeds(getBytes(ref(
            storageFor(testEnv, users.manager),
            "manager-documents/manager-user/idCard/seed.pdf",
        )));
        await assertSucceeds(getBytes(ref(
            storageFor(testEnv, users.admin),
            "manager-documents/manager-user/idCard/seed.pdf",
        )));
        await assertFails(getBytes(ref(
            storageFor(testEnv, users.patient),
            "manager-documents/manager-user/idCard/seed.pdf",
        )));
        await assertSucceeds(uploadBytes(
            ref(storageFor(testEnv, users.manager), "manager-documents/manager-user/license/upload.pdf"),
            new Uint8Array([4, 5, 6]),
            { contentType: "application/pdf" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.otherManager), "manager-documents/manager-user/license/other.pdf"),
            new Uint8Array([4, 5, 6]),
            { contentType: "application/pdf" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.manager), "manager-documents/manager-user/bankbook/upload.pdf"),
            new Uint8Array([4, 5, 6]),
            { contentType: "application/pdf" },
        ));
        await assertFails(uploadBytes(
            ref(storageFor(testEnv, users.manager), "manager-documents/manager-user/idCard/upload.exe"),
            new Uint8Array([4, 5, 6]),
            { contentType: "application/x-msdownload" },
        ));
      },
    },
    {
      name: "companion-chat-attachments Storage 경로는 세션 참여자만 읽고 쓸 수 있다",
      run: async () => {
        await seedFirestore(testEnv);
        await seedStorage(testEnv);

        await assertSucceeds(getBytes(ref(
            storageFor(testEnv, users.patient),
            "companion-chat-attachments/session-main/seed.png",
        )));
        await assertSucceeds(getBytes(ref(
            storageFor(testEnv, users.admin),
            "companion-chat-attachments/session-main/seed.png",
        )));
        await assertFails(getBytes(ref(
            storageFor(testEnv, users.outsider),
            "companion-chat-attachments/session-main/seed.png",
        )));
        await assertSucceeds(uploadBytes(
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
            ref(storageFor(testEnv, users.patient), "companion-chat-attachments/session-main/bad.txt"),
            new Uint8Array([7, 8, 9]),
            { contentType: "text/plain" },
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

  const cases = testCases(testEnv);
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
