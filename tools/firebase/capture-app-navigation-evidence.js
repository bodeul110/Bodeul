#!/usr/bin/env node

const path = require("path");

const {
  loadAppNavigationEvidence,
  normalizeAppNavigationEvidence,
  resolveDefaultEvidencePath,
  writeAppNavigationEvidence,
} = require("./lib/app-navigation-evidence");
const {
  APPLICATION_ID,
  DEFAULT_ROUTE_WAIT_MILLIS,
  ROUTE_PRESETS,
  resolveRoutePreset,
} = require("./lib/app-navigation-routes");
const {
  captureDeviceScreenshot,
  launchAutomationRoute,
  launchMainActivity,
  listConnectedDevices,
  readDeviceMetadata,
  resolveAdbPath,
  selectDevice,
  waitForFocusedActivity,
} = require("./lib/android-toolkit");

const DEFAULT_CAPTURE_SETTLE_MILLIS = 3000;

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const preset = options.preset ? resolveRoutePreset(options.preset) : null;
  if (options.preset && !preset) {
    throw new Error(`지원하지 않는 프리셋입니다: ${options.preset}`);
  }

  const screenId = options.screenId || preset?.screenId || "";
  const title = options.title || preset?.title || "";
  const role = options.role || preset?.role || "COMMON";
  if (!screenId || !title) {
    throw new Error("`--screen-id`와 `--title` 또는 `--preset` 중 하나는 반드시 필요합니다.");
  }

  const reportsRoot = path.resolve(__dirname, "reports");
  const adbPath = resolveAdbPath();
  if (!adbPath) {
    throw new Error("adb 경로를 찾지 못했습니다. Android SDK 또는 adb PATH를 확인해 주세요.");
  }

  const devices = listConnectedDevices(adbPath);
  const device = selectDevice(devices, options.serial);

  if (preset) {
    launchAutomationRoute(adbPath, device.serial, APPLICATION_ID, {
      automationScreen: preset.automationScreen,
      chatAttachment: options.chatAttachment,
      chatMessage: options.chatMessage,
      forceSignIn: options.forceSignIn,
      requestId: options.requestId || preset.defaultRequestId || "",
      role: preset.role,
    });
    await waitForFocusedActivity(
        adbPath,
        device.serial,
        (focusedActivity) => matchesActivity(focusedActivity, preset.expectedActivity),
        options.routeWaitMillis,
        300,
    );
    await delay(options.captureSettleMillis);
  } else if (options.launchMain) {
    launchMainActivity(adbPath, device.serial, APPLICATION_ID);
    await delay(options.captureSettleMillis);
  }

  const token = buildTimestampToken(new Date());
  const screenshotPath = options.imagePath ?
    path.resolve(process.cwd(), options.imagePath) :
    path.join(reportsRoot, "screenshots", `app-navigation-${token}-${screenId}.png`);
  await captureDeviceScreenshot(adbPath, device.serial, screenshotPath);

  const deviceMetadata = readDeviceMetadata(adbPath, device.serial);
  const manifestPath = options.manifestPath ?
    path.resolve(process.cwd(), options.manifestPath) :
    resolveDefaultEvidencePath(reportsRoot);
  const existingEvidence = safeLoadEvidence(manifestPath);
  const resolvedStatus = resolveStatus(options.status, preset, deviceMetadata.focusedActivity);
  const resolvedNote = buildNote(options.note, preset, deviceMetadata.focusedActivity);

  const nextEvidence = normalizeAppNavigationEvidence({
    generatedAt: new Date().toISOString(),
    source: preset ? "adb_route_capture" : "adb_capture",
    appId: APPLICATION_ID,
    buildVariant: "debug",
    device: {
      serial: deviceMetadata.serial,
      manufacturer: deviceMetadata.manufacturer,
      model: deviceMetadata.model,
      androidVersion: deviceMetadata.androidVersion,
    },
    screens: mergeScreens(existingEvidence?.screens || [], [{
      id: screenId,
      title,
      role,
      status: resolvedStatus,
      activity: options.activity || deviceMetadata.focusedActivity,
      capturedAt: new Date().toISOString(),
      note: resolvedNote,
      imagePath: path.relative(path.dirname(manifestPath), screenshotPath).split(path.sep).join("/"),
    }]),
  }, manifestPath);
  const savedPath = writeAppNavigationEvidence(manifestPath, nextEvidence);

  printSummary(nextEvidence, savedPath, screenshotPath, preset, deviceMetadata.focusedActivity);
}

function parseOptions(args) {
  return {
    activity: readOption(args, "--activity"),
    chatAttachment: args.includes("--chat-attachment"),
    chatMessage: readOption(args, "--chat-message"),
    captureSettleMillis: resolveDurationOption(
        readOption(args, "--capture-settle-ms"),
        DEFAULT_CAPTURE_SETTLE_MILLIS,
        "--capture-settle-ms",
    ),
    forceSignIn: args.includes("--force-sign-in"),
    help: args.includes("--help") || args.includes("-h"),
    imagePath: readOption(args, "--image"),
    launchMain: args.includes("--launch-main"),
    manifestPath: readOption(args, "--manifest"),
    note: readOption(args, "--note"),
    preset: readOption(args, "--preset"),
    requestId: readOption(args, "--request-id"),
    role: readOption(args, "--role"),
    routeWaitMillis: resolveDurationOption(
        readOption(args, "--route-wait-ms"),
        DEFAULT_ROUTE_WAIT_MILLIS,
        "--route-wait-ms",
    ),
    screenId: readOption(args, "--screen-id"),
    serial: readOption(args, "--serial"),
    status: readOption(args, "--status"),
    title: readOption(args, "--title"),
  };
}

function resolveDurationOption(value, defaultValue, optionName) {
  const resolvedValue = value === "" || value === undefined ? defaultValue : Number(value);
  if (!Number.isFinite(resolvedValue) || resolvedValue < 0) {
    throw new Error(`${optionName}에는 0 이상의 숫자를 입력해 주세요.`);
  }
  return resolvedValue;
}

function readOption(args, optionName) {
  const optionIndex = args.indexOf(optionName);
  return optionIndex >= 0 ? args[optionIndex + 1] : "";
}

function safeLoadEvidence(filePath) {
  try {
    return loadAppNavigationEvidence(filePath);
  } catch (error) {
    return null;
  }
}

function mergeScreens(existingScreens, nextScreens) {
  const screensById = new Map();
  for (const screen of existingScreens.concat(nextScreens)) {
    screensById.set(screen.id, screen);
  }
  return Array.from(screensById.values());
}

function resolveStatus(statusOption, preset, focusedActivity) {
  if (preset) {
    return matchesActivity(focusedActivity, preset.expectedActivity) ? "passed" : "failed";
  }
  return statusOption || "passed";
}

function buildNote(note, preset, focusedActivity) {
  const notes = [];
  if (note) {
    notes.push(note);
  }
  if (preset) {
    const routeNote = matchesActivity(focusedActivity, preset.expectedActivity) ?
      `자동 진입 성공: ${preset.expectedActivity}` :
      `자동 진입 확인 필요: 기대=${preset.expectedActivity}, 실제=${focusedActivity || "기록 없음"}`;
    notes.push(routeNote);
  }
  return notes.join("\n");
}

function matchesActivity(focusedActivity, expectedActivity) {
  if (!focusedActivity || !expectedActivity) {
    return false;
  }
  if (focusedActivity === expectedActivity) {
    return true;
  }

  const [expectedPackageName, expectedClassName] = expectedActivity.split("/");
  const [focusedPackageName, focusedClassName] = focusedActivity.split("/");
  if (!expectedClassName || !focusedClassName) {
    return false;
  }

  if (focusedClassName === expectedClassName) {
    return true;
  }

  if (expectedPackageName === focusedPackageName) {
    const normalizedExpectedClassName = expectedClassName.startsWith(".") ?
      expectedClassName :
      expectedClassName.replace(`${expectedPackageName}.`, ".");
    if (focusedClassName === normalizedExpectedClassName) {
      return true;
    }
  }

  const shortExpectedClassName = expectedClassName.split(".").pop();
  return focusedClassName.endsWith(`.${shortExpectedClassName}`) ||
      focusedClassName === `.${shortExpectedClassName}`;
}

function buildTimestampToken(date) {
  return [
    String(date.getFullYear()),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
    "-",
    String(date.getHours()).padStart(2, "0"),
    String(date.getMinutes()).padStart(2, "0"),
    String(date.getSeconds()).padStart(2, "0"),
  ].join("");
}

function delay(durationMillis) {
  return new Promise((resolve) => setTimeout(resolve, durationMillis));
}

function printHelp() {
  console.log("보들 앱 화면 증적 캡처 스크립트");
  console.log("");
  console.log("사용법");
  console.log("  node capture-app-navigation-evidence.js --screen-id login --title \"로그인 화면\"");
  console.log("  node capture-app-navigation-evidence.js --preset manager-home");
  console.log("  node capture-app-navigation-evidence.js --preset guardian-booking-status --request-id request-seed-progress");
  console.log("  node capture-app-navigation-evidence.js --preset guardian-chat --chat-attachment --chat-message \"실기기 첨부 점검\"");
  console.log("  node capture-app-navigation-evidence.js --screen-id splash --title \"스플래시\" --launch-main");
  console.log("");
  console.log("프리셋 목록");
  Object.keys(ROUTE_PRESETS).forEach((presetName) => {
    console.log(`- ${presetName}`);
  });
  console.log("");
  console.log("설명");
  console.log("- 연결된 에뮬레이터/디바이스의 현재 화면을 캡처해 reports/screenshots 아래에 저장합니다.");
  console.log("- `--preset`을 주면 debug 자동 진입 액티비티를 통해 기준선 계정 로그인과 대상 화면 이동을 먼저 수행합니다.");
  console.log("- 대상 액티비티 확인 뒤 기본 3초 동안 화면 데이터가 자리 잡기를 기다립니다. `--capture-settle-ms`로 조정할 수 있습니다.");
  console.log("- 캡처 결과는 reports/app-navigation-evidence-latest.json에 누적 기록됩니다.");
}

function printSummary(evidence, manifestPath, screenshotPath, preset, focusedActivity) {
  console.log("보들 앱 화면 증적 캡처");
  console.log(`- 저장된 증적 파일: ${manifestPath}`);
  console.log(`- 저장된 스크린샷: ${screenshotPath}`);
  if (preset) {
    console.log(`- 자동 진입 프리셋: ${preset.screenId}`);
    console.log(`- 현재 포커스: ${focusedActivity || "기록 없음"}`);
  }
  console.log(
      `- 화면 상태: 통과 ${evidence.summary.passedCount} / 경고 ${evidence.summary.warningCount} / 실패 ${evidence.summary.failedCount}`,
  );
  if (evidence.device.model || evidence.device.androidVersion) {
    console.log(
        `- 디바이스: ${[evidence.device.manufacturer, evidence.device.model].filter(Boolean).join(" ")} / Android ${evidence.device.androidVersion || "알 수 없음"}`,
    );
  }
}

if (require.main === module) {
  main().catch((error) => {
    console.error("앱 화면 증적 캡처 중 오류가 발생했습니다.");
    console.error(error.message || error);
    process.exitCode = 1;
  });
}

module.exports = {
  DEFAULT_CAPTURE_SETTLE_MILLIS,
  matchesActivity,
  resolveDurationOption,
  resolveStatus,
};
