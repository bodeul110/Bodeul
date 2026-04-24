const fs = require("fs");
const path = require("path");

function resolveDefaultEvidencePath(reportsRoot) {
  return path.resolve(reportsRoot, "app-navigation-evidence-latest.json");
}

function resolveAppNavigationEvidencePath(reportsRoot, inputPath) {
  if (inputPath) {
    return path.resolve(process.cwd(), inputPath);
  }

  const defaultPath = resolveDefaultEvidencePath(reportsRoot);
  if (fs.existsSync(defaultPath)) {
    return defaultPath;
  }

  let latestMatch = "";
  let latestModifiedAt = 0;
  if (!fs.existsSync(reportsRoot)) {
    return "";
  }

  for (const item of fs.readdirSync(reportsRoot)) {
    if (!item.startsWith("app-navigation-evidence-") || !item.endsWith(".json")) {
      continue;
    }
    const fullPath = path.join(reportsRoot, item);
    const stat = fs.statSync(fullPath);
    if (stat.mtimeMs > latestModifiedAt) {
      latestModifiedAt = stat.mtimeMs;
      latestMatch = fullPath;
    }
  }

  return latestMatch;
}

function loadAppNavigationEvidence(filePath) {
  const resolvedPath = path.resolve(filePath);
  const payload = JSON.parse(fs.readFileSync(resolvedPath, "utf8"));
  return normalizeAppNavigationEvidence(payload, resolvedPath);
}

function writeAppNavigationEvidence(filePath, evidence) {
  const resolvedPath = path.resolve(filePath);
  fs.mkdirSync(path.dirname(resolvedPath), {recursive: true});
  fs.writeFileSync(resolvedPath, `${JSON.stringify(evidence, null, 2)}\n`, "utf8");
  return resolvedPath;
}

function normalizeAppNavigationEvidence(payload, filePath) {
  const screens = Array.isArray(payload?.screens) ? payload.screens.map((screen) => ({
    id: sanitizeText(screen.id),
    title: sanitizeText(screen.title),
    role: sanitizeText(screen.role) || "COMMON",
    status: normalizeStatus(screen.status),
    activity: sanitizeText(screen.activity),
    capturedAt: sanitizeText(screen.capturedAt),
    note: sanitizeText(screen.note),
    imagePath: sanitizeText(screen.imagePath),
  })) : [];

  return {
    filePath,
    generatedAt: sanitizeText(payload?.generatedAt),
    source: sanitizeText(payload?.source) || "manual",
    appId: sanitizeText(payload?.appId),
    buildVariant: sanitizeText(payload?.buildVariant) || "debug",
    device: {
      serial: sanitizeText(payload?.device?.serial),
      manufacturer: sanitizeText(payload?.device?.manufacturer),
      model: sanitizeText(payload?.device?.model),
      androidVersion: sanitizeText(payload?.device?.androidVersion),
    },
    screens,
    summary: buildAppNavigationEvidenceSummary(screens),
  };
}

function buildAppNavigationEvidenceSummary(screens) {
  const passedCount = screens.filter((screen) => screen.status === "passed").length;
  const warningCount = screens.filter((screen) => screen.status === "warning").length;
  const failedCount = screens.filter((screen) => screen.status === "failed").length;
  const availableImageCount = screens.filter((screen) => Boolean(screen.imagePath)).length;
  return {
    totalScreens: screens.length,
    passedCount,
    warningCount,
    failedCount,
    availableImageCount,
  };
}

function normalizeStatus(status) {
  const normalized = sanitizeText(status).toLowerCase();
  if (normalized === "warning" || normalized === "warn") {
    return "warning";
  }
  if (normalized === "failed" || normalized === "fail") {
    return "failed";
  }
  return "passed";
}

function sanitizeText(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

module.exports = {
  buildAppNavigationEvidenceSummary,
  loadAppNavigationEvidence,
  normalizeAppNavigationEvidence,
  resolveAppNavigationEvidencePath,
  resolveDefaultEvidencePath,
  writeAppNavigationEvidence,
};
