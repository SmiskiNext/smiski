import { getApp, getApps, initializeApp } from 'firebase/app';
import { GoogleAuthProvider, getAuth } from 'firebase/auth';

const firebaseConfig = {
    NEXT_PUBLIC_FIREBASE_API_KEY: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
    NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN:
        process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
    NEXT_PUBLIC_FIREBASE_PROJECT_ID:
        process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
    NEXT_PUBLIC_FIREBASE_APP_ID: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
} as const;

for (const [key, value] of Object.entries(firebaseConfig)) {
    if (!value) {
        throw new Error(
            `Missing required environment variable: ${key}. `
                + 'Add it to your .env.local file. See .env.local.example for reference.',
        );
    }
}

const app =
    getApps().length > 0
        ? getApp()
        : initializeApp({
              apiKey: firebaseConfig.NEXT_PUBLIC_FIREBASE_API_KEY,
              authDomain: firebaseConfig.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
              projectId: firebaseConfig.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
              appId: firebaseConfig.NEXT_PUBLIC_FIREBASE_APP_ID,
          });

export const auth = getAuth(app);
export const googleProvider = new GoogleAuthProvider();
