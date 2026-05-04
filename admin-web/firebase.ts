import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';
import { getStorage } from 'firebase/storage';

export const firebaseConfig = {
  apiKey: 'AIzaSyCWloZRfAY8SI6B_gbZhc6Otxyqu4zfgqA',
  authDomain: 'bodeul-dev.firebaseapp.com',
  projectId: 'bodeul-dev',
  storageBucket: 'bodeul-dev.firebasestorage.app',
  messagingSenderId: '533563500316',
  appId: '1:533563500316:web:95f69c829116802842fcd9',
};

const app = initializeApp(firebaseConfig);

export { app };
export const auth = getAuth(app);
export const db = getFirestore(app);
export const storage = getStorage(app);
