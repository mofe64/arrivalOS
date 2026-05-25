import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { adminApi } from '../../api/arrivalos'
import { withFixtureFallback } from '../../api/fallback'
import { fixtureAdminTrips } from '../../data/fixtures'
import { ApiErrorMessage, EmptyState, LoadingState, SectionHeader } from '../../components/Primitives'
import { relativeTime, shortDateTime, statusLabel } from '../../components/format'

export function AdminTripsPage() {
  const [query, setQuery] = useState('')
  const tripsQuery = useQuery({
    queryKey: ['admin', 'trips'],
    queryFn: () => withFixtureFallback(() => adminApi.listTrips(false), fixtureAdminTrips),
  })
  const filtered = useMemo(() => {
    const trips = tripsQuery.data ?? []
    const needle = query.trim().toLowerCase()
    if (!needle) return trips
    return trips.filter((trip) => {
      const haystack = [
        trip.primaryPrincipal?.fullName,
        trip.flightNumber,
        trip.arrivalAirport,
        trip.arrivalTerminal,
        trip.status,
        trip.assignedConcierge?.fullName,
      ].join(' ').toLowerCase()
      return haystack.includes(needle)
    })
  }, [query, tripsQuery.data])

  return (
    <>
      <SectionHeader eyebrow="Trips" title="Arrival timeline register">
        <Link className="primary-link" to="/admin/trips/new">New trip</Link>
      </SectionHeader>

      <div className="filter-bar">
        <label className="field">
          <span>Search by principal, flight, airport, status, or concierge</span>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="BA075, LOS, Amina..." />
        </label>
      </div>

      {tripsQuery.isLoading && <LoadingState />}
      <ApiErrorMessage error={tripsQuery.error} />
      {filtered.length === 0 && !tripsQuery.isLoading ? (
        <EmptyState
          title="No trips match this view"
          body="Create the first trip or clear filters to return to the active register."
          action={{ to: '/admin/trips/new', label: 'Create new trip' }}
        />
      ) : (
        <section className="table-panel">
          <div className="table-scroll trip-desktop-table">
            <table>
              <thead>
                <tr>
                  <th>Trip</th>
                  <th>Principal</th>
                  <th>Flight</th>
                  <th>Airport</th>
                  <th>Status</th>
                  <th>Concierge</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((trip) => (
                  <tr key={trip.id}>
                    <td><Link to="/admin/trips/$tripId" params={{ tripId: trip.id }}>{trip.id.slice(0, 8)}</Link></td>
                    <td>{trip.primaryPrincipal?.fullName ?? 'Pending'}</td>
                    <td>{trip.flightNumber}</td>
                    <td>{trip.arrivalAirport} {trip.arrivalTerminal ? `· ${trip.arrivalTerminal}` : ''}</td>
                    <td><StatusPill stale={trip.stale} label={statusLabel(trip.status)} /></td>
                    <td>{trip.assignedConcierge?.fullName ?? 'Unassigned'}</td>
                    <td>{relativeTime(trip.lastUpdatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mobile-trip-list">
            {filtered.map((trip) => (
              <Link className="mobile-trip-row" key={trip.id} to="/admin/trips/$tripId" params={{ tripId: trip.id }}>
                <div>
                  <strong>{trip.primaryPrincipal?.fullName ?? 'Principal pending'}</strong>
                  <span>{trip.flightNumber} · {trip.arrivalAirport}</span>
                </div>
                <StatusPill stale={trip.stale} label={statusLabel(trip.status)} />
                <small>{shortDateTime(trip.scheduledArrivalAt)} · {trip.assignedConcierge?.fullName ?? 'Unassigned'}</small>
              </Link>
            ))}
          </div>
        </section>
      )}
    </>
  )
}

function StatusPill({ label, stale }: { label: string; stale: boolean }) {
  return <span className="trip-status" data-tone={stale ? 'watch' : 'active'}>{stale ? 'Stale · ' : ''}{label}</span>
}
