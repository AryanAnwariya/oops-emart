import { Link } from 'react-router-dom'
import { getUser } from '../api'
import '../App.css'

const DASHBOARD = { RETAILER: '/retailer/dashboard', WHOLESALER: '/wholesaler/dashboard' }

export default function LandingPage() {
  const user = getUser()
  const dashboard = user && DASHBOARD[user.role]

  return (
    <main className="page">
      <section className="landing-hero" aria-labelledby="hero-title">
        <h1 id="hero-title">One catalogue for local shops and their suppliers.</h1>
        <p>
          E-Mart lets customers browse what retailers have in stock, and gives retailers and wholesalers a simple
          way to keep their inventory up to date.
        </p>
        <div className="landing-actions">
          <Link className="btn primary large" to="/products">Browse products</Link>
          {dashboard ? (
            <Link className="btn large" to={dashboard}>Go to your dashboard</Link>
          ) : !user ? (
            <Link className="btn large" to="/login">Sign in or create an account</Link>
          ) : null}
        </div>
      </section>

      <section aria-label="Who E-Mart is for">
        <div className="role-grid">
          <article className="role-card">
            <h2>Customers</h2>
            <p>Search the retail catalogue and see prices and stock levels before you go.</p>
            <ul>
              <li>Search, filter by category and sort by price</li>
              <li>Clear in-stock and low-stock labels</li>
            </ul>
          </article>
          <article className="role-card">
            <h2>Retailers</h2>
            <p>Manage your shop's catalogue and know what to reorder.</p>
            <ul>
              <li>Add products with photos, edit prices and quantities</li>
              <li>Low-stock list for items under 10 units</li>
            </ul>
          </article>
          <article className="role-card">
            <h2>Wholesalers</h2>
            <p>List bulk stock in a separate wholesale catalogue.</p>
            <ul>
              <li>Your own dashboard and inventory table</li>
              <li>Only you can change the products you list</li>
            </ul>
          </article>
        </div>
      </section>
    </main>
  )
}
