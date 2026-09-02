const PRODUCTION_FIREBASE_PROJECT_ID = "bodeul-prod-110";

function isLegacyManagerLocationEnabled({
  configuredValue = process.env.BODEUL_SESSION_LEGACY_MANAGER_LOCATION_ENABLED,
  projectId = process.env.GCLOUD_PROJECT ||
    process.env.GOOGLE_CLOUD_PROJECT ||
    process.env.FIREBASE_PROJECT_ID,
} = {}) {
  if (`${projectId ?? ""}`.trim() === PRODUCTION_FIREBASE_PROJECT_ID) {
    return false;
  }
  return `${configuredValue ?? ""}`.trim().toLowerCase() === "true";
}

module.exports = {
  isLegacyManagerLocationEnabled,
};
