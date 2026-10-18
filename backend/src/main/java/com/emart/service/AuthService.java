package com.emart.service;

import com.emart.model.User;
import com.emart.model.OTP;
import com.emart.model.SocialUser;
import com.emart.model.Customer;
import com.emart.model.Retailer;
import com.emart.model.Wholesaler;

import com.emart.repository.UserRepo;
import com.emart.repository.OTPRepo;
import com.emart.repository.CustomerRepo;
import com.emart.repository.RetailerRepo;
import com.emart.repository.WholesalerRepo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; 

import java.time.LocalDateTime;
import java.util.Optional;
import java.security.SecureRandom;

@Service
public class AuthService {

    // --- Repositories Injected for Data Access ---
    @Autowired private UserRepo userRepo;
    @Autowired private OTPRepo otpRepo;
    @Autowired private EmailService emailService;
    @Autowired private CustomerRepo customerRepo; 
    @Autowired private RetailerRepo retailerRepo;
    @Autowired private WholesalerRepo wholesalerRepo;
    
    // --- Security Encoder Injected for Password Hashing ---
    @Autowired private BCryptPasswordEncoder passwordEncoder; 

    /** Trim and lower-case an email so register, login and OTP all agree. Returns null if blank. */
    private static final int MAX_OTP_ATTEMPTS = 5;

    /** Local-demo escape hatch only; see sendOtp. */
    @org.springframework.beans.factory.annotation.Value("${app.otp.log-codes:false}")
    private boolean logOtpCodes;

    public static String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    // -------------------------------------------------------------------
    // I. REGISTRATION AND PASSWORD LOGIN
    // -------------------------------------------------------------------

    @Transactional
    public User registerUser(User newUser, Customer customerDetails, Retailer retailerDetails, Wholesaler wholesalerDetails) {
        
        // 0. Validation: normalise email, require a password, allow only known roles
        String email = normalizeEmail(newUser.getEmail());
        if (email == null) {
            throw new RuntimeException("Email is required.");
        }
        if (newUser.getPassword() == null || newUser.getPassword().isEmpty()) {
            throw new RuntimeException("Password is required.");
        }
        String requestedRole = newUser.getRole() == null ? "" : newUser.getRole().trim().toUpperCase();
        if (!requestedRole.equals("CUSTOMER") && !requestedRole.equals("RETAILER") && !requestedRole.equals("WHOLESALER")) {
            throw new RuntimeException("Invalid role. Must be CUSTOMER, RETAILER or WHOLESALER.");
        }
        newUser.setEmail(email);
        newUser.setRole(requestedRole);

        // 1. Validation: Check for existing email
        if (userRepo.findByEmail(newUser.getEmail()).isPresent()) {
            throw new RuntimeException("Email already in use.");
        }
        
        // 2. SECURITY: Hash the password before saving
        String hashedPassword = passwordEncoder.encode(newUser.getPassword());
        newUser.setPassword(hashedPassword); 

        // 3. Save the base User
        User savedUser = userRepo.save(newUser);

        // 4. Multi-Role Profile Saving
        String role = savedUser.getRole();
        if ("CUSTOMER".equalsIgnoreCase(role) && customerDetails != null) {
            customerDetails.setUser(savedUser);
            customerRepo.save(customerDetails);
        } else if ("RETAILER".equalsIgnoreCase(role) && retailerDetails != null) {
            retailerDetails.setUser(savedUser);
            retailerRepo.save(retailerDetails);
        } else if ("WHOLESALER".equalsIgnoreCase(role) && wholesalerDetails != null) {
            wholesalerDetails.setUser(savedUser);
            wholesalerRepo.save(wholesalerDetails);
        }
        
        return savedUser;
    }
    
    public User login(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || rawPassword == null) {
            return null;
        }
        Optional<User> userOpt = userRepo.findByEmail(normalizedEmail);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // SECURITY: Compare the raw password with the hashed password from the DB
            if (passwordEncoder.matches(rawPassword, user.getPassword())) {
                return user; // Success: Credentials match
            }
        }
        return null; // Failure
    }


    // -------------------------------------------------------------------
    // II. OTP AUTHENTICATION METHODS
    // -------------------------------------------------------------------

    public void sendOtp(String email) {
        // Validate email
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        
        try {
            // 1. Generate a random 6-digit code
            SecureRandom random = new SecureRandom();
            int otpNumber = random.nextInt(900000) + 100000; // Ensures 6-digit number (100000-999999)
            String code = String.format("%06d", otpNumber);
            
            // 2. Create OTP entity with 5-minute expiration
            OTP otp = new OTP();
            otp.setEmail(email.trim().toLowerCase());
            otp.setCode(code);
            otp.setExpirationTime(LocalDateTime.now().plusMinutes(5));
            
            // 3. Save OTP to database
            otpRepo.save(otp);
            
            // 4. Send OTP via email. If delivery fails the code is useless to the user, so discard it and
            //    report the failure instead of claiming success. For local demos without SMTP only,
            //    OTP_LOG_CODES=true prints the code to the console (never enable in production).
            try {
                emailService.sendOtpEmail(email.trim().toLowerCase(), code);
            } catch (Exception emailError) {
                System.err.println("Failed to send OTP email: " + emailError.getMessage());
                if (logOtpCodes) {
                    System.out.println("DEBUG (OTP_LOG_CODES=true) OTP for " + email.trim().toLowerCase() + ": " + code);
                } else {
                    otpRepo.deleteByEmail(email.trim().toLowerCase());
                    throw new RuntimeException("Could not send the verification email. Please try again later.", emailError);
                }
            }
        } catch (Exception e) {
            System.err.println("Error generating OTP: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to generate OTP: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public boolean verifyOtp(String email, String code) {
        // 1. Retrieve the latest OTP (emails are stored lower-case by sendOtp)
        email = normalizeEmail(email);
        if (email == null || code == null) {
            return false;
        }
        Optional<OTP> otpOpt = otpRepo.findTopByEmailOrderByExpirationTimeDesc(email);

        if (otpOpt.isPresent()) {
            OTP otp = otpOpt.get();
            // 2. Check if code matches AND if it hasn't expired
            if (otp.getCode().equals(code) && otp.getExpirationTime().isAfter(LocalDateTime.now())) {
                // 3. Verification successful: Delete all related OTPs for security cleanup
                otpRepo.deleteByEmail(email); 
                return true;
            }
            // 4. Wrong code on a live OTP: count it, and burn the OTP after too many guesses
            //    (a 6-digit code is otherwise brute-forceable, and a correct code now yields a token).
            if (otp.getExpirationTime().isAfter(LocalDateTime.now())) {
                int attempts = (otp.getFailedAttempts() == null ? 0 : otp.getFailedAttempts()) + 1;
                if (attempts >= MAX_OTP_ATTEMPTS) {
                    otpRepo.deleteByEmail(email);
                } else {
                    otp.setFailedAttempts(attempts);
                    otpRepo.save(otp);
                }
            }
        }
        return false;
    }


    // -------------------------------------------------------------------
    // III. SOCIAL LOGIN (OAUTH) METHOD
    // -------------------------------------------------------------------

    @Transactional
    public User socialLogin(SocialUser socialUser) {
        
        Optional<User> existingUser = userRepo.findByEmail(socialUser.getEmail());

        if (existingUser.isPresent()) {
            return existingUser.get(); // Existing user logs in
        } else {
            // New user: Automatic registration
            User newUser = new User();
            newUser.setEmail(socialUser.getEmail());
            newUser.setRole("CUSTOMER"); // Default role for social sign-ups
            
            // Set a non-password placeholder hash
            newUser.setPassword("SOCIAL_LOGIN_" + socialUser.getProvider()); 

            User savedUser = userRepo.save(newUser);
            
            // Create and save the linked Customer profile
            Customer newCustomer = new Customer();
            newCustomer.setUser(savedUser);
            newCustomer.setName(socialUser.getName()); // Use name from social profile
            customerRepo.save(newCustomer);

            return savedUser;
        }
    }
}