package com.emart.demo;

import com.emart.model.Product;
import com.emart.model.User;
import com.emart.model.WholesalerProduct;
import com.emart.repository.ProductRepo;
import com.emart.repository.UserRepo;
import com.emart.repository.WholesalerProductRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs the real application with seeding switched on (it is off by default). */
@SpringBootTest(properties = {"app.demo.seed=true", "app.demo.password=Local-Test-Only-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoDataSeederTest {

    @Autowired DemoDataSeeder seeder;
    @Autowired UserRepo userRepo;
    @Autowired ProductRepo productRepo;
    @Autowired WholesalerProductRepo wholesalerRepo;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired MockMvc mvc;

    @Test
    void seeds_oneAccountPerRole_withHashedPasswords() {
        for (String email : List.of(DemoDataSeeder.CUSTOMER, DemoDataSeeder.RETAILER_1,
                DemoDataSeeder.RETAILER_2, DemoDataSeeder.WHOLESALER)) {
            User u = userRepo.findByEmail(email).orElseThrow(() -> new AssertionError("missing " + email));
            assertNotEquals("Local-Test-Only-1", u.getPassword(), "password must be stored hashed");
            assertTrue(encoder.matches("Local-Test-Only-1", u.getPassword()));
        }
        assertEquals("CUSTOMER", userRepo.findByEmail(DemoDataSeeder.CUSTOMER).orElseThrow().getRole());
        assertEquals("RETAILER", userRepo.findByEmail(DemoDataSeeder.RETAILER_1).orElseThrow().getRole());
        assertEquals("WHOLESALER", userRepo.findByEmail(DemoDataSeeder.WHOLESALER).orElseThrow().getRole());
    }

    @Test
    void seededProducts_areOwnedByTheirSeller_andIncludeLowStockItems() {
        User r1 = userRepo.findByEmail(DemoDataSeeder.RETAILER_1).orElseThrow();
        User r2 = userRepo.findByEmail(DemoDataSeeder.RETAILER_2).orElseThrow();
        User w = userRepo.findByEmail(DemoDataSeeder.WHOLESALER).orElseThrow();
        List<Product> p1 = productRepo.findByOwnerId(r1.getId());
        List<Product> p2 = productRepo.findByOwnerId(r2.getId());
        List<WholesalerProduct> pw = wholesalerRepo.findByOwnerId(w.getId());
        assertFalse(p1.isEmpty());
        assertFalse(p2.isEmpty());
        assertFalse(pw.isEmpty());
        assertTrue(p1.stream().anyMatch(p -> p.getQuantity() < 10), "retailer 1 needs a low-stock item");
        assertTrue(p1.stream().anyMatch(p -> p.getQuantity() >= 10), "and a well-stocked one");
        assertTrue(pw.stream().anyMatch(p -> p.getQuantity() < 10));
    }

    @Test
    void running_twice_doesNotDuplicateAnything() throws Exception {
        long users = userRepo.count();
        long products = productRepo.count();
        long wholesale = wholesalerRepo.count();
        seeder.run(null);
        seeder.run(null);
        assertEquals(users, userRepo.count());
        assertEquals(products, productRepo.count());
        assertEquals(wholesale, wholesalerRepo.count());
    }

    @Test
    void seededAccount_canLogIn_andManageOnlyItsOwnProducts() throws Exception {
        String body = "{\"email\":\"" + DemoDataSeeder.RETAILER_1 + "\",\"password\":\"Local-Test-Only-1\"}";
        String json = mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String token = json.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/products/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(
                        productRepo.findByOwnerId(userRepo.findByEmail(DemoDataSeeder.RETAILER_1).orElseThrow().getId()).size()));
    }

    @Test
    void blankPassword_failsFast_insteadOfCreatingWeakAccounts() {
        DemoDataSeeder s = new DemoDataSeeder(null, null, null, null);
        assertThrows(IllegalStateException.class, () -> s.run(null));
    }
}
