import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../../api/arrivalos'
import { withFixtureFallback } from '../../api/fallback'
import { fixtureAdminTripDetail, fixtureAdminTrips } from '../../data/fixtures'
import { ApiErrorMessage, EmptyState, LoadingState, SectionHeader } from '../../components/Primitives'
import { relativeTime, shortDateTime } from '../../components/format'

export function AdminNotificationsPage() {
  const tripsQuery = useQuery({
    queryKey: ['admin', 'notifications', 'trips'],
    queryFn: () => withFixtureFallback(() => adminApi.listTrips(false), fixtureAdminTrips),
  })
  const trips = tripsQuery.data ?? []
  const attemptsQuery = useQuery({
    queryKey: ['admin', 'notifications', 'attempts', trips.map((trip) => trip.id).join(',')],
    enabled: trips.length > 0,
    queryFn: async () => {
      const attemptGroups = await Promise.all(
        trips.map(async (trip) => {
          const attempts = await withFixtureFallback(
            () => adminApi.notificationAttempts(trip.id),
            trip.id === fixtureAdminTripDetail.id ? fixtureAdminTripDetail.notificationAttempts : [],
          )
          return attempts.map((attempt) => ({ attempt, trip }))
        }),
      )
      return attemptGroups.flat()
    },
  })
  const attempts = attemptsQuery.data ?? []

  return (
    <>
      <SectionHeader eyebrow="Notifications" title="Email delivery oversight" />
      {(tripsQuery.isLoading || attemptsQuery.isLoading) && <LoadingState />}
      <ApiErrorMessage error={tripsQuery.error} />
      <ApiErrorMessage error={attemptsQuery.error} />
      <section className="table-panel">
        <div className="table-heading">
          <div>
            <p className="eyebrow">Attempt register</p>
            <h2>Email notification attempts</h2>
          </div>
          <span className="muted-copy">{attempts.length} attempts loaded</span>
        </div>
        {attempts.length === 0 && !attemptsQuery.isLoading ? (
          <EmptyState title="No email attempts found" body="Timeline updates will populate this register once notification delivery starts." />
        ) : (
          <div className="table-scroll">
            <table>
              <thead><tr><th>Trip</th><th>Principal</th><th>Status</th><th>Provider</th><th>Created</th><th>Trip update</th><th>Action</th></tr></thead>
              <tbody>
                {attempts.map(({ attempt, trip }) => (
                  <tr key={attempt.id}>
                    <td>{trip.flightNumber}</td>
                    <td>{trip.primaryPrincipal?.fullName ?? 'Pending'}</td>
                    <td><span className="trip-status" data-tone={attempt.status === 'FAILED' ? 'watch' : 'active'}>{attempt.channel} · {attempt.status}</span></td>
                    <td>{attempt.provider ?? 'Pending'}</td>
                    <td>{shortDateTime(attempt.createdAt)}</td>
                    <td>{relativeTime(trip.lastUpdatedAt)}</td>
                    <td><Link to="/admin/trips/$tripId" params={{ tripId: trip.id }}>Open trip</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}
