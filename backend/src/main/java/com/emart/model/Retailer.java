package com.emart.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;

@Entity
public class Retailer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Foreign Key mapping: Links this Retailer profile to a base User account
    @OneToOne 
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user; 

    private String name;
    private String shopName;
    private String location;        // Shop location (may be derived from User location)

    // --- Constructors ---
    public Retailer() {}

    public Retailer(User user, String name, String shopName, String location) {
        this.user = user;
        this.name = name;
        this.shopName = shopName;
        this.location = location;
    }

    // --- Getters and Setters ---
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}