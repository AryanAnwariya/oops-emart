package com.emart.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;

@Entity
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Foreign Key mapping: Links this Customer profile to a base User account
    @OneToOne 
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user; 

    private String name;
    private String phone;          // Contact phone number
    private String address;        // Customer delivery address

    // --- Constructors ---
    public Customer() {}

    public Customer(User user, String name, String phone, String address) {
        this.user = user;
        this.name = name;
        this.phone = phone;
        this.address = address;
    }
    private String firstName; // <-- Add this field
    private String lastName;  // <-- Add this field

    // ... existing constructors/getters

    // --- NEW Setters ---
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}

