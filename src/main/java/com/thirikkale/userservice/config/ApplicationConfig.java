package com.thirikkale.userservice.config;

import com.thirikkale.userservice.repository.UserRepository; // Assuming CustomUserDetailsService uses this
import com.thirikkale.userservice.security.CustomUserDetailsService; // Assuming this is your implementation
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    // Inject UserRepository or whatever CustomUserDetailsService needs
    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        // Return your CustomUserDetailsService instance
        return new CustomUserDetailsService(userRepository);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        // Use the userDetailsService() bean defined above
        authProvider.setUserDetailsService(userDetailsService());
        // Use the passwordEncoder() bean defined below
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}