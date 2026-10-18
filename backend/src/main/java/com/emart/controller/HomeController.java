package com.emart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // Serve the React app (bundled into static/) and let react-router
    // handle client-side routes on refresh / direct links.
    @GetMapping({"/", "/products", "/login", "/create", "/retailer/**", "/wholesaler/**"})
    public String spa() {
        return "forward:/index.html";
    }
}
