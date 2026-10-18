import { useEffect, useState, useCallback } from 'react'
import { apiFetch, clearSession } from '../api'
import { useNavigate, Link } from 'react-router-dom'
import '../App.css'

const API_BASE = '/api'

export default function RetailerDashboard() {
  const [user, setUser] = useState(null)
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()

  useEffect(() => {
    // Check if user is logged in and is a retailer
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
      const res = await apiFetch(`${API_BASE}/products/mine`)
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
    } catch (e) {
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
    return `${API_BASE}/products/${productId}/image`
  }

  const handleLogout = () => {
    clearSession()
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
          <h1 style={{ margin: 0, color: '#333' }}>Retailer Dashboard</h1>
          <p style={{ margin: '0.5rem 0 0 0', color: '#666', fontSize: '0.9rem' }}>
            Welcome, {user.email} | Shop Management Portal
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
            <Link 
              to="/retailer/low-stock" 
              className="btn" 
              style={{ 
                position: 'relative',
                backgroundColor: '#dc2626',
                color: 'white',
                borderColor: '#dc2626'
              }}
            >
              Refill Stock
              {products.filter(p => (p.quantity || 0) < 10).length > 0 && (
                <span style={{
                  position: 'absolute',
                  top: '-10px',
                  right: '-10px',
                  backgroundColor: '#000000',
                  color: 'white',
                  borderRadius: '50%',
                  width: '24px',
                  height: '24px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '11px',
                  fontWeight: 'bold',
                  boxShadow: '0 2px 4px rgba(0, 0, 0, 0.2)',
                  border: '2px solid white'
                }}>
                  {products.filter(p => (p.quantity || 0) < 10).length}
                </span>
              )}
            </Link>
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
        )}
      </div>
    </div>
  )
}

