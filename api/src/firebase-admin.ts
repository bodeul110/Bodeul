import {cert, getApps, initializeApp, type App, type AppOptions, type ServiceAccount} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";

import {type FirebaseIdTokenVerifier, type VerifiedFirebaseToken} from "./auth.js";

interface FirebaseServiceAccount {
  readonly project_id: string;
  readonly client_email: string;
  readonly private_key: string;
}

interface FirebaseCredentialServiceAccount {
  readonly projectId: string;
  readonly clientEmail: string;
  readonly privateKey: string;
}

interface FirebaseAdminAppOptions {
  readonly projectId?: string;
  readonly credential?: unknown;
}

type FirebaseAppLike = unknown;

interface FirebaseDecodedToken {
  readonly uid: string;
  readonly email?: string;
  readonly [claim: string]: unknown;
}

interface FirebaseAuthLike {
  verifyIdToken(idToken: string): Promise<FirebaseDecodedToken>;
}

interface FirebaseAdminDependencies {
  readonly cert: (serviceAccount: FirebaseCredentialServiceAccount) => unknown;
  readonly getApps: () => readonly FirebaseAppLike[];
  readonly getAuth: (app?: FirebaseAppLike) => FirebaseAuthLike;
  readonly initializeApp: (options: FirebaseAdminAppOptions) => FirebaseAppLike;
}

type FirebaseAdminAuthConfig =
  | {readonly status: "missing"}
  | {
      readonly status: "configured";
      readonly projectId: string;
      readonly serviceAccount?: FirebaseServiceAccount;
    };

const defaultDependencies: FirebaseAdminDependencies = {
  cert: (serviceAccount) => cert(serviceAccount as ServiceAccount),
  getApps: () => getApps(),
  getAuth: (app) => getAuth(app as App),
  initializeApp: (options) => initializeApp(options as AppOptions),
};

export function createFirebaseAdminVerifier(
    env: NodeJS.ProcessEnv,
    dependencies: FirebaseAdminDependencies = defaultDependencies,
): FirebaseIdTokenVerifier | null {
  const config = getFirebaseAdminAuthConfig(env);
  if (config.status === "missing") {
    return null;
  }

  const app = getOrCreateFirebaseApp(config, dependencies);
  const auth = dependencies.getAuth(app);

  return {
    async verifyIdToken(idToken: string): Promise<VerifiedFirebaseToken> {
      const decoded = await auth.verifyIdToken(idToken);
      return {
        uid: decoded.uid,
        claims: {...decoded},
        ...(typeof decoded.email === "string" ? {email: decoded.email} : {}),
      };
    },
  };
}

function getFirebaseAdminAuthConfig(env: NodeJS.ProcessEnv): FirebaseAdminAuthConfig {
  const serviceAccount = parseServiceAccountJson(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  const projectId = serviceAccount?.project_id || env.FIREBASE_PROJECT_ID?.trim();

  if (!projectId) {
    return {status: "missing"};
  }

  return {
    status: "configured",
    projectId,
    ...(serviceAccount ? {serviceAccount} : {}),
  };
}

function parseServiceAccountJson(rawJson: string | undefined): FirebaseServiceAccount | undefined {
  if (rawJson === undefined || rawJson.trim() === "") {
    return undefined;
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(rawJson);
  } catch {
    throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON은 올바른 JSON이어야 합니다.");
  }

  if (!isRecord(parsed)) {
    throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON은 서비스 계정 객체여야 합니다.");
  }

  const projectId = readRequiredString(parsed, "project_id");
  const clientEmail = readRequiredString(parsed, "client_email");
  const privateKey = readRequiredString(parsed, "private_key").replace(/\\n/g, "\n");

  return {
    project_id: projectId,
    client_email: clientEmail,
    private_key: privateKey,
  };
}

function getOrCreateFirebaseApp(
    config: Extract<FirebaseAdminAuthConfig, {readonly status: "configured"}>,
    dependencies: FirebaseAdminDependencies,
): FirebaseAppLike {
  const existingApp = dependencies.getApps()[0];
  if (existingApp) {
    return existingApp;
  }

  const options: FirebaseAdminAppOptions = {
    projectId: config.projectId,
    ...(config.serviceAccount ? {credential: dependencies.cert(toCredentialServiceAccount(config.serviceAccount))} : {}),
  };

  return dependencies.initializeApp(options);
}

function toCredentialServiceAccount(serviceAccount: FirebaseServiceAccount): FirebaseCredentialServiceAccount {
  return {
    projectId: serviceAccount.project_id,
    clientEmail: serviceAccount.client_email,
    privateKey: serviceAccount.private_key,
  };
}

function readRequiredString(record: Readonly<Record<string, unknown>>, key: string): string {
  const value = record[key];
  if (typeof value !== "string" || value.trim() === "") {
    throw new Error(`FIREBASE_SERVICE_ACCOUNT_JSON에는 ${key} 값이 필요합니다.`);
  }
  return value;
}

function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
