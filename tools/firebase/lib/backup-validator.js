function validateBackupSnapshot(snapshot, managedCollections) {
  const errors = [];
  const warnings = [];

  if (!snapshot || typeof snapshot !== "object") {
    return {
      errors: ["백업 파일 루트가 객체 형식이 아닙니다."],
      warnings: [],
    };
  }

  if (typeof snapshot.schemaVersion !== "number") {
    warnings.push("schemaVersion이 없거나 숫자가 아닙니다.");
  }

  if (!snapshot.generatedAt || typeof snapshot.generatedAt !== "string") {
    warnings.push("generatedAt이 없거나 문자열이 아닙니다.");
  }

  if (!snapshot.collections || typeof snapshot.collections !== "object") {
    errors.push("collections 객체가 없습니다.");
    return {errors, warnings};
  }

  const seenPaths = new Set();
  for (const collectionName of managedCollections) {
    const documents = snapshot.collections[collectionName];
    if (!Array.isArray(documents)) {
      warnings.push(`${collectionName} 컬렉션이 없거나 배열이 아닙니다.`);
      continue;
    }

    for (const document of documents) {
      validateDocument(collectionName, document, seenPaths, errors, warnings);
    }
  }

  const unknownCollections = Object.keys(snapshot.collections)
      .filter((collectionName) => !managedCollections.includes(collectionName));
  for (const collectionName of unknownCollections) {
    warnings.push(`관리 대상이 아닌 컬렉션이 백업에 포함되어 있습니다: ${collectionName}`);
  }

  return {errors, warnings};
}

function validateDocument(collectionName, document, seenPaths, errors, warnings) {
  if (!document || typeof document !== "object") {
    errors.push(`${collectionName} 컬렉션에 객체가 아닌 항목이 있습니다.`);
    return;
  }

  const pathValue = typeof document.path === "string" ? document.path.trim() : "";
  const idValue = typeof document.id === "string" ? document.id.trim() : "";
  if (!pathValue) {
    errors.push(`${collectionName} 컬렉션 문서에 path가 없습니다.`);
    return;
  }
  if (!pathValue.startsWith(`${collectionName}/`)) {
    errors.push(`${pathValue} 경로가 ${collectionName} 컬렉션에 속하지 않습니다.`);
  }

  if (!idValue) {
    warnings.push(`${pathValue} 문서에 id가 없습니다.`);
  } else {
    const resolvedId = pathValue.split("/").pop();
    if (resolvedId !== idValue) {
      warnings.push(`${pathValue} 문서의 id(${idValue})가 경로 마지막 토큰과 다릅니다.`);
    }
  }

  if (seenPaths.has(pathValue)) {
    errors.push(`중복 문서 경로가 있습니다: ${pathValue}`);
  }
  seenPaths.add(pathValue);

  if (!document.fields || typeof document.fields !== "object" || Array.isArray(document.fields)) {
    errors.push(`${pathValue} 문서의 fields가 객체 형식이 아닙니다.`);
  }
}

module.exports = {
  validateBackupSnapshot,
};
