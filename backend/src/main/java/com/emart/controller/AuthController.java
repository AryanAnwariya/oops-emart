package com.emart.controller;

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
            );
            // HTTP 201 Created status for successful registration
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
        } catch (RuntimeException e) {
            // Return 400 Bad Request for validation errors (e.g., "Email already in use.")
            e.printStackTrace(); // Log for debugging
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            e.printStackTrace(); // Log for debugging
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Registration failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    // -------------------------------------------------------------------
    // II. PASSWORD LOGIN ENDPOINT (MULTI-ROLE)
    // -------------------------------------------------------------------

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            User authenticatedUser = authService.login(request.getEmail(), request.getPassword());

            if (authenticatedUser != null) {
                // Convert to DTO (exclude password for security)
                UserResponseDTO responseDTO = new UserResponseDTO(
                    authenticatedUser.getId(),
                    authenticatedUser.getEmail(),
                    authenticatedUser.getRole(),
                    authenticatedUser.getLocation()
                );
                // SUCCESS: Return the user object (JWT generation should happen here)
                return ResponseEntity.ok(responseDTO); 
            } else {
                // UNAUTHORIZED status for invalid credentials
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Invalid email or password.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Login failed: " + e.getMessage());
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
        } catch (Exception e) {
            e.printStackTrace(); // Log the error for debugging
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to send OTP: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestParam String email, @RequestParam String code) {
        try {
            if (authService.verifyOtp(email, code)) {
                // Check if user exists
                Optional<User> existingUser = userRepo.findByEmail(email);
                
                if (existingUser.isPresent()) {
                    // User exists - auto-login
                    User user = existingUser.get();
                    UserResponseDTO responseDTO = new UserResponseDTO(
                        user.getId(),
                        user.getEmail(),
                        user.getRole(),
                        user.getLocation()
                    );
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
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("error", "OTP verification failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    // -------------------------------------------------------------------
    // IV. SOCIAL LOGIN ENDPOINT (OAUTH)
    // -------------------------------------------------------------------
    
    @PostMapping("/social-login")
    public ResponseEntity<?> socialLogin(@RequestBody SocialUser socialUser) {
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
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Social login failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}