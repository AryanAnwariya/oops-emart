package com.emart.controller;

import com.emart.security.LoginAttemptService;
import com.emart.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emart.model.User;
import com.emart.model.SocialUser;
import com.emart.dto.RegisterRequest;
import com.emart.dto.LoginRequest;
import com.emart.dto.UserResponseDTO;
import com.emart.service.AuthService;
import com.emart.repository.UserRepo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// NOTE: Using @RestController and @RequestMapping is necessary for Spring to recognize this as an API controller
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;
    
    @Autowired
    private UserRepo userRepo;

    @Autowired
    private LoginAttemptService loginAttempts;

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private TokenService tokenService;

    // -------------------------------------------------------------------
    // I. REGISTRATION ENDPOINT (MULTI-ROLE)
    // -------------------------------------------------------------------

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            // Extract User data from request
            User user = new User();
            user.setEmail(request.getEmail());
            user.setPassword(request.getPassword());
            user.setRole(request.getRole());
            user.setLocation(request.getLocation());
            
            // Convert DTO objects to entities
            com.emart.model.Customer customerEntity = null;
            com.emart.model.Retailer retailerEntity = null;
            com.emart.model.Wholesaler wholesalerEntity = null;
            
            if (request.getCustomerDetails() != null) {
                customerEntity = new com.emart.model.Customer();
                customerEntity.setName(request.getCustomerDetails().getName());
                customerEntity.setPhone(request.getCustomerDetails().getPhone());
                customerEntity.setAddress(request.getCustomerDetails().getAddress());
            }
            
            if (request.getRetailerDetails() != null) {
                retailerEntity = new com.emart.model.Retailer();
                retailerEntity.setName(request.getRetailerDetails().getName());
                retailerEntity.setShopName(request.getRetailerDetails().getShopName());
                retailerEntity.setLocation(request.getRetailerDetails().getLocation());
            }
            
            if (request.getWholesalerDetails() != null) {
                wholesalerEntity = new com.emart.model.Wholesaler();
                wholesalerEntity.setName(request.getWholesalerDetails().getName());
                wholesalerEntity.setBusinessId(request.getWholesalerDetails().getBusinessId());
                wholesalerEntity.setContact(request.getWholesalerDetails().getContact());
            }
            
            // Call service with extracted data
            User registeredUser = authService.registerUser(
                user, 
                customerEntity, 
                retailerEntity, 
                wholesalerEntity
            );
            // Convert to DTO (exclude password)
            UserResponseDTO responseDTO = new UserResponseDTO(
                registeredUser.getId(),
                registeredUser.getEmail(),
                registeredUser.getRole(),
                registeredUser.getLocation()
            ).withToken(tokenService.issue(registeredUser));
            // HTTP 201 Created status for successful registration
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
        } catch (IllegalArgumentException e) {
            // Validation problems (bad e-mail/password/role, duplicate e-mail): the message is safe to show
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error while handling an authentication request", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Registration failed. Please try again later.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    // -------------------------------------------------------------------
    // II. PASSWORD LOGIN ENDPOINT (MULTI-ROLE)
    // -------------------------------------------------------------------

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            String attemptKey = AuthService.normalizeEmail(request.getEmail());
            if (attemptKey != null && loginAttempts.isBlocked(attemptKey)) {
                long seconds = Math.max(1, loginAttempts.retryAfter(attemptKey).toSeconds());
                Map<String, Object> locked = new HashMap<>();
                locked.put("error", "Too many failed sign-in attempts. Please try again in "
                        + ((seconds + 59) / 60) + " minute(s).");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", String.valueOf(seconds)).body(locked);
            }
            User authenticatedUser = authService.login(request.getEmail(), request.getPassword());
            if (attemptKey != null) {
                if (authenticatedUser == null) {
                    loginAttempts.recordFailure(attemptKey);
                } else {
                    loginAttempts.reset(attemptKey);
                }
            }

            if (authenticatedUser != null) {
                // Convert to DTO (exclude password for security)
                UserResponseDTO responseDTO = new UserResponseDTO(
                    authenticatedUser.getId(),
                    authenticatedUser.getEmail(),
                    authenticatedUser.getRole(),
                    authenticatedUser.getLocation()
                ).withToken(tokenService.issue(authenticatedUser));
                // SUCCESS: user profile plus a short-lived signed access token
                return ResponseEntity.ok(responseDTO); 
            } else {
                // UNAUTHORIZED status for invalid credentials
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid email or password.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
        } catch (Exception e) {
            log.error("Unexpected error while handling an authentication request", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Sign-in failed. Please try again later.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // -------------------------------------------------------------------
    // III. OTP FLOW ENDPOINTS
    // -------------------------------------------------------------------

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestParam String email) {
        try {
            authService.sendOtp(email);
            return ResponseEntity.ok("OTP sent successfully to " + email);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error while handling an authentication request", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Could not send the verification email. Please try again later.");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestParam String email, @RequestParam String code) {
        try {
            if (authService.verifyOtp(email, code)) {
                // Check if user exists (emails are stored lower-case)
                email = AuthService.normalizeEmail(email);
                Optional<User> existingUser = userRepo.findByEmail(email);
                
                if (existingUser.isPresent()) {
                    // User exists - auto-login
                    User user = existingUser.get();
                    UserResponseDTO responseDTO = new UserResponseDTO(
                        user.getId(),
                        user.getEmail(),
                        user.getRole(),
                        user.getLocation()
                    ).withToken(tokenService.issue(user));
                    return ResponseEntity.ok(responseDTO);
                } else {
                    // User doesn't exist - return verification success for registration
                    Map<String, Object> response = new HashMap<>();
                    response.put("verified", true);
                    response.put("email", email);
                    response.put("message", "Email verified. Please complete registration.");
                    return ResponseEntity.ok(response);
                }
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired OTP.");
            }
        } catch (Exception e) {
            log.error("Unexpected error while handling an authentication request", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Verification failed. Please try again later.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    // -------------------------------------------------------------------
    // IV. SOCIAL LOGIN ENDPOINT (OAUTH)
    // -------------------------------------------------------------------
    
    // SECURITY: this endpoint receives an email/name chosen by the client and never
    // checks a Google/Facebook token, so anyone could log in as any registered email.
    // It stays disabled until the server verifies the provider's ID token itself.
    private static final boolean SOCIAL_TOKEN_VERIFICATION_IMPLEMENTED = false;

    @PostMapping("/social-login")
    public ResponseEntity<?> socialLogin(@RequestBody SocialUser socialUser) {
        if (!SOCIAL_TOKEN_VERIFICATION_IMPLEMENTED) {
            Map<String, Object> disabled = new HashMap<>();
            disabled.put("error", "Social login is not available. Please sign in with email and password or OTP.");
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(disabled);
        }
        try {
            // NOTE: Frontend must have validated the social token and sent the structured profile data (SocialUser).
            User user = authService.socialLogin(socialUser);
            
            // Convert to DTO (exclude password for security)
            UserResponseDTO responseDTO = new UserResponseDTO(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getLocation()
            );
            
            // SUCCESS: Return the authenticated user profile.
            return ResponseEntity.ok(responseDTO);
        } catch (Exception e) {
            log.error("Unexpected error while handling an authentication request", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Social login is not available.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}