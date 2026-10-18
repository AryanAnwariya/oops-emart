package com.emart.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** 401 / 403 responses with a small JSON body the React app can read. No internal details are exposed. */
public final class JsonSecurityErrorHandlers {

    private JsonSecurityErrorHandlers() {}

    public static AuthenticationEntryPoint entryPoint() {
        return (HttpServletRequest req, HttpServletResponse res, org.springframework.security.core.AuthenticationException e) -> {
            res.setHeader("WWW-Authenticate", "Bearer");
            write(res, HttpStatus.UNAUTHORIZED, "Authentication required or token invalid/expired.");
        };
    }

    public static AccessDeniedHandler accessDenied() {
        return (req, res, e) -> write(res, HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
    }

    private static void write(HttpServletResponse res, HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
