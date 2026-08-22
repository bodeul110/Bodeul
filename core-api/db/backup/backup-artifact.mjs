import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";

export function inspectBackupArtifact(filePath) {
  const content = readFileSync(filePath);
  if (content.length === 0) {
    throw new Error("생성된 dump가 비어 있습니다.");
  }

  return {
    bytes: content.length,
    sha256: createHash("sha256").update(content).digest("hex"),
  };
}
