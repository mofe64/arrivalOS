import { ApiError } from './types'

export const fixturesEnabled = import.meta.env.VITE_ENABLE_FIXTURES === 'true'

export async function withFixtureFallback<T>(
  request: () => Promise<T>,
  fallback: T,
): Promise<T> {
  try {
    return await request()
  } catch (error) {
    if (!fixturesEnabled) {
      throw error
    }

    if (error instanceof ApiError && [401, 403].includes(error.status)) {
      throw error
    }

    return fallback
  }
}
