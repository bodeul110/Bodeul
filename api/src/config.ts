export interface ServerConfig {
  readonly host: string;
  readonly port: number;
}

export interface DatabaseConfig {
  readonly status: "configured" | "missing";
}

const DEFAULT_HOST = "127.0.0.1";
const DEFAULT_PORT = 8080;

export function getServerConfig(env: NodeJS.ProcessEnv): ServerConfig {
  return {
    host: env.BODEUL_API_HOST?.trim() || DEFAULT_HOST,
    port: parsePort(env.BODEUL_API_PORT),
  };
}

export function getDatabaseConfig(env: NodeJS.ProcessEnv): DatabaseConfig {
  const rawUrl = env.DATABASE_URL?.trim();
  if (!rawUrl) {
    return {status: "missing"};
  }

  let parsed: URL;
  try {
    parsed = new URL(rawUrl);
  } catch {
    throw new Error("DATABASE_URL은 올바른 PostgreSQL connection string이어야 합니다.");
  }

  if (parsed.protocol !== "postgres:" && parsed.protocol !== "postgresql:") {
    throw new Error("DATABASE_URL은 postgres 또는 postgresql 프로토콜을 사용해야 합니다.");
  }

  if (!parsed.hostname || parsed.pathname === "" || parsed.pathname === "/") {
    throw new Error("DATABASE_URL에는 host와 database 이름이 포함되어야 합니다.");
  }

  return {status: "configured"};
}

function parsePort(value: string | undefined): number {
  if (value === undefined || value.trim() === "") {
    return DEFAULT_PORT;
  }

  const port = Number(value);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("BODEUL_API_PORT는 1부터 65535 사이의 정수여야 합니다.");
  }

  return port;
}
