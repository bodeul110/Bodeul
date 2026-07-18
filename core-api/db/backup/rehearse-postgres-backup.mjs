import { createHash, randomBytes } from "node:crypto";
import { spawnSync } from "node:child_process";
import { appendFileSync, mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";

const POSTGRES_IMAGE = process.env.POSTGRES_BACKUP_IMAGE || "postgres:17-alpine";
const REQUIRED_RLS_TABLES = ["app_users", "hospital_guides", "appointment_requests"];
const FORBIDDEN_GRANTEES = ["PUBLIC", "anon", "authenticated", "service_role"];
const RESTORE_ROLES = [
  "bodeul_migration",
  "bodeul_core_runtime",
  "bodeul_admin_runtime",
  "anon",
  "authenticated",
  "service_role",
];

function fail(message) {
  throw new Error(message);
}

function parseOutputDirectory(argv) {
  const index = argv.indexOf("--output-dir");
  if (index === -1 || !argv[index + 1]) {
    fail("--output-dir 인자가 필요합니다.");
  }
  return path.resolve(argv[index + 1]);
}

function requireEnvironment(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    fail(`${name} 환경변수가 필요합니다.`);
  }
  return value;
}

function toPostgresUrl(jdbcUrl) {
  if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
    fail("MIGRATION_DB_JDBC_URL은 jdbc:postgresql:// 형식이어야 합니다.");
  }
  const postgresUrl = jdbcUrl.slice("jdbc:".length);
  const parsed = new URL(postgresUrl);
  if (
    parsed.username ||
    parsed.password ||
    parsed.searchParams.has("user") ||
    parsed.searchParams.has("password")
  ) {
    fail("DB URL에 자격 증명을 포함하지 말고 별도 환경변수를 사용해야 합니다.");
  }
  return postgresUrl;
}

function runDocker(args, options = {}) {
  const result = spawnSync("docker", args, {
    encoding: "utf8",
    env: options.env || process.env,
    stdio: options.capture ? ["ignore", "pipe", "pipe"] : "inherit",
  });

  if (result.error) {
    fail(`Docker 실행에 실패했습니다: ${result.error.message}`);
  }
  if (result.status !== 0) {
    const detail = options.capture ? result.stderr.trim() : "위 로그를 확인하세요.";
    fail(`Docker 명령이 실패했습니다. ${detail}`);
  }
  return options.capture ? result.stdout.trim() : "";
}

function sourceQuery(connection, sql) {
  return runDocker(
    [
      "run",
      "--rm",
      "--env",
      "PGPASSWORD",
      POSTGRES_IMAGE,
      "psql",
      "--dbname",
      connection.url,
      "--username",
      connection.username,
      "--no-psqlrc",
      "--no-align",
      "--tuples-only",
      "--field-separator=\t",
      "--set",
      "ON_ERROR_STOP=1",
      "--command",
      sql,
    ],
    {
      capture: true,
      env: { ...process.env, PGPASSWORD: connection.password },
    },
  );
}

function restoreQuery(containerName, sql) {
  return runDocker(
    [
      "exec",
      containerName,
      "psql",
      "--username",
      "postgres",
      "--dbname",
      "postgres",
      "--no-psqlrc",
      "--no-align",
      "--tuples-only",
      "--field-separator=\t",
      "--set",
      "ON_ERROR_STOP=1",
      "--command",
      sql,
    ],
    { capture: true },
  );
}

function quoteIdentifier(identifier) {
  if (!/^[a-z_][a-z0-9_]*$/.test(identifier)) {
    fail(`안전하지 않은 PostgreSQL 식별자입니다: ${identifier}`);
  }
  return `"${identifier}"`;
}

function collectManifest(query) {
  const tableRows = query(`
    select c.relname, pg_get_userbyid(c.relowner), c.relrowsecurity::text
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'bodeul'
      and c.relkind in ('r', 'p')
    order by c.relname;
  `);

  const tables = {};
  for (const line of tableRows.split(/\r?\n/).filter(Boolean)) {
    const [name, owner, rlsEnabled] = line.split("\t");
    tables[name] = {
      owner,
      rlsEnabled: rlsEnabled === "true" || rlsEnabled === "t",
      rowCount: query(`select count(*)::text from bodeul.${quoteIdentifier(name)};`),
    };
  }

  const grants = query(`
    select
      case when acl.grantee = 0 then 'PUBLIC' else grantee.rolname end,
      acl.privilege_type,
      count(*)::text
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    cross join lateral aclexplode(coalesce(c.relacl, acldefault('r', c.relowner))) acl
    left join pg_roles grantee on grantee.oid = acl.grantee
    where n.nspname = 'bodeul'
      and c.relkind in ('r', 'p')
    group by 1, 2
    order by 1, 2;
  `)
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => {
      const [grantee, privilege, count] = line.split("\t");
      return { grantee, privilege, count };
    });

  const flyway = tables.flyway_schema_history
    ? (() => {
        const [successfulCount, maxVersion] = query(`
          select count(*) filter (where success)::text, coalesce(max(version), '')
          from bodeul.flyway_schema_history;
        `).split("\t");
        return { successfulCount, maxVersion };
      })()
    : { successfulCount: "0", maxVersion: "" };

  return {
    schemaOwner: query(`
      select pg_get_userbyid(nspowner)
      from pg_namespace
      where nspname = 'bodeul';
    `),
    tables,
    policyCount: query("select count(*)::text from pg_policies where schemaname = 'bodeul';"),
    indexCount: query(`
      select count(*)::text
      from pg_index i
      join pg_class c on c.oid = i.indexrelid
      join pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'bodeul';
    `),
    constraintCount: query(`
      select count(*)::text
      from pg_constraint c
      join pg_namespace n on n.oid = c.connamespace
      where n.nspname = 'bodeul';
    `),
    grants,
    flyway,
  };
}

function validateManifest(manifest, label) {
  if (manifest.schemaOwner !== "bodeul_migration") {
    fail(`${label}: bodeul schema owner가 bodeul_migration이 아닙니다.`);
  }

  for (const tableName of REQUIRED_RLS_TABLES) {
    const table = manifest.tables[tableName];
    if (!table) {
      fail(`${label}: 필수 테이블 ${tableName}이 없습니다.`);
    }
    if (table.owner !== "bodeul_migration") {
      fail(`${label}: ${tableName} owner가 bodeul_migration이 아닙니다.`);
    }
    if (!table.rlsEnabled) {
      fail(`${label}: ${tableName}의 RLS가 비활성 상태입니다.`);
    }
  }

  for (const [tableName, table] of Object.entries(manifest.tables)) {
    if (table.owner !== "bodeul_migration") {
      fail(`${label}: ${tableName} owner가 bodeul_migration이 아닙니다.`);
    }
  }

  if (Number.parseInt(manifest.policyCount, 10) < REQUIRED_RLS_TABLES.length * 2) {
    fail(`${label}: 필수 RLS 정책 수가 기준보다 적습니다.`);
  }

  for (const role of ["bodeul_core_runtime", "bodeul_admin_runtime"]) {
    const selectGrant = manifest.grants.find(
      (grant) => grant.grantee === role && grant.privilege === "SELECT",
    );
    if (!selectGrant || Number.parseInt(selectGrant.count, 10) < REQUIRED_RLS_TABLES.length) {
      fail(`${label}: ${role}의 필수 SELECT 권한이 부족합니다.`);
    }
  }

  if (!manifest.tables.flyway_schema_history) {
    fail(`${label}: Flyway schema history가 없습니다.`);
  }
  if (Number.parseInt(manifest.flyway.successfulCount, 10) < 3) {
    fail(`${label}: 성공한 Flyway migration이 V1~V3보다 적습니다.`);
  }

  const forbiddenGrant = manifest.grants.find((grant) => FORBIDDEN_GRANTEES.includes(grant.grantee));
  if (forbiddenGrant) {
    fail(`${label}: 공개 role ${forbiddenGrant.grantee}에 ${forbiddenGrant.privilege} 권한이 남아 있습니다.`);
  }
}

function waitForPostgres(containerName) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const result = spawnSync(
      "docker",
      ["exec", containerName, "pg_isready", "--username", "postgres", "--dbname", "postgres"],
      { stdio: "ignore" },
    );
    if (result.status === 0) {
      return;
    }
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000);
  }
  fail("격리 복원 PostgreSQL이 제한 시간 안에 준비되지 않았습니다.");
}

function appendGithubOutput(entries) {
  if (!process.env.GITHUB_OUTPUT) {
    return;
  }
  const body = Object.entries(entries)
    .map(([key, value]) => `${key}=${value}`)
    .join("\n");
  appendFileSync(process.env.GITHUB_OUTPUT, `${body}\n`, "utf8");
}

function appendGithubSummary(report) {
  if (!process.env.GITHUB_STEP_SUMMARY) {
    return;
  }
  const tableSummary = Object.entries(report.source.manifest.tables)
    .map(([name, table]) => `| \`${name}\` | ${table.rowCount} | ${table.rlsEnabled ? "활성" : "비활성"} |`)
    .join("\n");
  appendFileSync(
    process.env.GITHUB_STEP_SUMMARY,
    [
      "## PostgreSQL 백업·격리 복원 검증",
      "",
      `- PostgreSQL: ${report.source.serverVersion}`,
      `- dump SHA-256: \`${report.backup.sha256}\``,
      `- dump 크기: ${report.backup.bytes} bytes`,
      `- Flyway 최대 version: ${report.source.manifest.flyway.maxVersion}`,
      `- 원본/복원 manifest 일치: ${report.restore.manifestMatched ? "예" : "아니오"}`,
      "",
      "| 테이블 | row 수 | RLS |",
      "| --- | ---: | --- |",
      tableSummary,
      "",
    ].join("\n"),
    "utf8",
  );
}

const outputDirectory = parseOutputDirectory(process.argv.slice(2));
const connection = {
  url: toPostgresUrl(requireEnvironment("MIGRATION_DB_JDBC_URL")),
  username: requireEnvironment("MIGRATION_DB_USERNAME"),
  password: requireEnvironment("MIGRATION_DB_PASSWORD"),
};
const timestamp = new Date().toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z");
const datePath = `${timestamp.slice(0, 4)}/${timestamp.slice(4, 6)}/${timestamp.slice(6, 8)}`;
const label = `${timestamp}-bodeul-production`;
const dumpName = `${label}.dump`;
const dumpPath = path.join(outputDirectory, dumpName);
const checksumPath = `${dumpPath}.sha256`;
const reportPath = path.join(outputDirectory, `${label}-restore-report.json`);
const containerName = `bodeul-postgres-restore-${process.pid}-${randomBytes(3).toString("hex")}`;

mkdirSync(outputDirectory, { recursive: true });

try {
  console.log("production PostgreSQL manifest를 읽습니다.");
  const sourceServerVersion = sourceQuery(connection, "show server_version;");
  const sourceManifest = collectManifest((sql) => sourceQuery(connection, sql));
  validateManifest(sourceManifest, "원본 DB");

  console.log("owner와 ACL을 포함한 custom-format logical dump를 생성합니다.");
  runDocker(
    [
      "run",
      "--rm",
      "--env",
      "PGPASSWORD",
      "--mount",
      `type=bind,source=${outputDirectory},target=/backup`,
      POSTGRES_IMAGE,
      "pg_dump",
      "--dbname",
      connection.url,
      "--username",
      connection.username,
      "--no-password",
      "--format=custom",
      "--compress=9",
      "--schema=bodeul",
      "--file",
      `/backup/${dumpName}`,
    ],
    { env: { ...process.env, PGPASSWORD: connection.password } },
  );

  const dumpBytes = statSync(dumpPath).size;
  if (dumpBytes === 0) {
    fail("생성된 dump가 비어 있습니다.");
  }
  const sha256 = createHash("sha256").update(readFileSync(dumpPath)).digest("hex");
  writeFileSync(checksumPath, `${sha256}  ${dumpName}\n`, "utf8");

  console.log("격리 PostgreSQL 컨테이너에 dump를 복원합니다.");
  runDocker([
    "run",
    "--detach",
    "--name",
    containerName,
    "--env",
    "POSTGRES_HOST_AUTH_METHOD=trust",
    "--mount",
    `type=bind,source=${outputDirectory},target=/backup,readonly`,
    POSTGRES_IMAGE,
  ]);
  waitForPostgres(containerName);

  const roleValues = RESTORE_ROLES.map((role) => `('${role}')`).join(", ");
  restoreQuery(
    containerName,
    `
      do $$
      declare role_name text;
      begin
        for role_name in select column1 from (values ${roleValues}) roles
        loop
          if not exists (select 1 from pg_roles where rolname = role_name) then
            execute format('create role %I nologin noinherit', role_name);
          end if;
        end loop;
      end
      $$;
    `,
  );
  runDocker([
    "exec",
    containerName,
    "pg_restore",
    "--username",
    "postgres",
    "--dbname",
    "postgres",
    "--exit-on-error",
    `/backup/${dumpName}`,
  ]);

  const restoreManifest = collectManifest((sql) => restoreQuery(containerName, sql));
  validateManifest(restoreManifest, "복원 DB");
  const manifestMatched = JSON.stringify(sourceManifest) === JSON.stringify(restoreManifest);
  if (!manifestMatched) {
    fail("원본과 복원 DB manifest가 일치하지 않습니다.");
  }

  const report = {
    generatedAt: new Date().toISOString(),
    source: {
      serverVersion: sourceServerVersion,
      manifest: sourceManifest,
    },
    backup: {
      format: "PostgreSQL custom",
      image: POSTGRES_IMAGE,
      fileName: dumpName,
      bytes: dumpBytes,
      sha256,
    },
    restore: {
      isolatedContainer: true,
      manifestMatched,
      manifest: restoreManifest,
    },
  };
  writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  appendGithubOutput({
    dump_path: dumpPath,
    checksum_path: checksumPath,
    report_path: reportPath,
    dump_name: dumpName,
    object_prefix: `${datePath}/${label}`,
    sha256,
  });
  appendGithubSummary(report);
  console.log(`격리 복원 검증 완료: ${dumpName} (${dumpBytes} bytes)`);
} finally {
  spawnSync("docker", ["rm", "--force", containerName], { stdio: "ignore" });
  connection.password = "";
  connection.url = "";
}
