import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { principalApi } from '../../api/arrivalos'
import { withFixtureFallback } from '../../api/fallback'
import { fixturePrincipalTripDetail } from '../../data/fixtures'
import {
  CheckpointTimeline,
  ConciergeIdentityCard,
  TimelineFeed,
  TripStatusBand,
} from '../../components/ArrivalComponents'
import { ApiErrorMessage, LoadingState } from '../../components/Primitives'
import { shortDateTime } from '../../components/format'

export function PrincipalTripDetailPage() {
  const { tripId } = useParams({ from: '/principal/trips/$tripId' })
  const queryClient = useQueryClient()
  const [watcherName, setWatcherName] = useState('')
  const [watcherEmail, setWatcherEmail] = useState('')
  const tripQuery = useQuery({
    queryKey: ['principal', 'trip', tripId],
    queryFn: () => withFixtureFallback(() => principalApi.trip(tripId), { ...fixturePrincipalTripDetail, id: tripId }),
  })
  const addWatcher = useMutation({
    mutationFn: () => principalApi.addWatcher(tripId, { fullName: watcherName, email: watcherEmail, notificationChannel: 'EMAIL' }),
    onSuccess: () => {
      setWatcherName('')
      setWatcherEmail('')
      void queryClient.invalidateQueries({ queryKey: ['principal', 'trip', tripId] })
    },
  })
  const trip = tripQuery.data

  function handleWatcher(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    addWatcher.mutate()
  }

  if (tripQuery.isLoading) return <LoadingState />
  if (!trip) return <ApiErrorMessage error={tripQuery.error} />

  return (
    <section className="principal-page">
      <TripStatusBand
        status={trip.status}
        principalName={trip.principals[0]?.fullName}
        flightNumber={trip.flightNumber}
        airport={trip.arrivalAirport}
        lastUpdatedAt={trip.lastUpdatedAt}
        currentCheckpoint={trip.currentCheckpoint}
      />

      <div className="principal-detail-grid">
        <article className="principal-focus-panel">
          <ConciergeIdentityCard concierge={trip.concierge} />
          <div className="meeting-point">
            <p className="eyebrow">Meeting point</p>
            <h2>{trip.meetingPoint ?? 'Gbèjà will confirm your meeting point before arrival.'}</h2>
            <p>{trip.arrivalAirport} {trip.arrivalTerminal ? `· ${trip.arrivalTerminal}` : ''} · {shortDateTime(trip.scheduledArrivalAt)}</p>
          </div>
          {trip.concierge?.phone && <a className="primary-link call-link" href={`tel:${trip.concierge.phone}`}>Call concierge</a>}
        </article>

        <article className="timeline-panel">
          <div className="panel-heading"><h2>Your arrival timeline</h2><span>Customer-safe updates</span></div>
          <TimelineFeed events={trip.timelineEvents} />
        </article>
      </div>

      <section className="detail-grid">
        <article className="ops-panel">
          <h2>Current checkpoints</h2>
          <CheckpointTimeline checkpoints={trip.checkpoints} />
        </article>
        <form className="ops-panel stack-form" onSubmit={handleWatcher}>
          <h2>Add email recipient</h2>
          <p className="muted-copy">Add a trusted contact to receive email updates for this trip.</p>
          <ApiErrorMessage error={addWatcher.error} />
          <label className="field"><span>Name</span><input value={watcherName} onChange={(event) => setWatcherName(event.target.value)} /></label>
          <label className="field"><span>Email</span><input type="email" value={watcherEmail} onChange={(event) => setWatcherEmail(event.target.value)} /></label>
          <button className="primary-button" disabled={!watcherName || !watcherEmail || addWatcher.isPending} type="submit">Add recipient</button>
        </form>
      </section>
    </section>
  )
}
