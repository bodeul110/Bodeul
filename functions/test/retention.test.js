const assert = require("node:assert/strict");
const test = require("node:test");

const {
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
    attachmentsDeleted: -1,
    unexpected: 100,
  });

  assert.equal(counts.postgresMessageCandidates, 3);
  assert.equal(counts.attachmentsDeleted, 0);
  assert.equal(counts.managerDocumentDeleteFailures, 0);
  assert.equal(Object.hasOwn(counts, "mode"), false);
  assert.equal(Object.hasOwn(counts, "unexpected"), false);
  assert.equal(Object.keys(counts).length, 20);
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
  assert.deepEqual(database.calls, ["begin", "preview", "finish"]);
});

test("Storage 삭제 실패 시 첨부를 파기 완료로 표시하지 않는다", async () => {
  let markCount = 0;
  const database = createDatabase({
    async claimAttachments() {
      return [{
        id: "50de4226-df48-4622-9de8-c292c3fc0ed9",
        storagePath: "companion-chat-attachments/728916a2-d57e-4e8f-bd99-c6c47498b4ba/a.pdf",
      }];
    },
    async markAttachmentDeleted() {
      markCount += 1;
      return true;
    },
  });
  const legacyStore = createLegacyStore();
  const managerStore = createManagerStore();
  const storage = {
    async deleteChatAttachment() {
      throw new Error("storage unavailable");
    },
    async deleteManagerDocument() {},
  };

  const summary = await runRetentionJob({
    database,
    legacyStore,
    managerStore,
    storage,
    apply: true,
    now: new Date("2026-07-18T00:00:00.000Z"),
  });

  assert.equal(markCount, 0);
  assert.equal(summary.attachmentsDeleted, 0);
  assert.equal(summary.attachmentDeleteFailures, 1);
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

  const held = evaluateManagerDocument("manager-1", {
    ...baseData,
    managerDocumentLegalHoldUntil: Date.parse("2026-08-01T00:00:00.000Z"),
  }, now);
  assert.equal(held.candidates.length, 0);
  assert.equal(held.legalHoldSkips, 1);
});

test("관리자 증빙은 Storage 삭제 후에만 Firestore 참조를 지운다", async () => {
  const candidate = {
    managerId: "manager-1",
    documentKey: "license",
    storagePath: "manager-documents/manager-1/license/license.pdf",
  };
  const order = [];
  const database = createDatabase();
  const legacyStore = createLegacyStore();
  const managerStore = createManagerStore({
    async preview() {
      return {candidates: [candidate], legalHoldSkips: 0};
    },
    async isStillEligible() {
      order.push("validate");
      return true;
    },
    async clearReference() {
      order.push("clear");
      return true;
    },
  });
  const storage = {
    async deleteChatAttachment() {},
    async deleteManagerDocument() {
      order.push("delete");
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

  assert.deepEqual(order, ["validate", "delete", "clear"]);
  assert.equal(summary.managerDocumentsDeleted, 1);
});

test("삭제 경로는 허용된 Storage 접두사와 단일 파일 깊이로 제한한다", () => {
  assert.equal(isAllowedChatAttachmentPath(
      "companion-chat-attachments/728916a2-d57e-4e8f-bd99-c6c47498b4ba/a.pdf",
  ), true);
  assert.equal(isAllowedChatAttachmentPath("manager-documents/user/idCard/a.pdf"), false);
  assert.equal(isAllowedManagerDocumentPath(
      "manager-documents/manager-1/idCard/a.pdf",
  ), true);
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
