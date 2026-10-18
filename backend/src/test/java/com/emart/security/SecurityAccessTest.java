package com.emart.security;

import com.emart.model.User;
import com.emart.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full filter-chain tests: authentication (401) and role enforcement (403) are decided by Spring Security. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAccessTest {

    static final String PRODUCT_JSON = "{\"name\":\"Apple\",\"description\":\"d\",\"category\":\"fruit\",\"price\":1.5,\"quantity\":3}";

    @Autowired MockMvc mvc;
    @Autowired UserRepo userRepo;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired TokenService tokens;
    @Autowired JwtEncoder jwtEncoder;

    User user(String email, String role) {
        return userRepo.save(new User(email, encoder.encode("Passw0rd!"), role, null));
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u).token();
    }

    MockHttpServletRequestBuilder createProduct(String path) {
        return multipart(path).file(new MockMultipartFile("product", "", "application/json", PRODUCT_JSON.getBytes()));
    }

    // ---- public surface -------------------------------------------------

    @Test
    void publicBrowsing_needsNoToken() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/wholesaler/products")).andExpect(status().is2xxSuccessful());
    }

    @Test
    void authEndpoints_arePublic_andFailInTheController_notTheFilter() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"nobody@x.com\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password."));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"email\":\"a@b.com\",\"password\":\"pw\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/social-login").contentType("application/json")
                        .content("{\"email\":\"a@b.com\",\"name\":\"x\"}"))
                .andExpect(status().isNotImplemented());
    }

    // ---- authentication -------------------------------------------------

    @Test
    void missingToken_isRejectedWith401Json() throws Exception {
        mvc.perform(createProduct("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.error").exists());
        mvc.perform(put("/api/products/update/abc")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/products/delete/abc")).andExpect(status().isUnauthorized());
        mvc.perform(createProduct("/api/wholesaler/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownApiRoutes_requireAuthenticationByDefault() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageAndMalformedTokens_areRejected() throws Exception {
        mvc.perform(createProduct("/api/products").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(createProduct("/api/products").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredToken_isRejected() throws Exception {
        User r = user("r1@test.local", "RETAILER");
        TokenService old = new TokenService(jwtEncoder, "emart", Duration.ofMinutes(30),
                Clock.offset(Clock.systemUTC(), Duration.ofHours(-2)));
        mvc.perform(createProduct("/api/products").header("Authorization", "Bearer " + old.issue(r).token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromOtherIssuer_isRejected() throws Exception {
        User r = user("r2@test.local", "RETAILER");
        TokenService other = new TokenService(jwtEncoder, "evil", Duration.ofMinutes(30));
        mvc.perform(createProduct("/api/products").header("Authorization", "Bearer " + other.issue(r).token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForDeletedUser_isRejected() throws Exception {
        User r = user("r3@test.local", "RETAILER");
        String auth = bearer(r);
        userRepo.delete(r);
        userRepo.flush();
        mvc.perform(createProduct("/api/products").header("Authorization", auth))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWhoseRoleClaimDisagreesWithDatabase_isRejected() throws Exception {
        User c = user("c1@test.local", "CUSTOMER");
        User claimsRetailer = new User(c.getEmail(), "x", "RETAILER", null);
        claimsRetailer.setId(c.getId());
        mvc.perform(createProduct("/api/products").header("Authorization", bearer(claimsRetailer)))
                .andExpect(status().isUnauthorized());
    }

    // ---- authorization (roles) -----------------------------------------

    @Test
    void customer_cannotManageEitherCatalogue() throws Exception {
        String auth = bearer(user("c2@test.local", "CUSTOMER"));
        mvc.perform(createProduct("/api/products").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(createProduct("/api/wholesaler/products").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/delete/x").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/mine").header("Authorization", auth)).andExpect(status().isForbidden());
    }

    @Test
    void retailer_cannotManageWholesalerCatalogue_andViceVersa() throws Exception {
        String retailer = bearer(user("r4@test.local", "RETAILER"));
        String wholesaler = bearer(user("w1@test.local", "WHOLESALER"));
        mvc.perform(createProduct("/api/wholesaler/products").header("Authorization", retailer)).andExpect(status().isForbidden());
        mvc.perform(createProduct("/api/products").header("Authorization", wholesaler)).andExpect(status().isForbidden());
        mvc.perform(put("/api/wholesaler/products/update/x").header("Authorization", retailer)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/delete/x").header("Authorization", wholesaler)).andExpect(status().isForbidden());
    }

    @Test
    void correctRole_canCreateProducts() throws Exception {
        mvc.perform(createProduct("/api/products").header("Authorization", bearer(user("r5@test.local", "RETAILER"))))
                .andExpect(status().isCreated());
        mvc.perform(createProduct("/api/wholesaler/products").header("Authorization", bearer(user("w2@test.local", "WHOLESALER"))))
                .andExpect(status().isCreated());
    }
}
