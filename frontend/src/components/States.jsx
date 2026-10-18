// Shared loading / empty / error / notice blocks so every page reports state the same way.

export function Loading({ label = 'Loading…' }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="state empty-state">
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  )
}

export function ErrorNotice({ children }) {
  if (!children) return null
  return <div className="notice error" role="alert">{children}</div>
}

export function SuccessNotice({ children }) {
  if (!children) return null
  return <div className="notice success" role="status">{children}</div>
}

/** Placeholder rows while the first load is in flight (layout stays stable, no fake data). */
export function GridSkeleton({ count = 6 }) {
  return (
    <div className="products-grid" aria-hidden="true">
      {Array.from({ length: count }).map((_, i) => (
        <div className="product-card skeleton" key={i}>
          <div className="product-media" />
          <div className="product-body">
            <span className="sk-line short" />
            <span className="sk-line" />
            <span className="sk-line short" />
          </div>
        </div>
      ))}
    </div>
  )
}

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  )
}
