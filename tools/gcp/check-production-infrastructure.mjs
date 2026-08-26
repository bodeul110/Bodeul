#!/usr/bin/env node

import {spawnSync} from "node:child_process";
import {appendFileSync, writeFileSync} from "node:fs";
import {pathToFileURL} from "node:url";

export const STATUS = Object.freeze({
  PASS: "PASS",
  DRIFT: "DRIFT",
  EXPECTED_BLOCKER: "EXPECTED_BLOCKER",
  EXPECTED_ABSENT: "EXPECTED_ABSENT",
  UNAVAILABLE: "UNAVAILABLE",
  ERROR: "ERROR",
});

const FIXED = Object.freeze({
  projectId: "bodeul-prod-110",
  projectNumber: "649312328770",
  region: "asia-northeast1",
  firebaseDisplayName: "BoDeul Production",
  artifactRepository: "bodeul-core-api",
  cloudRunService: "bodeul-core-api",
  workloadIdentityProvider:
    "projects/649312328770/locations/global/workloadIdentityPools/github-actions/providers/bodeul-infra-audit-production",
  serviceAccounts: Object.freeze({
    audit: "bodeul-infra-auditor@bodeul-prod-110.iam.gserviceaccount.com",
    deploy: "bodeul-core-deployer@bodeul-prod-110.iam.gserviceaccount.com",
    runtime: "bodeul-core-runtime@bodeul-prod-110.iam.gserviceaccount.com",
    backup: "bodeul-db-backup@bodeul-prod-110.iam.gserviceaccount.com",
    retention: "bodeul-retention-operator@bodeul-prod-110.iam.gserviceaccount.com",
  }),
  firebaseStorageBucket: "bodeul-prod-110.firebasestorage.app",
  backupBucket: "bodeul-prod-110-db-backups",
});

const EXPECTED_ENV = Object.freeze({
  GCP_PROJECT_ID: FIXED.projectId,
  GCP_PROJECT_NUMBER: FIXED.projectNumber,
  GCP_REGION: FIXED.region,
  AUDIT_WORKLOAD_IDENTITY_PROVIDER: FIXED.workloadIdentityProvider,
  AUDIT_SERVICE_ACCOUNT: FIXED.serviceAccounts.audit,
  CLOUD_RUN_ARTIFACT_REPOSITORY: FIXED.artifactRepository,
  CLOUD_RUN_SERVICE: FIXED.cloudRunService,
  CLOUD_RUN_DEPLOY_SERVICE_ACCOUNT: FIXED.serviceAccounts.deploy,
  CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT: FIXED.serviceAccounts.runtime,
  DB_BACKUP_SERVICE_ACCOUNT: FIXED.serviceAccounts.backup,
  FIREBASE_RETENTION_OPERATOR_SERVICE_ACCOUNT: FIXED.serviceAccounts.retention,
  FIREBASE_PROJECT_ID: FIXED.projectId,
  DB_BACKUP_BUCKET: FIXED.backupBucket,
  FIREBASE_STORAGE_BUCKET: FIXED.firebaseStorageBucket,
});

const STATE_ENV = Object.freeze({
  CLOUD_RUN_EXPECTED_STATE: Object.freeze(["absent", "present"]),
  KAKAO_SECRET_EXPECTED_STATE: Object.freeze(["metadata-only", "enabled"]),
  FIRESTORE_PITR_EXPECTED_STATE: Object.freeze(["deferred", "enabled"]),
  FIREBASE_STORAGE_UBLA_EXPECTED_STATE: Object.freeze(["deferred", "enabled"]),
  APP_CHECK_EXPECTED_STATE: Object.freeze(["unverified", "observe", "enforced"]),
});

const REQUIRED_APIS = Object.freeze([
  "artifactregistry.googleapis.com",
  "cloudfunctions.googleapis.com",
  "firebase.googleapis.com",
  "firebaseappcheck.googleapis.com",
  "firebaserules.googleapis.com",
  "firestore.googleapis.com",
  "iam.googleapis.com",
  "iamcredentials.googleapis.com",
  "identitytoolkit.googleapis.com",
  "monitoring.googleapis.com",
  "run.googleapis.com",
  "secretmanager.googleapis.com",
  "serviceusage.googleapis.com",
  "storage.googleapis.com",
  "sts.googleapis.com",
]);

const AUDIT_ROLE_PERMISSIONS = Object.freeze([
  "resourcemanager.projects.get",
  "resourcemanager.projects.getIamPolicy",
  "serviceusage.services.get",
  "serviceusage.services.use",
  "artifactregistry.repositories.get",
  "artifactregistry.repositories.getIamPolicy",
  "iam.workloadIdentityPools.get",
  "iam.workloadIdentityPoolProviders.get",
  "iam.roles.get",
  "iam.serviceAccounts.get",
  "iam.serviceAccounts.getIamPolicy",
  "iam.serviceAccountKeys.list",
  "secretmanager.secrets.get",
  "secretmanager.secrets.getIamPolicy",
  "secretmanager.versions.get",
  "run.services.get",
  "run.services.getIamPolicy",
  "datastore.databases.getMetadata",
  "storage.buckets.get",
  "storage.buckets.getIamPolicy",
  "firebase.projects.get",
  "firebaseauth.configs.get",
]);

const SECRET_BASELINE = Object.freeze([
  Object.freeze({
    id: "database-url",
    resourceId: "bodeul-core-api-production-db-jdbc-url",
    allowMissingLatest: false,
  }),
  Object.freeze({
    id: "database-user",
    resourceId: "bodeul-core-api-production-db-username",
    allowMissingLatest: false,
  }),
  Object.freeze({
    id: "database-password",
    resourceId: "bodeul-core-api-production-db-password",
    allowMissingLatest: false,
  }),
  Object.freeze({
    id: "external-api-key",
    resourceId: "bodeul-core-api-production-kakao-local-rest-api-key",
    allowMissingLatest: true,
  }),
]);

const SERVICE_ACCOUNT_LABELS = Object.freeze({
  audit: "감사 계정",
  deploy: "배포 계정",
  runtime: "런타임 계정",
  backup: "백업 계정",
  retention: "보존 정책 계정",
});

const AUDIT_PROVIDER_CONDITION = "assertion.repository == 'bodeul110/Bodeul' && assertion.repository_id == '1209358990' && assertion.repository_owner_id == '275679915' && assertion.ref == 'refs/heads/master' && assertion.environment == 'production-infrastructure-audit' && assertion.workflow_ref == 'bodeul110/Bodeul/.github/workflows/production-infrastructure-audit.yml@refs/heads/master' && assertion.event_name == 'workflow_dispatch'";

const COMMON_OPERATION_MAPPING = Object.freeze({
  "google.subject": "assertion.sub",
  "attribute.repository": "assertion.repository",
  "attribute.repository_owner": "assertion.repository_owner",
  "attribute.ref": "assertion.ref",
  "attribute.environment": "assertion.environment",
  "attribute.actor": "assertion.actor",
  "attribute.workflow": "assertion.workflow",
});

const OPERATION_WIF = Object.freeze([
  Object.freeze({
    id: "deploy",
    provider: "bodeul-core-api-production",
    condition: "assertion.repository == 'bodeul110/Bodeul' && assertion.repository_id == '1209358990' && assertion.repository_owner_id == '275679915' && assertion.ref == 'refs/heads/master' && assertion.environment == 'core-api-production' && assertion.workflow_ref == 'bodeul110/Bodeul/.github/workflows/core-api-production-deploy.yml@refs/heads/master' && assertion.event_name == 'workflow_dispatch'",
    mapping: COMMON_OPERATION_MAPPING,
  }),
  Object.freeze({
    id: "backup",
    provider: "bodeul-db-backup-production",
    condition: "assertion.repository == 'bodeul110/Bodeul' && assertion.repository_id == '1209358990' && assertion.repository_owner_id == '275679915' && assertion.ref == 'refs/heads/master' && assertion.environment == 'core-api-migration-production' && assertion.workflow_ref == 'bodeul110/Bodeul/.github/workflows/postgres-production-backup-restore.yml@refs/heads/master' && assertion.event_name == 'workflow_dispatch'",
    mapping: COMMON_OPERATION_MAPPING,
  }),
  Object.freeze({
    id: "retention",
    provider: "bodeul-retention-prod",
    condition: "assertion.repository == 'bodeul110/Bodeul' && assertion.repository_id == '1209358990' && assertion.ref == 'refs/heads/master' && assertion.environment == 'firebase-retention-production' && assertion.workflow_ref == 'bodeul110/Bodeul/.github/workflows/firebase-retention-production.yml@refs/heads/master' && assertion.event_name == 'workflow_dispatch'",
    mapping: Object.freeze({
      ...COMMON_OPERATION_MAPPING,
      "attribute.repository_id": "assertion.repository_id",
      "attribute.workflow_ref": "assertion.workflow_ref",
      "attribute.event_name": "assertion.event_name",
    }),
  }),
]);

class AuditRequestError extends Error {
  constructor(kind, httpStatus = 0) {
    super("원격 조회 실패");
    this.name = "AuditRequestError";
    this.kind = kind;
    this.httpStatus = httpStatus;
  }
}

export function classifyHttpStatus(httpStatus, notFoundStatus = STATUS.DRIFT) {
  if (httpStatus === 401 || httpStatus === 403 || httpStatus === 429) {
    return STATUS.UNAVAILABLE;
  }
  if (httpStatus === 404) {
    return notFoundStatus;
  }
  if (httpStatus >= 500 || httpStatus === 0) {
    return STATUS.ERROR;
  }
  return STATUS.ERROR;
}

export function sanitizeText(value) {
  return String(value ?? "")
    .replace(/Bearer\s+[A-Za-z0-9._~+/=-]+/gi, "Bearer [토큰 숨김]")
    .replace(/\b(?:ya29\.|AIza|eyJ)[A-Za-z0-9._~+/=-]+\b/g, "[토큰 숨김]")
    .replace(/projects\/[^\s/]+\/secrets\/[^\s/]+\/versions\/[^\s/]+/gi, "[secret version 숨김]")
    .replace(/gs:\/\/[^\s)\]}]+/gi, "[버킷 경로 숨김]")
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "[계정 식별자 숨김]")
    .replace(/[\r\n\t]+/g, " ")
    .replace(/\s{2,}/g, " ")
    .trim();
}

export function makeCheck({id, area, status, message}) {
  if (!Object.values(STATUS).includes(status)) {
    throw new Error("지원하지 않는 감사 상태입니다.");
  }
  return Object.freeze({
    id: sanitizeIdentifier(id),
    area: sanitizeText(area),
    status,
    message: sanitizeText(message),
  });
}

export function summarizeChecks(checks, section = "baseline") {
  const statuses = new Set(checks.map((check) => check.status));
  for (const failure of [STATUS.ERROR, STATUS.UNAVAILABLE, STATUS.DRIFT]) {
    if (statuses.has(failure)) {
      return failure;
    }
  }
  if (section === "release") {
    if (statuses.has(STATUS.EXPECTED_BLOCKER)) {
      return STATUS.EXPECTED_BLOCKER;
    }
    if (statuses.has(STATUS.EXPECTED_ABSENT)) {
      return STATUS.EXPECTED_ABSENT;
    }
  }
  return STATUS.PASS;
}

export function buildReport(baselineChecks, releaseChecks, generatedAt = new Date().toISOString()) {
  const safeBaseline = baselineChecks.map((check) => makeCheck(check));
  const safeRelease = releaseChecks.map((check) => makeCheck(check));
  return Object.freeze({
    schemaVersion: 1,
    generatedAt,
    target: "production",
    baseline: Object.freeze({
      status: summarizeChecks(safeBaseline, "baseline"),
      checks: Object.freeze(safeBaseline),
    }),
    releaseReadiness: Object.freeze({
      status: summarizeChecks(safeRelease, "release"),
      checks: Object.freeze(safeRelease),
    }),
  });
}

export function exitCodeForReport(report) {
  const failing = new Set([STATUS.DRIFT, STATUS.UNAVAILABLE, STATUS.ERROR]);
  const checks = [...report.baseline.checks, ...report.releaseReadiness.checks];
  return checks.some((check) => failing.has(check.status)) ? 1 : 0;
}

export function renderMarkdown(report) {
  const lines = [
    "## Production 인프라 읽기 전용 감사",
    "",
    `- Baseline: **${report.baseline.status}**`,
    `- Release readiness: **${report.releaseReadiness.status}**`,
    "",
    "### Baseline drift",
    "",
    "| 검사 | 상태 | 설명 |",
    "| --- | --- | --- |",
  ];
  for (const check of report.baseline.checks) {
    lines.push(markdownRow(check));
  }
  lines.push("", "### Release readiness", "", "| 검사 | 상태 | 설명 |", "| --- | --- | --- |");
  for (const check of report.releaseReadiness.checks) {
    lines.push(markdownRow(check));
  }
  return `${lines.join("\n")}\n`;
}

export function isExpectedAuditProvider(provider) {
  return isExpectedProvider(provider, {
    condition: AUDIT_PROVIDER_CONDITION,
    mapping: {"google.subject": "assertion.sub"},
  });
}

export function isExpectedOperationalProvider(provider, id) {
  const contract = OPERATION_WIF.find((candidate) => candidate.id === id);
  return Boolean(contract) && isExpectedProvider(provider, contract);
}

export function hasExactProjectLocalRoles(policy, member, expectedRoles) {
  const bindings = asArray(policy?.bindings)
    .filter((binding) => asArray(binding.members).includes(member));
  return bindings.every((binding) => !binding.condition) &&
    exactStringSet(bindings.map((binding) => binding.role), expectedRoles);
}

export function isExpectedCloudRunImage(image) {
  const prefix = `${FIXED.region}-docker.pkg.dev/${FIXED.projectId}/${FIXED.artifactRepository}/bodeul-core-api`;
  const escapedPrefix = prefix.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`^${escapedPrefix}(?::[0-9a-f]{40}|@sha256:[0-9a-f]{64})$`).test(String(image ?? ""));
}

function isExpectedProvider(provider, contract) {
  const mapping = provider?.attributeMapping ?? {};
  const allowedAudiences = asArray(provider?.oidc?.allowedAudiences);
  const mappingValid = exactStringMap(mapping, contract.mapping);
  return provider?.state === "ACTIVE" &&
    provider?.disabled !== true &&
    provider?.oidc?.issuerUri === "https://token.actions.githubusercontent.com" &&
    allowedAudiences.length === 0 &&
    mappingValid &&
    normalizeCondition(provider?.attributeCondition) === normalizeCondition(contract.condition);
}

export async function auditProductionInfrastructure({
  env = process.env,
  fetchImpl = globalThis.fetch,
  tokenResolver = resolveAccessToken,
} = {}) {
  const baseline = [checkEnvironmentContract(env)];
  const release = [];
  let token;
  try {
    token = await tokenResolver(env);
    baseline.push(makeCheck({
      id: "authentication.wif",
      area: "인증",
      status: STATUS.PASS,
      message: "단기 자격 증명을 확인했습니다.",
    }));
  } catch {
    baseline.push(makeCheck({
      id: "authentication.wif",
      area: "인증",
      status: STATUS.UNAVAILABLE,
      message: "단기 자격 증명을 확인할 수 없습니다.",
    }));
    return buildReport(baseline, release);
  }

  const client = createRestClient({token, fetchImpl});
  await auditProject(client, baseline);
  await auditApis(client, baseline);
  await auditArtifactRegistry(client, baseline);
  await auditWorkloadIdentity(client, baseline);
  await auditServiceAccounts(client, baseline);
  await auditIam(client, baseline);
  await auditSecrets(client, baseline, release, env);
  await auditCloudRun(client, baseline, release, env);
  await auditFirebase(client, baseline);
  await auditAuth(client, baseline);
  auditAppCheckRelease(release, env);
  await auditFirestore(client, baseline, release, env);
  await auditBuckets(client, baseline, release, env);
  return buildReport(baseline, release);
}

async function auditProject(client, checks) {
  await capture(checks, {
    id: "project.identity",
    area: "프로젝트",
    run: async () => {
      const project = await client.get(
        `https://cloudresourcemanager.googleapis.com/v3/projects/${FIXED.projectId}`,
      );
      const valid = project.projectId === FIXED.projectId &&
        project.name === `projects/${FIXED.projectNumber}` && project.state === "ACTIVE";
      return result(valid, "프로젝트 식별자와 활성 상태가 기준과 일치합니다.", "프로젝트 기준이 다릅니다.");
    },
  });
}

async function auditApis(client, checks) {
  await capture(checks, {
    id: "project.required-apis",
    area: "API",
    run: async () => {
      let missingCount = 0;
      for (const api of REQUIRED_APIS) {
        const service = await client.get(
          `https://serviceusage.googleapis.com/v1/projects/${FIXED.projectNumber}/services/${api}`,
        );
        if (service.state !== "ENABLED") missingCount += 1;
      }
      return result(
        missingCount === 0,
        `필수 API ${REQUIRED_APIS.length}개가 활성 상태입니다.`,
        `필수 API ${missingCount}개가 비활성 또는 누락 상태입니다.`,
      );
    },
  });
}

async function auditArtifactRegistry(client, checks) {
  await capture(checks, {
    id: "artifact.repository",
    area: "Artifact Registry",
    run: async () => {
      const url = `https://artifactregistry.googleapis.com/v1/projects/${FIXED.projectId}/locations/${FIXED.region}/repositories/${FIXED.artifactRepository}`;
      const repository = await client.get(url);
      const policy = await client.get(`${url}:getIamPolicy?options.requestedPolicyVersion=3`);
      const valid = repository.format === "DOCKER" && repository.mode === "STANDARD_REPOSITORY" &&
        exactIamPolicy(policy, [{
          role: "roles/artifactregistry.writer",
          members: [`serviceAccount:${FIXED.serviceAccounts.deploy}`],
        }]);
      return result(valid, "컨테이너 저장소 형식과 리전이 기준과 일치합니다.", "컨테이너 저장소 기준이 다릅니다.");
    },
  });
}

async function auditWorkloadIdentity(client, checks) {
  await capture(checks, {
    id: "iam.audit-provider",
    area: "WIF",
    run: async () => {
      const pool = await client.get(
        `https://iam.googleapis.com/v1/projects/${FIXED.projectNumber}/locations/global/workloadIdentityPools/github-actions`,
      );
      const provider = await client.get(`https://iam.googleapis.com/v1/${FIXED.workloadIdentityProvider}`);
      const valid = pool.state === "ACTIVE" && isExpectedAuditProvider(provider);
      return result(valid, "감사 provider의 상태와 OIDC 제한이 기준과 일치합니다.", "감사 provider의 상태 또는 OIDC 제한이 기준과 다릅니다.");
    },
  });

  for (const contract of OPERATION_WIF) {
    await capture(checks, {
      id: `iam.${contract.id}-provider`,
      area: "WIF",
      run: async () => {
        const provider = await client.get(
          `https://iam.googleapis.com/v1/projects/${FIXED.projectNumber}/locations/global/workloadIdentityPools/github-actions/providers/${contract.provider}`,
        );
        return result(
          isExpectedOperationalProvider(provider, contract.id),
          `${contract.id} provider가 현재 workflow 계약과 일치합니다.`,
          `${contract.id} provider가 현재 workflow 계약과 다릅니다.`,
        );
      },
    });
  }
}

async function auditServiceAccounts(client, checks) {
  for (const [key, account] of Object.entries(FIXED.serviceAccounts)) {
    await capture(checks, {
      id: `iam.service-account-${key}`,
      area: "서비스 계정",
      run: async () => {
        const encoded = encodeURIComponent(account);
        const metadata = await client.get(
          `https://iam.googleapis.com/v1/projects/-/serviceAccounts/${encoded}`,
        );
        const keyList = await client.get(
          `https://iam.googleapis.com/v1/projects/-/serviceAccounts/${encoded}/keys?keyTypes=USER_MANAGED`,
        );
        const keyCount = asArray(keyList.keys).length;
        const valid = metadata.disabled !== true && keyCount === 0;
        return result(
          valid,
          `${SERVICE_ACCOUNT_LABELS[key]}이 활성 상태이며 사용자 관리 키가 없습니다.`,
          `${SERVICE_ACCOUNT_LABELS[key]} 상태 또는 사용자 관리 키 기준이 다릅니다.`,
        );
      },
    });
  }
}

async function auditIam(client, checks) {
  await capture(checks, {
    id: "iam.audit-impersonation",
    area: "IAM",
    run: async () => {
      const encoded = encodeURIComponent(FIXED.serviceAccounts.audit);
      const policy = await client.post(
        `https://iam.googleapis.com/v1/projects/-/serviceAccounts/${encoded}:getIamPolicy`,
        {options: {requestedPolicyVersion: 3}},
      );
      const members = asArray(policy.bindings)
        .filter((binding) => binding.role === "roles/iam.workloadIdentityUser")
        .flatMap((binding) => asArray(binding.members));
      const expected = `principal://iam.googleapis.com/projects/${FIXED.projectNumber}/locations/global/workloadIdentityPools/github-actions/subject/repo:bodeul110/Bodeul:environment:production-infrastructure-audit`;
      const bindings = asArray(policy.bindings);
      const valid = bindings.length === 1 &&
        bindings[0].role === "roles/iam.workloadIdentityUser" &&
        !bindings[0].condition && members.length === 1 && members[0] === expected;
      return result(valid, "감사 계정 impersonation이 단일 exact subject로 제한됩니다.", "감사 계정 impersonation binding이 기준과 다릅니다.");
    },
  });

  const expectedServiceAccountPolicies = [
    {
      id: "deploy",
      account: FIXED.serviceAccounts.deploy,
      bindings: [{
        role: "roles/iam.workloadIdentityUser",
        members: [`principal://iam.googleapis.com/projects/${FIXED.projectNumber}/locations/global/workloadIdentityPools/github-actions/subject/repo:bodeul110/Bodeul:environment:core-api-production`],
      }],
    },
    {
      id: "runtime",
      account: FIXED.serviceAccounts.runtime,
      bindings: [{
        role: "roles/iam.serviceAccountUser",
        members: [`serviceAccount:${FIXED.serviceAccounts.deploy}`],
      }],
    },
    {
      id: "backup",
      account: FIXED.serviceAccounts.backup,
      bindings: [{
        role: "roles/iam.workloadIdentityUser",
        members: [`principal://iam.googleapis.com/projects/${FIXED.projectNumber}/locations/global/workloadIdentityPools/github-actions/subject/repo:bodeul110/Bodeul:environment:core-api-migration-production`],
      }],
    },
    {
      id: "retention",
      account: FIXED.serviceAccounts.retention,
      bindings: [{
        role: "roles/iam.workloadIdentityUser",
        members: [`principal://iam.googleapis.com/projects/${FIXED.projectNumber}/locations/global/workloadIdentityPools/github-actions/subject/repo:bodeul110/Bodeul:environment:firebase-retention-production`],
      }],
    },
  ];
  for (const contract of expectedServiceAccountPolicies) {
    await capture(checks, {
      id: `iam.${contract.id}-service-account-policy`,
      area: "IAM",
      run: async () => {
        const encoded = encodeURIComponent(contract.account);
        const policy = await client.post(
          `https://iam.googleapis.com/v1/projects/-/serviceAccounts/${encoded}:getIamPolicy`,
          {options: {requestedPolicyVersion: 3}},
        );
        return result(
          exactIamPolicy(policy, contract.bindings),
          `${contract.id} 서비스 계정 정책이 현재 workflow 계약과 일치합니다.`,
          `${contract.id} 서비스 계정 정책이 현재 workflow 계약과 다릅니다.`,
        );
      },
    });
  }

  await capture(checks, {
    id: "iam.audit-custom-role",
    area: "IAM",
    run: async () => {
      const role = await client.get(
        `https://iam.googleapis.com/v1/projects/${FIXED.projectId}/roles/bodeulProductionInfraAuditor`,
      );
      const valid = role.stage === "GA" && role.deleted !== true &&
        exactStringSet(role.includedPermissions, AUDIT_ROLE_PERMISSIONS);
      return result(valid, "감사 custom role 권한이 metadata allowlist와 일치합니다.", "감사 custom role 권한이 기준과 다릅니다.");
    },
  });

  await capture(checks, {
    id: "iam.audit-project-role",
    area: "IAM",
    run: async () => {
      const policy = await client.post(
        `https://cloudresourcemanager.googleapis.com/v1/projects/${FIXED.projectId}:getIamPolicy`,
        {options: {requestedPolicyVersion: 3}},
      );
      const member = `serviceAccount:${FIXED.serviceAccounts.audit}`;
      const expectedRole = `projects/${FIXED.projectId}/roles/bodeulProductionInfraAuditor`;
      const valid = hasExactProjectLocalRoles(policy, member, [expectedRole]);
      return result(valid, "감사 계정의 project-local 역할이 전용 metadata role과 일치합니다.", "감사 계정의 project-local 역할이 기준과 다릅니다.");
    },
  });

  await capture(checks, {
    id: "iam.operational-project-roles",
    area: "IAM",
    run: async () => {
      const policy = await client.post(
        `https://cloudresourcemanager.googleapis.com/v1/projects/${FIXED.projectId}:getIamPolicy`,
        {options: {requestedPolicyVersion: 3}},
      );
      const expected = new Map([
        [FIXED.serviceAccounts.deploy, ["roles/run.developer"]],
        [FIXED.serviceAccounts.runtime, []],
        [FIXED.serviceAccounts.backup, []],
        [FIXED.serviceAccounts.retention, ["roles/datastore.viewer", "roles/serviceusage.serviceUsageConsumer"]],
      ]);
      const valid = [...expected.entries()].every(([account, roles]) => {
        const member = `serviceAccount:${account}`;
        return hasExactProjectLocalRoles(policy, member, roles);
      });
      return result(valid, "운영 서비스 계정의 project-local 역할이 기준과 일치합니다.", "운영 서비스 계정의 project-local 역할이 기준과 다릅니다.");
    },
  });

  await capture(checks, {
    id: "iam.project-public-members",
    area: "IAM",
    run: async () => {
      const policy = await client.post(
        `https://cloudresourcemanager.googleapis.com/v1/projects/${FIXED.projectId}:getIamPolicy`,
        {options: {requestedPolicyVersion: 3}},
      );
      const members = asArray(policy.bindings).flatMap((binding) => asArray(binding.members));
      const publicCount = members.filter((member) => member === "allUsers" || member === "allAuthenticatedUsers").length;
      const directUserCount = members.filter((member) => String(member).startsWith("user:")).length;
      const valid = publicCount === 0 && directUserCount === 0;
      return result(valid, "project-local IAM에 공개 또는 개인 직접 binding이 없습니다.", "project-local IAM에 공개 또는 개인 직접 binding이 있습니다.");
    },
  });
}

async function auditSecrets(client, checks, releaseChecks, env) {
  for (const secret of SECRET_BASELINE) {
    let latestMissing = false;
    await capture(checks, {
      id: `secret.${secret.id}`,
      area: "Secret Manager",
      run: async () => {
        const baseUrl = `https://secretmanager.googleapis.com/v1/projects/${FIXED.projectId}/secrets/${secret.resourceId}`;
        await client.get(baseUrl);
        const policy = await client.get(`${baseUrl}:getIamPolicy?options.requestedPolicyVersion=3`);
        const accessValid = exactIamPolicy(policy, [{
          role: "roles/secretmanager.secretAccessor",
          members: [`serviceAccount:${FIXED.serviceAccounts.runtime}`],
        }]);
        try {
          const latest = await client.get(`${baseUrl}/versions/latest`);
          const expectedState = !secret.allowMissingLatest || env.KAKAO_SECRET_EXPECTED_STATE === "enabled";
          const valid = accessValid && latest.state === "ENABLED" && expectedState;
          return result(valid, "Secret 메타데이터와 최신 활성 상태를 확인했습니다.", "Secret 최신 상태가 활성 기준과 다릅니다.");
        } catch (error) {
          if (error instanceof AuditRequestError && error.httpStatus === 404 && secret.allowMissingLatest) {
            latestMissing = true;
            const expected = env.KAKAO_SECRET_EXPECTED_STATE === "metadata-only";
            return result(accessValid && expected, "Secret 메타데이터는 존재하며 최신 버전은 현재 미등록 상태입니다.", "Secret 상태 또는 runtime 접근 정책이 기대 단계와 다릅니다.");
          }
          throw error;
        }
      },
    });
    if (secret.allowMissingLatest) {
      const secretCheck = checks.at(-1);
      const status = secretCheck.status === STATUS.PASS ?
        (latestMissing ? STATUS.EXPECTED_BLOCKER : STATUS.PASS) : secretCheck.status;
      releaseChecks.push(makeCheck({
        id: "release.external-api-secret",
        area: "출시 준비",
        status,
        message: secretCheck.status !== STATUS.PASS ?
          "외부 API 운영 Secret 준비 상태를 판정할 수 없습니다." :
          (latestMissing ? "외부 API 운영 Secret 등록이 필요합니다." : "외부 API 운영 Secret이 준비됐습니다."),
      }));
    }
  }
}

async function auditCloudRun(client, checks, releaseChecks, configuration) {
  const url = `https://run.googleapis.com/v2/projects/${FIXED.projectId}/locations/${FIXED.region}/services/${FIXED.cloudRunService}`;
  try {
    const service = await client.get(url);
    const template = service.template ?? {};
    const containers = asArray(template.containers);
    const primaryContainer = containers[0] ?? {};
    const runtimeEnv = new Map(asArray(primaryContainer.env).map((entry) => [entry.name, entry]));
    const limits = primaryContainer?.resources?.limits ?? {};
    const expectedSecretBindings = {
      CORE_DB_JDBC_URL: "bodeul-core-api-production-db-jdbc-url",
      CORE_DB_USERNAME: "bodeul-core-api-production-db-username",
      CORE_DB_PASSWORD: "bodeul-core-api-production-db-password",
      KAKAO_LOCAL_REST_API_KEY: "bodeul-core-api-production-kakao-local-rest-api-key",
    };
    let secretRefsValid = true;
    for (const [name, expectedSecret] of Object.entries(expectedSecretBindings)) {
      const ref = runtimeEnv.get(name)?.valueSource?.secretKeyRef;
      const actualSecret = String(ref?.secret ?? "").split("/").at(-1);
      const version = String(ref?.version ?? "");
      if (actualSecret !== expectedSecret || !/^\d+$/.test(version)) {
        secretRefsValid = false;
        continue;
      }
      const metadata = await client.get(
        `https://secretmanager.googleapis.com/v1/projects/${FIXED.projectId}/secrets/${expectedSecret}/versions/${version}`,
      );
      if (metadata.state !== "ENABLED") secretRefsValid = false;
    }
    const expectedEnvNames = [
      "SPRING_PROFILES_ACTIVE",
      "CORE_DB_POOL_MAX",
      "FIREBASE_PROJECT_ID",
      "FIREBASE_PROJECT_NUMBER",
      "BODEUL_APP_CHECK_MODE",
      ...Object.keys(expectedSecretBindings),
    ];
    const envNamesValid = exactStringSet([...runtimeEnv.keys()], expectedEnvNames);
    const vpc = template.vpcAccess ?? {};
    const dynamicOutbound = !vpc.connector && asArray(vpc.networkInterfaces).length === 0;
    const labels = service.labels ?? {};
    const resources = primaryContainer.resources ?? {};
    const ports = asArray(primaryContainer.ports);
    const latestTraffic = asArray(service.traffic).some((target) =>
      target.type === "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST" && Number(target.percent) === 100);
    const revisionReady = service.reconciling === false &&
      service.terminalCondition?.state === "CONDITION_SUCCEEDED" &&
      Boolean(service.latestReadyRevision) &&
      service.latestReadyRevision === service.latestCreatedRevision &&
      String(service.observedGeneration ?? "") === String(service.generation ?? "");
    const expectedAppCheckMode = configuration.APP_CHECK_EXPECTED_STATE === "enforced" ? "enforce" : "observe";
    const valid = configuration.CLOUD_RUN_EXPECTED_STATE === "present" &&
      containers.length === 1 && isExpectedCloudRunImage(primaryContainer.image) && revisionReady &&
      template.serviceAccount === FIXED.serviceAccounts.runtime &&
      Number(template?.scaling?.minInstanceCount ?? 0) === 0 &&
      Number(template?.scaling?.maxInstanceCount ?? 0) === 2 &&
      String(limits.cpu ?? "") === "1" && String(limits.memory ?? "") === "1Gi" &&
      resources.startupCpuBoost === true && Number(template.maxInstanceRequestConcurrency) === 8 &&
      ports.some((port) => Number(port.containerPort) === 8080) &&
      template.timeout === "60s" && template.executionEnvironment === "EXECUTION_ENVIRONMENT_GEN2" &&
      service.ingress === "INGRESS_TRAFFIC_ALL" && latestTraffic &&
      labels.environment === "production" && labels.component === "core-api" && labels["kakao-egress"] === "dynamic" &&
      runtimeEnv.get("SPRING_PROFILES_ACTIVE")?.value === "production" && runtimeEnv.get("CORE_DB_POOL_MAX")?.value === "2" &&
      runtimeEnv.get("FIREBASE_PROJECT_ID")?.value === FIXED.projectId &&
      runtimeEnv.get("FIREBASE_PROJECT_NUMBER")?.value === FIXED.projectNumber &&
      runtimeEnv.get("BODEUL_APP_CHECK_MODE")?.value === expectedAppCheckMode && envNamesValid && secretRefsValid && dynamicOutbound;
    checks.push(makeCheck({
      id: "cloud-run.configuration",
      area: "Cloud Run",
      status: valid ? STATUS.PASS : STATUS.DRIFT,
      message: valid ? "서비스 런타임 설정이 production 기준과 일치합니다." : "서비스 런타임 설정이 production 기준과 다릅니다.",
    }));
    releaseChecks.push(makeCheck({
      id: "release.cloud-run-service",
      area: "출시 준비",
      status: valid ? STATUS.PASS : STATUS.DRIFT,
      message: valid ? "Core API production 서비스가 준비됐습니다." : "Core API production 서비스 설정을 바로잡아야 합니다.",
    }));
    try {
      const policy = await client.get(`${url}:getIamPolicy?options.requestedPolicyVersion=3`);
      const publiclyInvokable = exactIamPolicy(policy, [{
        role: "roles/run.invoker",
        members: ["allUsers"],
      }]);
      releaseChecks.push(makeCheck({
        id: "release.cloud-run-invoker",
        area: "출시 준비",
        status: publiclyInvokable ? STATUS.PASS : STATUS.EXPECTED_BLOCKER,
        message: publiclyInvokable ?
          "Core API 공개 호출 IAM이 기준과 일치합니다." :
          "첫 배포 뒤 공개 호출 IAM을 최소 권한 기준으로 구성해야 합니다.",
      }));
    } catch (error) {
      releaseChecks.push(checkFromError("release.cloud-run-invoker", "출시 준비", error));
    }
  } catch (error) {
    if (error instanceof AuditRequestError && error.httpStatus === 404) {
      const expectedAbsent = configuration.CLOUD_RUN_EXPECTED_STATE === "absent";
      checks.push(makeCheck({
        id: "cloud-run.configuration",
        area: "Cloud Run",
        status: expectedAbsent ? STATUS.PASS : STATUS.DRIFT,
        message: expectedAbsent ? "첫 승인 배포 전 서비스 미생성 상태가 현재 baseline과 일치합니다." : "생성이 필요한 Core API production 서비스가 없습니다.",
      }));
      releaseChecks.push(makeCheck({
        id: "release.cloud-run-service",
        area: "출시 준비",
        status: expectedAbsent ? STATUS.EXPECTED_ABSENT : STATUS.DRIFT,
        message: "첫 승인 배포로 Core API production 서비스를 생성해야 합니다.",
      }));
      return;
    }
    checks.push(checkFromError("cloud-run.configuration", "Cloud Run", error));
    releaseChecks.push(checkFromError("release.cloud-run-service", "출시 준비", error));
  }
}

async function auditFirebase(client, checks) {
  await capture(checks, {
    id: "firebase.project",
    area: "Firebase",
    run: async () => {
      const project = await client.get(`https://firebase.googleapis.com/v1beta1/projects/${FIXED.projectId}`);
      const valid = project.projectId === FIXED.projectId &&
        String(project.projectNumber) === FIXED.projectNumber &&
        project.displayName === FIXED.firebaseDisplayName;
      return result(valid, "Firebase 프로젝트 식별자가 기준과 일치합니다.", "Firebase 프로젝트 식별자가 기준과 다릅니다.");
    },
  });
}

async function auditAuth(client, checks) {
  await capture(checks, {
    id: "firebase.auth-config",
    area: "Firebase Auth",
    run: async () => {
      const config = await client.get(`https://identitytoolkit.googleapis.com/admin/v2/projects/${FIXED.projectId}/config`);
      const email = config?.signIn?.email ?? {};
      const privacy = config?.emailPrivacyConfig ?? {};
      const valid = email.enabled === true && email.passwordRequired === true &&
        privacy.enableImprovedEmailPrivacy === true;
      return result(valid, "이메일 인증과 이메일 열거 보호 설정이 기준과 일치합니다.", "Firebase Auth 설정이 기준과 다릅니다.");
    },
  });
}

function auditAppCheckRelease(releaseChecks, env) {
  const unverified = env.APP_CHECK_EXPECTED_STATE === "unverified";
  releaseChecks.push(makeCheck({
    id: "release.app-check-enforcement",
    area: "출시 준비",
    status: unverified ? STATUS.EXPECTED_BLOCKER : STATUS.UNAVAILABLE,
    message: unverified ?
      "Release App Check provider와 enforcement는 별도 실검증이 필요합니다." :
      "App Check enforcement 기대 상태를 검증하는 API 점검이 아직 구현되지 않았습니다.",
  }));
}

async function auditFirestore(client, checks, releaseChecks, env) {
  let pitrEnabled = false;
  await capture(checks, {
    id: "firestore.database",
    area: "Firestore",
    run: async () => {
      const database = await client.get(
        `https://firestore.googleapis.com/v1/projects/${FIXED.projectId}/databases/(default)`,
      );
      pitrEnabled = database.pointInTimeRecoveryEnablement === "POINT_IN_TIME_RECOVERY_ENABLED";
      const pitrStateValid = (pitrEnabled && env.FIRESTORE_PITR_EXPECTED_STATE === "enabled") ||
        (!pitrEnabled && env.FIRESTORE_PITR_EXPECTED_STATE === "deferred");
      const valid = database.locationId === FIXED.region && database.type === "FIRESTORE_NATIVE" &&
        database.deleteProtectionState === "DELETE_PROTECTION_ENABLED" && pitrStateValid;
      return result(valid, "데이터베이스 리전, 모드와 삭제 방지가 기준과 일치합니다.", "Firestore 메타데이터가 기준과 다릅니다.");
    },
  });
  const firestoreCheck = checks.at(-1);
  releaseChecks.push(makeCheck({
    id: "release.firestore-pitr",
    area: "출시 준비",
    status: firestoreCheck.status === STATUS.PASS ?
      (pitrEnabled ? STATUS.PASS : STATUS.EXPECTED_BLOCKER) : firestoreCheck.status,
    message: firestoreCheck.status !== STATUS.PASS ?
      "Firestore 시점 복구 준비 상태를 판정할 수 없습니다." :
      (pitrEnabled ? "Firestore 시점 복구가 활성화됐습니다." : "출시 전 Firestore 시점 복구 정책을 확정하고 적용해야 합니다."),
  }));
}

async function auditBuckets(client, checks, releaseChecks, env) {
  let firebaseUniformAccess = false;
  await auditBucket(client, checks, {
    id: "storage.firebase-bucket",
    bucket: FIXED.firebaseStorageBucket,
    backup: false,
    uniformExpectedState: env.FIREBASE_STORAGE_UBLA_EXPECTED_STATE,
    onUniformState: (enabled) => {
      firebaseUniformAccess = enabled;
    },
  });
  const firebaseBucketCheck = checks.at(-1);
  releaseChecks.push(makeCheck({
    id: "release.firebase-storage-ubla",
    area: "출시 준비",
    status: firebaseBucketCheck.status === STATUS.PASS ?
      (firebaseUniformAccess ? STATUS.PASS : STATUS.EXPECTED_BLOCKER) : firebaseBucketCheck.status,
    message: firebaseBucketCheck.status !== STATUS.PASS ?
      "Firebase Storage UBLA 준비 상태를 판정할 수 없습니다." :
      (firebaseUniformAccess ? "Firebase Storage UBLA가 활성화됐습니다." : "개발 버킷 실검증 뒤 Firebase Storage UBLA를 활성화해야 합니다."),
  }));
  await auditBucket(client, checks, {
    id: "storage.backup-bucket",
    bucket: FIXED.backupBucket,
    backup: true,
    uniformExpectedState: "enabled",
    onUniformState: () => {},
  });
}

async function auditBucket(client, checks, {id, bucket, backup, uniformExpectedState, onUniformState}) {
  await capture(checks, {
    id,
    area: "Cloud Storage",
    run: async () => {
      const metadata = await client.get(`https://storage.googleapis.com/storage/v1/b/${encodeURIComponent(bucket)}`);
      const policy = await client.get(
        `https://storage.googleapis.com/storage/v1/b/${encodeURIComponent(bucket)}/iam?optionsRequestedPolicyVersion=3`,
      );
      const uniform = metadata?.iamConfiguration?.uniformBucketLevelAccess?.enabled === true;
      onUniformState(uniform);
      const publicBlocked = metadata?.iamConfiguration?.publicAccessPrevention === "enforced";
      const commonBindings = [
        {
          role: "roles/storage.legacyBucketOwner",
          members: [`projectEditor:${FIXED.projectId}`, `projectOwner:${FIXED.projectId}`],
        },
        {role: "roles/storage.legacyBucketReader", members: [`projectViewer:${FIXED.projectId}`]},
      ];
      const expectedBindings = backup ? [
        ...commonBindings,
        {
          role: "roles/storage.legacyObjectOwner",
          members: [`projectEditor:${FIXED.projectId}`, `projectOwner:${FIXED.projectId}`],
        },
        {role: "roles/storage.legacyObjectReader", members: [`projectViewer:${FIXED.projectId}`]},
        {role: "roles/storage.objectCreator", members: [`serviceAccount:${FIXED.serviceAccounts.backup}`]},
        {
          role: "roles/storage.objectViewer",
          members: [
            `serviceAccount:${FIXED.serviceAccounts.backup}`,
            `serviceAccount:${FIXED.serviceAccounts.retention}`,
          ],
        },
      ] : [
        ...commonBindings,
        {role: "roles/storage.objectUser", members: [`serviceAccount:${FIXED.serviceAccounts.runtime}`]},
        {role: "roles/storage.objectViewer", members: [`serviceAccount:${FIXED.serviceAccounts.retention}`]},
      ];
      const uniformStateValid = (uniform && uniformExpectedState === "enabled") ||
        (!uniform && uniformExpectedState === "deferred");
      let valid = String(metadata.location ?? "").toLowerCase() === FIXED.region && uniformStateValid && publicBlocked &&
        exactIamPolicy(policy, expectedBindings);
      if (backup) {
        valid = valid && Number(metadata?.retentionPolicy?.retentionPeriod ?? 0) === 2_419_200 &&
          Number(metadata?.softDeletePolicy?.retentionDurationSeconds ?? 0) === 604_800;
      }
      return result(valid, "버킷 메타데이터가 비공개 production 기준과 일치합니다.", "버킷 메타데이터가 production 기준과 다릅니다.");
    },
  });
}

function checkEnvironmentContract(env) {
  const fixedMismatches = Object.entries(EXPECTED_ENV)
    .filter(([name, expected]) => String(env[name] ?? "").trim() !== expected).length;
  const stateMismatches = Object.entries(STATE_ENV)
    .filter(([name, allowed]) => !allowed.includes(String(env[name] ?? "").trim())).length;
  const mismatches = fixedMismatches + stateMismatches;
  return makeCheck({
    id: "configuration.fixed-target",
    area: "실행 경계",
    status: mismatches === 0 ? STATUS.PASS : STATUS.DRIFT,
    message: mismatches === 0 ?
      "workflow 대상 값이 고정 production 기준과 일치합니다." :
      `workflow 대상 값 ${mismatches}개가 고정 production 기준과 다릅니다.`,
  });
}

function createRestClient({token, fetchImpl}) {
  if (typeof fetchImpl !== "function") {
    throw new Error("fetch 구현이 필요합니다.");
  }
  const request = async (url, method, body) => {
    let response;
    try {
      response = await fetchImpl(url, {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
          "x-goog-user-project": FIXED.projectId,
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(30_000),
      });
    } catch {
      throw new AuditRequestError(STATUS.ERROR);
    }
    const classified = classifyHttpStatus(response.status);
    if (!response.ok) {
      throw new AuditRequestError(classified, response.status);
    }
    try {
      return await response.json();
    } catch {
      throw new AuditRequestError(STATUS.ERROR);
    }
  };
  return Object.freeze({
    get: (url) => request(url, "GET"),
    post: (url, body) => request(url, "POST", body),
  });
}

async function capture(checks, {id, area, run}) {
  try {
    const outcome = await run();
    checks.push(makeCheck({id, area, status: outcome.status, message: outcome.message}));
  } catch (error) {
    checks.push(checkFromError(id, area, error));
  }
}

function checkFromError(id, area, error) {
  if (error instanceof AuditRequestError) {
    if (error.kind === STATUS.UNAVAILABLE) {
      return makeCheck({id, area, status: STATUS.UNAVAILABLE, message: "조회 권한이 없거나 인증이 거부됐습니다."});
    }
    if (error.httpStatus === 404) {
      return makeCheck({id, area, status: STATUS.DRIFT, message: "필수 리소스를 찾지 못했습니다."});
    }
  }
  return makeCheck({id, area, status: STATUS.ERROR, message: "조회 또는 응답 해석 중 오류가 발생했습니다."});
}

function result(valid, passMessage, driftMessage) {
  return {
    status: valid ? STATUS.PASS : STATUS.DRIFT,
    message: valid ? passMessage : driftMessage,
  };
}

function normalizeCondition(value) {
  return String(value ?? "").replace(/"/g, "'").replace(/\s+/g, "");
}

function sanitizeIdentifier(value) {
  const safe = String(value ?? "").trim();
  if (!/^[a-z0-9][a-z0-9._-]{0,79}$/i.test(safe)) {
    throw new Error("감사 검사 ID 형식이 올바르지 않습니다.");
  }
  return safe;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function exactStringSet(actual, expected) {
  const left = [...new Set(asArray(actual))].sort();
  const right = [...new Set(asArray(expected))].sort();
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function exactStringMap(actual, expected) {
  const actualEntries = Object.entries(actual ?? {}).map(([key, value]) => `${key}=${value}`);
  const expectedEntries = Object.entries(expected ?? {}).map(([key, value]) => `${key}=${value}`);
  return exactStringSet(actualEntries, expectedEntries);
}

function exactIamPolicy(policy, expectedBindings) {
  const actual = asArray(policy?.bindings).map((binding) => ({
    role: String(binding.role ?? ""),
    members: asArray(binding.members),
    conditioned: Boolean(binding.condition),
  }));
  if (actual.some((binding) => binding.conditioned) || actual.length !== expectedBindings.length) {
    return false;
  }
  const byRole = new Map(actual.map((binding) => [binding.role, binding.members]));
  if (byRole.size !== actual.length) return false;
  return expectedBindings.every((binding) =>
    byRole.has(binding.role) && exactStringSet(byRole.get(binding.role), binding.members));
}

function markdownRow(check) {
  const id = sanitizeText(check.id).replace(/\|/g, "\\|");
  const message = sanitizeText(check.message).replace(/\|/g, "\\|");
  return `| \`${id}\` | **${check.status}** | ${message} |`;
}

async function resolveAccessToken(env) {
  const supplied = String(env.GOOGLE_OAUTH_ACCESS_TOKEN ?? env.GCLOUD_ACCESS_TOKEN ?? "").trim();
  if (supplied) {
    return supplied;
  }
  const command = process.platform === "win32" ? "gcloud.cmd" : "gcloud";
  const result = spawnSync(command, ["auth", "print-access-token", "--quiet"], {
    encoding: "utf8",
    windowsHide: true,
    timeout: 30_000,
    maxBuffer: 256 * 1024,
  });
  const token = String(result.stdout ?? "").trim();
  if (result.error || result.status !== 0 || token.length < 20 || /\s/.test(token)) {
    throw new Error("단기 자격 증명을 확인할 수 없습니다.");
  }
  return token;
}

function parseArguments(args) {
  const options = {summaryPath: "", jsonPath: "", stdout: "markdown", help: false};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--summary") {
      options.summaryPath = requireArgument(args, ++index, arg);
    } else if (arg === "--json") {
      options.jsonPath = requireArgument(args, ++index, arg);
    } else if (arg === "--stdout") {
      options.stdout = requireArgument(args, ++index, arg);
      if (!["markdown", "json", "none"].includes(options.stdout)) {
        throw new Error("--stdout는 markdown, json, none 중 하나여야 합니다.");
      }
    } else if (arg === "--help" || arg === "-h") {
      options.help = true;
    } else {
      throw new Error("지원하지 않는 인자입니다.");
    }
  }
  return options;
}

function requireArgument(args, index, name) {
  const value = String(args[index] ?? "").trim();
  if (!value || value.startsWith("--")) {
    throw new Error(`${name} 값이 필요합니다.`);
  }
  return value;
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  if (options.help) {
    console.log("사용법: node tools/gcp/check-production-infrastructure.mjs [--summary <path>] [--json <path>] [--stdout markdown|json|none]");
    return;
  }
  const report = await auditProductionInfrastructure();
  const markdown = renderMarkdown(report);
  const json = `${JSON.stringify(report, null, 2)}\n`;
  if (options.summaryPath) appendFileSync(options.summaryPath, markdown, "utf8");
  if (options.jsonPath) writeFileSync(options.jsonPath, json, "utf8");
  if (options.stdout === "json") process.stdout.write(json);
  if (options.stdout === "markdown") process.stdout.write(markdown);
  process.exitCode = exitCodeForReport(report);
}

const invokedPath = process.argv[1] ? pathToFileURL(process.argv[1]).href : "";
if (import.meta.url === invokedPath) {
  main().catch(() => {
    console.error("Production 인프라 감사 실행 중 안전하게 처리할 수 없는 오류가 발생했습니다.");
    process.exitCode = 1;
  });
}
