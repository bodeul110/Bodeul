const assert = require("node:assert/strict");
const test = require("node:test");

const {
  FirebaseLegacyCompanionStore,
  FirebaseStorageGateway,
  PostgresRetentionRepository,
  evaluateManagerDocument,
  evaluateLegacyCompanionSession,
  isAllowedChatAttachmentPath,
  isAllowedManagerDocumentPath,
  previousMonthStart,
  postgresConnectionOptions,
  retentionApplyEnabled,
  retentionCounts,
  runRetentionJob,
} = require("../src/retention");

test("PostgreSQL 연결은 Supabase CA 검증을 강제한다", () => {
  const ca = "-----BEGIN CERTIFICATE-----\nfixture\n-----END CERTIFICATE-----";
  const options = postgresConnectionOptions(ca);

  assert.equal(options.ssl.ca, ca);
  assert.equal(options.ssl.rejectUnauthorized, true);
  assert.equal(options.prepare, false);
  assert.equal(options.max, 1);
});

test("Supabase CA가 없거나 PEM 형식이 아니면 연결 구성을 거부한다", () => {
  assert.throws(
      () => postgresConnectionOptions(""),
      (error) => error.code === "DATABASE_CA_CONFIG_INVALID",
  );
  assert.throws(
      () => postgresConnectionOptions("not-a-certificate"),
      (error) => error.code === "DATABASE_CA_CONFIG_INVALID",
  );
});

test("DB 집계 payload는 계약 키만 남기고 안전한 정수로 정규화한다", () => {
  const counts = retentionCounts({
    mode: "DRY_RUN",
    asOf: "2026-07-19T00:00:00.000Z",
    postgresMessageCandidates: "3",
    adminAuditCandidates: "5",
    adminAuditsDeleted: "2",
    attachmentsDeleted: -1,
    unexpected: 100,
  });

  assert.equal(counts.postgresMessageCandidates, 3);
  assert.equal(counts.attachmentsDeleted, 0);
  assert.equal(counts.managerDocumentDeleteFailures, 0);
  assert.equal(counts.adminAuditCandidates, 5);
  assert.equal(counts.adminAuditsDeleted, 2);
  assert.equal(Object.hasOwn(counts, "mode"), false);
  assert.equal(Object.hasOwn(counts, "unexpected"), false);
  assert.equal(Object.keys(counts).length, 22);
});

test("월간 보고는 관리자 감사 후보·삭제 집계를 함께 반환한다", async () => {
  const repository = Object.create(PostgresRetentionRepository.prototype);
  repository.sql = async () => [{
    run_count: "2",
    failed_run_count: "1",
    admin_audit_candidates: "15",
    admin_audits_deleted: "12",
  }];

  const summary = await repository.monthlySummary(new Date("2026-07-01T00:00:00.000Z"));

  assert.equal(summary.month, "2026-07");
  assert.equal(summary.runCount, 2);
  assert.equal(summary.failedRunCount, 1);
  assert.equal(summary.adminAuditCandidates, 15);
  assert.equal(summary.adminAuditsDeleted, 12);
});

test("정기 파기는 true를 명시한 환경에서만 활성화한다", () => {
  assert.equal(retentionApplyEnabled("true"), true);
  assert.equal(retentionApplyEnabled(" TRUE "), true);
  assert.equal(retentionApplyEnabled("false"), false);
  assert.equal(retentionApplyEnabled("1"), false);
  assert.equal(retentionApplyEnabled(undefined), false);
});

function createDatabase(overrides = {}) {
  const calls = [];
  return {
    calls,
    async beginJob() {
      calls.push("begin");
      return "5a246e0e-e543-4fd4-936e-58e07b478247";
    },
    async preview() {
      calls.push("preview");
      return {
        messageCandidates: 2,
        attachmentCandidates: 1,
        locationCandidates: 3,
        legalHoldSkips: 4,
        adminAuditCandidates: 5,
      };
    },
    async claimAttachments() {
      calls.push("claimAttachments");
      return [];
    },
    async markAttachmentDeleted() {
      calls.push("markAttachmentDeleted");
      return true;
    },
    async purgeCompanionRecords() {
      calls.push("purgeCompanionRecords");
      return {messagesRedacted: 2, locationsDeleted: 3};
    },
    async purgeAdminAudits() {
      calls.push("purgeAdminAudits");
      return 5;
    },
    async finishJob() {
      calls.push("finish");
      return true;
    },
    ...overrides,
  };
}

function createManagerStore(overrides = {}) {
  const calls = [];
  return {
    calls,
    async preview() {
      calls.push("preview");
      return {candidates: [], legalHoldSkips: 0};
    },
    async isStillEligible() {
      calls.push("isStillEligible");
      return true;
    },
    async clearReference() {
      calls.push("clearReference");
      return true;
    },
    ...overrides,
  };
}

function createLegacyStore(overrides = {}) {
  const calls = [];
  return {
    calls,
    async preview() {
      calls.push("preview");
      return {
        sessions: [],
        messageCandidates: 0,
        attachmentCandidates: 0,
        locationCandidates: 0,
        legalHoldSkips: 0,
      };
    },
    async applySession() {
      calls.push("applySession");
      return {
        messagesRedacted: 0,
        attachmentsDeleted: 0,
        attachmentDeleteFailures: 0,
        locationsCleared: 0,
      };
    },
    ...overrides,
  };
}

test("dry-run은 후보 수만 기록하고 삭제 함수를 호출하지 않는다", async () => {
  const database = createDatabase();
  const legacyStore = createLegacyStore();
  const managerStore = createManagerStore();
  const storage = {
    async deleteChatAttachment() {
      assert.fail("dry-run에서 Storage를 삭제하면 안 됩니다.");
    },
    async deleteManagerDocument() {
      assert.fail("dry-run에서 Storage를 삭제하면 안 됩니다.");
    },
  };

  const summary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: false,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.equal(summary.mode, "DRY_RUN");
  assert.equal(summary.postgresMessageCandidates, 2);
  assert.equal(summary.postgresAttachmentCandidates, 1);
  assert.equal(summary.postgresLocationCandidates, 3);
  assert.equal(summary.postgresLegalHoldSkips, 4);
  assert.equal(summary.adminAuditCandidates, 5);
  assert.equal(summary.adminAuditsDeleted, 0);
  assert.deepEqual(database.calls, ["begin", "preview", "finish"]);
});

test("관리자 감사기록은 1년 경과 후보만 500건 단위로 끝까지 파기한다", async () => {
  const batches = [500, 2];
  const database = createDatabase({
    async preview() {
      return {
        messageCandidates: 0,
        attachmentCandidates: 0,
        locationCandidates: 0,
        legalHoldSkips: 0,
        adminAuditCandidates: 502,
      };
    },
    async purgeAdminAudits() {
      return batches.shift() ?? 0;
    },
  });

  const summary = await runRetentionJob({
    database,
    legacyStore: createLegacyStore(),
    managerStore: createManagerStore(),
    storage: {
      async deleteChatAttachment() {},
      async deleteManagerDocument() {},
    },
    apply: true,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.equal(summary.adminAuditCandidates, 502);
  assert.equal(summary.adminAuditsDeleted, 502);
  assert.equal(batches.length, 0);
});

test("PostgreSQL 첨부 일부 삭제 실패는 참조를 유지하고 다음 실행에서 재시도한다", async () => {
  const candidates = [
    {
      id: "50de4226-df48-4622-9de8-c292c3fc0ed9",
      storagePath: "companion-chat-attachments/728916a2-d57e-4e8f-bd99-c6c47498b4ba/retry.pdf",
    },
    {
      id: "04e413bf-8b76-4d1d-954e-1ca8ffb64c24",
      storagePath: "companion-chat-attachments/728916a2-d57e-4e8f-bd99-c6c47498b4ba/success.pdf",
    },
  ];
  const markedIds = new Set();
  const finishes = [];
  const database = createDatabase({
    async claimAttachments() {
      return candidates.filter((candidate) => !markedIds.has(candidate.id));
    },
    async markAttachmentDeleted(candidate) {
      markedIds.add(candidate.id);
      return true;
    },
    async finishJob(_jobId, status, _finishedAt, summary, failureStage) {
      finishes.push({
        status,
        attachmentsDeleted: summary.attachmentsDeleted,
        attachmentDeleteFailures: summary.attachmentDeleteFailures,
        failureStage: failureStage || null,
      });
      return true;
    },
  });
  const legacyStore = createLegacyStore();
  const managerStore = createManagerStore();
  const attempts = new Map();
  const storage = {
    async deleteChatAttachment(storagePath) {
      const attempt = (attempts.get(storagePath) || 0) + 1;
      attempts.set(storagePath, attempt);
      if (storagePath.endsWith("retry.pdf") && attempt === 1) {
        throw new Error("storage unavailable");
      }
    },
    async deleteManagerDocument() {},
  };

  const firstSummary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.equal(firstSummary.attachmentsDeleted, 1);
  assert.equal(firstSummary.attachmentDeleteFailures, 1);
  assert.deepEqual([...markedIds], [candidates[1].id]);

  const secondSummary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-19T00:00:00.000Z"),
  });

  assert.equal(secondSummary.attachmentsDeleted, 1);
  assert.equal(secondSummary.attachmentDeleteFailures, 0);
  assert.equal(markedIds.size, 2);
  assert.equal(attempts.get(candidates[0].storagePath), 2);
  assert.equal(attempts.get(candidates[1].storagePath), 1);
  assert.deepEqual(finishes, [
    {
      status: "COMPLETED",
      attachmentsDeleted: 1,
      attachmentDeleteFailures: 1,
      failureStage: null,
    },
    {
      status: "COMPLETED",
      attachmentsDeleted: 1,
      attachmentDeleteFailures: 0,
      failureStage: null,
    },
  ]);
});

test("관리자 증빙은 심사 후 30일이 지나고 법적 보존이 없을 때만 후보가 된다", () => {
  const now = new Date("2026-07-18T00:00:00.000Z");
  const baseData = {
    role: "MANAGER",
    managerDocumentStatus: "APPROVED",
    managerDocumentReviewedAt: Date.parse("2026-06-01T00:00:00.000Z"),
    managerDocumentUpdatedAt: Date.parse("2026-05-31T00:00:00.000Z"),
    managerDocumentFiles: {
      license: {
        fullPath: "manager-documents/manager-1/license/license.pdf",
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {
      license: "manager-documents/manager-1/license/license.pdf",
    },
    managerLicenseStoragePath: "manager-documents/manager-1/license/license.pdf",
  };

  const eligible = evaluateManagerDocument("manager-1", baseData, now);
  assert.equal(eligible.candidates.length, 1);
  assert.equal(eligible.legalHoldSkips, 0);

  const reviewedAt = Date.parse("2026-06-01T00:00:00.000Z");
  assert.equal(evaluateManagerDocument(
      "manager-1",
      baseData,
      new Date(reviewedAt + (30 * 24 * 60 * 60 * 1000) - 1),
  ).candidates.length, 0);
  assert.equal(evaluateManagerDocument(
      "manager-1",
      baseData,
      new Date(reviewedAt + (30 * 24 * 60 * 60 * 1000)),
  ).candidates.length, 1);

  const held = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentLegalHoldUntil: Date.parse("2026-08-01T00:00:00.000Z"),
    managerDocumentLegalHoldReason: "분쟁 대응",
    managerDocumentLegalHoldByAdminUserId: "admin-1",
  }, now);
  assert.equal(held.candidates.length, 0);
  assert.equal(held.legalHoldSkips, 1);

  const incompleteHold = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentLegalHoldReason: "분쟁 대응",
  }, now);
  assert.equal(incompleteHold.candidates.length, 0);
  assert.equal(incompleteHold.legalHoldSkips, 1);

  const invalidHold = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentLegalHoldUntil: "invalid",
  }, now);
  assert.equal(invalidHold.candidates.length, 0);
  assert.equal(invalidHold.legalHoldSkips, 1);

  const expiredHold = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentLegalHoldUntil: Date.parse("2026-07-17T23:59:59.999Z"),
    managerDocumentLegalHoldReason: "만료된 보존",
    managerDocumentLegalHoldByAdminUserId: "admin-1",
  }, now);
  assert.equal(expiredHold.candidates.length, 1);
  assert.equal(expiredHold.legalHoldSkips, 0);

  const otherManagerPath = "manager-documents/manager-2/license/license.pdf";
  const crossOwner = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      license: {...baseData.managerDocumentFiles.license, fullPath: otherManagerPath},
    },
    managerDocumentFilePaths: {license: otherManagerPath},
    managerLicenseStoragePath: otherManagerPath,
  }, now);
  assert.equal(crossOwner.candidates.length, 0);

  const whitespaceOwner = evaluateManagerDocument(" manager-1 ", baseData, now);
  assert.equal(whitespaceOwner.candidates.length, 0);

  const whitespacePath = " manager-documents/manager-1/license/license.pdf ";
  const whitespaceAliases = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      license: {...baseData.managerDocumentFiles.license, fullPath: whitespacePath},
    },
    managerDocumentFilePaths: {license: whitespacePath},
    managerLicenseStoragePath: whitespacePath,
  }, now);
  assert.equal(whitespaceAliases.candidates.length, 0);

  const wrongDocumentKeyPath = "manager-documents/manager-1/idCard/license.pdf";
  const crossDocumentKey = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      license: {
        ...baseData.managerDocumentFiles.license,
        fullPath: wrongDocumentKeyPath,
      },
    },
    managerDocumentFilePaths: {license: wrongDocumentKeyPath},
    managerLicenseStoragePath: wrongDocumentKeyPath,
  }, now);
  assert.equal(crossDocumentKey.candidates.length, 0);

  const mismatchedAliases = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFilePaths: {
      license: "manager-documents/manager-1/license/path-map-mismatch.pdf",
    },
  }, now);
  assert.equal(mismatchedAliases.candidates.length, 0);

  const missingLegacyAlias = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerLicenseStoragePath: "",
  }, now);
  assert.equal(missingLegacyAlias.candidates.length, 0);

  const missingPathMapAlias = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFilePaths: {},
  }, now);
  assert.equal(missingPathMapAlias.candidates.length, 0);

  const nursingLicensePath =
    "manager-documents/manager-1/nursingLicense/nursing-license.jpg";
  const nursingLicense = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      nursingLicense: {
        fullPath: nursingLicensePath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {nursingLicense: nursingLicensePath},
    managerLicenseStoragePath: "",
  }, now);
  assert.deepEqual(
      nursingLicense.candidates.map(({documentKey}) => documentKey),
      ["nursingLicense"],
  );

  const healthCertificatePath =
    "manager-documents/manager-1/healthCertificate/health.jpg";
  const healthCertificate = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      healthCertificate: {
        fullPath: healthCertificatePath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {healthCertificate: healthCertificatePath},
    managerLicenseStoragePath: "",
  }, now);
  assert.equal(healthCertificate.candidates.length, 1);

  const mismatchedHealthCertificateAlias = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      healthCertificate: {
        fullPath: healthCertificatePath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {healthCertificate: healthCertificatePath},
    managerHealthCertificateStoragePath:
      "manager-documents/manager-1/healthCertificate/mismatch.jpg",
    managerLicenseStoragePath: "",
  }, now);
  assert.equal(mismatchedHealthCertificateAlias.candidates.length, 0);

  const incompleteHealthCertificate = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      healthCertificate: {
        fullPath: healthCertificatePath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {},
    managerHealthCertificateStoragePath: healthCertificatePath,
    managerLicenseStoragePath: "",
  }, now);
  assert.equal(incompleteHealthCertificate.candidates.length, 0);

  const idCardPath = "manager-documents/manager-1/idCard/legacy-id.jpg";
  const idCard = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      idCard: {
        fullPath: idCardPath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {idCard: idCardPath},
    managerIdCardStoragePath: idCardPath,
    managerLicenseStoragePath: "",
  }, now);
  assert.deepEqual(idCard.candidates.map(({documentKey}) => documentKey), ["idCard"]);

  const criminalRecordPath =
    "manager-documents/manager-1/criminalRecord/legacy-record.jpg";
  const criminalRecord = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      criminalRecord: {
        fullPath: criminalRecordPath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {criminalRecord: criminalRecordPath},
    managerCriminalRecordStoragePath: criminalRecordPath,
    managerLicenseStoragePath: "",
  }, now);
  assert.deepEqual(
      criminalRecord.candidates.map(({documentKey}) => documentKey),
      ["criminalRecord"],
  );

  const unsupportedPath = "manager-documents/manager-1/passport/unsupported.jpg";
  const unsupported = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentFiles: {
      passport: {
        fullPath: unsupportedPath,
        uploadedAt: Date.parse("2026-05-31T00:00:00.000Z"),
      },
    },
    managerDocumentFilePaths: {passport: unsupportedPath},
    managerLicenseStoragePath: "",
  }, now);
  assert.equal(unsupported.candidates.length, 0);
});

test("관리자 증빙 Storage 삭제도 사용자와 문서 키를 다시 확인한다", async () => {
  const deletedPaths = [];
  const gateway = new FirebaseStorageGateway({
    file(storagePath) {
      return {
        async delete() {
          deletedPaths.push(storagePath);
        },
      };
    },
  });

  await assert.rejects(
      gateway.deleteManagerDocument(
          "manager-documents/manager-2/idCard/id.jpg",
          "manager-1",
          "idCard",
      ),
      (error) => error.code === "MANAGER_STORAGE_PATH_INVALID",
  );
  await assert.rejects(
      gateway.deleteManagerDocument(
          "manager-documents/manager-1/license/id.jpg",
          "manager-1",
          "idCard",
      ),
      (error) => error.code === "MANAGER_STORAGE_PATH_INVALID",
  );
  await gateway.deleteManagerDocument(
      "manager-documents/manager-1/idCard/id.jpg",
      "manager-1",
      "idCard",
  );
  await gateway.deleteManagerDocument(
      "manager-documents/manager-1/nursingLicense/license.jpg",
      "manager-1",
      "nursingLicense",
  );
  await gateway.deleteManagerDocument(
      "manager-documents/manager-1/healthCertificate/legacy.jpg",
      "manager-1",
      "healthCertificate",
  );
  await assert.rejects(
      gateway.deleteManagerDocument(
          "manager-documents/manager-1/passport/unsupported.jpg",
          "manager-1",
          "passport",
      ),
      (error) => error.code === "MANAGER_STORAGE_PATH_INVALID",
  );

  assert.deepEqual(deletedPaths, [
    "manager-documents/manager-1/idCard/id.jpg",
    "manager-documents/manager-1/nursingLicense/license.jpg",
    "manager-documents/manager-1/healthCertificate/legacy.jpg",
  ]);
});

test("관리자 증빙 삭제 실패는 참조를 유지하고 다음 실행에서 재시도한다", async () => {
  const candidate = {
    managerId: "manager-1",
    documentKey: "license",
    storagePath: "manager-documents/manager-1/license/license.pdf",
  };
  const order = [];
  const database = createDatabase();
  const legacyStore = createLegacyStore();
  let referenceExists = true;
  let deleteAttempt = 0;
  const managerStore = createManagerStore({
    async preview() {
      return {
        candidates: referenceExists ? [candidate] : [],
        legalHoldSkips: 0,
      };
    },
    async isStillEligible() {
      order.push("validate");
      return true;
    },
    async clearReference() {
      order.push("clear");
      referenceExists = false;
      return true;
    },
  });
  const storage = {
    async deleteChatAttachment() {},
    async deleteManagerDocument() {
      order.push("delete");
      deleteAttempt += 1;
      if (deleteAttempt === 1) {
        throw new Error("storage unavailable");
      }
    },
  };

  const firstSummary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.deepEqual(order, ["validate", "delete"]);
  assert.equal(referenceExists, true);
  assert.equal(firstSummary.managerDocumentsDeleted, 0);
  assert.equal(firstSummary.managerDocumentDeleteFailures, 1);

  const secondSummary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-19T00:00:00.000Z"),
  });

  assert.deepEqual(order, ["validate", "delete", "validate", "delete", "clear"]);
  assert.equal(referenceExists, false);
  assert.equal(secondSummary.managerDocumentsDeleted, 1);
  assert.equal(secondSummary.managerDocumentDeleteFailures, 0);
});

test("Firestore 전환 첨부 삭제 실패는 참조를 보존하고 다음 실행에서 제거한다", async () => {
  const successPath = "companion-chat-attachments/session-legacy/success.pdf";
  const retryPath = "companion-chat-attachments/session-legacy/retry.pdf";
  let sessionData = {
    currentStatus: "COMPLETED",
    completedAt: Date.parse("2026-06-01T00:00:00.000Z"),
    chatMessages: [{
      body: "민감한 대화",
      attachments: [
        {fullPath: successPath},
        {fullPath: retryPath},
      ],
    }],
  };
  const snapshot = () => ({
    exists: true,
    id: "session-legacy",
    data: () => structuredClone(sessionData),
  });
  const reference = {id: "session-legacy"};
  const firestore = {
    collection(name) {
      assert.equal(name, "companionSessions");
      return {
        doc(sessionId) {
          assert.equal(sessionId, "session-legacy");
          return {
            ...reference,
            async get() {
              return snapshot();
            },
          };
        },
      };
    },
    async runTransaction(callback) {
      return callback({
        async get() {
          return snapshot();
        },
        update(_reference, updates) {
          if (updates.chatMessages) {
            sessionData = {...sessionData, chatMessages: updates.chatMessages};
          }
        },
      });
    },
  };
  const legacyStore = new FirebaseLegacyCompanionStore(firestore);
  const deleteAttempts = new Map();
  const storage = {
    async deleteChatAttachment(candidatePath) {
      const attempt = (deleteAttempts.get(candidatePath) || 0) + 1;
      deleteAttempts.set(candidatePath, attempt);
      if (candidatePath === retryPath && attempt === 1) {
        throw new Error("storage unavailable");
      }
    },
  };
  const candidate = {sessionId: "session-legacy"};

  const firstResult = await legacyStore.applySession(
      candidate,
      new Date("2026-07-18T00:00:00.000Z"),
      storage,
  );

  assert.equal(firstResult.messagesRedacted, 0);
  assert.equal(firstResult.attachmentsDeleted, 1);
  assert.equal(firstResult.attachmentDeleteFailures, 1);
  assert.equal(sessionData.chatMessages[0].body, "민감한 대화");
  assert.deepEqual(sessionData.chatMessages[0].attachments, [{fullPath: retryPath}]);

  const secondResult = await legacyStore.applySession(
      candidate,
      new Date("2026-07-19T00:00:00.000Z"),
      storage,
  );

  assert.equal(secondResult.messagesRedacted, 0);
  assert.equal(secondResult.attachmentsDeleted, 1);
  assert.equal(secondResult.attachmentDeleteFailures, 0);
  assert.deepEqual(sessionData.chatMessages[0].attachments, []);
  assert.equal(deleteAttempts.get(successPath), 1);
  assert.equal(deleteAttempts.get(retryPath), 2);
});

test("부분 삭제 실패는 저장소별 성공과 실패 집계를 COMPLETED 작업에 분리 기록한다", async () => {
  const postgresAttachments = [
    {
      id: "df462d78-8a9d-4a75-8b35-fb09489a7f60",
      storagePath: "companion-chat-attachments/session-summary/postgres-success.pdf",
    },
    {
      id: "66ed98d5-f8da-4fab-9723-5f7587f5c7f7",
      storagePath: "companion-chat-attachments/session-summary/postgres-failure.pdf",
    },
  ];
  const managerCandidates = [
    {
      managerId: "manager-1",
      documentKey: "license",
      storagePath: "manager-documents/manager-1/license/success.pdf",
    },
    {
      managerId: "manager-2",
      documentKey: "license",
      storagePath: "manager-documents/manager-2/license/failure.pdf",
    },
  ];
  const finishes = [];
  const database = createDatabase({
    async claimAttachments() {
      return postgresAttachments;
    },
    async finishJob(_jobId, status, _finishedAt, summary, failureStage) {
      finishes.push({status, failureStage: failureStage || null, summary: {...summary}});
      return true;
    },
  });
  const legacyStore = createLegacyStore({
    async preview() {
      return {
        sessions: [{sessionId: "session-summary"}],
        messageCandidates: 1,
        attachmentCandidates: 3,
        locationCandidates: 1,
        legalHoldSkips: 0,
      };
    },
    async applySession() {
      return {
        messagesRedacted: 1,
        attachmentsDeleted: 2,
        attachmentDeleteFailures: 1,
        locationsCleared: 1,
      };
    },
  });
  const managerStore = createManagerStore({
    async preview() {
      return {candidates: managerCandidates, legalHoldSkips: 0};
    },
  });
  const storage = {
    async deleteChatAttachment(storagePath) {
      if (storagePath.endsWith("postgres-failure.pdf")) {
        throw new Error("postgres attachment unavailable");
      }
    },
    async deleteManagerDocument(storagePath) {
      if (storagePath.endsWith("failure.pdf")) {
        throw new Error("manager document unavailable");
      }
    },
  };

  const summary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.equal(summary.attachmentsDeleted, 1);
  assert.equal(summary.attachmentDeleteFailures, 1);
  assert.equal(summary.firestoreMessagesRedacted, 1);
  assert.equal(summary.firestoreAttachmentsDeleted, 2);
  assert.equal(summary.firestoreAttachmentDeleteFailures, 1);
  assert.equal(summary.firestoreLocationsCleared, 1);
  assert.equal(summary.managerDocumentsDeleted, 1);
  assert.equal(summary.managerDocumentDeleteFailures, 1);
  assert.equal(finishes.length, 1);
  assert.equal(finishes[0].status, "COMPLETED");
  assert.equal(finishes[0].failureStage, null);
  assert.equal(finishes[0].summary.attachmentsDeleted, 1);
  assert.equal(finishes[0].summary.attachmentDeleteFailures, 1);
  assert.equal(finishes[0].summary.firestoreMessagesRedacted, 1);
  assert.equal(finishes[0].summary.firestoreAttachmentsDeleted, 2);
  assert.equal(finishes[0].summary.firestoreAttachmentDeleteFailures, 1);
  assert.equal(finishes[0].summary.firestoreLocationsCleared, 1);
  assert.equal(finishes[0].summary.managerDocumentsDeleted, 1);
  assert.equal(finishes[0].summary.managerDocumentDeleteFailures, 1);
});

test("처리되지 않은 오류는 실패 단계와 함께 FAILED 작업으로 기록한다", async () => {
  const finishes = [];
  const database = createDatabase({
    async claimAttachments() {
      return [{
        id: "50de4226-df48-4622-9de8-c292c3fc0ed9",
        storagePath: "companion-chat-attachments/session-fatal/evidence.pdf",
      }];
    },
    async finishJob(_jobId, status, _finishedAt, summary, failureStage) {
      finishes.push({
        status,
        failureStage: failureStage || null,
        attachmentsDeleted: summary.attachmentsDeleted,
        messagesRedacted: summary.messagesRedacted,
        locationsDeleted: summary.locationsDeleted,
      });
      return true;
    },
  });
  const legacyStore = createLegacyStore({
    async preview() {
      return {
        sessions: [{sessionId: "session-fatal"}],
        messageCandidates: 1,
        attachmentCandidates: 1,
        locationCandidates: 1,
        legalHoldSkips: 0,
      };
    },
    async applySession() {
      throw new Error("firestore unavailable");
    },
  });

  await assert.rejects(
      runRetentionJob({
        database,
        legacyStore,
        managerStore: createManagerStore(),
        storage: {
          async deleteChatAttachment() {},
        },
        apply: true,
        now: new Date("2026-07-18T00:00:00.000Z"),
      }),
      (error) => error.retentionFailureStage === "PURGE_FIRESTORE",
  );

  assert.deepEqual(finishes, [{
    status: "FAILED",
    failureStage: "PURGE_FIRESTORE",
    attachmentsDeleted: 1,
    messagesRedacted: 2,
    locationsDeleted: 3,
  }]);
});

test("채팅 첨부 삭제 경로는 legacy와 Core API 구조만 허용한다", () => {
  assert.equal(isAllowedChatAttachmentPath(
      "companion-chat-attachments/728916a2-d57e-4e8f-bd99-c6c47498b4ba/a.pdf",
  ), true);
  assert.equal(isAllowedChatAttachmentPath(
      "companion-chat-attachments/728916a2-d57e-4e8f-bd99-c6c47498b4ba/"
      + "e402cf5d-8811-4dd5-83f5-58529251cc65/0-evidence.pdf",
  ), true);
  assert.equal(isAllowedChatAttachmentPath(
      "companion-chat-attachments/session/not-a-uuid/evidence.pdf",
  ), false);
  assert.equal(isAllowedChatAttachmentPath(
      "companion-chat-attachments/session/"
      + "e402cf5d-8811-4dd5-83f5-58529251cc65/nested/evidence.pdf",
  ), false);
  assert.equal(isAllowedChatAttachmentPath("manager-documents/user/idCard/a.pdf"), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
  ), true);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
      "manager-1",
      "idCard",
  ), true);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-2/idCard/a.pdf",
      "manager-1",
      "idCard",
  ), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/license/a.pdf",
      "manager-1",
      "idCard",
  ), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
      " manager-1 ",
      "idCard",
  ), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
      "manager-1",
      " idCard ",
  ), false);
  assert.equal(isAllowedManagerDocumentPath(
      " manager-documents/manager-1/idCard/a.pdf ",
      "manager-1",
      "idCard",
  ), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
      1,
      "idCard",
  ), false);
  assert.equal(isAllowedManagerDocumentPath("manager-documents/manager-1/other/a.pdf"), false);
});

test("Firestore 전환 데이터도 세션 종료 시각 기준 보관 기간을 적용한다", () => {
  const evaluation = evaluateLegacyCompanionSession("session-legacy", {
    currentStatus: "COMPLETED",
    updatedAt: Date.parse("2025-12-01T00:00:00.000Z"),
    sharedLatitude: 37.5,
    sharedLongitude: 127.0,
    sharedLocationHistory: [{latitude: 37.5, longitude: 127.0}],
    chatMessages: [{
      senderRole: "MANAGER",
      body: "민감한 대화",
      attachments: [{
        fullPath: "companion-chat-attachments/session-legacy/evidence.pdf",
      }],
    }],
  }, new Date("2026-07-18T00:00:00.000Z"));

  assert.equal(evaluation.locationEligible, true);
  assert.equal(evaluation.messageCandidates, 1);
  assert.equal(evaluation.attachments.length, 1);
  assert.equal(evaluation.hasWork, true);
});

test("Firestore 전환 데이터는 보관 기간이 끝나기 전에는 파기하지 않는다", () => {
  const evaluation = evaluateLegacyCompanionSession("session-recent", {
    currentStatus: "COMPLETED",
    updatedAt: Date.parse("2026-07-17T12:00:00.000Z"),
    sharedLatitude: 37.5,
    chatMessages: [{
      body: "보관 중인 대화",
      attachment: {
        fullPath: "companion-chat-attachments/session-recent/evidence.pdf",
      },
    }],
  }, new Date("2026-07-18T00:00:00.000Z"));

  assert.equal(evaluation.hasWork, false);
  assert.equal(evaluation.messageCandidates, 0);
  assert.equal(evaluation.attachments.length, 0);
  assert.equal(evaluation.locationEligible, false);
});

test("Firestore 전환 데이터의 legal hold는 모든 파기 후보를 제외한다", () => {
  const evaluation = evaluateLegacyCompanionSession("session-legacy", {
    currentStatus: "CANCELED",
    updatedAt: Date.parse("2025-12-01T00:00:00.000Z"),
    legalHoldUntil: Date.parse("2026-08-01T00:00:00.000Z"),
    sharedLatitude: 37.5,
    chatMessages: [{
      body: "민감한 대화",
      attachment: {
        fullPath: "companion-chat-attachments/session-legacy/evidence.pdf",
      },
    }],
  }, new Date("2026-07-18T00:00:00.000Z"));

  assert.equal(evaluation.hasWork, false);
  assert.equal(evaluation.legalHoldSkips, 3);
});

test("해석할 수 없는 legal hold 값은 삭제하지 않는 쪽으로 처리한다", () => {
  const evaluation = evaluateLegacyCompanionSession("session-invalid-hold", {
    currentStatus: "COMPLETED",
    updatedAt: Date.parse("2025-12-01T00:00:00.000Z"),
    legalHoldUntil: "invalid",
    sharedLatitude: 37.5,
  }, new Date("2026-07-18T00:00:00.000Z"));

  assert.equal(evaluation.hasWork, false);
  assert.equal(evaluation.legalHoldSkips, 1);
});

test("월간 집계 대상은 서울 시간 기준 직전 달이다", () => {
  assert.equal(
      previousMonthStart(new Date("2026-07-31T20:15:00.000Z")).toISOString(),
      "2026-07-01T00:00:00.000Z",
  );
});
