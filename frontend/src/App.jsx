import { useEffect, useMemo, useState, useCallback } from 'react'
import { BrowserRouter, Routes, Route, Link, useNavigate, useParams } from 'react-router-dom'
import './App.css'
import LoginPage from './pages/LoginPage'
import RetailerDashboard from './pages/RetailerDashboard'
import WholesalerDashboard from './pages/WholesalerDashboard'
import LandingPage from './pages/LandingPage'

function ProductsPage() {
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [user, setUser] = useState(null)
  const apiBase = useMemo(() => '/api', [])
  const navigate = useNavigate()

  useEffect(() => {
    // Load user from localStorage
    const userData = localStorage.getItem('user')
    if (userData) {
      try {
        setUser(JSON.parse(userData))
      } catch (e) {
        console.error('Failed to parse user data')
        setUser(null) // Explicitly set to null on error
      }
    } else {
      setUser(null) // Explicitly set to null if no user data
    }
  }, [])

  const getImageUrl = (productId) => {
    // Use different endpoint based on user role
    if (user?.role === 'WHOLESALER') {
      return `${apiBase}/wholesaler/products/${productId}/image`
    }
    return `${apiBase}/products/${productId}/image`
  }

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      setError('')
      try {
        // Read user role directly from localStorage to avoid race condition
        // This ensures we always use the correct endpoint
        let userRole = null
        const userData = localStorage.getItem('user')
        if (userData) {
          try {
            const parsed = JSON.parse(userData)
            userRole = parsed?.role
          } catch (e) {
            // Ignore parse errors, will default to retailer endpoint
          }
        }
        
        // Use different endpoint based on user role
        const endpoint = userRole === 'WHOLESALER' 
          ? `${apiBase}/wholesaler/products`
          : `${apiBase}/products`
        
        const res = await fetch(endpoint)
        if (res.status === 204) {
          // NO_CONTENT - no products found
          setProducts([])
          setError('')
          return
        }
        if (res.status === 500) {
          setError('Server error. Please try again later.')
          setProducts([]) // Clear products on server error
          return
        }
        if (!res.ok) {
          setProducts([]) // Clear products on other errors
          throw new Error(`Failed to load products (${res.status})`)
        }
        const data = await res.json()
        setProducts(data)
        setError('') // Clear error on success
      } catch (e) {
        setProducts([]) // Clear products on fetch error
        if (e.message.includes('Failed to fetch')) {
          setError('Network error. Please check your connection.')
        } else {
          setError(e.message || 'Failed to load products')
        }
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [apiBase])

  const handleLogout = () => {
    localStorage.removeItem('user')
    localStorage.removeItem('email')
    localStorage.removeItem('role')
    setUser(null)
    navigate('/login')
  }

  return (
    <div className="page">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <Link to="/" className="btn ghost" style={{ textDecoration: 'none' }}>🏠 Home</Link>
          <h1 style={{ margin: 0 }}>Products</h1>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          {user ? (
            <>
              <span style={{ color: '#666' }}>Logged in as: {user.email} ({user.role})</span>
              {user.role === 'RETAILER' && (
                <Link className="btn" to="/retailer/dashboard">Retailer Dashboard</Link>
              )}
              {user.role === 'WHOLESALER' && (
                <Link className="btn" to="/wholesaler/dashboard">Wholesaler Dashboard</Link>
              )}
              <button className="btn ghost" onClick={handleLogout}>Logout</button>
            </>
          ) : (
            <Link className="btn" to="/login">Login</Link>
          )}
        </div>
      </div>
      <div className="panel">
        <div className="panel-header">
          <h2>Existing Stock {loading && <span className="muted">(loading…)</span>}</h2>
          <div className="header-actions">
            {(user?.role === 'RETAILER' || user?.role === 'WHOLESALER') && (
              <>
                <Link className="btn primary" to="/create">Add Stock</Link>
                <Link className="btn ghost" to="/edit">Edit Stock</Link>
              </>
            )}
          </div>
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      <div className="products-grid">
        {loading && <div className="loading">Loading…</div>}
        {!loading && products.length === 0 && !error && (
          <div className="empty">No products found</div>
        )}
        {products.map((p) => (
          <Link to={`/product/${p.id}`} key={p.id} className="product-card-link">
            <div className="product-card">
              {(p.imageName || p.imageType) && (
                <div className="product-image-container">
                  <img 
                    src={getImageUrl(p.id)} 
                    alt={p.name}
                    className="product-image"
                    onError={(e) => {
                      e.target.parentElement.style.display = 'none'
                    }}
                  />
                </div>
              )}
              <div className="product-id">ID: {p.id}</div>
              <div className="product-name">{p.name}</div>
              <div className="product-price">₹{p.price}</div>
              <div className="product-quantity">Qty: {p.quantity}</div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}

function CreatePage() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [form, setForm] = useState({ name: '', description: '', category: '', price: '', quantity: '' })
  const [imageFile, setImageFile] = useState(null)
  const [imagePreview, setImagePreview] = useState(null)
  const [user, setUser] = useState(null)
  const apiBase = useMemo(() => '/api', [])
  const navigate = useNavigate()

  useEffect(() => {
    // Check if user is logged in and is retailer/wholesaler
    const userData = localStorage.getItem('user')
    if (!userData) {
      navigate('/login')
      return
    }
    try {
      const userObj = JSON.parse(userData)
      setUser(userObj)
      // Only retailers and wholesalers can create products
      if (userObj.role !== 'RETAILER' && userObj.role !== 'WHOLESALER') {
        navigate('/products')
      }
    } catch (e) {
      navigate('/login')
    }
  }, [navigate])

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const handleImageChange = (e) => {
    const file = e.target.files[0]
    if (file) {
      setImageFile(file)
      // Create preview
      const reader = new FileReader()
      reader.onloadend = () => {
        setImagePreview(reader.result)
      }
      reader.readAsDataURL(file)
    }
  }

  const createProduct = async () => {
    if (!form.name) {
      setError('Product name is required')
      return
    }
    setLoading(true)
    setError('')
    try {
      const productData = {
        name: form.name,
        description: form.description || '',
        category: form.category || '',
        price: Number(form.price) || 0,
        quantity: Number(form.quantity) || 0,
      }

      const formData = new FormData()
      formData.append('product', new Blob([JSON.stringify(productData)], { type: 'application/json' }))
      if (imageFile) {
        formData.append('imageFile', imageFile)
      }

      // Use different endpoint based on user role
      const endpoint = user?.role === 'WHOLESALER' 
        ? `${apiBase}/wholesaler/products`
        : `${apiBase}/products`

      const res = await fetch(endpoint, {
        method: 'POST',
        body: formData,
      })

      if (res.status === 201) {
        // CREATED - success
        setForm({ name: '', description: '', category: '', price: '', quantity: '' })
        setImageFile(null)
        setImagePreview(null)
        // Redirect based on user role
        if (user?.role === 'RETAILER') {
          navigate('/retailer/dashboard')
        } else if (user?.role === 'WHOLESALER') {
          navigate('/wholesaler/dashboard')
        } else {
          navigate('/')
        }
        return
      }
      if (res.status === 500) {
        setError('Server error. Please try again later.')
        return
      }
      if (!res.ok) {
        throw new Error(`Create failed (${res.status})`)
      }
    } catch (err) {
      if (err.message.includes('Failed to fetch')) {
        setError('Network error. Please check your connection.')
      } else {
        setError(err.message || 'Failed to create product')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <div className="panel">
        <div className="panel-header">
          <h2>Add New Product</h2>
          <div className="header-actions">
            <Link 
              className="btn ghost" 
              to={user?.role === 'WHOLESALER' ? '/wholesaler/dashboard' : '/retailer/dashboard'}
            >
              Back to Dashboard
            </Link>
          </div>
        </div>
        {error && <div className="error">{error}</div>}
        <div className="form">
          <div className="row">
            <label>Name</label>
            <input name="name" value={form.name} onChange={handleChange} placeholder="Product name" />
          </div>
          <div className="row">
            <label>Description</label>
            <textarea name="description" value={form.description} onChange={handleChange} placeholder="Product description" rows="3" />
          </div>
          <div className="row">
            <label>Category</label>
            <input name="category" value={form.category} onChange={handleChange} placeholder="Product category" />
          </div>
          <div className="row">
            <label>Price</label>
            <input name="price" type="number" step="0.01" value={form.price} onChange={handleChange} placeholder="0.00" />
          </div>
          <div className="row">
            <label>Quantity</label>
            <input name="quantity" type="number" value={form.quantity} onChange={handleChange} placeholder="0" />
          </div>
          <div className="row">
            <label>Product Image (Optional)</label>
            <input 
              type="file" 
              accept="image/*" 
              onChange={handleImageChange}
              className="file-input"
            />
            {imagePreview && (
              <div className="image-preview-container">
                <img src={imagePreview} alt="Preview" className="image-preview" />
              </div>
            )}
          </div>
          <div className="actions">
            <button className="btn primary" onClick={createProduct} disabled={loading || !form.name}>
              {loading ? 'Saving…' : 'Create'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function ProductDetailPage() {
  const { id } = useParams()
  const [product, setProduct] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [isEditing, setIsEditing] = useState(false)
  const [form, setForm] = useState({ name: '', description: '', category: '', price: '', quantity: '' })
  const [imageFile, setImageFile] = useState(null)
  const [imagePreview, setImagePreview] = useState(null)
  const apiBase = useMemo(() => '/api', [])
  const navigate = useNavigate()

  const getImageUrl = (productId) => {
    return `${apiBase}/products/${productId}/image`
  }

  const loadProduct = async () => {
    if (!id) return
    setLoading(true)
    setError('')
    try {
      const res = await fetch(`${apiBase}/products/${id}`)
      if (res.status === 404) {
        setError('Product not found')
        return
      }
      if (res.status === 500) {
        setError('Server error. Please try again later.')
        return
      }
      if (!res.ok) {
        throw new Error(`Failed to load product (${res.status})`)
      }
      const data = await res.json()
      setProduct(data)
      setForm({
        name: data.name || '',
        description: data.description || '',
        category: data.category || '',
        price: data.price || '',
        quantity: data.quantity || ''
      })
    } catch (e) {
      if (e.message.includes('Failed to fetch')) {
        setError('Network error. Please check your connection.')
      } else {
        setError(e.message || 'Failed to load product')
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadProduct()
  }, [id, apiBase])

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const handleImageChange = (e) => {
    const file = e.target.files[0]
    if (file) {
      setImageFile(file)
      const reader = new FileReader()
      reader.onloadend = () => {
        setImagePreview(reader.result)
      }
      reader.readAsDataURL(file)
    }
  }

  const handleEdit = () => {
    setIsEditing(true)
    setImageFile(null)
    setImagePreview(null)
  }

  const handleCancel = () => {
    setIsEditing(false)
    setImageFile(null)
    setImagePreview(null)
    if (product) {
      setForm({
        name: product.name || '',
        description: product.description || '',
        category: product.category || '',
        price: product.price || '',
        quantity: product.quantity || ''
      })
    }
  }

  const handleSave = async () => {
    if (!form.name) {
      setError('Product name is required')
      return
    }
    setLoading(true)
    setError('')
    try {
      const productData = {
        name: form.name,
        description: form.description || '',
        category: form.category || '',
        price: Number(form.price) || 0,
        quantity: Number(form.quantity) || 0,
      }

      const formData = new FormData()
      formData.append('product', new Blob([JSON.stringify(productData)], { type: 'application/json' }))
      if (imageFile) {
        formData.append('imageFile', imageFile)
      }

      const res = await fetch(`${apiBase}/products/update/${id}`, {
        method: 'PUT',
        body: formData,
      })

      if (res.status === 200) {
        setIsEditing(false)
        setImageFile(null)
        setImagePreview(null)
        await loadProduct()
        return
      }
      if (res.status === 404) {
        setError('Product not found')
        return
      }
      if (res.status === 500) {
        setError('Server error. Please try again later.')
        return
      }
      if (!res.ok) {
        throw new Error(`Update failed (${res.status})`)
      }
    } catch (err) {
      if (err.message.includes('Failed to fetch')) {
        setError('Network error. Please check your connection.')
      } else {
        setError(err.message || 'Failed to update product')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async () => {
    if (!window.confirm(`Are you sure you want to delete "${product.name}"? This action cannot be undone.`)) {
      return
    }
    
    setLoading(true)
    setError('')
    try {
      const res = await fetch(`${apiBase}/products/delete/${id}`, {
        method: 'DELETE',
      })

      if (res.status === 200) {
        // Success - redirect to dashboard
        navigate('/retailer/dashboard')
        return
      }
      if (res.status === 404) {
        setError('Product not found')
        return
      }
      if (res.status === 500) {
        setError('Server error. Please try again later.')
        return
      }
      if (!res.ok) {
        throw new Error(`Delete failed (${res.status})`)
      }
    } catch (err) {
      if (err.message.includes('Failed to fetch')) {
        setError('Network error. Please check your connection.')
      } else {
        setError(err.message || 'Failed to delete product')
      }
    } finally {
      setLoading(false)
    }
  }

  if (loading && !product) {
    return (
      <div className="page">
        <div className="panel">
          <div className="loading">Loading product details…</div>
        </div>
      </div>
    )
  }

  if (error && !product) {
    return (
      <div className="page">
        <div className="panel">
          <div className="panel-header">
            <h2>Product Details</h2>
            <div className="header-actions">
              <Link className="btn ghost" to="/retailer/dashboard">Back to Dashboard</Link>
            </div>
          </div>
          {error && <div className="error">{error}</div>}
        </div>
      </div>
    )
  }

  if (!product) {
    return null
  }

  if (isEditing) {
    return (
      <div className="page">
        <div className="panel">
          <div className="panel-header">
            <h2>Edit Product</h2>
            <div className="header-actions">
              <Link className="btn ghost" to="/retailer/dashboard">Back to Dashboard</Link>
            </div>
          </div>
          {error && <div className="error">{error}</div>}
          <div className="form">
            <div className="row">
              <label>Name</label>
              <input name="name" value={form.name} onChange={handleChange} placeholder="Product name" />
            </div>
            <div className="row">
              <label>Description</label>
              <textarea name="description" value={form.description} onChange={handleChange} placeholder="Product description" rows="3" />
            </div>
            <div className="row">
              <label>Category</label>
              <input name="category" value={form.category} onChange={handleChange} placeholder="Product category" />
            </div>
            <div className="row">
              <label>Price</label>
              <input name="price" type="number" step="0.01" value={form.price} onChange={handleChange} placeholder="0.00" />
            </div>
            <div className="row">
              <label>Quantity</label>
              <input name="quantity" type="number" value={form.quantity} onChange={handleChange} placeholder="0" />
            </div>
            <div className="row">
              <label>Product Image (Optional - leave empty to keep current image)</label>
              <input 
                type="file" 
                accept="image/*" 
                onChange={handleImageChange}
                className="file-input"
              />
              {imagePreview && (
                <div className="image-preview-container">
                  <img src={imagePreview} alt="Preview" className="image-preview" />
                </div>
              )}
              {!imagePreview && (product.imageName || product.imageType) && (
                <div className="image-preview-container">
                  <img src={getImageUrl(product.id)} alt="Current" className="image-preview" />
                  <p style={{ color: 'rgba(255, 255, 255, 0.6)', fontSize: '0.85rem', marginTop: '0.5rem' }}>Current image</p>
                </div>
              )}
            </div>
            <div className="actions">
              <button className="btn primary" onClick={handleSave} disabled={loading || !form.name}>
                {loading ? 'Saving…' : 'Save Changes'}
              </button>
              <button className="btn ghost" onClick={handleCancel} disabled={loading}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="page">
      <div className="panel">
        <div className="panel-header">
          <h2>Product Details</h2>
          <div className="header-actions">
            <button className="btn primary" onClick={handleEdit} disabled={loading}>Edit Product</button>
            <button className="btn danger" onClick={handleDelete} disabled={loading}>
              {loading ? 'Deleting…' : 'Delete Product'}
            </button>
            <Link className="btn ghost" to="/retailer/dashboard">Back to Dashboard</Link>
          </div>
        </div>
        {error && <div className="error">{error}</div>}
        <div className="product-detail">
          {(product.imageName || product.imageType) && (
            <div className="product-detail-image-container">
              <img 
                src={getImageUrl(product.id)} 
                alt={product.name}
                className="product-detail-image"
                onError={(e) => {
                  e.target.parentElement.style.display = 'none'
                }}
              />
            </div>
          )}
          <div className="product-detail-info">
            <div className="detail-row">
              <span className="detail-label">Product ID:</span>
              <span className="detail-value">{product.id}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Name:</span>
              <span className="detail-value">{product.name}</span>
            </div>
            {product.description && (
              <div className="detail-row">
                <span className="detail-label">Description:</span>
                <span className="detail-value">{product.description}</span>
              </div>
            )}
            {product.category && (
              <div className="detail-row">
                <span className="detail-label">Category:</span>
                <span className="detail-value">{product.category}</span>
              </div>
            )}
            <div className="detail-row">
              <span className="detail-label">Price:</span>
              <span className="detail-value price">₹{product.price}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Quantity:</span>
              <span className="detail-value">{product.quantity}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function LowStockPage() {
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [user, setUser] = useState(null)
  const apiBase = useMemo(() => '/api', [])
  const navigate = useNavigate()

  useEffect(() => {
    // Check if user is logged in and is retailer
    const userData = localStorage.getItem('user')
    if (!userData) {
      navigate('/login')
      return
    }
    try {
      const userObj = JSON.parse(userData)
      if (userObj.role !== 'RETAILER') {
        navigate('/login')
        return
      }
      setUser(userObj)
    } catch (e) {
      navigate('/login')
    }
  }, [navigate])

  const loadProducts = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const res = await fetch(`${apiBase}/products`)
      if (res.status === 204) {
        setProducts([])
        return
      }
      if (res.status === 500) {
        setError('Server error. Please try again later.')
        return
      }
      if (!res.ok) {
        throw new Error(`Failed to load products (${res.status})`)
      }
      const data = await res.json()
      setProducts(data)
    } catch (e) {
      if (e.message.includes('Failed to fetch')) {
        setError('Network error. Please check your connection.')
      } else {
        setError(e.message || 'Failed to load products')
      }
    } finally {
      setLoading(false)
    }
  }, [apiBase])

  useEffect(() => {
    if (user) {
      loadProducts()
    }
  }, [user, loadProducts])

  const getImageUrl = (productId) => {
    return `${apiBase}/products/${productId}/image`
  }

  const lowStockProducts = products.filter(p => (p.quantity || 0) < 10)

  if (!user) {
    return <div className="page"><div className="loading">Loading...</div></div>
  }

  return (
    <div className="page">
      <div className="panel">
        <div className="panel-header">
          <h2>Low on Stock {loading && <span className="muted">(loading…)</span>}</h2>
          <div className="header-actions">
            <Link className="btn ghost" to="/retailer/dashboard">Back to Dashboard</Link>
            <button className="btn ghost" onClick={loadProducts}>Refresh</button>
          </div>
        </div>
        {error && <div className="error">{error}</div>}
        
        {loading && (
          <div className="loading">Loading products…</div>
        )}
        {!loading && lowStockProducts.length === 0 && (
          <div className="empty">Great! No products are low on stock. All products have sufficient quantity (≥10 units).</div>
        )}
        {!loading && lowStockProducts.length > 0 && (
          <div>
            <p style={{ marginBottom: '1rem', color: '#666' }}>
              {lowStockProducts.length} product{lowStockProducts.length !== 1 ? 's' : ''} with less than 10 units in stock
            </p>
            <div className="products-grid">
              {lowStockProducts.map((p) => (
                <Link to={`/product/${p.id}`} key={p.id} className="product-card-link">
                  <div className="product-card">
                    {(p.imageName || p.imageType) && (
                      <div className="product-image-container">
                        <img 
                          src={getImageUrl(p.id)} 
                          alt={p.name}
                          className="product-image"
                          onError={(e) => {
                            e.target.parentElement.style.display = 'none'
                          }}
                        />
                      </div>
                    )}
                    <div className="product-id">ID: {p.id}</div>
                    <div className="product-name">{p.name}</div>
                    <div className="product-price">₹{p.price}</div>
                    <div className="product-quantity" style={{ color: p.quantity < 10 ? '#dc2626' : 'inherit', fontWeight: 'bold' }}>
                      Qty: {p.quantity} ⚠️
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function EditPage() {
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [form, setForm] = useState({ id: '', name: '', price: '', quantity: '' })
  const [user, setUser] = useState(null)
  const apiBase = useMemo(() => '/api', [])
  const navigate = useNavigate()

  useEffect(() => {
    // Check if user is logged in and is retailer/wholesaler
    const userData = localStorage.getItem('user')
    if (!userData) {
      navigate('/login')
      return
    }
    try {
      const userObj = JSON.parse(userData)
      setUser(userObj)
      // Only retailers and wholesalers can edit products
      if (userObj.role !== 'RETAILER' && userObj.role !== 'WHOLESALER') {
        navigate('/products')
      }
    } catch (e) {
      navigate('/login')
    }
  }, [navigate])

  const fetchProducts = async () => {
    setLoading(true)
    setError('')
    try {
      // Use different endpoint based on user role
      const endpoint = user?.role === 'WHOLESALER' 
        ? `${apiBase}/wholesaler/products`
        : `${apiBase}/products`
      
      const res = await fetch(endpoint)
      if (res.status === 204) {
        // NO_CONTENT - no products found
        setProducts([])
        return
      }
      if (res.status === 500) {
        setError('Server error. Please try again later.')
        return
      }
      if (!res.ok) {
        throw new Error(`Failed to load products (${res.status})`)
      }
      const data = await res.json()
      setProducts(data)
    } catch (err) {
      if (err.message.includes('Failed to fetch')) {
        setError('Network error. Please check your connection.')
      } else {
        setError(err.message || 'Failed to load products')
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { 
    if (user) {
      fetchProducts()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, apiBase])

  const saveChanges = async () => {
    if (!form.id) { setError('Product ID is required'); return }
    setLoading(true)
    setError('')
    try {
      const body = {
        id: String(form.id),
        name: form.name,
        price: Number(form.price) || 0,
        quantity: Number(form.quantity) || 0,
      }
      // Use different endpoint based on user role
      const updateEndpoint = user?.role === 'WHOLESALER' 
        ? `${apiBase}/wholesaler/products/update/${form.id}`
        : `${apiBase}/products/update/${form.id}`
      
      const res = await fetch(updateEndpoint, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      
      if (res.status === 200) {
        // OK - success
        setForm({ id: '', name: '', price: '', quantity: '' })
        await fetchProducts()
        return
      }
      if (res.status === 404) {
        setError('Product not found')
        return
      }
      if (res.status === 500) {
        setError('Server error. Please try again later.')
        return
      }
      if (!res.ok) {
        throw new Error(`Update failed (${res.status})`)
      }
    } catch (err) {
      if (err.message.includes('Failed to fetch')) {
        setError('Network error. Please check your connection.')
      } else {
        setError(err.message || 'Failed to update product')
      }
    } finally {
      setLoading(false)
    }
  }

  const deleteProduct = async (id) => {
    if (!id) return
    if (!window.confirm('Are you sure you want to delete this product?')) return
    
    setLoading(true)
    setError('')
    try {
      // Use different endpoint based on user role
      const deleteEndpoint = user?.role === 'WHOLESALER' 
        ? `${apiBase}/wholesaler/products/delete/${id}`
        : `${apiBase}/products/delete/${id}`
      
      const res = await fetch(deleteEndpoint, { method: 'DELETE' })
      
      if (res.status === 200) {
        // OK - success
        await fetchProducts()
        return
      }
      if (res.status === 404) {
        setError('Product not found')
        return
      }
      if (res.status === 500) {
        setError('Server error. Please try again later.')
        return
      }
      if (!res.ok) {
        throw new Error(`Delete failed (${res.status})`)
      }
    } catch (err) {
      if (err.message.includes('Failed to fetch')) {
        setError('Network error. Please check your connection.')
      } else {
        setError(err.message || 'Failed to delete product')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page">
      <div className="panel">
        <div className="panel-header">
          <h2>Edit Stock</h2>
          <div className="header-actions">
            <Link className="btn ghost" to="/retailer/dashboard">Back to Dashboard</Link>
          </div>
        </div>
        {error && <div className="error">{error}</div>}
        <div className="grid">
          <div className="panel">
            <h3>Enter Product ID and New Details</h3>
            <div className="form">
              <div className="row">
                <label>Product ID</label>
                <input name="id" type="number" value={form.id} onChange={(e) => setForm({ ...form, id: e.target.value })} placeholder="e.g. 3" />
              </div>
              <div className="row">
                <label>New Name</label>
                <input name="name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Product name" />
              </div>
              <div className="row">
                <label>New Price</label>
                <input name="price" type="number" step="0.01" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} placeholder="0.00" />
              </div>
              <div className="row">
                <label>New Quantity</label>
                <input name="quantity" type="number" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} placeholder="0" />
              </div>
              <div className="actions">
                <button className="btn primary" onClick={saveChanges} disabled={loading || !form.id}>Save Changes</button>
              </div>
            </div>
          </div>
          <div className="panel">
            <h3>All Products {loading && <span className="muted">(loading…)</span>}</h3>
            <div className="table">
              <div className="thead">
                <div>ID</div>
                <div>Name</div>
                <div>Price</div>
                <div>Qty</div>
                <div>Actions</div>
              </div>
              <div className="tbody">
                {products.length === 0 && (
                  <div className="empty">No products found</div>
                )}
                {products.map((p) => (
                  <div className="tr" key={p.id}>
                    <div>{p.id}</div>
                    <div>{p.name}</div>
                    <div>{p.price}</div>
                    <div>{p.quantity}</div>
                    <div className="row-actions">
                      <button className="btn small danger" onClick={() => deleteProduct(p.id)} disabled={loading}>Delete</button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/products" element={<ProductsPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/retailer/dashboard" element={<RetailerDashboard />} />
        <Route path="/retailer/low-stock" element={<LowStockPage />} />
        <Route path="/wholesaler/dashboard" element={<WholesalerDashboard />} />
        <Route path="/create" element={<CreatePage />} />
        <Route path="/edit" element={<EditPage />} />
        <Route path="/product/:id" element={<ProductDetailPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App


