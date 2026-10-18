import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import SocialLogin from '../components/SocialLogin'
import { saveSession, peekAuthNotice, clearAuthNotice } from '../api'
import './LoginPage.css'

const API_BASE = '/api'

export default function LoginPage() {
  const [mode, setMode] = useState('login') // 'login', 'register', 'otp'
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(() => peekAuthNotice())
  const [success, setSuccess] = useState('')
  const navigate = useNavigate()

  // the expiry notice is shown once, then forgotten
  useEffect(() => { clearAuthNotice() }, [])

  // Login form state
  const [loginForm, setLoginForm] = useState({ email: '', password: '' })

  // Register form state
  const [registerForm, setRegisterForm] = useState({
    email: '',
    password: '',
    role: 'CUSTOMER',
    location: '',
    customerDetails: null,
    retailerDetails: null,
    wholesalerDetails: null
  })

  // OTP form state
  const [otpForm, setOtpForm] = useState({ email: '', code: '' })
  const [otpSent, setOtpSent] = useState(false)

  // Handle login
  const handleLogin = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    setSuccess('')

    try {
      const res = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: loginForm.email,
          password: loginForm.password
        })
      })

      if (!res.ok) {
        let errorMessage = 'Login failed'
        try {
          const errorText = await res.text()
          // Try to parse as JSON first
          try {
            const errorJson = JSON.parse(errorText)
            errorMessage = errorJson.error || errorJson.message || errorText
          } catch {
            // If not JSON, use the text directly
            errorMessage = errorText || 'Invalid email or password'
          }
        } catch {
          errorMessage = `Login failed (Status: ${res.status})`
        }
        throw new Error(errorMessage)
      }

      const user = await res.json()
      // Store user info in localStorage
      saveSession(user)

      setSuccess('Login successful! Redirecting...')
      // Redirect retailers to retailer dashboard, others to products page
      if (user.role === 'RETAILER') {
        setTimeout(() => navigate('/retailer/dashboard'), 1000)
      } else {
        setTimeout(() => navigate('/products'), 1000)
      }
    } catch (err) {
      setError(err.message || 'Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  // Handle registration
  const handleRegister = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    setSuccess('')

    try {
      const registerData = {
        email: registerForm.email,
        password: registerForm.password,
        role: registerForm.role,
        location: registerForm.location || '',
        customerDetails: registerForm.role === 'CUSTOMER' ? {
          name: registerForm.customerName || '',
          phone: registerForm.customerPhone || '',
          address: registerForm.customerAddress || ''
        } : null,
        retailerDetails: registerForm.role === 'RETAILER' ? {
          name: registerForm.retailerName || '',
          shopName: registerForm.shopName || '',
          location: registerForm.location || ''
        } : null,
        wholesalerDetails: registerForm.role === 'WHOLESALER' ? {
          name: registerForm.wholesalerName || '',
          businessId: registerForm.businessId || '',
          contact: registerForm.contact || ''
        } : null
      }

      const res = await fetch(`${API_BASE}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(registerData)
      })

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({ error: 'Registration failed' }))
        throw new Error(errorData.error || 'Registration failed')
      }

      const user = await res.json()
      // Auto-login after registration for better UX
      saveSession(user)
      setSuccess('Registration successful! Redirecting...')
      // Redirect based on role
      if (user.role === 'RETAILER') {
        setTimeout(() => navigate('/retailer/dashboard'), 1500)
      } else {
        setTimeout(() => navigate('/products'), 1500)
      }
    } catch (err) {
      setError(err.message || 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  // Send OTP
  const handleSendOtp = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    setSuccess('')

    try {
      const params = new URLSearchParams({ email: otpForm.email })
      const res = await fetch(`${API_BASE}/auth/send-otp?${params}`, {
        method: 'POST'
      })

      const responseText = await res.text()

      if (!res.ok) {
        // Try to parse error message
        try {
          const errorData = JSON.parse(responseText)
          throw new Error(errorData.error || 'Failed to send OTP')
        } catch {
          throw new Error(responseText || `Failed to send OTP (Status: ${res.status})`)
        }
      }

      setSuccess('Verification code sent! Check your email inbox.')
      setOtpSent(true)
    } catch (err) {
      setError(err.message || 'Failed to send OTP. Please check your email and try again.')
    } finally {
      setLoading(false)
    }
  }

  // Verify OTP
  const handleVerifyOtp = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    setSuccess('')

    try {
      const params = new URLSearchParams({
        email: otpForm.email,
        code: otpForm.code
      })
      const res = await fetch(`${API_BASE}/auth/verify-otp?${params}`, {
        method: 'POST'
      })

      if (!res.ok) {
        const errorText = await res.text()
        throw new Error(errorText || 'Invalid or expired OTP')
      }

      const result = await res.json()

      // Check if user was logged in (existing user)
      if (result.id && result.email && result.role) {
        // User exists and is now logged in
        saveSession(result)

        setSuccess('Login successful! Redirecting...')

        // Redirect based on role
        if (result.role === 'RETAILER') {
          setTimeout(() => navigate('/retailer/dashboard'), 1000)
        } else {
          setTimeout(() => navigate('/products'), 1000)
        }
      } else if (result.verified && result.email) {
        // Email verified but user doesn't exist - switch to registration
        setSuccess('Email verified! Please complete your registration.')
        setRegisterForm({ ...registerForm, email: result.email })
        setTimeout(() => {
          setMode('register')
          setError('')
          setSuccess('')
        }, 1500)
      } else {
        setSuccess('OTP verified successfully!')
      }
    } catch (err) {
      setError(err.message || 'Invalid or expired OTP')
    } finally {
      setLoading(false)
    }
  }

  // Handle Google login success
  const handleGoogleSuccess = async (userInfo) => {
    setLoading(true)
    setError('')
    setSuccess('')

    try {
      // Try to login with Google using the backend's social login endpoint
      const loginRes = await fetch(`${API_BASE}/auth/social-login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          socialId: userInfo.email, // Use email as social ID for Google
          email: userInfo.email,
          name: userInfo.name,
          provider: 'GOOGLE'
        })
      })

      if (loginRes.status === 501) {
        const body = await loginRes.json().catch(() => ({}))
        throw new Error(body.error || 'Social login is not available')
      }

      if (loginRes.ok) {
        const user = await loginRes.json()
        saveSession(user)

        setSuccess('Google login successful! Redirecting...')
        if (user.role === 'RETAILER') {
          setTimeout(() => navigate('/retailer/dashboard'), 1000)
        } else {
          setTimeout(() => navigate('/products'), 1000)
        }
      } else {
        // If social login fails, try to register the user
        const registerRes = await fetch(`${API_BASE}/auth/register`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: userInfo.email,
            password: 'google-oauth-' + Date.now(), // Generate a random password
            role: 'CUSTOMER',
            location: '',
            customerDetails: {
              name: userInfo.name || '',
              phone: '',
              address: ''
            }
          })
        })

        if (registerRes.ok) {
          const user = await registerRes.json()
          saveSession(user)
          setSuccess('Account created with Google! Redirecting...')
          setTimeout(() => navigate('/products'), 1500)
        } else {
          throw new Error('Failed to create account with Google')
        }
      }
    } catch (err) {
      setError(err.message || 'Google login failed')
    } finally {
      setLoading(false)
    }
  }

  // Handle Facebook login success
  const handleFacebookSuccess = async (userInfo) => {
    setLoading(true)
    setError('')
    setSuccess('')

    try {
      // Try to login with Facebook using the backend's social login endpoint
      const loginRes = await fetch(`${API_BASE}/auth/social-login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          socialId: userInfo.id,
          email: userInfo.email,
          name: userInfo.name,
          provider: 'FACEBOOK'
        })
      })

      if (loginRes.status === 501) {
        const body = await loginRes.json().catch(() => ({}))
        throw new Error(body.error || 'Social login is not available')
      }

      if (loginRes.ok) {
        const user = await loginRes.json()
        saveSession(user)

        setSuccess('Facebook login successful! Redirecting...')
        if (user.role === 'RETAILER') {
          setTimeout(() => navigate('/retailer/dashboard'), 1000)
        } else {
          setTimeout(() => navigate('/products'), 1000)
        }
      } else {
        // If social login fails, try to register the user
        const registerRes = await fetch(`${API_BASE}/auth/register`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: userInfo.email,
            password: 'facebook-oauth-' + Date.now(), // Generate a random password
            role: 'CUSTOMER',
            location: '',
            customerDetails: {
              name: userInfo.name || '',
              phone: '',
              address: ''
            }
          })
        })

        if (registerRes.ok) {
          const user = await registerRes.json()
          saveSession(user)
          setSuccess('Account created with Facebook! Redirecting...')
          setTimeout(() => navigate('/products'), 1500)
        } else {
          throw new Error('Failed to create account with Facebook')
        }
      }
    } catch (err) {
      setError(err.message || 'Facebook login failed')
    } finally {
      setLoading(false)
    }
  }

  // Handle social login error
  const handleSocialError = (errorMessage) => {
    setError(errorMessage)
  }

  return (
    <div className="login-page">
      <Link className="home-link" to="/">← Home</Link>

      <div className="login-container">
        <div className="login-header">
          <h2>
            {mode === 'login' && 'Welcome Back'}
            {mode === 'register' && 'Create Account'}
            {mode === 'otp' && 'Verify Email'}
          </h2>
          <p className="subtitle">
            {mode === 'login' && 'Sign in to your account'}
            {mode === 'register' && 'Join our platform today'}
            {mode === 'otp' && 'Enter the verification code'}
          </p>
        </div>

        <div className="login-content">
          {error && <div className="error-message">{error}</div>}
          {success && <div className="success-message">{success}</div>}

          {/* Login Form */}
          {mode === 'login' && (
            <form onSubmit={handleLogin} className="login-form">
              <div className="form-group">
                <label className="form-label">Email Address</label>
                <input
                  type="email"
                  className="form-input"
                  value={loginForm.email}
                  onChange={(e) => setLoginForm({ ...loginForm, email: e.target.value })}
                  placeholder="Enter your email"
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label">Password</label>
                <input
                  type="password"
                  className="form-input"
                  value={loginForm.password}
                  onChange={(e) => setLoginForm({ ...loginForm, password: e.target.value })}
                  placeholder="Enter your password"
                  required
                />
              </div>

              <div className="login-actions">
                <button type="submit" className="btn-primary" disabled={loading}>
                  {loading ? 'Signing in...' : 'Sign In'}
                </button>

                <div className="mode-switcher">
                  <button type="button" className="btn-secondary" onClick={() => setMode('register')}>
                    Create Account
                  </button>
                  <button type="button" className="btn-ghost" onClick={() => setMode('otp')}>
                    Use OTP
                  </button>
                </div>
              </div>

              <SocialLogin
                onGoogleSuccess={handleGoogleSuccess}
                onFacebookSuccess={handleFacebookSuccess}
                onError={handleSocialError}
              />
            </form>
          )}

          {/* Registration Form */}
          {mode === 'register' && (
            <form onSubmit={handleRegister} className="login-form">
              <div className="form-group">
                <label className="form-label">Email Address</label>
                <input
                  type="email"
                  className="form-input"
                  value={registerForm.email}
                  onChange={(e) => setRegisterForm({ ...registerForm, email: e.target.value })}
                  placeholder="Enter your email"
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label">Password</label>
                <input
                  type="password"
                  className="form-input"
                  value={registerForm.password}
                  onChange={(e) => setRegisterForm({ ...registerForm, password: e.target.value })}
                  placeholder="Enter password (min 4 characters)"
                  required
                  minLength={4}
                />
              </div>
              <div className="form-group">
                <label className="form-label">Account Type</label>
                <select
                  className="form-select"
                  value={registerForm.role}
                  onChange={(e) => setRegisterForm({ ...registerForm, role: e.target.value })}
                  required
                >
                  <option value="CUSTOMER">Customer</option>
                  <option value="RETAILER">Retailer</option>
                  <option value="WHOLESALER">Wholesaler</option>
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Location (Optional)</label>
                <input
                  type="text"
                  className="form-input"
                  value={registerForm.location}
                  onChange={(e) => setRegisterForm({ ...registerForm, location: e.target.value })}
                  placeholder="Enter your location"
                />
              </div>

              {/* Customer-specific fields */}
              {registerForm.role === 'CUSTOMER' && (
                <>
                  <div className="form-group">
                    <label className="form-label">Full Name</label>
                    <input
                      type="text"
                      className="form-input"
                      value={registerForm.customerName || ''}
                      onChange={(e) => setRegisterForm({ ...registerForm, customerName: e.target.value })}
                      placeholder="Enter your full name"
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Phone Number</label>
                    <input
                      type="tel"
                      className="form-input"
                      value={registerForm.customerPhone || ''}
                      onChange={(e) => setRegisterForm({ ...registerForm, customerPhone: e.target.value })}
                      placeholder="Enter your phone number"
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Delivery Address</label>
                    <input
                      type="text"
                      className="form-input"
                      value={registerForm.customerAddress || ''}
                      onChange={(e) => setRegisterForm({ ...registerForm, customerAddress: e.target.value })}
                      placeholder="Enter your delivery address"
                    />
                  </div>
                </>
              )}

              {/* Retailer-specific fields */}
              {registerForm.role === 'RETAILER' && (
                <>
                  <div className="form-group">
                    <label className="form-label">Owner Name</label>
                    <input
                      type="text"
                      className="form-input"
                      value={registerForm.retailerName || ''}
                      onChange={(e) => setRegisterForm({ ...registerForm, retailerName: e.target.value })}
                      placeholder="Enter owner name"
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Shop Name</label>
                    <input
                      type="text"
                      className="form-input"
                      value={registerForm.shopName || ''}
                      onChange={(e) => setRegisterForm({ ...registerForm, shopName: e.target.value })}
                      placeholder="Enter your shop name"
                    />
                  </div>
                </>
              )}

              {/* Wholesaler-specific fields */}
              {registerForm.role === 'WHOLESALER' && (
                <>
                  <div className="form-group">
                    <label className="form-label">Business Owner</label>
                    <input
                      type="text"
                      className="form-input"
                      value={registerForm.wholesalerName || ''}
                      onChange={(e) => setRegisterForm({ ...registerForm, wholesalerName: e.target.value })}
                      placeholder="Enter owner name"
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Business Registration ID</label>
                    <input
                      type="text"
                      className="form-input"
                      value={registerForm.businessId || ''}
                      onChange={(e) => setRegisterForm({ ...registerForm, businessId: e.target.value })}
                      placeholder="Enter business registration number"
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Contact Information</label>
                    <input
                      type="text"
                      className="form-input"
                      value={registerForm.contact || ''}
                      onChange={(e) => setRegisterForm({ ...registerForm, contact: e.target.value })}
                      placeholder="Enter contact details"
                    />
                  </div>
                </>
              )}

              <div className="login-actions">
                <button type="submit" className="btn-primary" disabled={loading}>
                  {loading ? 'Creating Account...' : 'Create Account'}
                </button>
                <button type="button" className="btn-secondary" onClick={() => setMode('login')}>
                  Back to Sign In
                </button>
              </div>
            </form>
          )}

          {/* OTP Form */}
          {mode === 'otp' && (
            <form onSubmit={otpSent ? handleVerifyOtp : handleSendOtp} className="login-form">
              <div className="form-group">
                <label className="form-label">Email Address</label>
                <input
                  type="email"
                  className="form-input"
                  value={otpForm.email}
                  onChange={(e) => setOtpForm({ ...otpForm, email: e.target.value })}
                  placeholder="Enter your email"
                  required
                  disabled={otpSent}
                />
              </div>
              {otpSent && (
                <div className="form-group">
                  <label className="form-label">Verification Code</label>
                  <input
                    type="text"
                    className="form-input"
                    value={otpForm.code}
                    onChange={(e) => setOtpForm({ ...otpForm, code: e.target.value })}
                    placeholder="Enter 6-digit code"
                    required
                    maxLength={6}
                  />
                  <div className="otp-note">
                    Check your email inbox for the verification code
                  </div>
                </div>
              )}
              <div className="login-actions">
                <button type="submit" className="btn-primary" disabled={loading}>
                  {loading
                    ? otpSent
                      ? 'Verifying...'
                      : 'Sending Code...'
                    : otpSent
                      ? 'Verify Code'
                      : 'Send Code'}
                </button>

                <div className="mode-switcher">
                  {otpSent && (
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => {
                        setOtpSent(false)
                        setOtpForm({ ...otpForm, code: '' })
                      }}
                    >
                      Resend Code
                    </button>
                  )}
                  <button type="button" className="btn-ghost" onClick={() => setMode('login')}>
                    Back to Sign In
                  </button>
                </div>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}

