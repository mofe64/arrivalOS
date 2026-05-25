import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { principalApi } from '../../api/arrivalos'
import { withFixtureFallback } from '../../api/fallback'
import { fixturePrincipalTrips } from '../../data/fixtures'
import { ConciergeIdentityCard, TripStatusBand } from '../../components/ArrivalComponents'
import { ApiErrorMessage, EmptyState, LoadingState } from '../../components/Primitives'
import { shortDateTime } from '../../components/format'

export function PrincipalTripsPage() {
  const tripsQuery = useQuery({
    queryKey: ['principal', 'trips'],
    queryFn: () => withFixtureFallback(principalApi.trips, fixturePrincipalTrips),
  })
  const trips = tripsQuery.data ?? []

  if (tripsQuery.isLoading) return <LoadingState />
  if (tripsQuery.error) return <ApiErrorMessage error={tripsQuery.error} />
  if (trips.length === 0) {
    return (
      <EmptyState
        title="No arrival timelines yet"
        body="When Gbèjà links you to a trip, the current state, concierge identity, and meeting instructions will appear here."
      />
    )
  }

  return (
    <section className="principal-page">
      <TripStatusBand
        status={trips[0].status}
        principalName="Your arrival"
        flightNumber={trips[0].flightNumber}
        airport={trips[0].arrivalAirport}
        lastUpdatedAt={trips[0].lastUpdatedAt}
        currentCheckpoint={trips[0].currentCheckpoint}
      />
      <div className="principal-trip-list">
        {trips.map((trip) => (
          <Link className="principal-trip-card" key={trip.id} to="/principal/trips/$tripId" params={{ tripId: trip.id }}>
            <div>
              <p className="eyebrow">Arrival</p>
              <h2>{trip.flightNumber} · {trip.arrivalAirport}</h2>
              <p>{trip.meetingPoint ?? 'Meeting instructions will be confirmed before arrival.'}</p>
              <small>{shortDateTime(trip.scheduledArrivalAt)}</small>
            </div>
            <ConciergeIdentityCard concierge={trip.concierge} />
          </Link>
        ))}
      </div>
    </section>
  )
}
