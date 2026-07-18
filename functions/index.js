const {initializeApp} = require("firebase-admin/app");

initializeApp();

Object.assign(exports, require("./src/auth"));
Object.assign(exports, require("./src/supabase-auth"));
Object.assign(exports, require("./src/notifications"));
Object.assign(exports, require("./src/action-delivery"));
Object.assign(exports, require("./src/sync"));
Object.assign(exports, require("./src/reminders"));
