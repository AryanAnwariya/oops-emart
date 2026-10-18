import { GoogleLogin } from '@react-oauth/google'
import FacebookLogin from 'react-facebook-login-lite'
import { googleEnabled, facebookEnabled, facebookAppId, anySocialEnabled } from '../config/oauth'

const SocialLogin = ({ onGoogleSuccess, onFacebookSuccess, onError }) => {
  const handleGoogleSuccess = (credentialResponse) => {
    try {
      // Decode the JWT token to get user info
      const token = credentialResponse.credential
      const base64Url = token.split('.')[1]
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
      const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
      }).join(''))
      
      const userInfo = JSON.parse(jsonPayload)
      onGoogleSuccess({
        email: userInfo.email,
        name: userInfo.name,
        picture: userInfo.picture,
        provider: 'google',
        token: token
      })
    } catch (error) {
      console.error('Error processing Google login:', error)
      onError('Failed to process Google login')
    }
  }

  const handleGoogleError = () => {
    onError('Google login failed')
  }

  const handleFacebookSuccess = (response) => {
    if (response.status === 'connected') {
      // Get user info from Facebook
      window.FB.api('/me', { fields: 'name,email,picture' }, (userInfo) => {
        onFacebookSuccess({
          email: userInfo.email,
          name: userInfo.name,
          picture: userInfo.picture?.data?.url,
          provider: 'facebook',
          token: response.authResponse.accessToken,
          id: userInfo.id
        })
      })
    } else {
      onError('Facebook login failed')
    }
  }

  const handleFacebookError = (error) => {
    console.error('Facebook login error:', error)
    onError('Facebook login failed')
  }

  // Nothing configured: render nothing (no divider, no dead buttons).
  if (!anySocialEnabled) return null

  return (
    <div className="social-login">
      <div className="social-divider">
        <span>Or continue with</span>
      </div>
      
      <div className="social-buttons">
        {googleEnabled && (
        <GoogleLogin
          onSuccess={handleGoogleSuccess}
          onError={handleGoogleError}
          theme="outline"
          size="large"
          text="continue_with"
          shape="rectangular"
          width="100%"
        />
        )}
        
        {facebookEnabled && (
        <FacebookLogin
          appId={facebookAppId}
          onSuccess={handleFacebookSuccess}
          onError={handleFacebookError}
          className="facebook-login-btn"
        >
          <div className="facebook-btn-content">
            <div className="facebook-icon-container">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="#1877f2">
                <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
              </svg>
            </div>
            Continue with Facebook
          </div>
        </FacebookLogin>
        )}
      </div>
    </div>
  )
}

export default SocialLogin