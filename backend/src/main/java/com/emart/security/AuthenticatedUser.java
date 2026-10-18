package com.emart.security;

/** The verified principal placed in the SecurityContext. Built from the signed token and the database, never from request input. */
public record AuthenticatedUser(Integer id, String email, String role) {
}
