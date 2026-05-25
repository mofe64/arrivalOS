import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { adminApi } from '../../api/arrivalos'
import type { AccountType } from '../../api/types'
import { ApiErrorMessage, SectionHeader } from '../../components/Primitives'
import { shortDateTime } from '../../components/format'

export function AdminInvitationsPage() {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [accountType, setAccountType] = useState<AccountType>('PRINCIPAL')
  const invite = useMutation({
    mutationFn: () => adminApi.createInvitation({ fullName, email, phone, accountType }),
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    invite.mutate()
  }

  return (
    <>
      <SectionHeader eyebrow="Invitations" title="Invite login-capable accounts" />
      <section className="two-column">
        <form className="ops-panel stack-form" onSubmit={handleSubmit}>
          <h2>Create invitation</h2>
          <p className="muted-copy">Invite admins and principals only. Watchers remain trip-scoped email notification recipients.</p>
          <ApiErrorMessage error={invite.error} />
          <label className="field"><span>Full name</span><input value={fullName} onChange={(event) => setFullName(event.target.value)} /></label>
          <label className="field"><span>Email</span><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} /></label>
          <label className="field"><span>Phone</span><input value={phone} onChange={(event) => setPhone(event.target.value)} /></label>
          <label className="field"><span>Account type</span><select value={accountType} onChange={(event) => setAccountType(event.target.value as AccountType)}><option value="PRINCIPAL">Principal</option><option value="ADMIN">Admin</option></select></label>
          <button className="primary-button" disabled={!fullName || !email || invite.isPending} type="submit">Send invitation</button>
        </form>
        <article className="ops-panel">
          <h2>Last invitation</h2>
          {invite.data ? (
            <dl className="compact-dl">
              <div><dt>Name</dt><dd>{invite.data.fullName}</dd></div>
              <div><dt>Email</dt><dd>{invite.data.email}</dd></div>
              <div><dt>Type</dt><dd>{invite.data.accountType}</dd></div>
              <div><dt>Expires</dt><dd>{shortDateTime(invite.data.expiresAt)}</dd></div>
            </dl>
          ) : (
            <p className="muted-copy">Submitted invitations will appear here for confirmation.</p>
          )}
        </article>
      </section>
    </>
  )
}
