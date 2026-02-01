package com.emart.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String email;          // Used for login/authentication
    private String password;       // Should be stored as a hashed value!
    private String role;           // Stores "CUSTOMER", "RETAILER", or "WHOLESALER"
    private String location;       // For Google API location integration

    // --- Constructors ---
    public User() {}

    public User(String email, String password, String role, String location) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.location = location;
    }

    // --- Getters and Setters ---

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
