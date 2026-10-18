package com.emart.security;

import com.emart.model.Product;
import com.emart.model.User;
import com.emart.repository.ProductRepo;
import com.emart.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Images are served back from the application's own origin, so an uploaded file that is really HTML or SVG would be
 * a stored-XSS vector (it could read the JWT from localStorage). The server must never trust the declared type.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UploadAndInputValidationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] GIF = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
    static final byte[] HTML = "<html><script>fetch('/steal?'+localStorage.token)</script></html>".getBytes(StandardCharsets.UTF_8);
    static final byte[] SVG = "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8);

    @Autowired MockMvc mvc;
    @Autowired UserRepo userRepo;
    @Autowired ProductRepo productRepo;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired TokenService tokens;

    User retailer() {
        return userRepo.save(new User("up-" + UUID.randomUUID() + "@t.test", encoder.encode("Passw0rd!"), "RETAILER", null));
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u).token();
    }

    static String json(String name, String description, String category) {
        return "{\"name\":\"" + name + "\",\"description\":\"" + description + "\",\"category\":\"" + category
                + "\",\"price\":5,\"quantity\":5}";
    }

    MockHttpServletRequestBuilder create(User u, String productJson, String fileName, String declaredType, byte[] bytes) {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/products")
                .file(new MockMultipartFile("product", "", "application/json", productJson.getBytes(StandardCharsets.UTF_8)));
        if (bytes != null) b.file(new MockMultipartFile("imageFile", fileName, declaredType, bytes));
        return b.header("Authorization", bearer(u));
    }

    Product only(User u) {
        List<Product> list = productRepo.findByOwnerId(u.getId());
        assertEquals(1, list.size());
        return list.get(0);
    }

    // ------------------------------------------------------------------ image safety

    @Test
    void htmlDisguisedAsAnImage_isRejected() throws Exception {
        User u = retailer();
        mvc.perform(create(u, json("Evil", "d", "c"), "x.png", "image/png", HTML))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mvc.perform(create(u, json("Evil", "d", "c"), "x.html", "text/html", HTML)).andExpect(status().isBadRequest());
        assertTrue(productRepo.findByOwnerId(u.getId()).isEmpty());
    }

    @Test
    void svgIsRejected_becauseItCanCarryScript() throws Exception {
        User u = retailer();
        mvc.perform(create(u, json("Svg", "d", "c"), "x.svg", "image/svg+xml", SVG)).andExpect(status().isBadRequest());
    }

    @Test
    void realImages_areAccepted_andServedWithTheDetectedType_notTheClientsClaim() throws Exception {
        User u = retailer();
        // the client lies about the type; the server detects PNG from the bytes and serves image/png
        mvc.perform(create(u, json("Pic", "d", "c"), "../../evil.html", "text/html", PNG)).andExpect(status().isCreated());
        Product p = only(u);
        assertEquals("image/png", p.getImageType());
        assertFalse(p.getImageName().contains("/") || p.getImageName().contains("\\"), "stored file name must not hold a path");

        mvc.perform(get("/api/products/" + p.getId() + "/image"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void jpegAndGif_areAccepted() throws Exception {
        User u = retailer();
        mvc.perform(create(u, json("J", "d", "c"), "a.jpg", "image/jpeg", JPEG)).andExpect(status().isCreated());
        mvc.perform(create(u, json("G", "d", "c"), "a.gif", "image/gif", GIF)).andExpect(status().isCreated());
        assertEquals(2, productRepo.findByOwnerId(u.getId()).size());
    }

    @Test
    void updatingWithAnUnsafeImage_isRejected_andKeepsTheOldOne() throws Exception {
        User u = retailer();
        mvc.perform(create(u, json("Pic", "d", "c"), "a.png", "image/png", PNG)).andExpect(status().isCreated());
        Product p = only(u);
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/products/update/" + p.getId())
                        .file(new MockMultipartFile("product", "", "application/json", json("Pic", "d", "c").getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("imageFile", "b.png", "image/png", HTML))
                        .header("Authorization", bearer(u)))
                .andExpect(status().isBadRequest());
        assertArrayEquals(PNG, productRepo.findById(p.getId()).orElseThrow().getImageData());
    }

    // ------------------------------------------------------------------ input limits (columns are 255 chars)

    @Test
    void overlongFields_areRejectedWith400_insteadOfAServerError() throws Exception {
        User u = retailer();
        String tooLong = "x".repeat(300);
        mvc.perform(create(u, json(tooLong, "d", "c"), null, null, null)).andExpect(status().isBadRequest());
        mvc.perform(create(u, json("ok", tooLong, "c"), null, null, null)).andExpect(status().isBadRequest());
        mvc.perform(create(u, json("ok", "d", tooLong), null, null, null)).andExpect(status().isBadRequest());
        assertTrue(productRepo.findByOwnerId(u.getId()).isEmpty());
    }

    @Test
    void fieldsAtTheLimit_areAccepted() throws Exception {
        User u = retailer();
        mvc.perform(create(u, json("n".repeat(100), "d".repeat(255), "c".repeat(100)), null, null, null))
                .andExpect(status().isCreated());
    }
}
