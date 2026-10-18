package com.emart.security;

import com.emart.model.OTP;
import com.emart.model.User;
import com.emart.repository.OTPRepo;
import com.emart.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end: credentials/OTP in, signed token out, token accepted by protected endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthTokenIssuanceTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepo userRepo;
    @Autowired OTPRepo otpRepo;
    @Autowired BCryptPasswordEncoder encoder;

    static String registerJson(String email, String role) {
        return "{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\",\"role\":\"" + role + "\","
                + "\"retailerDetails\":{\"name\":\"R\",\"shopName\":\"S\",\"location\":\"l\"},"
                + "\"wholesalerDetails\":{\"name\":\"W\",\"businessId\":\"B\",\"contact\":\"c\"},"
                + "\"customerDetails\":{\"name\":\"C\",\"phone\":\"1\",\"address\":\"a\"}}";
    }

    String tokenFrom(MvcResult r) throws Exception {
        String body = r.getResponse().getContentAsString();
        int i = body.indexOf("\"token\":\"") + 9;
        return body.substring(i, body.indexOf('"', i));
    }

    @Test
    void register_returnsToken_thatWorksForTheRegisteredRole() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(registerJson("newret@test.local", "RETAILER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.role").value("RETAILER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        mvc.perform(get("/api/products/mine").header("Authorization", "Bearer " + tokenFrom(r)))
                .andExpect(status().isOk());
    }

    @Test
    void login_returnsToken_andBadCredentialsReturnNone() throws Exception {
        userRepo.save(new User("login@test.local", encoder.encode("Passw0rd!"), "WHOLESALER", null));
        MvcResult r = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"LOGIN@test.local\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        mvc.perform(get("/api/wholesaler/products/mine").header("Authorization", "Bearer " + tokenFrom(r)))
                .andExpect(status().isOk());
        // a wholesaler token must not open the retailer area
        mvc.perform(get("/api/products/mine").header("Authorization", "Bearer " + tokenFrom(r)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"login@test.local\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void social_login_issuesNoToken() throws Exception {
        userRepo.save(new User("victim@test.local", encoder.encode("Passw0rd!"), "RETAILER", null));
        mvc.perform(post("/api/auth/social-login").contentType("application/json")
                        .content("{\"email\":\"victim@test.local\",\"name\":\"x\",\"provider\":\"GOOGLE\"}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    // ---- OTP -------------------------------------------------------------

    OTP otp(String email, String code) {
        OTP o = new OTP();
        o.setEmail(email);
        o.setCode(code);
        o.setExpirationTime(LocalDateTime.now().plusMinutes(5));
        return otpRepo.save(o);
    }

    @Test
    void otpLogin_forExistingUser_returnsToken() throws Exception {
        userRepo.save(new User("otp@test.local", encoder.encode("Passw0rd!"), "CUSTOMER", null));
        otp("otp@test.local", "123456");
        mvc.perform(post("/api/auth/verify-otp").param("email", "otp@test.local").param("code", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void otpVerification_forNewEmail_issuesNoToken() throws Exception {
        otp("brandnew@test.local", "654321");
        mvc.perform(post("/api/auth/verify-otp").param("email", "brandnew@test.local").param("code", "654321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void otp_isLockedAfterTooManyWrongGuesses_evenForTheCorrectCode() throws Exception {
        userRepo.save(new User("brute@test.local", encoder.encode("Passw0rd!"), "CUSTOMER", null));
        otp("brute@test.local", "111111");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/verify-otp").param("email", "brute@test.local").param("code", "00000" + i))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/verify-otp").param("email", "brute@test.local").param("code", "111111"))
                .andExpect(status().isUnauthorized());
        assertTrue(otpRepo.findTopByEmailOrderByExpirationTimeDesc("brute@test.local").isEmpty());
    }
}
