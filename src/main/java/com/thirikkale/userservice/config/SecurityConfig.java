package com.thirikkale.userservice.config;

import com.thirikkale.userservice.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
// @EnableMethodSecurity(prePostEnabled = true)
@EnableMethodSecurity(prePostEnabled = false)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // Authentication endpoints - Allow all
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/login/password",
                                "/api/v1/auth/login/firebase")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/auth/register").permitAll()

                        // Rider & driver registration AND login - FIXED
                        .requestMatchers("/api/v1/riders/register", "/api/v1/riders/login").permitAll()
                        .requestMatchers("/api/v1/drivers/register", "/api/v1/drivers/login").permitAll()

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
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/health", "/info").permitAll()

                        // Error handling
                        .requestMatchers("/error").permitAll()

                        // All other requests require authentication
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}