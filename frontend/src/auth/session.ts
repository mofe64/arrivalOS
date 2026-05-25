import type { AuthResponse, AuthUser } from '../api/types'

const STORAGE_KEY = 'arrivalos.auth'

export type AuthSession = {
  accessToken: string
  refreshToken: string
  accessTokenExpiresAt: string
  refreshTokenExpiresAt: string
  user: AuthUser
}

let memorySession: AuthSession | null = readStoredSession()

function readStoredSession(): AuthSession | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as AuthSession) : null
  } catch {
    return null
  }
}

export function getSession() {
  return memorySession
}

export function setSession(auth: AuthResponse) {
  memorySession = {
    accessToken: auth.accessToken,
    refreshToken: auth.refreshToken,
    accessTokenExpiresAt: auth.accessTokenExpiresAt,
    refreshTokenExpiresAt: auth.refreshTokenExpiresAt,
    user: { ...auth.user, active: auth.user.active ?? true },
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(memorySession))
}

export function updateSessionUser(user: AuthUser) {
  if (!memorySession) return
  memorySession = { ...memorySession, user }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(memorySession))
}

export function clearSession() {
  memorySession = null
  window.localStorage.removeItem(STORAGE_KEY)
}

export function isAuthenticated() {
  return Boolean(memorySession?.accessToken)
}
