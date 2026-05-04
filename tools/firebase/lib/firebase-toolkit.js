const fs = require("fs");
const os = require("os");
const path = require("path");

const {DEFAULT_PASSWORD, LIST_PAGE_SIZE} = require("./baseline-config");
const DEFAULT_FIREBASE_OAUTH_CLIENT_ID =
  "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";

async function createCliContext() {
  const projectId = resolveProjectId();
  if (!projectId) {
    throw new Error("프로젝트 ID를 찾지 못했습니다. .firebaserc 또는 app/google-services.json을 확인해 주세요.");
  }

  const storageBucket = resolveStorageBucket(projectId);
  const accessToken = await resolveFirebaseAccessToken();
  if (!accessToken) {
    throw new Error("firebase login 토큰을 찾지 못했습니다. `firebase login`으로 다시 로그인한 뒤 실행해 주세요.");
  }

  return {
    projectId,
    storageBucket,
    accessToken,
    firestoreBaseUrl: `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents`,
    identityToolkitBaseUrl: `https://identitytoolkit.googleapis.com/v1/projects/${projectId}`,
    storageBaseUrl: `https://storage.googleapis.com/storage/v1/b/${encodeURIComponent(storageBucket)}`,
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

  const refreshedToken = await refreshFirebaseAccessToken(storedToken);
  persistFirebaseAccessToken(storedToken, refreshedToken);
  return refreshedToken.accessToken;
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

function resolveStoredFirebaseToken() {
  const homeDirectory = os.homedir();
  const candidates = [
    path.join(homeDirectory, ".config", "configstore", "firebase-tools.json"),
    path.join(homeDirectory, "AppData", "Roaming", "configstore", "firebase-tools.json"),
    path.join(homeDirectory, "AppData", "Local", "configstore", "firebase-tools.json"),
  ];

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
      config,
      configPath: candidate,
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

function persistFirebaseAccessToken(storedToken, refreshedToken) {
  if (!storedToken?.configPath || !storedToken?.config) {
    return;
  }

  const nextConfig = {
    ...storedToken.config,
    tokens: {
      ...(storedToken.config.tokens || {}),
      access_token: refreshedToken.accessToken,
      expires_at: refreshedToken.expiresAt,
      expires_in: Math.max(
          Math.round((refreshedToken.expiresAt - Date.now()) / 1000),
          0,
      ),
      scope: refreshedToken.scope || sanitizeText(storedToken.config?.tokens?.scope),
      refresh_token: storedToken.refreshToken || sanitizeText(storedToken.config?.tokens?.refresh_token),
    },
  };
  fs.writeFileSync(storedToken.configPath, `${JSON.stringify(nextConfig, null, "\t")}\n`, "utf8");
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
      `${context.identityToolkitBaseUrl}/accounts:lookup`,
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
      `${context.identityToolkitBaseUrl}/accounts`,
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
        `${context.firestoreBaseUrl}/${collectionName}?${query.toString()}`,
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
      `${context.firestoreBaseUrl}/${relativePath}`,
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
  const response = await fetch(
      `https://firestore.googleapis.com/v1/${documentName}`,
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
      `${context.storageBaseUrl}/o/${encodeURIComponent(objectName)}`,
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
      query.set("prefix", prefix);
    }
    if (pageToken) {
      query.set("pageToken", pageToken);
    }

    const response = await fetch(
        `${context.storageBaseUrl}/o?${query.toString()}`,
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
      `${context.storageBaseUrl}/o/${encodeURIComponent(objectName)}`,
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
  query.set("name", objectName);

  const response = await fetch(
      `https://storage.googleapis.com/upload/storage/v1/b/${encodeURIComponent(context.storageBucket)}/o?${query.toString()}`,
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
      `${context.firestoreBaseUrl}/${relativePath}${query.toString() ? `?${query.toString()}` : ""}`,
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
  const prefix = `projects/${projectId}/databases/(default)/documents/`;
  if (documentName.startsWith(prefix)) {
    return documentName.slice(prefix.length);
  }
  return documentName;
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
