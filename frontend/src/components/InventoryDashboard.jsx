import { useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { catalogue, roleForScope, useProductList, useRoleGuard } from '../hooks'
import { formatPrice, isLowStock } from '../utils'
import { StockBadge } from './ProductCard'
import { EmptyState, ErrorNotice, Loading, PageHeader, SuccessNotice } from './States'

export function Thumb({ product, imageUrl }) {
  const [failed, setFailed] = useState(false)
  const has = Boolean(product.imageName || product.imageType) && !failed
  return has ? (
    <img className="thumb" src={imageUrl} alt="" loading="lazy" onError={() => setFailed(true)} />
  ) : (
    <span className="thumb" aria-hidden="true">{(product.name || '?').charAt(0).toUpperCase()}</span>
  )
}

/**
 * Inventory dashboard shared by retailers and wholesalers. Everything shown is computed from the caller's own
 * products (GET .../mine); the summary figures are simple totals of that list.
 */
export default function InventoryDashboard({ scope }) {
  const api = catalogue(scope)
  const user = useRoleGuard([roleForScope(scope)])
  const { products, loading, error, reload } = useProductList(api.mine, { auth: true })
  const location = useLocation()
  const notice = location.state && location.state.notice

  const [query, setQuery] = useState('')
  const [lowOnly, setLowOnly] = useState(false)

  const lowCount = useMemo(() => products.filter((p) => isLowStock(p.quantity)).length, [products])
  const units = useMemo(() => products.reduce((sum, p) => sum + (Number(p.quantity) || 0), 0), [products])

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return products
      .filter((p) => !lowOnly || isLowStock(p.quantity))
      .filter((p) => !q || `${p.name} ${p.category || ''}`.toLowerCase().includes(q))
      .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
  }, [products, query, lowOnly])

  if (!user) return <div className="page"><Loading /></div>

  const title = scope === 'wholesale' ? 'Wholesaler dashboard' : 'Retailer dashboard'
  const subtitle = scope === 'wholesale' ? 'Your wholesale catalogue' : 'Your shop inventory'

  return (
    <main className="page">
      <PageHeader
        title={title}
        subtitle={`${subtitle} · signed in as ${user.email}`}
        actions={
          <>
            <button type="button" className="btn" onClick={reload} disabled={loading}>Refresh</button>
            <Link className="btn primary" to="/create">Add product</Link>
          </>
        }
      />

      <SuccessNotice>{notice}</SuccessNotice>
      <ErrorNotice>{error}</ErrorNotice>

      <section className="stats" aria-label="Inventory summary">
        <div className="stat">
          <div className="stat-label">Products</div>
          <div className="stat-value">{loading ? '–' : products.length}</div>
        </div>
        <div className="stat">
          <div className="stat-label">Units in stock</div>
          <div className="stat-value">{loading ? '–' : units}</div>
        </div>
        <div className={`stat${lowCount > 0 ? ' attention' : ''}`}>
          <div className="stat-label">Low stock (under 10)</div>
          <div className="stat-value">{loading ? '–' : lowCount}</div>
          {scope === 'retail' && lowCount > 0 && <Link to="/retailer/low-stock">Review items to refill</Link>}
        </div>
      </section>

      <div className="toolbar">
        <label className="sr-only" htmlFor="inv-search">Search your products</label>
        <input
          id="inv-search"
          className="search"
          type="search"
          placeholder="Search by name or category"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <label className="check">
          <input type="checkbox" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} /> Low stock only
        </label>
        {!loading && products.length > 0 && (
          <span className="result-count">{visible.length} of {products.length}</span>
        )}
      </div>

      {loading ? (
        <Loading label="Loading your products…" />
      ) : products.length === 0 && !error ? (
        <EmptyState
          title="No products yet"
          action={<Link className="btn primary" to="/create">Add your first product</Link>}
        >
          Products you add appear here, and customers can browse them in the catalogue.
        </EmptyState>
      ) : visible.length === 0 ? (
        <EmptyState title="Nothing matches">Try a different search, or clear the low-stock filter.</EmptyState>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <caption className="sr-only">Your products</caption>
            <thead>
              <tr>
                <th scope="col">Product</th>
                <th scope="col" className="num">Price</th>
                <th scope="col" className="num">Quantity</th>
                <th scope="col">Status</th>
                <th scope="col"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {visible.map((p) => (
                <tr key={p.id}>
                  <td>
                    <div className="name-cell">
                      <Thumb product={p} imageUrl={api.image(p.id)} />
                      <div>
                        <div className="cell-name">{p.name}</div>
                        {p.category && <div className="cell-sub">{p.category}</div>}
                      </div>
                    </div>
                  </td>
                  <td className="num">{formatPrice(p.price)}</td>
                  <td className="num">{p.quantity}</td>
                  <td><StockBadge quantity={p.quantity} /></td>
                  <td className="actions-cell">
                    <Link className="btn small" to={api.detailRoute(p.id)}>View / edit</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  )
}
