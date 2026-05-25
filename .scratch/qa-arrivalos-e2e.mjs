const API = process.env.API_URL ?? 'http://localhost:8080/api'
const MAILDEV = process.env.MAILDEV_URL ?? 'http://localhost:1080'
const FRONTEND = process.env.FRONTEND_URL ?? 'http://localhost:3001'

const stamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
const password = 'Passw0rd!local'
const report = {
  environment: { api: API, maildev: MAILDEV, frontend: FRONTEND, stamp },
  testData: {},
  passes: [],
  failures: [],
  productGaps: [],
  emailEvidence: [],
  apiResponses: {},
}

function recordPass(title, detail = '') {
  report.passes.push({ title, detail })
  console.log(`PASS ${title}${detail ? ` :: ${detail}` : ''}`)
}

function recordFailure(title, severity, detail, evidence = {}) {
  report.failures.push({ title, severity, detail, evidence })
  console.log(`FAIL ${severity} ${title} :: ${detail}`)
}

function recordGap(title, detail, evidence = {}) {
  report.productGaps.push({ title, detail, evidence })
  console.log(`GAP ${title} :: ${detail}`)
}

async function http(method, pathOrUrl, body, token, expectedStatuses) {
  const url = pathOrUrl.startsWith('http') ? pathOrUrl : `${API}${pathOrUrl}`
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let data
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = text
  }
  const result = { status: res.status, data, url }
  if (expectedStatuses && !expectedStatuses.includes(res.status)) {
    throw new Error(`${method} ${url} expected ${expectedStatuses.join('/')} got ${res.status}: ${text}`)
  }
  return result
}

async function mail(method, path) {
  const res = await fetch(`${MAILDEV}${path}`, { method })
  const text = await res.text()
  try {
    return text ? JSON.parse(text) : null
  } catch {
    return text
  }
}

async function clearMail() {
  await mail('DELETE', '/email/all').catch(() => {})
}

async function emails() {
  return await mail('GET', '/email')
}

async function findEmail({ subjectIncludes, toIncludes, afterCount = 0 }) {
  for (let i = 0; i < 40; i++) {
    const list = await emails()
    const found = list.slice(afterCount).find((item) => {
      const to = JSON.stringify(item.to ?? '').toLowerCase()
      return item.subject?.includes(subjectIncludes) && to.includes(toIncludes.toLowerCase())
    })
    if (found) return found
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  return null
}

function tokenFromEmail(email) {
  const haystack = JSON.stringify(email)
  const match = haystack.match(/[?&]token=([^"'\\\s&<>]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

async function expectBlocked(title, method, path, body, token, statuses = [401, 403]) {
  const res = await http(method, path, body, token)
  if (statuses.includes(res.status)) {
    recordPass(title, `blocked with ${res.status}`)
  } else {
    recordFailure(title, 'P1 critical', `expected ${statuses.join('/')} but got ${res.status}`, res)
  }
  return res
}

async function createAndVerifyAdmin(prefix) {
  const email = `${prefix}.${stamp}@qa.arrivalos.local`
  await http('POST', '/auth/register/admin', {
    fullName: `${prefix} Admin`,
    email,
    phone: '+15550000001',
    password,
  }, null, [200])
  const verificationEmail = await findEmail({ subjectIncludes: 'Verify your ArrivalOS email', toIncludes: email })
  if (!verificationEmail) throw new Error(`verification email missing for ${email}`)
  const token = tokenFromEmail(verificationEmail)
  if (!token) throw new Error(`verification token missing for ${email}`)
  await http('POST', '/auth/verify-email', { token }, null, [200])
  const login = await http('POST', '/auth/login', { email, password }, null, [200])
  return { email, auth: login.data, verificationEmail }
}

async function inviteAndAccept(adminToken, accountType, prefix) {
  const email = `${prefix}.${stamp}@qa.arrivalos.local`
  const invite = await http('POST', '/admin/invitations', {
    fullName: `${prefix} ${accountType}`,
    email,
    phone: '+15550000002',
    accountType,
  }, adminToken, [200])
  const inviteEmail = await findEmail({ subjectIncludes: 'Accept your ArrivalOS invite', toIncludes: email })
  if (!inviteEmail) throw new Error(`invite email missing for ${email}`)
  const token = tokenFromEmail(inviteEmail)
  if (!token) throw new Error(`invite token missing for ${email}`)
  const accept = await http('POST', '/auth/invitations/accept', { token, password }, null, [200])
  return { email, invite: invite.data, auth: accept.data, inviteEmail, inviteUrlToken: token }
}

async function main() {
  await clearMail()

  const health = await http('GET', '/health', undefined, null, [200])
  report.apiResponses.health = health.data
  recordPass('backend health', JSON.stringify(health.data))

  const unverifiedEmail = `unverified.${stamp}@qa.arrivalos.local`
  await http('POST', '/auth/register/admin', {
    fullName: 'Unverified Admin',
    email: unverifiedEmail,
    password,
  }, null, [200])
  const blockedLogin = await http('POST', '/auth/login', { email: unverifiedEmail, password }, null)
  if (blockedLogin.status === 403) recordPass('unverified admin login blocked', '403 Email verification required')
  else recordFailure('unverified admin login is not blocked', 'P1 critical', `got ${blockedLogin.status}`, blockedLogin)

  const admin = await createAndVerifyAdmin('primary.admin')
  report.testData.primaryAdminEmail = admin.email
  recordPass('admin creation and verification', admin.email)

  const me = await http('GET', '/auth/me', undefined, admin.auth.accessToken, [200])
  if (me.data.email === admin.email && me.data.accountType === 'ADMIN') recordPass('GET /auth/me returns current admin', me.data.id)
  else recordFailure('GET /auth/me returned wrong admin user', 'P1 critical', 'current user payload mismatch', me)

  await http('POST', '/auth/logout', { refreshToken: admin.auth.refreshToken }, admin.auth.accessToken, [204])
  const meAfterLogout = await http('GET', '/auth/me', undefined, admin.auth.accessToken)
  if ([401, 403].includes(meAfterLogout.status)) recordPass('logout invalidates access token', `GET /auth/me returned ${meAfterLogout.status}`)
  else recordFailure('logout does not invalidate access token', 'P1 critical', `GET /auth/me returned ${meAfterLogout.status}`, meAfterLogout)

  const relogin = await http('POST', '/auth/login', { email: admin.email, password }, null, [200])
  const adminToken = relogin.data.accessToken
  report.testData.primaryAdminUserId = relogin.data.user.id

  const invitedAdmin = await inviteAndAccept(adminToken, 'ADMIN', 'invited.admin')
  report.testData.invitedAdminEmail = invitedAdmin.email
  recordPass('admin invitation acceptance', invitedAdmin.email)
  await http('GET', '/admin/trips', undefined, invitedAdmin.auth.accessToken, [200])
  recordPass('invited admin can access admin routes')

  const duplicateInvite = await http('POST', '/admin/invitations', {
    fullName: 'Duplicate Admin',
    email: invitedAdmin.email,
    accountType: 'ADMIN',
  }, adminToken)
  if (duplicateInvite.status === 409) recordPass('duplicate invitation rejected', invitedAdmin.email)
  else recordFailure('duplicate invitation not rejected', 'P1 critical', `got ${duplicateInvite.status}`, duplicateInvite)

  const principal = await inviteAndAccept(adminToken, 'PRINCIPAL', 'principal.one')
  report.testData.principalEmail = principal.email
  report.testData.principalUserId = principal.auth.user.id
  recordPass('principal invitation acceptance and login', principal.email)

  const unrelatedPrincipal = await inviteAndAccept(adminToken, 'PRINCIPAL', 'principal.two')
  report.testData.unrelatedPrincipalEmail = unrelatedPrincipal.email
  report.testData.unrelatedPrincipalUserId = unrelatedPrincipal.auth.user.id

  await expectBlocked('principal cannot create admin invitations', 'POST', '/admin/invitations', {
    fullName: 'Forbidden Invite',
    email: `forbidden.${stamp}@qa.arrivalos.local`,
    accountType: 'ADMIN',
  }, principal.auth.accessToken)

  const principals = await http('GET', '/admin/principals', undefined, adminToken, [200])
  if (principals.data.some((p) => p.email === principal.email)) recordPass('accepted principal visible to admin')
  else recordFailure('accepted principal missing from admin principals list', 'P2 major', principal.email, principals)

  const watcher1 = `watcher.created.${stamp}@qa.arrivalos.local`
  const trip = await http('POST', '/admin/trips', {
    flightNumber: `QA${stamp.slice(-6)}`,
    arrivalAirport: 'LHR',
    arrivalTerminal: 'Terminal 5',
    meetingPoint: 'Arrivals Hall North',
    scheduledArrivalAt: new Date(Date.now() + 86400000).toISOString(),
    principals: [{ userAccountId: principal.auth.user.id, primaryContact: true }],
    watchers: [{ fullName: 'Created Watcher', email: watcher1, phone: '+15550000003' }],
    checkpoints: [{ name: 'Immigration' }, { name: 'Baggage' }],
  }, adminToken, [200])
  report.testData.tripId = trip.data.id
  recordPass('admin-created trip succeeds', trip.data.id)

  await http('GET', '/admin/trips', undefined, adminToken, [200])
  await http('GET', '/admin/trips/active', undefined, adminToken, [200])
  const detail = await http('GET', `/admin/trips/${trip.data.id}`, undefined, adminToken, [200])
  if (detail.data.watchers?.length && detail.data.principals?.length && detail.data.checkpoints?.length) {
    recordPass('admin trip detail contains principals, watchers, timeline, checkpoints')
  } else {
    recordFailure('admin trip detail missing expected aggregates', 'P2 major', 'principals/watchers/checkpoints not complete', detail)
  }

  const principalTrips = await http('GET', '/principal/trips', undefined, principal.auth.accessToken, [200])
  if (principalTrips.data.some((t) => t.id === trip.data.id)) recordPass('linked principal sees trip')
  else recordFailure('linked principal cannot see assigned trip', 'P1 critical', trip.data.id, principalTrips)

  await expectBlocked('unrelated principal cannot view trip', 'GET', `/principal/trips/${trip.data.id}`, undefined, unrelatedPrincipal.auth.accessToken, [404, 403])
  await expectBlocked('principal cannot access admin trip list', 'GET', '/admin/trips', undefined, principal.auth.accessToken)

  const principalCreate = await http('POST', '/principal/trips', {
    flightNumber: 'SELF1',
    arrivalAirport: 'LHR',
    principals: [{ userAccountId: principal.auth.user.id }],
  }, principal.auth.accessToken)
  if ([401, 403, 404, 405].includes(principalCreate.status)) {
    recordGap('principal-created trip is not implemented', `POST /api/principal/trips returned ${principalCreate.status}`)
  } else {
    recordPass('principal-created trip endpoint exists', `returned ${principalCreate.status}`)
  }

  await http('PATCH', `/admin/trips/${trip.data.id}`, {
    meetingPoint: 'Updated QA meeting point',
    arrivalTerminal: 'Terminal 5B',
  }, adminToken, [200])
  recordPass('admin can update trip fields')

  await http('POST', `/admin/trips/${trip.data.id}/principals`, {
    userAccountId: unrelatedPrincipal.auth.user.id,
  }, adminToken, [200])
  recordPass('admin can add another principal')

  const adminWatcher = `watcher.admin.added.${stamp}@qa.arrivalos.local`
  await http('POST', `/admin/trips/${trip.data.id}/watchers`, {
    fullName: 'Admin Added Watcher',
    email: adminWatcher,
    phone: '+15550000004',
  }, adminToken, [200])
  recordPass('admin can add another watcher')

  const duplicateWatcher = await http('POST', `/admin/trips/${trip.data.id}/watchers`, {
    fullName: 'Duplicate Watcher',
    email: adminWatcher,
  }, adminToken)
  if (duplicateWatcher.status === 409) recordPass('duplicate watcher on same trip rejected')
  else recordFailure('duplicate watcher accepted', 'P1 critical', `got ${duplicateWatcher.status}`, duplicateWatcher)

  const principalWatcher = `watcher.principal.added.${stamp}@qa.arrivalos.local`
  await http('POST', `/principal/trips/${trip.data.id}/watchers`, {
    fullName: 'Principal Added Watcher',
    email: principalWatcher,
  }, principal.auth.accessToken, [200])
  recordPass('assigned principal can add watcher')

  const watcherLogin = await http('POST', '/auth/login', { email: principalWatcher, password }, null)
  if (watcherLogin.status === 401) recordPass('watcher is not login-capable')
  else recordFailure('watcher can log in or gets unexpected auth response', 'P1 critical', `got ${watcherLogin.status}`, watcherLogin)

  const thirdPrincipal = await inviteAndAccept(adminToken, 'PRINCIPAL', 'principal.three')
  await expectBlocked('unassigned principal cannot add watchers', 'POST', `/principal/trips/${trip.data.id}/watchers`, {
    fullName: 'Forbidden Watcher',
    email: `forbidden.watcher.${stamp}@qa.arrivalos.local`,
  }, thirdPrincipal.auth.accessToken, [404, 403])

  const conciergeA = await http('POST', '/admin/concierges', {
    fullName: 'QA Concierge A',
    phone: '+15550000005',
    publicId: `qa-concierge-a-${stamp}`,
    photoUrl: 'https://example.com/a.jpg',
  }, adminToken, [200])
  const conciergeB = await http('POST', '/admin/concierges', {
    fullName: 'QA Concierge B',
    phone: '+15550000006',
    publicId: `qa-concierge-b-${stamp}`,
  }, adminToken, [200])
  report.testData.conciergeAPublicId = conciergeA.data.publicId
  report.testData.conciergeBPublicId = conciergeB.data.publicId
  recordPass('admin can create concierges')

  await http('GET', '/admin/concierges', undefined, adminToken, [200])
  await http('GET', `/admin/concierges/${conciergeA.data.id}`, undefined, adminToken, [200])
  await http('PATCH', `/admin/concierges/${conciergeA.data.id}`, { phone: '+15550000999' }, adminToken, [200])
  recordPass('concierge list/detail/update work')

  const duplicatePublicId = await http('POST', '/admin/concierges', {
    fullName: 'Duplicate Concierge',
    phone: '+15550000007',
    publicId: conciergeA.data.publicId,
  }, adminToken)
  if (duplicatePublicId.status === 409) recordPass('duplicate concierge publicId rejected')
  else recordFailure('duplicate concierge publicId accepted', 'P1 critical', `got ${duplicatePublicId.status}`, duplicatePublicId)

  await http('PATCH', `/admin/concierges/${conciergeA.data.id}`, { active: false }, adminToken, [200])
  const inactiveAssign = await http('POST', `/admin/trips/${trip.data.id}/concierge-assignment`, {
    conciergeId: conciergeA.data.id,
  }, adminToken)
  if (inactiveAssign.status === 409) recordPass('inactive concierge cannot be assigned')
  else recordFailure('inactive concierge can be assigned', 'P1 critical', `got ${inactiveAssign.status}`, inactiveAssign)
  await http('PATCH', `/admin/concierges/${conciergeA.data.id}`, { active: true }, adminToken, [200])

  const deleteConcierge = await http('DELETE', `/admin/concierges/${conciergeA.data.id}`, undefined, adminToken)
  if ([401, 403, 404, 405].includes(deleteConcierge.status)) recordGap('true concierge delete/remove endpoint missing', `DELETE returned ${deleteConcierge.status}; deactivate is the available removal model`)
  else recordFailure('unexpected concierge delete behavior', 'P2 major', `DELETE returned ${deleteConcierge.status}`, deleteConcierge)

  await http('POST', `/admin/trips/${trip.data.id}/concierge-assignment`, {
    conciergeId: conciergeA.data.id,
  }, adminToken, [200])
  const accessA = await http('POST', `/admin/trips/${trip.data.id}/concierge-access-links`, {
    conciergeId: conciergeA.data.id,
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
  }, adminToken, [200])
  report.testData.conciergeAccessUrlA = accessA.data.updateUrl
  recordPass('assigned concierge access link generated', accessA.data.updateUrl)

  const conciergeViewA = await http('GET', `/concierge/trips/${trip.data.id}?accessToken=${encodeURIComponent(accessA.data.token)}`, undefined, null, [200])
  if (!JSON.stringify(conciergeViewA.data).includes('notificationAttempts')) recordPass('concierge view omits admin-only notification attempts')
  else recordFailure('concierge view exposes notification attempts', 'P1 critical', 'safe view includes notificationAttempts', conciergeViewA)

  const linkForUnassignedB = await http('POST', `/admin/trips/${trip.data.id}/concierge-access-links`, {
    conciergeId: conciergeB.data.id,
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
  }, adminToken)
  if (linkForUnassignedB.status === 409) recordPass('unassigned concierge cannot receive access link')
  else recordFailure('unassigned concierge access link generated', 'P1 critical', `got ${linkForUnassignedB.status}`, linkForUnassignedB)

  await http('POST', `/admin/trips/${trip.data.id}/concierge-assignment`, {
    conciergeId: conciergeB.data.id,
  }, adminToken, [200])
  const oldTokenUpdate = await http('POST', `/concierge/trips/${trip.data.id}/timeline-events`, {
    accessToken: accessA.data.token,
    eventType: 'CONCIERGE_IN_POSITION',
    idempotencyKey: `old-token-${stamp}`,
  }, null)
  if (oldTokenUpdate.status === 403) recordPass('old concierge token blocked after reassignment')
  else recordFailure('old concierge token remains usable after reassignment', 'P1 critical', `got ${oldTokenUpdate.status}`, oldTokenUpdate)

  const expiredLink = await http('POST', `/admin/trips/${trip.data.id}/concierge-access-links`, {
    conciergeId: conciergeB.data.id,
    expiresAt: new Date(Date.now() - 1000).toISOString(),
  }, adminToken)
  if (expiredLink.status === 400) recordPass('past access-link expiry rejected')
  else recordFailure('past access-link expiry accepted or wrong error', 'P2 major', `got ${expiredLink.status}`, expiredLink)

  const accessB = await http('POST', `/admin/trips/${trip.data.id}/concierge-access-links`, {
    conciergeId: conciergeB.data.id,
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
  }, adminToken, [200])
  report.testData.conciergeAccessUrlB = accessB.data.updateUrl

  const statusEvents = [
    ['CONCIERGE_IN_POSITION', undefined, 'Concierge waiting at arrivals'],
    ['FLIGHT_LANDED', undefined, 'Flight is on stand'],
    ['CLIENT_MET', undefined, 'Client met at meeting point'],
    ['CHECKPOINT_STARTED', 'Immigration', 'Queue entered'],
    ['CHECKPOINT_COMPLETED', 'Immigration', undefined],
    ['CHECKPOINT_STARTED', 'Baggage', 'Baggage wait started'],
    ['CHECKPOINT_COMPLETED', 'Baggage', 'Bags collected'],
    ['TERMINAL_EXITED', undefined, 'Exited terminal'],
    ['HANDOVER_COMPLETED', undefined, 'Client handed over'],
  ]
  let duplicateResponse
  for (const [index, [eventType, checkpointName, note]] of statusEvents.entries()) {
    const key = `${eventType}-${checkpointName ?? 'trip'}-${index}-${stamp}`
    const first = await http('POST', `/concierge/trips/${trip.data.id}/timeline-events`, {
      accessToken: accessB.data.token,
      eventType,
      checkpointName,
      note,
      idempotencyKey: key,
    }, null, [200])
    if (eventType === 'FLIGHT_LANDED') {
      duplicateResponse = await http('POST', `/concierge/trips/${trip.data.id}/timeline-events`, {
        accessToken: accessB.data.token,
        eventType,
        checkpointName,
        note,
        idempotencyKey: key,
      }, null, [200])
    }
    if (first.data.duplicate === true) {
      recordFailure('first status transition treated as duplicate', 'P1 critical', eventType, first)
    }
  }
  if (duplicateResponse?.data?.duplicate === true) recordPass('idempotent duplicate status update does not create new timeline event')
  else recordFailure('idempotency duplicate response not marked duplicate', 'P1 critical', 'FLIGHT_LANDED retry was not duplicate', duplicateResponse)

  const invalidTransition = await http('POST', `/concierge/trips/${trip.data.id}/timeline-events`, {
    accessToken: accessB.data.token,
    eventType: 'CLIENT_MET',
    idempotencyKey: `invalid-${stamp}`,
  }, null)
  if (invalidTransition.status === 409) recordPass('invalid status transition rejected')
  else recordFailure('invalid status transition accepted', 'P1 critical', `got ${invalidTransition.status}`, invalidTransition)

  await http('POST', `/concierge/trips/${trip.data.id}/timeline-events`, {
    accessToken: accessB.data.token,
    eventType: 'TRIP_COMPLETED',
    note: 'Trip complete',
    idempotencyKey: `TRIP_COMPLETED-${stamp}`,
  }, null, [200])
  recordPass('concierge can complete trip through valid workflow')

  const afterClosedToken = await http('GET', `/concierge/trips/${trip.data.id}?accessToken=${encodeURIComponent(accessB.data.token)}`, undefined, null)
  if (afterClosedToken.status === 403) recordPass('completed trip revokes concierge capability link')
  else recordFailure('completed trip does not revoke concierge capability link', 'P1 critical', `got ${afterClosedToken.status}`, afterClosedToken)

  const attempts = await http('GET', `/admin/trips/${trip.data.id}/notification-attempts`, undefined, adminToken, [200])
  report.apiResponses.notificationAttemptsCount = attempts.data.length
  const attemptShapeOk = attempts.data.every((a) => a.recipientType && a.channel === 'EMAIL' && a.provider === 'EMAIL' && a.status && a.createdAt)
  if (attemptShapeOk && attempts.data.length > 0) recordPass('notification attempts persisted with expected shape', `${attempts.data.length} attempts`)
  else recordFailure('notification attempts missing or malformed', 'P1 critical', 'attempt shape/count invalid', attempts)
  if (!attempts.data.some((a) => a.recipientType === 'OPS' || a.recipientType === 'ADMIN')) {
    recordGap('admin/ops status-update email notifications are missing', 'No OPS/ADMIN notification attempts were persisted for trip status updates')
  }

  const finalEmails = await emails()
  const wantedSubjects = [
    'ArrivalOS trip created',
    'ArrivalOS watcher access added',
    'ArrivalOS concierge in position',
    'ArrivalOS flight landed',
    'ArrivalOS client met',
    'ArrivalOS checkpoint started',
    'ArrivalOS checkpoint completed',
    'ArrivalOS handover completed',
    'ArrivalOS trip completed',
  ]
  for (const subject of wantedSubjects) {
    const matching = finalEmails.filter((e) => e.subject === subject)
    if (matching.length) {
      recordPass(`email delivered: ${subject}`, `${matching.length} messages`)
      for (const email of matching) {
        report.emailEvidence.push({
          subject: email.subject,
          to: email.to?.map((t) => t.address).join(','),
          id: email.id,
        })
      }
    } else {
      recordFailure(`missing email: ${subject}`, 'P2 major', 'MailDev has no matching message')
    }
  }

  const apiInvalidUuid = await http('GET', '/admin/trips/not-a-uuid', undefined, adminToken)
  if (apiInvalidUuid.status === 400) recordPass('invalid UUID returns useful client error')
  else recordFailure('invalid UUID does not return 400', 'P3 minor', `got ${apiInvalidUuid.status}`, apiInvalidUuid)

  const invalidEnum = await http('POST', `/admin/trips/${trip.data.id}/timeline-events`, {
    eventType: 'NOT_A_STATUS',
    idempotencyKey: `bad-enum-${stamp}`,
  }, adminToken)
  if (invalidEnum.status === 400) recordPass('invalid enum returns client error')
  else recordFailure('invalid enum does not return 400', 'P3 minor', `got ${invalidEnum.status}`, invalidEnum)

  const missingTokenConcierge = await http('GET', `/concierge/trips/${trip.data.id}`, undefined, null)
  if ([400, 401].includes(missingTokenConcierge.status)) recordPass('concierge access token is required')
  else recordFailure('concierge route allows missing token or wrong error', 'P1 critical', `got ${missingTokenConcierge.status}`, missingTokenConcierge)

  report.testData.allEmailsCreated = {
    watcher1,
    adminWatcher,
    principalWatcher,
    unverifiedEmail,
  }

  const fs = await import('node:fs/promises')
  await fs.writeFile('.scratch/qa-arrivalos-e2e-report.json', JSON.stringify(report, null, 2))
  console.log(`REPORT .scratch/qa-arrivalos-e2e-report.json`)
}

main().catch(async (error) => {
  recordFailure('QA runner crashed', 'P0 blocker', error.stack || error.message)
  const fs = await import('node:fs/promises')
  await fs.writeFile('.scratch/qa-arrivalos-e2e-report.json', JSON.stringify(report, null, 2))
  process.exitCode = 1
})
