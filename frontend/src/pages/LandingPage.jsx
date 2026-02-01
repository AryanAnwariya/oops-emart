import { Link } from 'react-router-dom'
import '../App.css'

export default function LandingPage() {
  return (
    <div className="page" style={{ 
      minHeight: '100vh', 
      display: 'flex', 
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      color: '#fff'
    }}>
      <div style={{ 
        textAlign: 'center', 
        maxWidth: '800px', 
        padding: '2rem',
        background: 'rgba(255, 255, 255, 0.1)',
        borderRadius: '20px',
        backdropFilter: 'blur(10px)',
        boxShadow: '0 8px 32px rgba(0, 0, 0, 0.1)'
      }}>
        <h1 style={{ 
          fontSize: '4rem', 
          margin: '0 0 1rem 0',
          fontWeight: 'bold',
          textShadow: '2px 2px 4px rgba(0,0,0,0.2)'
        }}>
          eMart
        </h1>
        <p style={{ 
          fontSize: '1.5rem', 
          margin: '0 0 2rem 0',
          opacity: 0.95
        }}>
          Your One-Stop Shop for Everything
        </p>
        <p style={{ 
          fontSize: '1.1rem', 
          margin: '0 0 3rem 0',
          opacity: 0.9,
          lineHeight: '1.6'
        }}>
          Connect with retailers and wholesalers. Manage your inventory. 
          Shop smarter, faster, and better.
        </p>
        
        <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
          <Link 
            to="/login" 
            className="btn primary"
            style={{ 
              fontSize: '1.2rem', 
              padding: '1rem 2rem',
              minWidth: '200px'
            }}
          >
            Login
          </Link>
          <Link 
            to="/products" 
            className="btn"
            style={{ 
              fontSize: '1.2rem', 
              padding: '1rem 2rem',
              minWidth: '200px',
              background: 'rgba(255, 255, 255, 0.2)',
              color: '#fff',
              border: '2px solid rgba(255, 255, 255, 0.3)'
            }}
          >
            Browse Products
          </Link>
        </div>

        <div style={{ 
          marginTop: '3rem', 
          padding: '2rem',
          background: 'rgba(255, 255, 255, 0.05)',
          borderRadius: '10px',
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '2rem'
        }}>
          <div>
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.3rem' }}>👥 Customers</h3>
            <p style={{ margin: 0, opacity: 0.8, fontSize: '0.9rem' }}>
              Shop from verified retailers and wholesalers
            </p>
          </div>
          <div>
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.3rem' }}>🏪 Retailers</h3>
            <p style={{ margin: 0, opacity: 0.8, fontSize: '0.9rem' }}>
              Manage your shop inventory and sales
            </p>
          </div>
          <div>
            <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.3rem' }}>📦 Wholesalers</h3>
            <p style={{ margin: 0, opacity: 0.8, fontSize: '0.9rem' }}>
              Connect with retailers and expand your reach
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}

