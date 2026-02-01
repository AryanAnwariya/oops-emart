import { useEffect, useState, useCallback } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import '../App.css'

const API_BASE = '/api'

export default function WholesalerDashboard() {
  const [user, setUser] = useState(null)
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()

  useEffect(() => {
    // Check if user is logged in and is a wholesaler
    const userData = localStorage.getItem('user')
    if (!userData) {
      navigate('/login')
      return
    }

    try {
      const userObj = JSON.parse(userData)
      if (userObj.role !== 'WHOLESALER') {
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
      const res = await fetch(`${API_BASE}/wholesaler/products`)
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
  }, [])

  useEffect(() => {
    if (user) {
      loadProducts()
    }
  }, [user, loadProducts])

  const getImageUrl = (productId) => {
    return `${API_BASE}/wholesaler/products/${productId}/image`
  }

  const handleLogout = () => {
    localStorage.removeItem('user')
    localStorage.removeItem('email')
    localStorage.removeItem('role')
    navigate('/login')
  }

  if (!user) {
    return <div>Loading...</div>
  }

  return (
    <div className="page">
      <div style={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center', 
        marginBottom: '2rem',
        padding: '1rem',
        background: '#fff',
        borderRadius: '8px',
        boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
      }}>
        <div>
          <h1 style={{ margin: 0, color: '#333' }}>Wholesaler Dashboard</h1>
          <p style={{ margin: '0.5rem 0 0 0', color: '#666', fontSize: '0.9rem' }}>
            Welcome, {user.email} | Wholesale Management Portal
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <button 
            className="btn" 
            onClick={handleLogout}
            style={{
              backgroundColor: '#dc2626',
              color: 'white',
              borderColor: '#dc2626'
            }}
          >
            Logout
          </button>
        </div>
      </div>

      <div className="panel">
        <div className="panel-header">
          <h2>Your Products {loading && <span className="muted">(loading…)</span>}</h2>
          <div className="header-actions">
            <Link className="btn primary" to="/create">Add New Product</Link>
            <button className="btn ghost" onClick={loadProducts}>Refresh</button>
          </div>
        </div>
        {error && <div className="error">{error}</div>}
        
        {/* Product Grid View */}
        {loading && (
          <div className="loading">Loading products…</div>
        )}
        {!loading && products.length === 0 && (
          <div className="empty">No products found. Add your first product!</div>
        )}
        {!loading && products.length > 0 && (
          <div className="products-grid">
            {products.map((p) => (
              <Link to={`/wholesaler/product/${p.id}`} key={p.id} className="product-card-link">
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
        )}
      </div>
    </div>
  )
}

