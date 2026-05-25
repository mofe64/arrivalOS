import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { adminApi } from '../../api/arrivalos'
import { withFixtureFallback } from '../../api/fallback'
import { fixtureConcierges } from '../../data/fixtures'
import { ApiErrorMessage, EmptyState, LoadingState, SectionHeader } from '../../components/Primitives'
import { ConciergeIdentityCard } from '../../components/ArrivalComponents'

export function AdminConciergesPage() {
  const queryClient = useQueryClient()
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [publicId, setPublicId] = useState('')
  const [photoUrl, setPhotoUrl] = useState('')
  const conciergesQuery = useQuery({
    queryKey: ['admin', 'concierges'],
    queryFn: () => withFixtureFallback(adminApi.concierges, fixtureConcierges),
  })
  const create = useMutation({
    mutationFn: () => adminApi.createConcierge({ fullName, phone, publicId, photoUrl }),
    onSuccess: () => {
      setFullName('')
      setPhone('')
      setPublicId('')
      setPhotoUrl('')
      void queryClient.invalidateQueries({ queryKey: ['admin', 'concierges'] })
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <>
      <SectionHeader eyebrow="Concierges" title="Field operator records" />
      <section className="two-column">
        <form className="ops-panel stack-form" onSubmit={handleSubmit}>
          <h2>Create concierge</h2>
          <p className="muted-copy">Concierges do not log in. They use trip-scoped access links issued from a trip detail page.</p>
          <ApiErrorMessage error={create.error} />
          <label className="field"><span>Full name</span><input value={fullName} onChange={(event) => setFullName(event.target.value)} /></label>
          <label className="field"><span>Phone</span><input value={phone} onChange={(event) => setPhone(event.target.value)} /></label>
          <label className="field"><span>Public ID</span><input value={publicId} onChange={(event) => setPublicId(event.target.value)} placeholder="GGS-NAME" /></label>
          <label className="field"><span>Photo URL</span><input value={photoUrl} onChange={(event) => setPhotoUrl(event.target.value)} /></label>
          <button className="primary-button" disabled={!fullName || !phone || !publicId || create.isPending} type="submit">Create concierge</button>
        </form>
        <section className="ops-panel">
          <div className="panel-heading"><h2>Concierge list</h2><span>API backed</span></div>
          {conciergesQuery.isLoading && <LoadingState />}
          <ApiErrorMessage error={conciergesQuery.error} />
          {(conciergesQuery.data ?? []).length === 0 ? (
            <EmptyState title="Concierge list API pending" body="Creation is supported. The list will populate when the backend list endpoint is available." />
          ) : (
            <div className="identity-list">
              {(conciergesQuery.data ?? []).map((concierge) => <ConciergeIdentityCard concierge={concierge} key={concierge.id} />)}
            </div>
          )}
        </section>
      </section>
    </>
  )
}
