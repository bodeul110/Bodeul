const {onCall, HttpsError} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");

initializeApp();

const FUNCTIONS_OPTIONS = {
  region: "asia-northeast3",
  cors: true,
};

const CLIENT_CREATABLE_ROLES = new Set(["PATIENT", "GUARDIAN", "MANAGER"]);

exports.kakaoCustomToken = onCall(FUNCTIONS_OPTIONS, async (request) => {
  const accessToken = `${request.data?.accessToken ?? ""}`.trim();
  const role = `${request.data?.role ?? ""}`.trim();

  // 앱이 선택한 역할은 Firestore users 문서 생성 전에 한 번 더 서버에서 제한한다.
  if (!CLIENT_CREATABLE_ROLES.has(role)) {
    throw invalidArgument("허용되지 않은 사용자 역할입니다.");
  }

  if (!accessToken) {
    throw invalidArgument("카카오 access token이 필요합니다.");
  }

  const kakaoProfile = await fetchKakaoProfile(accessToken);
  const providerUserId = `${kakaoProfile.id ?? ""}`.trim();
  if (!providerUserId) {
    throw unauthenticated("카카오 사용자 정보를 확인하지 못했습니다.");
  }

  const firebaseToken = await getAuth().createCustomToken(`kakao_${providerUserId}`);
  return {
    firebaseToken,
    profile: {
      providerUserId,
      name: extractKakaoName(kakaoProfile),
      email: extractKakaoEmail(kakaoProfile, providerUserId),
      phone: extractKakaoPhone(kakaoProfile),
    },
  };
});

async function fetchKakaoProfile(accessToken) {
  const response = await fetch("https://kapi.kakao.com/v2/user/me", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/x-www-form-urlencoded;charset=utf-8",
    },
  });

  let responseBody = {};
  try {
    responseBody = await response.json();
  } catch (error) {
    logger.error("카카오 응답 JSON 파싱 실패", error);
  }

  if (!response.ok) {
    logger.error("카카오 사용자 정보 조회 실패", {
      status: response.status,
      body: responseBody,
    });
    throw unauthenticated("카카오 로그인 정보가 유효하지 않거나 만료되었습니다.");
  }

  return responseBody;
}

function extractKakaoName(kakaoProfile) {
  const nickname = kakaoProfile?.kakao_account?.profile?.nickname;
  if (typeof nickname === "string" && nickname.trim()) {
    return nickname.trim();
  }
  return "카카오 사용자";
}

function extractKakaoEmail(kakaoProfile, providerUserId) {
  const email = kakaoProfile?.kakao_account?.email;
  if (typeof email === "string" && email.trim()) {
    return email.trim();
  }

  // 이메일 동의항목이 아직 비어 있어도 앱 모델이 깨지지 않도록 대체 메일을 사용한다.
  return `kakao_${providerUserId}@bodeul.local`;
}

function extractKakaoPhone(kakaoProfile) {
  const phone = kakaoProfile?.kakao_account?.phone_number;
  if (typeof phone === "string" && phone.trim()) {
    return phone.trim();
  }
  return "";
}

function invalidArgument(message) {
  return new HttpsError("invalid-argument", message, {message});
}

function unauthenticated(message) {
  return new HttpsError("unauthenticated", message, {message});
}
