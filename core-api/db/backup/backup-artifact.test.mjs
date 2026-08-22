import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { inspectBackupArtifact } from "./backup-artifact.mjs";

test("같은 dump 바이트로 크기와 SHA-256을 계산한다", () => {
  const directory = mkdtempSync(path.join(tmpdir(), "bodeul-backup-artifact-"));
  const filePath = path.join(directory, "backup.dump");
  const content = Buffer.from("bodeul postgres backup");

  try {
    writeFileSync(filePath, content);

    assert.deepEqual(inspectBackupArtifact(filePath), {
      bytes: content.length,
      sha256: createHash("sha256").update(content).digest("hex"),
    });
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("빈 dump는 거부한다", () => {
  const directory = mkdtempSync(path.join(tmpdir(), "bodeul-backup-artifact-"));
  const filePath = path.join(directory, "empty.dump");

  try {
    writeFileSync(filePath, Buffer.alloc(0));

    assert.throws(
      () => inspectBackupArtifact(filePath),
      /생성된 dump가 비어 있습니다\./,
    );
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("존재하지 않는 dump 경로는 파일 시스템 오류를 유지한다", () => {
  const filePath = path.join(tmpdir(), `missing-bodeul-backup-${process.pid}.dump`);

  assert.throws(
    () => inspectBackupArtifact(filePath),
    (error) => error?.code === "ENOENT",
  );
});
