import { Link, Outlet, useNavigate } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '../../api/arrivalos'
import { getSession } from '../../auth/session'
import arrivalOsLogo from '../../assets/arrivalos-logo.png'

const navItems = [
  { to: '/admin/dashboard', label: 'Dashboard' },
  { to: '/admin/trips', label: 'Trips' },
  { to: '/admin/principals', label: 'Principals' },
  { to: '/admin/concierges', label: 'Concierges' },
  { to: '/admin/invitations', label: 'Invitations' },
  { to: '/admin/notifications', label: 'Notifications' },
] as const

export function AdminShell() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const user = getSession()?.user
  const logout = useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      queryClient.clear()
      void navigate({ to: '/' })
    },
  })

  return (
    <main className="admin-shell">
      <aside className="admin-sidebar" aria-label="Admin navigation">
        <div className="admin-brand">
          <img src={arrivalOsLogo} alt="" />
          <div>
            <p className="eyebrow">ArrivalOS</p>
            <strong>Admin</strong>
          </div>
        </div>

        <nav className="admin-nav">
          {navItems.map((item) => (
            <Link activeProps={{ className: 'active' }} key={item.to} to={item.to}>
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="sidebar-note">
          <p className="eyebrow">Signed in</p>
          <span>{user?.fullName ?? 'Admin user'}</span>
          <button disabled={logout.isPending} onClick={() => logout.mutate()} type="button">
            Log out
          </button>
        </div>
      </aside>

      <section className="admin-workspace">
        {!user?.emailVerified && <ResendVerificationBanner />}
        <Outlet />
      </section>
    </main>
  )
}

function ResendVerificationBanner() {
  const resend = useMutation({ mutationFn: authApi.resendVerification })
  return (
    <div className="verification-banner">
      <span>Email verification is still pending for this account.</span>
      <button disabled={resend.isPending} onClick={() => resend.mutate()} type="button">
        {resend.isPending ? 'Sending...' : 'Resend email'}
      </button>
      {resend.data && <small>{resend.data.message}</small>}
    </div>
  )
}
