package com.emart.security;

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
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Ownership is derived from the verified principal; ids/owners in request bodies are never trusted. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductOwnershipTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepo userRepo;
    @Autowired ProductRepo productRepo;
    @Autowired WholesalerProductRepo wholesalerRepo;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired TokenService tokens;

    User user(String email, String role) {
        return userRepo.save(new User(email, encoder.encode("Passw0rd!"), role, null));
    }

    String bearer(User u) {
        return "Bearer " + tokens.issue(u).token();
    }

    static String json(String name, double price, int qty, String extra) {
        return "{\"name\":\"" + name + "\",\"description\":\"d\",\"category\":\"c\",\"price\":" + price
                + ",\"quantity\":" + qty + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    MockHttpServletRequestBuilder send(HttpMethod method, String url, User u, String productJson) {
        return multipart(method, url)
                .file(new MockMultipartFile("product", "", "application/json", productJson.getBytes()))
                .header("Authorization", bearer(u));
    }

    Product firstProductOf(User owner) {
        List<Product> list = productRepo.findByOwnerId(owner.getId());
        assertEquals(1, list.size());
        return list.get(0);
    }

    // ---- retailer catalogue --------------------------------------------

    @Test
    void create_setsOwnerFromPrincipal_andIgnoresClientSuppliedOwner() throws Exception {
        User a = user("ra@test.local", "RETAILER");
        User b = user("rb@test.local", "RETAILER");
        String forged = json("Apple", 1, 5, "\"ownerId\":" + b.getId() + ",\"owner_id\":" + b.getId());
        mvc.perform(send(HttpMethod.POST, "/api/products", a, forged)).andExpect(status().isCreated());

        assertEquals(1, productRepo.findByOwnerId(a.getId()).size());
        assertEquals(0, productRepo.findByOwnerId(b.getId()).size());
        mvc.perform(get("/api/products/mine").header("Authorization", bearer(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/products/mine").header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ownerId_isNeverExposedInResponses() throws Exception {
        User a = user("ra2@test.local", "RETAILER");
        mvc.perform(send(HttpMethod.POST, "/api/products", a, json("Apple", 1, 5, ""))).andExpect(status().isCreated());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$[0].ownerId").doesNotExist());
    }

    @Test
    void clientSuppliedId_cannotOverwriteSomeoneElsesProduct() throws Exception {
        User a = user("ra3@test.local", "RETAILER");
        User b = user("rb3@test.local", "RETAILER");
        mvc.perform(send(HttpMethod.POST, "/api/products", a, json("Original", 10, 1, ""))).andExpect(status().isCreated());
        Product victim = firstProductOf(a);

        mvc.perform(send(HttpMethod.POST, "/api/products", b, json("Hijack", 0, 0, "\"id\":\"" + victim.getId() + "\"")))
                .andExpect(status().isCreated());

        Product after = productRepo.findById(victim.getId()).orElseThrow();
        assertEquals("Original", after.getName());
        assertEquals(a.getId(), after.getOwnerId());
        assertEquals(1, productRepo.findByOwnerId(b.getId()).size());
        assertNotEquals(victim.getId(), productRepo.findByOwnerId(b.getId()).get(0).getId());
    }

    @Test
    void otherRetailer_cannotUpdateOrDelete() throws Exception {
        User a = user("ra4@test.local", "RETAILER");
        User b = user("rb4@test.local", "RETAILER");
        mvc.perform(send(HttpMethod.POST, "/api/products", a, json("Mine", 10, 1, ""))).andExpect(status().isCreated());
        Product p = firstProductOf(a);

        mvc.perform(send(HttpMethod.PUT, "/api/products/update/" + p.getId(), b, json("Stolen", 1, 1, "")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/delete/" + p.getId()).header("Authorization", bearer(b)))
                .andExpect(status().isForbidden());

        Product after = productRepo.findById(p.getId()).orElseThrow();
        assertEquals("Mine", after.getName());
        assertEquals(10.0, after.getPrice());
    }

    @Test
    void owner_canUpdateAndDelete_thenGets404() throws Exception {
        User a = user("ra5@test.local", "RETAILER");
        mvc.perform(send(HttpMethod.POST, "/api/products", a, json("Old", 10, 1, ""))).andExpect(status().isCreated());
        Product p = firstProductOf(a);

        mvc.perform(send(HttpMethod.PUT, "/api/products/update/" + p.getId(), a,
                        json("New", 12.5, 0, "\"ownerId\":999")))
                .andExpect(status().isOk());
        Product after = productRepo.findById(p.getId()).orElseThrow();
        assertEquals("New", after.getName());
        assertEquals(0, after.getQuantity());
        assertEquals(a.getId(), after.getOwnerId());

        mvc.perform(delete("/api/products/delete/" + p.getId()).header("Authorization", bearer(a))).andExpect(status().isOk());
        mvc.perform(get("/api/products/" + p.getId())).andExpect(status().isNotFound());
    }

    @Test
    void missingProducts_return404_notServerError() throws Exception {
        User a = user("ra6@test.local", "RETAILER");
        mvc.perform(get("/api/products/nope")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/nope/image")).andExpect(status().isNotFound());
        mvc.perform(send(HttpMethod.PUT, "/api/products/update/nope", a, json("x", 1, 1, ""))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/products/delete/nope").header("Authorization", bearer(a))).andExpect(status().isNotFound());
    }

    @Test
    void legacyProductWithoutOwner_isReadableButNotModifiable() throws Exception {
        User a = user("ra7@test.local", "RETAILER");
        Product legacy = productRepo.save(new Product(null, "d", "c", "Legacy", 1, 1));
        mvc.perform(get("/api/products/" + legacy.getId())).andExpect(status().isOk());
        mvc.perform(send(HttpMethod.PUT, "/api/products/update/" + legacy.getId(), a, json("x", 1, 1, "")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/delete/" + legacy.getId()).header("Authorization", bearer(a)))
                .andExpect(status().isForbidden());
        assertTrue(productRepo.existsById(legacy.getId()));
    }

    @Test
    void invalidProductData_returns400() throws Exception {
        User a = user("ra8@test.local", "RETAILER");
        mvc.perform(send(HttpMethod.POST, "/api/products", a, json("x", -1, 1, ""))).andExpect(status().isBadRequest());
        mvc.perform(send(HttpMethod.POST, "/api/products", a, json("x", 1, -5, ""))).andExpect(status().isBadRequest());
        mvc.perform(send(HttpMethod.POST, "/api/products", a, json("  ", 1, 1, ""))).andExpect(status().isBadRequest());
        assertEquals(0, productRepo.findByOwnerId(a.getId()).size());
    }

    @Test
    void imageUpload_isOwnerOnly_andServedPublicly() throws Exception {
        User a = user("ra9@test.local", "RETAILER");
        User b = user("rb9@test.local", "RETAILER");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        mvc.perform(multipart("/api/products")
                        .file(new MockMultipartFile("product", "", "application/json", json("Pic", 1, 1, "").getBytes()))
                        .file(new MockMultipartFile("imageFile", "a.png", "image/png", png))
                        .header("Authorization", bearer(a)))
                .andExpect(status().isCreated());
        Product p = firstProductOf(a);

        mvc.perform(get("/api/products/" + p.getId() + "/image"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(png));

        mvc.perform(multipart(HttpMethod.PUT, "/api/products/update/" + p.getId())
                        .file(new MockMultipartFile("product", "", "application/json", json("Pic", 1, 1, "").getBytes()))
                        .file(new MockMultipartFile("imageFile", "evil.png", "image/png", new byte[]{9, 9}))
                        .header("Authorization", bearer(b)))
                .andExpect(status().isForbidden());
        assertArrayEquals(png, productRepo.findById(p.getId()).orElseThrow().getImageData());
    }

    // ---- wholesaler catalogue ------------------------------------------

    @Test
    void wholesalerProducts_followTheSameRules() throws Exception {
        User a = user("wa@test.local", "WHOLESALER");
        User b = user("wb@test.local", "WHOLESALER");
        mvc.perform(send(HttpMethod.POST, "/api/wholesaler/products", a, json("Bulk", 9, 100, "\"ownerId\":" + b.getId())))
                .andExpect(status().isCreated());
        List<WholesalerProduct> mine = wholesalerRepo.findByOwnerId(a.getId());
        assertEquals(1, mine.size());
        assertEquals(0, wholesalerRepo.findByOwnerId(b.getId()).size());
        String id = mine.get(0).getId();

        mvc.perform(get("/api/wholesaler/products/mine").header("Authorization", bearer(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/wholesaler/products/mine").header("Authorization", bearer(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(send(HttpMethod.PUT, "/api/wholesaler/products/update/" + id, b, json("Stolen", 1, 1, "")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/wholesaler/products/delete/" + id).header("Authorization", bearer(b)))
                .andExpect(status().isForbidden());
        assertEquals("Bulk", wholesalerRepo.findById(id).orElseThrow().getName());

        mvc.perform(send(HttpMethod.PUT, "/api/wholesaler/products/update/" + id, a, json("Bulk2", 8, 90, "")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/wholesaler/products/delete/" + id).header("Authorization", bearer(a)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/wholesaler/products/" + id)).andExpect(status().isNotFound());
    }
}
