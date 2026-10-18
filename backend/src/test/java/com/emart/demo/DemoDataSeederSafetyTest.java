package com.emart.demo;

import com.emart.model.Product;
import com.emart.model.User;
import com.emart.model.WholesalerProduct;
import com.emart.repository.UserRepo;
import com.emart.service.AuthService;
import com.emart.service.ProductService;
import com.emart.service.WholesalerProductService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Production-safety rules for the seeder, tested with mocks so no real database or context is involved. */
class DemoDataSeederSafetyTest {

    static final String DEMO_PASSWORD = "Unit-Test-Only-Pass-1";
    static final List<String> ALL_EMAILS = List.of(DemoDataSeeder.CUSTOMER, DemoDataSeeder.RETAILER_1,
            DemoDataSeeder.RETAILER_2, DemoDataSeeder.WHOLESALER);

    final AuthService authService = mock(AuthService.class);
    final ProductService productService = mock(ProductService.class);
    final WholesalerProductService wholesalerService = mock(WholesalerProductService.class);
    final UserRepo userRepo = mock(UserRepo.class);
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    DemoDataSeeder seeder(String password) {
        DemoDataSeeder s = new DemoDataSeeder(authService, productService, wholesalerService, userRepo, encoder);
        ReflectionTestUtils.setField(s, "password", password);
        return s;
    }

    static String roleOf(String email) {
        return email.contains("customer") ? "CUSTOMER" : email.contains("wholesaler") ? "WHOLESALER" : "RETAILER";
    }

    User user(int id, String email, String role, String rawPassword) {
        User u = new User(email, encoder.encode(rawPassword), role, null);
        u.setId(id);
        return u;
    }

    void registerGivesIds() {
        when(authService.registerUser(any(), any(), any(), any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(77);
            return u;
        });
    }

    @Test
    void passwordShorterThan12_isRejected_andNothingIsCreated() {
        assertThrows(IllegalStateException.class, () -> seeder("short-pass-").seed());   // 11 chars
        assertThrows(IllegalStateException.class, () -> seeder("").seed());
        assertThrows(IllegalStateException.class, () -> seeder(null).seed());
        verifyNoInteractions(authService, productService, wholesalerService);
    }

    @Test
    void existingDemoUserWithADifferentPassword_isNeverModified_andGetsNoProducts() throws Exception {
        // someone registered the demo emails first with their own password
        for (String email : ALL_EMAILS) {
            when(userRepo.findByEmail(email))
                    .thenReturn(Optional.of(user(9, email, roleOf(email), "attackers-own-password")));
        }
        seeder(DEMO_PASSWORD).seed();

        verify(authService, never()).registerUser(any(), any(), any(), any());
        verify(userRepo, never()).save(any());
        verify(productService, never()).addProduct(any(), any(), anyInt());
        verify(wholesalerService, never()).addProduct(any(), any(), anyInt());
    }

    @Test
    void existingDemoUserWithTheWrongRole_isNeverUsed() throws Exception {
        when(userRepo.findByEmail(DemoDataSeeder.RETAILER_1))
                .thenReturn(Optional.of(user(5, DemoDataSeeder.RETAILER_1, "CUSTOMER", DEMO_PASSWORD)));
        registerGivesIds();
        seeder(DEMO_PASSWORD).seed();

        verify(productService, never()).addProduct(any(), any(), eq(5));
        verify(userRepo, never()).save(any());
    }

    @Test
    void existingCorrectAccounts_areKept_andProductsAreNotDuplicated() throws Exception {
        for (String email : ALL_EMAILS) {
            when(userRepo.findByEmail(email))
                    .thenReturn(Optional.of(user(email.hashCode() & 0xff, email, roleOf(email), DEMO_PASSWORD)));
        }
        when(productService.getProductsOwnedBy(anyInt())).thenAnswer(inv ->
                DemoDataSeeder.retailProductNames().stream().map(n -> new Product(null, "d", "c", n, 1, 1)).toList());
        when(wholesalerService.getProductsOwnedBy(anyInt())).thenAnswer(inv ->
                DemoDataSeeder.wholesaleProductNames().stream().map(n -> new WholesalerProduct(null, "d", "c", n, 1, 1)).toList());
        seeder(DEMO_PASSWORD).seed();

        verify(authService, never()).registerUser(any(), any(), any(), any());
        verify(productService, never()).addProduct(any(), any(), anyInt());
        verify(wholesalerService, never()).addProduct(any(), any(), anyInt());
    }

    @Test
    void passwordIsNeverWrittenToConsoleOutput() throws Exception {
        registerGivesIds();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream oldOut = System.out, oldErr = System.err;
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));
        try {
            seeder(DEMO_PASSWORD).seed();
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        assertFalse((out + "" + err).contains(DEMO_PASSWORD));
        verify(authService, times(4)).registerUser(any(), any(), any(), any());
    }
}
