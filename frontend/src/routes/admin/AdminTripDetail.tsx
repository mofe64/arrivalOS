import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { adminApi } from '../../api/arrivalos'
import { withFixtureFallback } from '../../api/fallback'
import type { ConciergeAccessLink, TimelineEventType, TripStatus } from '../../api/types'
import { fixtureAdminTripDetail, fixtureConcierges } from '../../data/fixtures'
import {
  CheckpointTimeline,
  ConciergeIdentityCard,
  NotificationRecipientList,
  PrincipalIdentityBlock,
  TimelineFeed,
  TripStatusBand,
} from '../../components/ArrivalComponents'
import { ApiErrorMessage, LoadingState } from '../../components/Primitives'
import { shortDateTime, statusLabel } from '../../components/format'
import { eventOptions, isClosedStatus, nextEventForStatus } from '../../components/tripFlow'

export function AdminTripDetailPage() {
  const { tripId } = useParams({ from: '/admin/trips/$tripId' })
  const queryClient = useQueryClient()
  const tripQuery = useQuery({
    queryKey: ['admin', 'trip', tripId],
    queryFn: () => withFixtureFallback(() => adminApi.trip(tripId), { ...fixtureAdminTripDetail, id: tripId }),
  })
  const conciergesQuery = useQuery({
    queryKey: ['admin', 'concierges'],
    queryFn: () => withFixtureFallback(adminApi.concierges, fixtureConcierges),
  })
  const trip = tripQuery.data
  const invalidateTrip = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'trip', tripId] })
    void queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] })
    void queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
  }

  const addEvent = useMutation({
    mutationFn: ({ eventType, note, checkpointName }: { eventType: TimelineEventType; note?: string; checkpointName?: string }) =>
      adminApi.addTimelineEvent(tripId, eventType, note, checkpointName),
    onSuccess: invalidateTrip,
  })
  const addWatcher = useMutation({
    mutationFn: (payload: { fullName: string; email: string }) => adminApi.addWatcher(tripId, { ...payload, notificationChannel: 'EMAIL' }),
    onSuccess: invalidateTrip,
  })
  const addPrincipal = useMutation({
    mutationFn: (payload: { fullName: string; phone?: string }) => adminApi.addPrincipal(tripId, payload),
    onSuccess: invalidateTrip,
  })
  const assign = useMutation({
    mutationFn: (conciergeId: string) => adminApi.assignConcierge(tripId, conciergeId),
    onSuccess: invalidateTrip,
  })
  const accessLink = useMutation({
    mutationFn: (conciergeId: string) => adminApi.createConciergeAccessLink(tripId, conciergeId),
  })
  const cancel = useMutation({
    mutationFn: (note: string) => adminApi.cancelTrip(tripId, note),
    onSuccess: invalidateTrip,
  })

  if (tripQuery.isLoading) return <LoadingState />
  if (!trip) return <ApiErrorMessage error={tripQuery.error} />

  const closed = isClosedStatus(trip.status)

  return (
    <div className="detail-layout">
      <TripStatusBand
        status={trip.status}
        principalName={trip.principals[0]?.fullName}
        flightNumber={trip.flightNumber}
        airport={trip.arrivalAirport}
        lastUpdatedAt={trip.lastUpdatedAt}
        currentCheckpoint={trip.currentCheckpoint}
      />
      <ApiErrorMessage error={tripQuery.error} />

      <section className="detail-main">
        <article className="timeline-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Source of truth</p>
              <h2>Timeline feed</h2>
            </div>
            <span>{statusLabel(trip.status)}</span>
          </div>
          <TimelineFeed events={trip.timelineEvents} />
        </article>

        <aside className="side-stack">
          <ConciergeIdentityCard concierge={trip.concierge} />
          <InfoPanel title="Flight details">
            <dl className="compact-dl">
              <div><dt>Flight</dt><dd>{trip.flightNumber}</dd></div>
              <div><dt>Airport</dt><dd>{trip.arrivalAirport}</dd></div>
              <div><dt>Terminal</dt><dd>{trip.arrivalTerminal ?? 'Not set'}</dd></div>
              <div><dt>Meeting point</dt><dd>{trip.meetingPoint ?? 'Not set'}</dd></div>
              <div><dt>Scheduled</dt><dd>{shortDateTime(trip.scheduledArrivalAt)}</dd></div>
            </dl>
            {!trip.meetingPoint && <p className="warning-note">Meeting point is not set. Principal and concierge views will show fallback instructions.</p>}
          </InfoPanel>
          <InfoPanel title="Principals"><PrincipalIdentityBlock principals={trip.principals} /></InfoPanel>
          <InfoPanel title="Notification recipients"><NotificationRecipientList watchers={trip.watchers} /></InfoPanel>
        </aside>
      </section>

      <section className="detail-grid">
        <article className="ops-panel">
          <h2>Checkpoints</h2>
          <CheckpointTimeline checkpoints={trip.checkpoints} />
        </article>
        <AdminActions
          addEvent={(eventType, note, checkpointName) => addEvent.mutate({ eventType, note, checkpointName })}
          addEventPending={addEvent.isPending}
          addEventError={addEvent.error}
          addPrincipal={(payload) => addPrincipal.mutate(payload)}
          addPrincipalError={addPrincipal.error}
          addWatcher={(payload) => addWatcher.mutate(payload)}
          addWatcherError={addWatcher.error}
          assignConcierge={(conciergeId) => assign.mutate(conciergeId)}
          assignError={assign.error}
          cancelTrip={(note) => cancel.mutate(note)}
          cancelError={cancel.error}
          conciergeId={trip.concierge?.id}
          concierges={conciergesQuery.data ?? []}
          currentCheckpointName={trip.currentCheckpoint?.name}
          disabled={closed}
          issueAccessLink={(conciergeId) => accessLink.mutate(conciergeId)}
          issueAccessLinkError={accessLink.error}
          accessLink={accessLink.data}
          tripStatus={trip.status}
        />
        <article className="ops-panel">
          <h2>Email notification attempts</h2>
          {trip.notificationAttempts.length === 0 ? (
            <p className="muted-copy">No email attempts recorded yet.</p>
          ) : (
            <ul className="attempt-list">
              {trip.notificationAttempts.map((attempt) => (
                <li key={attempt.id}>
                  <strong>{attempt.channel} · {attempt.status}</strong>
                  <span>{attempt.provider ?? 'provider pending'} · {shortDateTime(attempt.createdAt)}</span>
                  {attempt.failureReason && <small>{attempt.failureReason}</small>}
                </li>
              ))}
            </ul>
          )}
        </article>
      </section>
    </div>
  )
}

function AdminActions({
  addEvent,
  addEventPending,
  addEventError,
  addPrincipal,
  addPrincipalError,
  addWatcher,
  addWatcherError,
  assignConcierge,
  assignError,
  cancelTrip,
  cancelError,
  conciergeId,
  concierges,
  currentCheckpointName,
  disabled,
  issueAccessLink,
  issueAccessLinkError,
  accessLink,
  tripStatus,
}: {
  addEvent: (eventType: TimelineEventType, note?: string, checkpointName?: string) => void
  addEventPending: boolean
  addEventError: unknown
  addPrincipal: (payload: { fullName: string; phone?: string }) => void
  addPrincipalError: unknown
  addWatcher: (payload: { fullName: string; email: string }) => void
  addWatcherError: unknown
  assignConcierge: (conciergeId: string) => void
  assignError: unknown
  cancelTrip: (note: string) => void
  cancelError: unknown
  conciergeId?: string
  concierges: Array<{ id: string; fullName: string }>
  currentCheckpointName?: string
  disabled: boolean
  issueAccessLink: (conciergeId: string) => void
  issueAccessLinkError: unknown
  accessLink?: ConciergeAccessLink
  tripStatus: TripStatus
}) {
  const suggestedEvent = nextEventForStatus(tripStatus)
  const [eventType, setEventType] = useState<TimelineEventType>(suggestedEvent?.eventType ?? 'CONCIERGE_IN_POSITION')
  const [checkpointName, setCheckpointName] = useState(currentCheckpointName ?? '')
  const [note, setNote] = useState('')
  const [principalName, setPrincipalName] = useState('')
  const [principalPhone, setPrincipalPhone] = useState('')
  const [watcherName, setWatcherName] = useState('')
  const [watcherEmail, setWatcherEmail] = useState('')
  const [watcherValidation, setWatcherValidation] = useState('')
  const [selectedConcierge, setSelectedConcierge] = useState(conciergeId ?? '')
  const selectedEvent = eventOptions.find((option) => option.eventType === eventType)

  function submitEvent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (selectedEvent?.checkpointRequired && !checkpointName.trim()) {
      return
    }
    addEvent(eventType, note || undefined, checkpointName || undefined)
    setNote('')
  }

  function submitWatcher() {
    const validEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(watcherEmail.trim())
    if (!watcherName.trim() || !validEmail) {
      setWatcherValidation('Enter a recipient name and a valid email address.')
      return
    }
    setWatcherValidation('')
    addWatcher({ fullName: watcherName.trim(), email: watcherEmail.trim() })
  }

  function submitPrincipal() {
    if (!principalName.trim()) return
    addPrincipal({ fullName: principalName.trim(), phone: principalPhone.trim() || undefined })
  }

  async function copyAccessLink() {
    if (!accessLink?.updateUrl) return
    await navigator.clipboard.writeText(accessLink.updateUrl)
  }

  return (
    <article className="ops-panel">
      <h2>Admin actions</h2>
      {disabled && <p className="warning-note">This trip is closed. Operational mutations are disabled.</p>}
      <form className="stack-form" onSubmit={submitEvent}>
        <ApiErrorMessage error={addEventError} />
        <label className="field">
          <span>Timeline event {suggestedEvent ? `(suggested: ${suggestedEvent.label})` : ''}</span>
          <select value={eventType} onChange={(event) => setEventType(event.target.value as TimelineEventType)}>
            {eventOptions.map((option) => (
              <option key={option.eventType} value={option.eventType}>{option.label}</option>
            ))}
          </select>
        </label>
        {selectedEvent?.checkpointRequired && (
          <label className="field"><span>Checkpoint name</span><input value={checkpointName} onChange={(event) => setCheckpointName(event.target.value)} /></label>
        )}
        <label className="field"><span>Ops note</span><textarea value={note} onChange={(event) => setNote(event.target.value)} /></label>
        <button className="primary-button" disabled={disabled || addEventPending || Boolean(selectedEvent?.checkpointRequired && !checkpointName.trim())} type="submit">Submit timeline event</button>
      </form>

      <div className="inline-form">
        <ApiErrorMessage error={addPrincipalError} />
        <input aria-label="Principal name" placeholder="Principal name" value={principalName} onChange={(event) => setPrincipalName(event.target.value)} />
        <input aria-label="Principal phone" placeholder="Phone optional" value={principalPhone} onChange={(event) => setPrincipalPhone(event.target.value)} />
        <button disabled={disabled || !principalName.trim()} type="button" onClick={submitPrincipal}>Add principal</button>
      </div>

      <div className="inline-form">
        <ApiErrorMessage error={addWatcherError} />
        {watcherValidation && <p className="warning-note">{watcherValidation}</p>}
        <input aria-label="Watcher name" placeholder="Recipient name" value={watcherName} onChange={(event) => setWatcherName(event.target.value)} />
        <input aria-label="Watcher email" placeholder="Email address" type="email" value={watcherEmail} onChange={(event) => setWatcherEmail(event.target.value)} />
        <button disabled={disabled} type="button" onClick={submitWatcher}>Add email recipient</button>
      </div>

      <div className="inline-form">
        <ApiErrorMessage error={assignError} />
        <ApiErrorMessage error={issueAccessLinkError} />
        <select aria-label="Assign concierge" value={selectedConcierge} onChange={(event) => setSelectedConcierge(event.target.value)}>
          <option value="">Select concierge</option>
          {concierges.map((concierge) => <option key={concierge.id} value={concierge.id}>{concierge.fullName}</option>)}
        </select>
        <button disabled={disabled || !selectedConcierge} type="button" onClick={() => assignConcierge(selectedConcierge)}>Assign/change</button>
        <button disabled={disabled || !selectedConcierge} type="button" onClick={() => issueAccessLink(selectedConcierge)}>Issue access link</button>
      </div>
      {accessLink && (
        <div className="access-link-panel">
          <p className="eyebrow">Concierge access link</p>
          <a href={accessLink.updateUrl}>{accessLink.updateUrl}</a>
          <small>Expires {shortDateTime(accessLink.expiresAt)}</small>
          <button className="secondary-button" type="button" onClick={copyAccessLink}>Copy link</button>
        </div>
      )}
      <ApiErrorMessage error={cancelError} />
      <button className="danger-button" disabled={disabled} type="button" onClick={() => cancelTrip('Cancelled from admin detail view')}>Cancel trip</button>
    </article>
  )
}

function InfoPanel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <article className="info-panel">
      <h2>{title}</h2>
      {children}
    </article>
  )
}
