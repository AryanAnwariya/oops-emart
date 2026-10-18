package com.emart.controller;

import com.emart.model.User;
import com.emart.repository.UserRepo;
import com.emart.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone MockMvc tests: controller wiring and status codes only, no Spring context or security chain. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock AuthService authService;
    @Mock UserRepo userRepo;
    @Mock com.emart.security.TokenService tokenService;
    @Mock com.emart.security.LoginAttemptService loginAttempts;
    @InjectMocks AuthController controller;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(tokenService.issue(any())).thenReturn(new com.emart.security.TokenService.IssuedToken("tok", 1800));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void socialLogin_isDisabledUntilServerSideTokenVerificationExists() throws Exception {
        mvc.perform(post("/api/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"victim@example.com\",\"name\":\"X\",\"provider\":\"GOOGLE\"}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").exists());
        verify(authService, never()).socialLogin(any());
    }

    @Test
    void register_invalidRoleReturns400WithMessage() throws Exception {
        when(authService.registerUser(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid role. Must be CUSTOMER, RETAILER or WHOLESALER."));
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"pw\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid role. Must be CUSTOMER, RETAILER or WHOLESALER."));
    }

    @Test
    void login_badCredentialsReturn401_andGoodOnesReturnUserWithoutPassword() throws Exception {
        when(authService.login("a@b.com", "bad")).thenReturn(null);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized());

        User u = new User("a@b.com", "HASH", "RETAILER", "");
        u.setId(7);
        when(authService.login("a@b.com", "good")).thenReturn(u);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RETAILER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.token").value("tok"));
    }

    @Test
    void verifyOtp_looksUpUserWithNormalizedEmail() throws Exception {
        when(authService.verifyOtp(any(), any())).thenReturn(true);
        User u = new User("a@b.com", "HASH", "CUSTOMER", "");
        u.setId(1);
        when(userRepo.findByEmail("a@b.com")).thenReturn(Optional.of(u));

        mvc.perform(post("/api/auth/verify-otp").param("email", " A@B.com ").param("code", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@b.com"));
        verify(userRepo).findByEmail("a@b.com");
    }

    @Test
    void verifyOtp_invalidCodeReturns401() throws Exception {
        when(authService.verifyOtp(any(), any())).thenReturn(false);
        mvc.perform(post("/api/auth/verify-otp").param("email", "a@b.com").param("code", "000000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unexpectedFailures_returnAGenericMessage_neverTheExceptionText() throws Exception {
        String secret = "jdbc:h2:mem:emartDB constraint SYS_CT_10092 violated";
        when(authService.login(any(), any())).thenThrow(new RuntimeException(secret));
        when(authService.registerUser(any(), any(), any(), any())).thenThrow(new RuntimeException(secret));
        org.mockito.Mockito.doThrow(new RuntimeException(secret)).when(authService).sendOtp(any());
        when(authService.verifyOtp(any(), any())).thenThrow(new RuntimeException(secret));

        for (var req : java.util.List.of(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.com\",\"password\":\"x\"}"),
                post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.com\",\"password\":\"x\",\"role\":\"CUSTOMER\"}"),
                post("/api/auth/send-otp").param("email", "a@b.com"),
                post("/api/auth/verify-otp").param("email", "a@b.com").param("code", "123456"))) {
            String body = mvc.perform(req).andExpect(status().isInternalServerError())
                    .andReturn().getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertFalse(body.contains("jdbc") || body.contains("SYS_CT"), "leaked: " + body);
            org.junit.jupiter.api.Assertions.assertTrue(body.contains("error"));
        }
    }

    @Test
    void login_whenTheAccountIsLocked_returns429WithRetryAfter_andNeverChecksThePassword() throws Exception {
        when(loginAttempts.isBlocked("a@b.com")).thenReturn(true);
        when(loginAttempts.retryAfter("a@b.com")).thenReturn(java.time.Duration.ofMinutes(7));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" A@B.com \",\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "420"));
        verify(authService, never()).login(any(), any());
    }

    @Test
    void login_recordsFailures_andResetsOnSuccess() throws Exception {
        when(authService.login("a@b.com", "bad")).thenReturn(null);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"password\":\"bad\"}")).andExpect(status().isUnauthorized());
        verify(loginAttempts).recordFailure("a@b.com");

        User u = new User("a@b.com", "HASH", "RETAILER", "");
        u.setId(7);
        when(authService.login("a@b.com", "good")).thenReturn(u);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"password\":\"good\"}")).andExpect(status().isOk());
        verify(loginAttempts).reset("a@b.com");
    }
}
