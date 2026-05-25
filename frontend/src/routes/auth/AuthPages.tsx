import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { authApi } from '../../api/arrivalos'
import type { AccountType } from '../../api/types'
import { ApiErrorMessage } from '../../components/Primitives'
import arrivalOsLogo from '../../assets/arrivalos-logo.png'

function authDestination(accountType: AccountType) {
  return accountType === 'ADMIN' ? '/admin/dashboard' : '/principal/trips'
}

export function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const login = useMutation({
    mutationFn: () => authApi.login(email.trim(), password),
    onSuccess: (auth) => {
      void navigate({ to: authDestination(auth.user.accountType) })
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    login.mutate()
  }

  const canSubmit = email.trim().length > 0 && password.length > 0 && !login.isPending

  return (
    <AuthFrame
      eyebrow="Trusted arrival command"
      title="Secure access for live airport timelines."
      body="Sign in to follow verified updates, manage handoffs, and keep every trip auditable."
    >
      <div className="login-heading">
        <p className="eyebrow">Secure sign in</p>
        <h2 id="login-title">Sign in to continue</h2>
        <p>Use your approved ArrivalOS credentials.</p>
      </div>

      <form className="login-form" onSubmit={handleSubmit}>
        <ApiErrorMessage error={login.error} />
        <label className="field">
          <span>Email address</span>
          <input
            autoComplete="email"
            inputMode="email"
            name="email"
            placeholder="name@company.com"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            autoComplete="current-password"
            name="password"
            placeholder="Enter your password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        <div className="form-actions">
          <button className="primary-button" disabled={!canSubmit} type="submit">
            {login.isPending ? 'Signing in...' : 'Continue securely'}
          </button>
        </div>
        <Link className="text-link" to="/forgot-password">Forgot password?</Link>
      </form>
    </AuthFrame>
  )
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const forgot = useMutation({ mutationFn: () => authApi.forgotPassword(email.trim()) })

  return (
    <AuthFrame eyebrow="Account recovery" title="Recover access without exposing account status." body="ArrivalOS sends a reset link when the email belongs to an active account.">
      <div className="login-heading">
        <p className="eyebrow">Password reset</p>
        <h2>Request a reset link</h2>
      </div>
      <form className="login-form" onSubmit={(event) => { event.preventDefault(); forgot.mutate() }}>
        <ApiErrorMessage error={forgot.error} />
        {forgot.data && <p className="form-message">{forgot.data.message}</p>}
        <label className="field">
          <span>Email address</span>
          <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
        </label>
        <button className="primary-button" disabled={!email || forgot.isPending} type="submit">
          {forgot.isPending ? 'Sending...' : 'Send reset email'}
        </button>
        <Link className="text-link" to="/">Back to sign in</Link>
      </form>
    </AuthFrame>
  )
}

export function ResetPasswordPage() {
  const search = useSearch({ strict: false }) as { token?: string }
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const token = search.token ?? ''
  const reset = useMutation({
    mutationFn: () => authApi.resetPassword(token, password),
    onSuccess: () => {
      window.setTimeout(() => void navigate({ to: '/' }), 900)
    },
  })

  return (
    <AuthFrame eyebrow="Credential update" title="Set a new ArrivalOS password." body="Use the reset token from your email to restore access.">
      <div className="login-heading">
        <p className="eyebrow">Reset password</p>
        <h2>Choose a new password</h2>
      </div>
      <form className="login-form" onSubmit={(event) => { event.preventDefault(); reset.mutate() }}>
        <ApiErrorMessage error={reset.error} />
        {reset.data && <p className="form-message">{reset.data.message}</p>}
        {!token && <p className="form-message warning">Reset token missing from the link.</p>}
        <label className="field">
          <span>New password</span>
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
        </label>
        <button className="primary-button" disabled={!token || password.length < 8 || reset.isPending} type="submit">
          {reset.isPending ? 'Updating...' : 'Reset password'}
        </button>
      </form>
    </AuthFrame>
  )
}

export function AcceptInvitationPage() {
  const search = useSearch({ strict: false }) as { token?: string }
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const token = search.token ?? ''
  const accept = useMutation({
    mutationFn: () => authApi.acceptInvitation(token, password),
    onSuccess: (auth) => {
      void navigate({ to: authDestination(auth.user.accountType) })
    },
  })

  return (
    <AuthFrame eyebrow="Invitation acceptance" title="Activate an approved ArrivalOS account." body="Accept the invitation and continue into the workspace allowed for your account type.">
      <div className="login-heading">
        <p className="eyebrow">Invitation</p>
        <h2>Create your password</h2>
      </div>
      <form className="login-form" onSubmit={(event) => { event.preventDefault(); accept.mutate() }}>
        <ApiErrorMessage error={accept.error} />
        {!token && <p className="form-message warning">Invitation token missing from the link.</p>}
        <label className="field">
          <span>Password</span>
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
        </label>
        <button className="primary-button" disabled={!token || password.length < 8 || accept.isPending} type="submit">
          {accept.isPending ? 'Activating...' : 'Accept invitation'}
        </button>
      </form>
    </AuthFrame>
  )
}

export function VerifyEmailPage() {
  const search = useSearch({ strict: false }) as { token?: string }
  const token = search.token ?? ''
  const verify = useMutation({ mutationFn: () => authApi.verifyEmail(token) })

  return (
    <AuthFrame eyebrow="Email verification" title="Confirm the email tied to this account." body="Verified email is required before ArrivalOS allows login.">
      <div className="login-heading">
        <p className="eyebrow">Verification</p>
        <h2>Verify your email</h2>
      </div>
      <form className="login-form" onSubmit={(event) => { event.preventDefault(); verify.mutate() }}>
        <ApiErrorMessage error={verify.error} />
        {verify.data && <p className="form-message">{verify.data.message}</p>}
        {!token && <p className="form-message warning">Verification token missing from the link.</p>}
        <button className="primary-button" disabled={!token || verify.isPending} type="submit">
          {verify.isPending ? 'Verifying...' : 'Verify email'}
        </button>
        <Link className="text-link" to="/">Back to sign in</Link>
      </form>
    </AuthFrame>
  )
}

function AuthFrame({
  eyebrow,
  title,
  body,
  children,
}: {
  eyebrow: string
  title: string
  body: string
  children: React.ReactNode
}) {
  return (
    <main className="login-screen">
      <section className="brand-panel" aria-labelledby="brand-title">
        <div className="brand-lockup">
          <img src={arrivalOsLogo} alt="" className="brand-mark" />
          <div>
            <p className="eyebrow">Gbèjà Global Security</p>
            <h1 id="brand-title">ArrivalOS</h1>
          </div>
        </div>
        <div className="mission-copy">
          <p className="eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
          <p>{body}</p>
        </div>
        <div className="assurance-strip" aria-label="Security assurances">
          <span>Timeline first</span>
          <span>Email updates</span>
          <span>Audit ready</span>
        </div>
      </section>
      <section className="login-panel">{children}</section>
    </main>
  )
}
