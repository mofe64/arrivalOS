import { createRootRoute, createRoute, createRouter, redirect } from '@tanstack/react-router'
import { getSession } from './auth/session'
import { RootLayout } from './routes/RootLayout'
import {
  AcceptInvitationPage,
  ForgotPasswordPage,
  LoginPage,
  ResetPasswordPage,
  VerifyEmailPage,
} from './routes/auth/AuthPages'
import { AdminShell } from './routes/admin/AdminShell'
import { AdminDashboard } from './routes/admin/AdminDashboard'
import { AdminTripsPage } from './routes/admin/AdminTrips'
import { AdminTripCreatePage } from './routes/admin/AdminTripCreate'
import { AdminTripDetailPage } from './routes/admin/AdminTripDetail'
import { AdminPrincipalsPage } from './routes/admin/AdminPrincipals'
import { AdminConciergesPage } from './routes/admin/AdminConcierges'
import { AdminInvitationsPage } from './routes/admin/AdminInvitations'
import { AdminNotificationsPage } from './routes/admin/AdminNotifications'
import { PrincipalShell } from './routes/principal/PrincipalShell'
import { PrincipalTripsPage } from './routes/principal/PrincipalTrips'
import { PrincipalTripDetailPage } from './routes/principal/PrincipalTripDetail'
import { ConciergeTripPage } from './routes/concierge/ConciergeTripPage'

const rootRoute = createRootRoute({
  component: RootLayout,
})

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: LoginPage,
})

const forgotPasswordRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/forgot-password',
  component: ForgotPasswordPage,
})

const resetPasswordRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/reset-password',
  component: ResetPasswordPage,
})

const acceptInvitationRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/invitations/accept',
  component: AcceptInvitationPage,
})

const verifyEmailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/verify-email',
  component: VerifyEmailPage,
})

const adminRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/admin',
  component: AdminShell,
  beforeLoad: () => requireRole('ADMIN'),
})

const adminDashboardRoute = createRoute({
  getParentRoute: () => adminRoute,
  path: '/dashboard',
  component: AdminDashboard,
})

const adminTripsRoute = createRoute({
  getParentRoute: () => adminRoute,
  path: '/trips',
  component: AdminTripsPage,
})

const adminTripCreateRoute = createRoute({
  getParentRoute: () => adminRoute,
  path: '/trips/new',
  component: AdminTripCreatePage,
})

const adminTripDetailRoute = createRoute({
  getParentRoute: () => adminRoute,
  path: '/trips/$tripId',
  component: AdminTripDetailPage,
})

const adminPrincipalsRoute = createRoute({
  getParentRoute: () => adminRoute,
  path: '/principals',
  component: AdminPrincipalsPage,
})

const adminConciergesRoute = createRoute({
  getParentRoute: () => adminRoute,
  path: '/concierges',
  component: AdminConciergesPage,
})

const adminInvitationsRoute = createRoute({
  getParentRoute: () => adminRoute,
  path: '/invitations',
  component: AdminInvitationsPage,
})

const adminNotificationsRoute = createRoute({
  getParentRoute: () => adminRoute,
  path: '/notifications',
  component: AdminNotificationsPage,
})

const principalRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/principal',
  component: PrincipalShell,
  beforeLoad: () => requireRole('PRINCIPAL'),
})

const principalTripsRoute = createRoute({
  getParentRoute: () => principalRoute,
  path: '/trips',
  component: PrincipalTripsPage,
})

const principalTripDetailRoute = createRoute({
  getParentRoute: () => principalRoute,
  path: '/trips/$tripId',
  component: PrincipalTripDetailPage,
})

const conciergeTripRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/concierge/trips/$tripId',
  component: ConciergeTripPage,
})

const routeTree = rootRoute.addChildren([
  indexRoute,
  forgotPasswordRoute,
  resetPasswordRoute,
  acceptInvitationRoute,
  verifyEmailRoute,
  adminRoute.addChildren([
    adminDashboardRoute,
    adminTripsRoute,
    adminTripCreateRoute,
    adminTripDetailRoute,
    adminPrincipalsRoute,
    adminConciergesRoute,
    adminInvitationsRoute,
    adminNotificationsRoute,
  ]),
  principalRoute.addChildren([principalTripsRoute, principalTripDetailRoute]),
  conciergeTripRoute,
])

export const router = createRouter({ routeTree })

function requireRole(accountType: 'ADMIN' | 'PRINCIPAL') {
  const session = getSession()
  if (!session) {
    throw redirect({ to: '/' })
  }
  if (session.user.accountType !== accountType) {
    throw redirect({
      to: session.user.accountType === 'ADMIN' ? '/admin/dashboard' : '/principal/trips',
    })
  }
}

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
