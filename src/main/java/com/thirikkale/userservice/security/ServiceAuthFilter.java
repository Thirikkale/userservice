package com.thirikkale.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceAuthFilter extends OncePerRequestFilter {

    @Value("${service.auth.secret}") // Use the same property name as in rideservice
    private String expectedServiceSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String serviceName = request.getHeader("X-Service-Name");
        final String serviceSecret = request.getHeader("X-Service-Secret");

        // Only process if headers are present and context is not already authenticated
        if (serviceName != null && serviceSecret != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.info("Attempting authentication via Service Secret header from: {}", serviceName);

            if (serviceSecret.equals(expectedServiceSecret)) {
                log.info("Service Secret validated successfully for {}", serviceName);
                // Grant a specific "SERVICE" role
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        serviceName, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_SERVICE")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                log.warn("Invalid Service Secret received from {}", serviceName);
                // Optionally deny immediately:
                // response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Service Secret");
                // return;
            }
        } else if (serviceName != null || serviceSecret != null) {
            log.debug("Skipping ServiceAuthFilter: Context already authenticated or missing headers.");
        }

        filterChain.doFilter(request, response);
    }
}