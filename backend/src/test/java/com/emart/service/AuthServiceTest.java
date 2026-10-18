package com.emart.service;

import com.emart.model.Customer;
import com.emart.model.OTP;
import com.emart.model.Retailer;
import com.emart.model.User;
import com.emart.model.Wholesaler;
import com.emart.repository.CustomerRepo;
import com.emart.repository.OTPRepo;
import com.emart.repository.RetailerRepo;
import com.emart.repository.UserRepo;
import com.emart.repository.WholesalerRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Plain Mockito unit tests (no Spring context) for the registration, login and OTP rules. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepo userRepo;
    @Mock OTPRepo otpRepo;
    @Mock EmailService emailService;
    @Mock CustomerRepo customerRepo;
    @Mock RetailerRepo retailerRepo;
    @Mock WholesalerRepo wholesalerRepo;
    @Spy BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @InjectMocks AuthService service;

    private User newUser(String email, String password, String role) {
        return new User(email, password, role, "");
    }

    // ---------- registration ----------

    @Test
    void register_normalizesEmailAndRole_hashesPassword_andCreatesRetailerProfile() {
        when(userRepo.findByEmail("shop@example.com")).thenReturn(Optional.empty());
        when(userRepo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User saved = service.registerUser(newUser("  Shop@Example.COM ", "pw123456", " retailer "),
                null, new Retailer(), null);

        assertEquals("shop@example.com", saved.getEmail());
        assertEquals("RETAILER", saved.getRole());
        assertNotEquals("pw123456", saved.getPassword(), "password must not be stored in plain text");
        assertTrue(passwordEncoder.matches("pw123456", saved.getPassword()));
        verify(retailerRepo).save(any(Retailer.class));
        verifyNoInteractions(customerRepo, wholesalerRepo);
    }

    @Test
    void register_customerAndWholesalerCreateTheirOwnProfiles() {
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.registerUser(newUser("c@example.com", "pw123456", "CUSTOMER"), new Customer(), null, null);
        service.registerUser(newUser("w@example.com", "pw123456", "WHOLESALER"), null, null, new Wholesaler());

        verify(customerRepo).save(any(Customer.class));
        verify(wholesalerRepo).save(any(Wholesaler.class));
    }

    @Test
    void register_rejectsUnknownRole_andSavesNothing() {
        assertThrows(RuntimeException.class,
                () -> service.registerUser(newUser("x@example.com", "pw123456", "ADMIN"), null, null, null));
        assertThrows(RuntimeException.class,
                () -> service.registerUser(newUser("x@example.com", "pw123456", null), null, null, null));
        verify(userRepo, never()).save(any());
    }

    @Test
    void register_rejectsBlankEmailAndEmptyPassword() {
        assertThrows(RuntimeException.class,
                () -> service.registerUser(newUser("   ", "pw123456", "CUSTOMER"), null, null, null));
        assertThrows(RuntimeException.class,
                () -> service.registerUser(newUser("a@example.com", "", "CUSTOMER"), null, null, null));
        assertThrows(RuntimeException.class,
                () -> service.registerUser(newUser("a@example.com", null, "CUSTOMER"), null, null, null));
        verify(userRepo, never()).save(any());
    }

    @Test
    void register_duplicateEmailIsDetectedAfterNormalization() {
        when(userRepo.findByEmail("dup@example.com")).thenReturn(Optional.of(new User()));
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.registerUser(newUser("DUP@example.com ", "pw123456", "CUSTOMER"), null, null, null));
        assertEquals("Email already in use.", e.getMessage());
        verify(userRepo, never()).save(any());
    }


    @Test
    void register_enforcesPasswordLengthAndEmailFormat() {
        IllegalArgumentException shortPw = assertThrows(IllegalArgumentException.class,
                () -> service.registerUser(newUser("a@example.com", "short77", "CUSTOMER"), null, null, null));
        assertTrue(shortPw.getMessage().contains("8"));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerUser(newUser("a@example.com", "x".repeat(73), "CUSTOMER"), null, null, null),
                "BCrypt ignores everything after 72 bytes, so longer passwords are refused");
        assertThrows(IllegalArgumentException.class,
                () -> service.registerUser(newUser("not-an-email", "long-enough-1", "CUSTOMER"), null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerUser(newUser("a@b", "long-enough-1", "CUSTOMER"), null, null, null));
        verify(userRepo, never()).save(any());
    }

    @Test
    void register_validationFailuresAreIllegalArgumentExceptions_soTheControllerCanTellThemFromBugs() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerUser(newUser("x@example.com", "pw123456", "ADMIN"), null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerUser(newUser("   ", "pw123456", "CUSTOMER"), null, null, null));
    }

    // ---------- login ----------

    @Test
    void login_isCaseAndWhitespaceInsensitive_andChecksBcrypt() {
        User stored = newUser("a@example.com", passwordEncoder.encode("secret1"), "CUSTOMER");
        when(userRepo.findByEmail("a@example.com")).thenReturn(Optional.of(stored));

        assertSame(stored, service.login("  A@Example.com", "secret1"));
        assertNull(service.login("a@example.com", "wrong"));
    }

    @Test
    void login_returnsNullForMissingInputsOrUnknownUser() {
        when(userRepo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        assertNull(service.login(null, "pw"));
        assertNull(service.login("a@example.com", null));
        assertNull(service.login("nobody@example.com", "pw"));
    }

    // ---------- OTP ----------

    @Test
    void sendOtp_storesLowercaseEmail_sixDigitCode_fiveMinuteExpiry() {
        service.sendOtp("  Person@Example.COM ");

        ArgumentCaptor<OTP> captor = ArgumentCaptor.forClass(OTP.class);
        verify(otpRepo).save(captor.capture());
        OTP otp = captor.getValue();
        assertEquals("person@example.com", otp.getEmail());
        assertTrue(otp.getCode().matches("\\d{6}"));
        assertTrue(otp.getExpirationTime().isAfter(LocalDateTime.now().plusMinutes(4)));
        assertTrue(otp.getExpirationTime().isBefore(LocalDateTime.now().plusMinutes(6)));
        verify(emailService).sendOtpEmail("person@example.com", otp.getCode());
    }

    @Test
    void sendOtp_whenEmailFails_reportsFailure_andNeverLogsTheCode() {
        doThrow(new RuntimeException("smtp down")).when(emailService).sendOtpEmail(any(), any());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        java.io.PrintStream oldOut = System.out, oldErr = System.err;
        System.setOut(new java.io.PrintStream(out));
        System.setErr(new java.io.PrintStream(err));
        try {
            assertThrows(RuntimeException.class, () -> service.sendOtp("a@example.com"));
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        ArgumentCaptor<OTP> captor = ArgumentCaptor.forClass(OTP.class);
        verify(otpRepo, atLeastOnce()).save(captor.capture());
        String code = captor.getValue().getCode();
        assertFalse((out + "" + err).contains(code), "OTP code must not be logged by default");
        verify(otpRepo).deleteByEmail("a@example.com"); // unusable code is discarded
    }

    @Test
    void sendOtp_whenEmailFails_canOptInToConsoleCodeForLocalDemos() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "logOtpCodes", true);
        doThrow(new RuntimeException("smtp down")).when(emailService).sendOtpEmail(any(), any());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream oldOut = System.out;
        System.setOut(new java.io.PrintStream(out));
        try {
            assertDoesNotThrow(() -> service.sendOtp("a@example.com"));
        } finally {
            System.setOut(oldOut);
        }
        ArgumentCaptor<OTP> captor = ArgumentCaptor.forClass(OTP.class);
        verify(otpRepo).save(captor.capture());
        assertTrue(out.toString().contains(captor.getValue().getCode()));
    }

@Test
    void sendOtp_rejectsBlankEmail() {
        assertThrows(IllegalArgumentException.class, () -> service.sendOtp("  "));
    }

    private OTP otp(String code, LocalDateTime expires) {
        OTP o = new OTP();
        o.setEmail("a@example.com");
        o.setCode(code);
        o.setExpirationTime(expires);
        return o;
    }

    @Test
    void verifyOtp_acceptsMixedCaseEmail_andDeletesCodes() {
        when(otpRepo.findTopByEmailOrderByExpirationTimeDesc("a@example.com"))
                .thenReturn(Optional.of(otp("123456", LocalDateTime.now().plusMinutes(3))));

        assertTrue(service.verifyOtp(" A@Example.com ", "123456"));
        verify(otpRepo).deleteByEmail("a@example.com");
    }

    @Test
    void verifyOtp_rejectsExpiredWrongOrMissingCode() {
        when(otpRepo.findTopByEmailOrderByExpirationTimeDesc("a@example.com"))
                .thenReturn(Optional.of(otp("123456", LocalDateTime.now().minusSeconds(1))));
        assertFalse(service.verifyOtp("a@example.com", "123456"), "expired");

        when(otpRepo.findTopByEmailOrderByExpirationTimeDesc("a@example.com"))
                .thenReturn(Optional.of(otp("123456", LocalDateTime.now().plusMinutes(3))));
        assertFalse(service.verifyOtp("a@example.com", "000000"), "wrong code");
        assertFalse(service.verifyOtp("a@example.com", null), "null code");

        when(otpRepo.findTopByEmailOrderByExpirationTimeDesc("none@example.com")).thenReturn(Optional.empty());
        assertFalse(service.verifyOtp("none@example.com", "123456"), "no OTP on file");
        verify(otpRepo, never()).deleteByEmail(any());
    }

    // ---------- helper ----------

    @Test
    void normalizeEmail_trimsLowercasesAndNullsBlank() {
        assertEquals("a@b.com", AuthService.normalizeEmail("  A@B.com "));
        assertNull(AuthService.normalizeEmail("   "));
        assertNull(AuthService.normalizeEmail(null));
    }
}
