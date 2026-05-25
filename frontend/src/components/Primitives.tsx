import { Link } from '@tanstack/react-router'
import { ApiError, type FieldError } from '../api/types'

export function ApiErrorMessage({ error }: { error: unknown }) {
  if (!error) return null
  const apiError = error instanceof ApiError ? error : null
  const message = apiError?.message ?? 'Something went wrong. Please try again.'
  const fieldErrors = apiError?.fieldErrors ?? []

  return (
    <div className="api-error" role="alert">
      <strong>{message}</strong>
      {fieldErrors.length > 0 && (
        <ul>
          {fieldErrors.map((fieldError: FieldError) => (
            <li key={`${fieldError.field}-${fieldError.message}`}>
              {fieldError.field}: {fieldError.message}
            </li>
          ))}
        </ul>
      )}
      {apiError?.requestId && <small>Request ID: {apiError.requestId}</small>}
    </div>
  )
}

export function EmptyState({
  title,
  body,
  action,
}: {
  title: string
  body: string
  action?: { to: string; label: string }
}) {
  return (
    <section className="empty-state">
      <p className="eyebrow">No records</p>
      <h2>{title}</h2>
      <p>{body}</p>
      {action && (
        <Link className="primary-link" to={action.to}>
          {action.label}
        </Link>
      )}
    </section>
  )
}

export function LoadingState({ label = 'Loading operational records' }: { label?: string }) {
  return (
    <div className="loading-state" role="status">
      <span />
      {label}
    </div>
  )
}

export function SectionHeader({
  eyebrow,
  title,
  children,
}: {
  eyebrow?: string
  title: string
  children?: React.ReactNode
}) {
  return (
    <div className="section-header">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
      </div>
      {children}
    </div>
  )
}
