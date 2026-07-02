import {type PostgresClient} from "./database.js";

export interface HospitalGuideItem {
  readonly id: string;
  readonly hospitalName: string;
  readonly departmentName: string;
  readonly steps: readonly unknown[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface HospitalGuidesPayload {
  readonly items: readonly HospitalGuideItem[];
  readonly limit: number;
}

export interface HospitalGuideReader {
  listHospitalGuides(limit: number): Promise<HospitalGuidesPayload>;
}

export interface LimitParseFailure {
  readonly statusCode: 400;
  readonly error: "invalid_limit";
  readonly message: string;
}

const DEFAULT_LIMIT = 50;
const MAX_LIMIT = 100;

export function createPostgresHospitalGuideReader(
    postgresClient: PostgresClient | null | undefined,
): HospitalGuideReader | null {
  if (!postgresClient) {
    return null;
  }

  return {
    async listHospitalGuides(limit) {
      const result = await postgresClient.query(
          [
            "select id, hospital_name, department_name, steps, created_at, updated_at",
            "from hospital_guides",
            "order by updated_at desc, hospital_name asc, department_name asc",
            "limit $1",
          ].join(" "),
          [limit],
      );

      return {
        items: result.rows.map(toHospitalGuideItem),
        limit,
      };
    },
  };
}

export function parseHospitalGuideLimit(rawLimit: string | null): {readonly ok: true; readonly limit: number} | {
  readonly ok: false;
  readonly failure: LimitParseFailure;
} {
  if (rawLimit === null || rawLimit === "") {
    return {ok: true, limit: DEFAULT_LIMIT};
  }

  const limit = Number(rawLimit);
  if (!Number.isInteger(limit) || limit < 1 || limit > MAX_LIMIT) {
    return {
      ok: false,
      failure: {
        statusCode: 400,
        error: "invalid_limit",
        message: `limit은 1부터 ${MAX_LIMIT} 사이의 정수여야 합니다.`,
      },
    };
  }

  return {ok: true, limit};
}

function toHospitalGuideItem(row: Record<string, unknown>): HospitalGuideItem {
  return {
    id: String(row.id),
    hospitalName: String(row.hospital_name),
    departmentName: String(row.department_name),
    steps: Array.isArray(row.steps) ? row.steps : [],
    createdAt: toTimestampString(row.created_at),
    updatedAt: toTimestampString(row.updated_at),
  };
}

function toTimestampString(value: unknown): string {
  if (value instanceof Date) {
    return value.toISOString();
  }

  return String(value);
}
