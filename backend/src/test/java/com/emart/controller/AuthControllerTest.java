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
    @InjectMocks AuthController controller;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
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
                .thenThrow(new RuntimeException("Invalid role. Must be CUSTOMER, RETAILER or WHOLESALER."));
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
                .andExpect(jsonPath("$.password").doesNotExist());
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
}
