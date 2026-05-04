import { initializeAppCheck, ReCaptchaV3Provider } from 'firebase/app-check'
import { app } from '../firebase'

type AppCheckWindow = Window & typeof globalThis & {
  FIREBASE_APPCHECK_DEBUG_TOKEN?: string | boolean
}

let initialized = false

export function initializeFirebaseAppCheck() {
  if (initialized) {
    return
  }

  const siteKey = readEnv('VITE_FIREBASE_APPCHECK_SITE_KEY')
  if (!siteKey) {
    console.warn('[AppCheck] 사이트 키가 없어 관리자 웹 App Check 초기화를 건너뜁니다.')
    return
  }

  const debugToken = resolveDebugToken()
  if (debugToken !== undefined) {
    (window as AppCheckWindow).FIREBASE_APPCHECK_DEBUG_TOKEN = debugToken
  }

  initializeAppCheck(app, {
    provider: new ReCaptchaV3Provider(siteKey),
    isTokenAutoRefreshEnabled: true,
  })
  initialized = true
}

function resolveDebugToken(): string | boolean | undefined {
  const configuredToken = readEnv('VITE_FIREBASE_APPCHECK_DEBUG_TOKEN')
  if (configuredToken) {
    return configuredToken
  }

  if (import.meta.env.DEV && isLocalhost()) {
    return true
  }

  return undefined
}

function isLocalhost(): boolean {
  return window.location.hostname === 'localhost'
    || window.location.hostname === '127.0.0.1'
}

function readEnv(name: string): string {
  const value = import.meta.env[name]
  return typeof value === 'string' ? value.trim() : ''
}
