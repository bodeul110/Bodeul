const functions = require("firebase-functions/v1");
const logger = require("firebase-functions/logger");
const {getAuth} = require("firebase-admin/auth");
const {
  SUPABASE_AUTHENTICATED_ROLE,
  mergeSupabaseAuthenticatedRole,
} = require("./lib/supabase-auth-claims");

const assignSupabaseAuthenticatedRole = functions
    .runWith({failurePolicy: true})
    .region("asia-northeast3")
    .auth.user()
    .onCreate(async (user) => {
      if (user.customClaims?.role === SUPABASE_AUTHENTICATED_ROLE) {
        return;
      }

      await getAuth().setCustomUserClaims(
          user.uid,
          mergeSupabaseAuthenticatedRole(user.customClaims),
      );
      logger.info("신규 사용자에게 Supabase Realtime 인증 역할을 부여했습니다.");
    });

module.exports = {
  assignSupabaseAuthenticatedRole,
};
