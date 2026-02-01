# Email Configuration Guide

## Gmail Setup (Recommended)

1. **Enable 2-Factor Authentication** on your Gmail account
2. **Generate App Password**:
   - Go to [Google Account Settings](https://myaccount.google.com/)
   - Security → 2-Step Verification → App passwords
   - Select "Mail" and generate password
   - Copy the 16-digit password

3. **Update application.properties**:
```properties
spring.mail.username=your-email@gmail.com
spring.mail.password=your-16-digit-app-password
app.mail.from=your-email@gmail.com
```

## Alternative Email Providers

### Outlook/Hotmail
```properties
spring.mail.host=smtp-mail.outlook.com
spring.mail.port=587
spring.mail.username=your-email@outlook.com
spring.mail.password=your-password
```

### Yahoo Mail
```properties
spring.mail.host=smtp.mail.yahoo.com
spring.mail.port=587
spring.mail.username=your-email@yahoo.com
spring.mail.password=your-app-password
```

### Custom SMTP Server
```properties
spring.mail.host=your-smtp-server.com
spring.mail.port=587
spring.mail.username=your-username
spring.mail.password=your-password
```

## Testing Email Configuration

1. **Start your backend server**
2. **Test OTP endpoint**:
   ```bash
   curl -X POST "http://localhost:8080/api/auth/send-otp?email=test@example.com"
   ```
3. **Check your email inbox** for the OTP

## Troubleshooting

### Common Issues:
- **Authentication failed**: Check username/password
- **Connection timeout**: Check host/port settings
- **SSL errors**: Verify SSL/TLS settings
- **App password required**: Enable 2FA and use app password

### Fallback Behavior:
If email sending fails, the system will:
1. Log the error
2. Fall back to console output
3. Continue working normally

## Production Recommendations

For production use:
1. **Use environment variables** for sensitive data
2. **Consider email services** like SendGrid, Mailgun, or AWS SES
3. **Implement rate limiting** to prevent spam
4. **Add email templates** for better branding
5. **Monitor email delivery** rates and failures