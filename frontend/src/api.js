// Session + authenticated fetch helper.
//
// The server issues a short-lived signed token at login/registration/OTP-login. It is sent as a Bearer
// header on protected requests. The role and user shown in the UI are only for display: every
// permission decision is made by the server from the verified token.

const TOKEN_KEY = 'token'
const NOTICE_KEY = 'authNotice'

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

/** Store the logged-in user (without the token) and the token separately. */
export function saveSession(userWithToken) {
  const { token, expiresIn, ...user } = userWithToken || {}
  localStorage.setItem('user', JSON.stringify(user))
  localStorage.setItem('email', user.email)
  localStorage.setItem('role', user.role || 'CUSTOMER')
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
  if (expiresIn) {
    localStorage.setItem('tokenExpiresAt', String(Date.now() + expiresIn * 1000))
  } else {
    localStorage.removeItem('tokenExpiresAt')
  }
}

export function clearSession() {
  localStorage.removeItem('user')
  localStorage.removeItem('email')
  localStorage.removeItem('role')
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem('tokenExpiresAt')
}

/** Message to show on the login page (e.g. after a session expired). Reading is side-effect free so React StrictMode's double render is safe. */
export function peekAuthNotice() {
  try {
    return sessionStorage.getItem(NOTICE_KEY) || ''
  } catch {
    return ''
  }
}

export function clearAuthNotice() {
  try {
    sessionStorage.removeItem(NOTICE_KEY)
  } catch {
    /* ignore */
  }
}

/**
 * fetch() that adds the Bearer token.
 *  - 401: the session is missing/expired/invalid -> clear it and send the user to the login page.
 *  - 403: returned to the caller, which shows a "not allowed" message (the user is signed in but lacks permission).
 */
export async function apiFetch(url, options = {}) {
  const headers = new Headers(options.headers || {})
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(url, { ...options, headers })

  if (res.status === 401) {
    clearSession()
    try {
      sessionStorage.setItem(NOTICE_KEY, 'Your session has expired or is invalid. Please sign in again.')
    } catch {
      /* ignore */
    }
    if (window.location.pathname !== '/login') {
      window.location.assign('/login')
    }
  }
  return res
}

export const FORBIDDEN_MESSAGE = 'You do not have permission to change this item.'
