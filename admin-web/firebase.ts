import { initializeApp } from 'firebase/app';
import { getFirestore } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: 'AIzaSyCWloZRfAY8SI6B_gbZhc6Otxyqu4zfgqA',
  authDomain: 'bodeul-dev.firebaseapp.com',
  projectId: 'bodeul-dev',
  storageBucket: 'bodeul-dev.firebasestorage.app',
  messagingSenderId: '533563500316',
  appId: '1:533563500316:web:95f69c829116802842fcd9',
};

const app = initializeApp(firebaseConfig);

export const db = getFirestore(app);