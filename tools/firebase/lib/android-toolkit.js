const fs = require("fs");
const path = require("path");
const {execFileSync, spawn} = require("child_process");

function resolveRepoRoot() {
  return path.resolve(__dirname, "..", "..", "..");
}

function resolveAndroidSdkPath() {
  const repoRoot = resolveRepoRoot();
  const candidates = [
    sanitizeText(process.env.ANDROID_SDK_ROOT),
    sanitizeText(process.env.ANDROID_HOME),
    resolveSdkDirFromLocalProperties(path.join(repoRoot, "local.properties")),
  ].filter(Boolean);

  return candidates.find((candidate) => fs.existsSync(candidate)) || "";
}

function resolveAdbPath() {
  const sdkPath = resolveAndroidSdkPath();
  if (sdkPath) {
    const adbPath = path.join(sdkPath, "platform-tools", process.platform === "win32" ? "adb.exe" : "adb");
    if (fs.existsSync(adbPath)) {
      return adbPath;
    }
  }

  try {
    const command = process.platform === "win32" ? "where.exe" : "which";
    const output = execFileSync(command, ["adb"], {encoding: "utf8"});
    const firstLine = output.split(/\r?\n/).find((line) => sanitizeText(line));
    return sanitizeText(firstLine);
  } catch (error) {
    return "";
  }
}

function listConnectedDevices(adbPath) {
  const output = execFileSync(adbPath, ["devices"], {encoding: "utf8"});
  return output.split(/\r?\n/)
      .slice(1)
      .map((line) => line.trim())
      .filter((line) => line.endsWith("\tdevice"))
      .map((line) => {
        const [serial] = line.split("\t");
        return {serial};
      });
}

function selectDevice(devices, serial) {
  if (serial) {
    const matchedDevice = devices.find((device) => device.serial === serial);
    if (!matchedDevice) {
      throw new Error(`지정한 디바이스(${serial})를 찾지 못했습니다.`);
    }
    return matchedDevice;
  }

  if (devices.length === 1) {
    return devices[0];
  }
  if (devices.length === 0) {
    throw new Error("연결된 Android 디바이스가 없습니다. 에뮬레이터를 켜거나 USB 디바이스를 연결해 주세요.");
  }
  throw new Error("디바이스가 여러 대 연결되어 있습니다. `--serial`로 대상 디바이스를 지정해 주세요.");
}

function runAdbText(adbPath, serial, args) {
  const fullArgs = withSerial(serial, args);
  return execFileSync(adbPath, fullArgs, {encoding: "utf8"}).trim();
}

async function captureDeviceScreenshot(adbPath, serial, outputPath) {
  fs.mkdirSync(path.dirname(outputPath), {recursive: true});
  const fullArgs = withSerial(serial, ["exec-out", "screencap", "-p"]);

  return new Promise((resolve, reject) => {
    const output = fs.createWriteStream(outputPath);
    const child = spawn(adbPath, fullArgs, {
      stdio: ["ignore", "pipe", "pipe"],
      shell: false,
    });

    let errorOutput = "";
    child.stdout.pipe(output);
    child.stderr.on("data", (chunk) => {
      errorOutput += chunk.toString();
    });
    child.on("error", reject);
    child.on("close", (exitCode) => {
      output.close(() => {
        if (exitCode === 0) {
          resolve(outputPath);
          return;
        }
        reject(new Error(`adb screencap 실패: ${errorOutput || exitCode}`));
      });
    });
  });
}

function launchMainActivity(adbPath, serial, applicationId) {
  runAdbText(adbPath, serial, [
    "shell",
    "monkey",
    "-p",
    applicationId,
    "-c",
    "android.intent.category.LAUNCHER",
    "1",
  ]);
}

function launchAutomationRoute(adbPath, serial, applicationId, route) {
  const args = [
    "shell",
    "am",
    "start",
    "-S",
    "-W",
    "-n",
    `${applicationId}/com.example.bodeul.debug.AutomationEntryActivity`,
    "--es",
    "role",
    route.role,
    "--es",
    "screen",
    route.automationScreen,
  ];

  if (route.requestId) {
    args.push("--es", "requestId", route.requestId);
  }
  if (route.forceSignIn) {
    args.push("--ez", "forceSignIn", "true");
  }
  return runAdbText(adbPath, serial, args);
}

function readDeviceMetadata(adbPath, serial) {
  return {
    serial,
    manufacturer: readDeviceProperty(adbPath, serial, "ro.product.manufacturer"),
    model: readDeviceProperty(adbPath, serial, "ro.product.model"),
    androidVersion: readDeviceProperty(adbPath, serial, "ro.build.version.release"),
    focusedActivity: resolveFocusedActivity(adbPath, serial),
  };
}

function readDeviceProperty(adbPath, serial, propertyName) {
  try {
    return runAdbText(adbPath, serial, ["shell", "getprop", propertyName]);
  } catch (error) {
    return "";
  }
}

function resolveFocusedActivity(adbPath, serial) {
  try {
    const dumpsys = runAdbText(adbPath, serial, ["shell", "dumpsys", "activity", "activities"]);
    const match = dumpsys.match(/mResumedActivity:.*? ([^ ]+\/[^ ]+)/) ||
      dumpsys.match(/topResumedActivity=.*? ([^ ]+\/[^ }]+)/) ||
      dumpsys.match(/ResumedActivity:.*? ([^ ]+\/[^ ]+)/);
    return match ? sanitizeText(match[1]) : "";
  } catch (error) {
    return "";
  }
}

async function waitForFocusedActivity(adbPath, serial, matcher, timeoutMillis, intervalMillis) {
  const startedAt = Date.now();
  while (Date.now() - startedAt <= timeoutMillis) {
    const focusedActivity = resolveFocusedActivity(adbPath, serial);
    if (matcher(focusedActivity)) {
      return focusedActivity;
    }
    await delay(intervalMillis);
  }
  return resolveFocusedActivity(adbPath, serial);
}

function resolveSdkDirFromLocalProperties(filePath) {
  if (!fs.existsSync(filePath)) {
    return "";
  }

  const lines = fs.readFileSync(filePath, "utf8").split(/\r?\n/);
  const sdkLine = lines.find((line) => line.startsWith("sdk.dir="));
  if (!sdkLine) {
    return "";
  }
  return sdkLine
      .slice("sdk.dir=".length)
      .replace(/\\:/g, ":")
      .replace(/\\\\/g, "\\");
}

function withSerial(serial, args) {
  return serial ? ["-s", serial].concat(args) : args;
}

function delay(durationMillis) {
  return new Promise((resolve) => setTimeout(resolve, durationMillis));
}

function sanitizeText(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

module.exports = {
  captureDeviceScreenshot,
  launchAutomationRoute,
  launchMainActivity,
  listConnectedDevices,
  readDeviceMetadata,
  resolveAdbPath,
  resolveAndroidSdkPath,
  resolveRepoRoot,
  runAdbText,
  selectDevice,
  waitForFocusedActivity,
};
