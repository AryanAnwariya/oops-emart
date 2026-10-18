import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { clearSession, getUser } from '../api'
import './Navbar.css'

const ROLE_LABEL = { CUSTOMER: 'Customer', RETAILER: 'Retailer', WHOLESALER: 'Wholesaler' }

/** Links shown for each role. Display only: every action is re-checked by the server. */
function linksFor(role) {
  const base = [{ to: '/products', label: 'Products' }]
  if (role === 'RETAILER') {
    return [
      { to: '/retailer/dashboard', label: 'Dashboard' },
      ...base,
      { to: '/retailer/low-stock', label: 'Low stock' },
      { to: '/create', label: 'Add product' },
      { to: '/edit', label: 'Edit stock' },
    ]
  }
  if (role === 'WHOLESALER') {
    return [
      { to: '/wholesaler/dashboard', label: 'Dashboard' },
      ...base,
      { to: '/create', label: 'Add product' },
      { to: '/edit', label: 'Edit stock' },
    ]
  }
  return base
}

export default function Navbar() {
  const location = useLocation()
  const navigate = useNavigate()
  // The login page has its own full-screen layout and brand.
  if (location.pathname === '/login') return null

  const user = getUser()
  const links = linksFor(user?.role)

  const handleLogout = () => {
    clearSession()
    navigate('/login')
  }

  return (
    <header className="navbar">
      <div className="navbar-inner">
        <Link to="/" className="brand" aria-label="E-Mart home">
          <span className="brand-mark" aria-hidden="true">E</span>
          <span className="brand-name">E-Mart</span>
        </Link>

        <nav className="nav-links" aria-label="Main">
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              {l.label}
            </NavLink>
          ))}
        </nav>

        <div className="nav-user">
          {user ? (
            <>
              <div className="user-chip" title={user.email}>
                <span className="user-email">{user.email}</span>
                <span className="user-role">{ROLE_LABEL[user.role] || user.role}</span>
              </div>
              <button type="button" className="btn small" onClick={handleLogout}>Sign out</button>
            </>
          ) : (
            <Link className="btn small primary" to="/login">Sign in</Link>
          )}
        </div>
      </div>
    </header>
  )
}
