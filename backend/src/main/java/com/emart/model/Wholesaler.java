package com.emart.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;

@Entity
public class Wholesaler {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Foreign Key mapping: Links this Wholesaler profile to a base User account
    @OneToOne 
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user; 

    private String name;
    private String businessId; // Unique business registration number
    private String contact;

    // --- Constructors ---
    public Wholesaler() {}

    public Wholesaler(User user, String name, String businessId, String contact) {
        this.user = user;
        this.name = name;
        this.businessId = businessId;
        this.contact = contact;
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

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }
}