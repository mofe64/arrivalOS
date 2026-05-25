import type { TimelineEventType } from './types'

const QUEUE_PREFIX = 'arrivalos.concierge.queue.'

export type QueuedConciergeAction = {
  id: string
  tripId: string
  accessToken: string
  eventType: TimelineEventType
  idempotencyKey: string
  note?: string
  checkpointName?: string
  offlineCreatedAt: string
}

export function readQueue(tripId: string): QueuedConciergeAction[] {
  try {
    const raw = window.localStorage.getItem(`${QUEUE_PREFIX}${tripId}`)
    const parsed = raw ? (JSON.parse(raw) as QueuedConciergeAction[]) : []
    return parsed.map((action) => ({
      ...action,
      idempotencyKey: action.idempotencyKey ?? action.id,
    }))
  } catch {
    return []
  }
}

export function enqueueAction(action: Omit<QueuedConciergeAction, 'id' | 'offlineCreatedAt' | 'idempotencyKey'> & { idempotencyKey?: string }) {
  const idempotencyKey = action.idempotencyKey ?? crypto.randomUUID()
  const nextAction: QueuedConciergeAction = {
    ...action,
    id: idempotencyKey,
    idempotencyKey,
    offlineCreatedAt: new Date().toISOString(),
  }
  const queue = [...readQueue(action.tripId), nextAction]
  writeQueue(action.tripId, queue)
  return nextAction
}

export function removeQueuedAction(tripId: string, id: string) {
  writeQueue(tripId, readQueue(tripId).filter((action) => action.id !== id))
}

export function writeQueue(tripId: string, queue: QueuedConciergeAction[]) {
  window.localStorage.setItem(`${QUEUE_PREFIX}${tripId}`, JSON.stringify(queue))
}
