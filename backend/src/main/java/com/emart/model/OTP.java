package com.emart.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class OTP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String email;           // The email address the code was sent to
    private String code;            // The actual 6-digit code
    private LocalDateTime expirationTime; // When the code becomes invalid
    private Integer failedAttempts;  // Wrong guesses so far (null = 0); code is burned after too many

    // --- Constructors, Getters, and Setters ---

    public OTP() {}

    // Getters and Setters for all fields (id, email, code, expirationTime) are required here.
    
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Integer getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(Integer failedAttempts) { this.failedAttempts = failedAttempts; }

    public LocalDateTime getExpirationTime() { return expirationTime; }
    public void setExpirationTime(LocalDateTime expirationTime) { this.expirationTime = expirationTime; }
}