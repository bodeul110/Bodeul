import {Pool, type PoolConfig, type QueryResult} from "pg";

import {type DatabaseConfig, getDatabaseConfig} from "./config.js";

export interface PostgresClient {
  readonly status: DatabaseConfig["status"];
  query(sql: string, values?: readonly unknown[]): Promise<QueryResult>;
  close(): Promise<void>;
  checkConnection(): Promise<DatabaseConnectionCheckResult>;
}

export type DatabaseConnectionCheckResult =
  | {readonly ok: true}
  | {readonly ok: false; readonly error: "db_connection_failed"};

interface PostgresPoolLike {
  query(sql: string, values?: readonly unknown[]): Promise<QueryResult>;
  end(): Promise<void>;
}

interface PostgresClientDependencies {
  readonly createPool: (config: PoolConfig) => PostgresPoolLike;
}

const DEFAULT_POOL_MAX = 5;
const DEFAULT_IDLE_TIMEOUT_MS = 10_000;
const DEFAULT_CONNECTION_TIMEOUT_MS = 5_000;

const defaultDependencies: PostgresClientDependencies = {
  createPool: (config) => new Pool(config),
};

export function createPostgresClient(
    env: NodeJS.ProcessEnv,
    dependencies: PostgresClientDependencies = defaultDependencies,
): PostgresClient | null {
  const config = getDatabaseConfig(env);
  if (config.status === "missing") {
    return null;
  }

  const pool = dependencies.createPool({
    connectionString: config.connectionString,
    max: DEFAULT_POOL_MAX,
    idleTimeoutMillis: DEFAULT_IDLE_TIMEOUT_MS,
    connectionTimeoutMillis: DEFAULT_CONNECTION_TIMEOUT_MS,
  });

  return {
    status: "configured",
    query(sql, values) {
      return pool.query(sql, values);
    },
    async close() {
      await pool.end();
    },
    async checkConnection() {
      try {
        await pool.query("select 1");
        return {ok: true};
      } catch {
        return {ok: false, error: "db_connection_failed"};
      }
    },
  };
}
