package com.emart.model;

// This is a Data Transfer Object (DTO) used by the AuthService to process
// validated data received from a social media platform.
public class SocialUser {
    
    private String socialId;       // Unique ID assigned by the provider (e.g., Google user ID)
    private String email;          // The validated email address
    private String name;           // The user's full name
    private String provider;       // "GOOGLE" or "FACEBOOK"

    // --- Constructors ---
    public SocialUser() {}

    public SocialUser(String socialId, String email, String name, String provider) {
        this.socialId = socialId;
        this.email = email;
        this.name = name;
        this.provider = provider;
    }

    // --- Getters and Setters ---

    public String getSocialId() {
        return socialId;
    }

    public void setSocialId(String socialId) {
        this.socialId = socialId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}