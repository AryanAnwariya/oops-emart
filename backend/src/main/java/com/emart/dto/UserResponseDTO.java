package com.emart.dto;

public class UserResponseDTO {
    private Integer id;
    private String email;
    private String role;
    private String location;

    public UserResponseDTO() {}

    public UserResponseDTO(Integer id, String email, String role, String location) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.location = location;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}

