package com.thirikkale.userservice.config;

import com.thirikkale.userservice.security.JwtAuthenticationFilter;
import com.thirikkale.userservice.security.ServiceAuthFilter;
// REMOVE Lombok import if not needed
// import lombok.RequiredArgsConstructor;
// import org.springframework.beans.factory.annotation.Value; // REMOVE if serviceAuthSecret field is removed
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider; // Keep this
// REMOVE unused imports
// import org.springframework.security.authentication.AuthenticationManager;
// import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
// import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // Keep if filterChain uses it
import org.springframework.security.config.http.SessionCreationPolicy;
// import org.springframework.security.core.userdetails.UserDetailsService; // REMOVE this import
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // REMOVE this import
// import org.springframework.security.crypto.password.PasswordEncoder; // REMOVE this import
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
// import org.springframework.context.annotation.Lazy; // Can likely remove @Lazy

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
// @EnableMethodSecurity(prePostEnabled = true)
@EnableMethodSecurity(prePostEnabled = false)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ServiceAuthFilter serviceAuthFilter;
    // REMOVE UserDetailsService injection
    private final AuthenticationProvider authenticationProvider; // Keep this

    // Explicit Constructor
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ServiceAuthFilter serviceAuthFilter,
                          AuthenticationProvider authenticationProvider) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.serviceAuthFilter = serviceAuthFilter;
        this.authenticationProvider = authenticationProvider;
    }

    // --- Keep only ONE SecurityFilterChain Bean ---
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable) // Use disable method reference
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // Authentication endpoints - Allow all
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/login/password",
                                "/api/v1/auth/login/firebase")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/auth/register").permitAll()

                        // Rider & driver registration (Public)
                        .requestMatchers("/api/v1/riders/register").permitAll()
                        .requestMatchers("/api/v1/drivers/register").permitAll()

                        // Admin auth endpoints
                        .requestMatchers("/api/v1/auth/admin/login",
                                "/api/v1/auth/admin/register-super-admin",
                                "/api/v1/auth/admin/register",
                                "/api/v1/auth/admin/verify-email",
                                "/api/v1/auth/admin/verify-email-page",
                                "/api/v1/auth/admin/resend-verification",
                                "/api/v1/auth/admin/users")  // Temporarily public for testing
                        .permitAll()

                        // Internal service endpoints - Allow without authentication for admin service
                        .requestMatchers("/api/v1/drivers/**", "/api/v1/riders/**", "/api/v1/users/**",
                                "/api/v1/vehicles/**")
                        .permitAll()

                        // Static file serving - Allow uploads folder
                        .requestMatchers("/uploads/**").permitAll()

                        // Public docs & health
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                        .requestMatchers("/health", "/info").permitAll()

                        // Error handling
                        .requestMatchers("/error").permitAll()

                        // Specific User Role Access (Examples)
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/riders/{id}").hasRole("RIDER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/drivers/{id}").hasRole("DRIVER")

                        // All other requests require authentication
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Keep corsConfigurationSource bean - ensure Allowed Methods includes PATCH if needed elsewhere
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*")); // Consider restricting in production
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")); // Added PATCH
        configuration.setAllowedHeaders(List.of("*")); // Use List.of("*")
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Optional: Set max age

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply CORS to all paths
        return source;
    }

    // REMOVE serviceAuthSecret field - It's used in ServiceAuthFilter, not directly here
    // @Value("${service.auth.secret:default-service-secret-12345}")
    // private String serviceAuthSecret;

    // REMOVE the duplicate filterChain bean method

    // REMOVE passwordEncoder() bean - MOVED to ApplicationConfig

    // REMOVE authenticationProvider() bean - MOVED to ApplicationConfig

    // REMOVE authenticationManager() bean - MOVED to ApplicationConfig
}