import { useEffect, useMemo, useState } from 'react'
import { BrowserRouter, Routes, Route, Link, useNavigate, useParams } from 'react-router-dom'
import './App.css'
import { apiFetch, getUser } from './api'
import { catalogue, failureMessage, networkMessage, roleForScope, scopeForRole, useProductList, useRoleGuard } from './hooks'
import { formatPrice, isLowStock } from './utils'
import LoginPage from './pages/LoginPage'
import RetailerDashboard from './pages/RetailerDashboard'
import WholesalerDashboard from './pages/WholesalerDashboard'
import LandingPage from './pages/LandingPage'
import Navbar from './components/Navbar'
import ProductCard, { StockBadge } from './components/ProductCard'
import { Thumb } from './components/InventoryDashboard'
import { EmptyState, ErrorNotice, GridSkeleton, Loading, PageHeader, SuccessNotice } from './components/States'

// ---------------------------------------------------------------------------
// Catalogue (public browsing)
// ---------------------------------------------------------------------------
function ProductsPage() {
  const user = getUser()
  // A wholesaler browses the wholesale catalogue; everyone else browses the retail catalogue.
  const scope = scopeForRole(user?.role)
  const api = catalogue(scope)
  const { products, loading, error, reload } = useProductList(api.list)

  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('')
  const [sort, setSort] = useState('name')

  const categories = useMemo(
    () => [...new Set(products.map((p) => p.category).filter(Boolean))].sort((a, b) => a.localeCompare(b)),
    [products]
  )

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    const list = products
      .filter((p) => !category || p.category === category)
      .filter((p) => !q || `${p.name} ${p.category || ''} ${p.description || ''}`.toLowerCase().includes(q))
    const by = {
      name: (a, b) => (a.name || '').localeCompare(b.name || ''),
      'price-asc': (a, b) => a.price - b.price,
      'price-desc': (a, b) => b.price - a.price,
      'stock-asc': (a, b) => a.quantity - b.quantity,
    }[sort]
    return [...list].sort(by)
  }, [products, query, category, sort])

  const title = scope === 'wholesale' ? 'Wholesale catalogue' : 'Products'
  const subtitle = scope === 'wholesale'
    ? 'Bulk stock listed by wholesalers'
    : 'Browse what local retailers have in stock'

  return (
    <main className="page">
      <PageHeader title={title} subtitle={subtitle} />
      <ErrorNotice>{error}</ErrorNotice>

      <div className="toolbar">
        <label className="sr-only" htmlFor="catalogue-search">Search products</label>
        <input
          id="catalogue-search"
          className="search"
          type="search"
          placeholder="Search by name, category or description"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <label className="sr-only" htmlFor="catalogue-category">Category</label>
        <select id="catalogue-category" value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">All categories</option>
          {categories.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <label className="sr-only" htmlFor="catalogue-sort">Sort by</label>
        <select id="catalogue-sort" value={sort} onChange={(e) => setSort(e.target.value)}>
          <option value="name">Name (A–Z)</option>
          <option value="price-asc">Price: low to high</option>
          <option value="price-desc">Price: high to low</option>
          <option value="stock-asc">Stock: lowest first</option>
        </select>
        {!loading && products.length > 0 && (
          <span className="result-count">{visible.length} of {products.length} products</span>
        )}
      </div>

      {loading ? (
        <GridSkeleton />
      ) : products.length === 0 ? (
        error ? (
          <div className="state">
            <button type="button" className="btn" onClick={reload}>Try again</button>
          </div>
        ) : (
          <EmptyState title="No products listed yet">
            Check back soon. Retailers and wholesalers add their stock here.
          </EmptyState>
        )
      ) : visible.length === 0 ? (
        <EmptyState
          title="No matching products"
          action={<button type="button" className="btn" onClick={() => { setQuery(''); setCategory('') }}>Clear filters</button>}
        >
          Nothing matches your search and filters.
        </EmptyState>
      ) : (
        <div className="products-grid">
          {visible.map((p) => (
            <ProductCard key={p.id} product={p} to={api.detailRoute(p.id)} imageUrl={api.image(p.id)} />
          ))}
        </div>
      )}
    </main>
  )
}

// ---------------------------------------------------------------------------
// Shared product form fields (create + edit)
// ---------------------------------------------------------------------------
function validateProduct(form) {
  const errors = {}
  if (!form.name || !form.name.trim()) errors.name = 'Enter a product name.'
  if (form.price !== '' && (Number.isNaN(Number(form.price)) || Number(form.price) < 0)) {
    errors.price = 'Price cannot be negative.'
  }
  if (form.quantity !== '' && (!Number.isInteger(Number(form.quantity)) || Number(form.quantity) < 0)) {
    errors.quantity = 'Quantity must be a whole number, 0 or more.'
  }
  return errors
}

function toPayload(form) {
  return {
    name: form.name.trim(),
    description: form.description || '',
    category: form.category || '',
    price: Number(form.price) || 0,
    quantity: Number(form.quantity) || 0,
  }
}

function toFormData(payload, imageFile) {
  const formData = new FormData()
  formData.append('product', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  if (imageFile) formData.append('imageFile', imageFile)
  return formData
}

function ProductFields({ form, errors, onChange, imageFile, onImageChange, imagePreview, currentImageUrl, idPrefix }) {
  const id = (n) => `${idPrefix}-${n}`
  const err = (n) => (errors[n] ? { 'aria-invalid': true, 'aria-describedby': id(`${n}-err`) } : {})
  const msg = (n) => errors[n] && <span id={id(`${n}-err`)} className="hint" style={{ color: 'var(--danger)' }}>{errors[n]}</span>
  return (
    <>
      <div className="row">
        <label htmlFor={id('name')}>Name</label>
        <input id={id('name')} name="name" value={form.name} onChange={onChange} placeholder="e.g. Basmati Rice 1kg" required {...err('name')} />
        {msg('name')}
      </div>
      <div className="row">
        <label htmlFor={id('description')}>Description</label>
        <textarea id={id('description')} name="description" value={form.description} onChange={onChange} placeholder="Short description (optional)" rows="3" />
      </div>
      <div className="form-grid">
        <div className="row">
          <label htmlFor={id('category')}>Category</label>
          <input id={id('category')} name="category" value={form.category} onChange={onChange} placeholder="e.g. Grocery" />
        </div>
        <div className="row">
          <label htmlFor={id('price')}>Price (₹)</label>
          <input id={id('price')} name="price" type="number" min="0" step="0.01" value={form.price} onChange={onChange} placeholder="0.00" {...err('price')} />
          {msg('price')}
        </div>
      </div>
      <div className="form-grid">
        <div className="row">
          <label htmlFor={id('quantity')}>Quantity in stock</label>
          <input id={id('quantity')} name="quantity" type="number" min="0" step="1" value={form.quantity} onChange={onChange} placeholder="0" {...err('quantity')} />
          {msg('quantity')}
          <span className="hint">Under 10 units is flagged as low stock.</span>
        </div>
      </div>
      <div className="row">
        <label htmlFor={id('image')}>Product image {currentImageUrl ? '(leave empty to keep the current one)' : '(optional)'}</label>
        <input id={id('image')} type="file" accept="image/png,image/jpeg,image/gif,image/webp" onChange={onImageChange} className="file-input" />
        {imagePreview ? (
          <div className="image-preview-container">
            <img src={imagePreview} alt="Selected product preview" className="image-preview" />
          </div>
        ) : currentImageUrl ? (
          <div className="image-preview-container">
            <img src={currentImageUrl} alt="Current product" className="image-preview" />
            <p className="hint">Current image</p>
          </div>
        ) : null}
        {imageFile ? <span className="hint">{imageFile.name}</span> : <span className="hint">PNG, JPEG, GIF or WebP, up to 5 MB.</span>}
      </div>
    </>
  )
}

function useImagePicker() {
  const [imageFile, setImageFile] = useState(null)
  const [imagePreview, setImagePreview] = useState(null)
  const onImageChange = (e) => {
    const file = e.target.files[0]
    if (!file) return
    setImageFile(file)
    const reader = new FileReader()
    reader.onloadend = () => setImagePreview(reader.result)
    reader.readAsDataURL(file)
  }
  const reset = () => { setImageFile(null); setImagePreview(null) }
  return { imageFile, imagePreview, onImageChange, reset }
}

// ---------------------------------------------------------------------------
// Create
// ---------------------------------------------------------------------------
function CreatePage() {
  const user = useRoleGuard(['RETAILER', 'WHOLESALER'])
  const navigate = useNavigate()
  const [form, setForm] = useState({ name: '', description: '', category: '', price: '', quantity: '' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const picker = useImagePicker()

  if (!user) return <div className="page"><Loading /></div>
  const scope = scopeForRole(user.role)
  const api = catalogue(scope)

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    const found = validateProduct(form)
    setErrors(found)
    if (Object.keys(found).length) return
    setLoading(true)
    setError('')
    try {
      const res = await apiFetch(api.create, { method: 'POST', body: toFormData(toPayload(form), picker.imageFile) })
      if (res.status === 201) {
        navigate(api.dashboardRoute, { state: { notice: `“${form.name.trim()}” was added to your catalogue.` } })
        return
      }
      setError(await failureMessage(res, 'Creating the product'))
    } catch (err) {
      setError(networkMessage(err, 'Failed to create product.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="page">
      <PageHeader
        title="Add product"
        subtitle={scope === 'wholesale' ? 'List an item in the wholesale catalogue' : 'List an item in your shop'}
        actions={<Link className="btn" to={api.dashboardRoute}>Back to dashboard</Link>}
      />
      <div className="panel">
        <ErrorNotice>{error}</ErrorNotice>
        <form className="form" onSubmit={handleSubmit} noValidate>
          <ProductFields form={form} errors={errors} onChange={handleChange} idPrefix="new" {...picker} />
          <div className="actions">
            <button type="submit" className="btn primary" disabled={loading}>{loading ? 'Saving…' : 'Create product'}</button>
            <Link className="btn ghost" to={api.dashboardRoute}>Cancel</Link>
          </div>
        </form>
      </div>
    </main>
  )
}

// ---------------------------------------------------------------------------
// Detail (+ edit / delete for the owner's role)
// ---------------------------------------------------------------------------
function ProductDetailPage({ scope }) {
  const { id } = useParams()
  const navigate = useNavigate()
  const api = catalogue(scope)
  const user = getUser()
  // Show manage controls only to the matching seller role; the server still decides (owner check, 403).
  const canManage = user?.role === roleForScope(scope)

  const [product, setProduct] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [isEditing, setIsEditing] = useState(false)
  const [form, setForm] = useState({ name: '', description: '', category: '', price: '', quantity: '' })
  const [errors, setErrors] = useState({})
  const picker = useImagePicker()

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const res = await fetch(api.one(id))
      if (!res.ok) {
        setError(await failureMessage(res, 'Loading the product'))
        return
      }
      const data = await res.json()
      setProduct(data)
      setForm({
        name: data.name || '',
        description: data.description || '',
        category: data.category || '',
        price: data.price ?? '',
        quantity: data.quantity ?? '',
      })
    } catch (e) {
      setError(networkMessage(e, 'Failed to load product.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, scope])

  const backTo = canManage ? api.dashboardRoute : '/products'
  const backLabel = canManage ? 'Back to dashboard' : 'Back to products'

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const startEditing = () => { setNotice(''); setError(''); setErrors({}); setIsEditing(true) }
  const cancelEditing = () => {
    setIsEditing(false)
    setErrors({})
    picker.reset()
    if (product) {
      setForm({
        name: product.name || '', description: product.description || '', category: product.category || '',
        price: product.price ?? '', quantity: product.quantity ?? '',
      })
    }
  }

  const handleSave = async (e) => {
    e.preventDefault()
    const found = validateProduct(form)
    setErrors(found)
    if (Object.keys(found).length) return
    setSaving(true)
    setError('')
    try {
      const res = await apiFetch(api.update(id), { method: 'PUT', body: toFormData(toPayload(form), picker.imageFile) })
      if (res.status === 200) {
        setIsEditing(false)
        picker.reset()
        setNotice('Changes saved.')
        await load()
        return
      }
      setError(await failureMessage(res, 'Saving the product'))
    } catch (err) {
      setError(networkMessage(err, 'Failed to save product.'))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!window.confirm(`Delete “${product.name}”? This cannot be undone.`)) return
    setSaving(true)
    setError('')
    try {
      const res = await apiFetch(api.remove(id), { method: 'DELETE' })
      if (res.status === 200) {
        navigate(api.dashboardRoute, { state: { notice: `“${product.name}” was deleted.` } })
        return
      }
      setError(await failureMessage(res, 'Deleting the product'))
    } catch (err) {
      setError(networkMessage(err, 'Failed to delete product.'))
    } finally {
      setSaving(false)
    }
  }

  if (loading && !product) {
    return <main className="page"><Loading label="Loading product…" /></main>
  }

  if (!product) {
    return (
      <main className="page">
        <PageHeader title="Product" actions={<Link className="btn" to={backTo}>{backLabel}</Link>} />
        <ErrorNotice>{error || 'Product not found.'}</ErrorNotice>
      </main>
    )
  }

  const hasImage = Boolean(product.imageName || product.imageType)
  const imageUrl = api.image(product.id)

  if (isEditing) {
    return (
      <main className="page">
        <PageHeader title="Edit product" actions={<Link className="btn" to={backTo}>{backLabel}</Link>} />
        <div className="panel">
          <ErrorNotice>{error}</ErrorNotice>
          <form className="form" onSubmit={handleSave} noValidate>
            <ProductFields
              form={form}
              errors={errors}
              onChange={handleChange}
              idPrefix="edit"
              {...picker}
              currentImageUrl={hasImage ? imageUrl : null}
            />
            <div className="actions">
              <button type="submit" className="btn primary" disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</button>
              <button type="button" className="btn ghost" onClick={cancelEditing} disabled={saving}>Cancel</button>
            </div>
          </form>
        </div>
      </main>
    )
  }

  return (
    <main className="page">
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to={backTo}>{canManage ? 'Dashboard' : 'Products'}</Link> / <span>{product.name}</span>
      </nav>
      <SuccessNotice>{notice}</SuccessNotice>
      <ErrorNotice>{error}</ErrorNotice>
      <div className="panel">
        <div className="product-detail">
          <div className="product-detail-image-container">
            {hasImage ? (
              <img
                src={imageUrl}
                alt={product.name}
                className="product-detail-image"
                onError={(e) => { e.currentTarget.style.display = 'none' }}
              />
            ) : (
              <span className="media-placeholder" aria-hidden="true">{(product.name || '?').charAt(0).toUpperCase()}</span>
            )}
          </div>
          <div className="product-detail-info">
            {product.category && <span className="product-category">{product.category}</span>}
            <h1 className="detail-title">{product.name}</h1>
            <div className="detail-price">{formatPrice(product.price)}</div>
            <div><StockBadge quantity={product.quantity} /></div>
            {product.description && <p className="detail-description">{product.description}</p>}
            <dl className="detail-list">
              <dt>Quantity</dt><dd>{product.quantity}</dd>
              <dt>Product ID</dt><dd>{product.id}</dd>
            </dl>
            {canManage && (
              <div className="actions">
                <button type="button" className="btn primary" onClick={startEditing} disabled={saving}>Edit product</button>
                <button type="button" className="btn danger" onClick={handleDelete} disabled={saving}>
                  {saving ? 'Working…' : 'Delete product'}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </main>
  )
}

// ---------------------------------------------------------------------------
// Edit stock (quick price / quantity editor)
// ---------------------------------------------------------------------------
function EditPage() {
  const user = useRoleGuard(['RETAILER', 'WHOLESALER'])
  const scope = scopeForRole(user?.role)
  const api = catalogue(scope)
  const { products, loading, error: loadError, reload } = useProductList(api.mine, { auth: true })
  const [form, setForm] = useState({ id: '', name: '', price: '', quantity: '' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [saving, setSaving] = useState(false)

  if (!user) return <div className="page"><Loading /></div>

  const select = (p) => {
    setNotice('')
    setError('')
    setErrors({})
    setForm({ id: String(p.id), name: p.name || '', price: String(p.price ?? ''), quantity: String(p.quantity ?? '') })
  }

  const save = async (e) => {
    e.preventDefault()
    if (!form.id) { setError('Select a product from the list first.'); return }
    const found = validateProduct(form)
    setErrors(found)
    if (Object.keys(found).length) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      // Keep description/category: the update endpoint replaces every field it receives.
      const existing = products.find((p) => String(p.id) === String(form.id)) || {}
      const payload = {
        name: form.name.trim(),
        description: existing.description,
        category: existing.category,
        price: Number(form.price) || 0,
        quantity: Number(form.quantity) || 0,
      }
      const res = await apiFetch(api.update(form.id), { method: 'PUT', body: toFormData(payload, null) })
      if (res.status === 200) {
        setNotice(`Saved “${payload.name}”.`)
        setForm({ id: '', name: '', price: '', quantity: '' })
        await reload()
        return
      }
      setError(await failureMessage(res, 'Saving the product'))
    } catch (err) {
      setError(networkMessage(err, 'Failed to save product.'))
    } finally {
      setSaving(false)
    }
  }

  const remove = async (p) => {
    if (!window.confirm(`Delete “${p.name}”? This cannot be undone.`)) return
    setError('')
    setNotice('')
    try {
      const res = await apiFetch(api.remove(p.id), { method: 'DELETE' })
      if (res.status === 200) {
        if (String(form.id) === String(p.id)) setForm({ id: '', name: '', price: '', quantity: '' })
        setNotice(`Deleted “${p.name}”.`)
        await reload()
        return
      }
      setError(await failureMessage(res, 'Deleting the product'))
    } catch (err) {
      setError(networkMessage(err, 'Failed to delete product.'))
    }
  }

  const fieldErr = (n) => (errors[n] ? { 'aria-invalid': true, 'aria-describedby': `es-${n}-err` } : {})
  const msg = (n) => errors[n] && <span id={`es-${n}-err`} className="hint" style={{ color: 'var(--danger)' }}>{errors[n]}</span>

  return (
    <main className="page">
      <PageHeader
        title="Edit stock"
        subtitle="Pick a product, then update its name, price or quantity"
        actions={<Link className="btn" to={api.dashboardRoute}>Back to dashboard</Link>}
      />
      <SuccessNotice>{notice}</SuccessNotice>
      <ErrorNotice>{error || loadError}</ErrorNotice>

      <div className="edit-layout">
        <div className="panel">
          <form className="form" onSubmit={save} noValidate>
            <div className="row">
              <label htmlFor="es-id">Selected product ID</label>
              <input id="es-id" value={form.id} readOnly placeholder="Select a product from the list" />
            </div>
            <div className="row">
              <label htmlFor="es-name">Name</label>
              <input id="es-name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} disabled={!form.id} {...fieldErr('name')} />
              {msg('name')}
            </div>
            <div className="row">
              <label htmlFor="es-price">Price (₹)</label>
              <input id="es-price" type="number" min="0" step="0.01" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} disabled={!form.id} {...fieldErr('price')} />
              {msg('price')}
            </div>
            <div className="row">
              <label htmlFor="es-quantity">Quantity</label>
              <input id="es-quantity" type="number" min="0" step="1" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} disabled={!form.id} {...fieldErr('quantity')} />
              {msg('quantity')}
            </div>
            <div className="actions">
              <button type="submit" className="btn primary" disabled={saving || !form.id}>{saving ? 'Saving…' : 'Save changes'}</button>
            </div>
          </form>
        </div>

        <div>
          {loading ? (
            <Loading label="Loading your products…" />
          ) : products.length === 0 ? (
            <EmptyState title="No products yet" action={<Link className="btn primary" to="/create">Add a product</Link>}>
              Add a product first, then you can edit it here.
            </EmptyState>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <caption className="sr-only">Your products. Select a row to edit it.</caption>
                <thead>
                  <tr>
                    <th scope="col">Product</th>
                    <th scope="col" className="num">Price</th>
                    <th scope="col" className="num">Qty</th>
                    <th scope="col"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((p) => (
                    <tr key={p.id} className={String(form.id) === String(p.id) ? 'is-selected' : ''}>
                      <td>
                        <div className="name-cell">
                          <Thumb product={p} imageUrl={api.image(p.id)} />
                          <div className="cell-name">{p.name}</div>
                        </div>
                      </td>
                      <td className="num">{formatPrice(p.price)}</td>
                      <td className="num">{p.quantity}</td>
                      <td className="actions-cell">
                        <button type="button" className="btn small" onClick={() => select(p)} aria-label={`Edit ${p.name}`}>Edit</button>{' '}
                        <button type="button" className="btn small danger" onClick={() => remove(p)} aria-label={`Delete ${p.name}`}>Delete</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </main>
  )
}

// ---------------------------------------------------------------------------
// Low stock (retailers)
// ---------------------------------------------------------------------------
function LowStockPage() {
  const user = useRoleGuard(['RETAILER'])
  const api = catalogue('retail')
  const { products, loading, error, reload } = useProductList(api.mine, { auth: true })

  const low = useMemo(
    () => products.filter((p) => isLowStock(p.quantity)).sort((a, b) => a.quantity - b.quantity),
    [products]
  )

  if (!user) return <div className="page"><Loading /></div>

  return (
    <main className="page">
      <PageHeader
        title="Low stock"
        subtitle="Products with fewer than 10 units, lowest first"
        actions={
          <>
            <button type="button" className="btn" onClick={reload} disabled={loading}>Refresh</button>
            <Link className="btn" to={api.dashboardRoute}>Back to dashboard</Link>
          </>
        }
      />
      <ErrorNotice>{error}</ErrorNotice>
      {loading ? (
        <Loading label="Checking your stock…" />
      ) : low.length === 0 && !error ? (
        <EmptyState title="Nothing to refill">All your products have 10 or more units in stock.</EmptyState>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <caption className="sr-only">Products to refill</caption>
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
              {low.map((p) => (
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
                    <Link className="btn small primary" to={api.detailRoute(p.id)}>Update stock</Link>
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

function NotFoundPage() {
  return (
    <main className="page">
      <EmptyState title="Page not found" action={<Link className="btn primary" to="/products">Browse products</Link>}>
        The page you are looking for does not exist.
      </EmptyState>
    </main>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/products" element={<ProductsPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/retailer/dashboard" element={<RetailerDashboard />} />
        <Route path="/retailer/low-stock" element={<LowStockPage />} />
        <Route path="/wholesaler/dashboard" element={<WholesalerDashboard />} />
        <Route path="/create" element={<CreatePage />} />
        <Route path="/edit" element={<EditPage />} />
        <Route path="/product/:id" element={<ProductDetailPage scope="retail" />} />
        <Route path="/wholesaler/product/:id" element={<ProductDetailPage scope="wholesale" />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  )
}
