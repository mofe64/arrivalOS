import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { adminApi } from '../../api/arrivalos'
import { withFixtureFallback } from '../../api/fallback'
import { fixtureConcierges, defaultCheckpoints } from '../../data/fixtures'
import { ApiErrorMessage, SectionHeader } from '../../components/Primitives'

type Step = 'details' | 'principals' | 'recipients' | 'operations'

export function AdminTripCreatePage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('details')
  const [flightNumber, setFlightNumber] = useState('')
  const [arrivalAirport, setArrivalAirport] = useState('')
  const [arrivalTerminal, setArrivalTerminal] = useState('')
  const [meetingPoint, setMeetingPoint] = useState('')
  const [scheduledArrivalAt, setScheduledArrivalAt] = useState('')
  const [principalName, setPrincipalName] = useState('')
  const [principalPhone, setPrincipalPhone] = useState('')
  const [watcherName, setWatcherName] = useState('')
  const [watcherEmail, setWatcherEmail] = useState('')
  const [conciergeId, setConciergeId] = useState('')
  const [checkpoints, setCheckpoints] = useState(defaultCheckpoints.map((checkpoint) => checkpoint.name).join('\n'))
  const conciergesQuery = useQuery({
    queryKey: ['admin', 'concierges', 'trip-create'],
    queryFn: () => withFixtureFallback(adminApi.concierges, fixtureConcierges),
  })
  const createTrip = useMutation({
    mutationFn: () => adminApi.createTrip({
      flightNumber: flightNumber.trim(),
      arrivalAirport: arrivalAirport.trim(),
      arrivalTerminal: arrivalTerminal.trim() || undefined,
      meetingPoint: meetingPoint.trim() || undefined,
      scheduledArrivalAt: scheduledArrivalAt ? new Date(scheduledArrivalAt).toISOString() : undefined,
      principals: [{ fullName: principalName.trim(), phone: principalPhone.trim() || undefined, primaryContact: true }],
      watchers: watcherEmail ? [{ fullName: watcherName.trim() || watcherEmail.trim(), email: watcherEmail.trim() }] : [],
      assignedConciergeId: conciergeId || undefined,
      checkpoints: checkpoints.split('\n').map((name) => name.trim()).filter(Boolean).map((name) => ({ name })),
    }),
    onSuccess: (trip) => void navigate({ to: '/admin/trips/$tripId', params: { tripId: trip.id } }),
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    createTrip.mutate()
  }

  return (
    <>
      <SectionHeader eyebrow="New trip" title="Create arrival workflow" />
      <form className="wizard-shell" onSubmit={handleSubmit}>
        <ApiErrorMessage error={createTrip.error} />
        <nav className="wizard-steps" aria-label="Trip creation steps">
          {(['details', 'principals', 'recipients', 'operations'] as Step[]).map((item) => (
            <button className={step === item ? 'active' : ''} key={item} onClick={() => setStep(item)} type="button">
              {item}
            </button>
          ))}
        </nav>

        {step === 'details' && (
          <section className="form-grid">
            <label className="field"><span>Flight number</span><input value={flightNumber} onChange={(event) => setFlightNumber(event.target.value)} /></label>
            <label className="field"><span>Arrival airport</span><input value={arrivalAirport} onChange={(event) => setArrivalAirport(event.target.value)} placeholder="LOS" /></label>
            <label className="field"><span>Arrival terminal</span><input value={arrivalTerminal} onChange={(event) => setArrivalTerminal(event.target.value)} /></label>
            <label className="field"><span>Scheduled arrival time</span><input type="datetime-local" value={scheduledArrivalAt} onChange={(event) => setScheduledArrivalAt(event.target.value)} /></label>
            <label className="field full-span"><span>Meeting point</span><input value={meetingPoint} onChange={(event) => setMeetingPoint(event.target.value)} placeholder="Arrivals hall, left of the currency exchange desk" /></label>
            <p className="form-hint full-span">Backend persistence for meeting point is expected in the trip DTO. Until then, the frontend sends the field and warns admins when the API returns it as missing.</p>
          </section>
        )}
        {step === 'principals' && (
          <section className="form-grid">
            <label className="field"><span>Principal name</span><input value={principalName} onChange={(event) => setPrincipalName(event.target.value)} /></label>
            <label className="field"><span>Principal phone</span><input value={principalPhone} onChange={(event) => setPrincipalPhone(event.target.value)} /></label>
            <p className="form-hint">A trip can support multiple principals in the backend. This MVP form starts with the primary contact; additional principals can be added from the trip detail view.</p>
          </section>
        )}
        {step === 'recipients' && (
          <section className="form-grid">
            <label className="field"><span>Email recipient name</span><input value={watcherName} onChange={(event) => setWatcherName(event.target.value)} /></label>
            <label className="field"><span>Email recipient address</span><input type="email" value={watcherEmail} onChange={(event) => setWatcherEmail(event.target.value)} /></label>
            <p className="form-hint">Watchers are trip-scoped notification recipients, not login accounts.</p>
          </section>
        )}
        {step === 'operations' && (
          <section className="form-grid">
            <div className="full-span"><ApiErrorMessage error={conciergesQuery.error} /></div>
            <label className="field"><span>Concierge assignment</span><select value={conciergeId} onChange={(event) => setConciergeId(event.target.value)}><option value="">Assign later</option>{(conciergesQuery.data ?? []).map((concierge) => <option key={concierge.id} value={concierge.id}>{concierge.fullName}</option>)}</select></label>
            <label className="field full-span"><span>Default checkpoint list</span><textarea rows={8} value={checkpoints} onChange={(event) => setCheckpoints(event.target.value)} /></label>
          </section>
        )}

        <div className="wizard-actions">
          <button className="secondary-button" type="button" onClick={() => setStep(previousStep(step))}>Back</button>
          {step === 'operations' ? (
            <button className="primary-button" disabled={!flightNumber || !arrivalAirport || !principalName || createTrip.isPending} type="submit">
              {createTrip.isPending ? 'Creating...' : 'Create trip'}
            </button>
          ) : (
            <button className="primary-button" type="button" onClick={() => setStep(nextStep(step))}>Continue</button>
          )}
        </div>
      </form>
    </>
  )
}

function nextStep(step: Step): Step {
  if (step === 'details') return 'principals'
  if (step === 'principals') return 'recipients'
  return 'operations'
}

function previousStep(step: Step): Step {
  if (step === 'operations') return 'recipients'
  if (step === 'recipients') return 'principals'
  return 'details'
}
