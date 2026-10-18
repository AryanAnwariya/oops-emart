import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiFetch, FORBIDDEN_MESSAGE, getUser } from './api'

/** URL builders for the two catalogues. The paths are the existing, unchanged API routes. */
export function catalogue(scope) {
  const base = scope === 'wholesale' ? '/api/wholesaler/products' : '/api/products'
  return {
    list: base,
    mine: `${base}/mine`,
    one: (id) => `${base}/${id}`,
    image: (id) => `${base}/${id}/image`,
    create: base,
    update: (id) => `${base}/update/${id}`,
    remove: (id) => `${base}/delete/${id}`,
    detailRoute: (id) => (scope === 'wholesale' ? `/wholesaler/product/${id}` : `/product/${id}`),
    dashboardRoute: scope === 'wholesale' ? '/wholesaler/dashboard' : '/retailer/dashboard',
  }
}

export const scopeForRole = (role) => (role === 'WHOLESALER' ? 'wholesale' : 'retail')
export const roleForScope = (scope) => (scope === 'wholesale' ? 'WHOLESALER' : 'RETAILER')

/** Turns a failed response into a message for the user. 401 is handled globally by apiFetch (back to sign-in). */
export async function failureMessage(res, what) {
  if (res.status === 403) return FORBIDDEN_MESSAGE
  if (res.status === 404) return 'Product not found.'
  if (res.status === 400) {
    try {
      const body = await res.json()
      if (body && body.error) return body.error
    } catch {
      /* fall through */
    }
    return 'Please check the values you entered.'
  }
  if (res.status >= 500) return 'Server error. Please try again later.'
  return `${what} failed (${res.status}).`
}

export function networkMessage(e, fallback) {
  if (e && typeof e.message === 'string' && e.message.includes('Failed to fetch')) {
    return 'Network error. Please check your connection.'
  }
  return (e && e.message) || fallback
}

/**
 * Redirects to sign-in when there is no session, or to the catalogue when the role is not allowed.
 * Display-level only: the server enforces access independently.
 */
export function useRoleGuard(allowedRoles) {
  const navigate = useNavigate()
  const user = getUser()
  const allowed = Boolean(user) && allowedRoles.includes(user.role)
  useEffect(() => {
    if (!user) navigate('/login', { replace: true })
    else if (!allowed) navigate('/products', { replace: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return allowed ? user : null
}

/** Loads a product list (public or owner-scoped) and exposes loading/error state plus reload(). */
export function useProductList(url, { auth = false } = {}) {
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const res = auth ? await apiFetch(url) : await fetch(url)
      if (res.status === 204) {
        setProducts([])
        return
      }
      if (!res.ok) {
        setProducts([])
        setError(await failureMessage(res, 'Loading products'))
        return
      }
      setProducts(await res.json())
    } catch (e) {
      setProducts([])
      setError(networkMessage(e, 'Failed to load products.'))
    } finally {
      setLoading(false)
    }
  }, [url, auth])

  useEffect(() => {
    reload()
  }, [reload])

  return { products, loading, error, reload }
}
