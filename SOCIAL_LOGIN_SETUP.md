# Social Login Setup Guide

This guide will help you set up Google and Facebook social login for your e-commerce application.

## Prerequisites

1. A Google Cloud Console account
2. A Facebook Developer account
3. Your application running on a specific domain (localhost for development)

## Google OAuth Setup

### Step 1: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Google+ API (if not already enabled)

### Step 2: Configure OAuth Consent Screen

1. Navigate to "APIs & Services" > "OAuth consent screen"
2. Choose "External" user type (for testing with personal accounts)
3. Fill in the required information:
   - App name: Your app name
   - User support email: Your email
   - Developer contact information: Your email
4. Add scopes: `email`, `profile`, `openid`
5. Add test users (your email addresses for testing)

### Step 3: Create OAuth 2.0 Credentials

1. Go to "APIs & Services" > "Credentials"
2. Click "Create Credentials" > "OAuth 2.0 Client IDs"
3. Choose "Web application"
4. Add authorized origins:
   - `http://localhost:5173` (for Vite dev server)
   - `http://localhost:3000` (if using different port)
   - Your production domain
5. Add authorized redirect URIs:
   - `http://localhost:5173` (for Vite dev server)
   - Your production domain
6. Copy the Client ID

### Step 4: Update Environment Variables

1. Open `frontend/.env`
2. Replace `your-google-client-id-here` with your actual Google Client ID:
   ```
   VITE_GOOGLE_CLIENT_ID=your-actual-google-client-id
   ```

## Facebook OAuth Setup

### Step 1: Create a Facebook App

1. Go to [Facebook Developers](https://developers.facebook.com/)
2. Click "My Apps" > "Create App"
3. Choose "Consumer" app type
4. Fill in app details and create the app

### Step 2: Configure Facebook Login

1. In your app dashboard, click "Add Product"
2. Find "Facebook Login" and click "Set Up"
3. Choose "Web" platform
4. Add your site URL: `http://localhost:5173` (for development)

### Step 3: Configure App Settings

1. Go to "Settings" > "Basic"
2. Add your app domains: `localhost` (for development)
3. Copy the App ID

### Step 4: Configure Valid OAuth Redirect URIs

1. Go to "Facebook Login" > "Settings"
2. Add Valid OAuth Redirect URIs:
   - `http://localhost:5173/` (for development)
   - Your production domain

### Step 5: Update Environment Variables

1. Open `frontend/.env`
2. Replace `your-facebook-app-id-here` with your actual Facebook App ID:
   ```
   VITE_FACEBOOK_APP_ID=your-actual-facebook-app-id
   ```

## Testing the Setup

1. Start your backend server
2. Start your frontend development server: `npm run dev`
3. Navigate to the login page
4. Try logging in with Google and Facebook buttons

## Troubleshooting

### Google Login Issues

- **"redirect_uri_mismatch"**: Make sure your redirect URI in Google Console matches your app URL
- **"access_blocked"**: Add your email to test users in OAuth consent screen
- **"popup_closed_by_user"**: User closed the popup, this is normal behavior

### Facebook Login Issues

- **"Can't Load URL"**: Check that your app domain is correctly configured
- **"App Not Setup"**: Make sure Facebook Login product is added and configured
- **"Invalid Redirect URI"**: Verify the redirect URIs in Facebook Login settings

### General Issues

- **CORS errors**: Make sure your backend allows requests from your frontend domain
- **Network errors**: Check that your backend is running and accessible
- **Environment variables not loading**: Restart your development server after changing .env files

## Production Deployment

When deploying to production:

1. Update OAuth redirect URIs to use your production domain
2. Update environment variables with production values
3. Make sure your production domain is added to authorized origins/domains
4. For Facebook: Submit your app for review if you need access beyond test users

## Security Notes

- Never commit your actual OAuth credentials to version control
- Use environment variables for all sensitive configuration
- Regularly rotate your OAuth secrets
- Monitor your OAuth usage in the respective consoles