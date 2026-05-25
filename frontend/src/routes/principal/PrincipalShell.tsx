import { Link, Outlet, useNavigate } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '../../api/arrivalos'
import { getSession } from '../../auth/session'
import arrivalOsLogo from '../../assets/arrivalos-logo.png'

export function PrincipalShell() {
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
    <main className="principal-shell">
      <header className="principal-topbar">
        <Link className="principal-brand" to="/principal/trips">
          <img src={arrivalOsLogo} alt="" />
          <span>ArrivalOS</span>
        </Link>
        <div>
          <span>{user?.fullName ?? 'Principal'}</span>
          <button disabled={logout.isPending} onClick={() => logout.mutate()} type="button">Log out</button>
        </div>
      </header>
      <Outlet />
    </main>
  )
}
