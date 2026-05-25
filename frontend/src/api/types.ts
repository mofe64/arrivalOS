export type AccountType = 'ADMIN' | 'PRINCIPAL'

export type TripStatus =
  | 'CREATED'
  | 'FLIGHT_APPROACHING'
  | 'CONCIERGE_IN_POSITION'
  | 'FLIGHT_LANDED'
  | 'CLIENT_MET'
  | 'PROCESSING'
  | 'TERMINAL_EXITED'
  | 'HANDOVER_COMPLETED'
  | 'COMPLETED'
  | 'CANCELLED'

export type TimelineEventType =
  | 'TRIP_CREATED'
  | 'FLIGHT_APPROACHING'
  | 'CONCIERGE_IN_POSITION'
  | 'FLIGHT_LANDED'
  | 'CLIENT_MET'
  | 'CHECKPOINT_STARTED'
  | 'CHECKPOINT_COMPLETED'
  | 'TERMINAL_EXITED'
  | 'HANDOVER_COMPLETED'
  | 'TRIP_COMPLETED'
  | 'TRIP_CANCELLED'

export type ActorType = 'OPS' | 'CONCIERGE' | 'SYSTEM' | 'PRINCIPAL'
export type CheckpointStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'SKIPPED'
export type NotificationChannel = 'EMAIL' | 'SMS'
export type NotificationStatus = 'PENDING' | 'SENT' | 'DELIVERED' | 'FAILED'
export type RecipientType = 'PRINCIPAL' | 'WATCHER' | 'OPS'

export type FieldError = {
  field: string
  message: string
}

export type ApiErrorBody = {
  timestamp?: string
  status?: number
  code?: string
  message?: string
  path?: string
  requestId?: string
  fieldErrors?: FieldError[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly path?: string
  readonly requestId?: string
  readonly fieldErrors: FieldError[]

  constructor(body: ApiErrorBody, fallbackStatus: number) {
    super(body.message || 'The request could not be completed')
    this.name = 'ApiError'
    this.status = body.status ?? fallbackStatus
    this.code = body.code
    this.path = body.path
    this.requestId = body.requestId
    this.fieldErrors = body.fieldErrors ?? []
  }
}

export type AuthUser = {
  id: string
  fullName: string
  email: string
  phone?: string | null
  accountType: AccountType
  active?: boolean
  emailVerified: boolean
}

export type AuthResponse = {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessTokenExpiresAt: string
  refreshTokenExpiresAt: string
  user: AuthUser
}

export type MessageResponse = {
  message: string
}

export type InvitationResponse = {
  id: string
  fullName: string
  email: string
  phone?: string | null
  accountType: AccountType
  expiresAt: string
  accepted: boolean
}

export type Concierge = {
  id: string
  fullName: string
  phone: string
  photoUrl?: string | null
  publicId: string
  active: boolean
}

export type TripPrincipal = {
  id: string
  userAccountId?: string | null
  fullName: string
  phone?: string | null
  photoUrl?: string | null
  primaryContact: boolean
  sequenceNumber: number
}

export type Watcher = {
  id: string
  fullName: string
  email?: string | null
  phone?: string | null
  notificationChannel?: NotificationChannel | null
}

export type TripCheckpoint = {
  id: string
  name: string
  sequenceNumber: number
  status: CheckpointStatus
  startedAt?: string | null
  completedAt?: string | null
  skippedAt?: string | null
}

export type TimelineEvent = {
  id: string
  eventType: TimelineEventType
  actorType: ActorType
  actorId?: string | null
  checkpointName?: string | null
  note?: string | null
  idempotencyKey?: string | null
  occurredAt: string
  offlineCreatedAt?: string | null
}

export type NotificationAttempt = {
  id: string
  recipientType: RecipientType
  recipientId: string
  channel: NotificationChannel
  provider?: string | null
  status: NotificationStatus
  failureReason?: string | null
  sentAt?: string | null
  deliveredAt?: string | null
  createdAt: string
}

export type ConciergeAccessLink = {
  tripId: string
  conciergeId: string
  token: string
  updateUrl: string
  expiresAt: string
}

export type TripTransitionResponse = {
  tripId: string
  status: TripStatus
  event: TimelineEvent
  duplicate: boolean
}

export type AdminTripListItem = {
  id: string
  flightNumber: string
  arrivalAirport: string
  arrivalTerminal?: string | null
  scheduledArrivalAt?: string | null
  status: TripStatus
  primaryPrincipal?: TripPrincipal | null
  principalCount: number
  assignedConcierge?: Concierge | null
  watcherCount: number
  lastTimelineEvent?: TimelineEvent | null
  lastUpdatedAt?: string | null
  stale: boolean
}

export type AdminTripDetail = {
  id: string
  flightNumber: string
  arrivalAirport: string
  arrivalTerminal?: string | null
  scheduledArrivalAt?: string | null
  actualArrivalAt?: string | null
  status: TripStatus
  principals: TripPrincipal[]
  watchers: Watcher[]
  concierge?: Concierge | null
  checkpoints: TripCheckpoint[]
  timelineEvents: TimelineEvent[]
  notificationAttempts: NotificationAttempt[]
  lastUpdatedAt?: string | null
  currentCheckpoint?: TripCheckpoint | null
  meetingPoint?: string | null
}

export type PrincipalTripSummary = {
  id: string
  flightNumber: string
  arrivalAirport: string
  arrivalTerminal?: string | null
  scheduledArrivalAt?: string | null
  actualArrivalAt?: string | null
  status: TripStatus
  concierge?: Concierge | null
  meetingPoint?: string | null
  lastTimelineEvent?: TimelineEvent | null
  lastUpdatedAt?: string | null
  currentCheckpoint?: TripCheckpoint | null
}

export type PrincipalTripDetail = {
  id: string
  flightNumber: string
  arrivalAirport: string
  arrivalTerminal?: string | null
  scheduledArrivalAt?: string | null
  actualArrivalAt?: string | null
  status: TripStatus
  principals: TripPrincipal[]
  concierge?: Concierge | null
  meetingPoint?: string | null
  checkpoints: TripCheckpoint[]
  timelineEvents: TimelineEvent[]
  lastUpdatedAt?: string | null
  currentCheckpoint?: TripCheckpoint | null
}

export type ConciergeTripDetail = {
  id: string
  flightNumber: string
  arrivalAirport: string
  arrivalTerminal?: string | null
  scheduledArrivalAt?: string | null
  actualArrivalAt?: string | null
  status: TripStatus
  principals: TripPrincipal[]
  watchers?: Watcher[]
  watcherCount: number
  concierge?: Concierge | null
  meetingPoint?: string | null
  checkpoints: TripCheckpoint[]
  timelineEvents: TimelineEvent[]
  lastUpdatedAt?: string | null
  currentCheckpoint?: TripCheckpoint | null
  nextAllowedAction?: {
    eventType: TimelineEventType
    checkpointName?: string | null
    label: string
  } | null
}

export type AdminPrincipalTrip = {
  id: string
  flightNumber: string
  arrivalAirport: string
  arrivalTerminal?: string | null
  scheduledArrivalAt?: string | null
  status: TripStatus
  concierge?: Concierge | null
  watchers: Watcher[]
}

export type AdminPrincipalSummary = {
  id: string
  fullName: string
  email: string
  phone?: string | null
  active: boolean
  emailVerified: boolean
  trips: AdminPrincipalTrip[]
}

export type CreateTripPayload = {
  flightNumber: string
  arrivalAirport: string
  arrivalTerminal?: string
  meetingPoint?: string
  scheduledArrivalAt?: string
  principals: Array<{
    userAccountId?: string
    fullName?: string
    phone?: string
    photoUrl?: string
    primaryContact?: boolean
  }>
  watchers?: Array<{
    fullName: string
    email: string
    phone?: string
  }>
  assignedConciergeId?: string
  checkpoints?: Array<{ name: string }>
}

export type CreateWatcherPayload = {
  fullName: string
  email?: string
  phone?: string
  notificationChannel?: NotificationChannel
}

export type CreateConciergePayload = {
  fullName: string
  phone: string
  photoUrl?: string
  publicId: string
}
