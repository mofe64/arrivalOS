import { clearSession, getSession, setSession, updateSessionUser } from '../auth/session'
import { apiRequest } from './client'
import type {
  AdminPrincipalSummary,
  AdminTripDetail,
  AdminTripListItem,
  AuthResponse,
  AuthUser,
  Concierge,
  ConciergeAccessLink,
  ConciergeTripDetail,
  CreateConciergePayload,
  CreateTripPayload,
  CreateWatcherPayload,
  InvitationResponse,
  MessageResponse,
  NotificationAttempt,
  PrincipalTripDetail,
  PrincipalTripSummary,
  TimelineEventType,
  TripTransitionResponse,
  TripStatus,
  Watcher,
} from './types'

export const authApi = {
  async login(email: string, password: string) {
    const auth = await apiRequest<AuthResponse>('/auth/login', {
      auth: false,
      body: { email, password },
      method: 'POST',
    })
    setSession(auth)
    return auth
  },

  async me() {
    const user = await apiRequest<AuthUser>('/auth/me')
    updateSessionUser(user)
    return user
  },

  async logout() {
    const refreshToken = getSession()?.refreshToken
    try {
      await apiRequest<void>('/auth/logout', {
        body: refreshToken ? { refreshToken } : undefined,
        method: 'POST',
      })
    } finally {
      clearSession()
    }
  },

  forgotPassword(email: string) {
    return apiRequest<MessageResponse>('/auth/password/forgot', {
      auth: false,
      body: { email },
      method: 'POST',
    })
  },

  resetPassword(token: string, newPassword: string) {
    return apiRequest<MessageResponse>('/auth/password/reset', {
      auth: false,
      body: { token, newPassword },
      method: 'POST',
    })
  },

  verifyEmail(token: string) {
    return apiRequest<MessageResponse>('/auth/verify-email', {
      auth: false,
      body: { token },
      method: 'POST',
    })
  },

  resendVerification() {
    return apiRequest<MessageResponse>('/auth/verify-email/resend', {
      method: 'POST',
    })
  },

  async acceptInvitation(token: string, password: string) {
    const auth = await apiRequest<AuthResponse>('/auth/invitations/accept', {
      auth: false,
      body: { token, password },
      method: 'POST',
    })
    setSession(auth)
    return auth
  },
}

export const adminApi = {
  listTrips(activeOnly = false) {
    return apiRequest<AdminTripListItem[]>(
      activeOnly ? '/admin/trips/active' : '/admin/trips',
    )
  },
  trip(id: string) {
    return apiRequest<AdminTripDetail>(`/admin/trips/${id}`)
  },
  createTrip(payload: CreateTripPayload) {
    return apiRequest<AdminTripDetail>('/admin/trips', {
      body: payload,
      method: 'POST',
    })
  },
  addPrincipal(tripId: string, payload: { fullName?: string; phone?: string; userAccountId?: string }) {
    return apiRequest<AdminTripDetail>(`/admin/trips/${tripId}/principals`, {
      body: payload,
      method: 'POST',
    })
  },
  addWatcher(tripId: string, payload: CreateWatcherPayload) {
    return apiRequest<Watcher>(`/admin/trips/${tripId}/watchers`, {
      body: { ...payload, notificationChannel: payload.notificationChannel ?? 'EMAIL' },
      method: 'POST',
    })
  },
  assignConcierge(tripId: string, conciergeId: string) {
    return apiRequest(`/admin/trips/${tripId}/concierge-assignment`, {
      body: { conciergeId },
      method: 'POST',
    })
  },
  createConciergeAccessLink(tripId: string, conciergeId: string) {
    const expiresAt = new Date(Date.now() + 1000 * 60 * 60 * 12).toISOString()
    return apiRequest<ConciergeAccessLink>(`/admin/trips/${tripId}/concierge-access-links`, {
      body: { conciergeId, expiresAt },
      method: 'POST',
    })
  },
  addTimelineEvent(
    tripId: string,
    eventType: TimelineEventType,
    note?: string,
    checkpointName?: string,
  ) {
    return apiRequest<TripTransitionResponse>(`/admin/trips/${tripId}/timeline-events`, {
      body: {
        eventType,
        note,
        checkpointName,
        idempotencyKey: crypto.randomUUID(),
      },
      method: 'POST',
    })
  },
  cancelTrip(tripId: string, note: string) {
    return apiRequest<TripTransitionResponse>(`/admin/trips/${tripId}/cancel`, {
      body: { note, idempotencyKey: crypto.randomUUID() },
      method: 'POST',
    })
  },
  notificationAttempts(tripId: string) {
    return apiRequest<NotificationAttempt[]>(`/admin/trips/${tripId}/notification-attempts`)
  },
  principals() {
    return apiRequest<AdminPrincipalSummary[]>('/admin/principals')
  },
  concierges() {
    return apiRequest<Concierge[]>('/admin/concierges')
  },
  createConcierge(payload: CreateConciergePayload) {
    return apiRequest<Concierge>('/admin/concierges', {
      body: payload,
      method: 'POST',
    })
  },
  createInvitation(payload: {
    fullName: string
    email: string
    phone?: string
    accountType: 'ADMIN' | 'PRINCIPAL'
  }) {
    return apiRequest<InvitationResponse>('/admin/invitations', {
      body: payload,
      method: 'POST',
    })
  },
}

export const conciergeApi = {
  trip(tripId: string, accessToken: string) {
    return apiRequest<ConciergeTripDetail>(
      `/concierge/trips/${tripId}?accessToken=${encodeURIComponent(accessToken)}`,
      { auth: false },
    )
  },
  addTimelineEvent(
    tripId: string,
    accessToken: string,
    eventType: TimelineEventType,
    note?: string,
    checkpointName?: string,
    offlineCreatedAt?: string,
    idempotencyKey?: string,
  ) {
    return apiRequest<TripTransitionResponse>(`/concierge/trips/${tripId}/timeline-events`, {
      auth: false,
      body: {
        accessToken,
        eventType,
        checkpointName,
        note,
        idempotencyKey: idempotencyKey ?? crypto.randomUUID(),
        offlineCreatedAt,
      },
      method: 'POST',
    })
  },
}

export const principalApi = {
  trips() {
    return apiRequest<PrincipalTripSummary[]>('/principal/trips')
  },
  trip(id: string) {
    return apiRequest<PrincipalTripDetail>(`/principal/trips/${id}`)
  },
  timeline(id: string) {
    return apiRequest(`/principal/trips/${id}/timeline`)
  },
  addWatcher(tripId: string, payload: CreateWatcherPayload) {
    return apiRequest<Watcher>(`/principal/trips/${tripId}/watchers`, {
      body: { ...payload, notificationChannel: 'EMAIL' },
      method: 'POST',
    })
  },
}

export function isActiveTrip(status: TripStatus) {
  return status !== 'COMPLETED' && status !== 'CANCELLED'
}
