package com.emart.config;

import com.emart.security.JsonSecurityErrorHandlers;
import com.emart.security.JwtUserConverter;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Provides BCryptPasswordEncoder bean for password hashing
     * Used by AuthService for password encryption/verification
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures Spring Security to allow custom authentication
     * Disables default security chain since we're using custom AuthService
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                   JwtUserConverter jwtUserConverter) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Stateless bearer-token API: no cookies, so no CSRF surface
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable CORS
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Stateless sessions
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll() // register, login, OTP, (disabled) social login

                // Owner-scoped listings must be matched before the public GET rules below.
                .requestMatchers("/api/products/mine").hasRole("RETAILER")
                .requestMatchers("/api/wholesaler/products/mine").hasRole("WHOLESALER")

                // Public browsing (read-only)
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/wholesaler/products", "/api/wholesaler/products/**").permitAll()

                // Any write is role-restricted
                .requestMatchers("/api/products/**").hasRole("RETAILER")
                .requestMatchers("/api/wholesaler/products/**").hasRole("WHOLESALER")

                .requestMatchers("/api/**").authenticated() // secure by default for any other API route

                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").denyAll()
                .anyRequest().permitAll() // SPA static files and client-side routes
            )
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtUserConverter))
                .authenticationEntryPoint(JsonSecurityErrorHandlers.entryPoint())
                .accessDeniedHandler(JsonSecurityErrorHandlers.accessDenied())
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(JsonSecurityErrorHandlers.entryPoint())
                .accessDeniedHandler(JsonSecurityErrorHandlers.accessDenied())
            )
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())); // Allow H2 console frames

        return http.build();
    }

    /**
     * CORS configuration to allow frontend to access the API
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:4173"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

