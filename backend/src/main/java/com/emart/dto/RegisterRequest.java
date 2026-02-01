package com.emart.dto;

public class RegisterRequest {
    private String email;
    private String password;
    private String role;
    private String location;
    
    private CustomerDetailsDTO customerDetails;
    private RetailerDetailsDTO retailerDetails;
    private WholesalerDetailsDTO wholesalerDetails;

    // Getters and Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public CustomerDetailsDTO getCustomerDetails() { return customerDetails; }
    public void setCustomerDetails(CustomerDetailsDTO customerDetails) { this.customerDetails = customerDetails; }

    public RetailerDetailsDTO getRetailerDetails() { return retailerDetails; }
    public void setRetailerDetails(RetailerDetailsDTO retailerDetails) { this.retailerDetails = retailerDetails; }

    public WholesalerDetailsDTO getWholesalerDetails() { return wholesalerDetails; }
    public void setWholesalerDetails(WholesalerDetailsDTO wholesalerDetails) { this.wholesalerDetails = wholesalerDetails; }
}

