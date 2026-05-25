import type {
  Concierge,
  TimelineEvent,
  TripCheckpoint,
  TripPrincipal,
  TripStatus,
  Watcher,
} from '../api/types'
import { eventLabel, relativeTime, shortDateTime, statusLabel, statusTone } from './format'

export function TripStatusBand({
  status,
  principalName,
  flightNumber,
  airport,
  lastUpdatedAt,
  currentCheckpoint,
}: {
  status: TripStatus
  principalName?: string
  flightNumber?: string
  airport?: string
  lastUpdatedAt?: string | null
  currentCheckpoint?: TripCheckpoint | null
}) {
  return (
    <section className="status-band" data-tone={statusTone(status)}>
      <div>
        <p className="eyebrow">Current trip state</p>
        <h1>{statusLabel(status)}</h1>
        {currentCheckpoint && <p>Current: {currentCheckpoint.name}</p>}
      </div>
      <dl>
        <div>
          <dt>Principal</dt>
          <dd>{principalName ?? 'Principal pending'}</dd>
        </div>
        <div>
          <dt>Flight</dt>
          <dd>{flightNumber ?? 'Not set'}</dd>
        </div>
        <div>
          <dt>Airport</dt>
          <dd>{airport ?? 'Not set'}</dd>
        </div>
        <div>
          <dt>Last trusted update</dt>
          <dd>{relativeTime(lastUpdatedAt)}</dd>
        </div>
      </dl>
    </section>
  )
}

export function TimelineFeed({
  events,
  emptyLabel = 'No timeline events have been recorded yet.',
}: {
  events: TimelineEvent[]
  emptyLabel?: string
}) {
  if (events.length === 0) {
    return <p className="muted-copy">{emptyLabel}</p>
  }

  return (
    <ol className="timeline-feed">
      {events.map((event) => (
        <TimelineItem event={event} key={event.id} />
      ))}
    </ol>
  )
}

export function TimelineItem({ event }: { event: TimelineEvent }) {
  return (
    <li className="timeline-item">
      <span className="timeline-dot" aria-hidden="true" />
      <div>
        <p>
          <strong>{eventLabel(event.eventType)}</strong>
          {event.checkpointName && <span>{event.checkpointName}</span>}
        </p>
        <small>
          {shortDateTime(event.occurredAt)} · {actorLabel(event.actorType)}
        </small>
        {event.note && <p className="timeline-note">{event.note}</p>}
      </div>
    </li>
  )
}

export function CheckpointTimeline({ checkpoints }: { checkpoints: TripCheckpoint[] }) {
  return (
    <ol className="checkpoint-list">
      {checkpoints.map((checkpoint) => (
        <li className="checkpoint-item" data-status={checkpoint.status.toLowerCase()} key={checkpoint.id}>
          <span>{checkpoint.sequenceNumber}</span>
          <div>
            <strong>
              {checkpoint.status === 'ACTIVE' ? `Current: ${checkpoint.name}` : checkpoint.name}
            </strong>
            <small>{checkpoint.status.toLowerCase().replace('_', ' ')}</small>
          </div>
        </li>
      ))}
    </ol>
  )
}

export function ConciergeIdentityCard({ concierge }: { concierge?: Concierge | null }) {
  if (!concierge) {
    return (
      <article className="identity-card">
        <Avatar name="Unassigned" />
        <div>
          <p className="eyebrow">Concierge</p>
          <h2>Assignment pending</h2>
          <p className="muted-copy">Assign a concierge before issuing a trip-scoped access link.</p>
        </div>
      </article>
    )
  }

  return (
    <article className="identity-card">
      <Avatar name={concierge.fullName} src={concierge.photoUrl} />
      <div>
        <p className="eyebrow">Concierge</p>
        <h2>{concierge.fullName}</h2>
        <p>{concierge.phone}</p>
        <small>{concierge.publicId} · {concierge.active ? 'Active' : 'Inactive'}</small>
      </div>
    </article>
  )
}

export function PrincipalIdentityBlock({ principals }: { principals: TripPrincipal[] }) {
  return (
    <div className="principal-block">
      {principals.map((principal) => (
        <article className="principal-row" key={principal.id}>
          <Avatar name={principal.fullName} src={principal.photoUrl} />
          <div>
            <strong>{principal.fullName}</strong>
            <small>
              {principal.primaryContact ? 'Primary contact' : `Passenger ${principal.sequenceNumber}`}
              {principal.phone ? ` · ${principal.phone}` : ''}
            </small>
          </div>
        </article>
      ))}
    </div>
  )
}

export function NotificationRecipientList({ watchers }: { watchers: Watcher[] }) {
  if (watchers.length === 0) {
    return <p className="muted-copy">No email notification recipients have been added.</p>
  }

  return (
    <ul className="recipient-list">
      {watchers.map((watcher) => (
        <li key={watcher.id}>
          <strong>{watcher.fullName}</strong>
          <span>{watcher.email ?? watcher.phone ?? 'Contact pending'}</span>
          <small>{watcher.notificationChannel ?? 'EMAIL'} updates</small>
        </li>
      ))}
    </ul>
  )
}

function Avatar({ name, src }: { name: string; src?: string | null }) {
  if (src) {
    return <img className="avatar" src={src} alt="" />
  }
  return <span className="avatar" aria-hidden="true">{initials(name)}</span>
}

function initials(name: string) {
  return name
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()
}

function actorLabel(actorType: TimelineEvent['actorType']) {
  const labels: Record<TimelineEvent['actorType'], string> = {
    OPS: 'Gbèjà ops',
    CONCIERGE: 'Concierge',
    SYSTEM: 'System',
    PRINCIPAL: 'Principal',
  }
  return labels[actorType]
}
