import assert from "node:assert/strict";
import test from "node:test";

import {
  STATUS,
  auditProductionInfrastructure,
  buildReport,
  classifyHttpStatus,
  exitCodeForReport,
  hasExactProjectLocalRoles,
  isExpectedAuditProvider,
  isExpectedCloudRunImage,
  makeCheck,
  renderMarkdown,
  sanitizeText,
  summarizeChecks,
} from "../check-production-infrastructure.mjs";

const check = (status, message = "검사 결과") => makeCheck({
  id: `test.${status.toLowerCase()}`,
  area: "테스트",
  status,
  message,
});

const validEnvironment = Object.freeze({
  GCP_PROJECT_ID: "bodeul-prod-110",
  GCP_PROJECT_NUMBER: "649312328770",
  GCP_REGION: "asia-northeast1",
  AUDIT_WORKLOAD_IDENTITY_PROVIDER: "projects/649312328770/locations/global/workloadIdentityPools/github-actions/providers/bodeul-infra-audit-production",
  AUDIT_SERVICE_ACCOUNT: "bodeul-infra-auditor@bodeul-prod-110.iam.gserviceaccount.com",
  CLOUD_RUN_ARTIFACT_REPOSITORY: "bodeul-core-api",
  CLOUD_RUN_SERVICE: "bodeul-core-api",
  CLOUD_RUN_DEPLOY_SERVICE_ACCOUNT: "bodeul-core-deployer@bodeul-prod-110.iam.gserviceaccount.com",
  CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT: "bodeul-core-runtime@bodeul-prod-110.iam.gserviceaccount.com",
  DB_BACKUP_SERVICE_ACCOUNT: "bodeul-db-backup@bodeul-prod-110.iam.gserviceaccount.com",
  FIREBASE_RETENTION_OPERATOR_SERVICE_ACCOUNT: "bodeul-retention-operator@bodeul-prod-110.iam.gserviceaccount.com",
  FIREBASE_PROJECT_ID: "bodeul-prod-110",
  DB_BACKUP_BUCKET: "bodeul-prod-110-db-backups",
  FIREBASE_STORAGE_BUCKET: "bodeul-prod-110.firebasestorage.app",
  CLOUD_RUN_EXPECTED_STATE: "absent",
  KAKAO_SECRET_EXPECTED_STATE: "metadata-only",
  FIRESTORE_PITR_EXPECTED_STATE: "deferred",
  FIREBASE_STORAGE_UBLA_EXPECTED_STATE: "deferred",
  APP_CHECK_EXPECTED_STATE: "unverified",
});

test("HTTP 상태를 감사 상태로 분류한다", () => {
  assert.equal(classifyHttpStatus(401), STATUS.UNAVAILABLE);
  assert.equal(classifyHttpStatus(403), STATUS.UNAVAILABLE);
  assert.equal(classifyHttpStatus(429), STATUS.UNAVAILABLE);
  assert.equal(classifyHttpStatus(404), STATUS.DRIFT);
  assert.equal(classifyHttpStatus(404, STATUS.EXPECTED_ABSENT), STATUS.EXPECTED_ABSENT);
  assert.equal(classifyHttpStatus(500), STATUS.ERROR);
  assert.equal(classifyHttpStatus(0), STATUS.ERROR);
});

test("baseline과 release expected 상태를 분리한다", () => {
  assert.equal(summarizeChecks([check(STATUS.PASS)], "baseline"), STATUS.PASS);
  assert.equal(
    summarizeChecks([check(STATUS.PASS), check(STATUS.EXPECTED_BLOCKER)], "release"),
    STATUS.EXPECTED_BLOCKER,
  );
  assert.equal(
    summarizeChecks([check(STATUS.PASS), check(STATUS.EXPECTED_ABSENT)], "release"),
    STATUS.EXPECTED_ABSENT,
  );
  assert.equal(
    summarizeChecks([check(STATUS.EXPECTED_ABSENT), check(STATUS.DRIFT)], "release"),
    STATUS.DRIFT,
  );
});

test("민감 식별자와 자격 증명을 안전한 문구로 치환한다", () => {
  const raw = [
    "Bearer secret-token-for-test",
    "operator@example.com",
    "gs://private-bucket/path/report.json",
    "projects/p/secrets/database-password/versions/17",
  ].join(" ");
  const sanitized = sanitizeText(raw);
  assert.doesNotMatch(sanitized, /secret-token-for-test/);
  assert.doesNotMatch(sanitized, /operator@example\.com/);
  assert.doesNotMatch(sanitized, /gs:\/\//);
  assert.doesNotMatch(sanitized, /versions\/17/);
  assert.match(sanitized, /토큰 숨김/);
  assert.match(sanitized, /계정 식별자 숨김/);
  assert.match(sanitized, /버킷 경로 숨김/);
  assert.match(sanitized, /secret version 숨김/);
});

test("Markdown과 JSON 결과에 원래 민감값을 남기지 않는다", () => {
  const report = buildReport([
    check(STATUS.PASS, "svc@example.com gs://private-bucket/a projects/p/secrets/s/versions/2"),
  ], [
    check(STATUS.EXPECTED_BLOCKER, "Bearer test-token-value"),
  ], "2026-08-26T00:00:00.000Z");
  const serialized = `${renderMarkdown(report)}\n${JSON.stringify(report)}`;
  assert.doesNotMatch(serialized, /svc@example\.com/);
  assert.doesNotMatch(serialized, /private-bucket/);
  assert.doesNotMatch(serialized, /versions\/2/);
  assert.doesNotMatch(serialized, /test-token-value/);
});

test("expected 상태만 있으면 성공하고 drift, unavailable, error는 실패한다", () => {
  const expectedOnly = buildReport(
    [check(STATUS.PASS)],
    [check(STATUS.EXPECTED_BLOCKER), check(STATUS.EXPECTED_ABSENT)],
  );
  assert.equal(exitCodeForReport(expectedOnly), 0);

  for (const status of [STATUS.DRIFT, STATUS.UNAVAILABLE, STATUS.ERROR]) {
    const report = buildReport([check(status)], []);
    assert.equal(exitCodeForReport(report), 1);
  }
});

test("WIF provider 조건은 허용 조건 전체가 정확히 일치해야 한다", () => {
  const condition = "assertion.repository == 'bodeul110/Bodeul' && assertion.repository_id == '1209358990' && assertion.repository_owner_id == '275679915' && assertion.ref == 'refs/heads/master' && assertion.environment == 'production-infrastructure-audit' && assertion.workflow_ref == 'bodeul110/Bodeul/.github/workflows/production-infrastructure-audit.yml@refs/heads/master' && assertion.event_name == 'workflow_dispatch'";
  const provider = {
    state: "ACTIVE",
    oidc: {issuerUri: "https://token.actions.githubusercontent.com"},
    attributeMapping: {"google.subject": "assertion.sub"},
    attributeCondition: condition,
  };
  assert.equal(isExpectedAuditProvider(provider), true);
  assert.equal(isExpectedAuditProvider({...provider, attributeCondition: `${condition} || true`}), false);
  assert.equal(isExpectedAuditProvider({...provider, attributeMapping: {...provider.attributeMapping, "attribute.ref": "assertion.ref"}}), false);
  assert.equal(isExpectedAuditProvider({...provider, oidc: {...provider.oidc, allowedAudiences: ["alternate"]}}), false);
  assert.equal(isExpectedAuditProvider({...provider, disabled: true}), false);
  assert.equal(isExpectedAuditProvider({...provider, state: "DELETED"}), false);
  assert.equal(isExpectedAuditProvider({...provider, oidc: {...provider.oidc, issuerUri: "https://example.com"}}), false);
});

test("project-local 역할은 조건 없는 정확한 집합만 허용한다", () => {
  const member = "serviceAccount:auditor@example.iam.gserviceaccount.com";
  const expectedRole = "projects/example/roles/auditor";
  const policy = {bindings: [{role: expectedRole, members: [member]}]};
  assert.equal(hasExactProjectLocalRoles(policy, member, [expectedRole]), true);
  assert.equal(hasExactProjectLocalRoles({bindings: [{...policy.bindings[0], condition: {title: "limited"}}]}, member, [expectedRole]), false);
  assert.equal(hasExactProjectLocalRoles({bindings: [...policy.bindings, {role: "roles/viewer", members: [member]}]}, member, [expectedRole]), false);
  assert.equal(hasExactProjectLocalRoles({bindings: []}, member, []), true);
});

test("Cloud Run 이미지는 production 저장소의 불변 식별자만 허용한다", () => {
  const prefix = "asia-northeast1-docker.pkg.dev/bodeul-prod-110/bodeul-core-api/bodeul-core-api";
  assert.equal(isExpectedCloudRunImage(`${prefix}:${"a".repeat(40)}`), true);
  assert.equal(isExpectedCloudRunImage(`${prefix}@sha256:${"b".repeat(64)}`), true);
  assert.equal(isExpectedCloudRunImage(`${prefix}:production`), false);
  assert.equal(isExpectedCloudRunImage(`docker.io/example/bodeul-core-api:${"a".repeat(40)}`), false);
});

test("허용하지 않은 단계 상태는 실행 경계 drift로 기록한다", async () => {
  const report = await auditProductionInfrastructure({
    env: {...validEnvironment, CLOUD_RUN_EXPECTED_STATE: "deleted"},
    tokenResolver: async () => {
      throw new Error("테스트 인증 중단");
    },
  });
  const configuration = report.baseline.checks.find((item) => item.id === "configuration.fixed-target");
  assert.equal(configuration?.status, STATUS.DRIFT);
  assert.match(configuration?.message ?? "", /1개/);
});

test("원격 조회가 모두 거부되면 raw 응답 없이 실패 상태를 반환한다", async () => {
  const report = await auditProductionInfrastructure({
    env: validEnvironment,
    tokenResolver: async () => "short-lived-test-token-without-real-access",
    fetchImpl: async () => new Response("operator@example.com raw denial", {status: 403}),
  });
  const serialized = `${renderMarkdown(report)}\n${JSON.stringify(report)}`;
  assert.equal(report.baseline.status, STATUS.UNAVAILABLE);
  assert.equal(exitCodeForReport(report), 1);
  assert.doesNotMatch(serialized, /operator@example\.com|raw denial/);
});
