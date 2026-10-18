// Social login is OFF unless explicitly enabled. A client ID being present is
// not enough: each provider needs its own VITE_ENABLE_*_LOGIN=true flag, set only
// after the provider's console lists this site's origin.
// These are build-time values (Vite inlines them) and only hold public IDs.
const isTrue = (value) => String(value ?? '').trim().toLowerCase() === 'true'
const clean = (value) => String(value ?? '').trim()
const isPlaceholder = (value) => !value || value.startsWith('your-')

const googleClientId = clean(import.meta.env.VITE_GOOGLE_CLIENT_ID)
const facebookAppId = clean(import.meta.env.VITE_FACEBOOK_APP_ID)

export const googleEnabled =
  isTrue(import.meta.env.VITE_ENABLE_GOOGLE_LOGIN) && !isPlaceholder(googleClientId)
export const facebookEnabled =
  isTrue(import.meta.env.VITE_ENABLE_FACEBOOK_LOGIN) && !isPlaceholder(facebookAppId)

export { googleClientId, facebookAppId }
export const anySocialEnabled = googleEnabled || facebookEnabled
