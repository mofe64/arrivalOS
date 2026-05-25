import type { TimelineEventType, TripStatus } from '../api/types'

export type EventOption = {
  eventType: TimelineEventType
  label: string
  checkpointRequired?: boolean
}

export const eventOptions: EventOption[] = [
  { eventType: 'CONCIERGE_IN_POSITION', label: 'Concierge in position' },
  { eventType: 'FLIGHT_LANDED', label: 'Flight landed' },
  { eventType: 'CLIENT_MET', label: 'Client met' },
  { eventType: 'CHECKPOINT_STARTED', label: 'Checkpoint started', checkpointRequired: true },
  { eventType: 'CHECKPOINT_COMPLETED', label: 'Checkpoint completed', checkpointRequired: true },
  { eventType: 'TERMINAL_EXITED', label: 'Terminal exited' },
  { eventType: 'HANDOVER_COMPLETED', label: 'Handover completed' },
  { eventType: 'TRIP_COMPLETED', label: 'Trip completed' },
]

export function nextEventForStatus(status: TripStatus): EventOption | null {
  const nextByStatus: Record<TripStatus, TimelineEventType | null> = {
    CREATED: 'CONCIERGE_IN_POSITION',
    FLIGHT_APPROACHING: 'CONCIERGE_IN_POSITION',
    CONCIERGE_IN_POSITION: 'FLIGHT_LANDED',
    FLIGHT_LANDED: 'CLIENT_MET',
    CLIENT_MET: 'CHECKPOINT_STARTED',
    PROCESSING: 'CHECKPOINT_COMPLETED',
    TERMINAL_EXITED: 'HANDOVER_COMPLETED',
    HANDOVER_COMPLETED: 'TRIP_COMPLETED',
    COMPLETED: null,
    CANCELLED: null,
  }
  return eventOptions.find((option) => option.eventType === nextByStatus[status]) ?? null
}

export function isClosedStatus(status: TripStatus) {
  return status === 'COMPLETED' || status === 'CANCELLED'
}
