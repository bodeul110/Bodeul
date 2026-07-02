export interface HealthPayload {
  readonly status: "ok";
  readonly service: "bodeul-api";
  readonly timestamp: string;
}

export function createHealthPayload(now: () => Date = () => new Date()): HealthPayload {
  return {
    status: "ok",
    service: "bodeul-api",
    timestamp: now().toISOString(),
  };
}
