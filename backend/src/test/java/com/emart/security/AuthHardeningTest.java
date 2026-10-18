package com.emart.security;

import com.emart.model.User;
import com.emart.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Brute-force protection, registration rules and CORS, exercised through the real filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthHardeningTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepo userRepo;
    @Autowired BCryptPasswordEncoder encoder;

    static String login(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    static String register(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"role\":\"CUSTOMER\","
                + "\"customerDetails\":{\"name\":\"C\",\"phone\":\"1\",\"address\":\"a\"}}";
    }

    // ------------------------------------------------------------------ login throttling

    @Test
    void fiveWrongPasswords_lockTheAccountTemporarily_evenForTheCorrectPassword() throws Exception {
        String email = "lock-" + UUID.randomUUID() + "@t.test";
        userRepo.save(new User(email, encoder.encode("Correct-Pass-1"), "CUSTOMER", null));

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType("application/json").content(login(email, "wrong-" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType("application/json").content(login(email.toUpperCase(), "Correct-Pass-1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void aSuccessfulLogin_resetsTheFailureCount() throws Exception {
        String email = "reset-" + UUID.randomUUID() + "@t.test";
        userRepo.save(new User(email, encoder.encode("Correct-Pass-1"), "CUSTOMER", null));
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 4; i++) {
                mvc.perform(post("/api/auth/login").contentType("application/json").content(login(email, "bad"))).andExpect(status().isUnauthorized());
            }
            mvc.perform(post("/api/auth/login").contentType("application/json").content(login(email, "Correct-Pass-1")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void otherAccountsAreNotAffected() throws Exception {
        String victim = "victim-" + UUID.randomUUID() + "@t.test";
        String other = "other-" + UUID.randomUUID() + "@t.test";
        userRepo.save(new User(other, encoder.encode("Correct-Pass-1"), "CUSTOMER", null));
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/auth/login").contentType("application/json").content(login(victim, "bad")));
        }
        mvc.perform(post("/api/auth/login").contentType("application/json").content(login(other, "Correct-Pass-1")))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ registration rules

    @Test
    void registration_requiresAReasonablePasswordAndAValidEmail() throws Exception {
        String ok = "reg-" + UUID.randomUUID() + "@t.test";
        mvc.perform(post("/api/auth/register").contentType("application/json").content(register(ok, "short7!")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("8")));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(register("not-an-email", "Long-Enough-1")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(register("a@b", "Long-Enough-1")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(register(ok, "x".repeat(73))))
                .andExpect(status().isBadRequest());
        assertTrue(userRepo.findByEmail(ok).isEmpty());

        mvc.perform(post("/api/auth/register").contentType("application/json").content(register(ok, "Long-Enough-1")))
                .andExpect(status().isCreated());
        assertNotEquals("Long-Enough-1", userRepo.findByEmail(ok).orElseThrow().getPassword(), "stored as a BCrypt hash");
        assertTrue(userRepo.findByEmail(ok).orElseThrow().getPassword().startsWith("$2"));
    }

    // ------------------------------------------------------------------ CORS

    @Test
    void corsPreflight_isAllowedForTheLocalDevOrigin_andRefusedForOthers() throws Exception {
        mvc.perform(options("/api/products")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/products")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
