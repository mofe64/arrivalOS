import type {
  AdminPrincipalSummary,
  AdminTripDetail,
  AdminTripListItem,
  Concierge,
  ConciergeTripDetail,
  PrincipalTripDetail,
  PrincipalTripSummary,
  TimelineEvent,
  TripCheckpoint,
  TripPrincipal,
  Watcher,
} from '../api/types'

const now = new Date()
const minutesAgo = (minutes: number) =>
  new Date(now.getTime() - minutes * 60_000).toISOString()
const hoursFromNow = (hours: number) =>
  new Date(now.getTime() + hours * 60 * 60_000).toISOString()

export const defaultCheckpoints: TripCheckpoint[] = [
  checkpoint('cp-1', 'Port Health', 1, 'COMPLETED', 76),
  checkpoint('cp-2', 'DSS', 2, 'COMPLETED', 58),
  checkpoint('cp-3', 'Immigration', 3, 'ACTIVE', 22),
  checkpoint('cp-4', 'Baggage Claim', 4, 'PENDING'),
  checkpoint('cp-5', 'Customs', 5, 'PENDING'),
  checkpoint('cp-6', 'NDLEA', 6, 'PENDING'),
  checkpoint('cp-7', 'Quarantine', 7, 'PENDING'),
]

export const fixtureConcierges: Concierge[] = [
  {
    id: '11111111-1111-4111-8111-111111111111',
    fullName: 'Tunde Adeyemi',
    phone: '+234 801 555 0142',
    photoUrl: '',
    publicId: 'GGS-TUNDE',
    active: true,
  },
  {
    id: '22222222-2222-4222-8222-222222222222',
    fullName: 'Mariam Bello',
    phone: '+234 802 555 0188',
    photoUrl: '',
    publicId: 'GGS-MARIAM',
    active: true,
  },
  {
    id: '33333333-3333-4333-8333-333333333333',
    fullName: 'Ifeanyi Cole',
    phone: '+234 803 555 0119',
    photoUrl: '',
    publicId: 'GGS-IFEANYI',
    active: false,
  },
]

const principals: TripPrincipal[] = [
  {
    id: 'p-1',
    userAccountId: 'principal-1',
    fullName: 'Amina Okonkwo',
    phone: '+44 7700 900123',
    photoUrl: '',
    primaryContact: true,
    sequenceNumber: 1,
  },
]

const watchers: Watcher[] = [
  {
    id: 'w-1',
    fullName: 'Kemi Okonkwo',
    email: 'kemi@example.com',
    phone: '+44 7700 900777',
    notificationChannel: 'EMAIL',
  },
  {
    id: 'w-2',
    fullName: 'Executive Office',
    email: 'ea@example.com',
    notificationChannel: 'EMAIL',
  },
]

const timelineEvents: TimelineEvent[] = [
  event('e-1', 'TRIP_CREATED', 'OPS', 190, 'Trip created and notification recipients confirmed.'),
  event('e-2', 'CONCIERGE_IN_POSITION', 'CONCIERGE', 54, 'Concierge confirmed at arrivals hall.'),
  event('e-3', 'CHECKPOINT_STARTED', 'CONCIERGE', 22, 'Immigration desk joined.', 'Immigration'),
]

export const fixtureAdminTrips: AdminTripListItem[] = [
  {
    id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    flightNumber: 'BA075',
    arrivalAirport: 'LOS',
    arrivalTerminal: 'International Terminal 2',
    scheduledArrivalAt: hoursFromNow(1),
    status: 'PROCESSING',
    primaryPrincipal: principals[0],
    principalCount: 1,
    assignedConcierge: fixtureConcierges[0],
    watcherCount: 2,
    lastTimelineEvent: timelineEvents[2],
    lastUpdatedAt: minutesAgo(22),
    stale: false,
  },
  {
    id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    flightNumber: 'QR1407',
    arrivalAirport: 'LOS',
    arrivalTerminal: 'International Terminal 1',
    scheduledArrivalAt: minutesAgo(48),
    status: 'FLIGHT_LANDED',
    primaryPrincipal: {
      ...principals[0],
      id: 'p-2',
      fullName: 'Daniel Reeves',
    },
    principalCount: 1,
    assignedConcierge: fixtureConcierges[1],
    watcherCount: 3,
    lastTimelineEvent: event('e-4', 'FLIGHT_LANDED', 'SYSTEM', 48, 'Flight landed.'),
    lastUpdatedAt: minutesAgo(48),
    stale: true,
  },
  {
    id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    flightNumber: 'KL587',
    arrivalAirport: 'ABV',
    arrivalTerminal: 'Main Terminal',
    scheduledArrivalAt: hoursFromNow(4),
    status: 'CREATED',
    primaryPrincipal: {
      ...principals[0],
      id: 'p-3',
      fullName: 'Nkechi Eze',
    },
    principalCount: 2,
    assignedConcierge: null,
    watcherCount: 1,
    lastTimelineEvent: event('e-5', 'TRIP_CREATED', 'OPS', 70, 'Arrival created.'),
    lastUpdatedAt: minutesAgo(70),
    stale: false,
  },
]

export const fixtureAdminTripDetail: AdminTripDetail = {
  ...fixtureAdminTrips[0],
  actualArrivalAt: null,
  principals,
  watchers,
  concierge: fixtureConcierges[0],
  checkpoints: defaultCheckpoints,
  timelineEvents,
  notificationAttempts: [
    {
      id: 'n-1',
      recipientType: 'WATCHER',
      recipientId: 'w-1',
      channel: 'EMAIL',
      provider: 'smtp',
      status: 'SENT',
      failureReason: null,
      sentAt: minutesAgo(21),
      deliveredAt: null,
      createdAt: minutesAgo(21),
    },
  ],
  currentCheckpoint: defaultCheckpoints[2],
  meetingPoint: 'Arrivals hall, left of the currency exchange desk.',
}

export const fixturePrincipals: AdminPrincipalSummary[] = [
  {
    id: 'principal-1',
    fullName: 'Amina Okonkwo',
    email: 'amina@example.com',
    phone: '+44 7700 900123',
    active: true,
    emailVerified: true,
    trips: [
      {
        id: fixtureAdminTrips[0].id,
        flightNumber: 'BA075',
        arrivalAirport: 'LOS',
        arrivalTerminal: 'International Terminal 2',
        scheduledArrivalAt: fixtureAdminTrips[0].scheduledArrivalAt,
        status: 'PROCESSING',
        concierge: fixtureConcierges[0],
        watchers,
      },
    ],
  },
  {
    id: 'principal-2',
    fullName: 'Daniel Reeves',
    email: 'daniel@example.com',
    active: true,
    emailVerified: false,
    trips: [],
  },
]

export const fixturePrincipalTrips: PrincipalTripSummary[] = [
  {
    id: fixtureAdminTrips[0].id,
    flightNumber: 'BA075',
    arrivalAirport: 'LOS',
    arrivalTerminal: 'International Terminal 2',
    scheduledArrivalAt: fixtureAdminTrips[0].scheduledArrivalAt,
    actualArrivalAt: null,
    status: 'PROCESSING',
    concierge: fixtureConcierges[0],
    meetingPoint: fixtureAdminTripDetail.meetingPoint,
    lastTimelineEvent: timelineEvents[2],
    lastUpdatedAt: minutesAgo(22),
    currentCheckpoint: defaultCheckpoints[2],
  },
]

export const fixturePrincipalTripDetail: PrincipalTripDetail = {
  id: fixtureAdminTripDetail.id,
  flightNumber: fixtureAdminTripDetail.flightNumber,
  arrivalAirport: fixtureAdminTripDetail.arrivalAirport,
  arrivalTerminal: fixtureAdminTripDetail.arrivalTerminal,
  scheduledArrivalAt: fixtureAdminTripDetail.scheduledArrivalAt,
  actualArrivalAt: fixtureAdminTripDetail.actualArrivalAt,
  status: fixtureAdminTripDetail.status,
  principals: fixtureAdminTripDetail.principals,
  concierge: fixtureAdminTripDetail.concierge,
  meetingPoint: fixtureAdminTripDetail.meetingPoint,
  checkpoints: fixtureAdminTripDetail.checkpoints,
  timelineEvents,
  lastUpdatedAt: fixtureAdminTripDetail.lastUpdatedAt,
  currentCheckpoint: fixtureAdminTripDetail.currentCheckpoint,
}

export const fixtureConciergeTripDetail: ConciergeTripDetail = {
  id: fixtureAdminTripDetail.id,
  flightNumber: fixtureAdminTripDetail.flightNumber,
  arrivalAirport: fixtureAdminTripDetail.arrivalAirport,
  arrivalTerminal: fixtureAdminTripDetail.arrivalTerminal,
  scheduledArrivalAt: fixtureAdminTripDetail.scheduledArrivalAt,
  actualArrivalAt: fixtureAdminTripDetail.actualArrivalAt,
  status: fixtureAdminTripDetail.status,
  principals: fixtureAdminTripDetail.principals,
  watchers,
  watcherCount: watchers.length,
  concierge: fixtureAdminTripDetail.concierge,
  meetingPoint: fixtureAdminTripDetail.meetingPoint,
  checkpoints: fixtureAdminTripDetail.checkpoints,
  timelineEvents,
  lastUpdatedAt: fixtureAdminTripDetail.lastUpdatedAt,
  currentCheckpoint: fixtureAdminTripDetail.currentCheckpoint,
  nextAllowedAction: {
    eventType: 'CHECKPOINT_COMPLETED',
    checkpointName: 'Immigration',
    label: 'Complete Immigration',
  },
}

function checkpoint(
  id: string,
  name: string,
  sequenceNumber: number,
  status: TripCheckpoint['status'],
  minutes?: number,
): TripCheckpoint {
  return {
    id,
    name,
    sequenceNumber,
    status,
    startedAt: status === 'ACTIVE' || status === 'COMPLETED' ? minutesAgo(minutes ?? 10) : null,
    completedAt: status === 'COMPLETED' ? minutesAgo(Math.max((minutes ?? 10) - 8, 1)) : null,
    skippedAt: status === 'SKIPPED' ? minutesAgo(minutes ?? 10) : null,
  }
}

function event(
  id: string,
  eventType: TimelineEvent['eventType'],
  actorType: TimelineEvent['actorType'],
  minutes: number,
  note: string,
  checkpointName?: string,
): TimelineEvent {
  return {
    id,
    eventType,
    actorType,
    actorId: null,
    checkpointName,
    note,
    idempotencyKey: id,
    occurredAt: minutesAgo(minutes),
    offlineCreatedAt: null,
  }
}
