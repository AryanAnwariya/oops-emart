package com.emart.controller;
import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String welcomeScreen() {
        return "Welcome to E-Mart!";
    }
}

