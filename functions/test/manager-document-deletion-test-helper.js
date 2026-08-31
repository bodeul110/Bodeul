function createTransactionalDocument(initialData, {
  id = "manager-1",
  beforeTransaction = null,
} = {}) {
  let data = structuredClone(initialData);
  let transactionCount = 0;
  const reference = {id};
  const firestore = {
    async runTransaction(handler) {
      transactionCount += 1;
      if (beforeTransaction) {
        await beforeTransaction({
          transactionCount,
          getData: () => data,
          setData: (nextData) => {
            data = structuredClone(nextData);
          },
        });
      }
      return handler({
        async get(requestedReference) {
          if (requestedReference !== reference) {
            throw new Error("예상하지 않은 문서 참조입니다.");
          }
          return snapshot();
        },
        update(requestedReference, updates) {
          if (requestedReference !== reference) {
            throw new Error("예상하지 않은 문서 참조입니다.");
          }
          data = applyFirestoreUpdates(data, updates);
        },
      });
    },
  };
  reference.firestore = firestore;
  reference.get = async () => snapshot();

  function snapshot() {
    return {
      exists: data !== null,
      id,
      data: () => structuredClone(data),
      get: (field) => data?.[field],
      ref: reference,
    };
  }

  return {
    firestore,
    reference,
    getData: () => structuredClone(data),
    setData: (nextData) => {
      data = nextData === null ? null : structuredClone(nextData);
    },
    getTransactionCount: () => transactionCount,
  };
}

function applyFirestoreUpdates(currentData, updates) {
  const nextData = structuredClone(currentData || {});
  for (const [fieldPath, value] of Object.entries(updates)) {
    const segments = fieldPath.split(".");
    let target = nextData;
    for (let index = 0; index < segments.length - 1; index += 1) {
      const segment = segments[index];
      if (!isPlainObject(target[segment])) {
        target[segment] = {};
      }
      target = target[segment];
    }
    const leaf = segments[segments.length - 1];
    if (value?.constructor?.name === "DeleteTransform") {
      delete target[leaf];
    } else if (value?.constructor?.name === "ServerTimestampTransform") {
      target[leaf] = new Date("2026-08-31T00:00:00.000Z");
    } else {
      target[leaf] = structuredClone(value);
    }
  }
  return nextData;
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

module.exports = {
  applyFirestoreUpdates,
  createTransactionalDocument,
};
