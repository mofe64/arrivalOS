import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { adminApi } from '../../api/arrivalos'
import { withFixtureFallback } from '../../api/fallback'
import type { AdminPrincipalSummary } from '../../api/types'
import { fixturePrincipals } from '../../data/fixtures'
import { ApiErrorMessage, EmptyState, LoadingState, SectionHeader } from '../../components/Primitives'
import { NotificationRecipientList } from '../../components/ArrivalComponents'
import { shortDateTime, statusLabel } from '../../components/format'

export function AdminPrincipalsPage() {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const principalsQuery = useQuery({
    queryKey: ['admin', 'principals'],
    queryFn: () => withFixtureFallback(adminApi.principals, fixturePrincipals),
  })
  const principals = principalsQuery.data ?? []
  const selected = principals.find((principal) => principal.id === selectedId) ?? principals[0]

  return (
    <>
      <SectionHeader eyebrow="Principals" title="Principal management" />
      {principalsQuery.isLoading && <LoadingState />}
      <ApiErrorMessage error={principalsQuery.error} />
      {principals.length === 0 ? (
        <EmptyState title="No principals yet" body="Invite a principal account, then link that account to a trip principal." action={{ to: '/admin/invitations', label: 'Invite principal' }} />
      ) : (
        <section className="split-panel">
          <div className="record-list">
            {principals.map((principal) => (
              <button className={principal.id === selected?.id ? 'active' : ''} key={principal.id} onClick={() => setSelectedId(principal.id)} type="button">
                <strong>{principal.fullName}</strong>
                <span>{principal.email}</span>
                <small>{principal.emailVerified ? 'Verified' : 'Email pending'} · {principal.trips.length} linked trips</small>
              </button>
            ))}
          </div>
          {selected && <PrincipalDetail principal={selected} />}
        </section>
      )}
    </>
  )
}

function PrincipalDetail({ principal }: { principal: AdminPrincipalSummary }) {
  return (
    <article className="detail-card">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Principal detail</p>
          <h2>{principal.fullName}</h2>
        </div>
        <span className="trip-status" data-tone={principal.active ? 'active' : 'watch'}>{principal.active ? 'Active' : 'Inactive'}</span>
      </div>
      <dl className="compact-dl">
        <div><dt>Email</dt><dd>{principal.email}</dd></div>
        <div><dt>Email verification</dt><dd>{principal.emailVerified ? 'Verified' : 'Pending'}</dd></div>
        <div><dt>Phone</dt><dd>{principal.phone ?? 'Not provided'}</dd></div>
      </dl>
      <h3>Linked trips</h3>
      {principal.trips.length === 0 ? (
        <p className="muted-copy">No linked trips yet.</p>
      ) : (
        <div className="linked-trip-list">
          {principal.trips.map((trip) => (
            <section key={trip.id}>
              <strong>{trip.flightNumber} · {trip.arrivalAirport}</strong>
              <span>{statusLabel(trip.status)} · {shortDateTime(trip.scheduledArrivalAt)}</span>
              <small>Concierge: {trip.concierge?.fullName ?? 'Unassigned'}</small>
              <NotificationRecipientList watchers={trip.watchers} />
            </section>
          ))}
        </div>
      )}
    </article>
  )
}
