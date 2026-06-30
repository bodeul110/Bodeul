const fs = require("fs");
const os = require("os");
const path = require("path");

const {DEFAULT_PASSWORD, LIST_PAGE_SIZE} = require("./baseline-config");
const DEFAULT_FIREBASE_OAUTH_CLIENT_ID =
  "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const FIRESTORE_API_ORIGIN = "https://firestore.googleapis.com";
const IDENTITY_TOOLKIT_API_ORIGIN = "https://identitytoolkit.googleapis.com";
const STORAGE_API_ORIGIN = "https://storage.googleapis.com";
const PROJECT_ID_PATTERN = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/;
const STORAGE_BUCKET_PATTERN = /^[a-z0-9][a-z0-9._-]{1,220}[a-z0-9]$/;

async function createCliContext() {
  const projectId = resolveProjectId();
  if (!projectId) {
    throw new Error("프로젝트 ID를 찾지 못했습니다. .firebaserc 또는 app/google-services.json을 확인해 주세요.");
  }
  assertProjectId(projectId);

  const storageBucket = resolveStorageBucket(projectId);
  assertStorageBucket(storageBucket);
  const accessToken = await resolveFirebaseAccessToken();
  if (!accessToken) {
    throw new Error("firebase login 토큰을 찾지 못했습니다. `firebase login`으로 다시 로그인한 뒤 실행해 주세요.");
  }

  return {
    projectId,
    storageBucket,
    accessToken,
  };
}

function resolveProjectId() {
  const envProjectId = sanitizeText(
      process.env.GOOGLE_CLOUD_PROJECT ||
      process.env.GCLOUD_PROJECT ||
      process.env.FIREBASE_PROJECT_ID,
  );
  if (envProjectId) {
    return envProjectId;
  }

  const repoRoot = resolveRepoRoot();
  const firebasercProjectId = resolveJsonValue(
      path.join(repoRoot, ".firebaserc"),
      (data) => data?.projects?.default,
  );
  if (firebasercProjectId) {
    return firebasercProjectId;
  }

  return resolveJsonValue(
      path.join(repoRoot, "app", "google-services.json"),
      (data) => data?.project_info?.project_id,
  );
}

function resolveStorageBucket(projectId) {
  const envBucket = sanitizeText(process.env.FIREBASE_STORAGE_BUCKET);
  if (envBucket) {
    return envBucket;
  }

  const repoRoot = resolveRepoRoot();
  const configuredBucket = resolveJsonValue(
      path.join(repoRoot, "app", "google-services.json"),
      (data) => data?.project_info?.storage_bucket,
  );
  if (configuredBucket) {
    return configuredBucket;
  }

  return `${projectId}.firebasestorage.app`;
}

async function resolveFirebaseAccessToken() {
  const tokenFromEnv = sanitizeText(process.env.FIREBASE_TOKEN);
  if (tokenFromEnv) {
    return resolveAccessTokenFromRawToken(tokenFromEnv);
  }

  const storedToken = resolveStoredFirebaseToken();
  if (!storedToken) {
    return "";
  }
  if (storedToken.accessToken && storedToken.expiresAt > Date.now() + 60000) {
    return storedToken.accessToken;
  }
  if (!storedToken.refreshToken) {
    return storedToken.accessToken;
  }

  return (await refreshFirebaseAccessToken(storedToken)).accessToken;
}

async function resolveAccessTokenFromRawToken(rawToken) {
  if (!rawToken) {
    return "";
  }

  if (isLikelyAccessToken(rawToken)) {
    return rawToken;
  }

  try {
    const refreshedToken = await refreshFirebaseAccessToken({
      refreshToken: rawToken,
      scope: "",
    });
    return refreshedToken.accessToken;
  } catch (error) {
    return rawToken;
  }
}

function resolveRepoRoot() {
  return path.resolve(__dirname, "..", "..", "..");
}

function resolveFirebaseTokenConfigCandidates() {
  const homeDirectory = os.homedir();
  return [
    path.join(homeDirectory, ".config", "configstore", "firebase-tools.json"),
    path.join(homeDirectory, "AppData", "Roaming", "configstore", "firebase-tools.json"),
    path.join(homeDirectory, "AppData", "Local", "configstore", "firebase-tools.json"),
  ];
}

function resolveStoredFirebaseToken() {
  const candidates = resolveFirebaseTokenConfigCandidates();

  for (const candidate of candidates) {
    const config = readJsonFile(candidate);
    if (!config) {
      continue;
    }

    const accessToken = sanitizeText(config?.tokens?.access_token);
    const refreshToken = sanitizeText(config?.tokens?.refresh_token);
    if (!accessToken && !refreshToken) {
      continue;
    }

    return {
      accessToken,
      refreshToken,
      expiresAt: Number(config?.tokens?.expires_at || 0),
      scope: sanitizeText(config?.tokens?.scope),
    };
  }

  return null;
}

function readJsonFile(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (error) {
    return null;
  }
}

function resolveJsonValue(filePath, selector) {
  try {
    const raw = fs.readFileSync(filePath, "utf8");
    return sanitizeText(selector(JSON.parse(raw)));
  } catch (error) {
    return "";
  }
}

function sanitizeText(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

function assertProjectId(projectId) {
  const value = sanitizeText(projectId);
  if (!PROJECT_ID_PATTERN.test(value)) {
    throw new Error("프로젝트 ID 형식이 올바르지 않습니다.");
  }
  return value;
}

function assertStorageBucket(storageBucket) {
  const value = sanitizeText(storageBucket);
  if (!STORAGE_BUCKET_PATTERN.test(value) || value.includes("..")) {
    throw new Error("Storage 버킷 형식이 올바르지 않습니다.");
  }
  return value;
}

function assertApiPathSegment(value, label) {
  const segment = sanitizeText(value);
  if (!segment || segment === "." || segment === ".."
      || /[\u0000-\u001f\u007f?#\\]/.test(segment)
      || segment.includes("://")) {
    throw new Error(`${label} 형식이 올바르지 않습니다.`);
  }
  return segment;
}

function assertFirestoreRelativePath(relativePath, label = "Firestore 경로") {
  const value = sanitizeText(relativePath);
  const segments = value.split("/");
  if (!value || segments.some((segment) => !assertApiPathSegment(segment, label))) {
    throw new Error(`${label} 형식이 올바르지 않습니다.`);
  }
  return value;
}

function assertStorageObjectName(objectName, label = "Storage 객체 경로") {
  const value = sanitizeText(objectName);
  const segments = value.split("/");
  if (!value || /[\u0000-\u001f\u007f?#\\]/.test(value)
      || segments.some((segment) => segment === "." || segment === "..")) {
    throw new Error(`${label} 형식이 올바르지 않습니다.`);
  }
  return value;
}

function encodeApiPath(relativePath, label) {
  return assertFirestoreRelativePath(relativePath, label)
      .split("/")
      .map((segment) => encodeURIComponent(segment))
      .join("/");
}

function buildApiUrl(origin, pathname, queryParams) {
  const url = new URL(pathname, origin);
  if (queryParams) {
    for (const [key, value] of queryParams.entries()) {
      url.searchParams.append(key, value);
    }
  }
  return url.toString();
}

function buildIdentityToolkitUrl(context, endpoint) {
  const safeEndpoint = assertApiPathSegment(endpoint, "Identity Toolkit 엔드포인트");
  return buildApiUrl(
      IDENTITY_TOOLKIT_API_ORIGIN,
      `/v1/projects/${encodeURIComponent(assertProjectId(context.projectId))}/${safeEndpoint}`,
  );
}

function buildFirestoreDocumentUrl(context, relativePath, queryParams) {
  return buildApiUrl(
      FIRESTORE_API_ORIGIN,
      `/v1/projects/${encodeURIComponent(assertProjectId(context.projectId))}` +
      `/databases/(default)/documents/${encodeApiPath(relativePath, "Firestore 문서 경로")}`,
      queryParams,
  );
}

function buildStorageListUrl(context, queryParams) {
  return buildApiUrl(
      STORAGE_API_ORIGIN,
      `/storage/v1/b/${encodeURIComponent(assertStorageBucket(context.storageBucket))}/o`,
      queryParams,
  );
}

function buildStorageObjectUrl(context, objectName) {
  return buildApiUrl(
      STORAGE_API_ORIGIN,
      `/storage/v1/b/${encodeURIComponent(assertStorageBucket(context.storageBucket))}` +
      `/o/${encodeURIComponent(assertStorageObjectName(objectName))}`,
  );
}

function buildStorageUploadUrl(context, queryParams) {
  return buildApiUrl(
      STORAGE_API_ORIGIN,
      `/upload/storage/v1/b/${encodeURIComponent(assertStorageBucket(context.storageBucket))}/o`,
      queryParams,
  );
}

function isLikelyAccessToken(token) {
  return sanitizeText(token).startsWith("ya29.");
}

async function refreshFirebaseAccessToken(storedToken) {
  const clientId = resolveFirebaseOAuthClientId();
  const clientSecret = resolveFirebaseOAuthClientSecret();
  if (!clientSecret) {
    throw new Error(
        "Firebase OAuth client secret을 찾지 못했습니다. " +
        "FIREBASE_OAUTH_CLIENT_SECRET 환경 변수 또는 local.properties의 " +
        "firebaseOauthClientSecret 값을 설정해 주세요.",
    );
  }

  const body = new URLSearchParams();
  body.set("refresh_token", storedToken.refreshToken);
  body.set("client_id", clientId);
  body.set("client_secret", clientSecret);
  body.set("grant_type", "refresh_token");
  if (storedToken.scope) {
    body.set("scope", storedToken.scope);
  }

  const response = await fetch("https://www.googleapis.com/oauth2/v3/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body,
  });
  const payload = await response.json();
  if (!response.ok || !sanitizeText(payload?.access_token)) {
    throw new Error(`firebase login 토큰 갱신 실패: ${response.status} ${JSON.stringify(payload)}`);
  }

  return {
    accessToken: sanitizeText(payload.access_token),
    expiresAt: Date.now() + (Number(payload.expires_in || 3600) * 1000),
    scope: sanitizeText(payload.scope) || storedToken.scope,
  };
}

async function resolveBaselineUsers(context, baselineUsers, createMissingUsers) {
  const foundUsers = [];
  const missingUsers = [];

  for (const baselineUser of baselineUsers) {
    const existingUser = await lookupAuthUserByEmail(context, baselineUser.email);
    if (existingUser) {
      foundUsers.push({
        uid: existingUser.localId,
        emailVerified: Boolean(existingUser.emailVerified),
        ...baselineUser,
      });
      continue;
    }

    if (!createMissingUsers) {
      missingUsers.push(baselineUser);
      continue;
    }

    const createdUser = await createAuthUser(context, baselineUser);
    foundUsers.push({
      uid: createdUser.localId,
      emailVerified: true,
      ...baselineUser,
    });
  }

  return {foundUsers, missingUsers};
}

async function lookupAuthUserByEmail(context, email) {
  const response = await postJson(
      buildIdentityToolkitUrl(context, "accounts:lookup"),
      context.accessToken,
      {email: [email]},
  );
  if (!Array.isArray(response.users) || response.users.length === 0) {
    return null;
  }
  return response.users[0];
}

async function createAuthUser(context, baselineUser) {
  return postJson(
      buildIdentityToolkitUrl(context, "accounts"),
      context.accessToken,
      {
        email: baselineUser.email,
        password: DEFAULT_PASSWORD,
        displayName: baselineUser.name,
        emailVerified: true,
        disabled: false,
      },
  );
}

async function getCollectionCounts(context, collectionNames) {
  const counts = {};
  for (const collectionName of collectionNames) {
    counts[collectionName] = (await listCollectionDocuments(context, collectionName)).length;
  }
  return counts;
}

async function listCollectionDocuments(context, collectionName) {
  const documents = [];
  let pageToken = "";

  while (true) {
    const query = new URLSearchParams();
    query.set("pageSize", String(LIST_PAGE_SIZE));
    if (pageToken) {
      query.set("pageToken", pageToken);
    }

    const response = await fetch(
        buildFirestoreDocumentUrl(context, collectionName, query),
        {
          headers: {
            Authorization: `Bearer ${context.accessToken}`,
          },
        },
    );
    const payload = await response.json();
    if (!response.ok) {
      throw new Error(`${collectionName} 조회 실패: ${response.status} ${JSON.stringify(payload)}`);
    }

    if (Array.isArray(payload.documents)) {
      documents.push(...payload.documents);
    }

    pageToken = sanitizeText(payload.nextPageToken);
    if (!pageToken) {
      return documents;
    }
  }
}

async function getDocument(context, relativePath) {
  const response = await fetch(
      buildFirestoreDocumentUrl(context, relativePath),
      {
        headers: {
          Authorization: `Bearer ${context.accessToken}`,
        },
      },
  );

  if (response.status === 404) {
    return null;
  }

  const payload = await response.json();
  if (!response.ok) {
    throw new Error(`${relativePath} 조회 실패: ${response.status} ${JSON.stringify(payload)}`);
  }
  return payload;
}

async function deleteCollectionDocuments(context, collectionName) {
  const documents = await listCollectionDocuments(context, collectionName);
  for (const document of documents) {
    await deleteDocument(context, document.name);
  }
  return documents.length;
}

async function deleteDocument(context, documentName) {
  const relativePath = extractRelativeDocumentPath(documentName, context.projectId);
  const response = await fetch(
      buildFirestoreDocumentUrl(context, relativePath),
      {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${context.accessToken}`,
        },
      },
  );

  if (!response.ok) {
    const payload = await response.json();
    throw new Error(`문서 삭제 실패: ${response.status} ${JSON.stringify(payload)}`);
  }
}

async function getStorageObject(context, objectName) {
  const response = await fetch(
      buildStorageObjectUrl(context, objectName),
      {
        headers: {
          Authorization: `Bearer ${context.accessToken}`,
        },
      },
  );

  if (response.status === 404) {
    return null;
  }

  const payload = await response.json();
  if (!response.ok) {
    throw new Error(`Storage 객체 조회 실패: ${response.status} ${JSON.stringify(payload)}`);
  }
  return payload;
}

async function listStorageObjects(context, prefix) {
  const objects = [];
  let pageToken = "";

  while (true) {
    const query = new URLSearchParams();
    if (prefix) {
      query.set("prefix", assertStorageObjectName(prefix, "Storage 접두어"));
    }
    if (pageToken) {
      query.set("pageToken", pageToken);
    }

    const response = await fetch(
        buildStorageListUrl(context, query),
        {
          headers: {
            Authorization: `Bearer ${context.accessToken}`,
          },
        },
    );
    const payload = await response.json();
    if (!response.ok) {
      throw new Error(`Storage 목록 조회 실패: ${response.status} ${JSON.stringify(payload)}`);
    }

    if (Array.isArray(payload.items)) {
      objects.push(...payload.items);
    }

    pageToken = sanitizeText(payload.nextPageToken);
    if (!pageToken) {
      return objects;
    }
  }
}

async function deleteStorageObject(context, objectName) {
  const response = await fetch(
      buildStorageObjectUrl(context, objectName),
      {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${context.accessToken}`,
        },
      },
  );

  if (response.status === 404) {
    return false;
  }

  if (!response.ok) {
    const payload = await response.json();
    throw new Error(`Storage 객체 삭제 실패: ${response.status} ${JSON.stringify(payload)}`);
  }
  return true;
}

async function uploadStorageObject(context, objectName, contentType, body) {
  const query = new URLSearchParams();
  query.set("uploadType", "media");
  query.set("name", assertStorageObjectName(objectName));

  const response = await fetch(
      buildStorageUploadUrl(context, query),
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${context.accessToken}`,
          "Content-Type": contentType || "application/octet-stream",
        },
        body,
      },
  );
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(`Storage 객체 업로드 실패: ${response.status} ${JSON.stringify(payload)}`);
  }
  return payload;
}

async function patchDocumentData(context, relativePath, data) {
  return patchDocumentFields(context, relativePath, toFirestoreMap(data));
}

async function patchDocumentFields(context, relativePath, fields) {
  const query = new URLSearchParams();
  for (const fieldPath of Object.keys(fields || {})) {
    query.append("updateMask.fieldPaths", fieldPath);
  }

  const response = await fetch(
      buildFirestoreDocumentUrl(context, relativePath, query),
      {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${context.accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({fields}),
      },
  );
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(`${relativePath} 저장 실패: ${response.status} ${JSON.stringify(payload)}`);
  }
  return payload;
}

async function postJson(url, accessToken, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}: ${JSON.stringify(payload)}`);
  }
  return payload;
}

function toFirestoreMap(value) {
  const fields = {};
  for (const [key, entry] of Object.entries(value)) {
    if (entry === undefined) {
      continue;
    }
    fields[key] = toFirestoreValue(entry);
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
        fields: toFirestoreMap(value),
      },
    };
  }
  return {stringValue: String(value)};
}

function extractRelativeDocumentPath(documentName, projectId) {
  const safeProjectId = assertProjectId(projectId);
  const value = sanitizeText(documentName);
  const prefix = `projects/${safeProjectId}/databases/(default)/documents/`;
  if (value.startsWith(prefix)) {
    return assertFirestoreRelativePath(value.slice(prefix.length));
  }
  if (value.startsWith("projects/")) {
    throw new Error("Firestore 문서 이름이 현재 프로젝트와 일치하지 않습니다.");
  }
  return assertFirestoreRelativePath(value);
}

function buildBackupFileName() {
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
  return `firestore-backup-${token}.json`;
}

function resolveFirebaseCiToken() {
  const tokenFromEnv = sanitizeText(process.env.FIREBASE_TOKEN);
  if (tokenFromEnv) {
    return tokenFromEnv;
  }

  const storedToken = resolveStoredFirebaseToken();
  return sanitizeText(storedToken?.refreshToken || storedToken?.accessToken);
}

function resolveFirebaseOAuthClientId() {
  return sanitizeText(
      process.env.FIREBASE_OAUTH_CLIENT_ID ||
      readLocalProperty("firebaseOauthClientId") ||
      DEFAULT_FIREBASE_OAUTH_CLIENT_ID,
  );
}

function resolveFirebaseOAuthClientSecret() {
  return sanitizeText(
      process.env.FIREBASE_OAUTH_CLIENT_SECRET ||
      readLocalProperty("firebaseOauthClientSecret"),
  );
}

function readLocalProperty(key) {
  const localPropertiesPath = path.join(resolveRepoRoot(), "local.properties");
  if (!fs.existsSync(localPropertiesPath)) {
    return "";
  }

  try {
    const lines = fs.readFileSync(localPropertiesPath, "utf8").split(/\r?\n/);
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) {
        continue;
      }
      const separatorIndex = trimmed.indexOf("=");
      if (separatorIndex < 0) {
        continue;
      }
      const propertyKey = trimmed.slice(0, separatorIndex).trim();
      if (propertyKey !== key) {
        continue;
      }
      return trimmed.slice(separatorIndex + 1).trim();
    }
  } catch (error) {
    return "";
  }

  return "";
}

module.exports = {
  buildBackupFileName,
  createCliContext,
  deleteCollectionDocuments,
  deleteStorageObject,
  extractRelativeDocumentPath,
  getCollectionCounts,
  getDocument,
  getStorageObject,
  listCollectionDocuments,
  listStorageObjects,
  lookupAuthUserByEmail,
  patchDocumentData,
  patchDocumentFields,
  resolveFirebaseCiToken,
  resolveFirebaseOAuthClientSecret,
  resolveBaselineUsers,
  resolveProjectId,
  resolveStorageBucket,
  uploadStorageObject,
};
