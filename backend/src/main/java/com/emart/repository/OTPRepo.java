package com.emart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.emart.model.OTP;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OTPRepo extends JpaRepository<OTP, Integer> {
    
    // Custom query to find the LATEST, unexpired OTP for an email address
    Optional<OTP> findTopByEmailOrderByExpirationTimeDesc(String email);
    
    // Clean up all OTPs for an email after successful verification
    void deleteByEmail(String email);
}