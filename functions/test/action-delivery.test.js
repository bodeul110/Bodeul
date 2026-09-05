"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const firestoreModule = require("firebase-admin/firestore");

let activeFirestore = null;
const originalGetFirestore = firestoreModule.getFirestore;
firestoreModule.getFirestore = () => activeFirestore;
delete require.cache[require.resolve("../src/action-delivery")];
const {
  deliverAdminActionDeliveryJobs,
  dispatchAdminActionDeliveryJobs,
} = require("../src/action-delivery");
firestoreModule.getFirestore = originalGetFirestore;

test("기존 긴급 전달 작업은 provider 호출 전에 job과 delivery를 함께 종료한다", async () => {
  const originalFetch = global.fetch;
  const originalEndpoint = process.env.ADMIN_PUSH_ENDPOINT;
  const originalApiKey = process.env.ADMIN_PUSH_API_KEY;
  let fetchCalls = 0;

  process.env.ADMIN_PUSH_ENDPOINT = "https://push.example.test/deliver";
  process.env.ADMIN_PUSH_API_KEY = "test-key";
  global.fetch = async () => {
    fetchCalls++;
    throw new Error("기존 긴급 전달 작업은 provider를 호출하면 안 됩니다.");
  };

  try {
    const cases = [
      {
        name: "작업 문서 sourceType",
        dispatcher: "scheduled",
        initialState: "PENDING",
        jobSourceType: " emergency ",
        deliverySourceType: "SUPPORT",
      },
      {
        name: "연결 전달 기록 sourceType",
        dispatcher: "manual",
        initialState: "FAILED",
        jobSourceType: "",
        deliverySourceType: "EMERGENCY",
      },
    ];

    for (const scenario of cases) {
      const fixture = createActionDeliveryFixture(scenario);
      activeFirestore = fixture.firestore;

      await runDispatcher(scenario.dispatcher);

      const expectedAuthLookups = scenario.dispatcher === "manual" ? 1 : 0;
      assert.equal(
          fixture.userLookupCount(),
          expectedAuthLookups,
          `${scenario.name}: 인증 외 관리자 수신자를 조회하지 않아야 합니다.`,
      );
      assert.equal(fetchCalls, 0, `${scenario.name}: provider를 호출하지 않아야 합니다.`);
      assert.equal(fixture.jobData.state, "SKIPPED");
      assert.equal(fixture.jobData.skipReason, "legacy_emergency_disabled");
      assert.equal(
          fixture.jobData.deliveredBy,
          scenario.dispatcher === "manual" ? "manual" : "delivery",
      );
      assert.equal(fixture.jobData.deliveryAttempts, 1);
      assert.equal(fixture.deliveryData.status, "skipped");
      assert.equal(fixture.deliveryData.state, "skipped");
      assert.equal(fixture.deliveryData.priority, "archived");
      assert.deepEqual(fixture.deliveryData.filterKeys, ["completed"]);
      assert.equal(fixture.deliveryData.slaStatus, "completed");

      const updateCount = fixture.updateCount();
      await runDispatcher(scenario.dispatcher);
      assert.equal(
          fixture.updateCount(),
          updateCount,
          `${scenario.name}: 종료된 작업은 다음 주기에서 다시 선점하지 않아야 합니다.`,
      );
    }
  } finally {
    activeFirestore = null;
    global.fetch = originalFetch;
    restoreEnvironmentValue("ADMIN_PUSH_ENDPOINT", originalEndpoint);
    restoreEnvironmentValue("ADMIN_PUSH_API_KEY", originalApiKey);
  }
});

function createActionDeliveryFixture({initialState, jobSourceType, deliverySourceType}) {
  const jobData = {
    state: initialState,
    channel: "app_push",
    deliveryId: "delivery-1",
    sourceType: jobSourceType,
    deliveryAttempts: 0,
    maxAttempts: 3,
  };
  const deliveryData = {
    sourceType: deliverySourceType,
    channel: "app_push",
    status: "failed",
    createdAt: new Date("2026-09-01T00:00:00.000Z"),
  };
  const updates = [];
  let userLookups = 0;

  const jobRef = createDocumentReference("adminActionDeliveryJobs", "job-1");
  const deliveryRef = createDocumentReference("adminActionDeliveries", "delivery-1");
  const jobSnapshot = createDocumentSnapshot(jobRef, jobData);
  const deliverySnapshot = createDocumentSnapshot(deliveryRef, deliveryData);

  const firestore = {
    collection(name) {
      if (name === "adminActionDeliveryJobs") {
        return {
          doc: () => jobRef,
          where(field, operator, values) {
            assert.equal(field, "state");
            assert.equal(operator, "in");
            return {
              limit() {
                return {
                  async get() {
                    const docs = values.includes(jobData.state) ? [jobSnapshot] : [];
                    return {size: docs.length, docs};
                  },
                };
              },
            };
          },
        };
      }
      if (name === "adminActionDeliveries") {
        return {
          doc() {
            return {
              ...deliveryRef,
              async get() {
                return deliverySnapshot;
              },
            };
          },
        };
      }
      if (name === "users") {
        return {
          doc() {
            userLookups++;
            return {
              async get() {
                return {
                  exists: true,
                  get(field) {
                    return field === "role" ? "ADMIN" : "";
                  },
                };
              },
            };
          },
          where() {
            userLookups++;
            throw new Error("기존 긴급 전달 작업은 관리자 수신자를 조회하면 안 됩니다.");
          },
        };
      }
      throw new Error(`지원하지 않는 테스트 컬렉션입니다: ${name}`);
    },
    async runTransaction(callback) {
      return callback({
        async get() {
          return jobSnapshot;
        },
        update(ref, fields) {
          applyUpdate(ref, fields);
        },
      });
    },
    batch() {
      const pendingUpdates = [];
      return {
        update(ref, fields) {
          pendingUpdates.push({ref, fields});
        },
        async commit() {
          for (const {ref, fields} of pendingUpdates) {
            applyUpdate(ref, fields);
          }
        },
      };
    },
  };

  function applyUpdate(ref, fields) {
    updates.push({ref, fields});
    const target = ref.collection === "adminActionDeliveryJobs" ? jobData : deliveryData;
    for (const [field, value] of Object.entries(fields)) {
      target[field] = value;
    }
  }

  return {
    firestore,
    jobData,
    deliveryData,
    updateCount: () => updates.length,
    userLookupCount: () => userLookups,
  };
}

async function runDispatcher(dispatcher) {
  if (dispatcher === "manual") {
    return dispatchAdminActionDeliveryJobs.run({
      auth: {uid: "admin-user"},
      data: {batchSize: 20},
    });
  }
  return deliverAdminActionDeliveryJobs.run({});
}

function createDocumentReference(collection, id) {
  return {collection, id, path: `${collection}/${id}`};
}

function createDocumentSnapshot(ref, data) {
  return {
    exists: true,
    id: ref.id,
    ref,
    data: () => ({...data}),
    get: (field) => data[field],
  };
}

function restoreEnvironmentValue(name, value) {
  if (value === undefined) {
    delete process.env[name];
  } else {
    process.env[name] = value;
  }
}
