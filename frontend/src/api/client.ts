import { clearSession, getSession, setSession } from '../auth/session'
import { ApiError, type ApiErrorBody, type AuthResponse } from './types'

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? '/api'

type RequestOptions = {
  auth?: boolean
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  retryOnUnauthorized?: boolean
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const response = await rawRequest(path, options)

  if (response.status === 401 && options.auth !== false && options.retryOnUnauthorized !== false) {
    const refreshed = await tryRefresh()
    if (refreshed) {
      const retry = await rawRequest(path, {
        ...options,
        retryOnUnauthorized: false,
      })
      return parseResponse<T>(retry)
    }
  }

  return parseResponse<T>(response)
}

async function rawRequest(path: string, options: RequestOptions) {
  const session = getSession()
  const headers = new Headers()
  headers.set('Accept', 'application/json')

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }

  if (options.auth !== false && session?.accessToken) {
    headers.set('Authorization', `Bearer ${session.accessToken}`)
  }

  return fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? (options.body === undefined ? 'GET' : 'POST'),
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type')
  const body = contentType?.includes('application/json')
    ? await response.json()
    : undefined

  if (!response.ok) {
    throw new ApiError((body ?? {}) as ApiErrorBody, response.status)
  }

  return body as T
}

async function tryRefresh() {
  const session = getSession()
  if (!session?.refreshToken) return false

  try {
    const response = await rawRequest('/auth/refresh', {
      auth: false,
      body: { refreshToken: session.refreshToken },
      method: 'POST',
      retryOnUnauthorized: false,
    })
    const auth = await parseResponse<AuthResponse>(response)
    setSession(auth)
    return true
  } catch {
    clearSession()
    return false
  }
}
