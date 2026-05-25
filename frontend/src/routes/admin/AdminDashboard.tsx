import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { adminApi, isActiveTrip } from '../../api/arrivalos'
import { withFixtureFallback } from '../../api/fallback'
import type { NotificationAttempt } from '../../api/types'
import { fixtureAdminTripDetail, fixtureAdminTrips } from '../../data/fixtures'
import { ApiErrorMessage, LoadingState } from '../../components/Primitives'
import { relativeTime, statusLabel } from '../../components/format'

export function AdminDashboard() {
  const tripsQuery = useQuery({
    queryKey: ['admin', 'trips', 'dashboard'],
    queryFn: () => withFixtureFallback(() => adminApi.listTrips(true), fixtureAdminTrips),
  })
  const trips = tripsQuery.data ?? []
  const attemptsQuery = useQuery({
    queryKey: ['admin', 'dashboard', 'notification-attempts', trips.map((trip) => trip.id).join(',')],
    enabled: trips.length > 0,
    queryFn: async () => {
      const attemptGroups = await Promise.all(
        trips.map((trip) =>
          withFixtureFallback(
            () => adminApi.notificationAttempts(trip.id),
            trip.id === fixtureAdminTripDetail.id ? fixtureAdminTripDetail.notificationAttempts : [],
          ),
        ),
      )
      return attemptGroups.flat()
    },
  })
  const attempts: NotificationAttempt[] = attemptsQuery.data ?? []
  const attentionTrips = trips.filter((trip) => trip.stale || !trip.assignedConcierge)
  const emailFailures = attempts.filter((attempt) => attempt.status === 'FAILED').length

  return (
    <>
      <header className="dashboard-header">
        <div className="dashboard-copy">
          <p className="eyebrow">Ops dashboard</p>
          <h1>Arrival command overview</h1>
          <p>Monitor active arrivals, stale updates, and concierge readiness from one trusted timeline register.</p>
          <div className="command-state" aria-label="Current operations state">
            <span>Live board</span>
            <span>{attentionTrips.length} need attention</span>
            <span>Email failures: {emailFailures}</span>
          </div>
        </div>
        <div className="dashboard-actions" aria-label="Admin actions">
          <Link to="/admin/trips/new">New trip</Link>
          <Link to="/admin/principals">Add principal</Link>
          <Link to="/admin/invitations">Invite account</Link>
        </div>
      </header>

      {tripsQuery.isLoading && <LoadingState />}
      <ApiErrorMessage error={tripsQuery.error} />
      <ApiErrorMessage error={attemptsQuery.error} />

      <section className="metric-row" aria-label="Trip metrics">
        <Metric label="Active trips" value={trips.filter((trip) => isActiveTrip(trip.status)).length} detail="Open arrival workflows" tone="neutral" />
        <Metric label="Needs attention" value={attentionTrips.length} detail="Stale or unassigned" tone="attention" />
        <Metric label="Email failures" value={emailFailures} detail={`${attempts.length} attempts checked`} tone="watch" />
      </section>

      <section className="table-panel">
        <div className="table-heading">
          <div>
            <p className="eyebrow">Recently updated</p>
            <h2>Active arrival register</h2>
          </div>
          <Link className="secondary-link" to="/admin/trips">Open trips</Link>
        </div>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Principal</th>
                <th>Flight</th>
                <th>State</th>
                <th>Last event</th>
                <th>Updated</th>
                <th>Concierge</th>
              </tr>
            </thead>
            <tbody>
              {trips.slice(0, 6).map((trip) => (
                <tr key={trip.id}>
                  <td>{trip.primaryPrincipal?.fullName ?? 'Principal pending'}</td>
                  <td>{trip.flightNumber} · {trip.arrivalAirport}</td>
                  <td><span className="trip-status" data-tone={trip.stale ? 'watch' : trip.status.toLowerCase()}>{statusLabel(trip.status)}</span></td>
                  <td>{trip.lastTimelineEvent ? trip.lastTimelineEvent.eventType.replaceAll('_', ' ').toLowerCase() : 'No event'}</td>
                  <td>{relativeTime(trip.lastUpdatedAt)}</td>
                  <td>{trip.assignedConcierge?.fullName ?? 'Unassigned'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}

function Metric({ label, value, detail, tone }: { label: string; value: number; detail: string; tone: string }) {
  return (
    <article className="metric-card" data-tone={tone}>
      <p>{label}</p>
      <div><strong>{value}</strong></div>
      <small>{detail}</small>
    </article>
  )
}
