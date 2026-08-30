const test = require("node:test");
const assert = require("node:assert/strict");
const {Timestamp} = require("firebase-admin/firestore");

const {
  recordManagerDocumentSubmission,
  resolveManagerDocumentSubmissionEvent,
  appendManagerDocumentSubmissionHistory,
  managerDocumentSubmissionContentChanged,
  resolveManagerDocumentEventTime,
} = require("../src/sync");

test("심사 전 상태에서 재심사 대기로 바뀔 때만 제출 이력을 만든다", () => {
  const happenedAt = Timestamp.fromMillis(1_760_000_000_000);
  const event = resolveManagerDocumentSubmissionEvent(
      {role: "MANAGER", managerDocumentStatus: "APPROVED"},
      {
        role: "MANAGER",
        name: "매니저",
        managerDocumentStatus: "PENDING_REVIEW",
        managerDocumentSummary: "재제출 요약",
      },
      "manager-1",
      "event-1",
      happenedAt,
  );

  assert.deepEqual(event, {
    eventId: "event-1",
    eventType: "SUBMITTED",
    happenedAt,
    actorUserId: "manager-1",
    actorName: "매니저 본인",
    summary: "재제출 요약",
    reviewNote: "",
  });
  assert.equal(resolveManagerDocumentSubmissionEvent(
      {role: "MANAGER", managerDocumentStatus: "PENDING_REVIEW"},
      {
        role: "MANAGER",
        managerDocumentStatus: "PENDING_REVIEW",
        managerDocumentHistory: [{eventId: "history-only"}],
      },
      "manager-1",
      "event-2",
      happenedAt,
  ), null);
});

test("심사 대기 중 활성 자격 증빙이 바뀌면 새 제출 이력을 만든다", () => {
  const happenedAt = Timestamp.fromMillis(1_760_000_000_100);
  const beforeData = {
    role: "MANAGER",
    managerDocumentStatus: "PENDING_REVIEW",
    managerDocumentSummary: "첫 제출",
    managerDocumentFiles: {
      nursingLicense: {
        fullPath: "manager-documents/manager-1/nursingLicense/old.jpg",
      },
    },
  };
  const afterData = {
    ...beforeData,
    managerDocumentFiles: {
      nursingLicense: {
        fullPath: "manager-documents/manager-1/nursingLicense/new.jpg",
      },
    },
  };

  const event = resolveManagerDocumentSubmissionEvent(
      beforeData,
      afterData,
      "manager-1",
      "event-pending-revision",
      happenedAt,
  );

  assert.equal(managerDocumentSubmissionContentChanged(beforeData, afterData), true);
  assert.equal(event.eventId, "event-pending-revision");
  assert.equal(event.happenedAt, happenedAt);
  assert.equal(event.summary, "첫 제출");
});

test("활성 자격 증빙 두 키와 요약만 제출 내용 변경으로 판단한다", () => {
  const baseData = {
    managerDocumentSummary: "제출 요약",
    managerDocumentFiles: {
      license: {fullPath: "manager-documents/manager-1/license/current.jpg"},
      nursingLicense: {
        fullPath: "manager-documents/manager-1/nursingLicense/current.jpg",
      },
      healthCertificate: {
        fullPath: "manager-documents/manager-1/healthCertificate/legacy.jpg",
      },
      idCard: {fullPath: "manager-documents/manager-1/idCard/legacy.jpg"},
      criminalRecord: {
        fullPath: "manager-documents/manager-1/criminalRecord/legacy.jpg",
      },
    },
    managerDocumentFilePaths: {
      license: "manager-documents/manager-1/license/current.jpg",
      nursingLicense: "manager-documents/manager-1/nursingLicense/current.jpg",
      healthCertificate: "manager-documents/manager-1/healthCertificate/legacy.jpg",
      idCard: "manager-documents/manager-1/idCard/legacy.jpg",
      criminalRecord: "manager-documents/manager-1/criminalRecord/legacy.jpg",
    },
    managerIdCardStoragePath: "manager-documents/manager-1/idCard/legacy.jpg",
    managerLicenseStoragePath: "manager-documents/manager-1/license/current.jpg",
    managerHealthCertificateStoragePath:
      "manager-documents/manager-1/healthCertificate/legacy.jpg",
    managerCriminalRecordStoragePath:
      "manager-documents/manager-1/criminalRecord/legacy.jpg",
  };

  assert.equal(managerDocumentSubmissionContentChanged(baseData, {
    ...baseData,
    managerDocumentFiles: {
      ...baseData.managerDocumentFiles,
      license: {fullPath: "manager-documents/manager-1/license/revised.jpg"},
    },
  }), true);
  assert.equal(managerDocumentSubmissionContentChanged(baseData, {
    ...baseData,
    managerDocumentFilePaths: {
      ...baseData.managerDocumentFilePaths,
      nursingLicense: "manager-documents/manager-1/nursingLicense/revised.jpg",
    },
  }), true);
  assert.equal(managerDocumentSubmissionContentChanged(baseData, {
    ...baseData,
    managerDocumentSummary: "수정한 제출 요약",
  }), true);
});

test("이관 및 파기 전용 기존 키 변경은 새 제출 이력을 만들지 않는다", () => {
  const beforeData = {
    role: "MANAGER",
    managerDocumentStatus: "PENDING_REVIEW",
    managerDocumentSummary: "제출 요약",
    managerDocumentFiles: {
      license: {fullPath: "manager-documents/manager-1/license/current.jpg"},
      healthCertificate: {
        fullPath: "manager-documents/manager-1/healthCertificate/old.jpg",
      },
      idCard: {fullPath: "manager-documents/manager-1/idCard/old.jpg"},
      criminalRecord: {
        fullPath: "manager-documents/manager-1/criminalRecord/old.jpg",
      },
    },
    managerDocumentFilePaths: {
      license: "manager-documents/manager-1/license/current.jpg",
    },
    managerLicenseStoragePath: "manager-documents/manager-1/license/current.jpg",
  };
  const afterData = {
    ...beforeData,
    managerDocumentFiles: {
      ...beforeData.managerDocumentFiles,
      healthCertificate: {
        fullPath: "manager-documents/manager-1/healthCertificate/new.jpg",
      },
      idCard: {fullPath: "manager-documents/manager-1/idCard/new.jpg"},
      criminalRecord: {
        fullPath: "manager-documents/manager-1/criminalRecord/new.jpg",
      },
    },
    managerIdCardStoragePath: "manager-documents/manager-1/idCard/new.jpg",
    managerHealthCertificateStoragePath:
      "manager-documents/manager-1/healthCertificate/new.jpg",
    managerCriminalRecordStoragePath:
      "manager-documents/manager-1/criminalRecord/new.jpg",
  };

  assert.equal(managerDocumentSubmissionContentChanged(beforeData, afterData), false);
  assert.equal(resolveManagerDocumentSubmissionEvent(
      beforeData,
      afterData,
      "manager-1",
      "event-legacy-only",
      Timestamp.fromMillis(1_760_000_000_125),
  ), null);
});

test("서버 표식과 경로가 일치하는 건강진단서 이관은 사용자 재제출로 기록하지 않는다", () => {
  const managerId = "manager-1";
  const sourcePath =
    `manager-documents/${managerId}/healthCertificate/evidence.jpg`;
  const destinationPath =
    `manager-documents/${managerId}/nursingLicense/evidence.jpg`;
  const sourceMetadata = {
    fullPath: sourcePath,
    fileName: "evidence.jpg",
    contentType: "image/jpeg",
    sizeBytes: 1024,
    uploadedAt: 1_760_000_000_000,
  };
  const marker = {
    migrationId: "health-certificate-to-nursing-license-v1",
    sourceKey: "healthCertificate",
    destinationKey: "nursingLicense",
    sourcePath,
    destinationPath,
  };
  const beforeData = {
    role: "MANAGER",
    managerDocumentStatus: "PENDING_REVIEW",
    managerDocumentSummary: "기존 제출",
    managerDocumentFiles: {healthCertificate: sourceMetadata},
    managerDocumentFilePaths: {healthCertificate: sourcePath},
    managerHealthCertificateStoragePath: sourcePath,
  };
  const afterData = {
    role: "MANAGER",
    managerDocumentStatus: "PENDING_REVIEW",
    managerDocumentSummary: "기존 제출",
    managerDocumentFiles: {
      nursingLicense: {...sourceMetadata, fullPath: destinationPath},
    },
    managerDocumentFilePaths: {nursingLicense: destinationPath},
    managerDocumentEvidenceMigration: marker,
  };

  assert.equal(
      managerDocumentSubmissionContentChanged(beforeData, afterData, managerId),
      false,
  );
  assert.equal(resolveManagerDocumentSubmissionEvent(
      beforeData,
      afterData,
      managerId,
      "migration-event",
      Timestamp.fromMillis(1_760_000_000_150),
  ), null);
});

test("표식이 없거나 불일치한 canonical 변경은 실제 제출 변경으로 기록한다", () => {
  const managerId = "manager-1";
  const sourcePath =
    `manager-documents/${managerId}/healthCertificate/evidence.jpg`;
  const destinationPath =
    `manager-documents/${managerId}/nursingLicense/evidence.jpg`;
  const sourceMetadata = {
    fullPath: sourcePath,
    fileName: "evidence.jpg",
    contentType: "image/jpeg",
    sizeBytes: 1024,
  };
  const beforeData = {
    role: "MANAGER",
    managerDocumentStatus: "PENDING_REVIEW",
    managerDocumentSummary: "기존 제출",
    managerDocumentFiles: {healthCertificate: sourceMetadata},
    managerDocumentFilePaths: {healthCertificate: sourcePath},
    managerHealthCertificateStoragePath: sourcePath,
  };
  const migrated = {
    role: "MANAGER",
    managerDocumentStatus: "PENDING_REVIEW",
    managerDocumentSummary: "기존 제출",
    managerDocumentFiles: {
      nursingLicense: {...sourceMetadata, fullPath: destinationPath},
    },
    managerDocumentFilePaths: {nursingLicense: destinationPath},
  };
  const validMarker = {
    migrationId: "health-certificate-to-nursing-license-v1",
    sourceKey: "healthCertificate",
    destinationKey: "nursingLicense",
    sourcePath,
    destinationPath,
  };
  const cases = [
    migrated,
    {
      ...migrated,
      managerDocumentEvidenceMigration: {
        ...validMarker,
        destinationPath: `${destinationPath}.mismatch`,
      },
    },
    {
      ...migrated,
      managerDocumentFiles: {
        nursingLicense: {
          ...sourceMetadata,
          fullPath: destinationPath,
          sizeBytes: 2048,
        },
      },
      managerDocumentEvidenceMigration: validMarker,
    },
    {
      ...migrated,
      managerDocumentSummary: "사용자가 수정한 제출",
      managerDocumentEvidenceMigration: validMarker,
    },
  ];

  for (const afterData of cases) {
    assert.equal(
        managerDocumentSubmissionContentChanged(beforeData, afterData, managerId),
        true,
    );
  }
  assert.equal(managerDocumentSubmissionContentChanged(
      {...beforeData, managerDocumentEvidenceMigration: validMarker},
      {...migrated, managerDocumentEvidenceMigration: validMarker},
      managerId,
  ), true);
});

test("반려 뒤 같은 자료로 다시 요청해도 새 제출 이력을 만든다", () => {
  const happenedAt = Timestamp.fromMillis(1_760_000_000_150);
  const unchangedSubmission = {
    role: "MANAGER",
    managerDocumentSummary: "기존 제출",
    managerDocumentFiles: {idCard: {fullPath: "manager-documents/manager-1/idCard/current.jpg"}},
  };

  const event = resolveManagerDocumentSubmissionEvent(
      {...unchangedSubmission, managerDocumentStatus: "REJECTED"},
      {...unchangedSubmission, managerDocumentStatus: "PENDING_REVIEW"},
      "manager-1",
      "event-requested-again",
      happenedAt,
  );

  assert.equal(event.eventId, "event-requested-again");
  assert.equal(event.happenedAt, happenedAt);
});

test("시간이나 이력만 바뀐 심사 대기 문서는 새 제출 버전으로 보지 않는다", () => {
  const beforeData = {
    role: "MANAGER",
    managerDocumentStatus: "PENDING_REVIEW",
    managerDocumentSummary: "제출 요약",
  };
  const afterData = {
    ...beforeData,
    managerDocumentUpdatedAt: Timestamp.fromMillis(4_102_444_800_000),
    managerDocumentHistory: [{eventId: "server-history"}],
  };

  assert.equal(managerDocumentSubmissionContentChanged(beforeData, afterData), false);
  assert.equal(resolveManagerDocumentSubmissionEvent(
      beforeData,
      afterData,
      "manager-1",
      "event-history-only",
      Timestamp.fromMillis(1_760_000_000_200),
  ), null);
});

test("제출 이력 시각은 Firestore update time을 우선하고 CloudEvent time을 대체값으로 쓴다", () => {
  const updateTime = Timestamp.fromMillis(1_760_000_000_300);

  assert.equal(
      resolveManagerDocumentEventTime(updateTime, "2100-01-01T00:00:00.000Z"),
      updateTime,
  );
  assert.equal(
      resolveManagerDocumentEventTime(undefined, "2026-08-29T09:10:00.000Z").toDate().toISOString(),
      "2026-08-29T09:10:00.000Z",
  );
  assert.throws(
      () => resolveManagerDocumentEventTime(undefined, "invalid"),
      /서버 시각/,
  );
});

test("제출 이력 trigger는 실패 이벤트 재처리를 사용한다", () => {
  assert.equal(recordManagerDocumentSubmission.__endpoint.eventTrigger.retry, true);
});

test("동일 Functions 이벤트는 제출 이력에 한 번만 기록한다", async () => {
  const history = [];
  const snapshot = {
    exists: true,
    get(field) {
      return field === "managerDocumentHistory" ? history : undefined;
    },
  };
  const transaction = {
    async get() {
      return snapshot;
    },
    update(_reference, updates) {
      history.push(...updates.managerDocumentHistory.elements);
    },
  };
  const firestore = {
    async runTransaction(callback) {
      return callback(transaction);
    },
  };
  const submissionEvent = {
    eventId: "event-once",
    eventType: "SUBMITTED",
    happenedAt: Timestamp.fromMillis(1_760_000_000_000),
    actorUserId: "manager-1",
    actorName: "매니저",
    summary: "제출 요약",
    reviewNote: "",
  };

  assert.equal(await appendManagerDocumentSubmissionHistory(firestore, {}, submissionEvent), true);
  assert.equal(await appendManagerDocumentSubmissionHistory(firestore, {}, submissionEvent), false);
  assert.equal(history.length, 1);
});

test("첫 기록 실패 뒤 같은 이벤트를 재처리해도 이력은 정확히 한 번만 남는다", async () => {
  const history = [];
  let transactionAttempts = 0;
  const firestore = {
    async runTransaction(callback) {
      transactionAttempts += 1;
      if (transactionAttempts === 1) {
        throw new Error("temporary failure");
      }
      return callback({
        async get() {
          return {
            exists: true,
            get(field) {
              return field === "managerDocumentHistory" ? history : undefined;
            },
          };
        },
        update(_reference, updates) {
          history.push(...updates.managerDocumentHistory.elements);
        },
      });
    },
  };
  const submissionEvent = {
    eventId: "event-retried",
    eventType: "SUBMITTED",
    happenedAt: Timestamp.fromMillis(1_760_000_000_400),
    actorUserId: "manager-1",
    actorName: "매니저",
    summary: "재처리 제출",
    reviewNote: "",
  };

  await assert.rejects(
      appendManagerDocumentSubmissionHistory(firestore, {}, submissionEvent),
      /temporary failure/,
  );
  assert.equal(await appendManagerDocumentSubmissionHistory(firestore, {}, submissionEvent), true);
  assert.equal(await appendManagerDocumentSubmissionHistory(firestore, {}, submissionEvent), false);
  assert.equal(history.length, 1);
});
