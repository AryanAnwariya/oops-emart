package com.emart.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The React app uses client-side routes. When one of them is opened directly (refresh, shared link, bookmark) the
 * server must hand back index.html instead of a 404, otherwise the deployed site breaks on reload.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaForwardingTest {

    @Autowired MockMvc mvc;

    @Test
    void everyClientSideRoute_isForwardedToTheReactApp() throws Exception {
        for (String path : new String[]{
                "/", "/products", "/login", "/create", "/edit",
                "/product/3a179318-2b92-4ca4-82e4-594e5895b0c5",
                "/retailer/dashboard", "/retailer/low-stock",
                "/wholesaler/dashboard", "/wholesaler/product/abc"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void unknownApiRoutesAreNeverSwallowedByTheSpaFallback() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
    }
}
