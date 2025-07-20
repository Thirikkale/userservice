package com.thirikkale.userservice.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.thirikkale.userservice.exception.CustomExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthService {

    private final FirebaseAuth firebaseAuth;//auth

    public FirebaseUserInfo extractUserInfo(String firebaseIdToken) {
        try {
            log.info("Verifying Firebase ID token");

            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(firebaseIdToken);

            String phoneNumber = (String) decodedToken.getClaims().get("phone_number");
            String email = decodedToken.getEmail();
            String name = decodedToken.getName();
            String picture = decodedToken.getPicture();
            boolean emailVerified = decodedToken.isEmailVerified();

            // Firebase phone auth automatically verifies phone numbers
            boolean phoneVerified = phoneNumber != null && !phoneNumber.isEmpty();

            log.info("Firebase token verified successfully for phone: {}", phoneNumber);

            return FirebaseUserInfo.builder()
                    .uid(decodedToken.getUid())
                    .phoneNumber(phoneNumber)
                    .email(email)
                    .name(name)
                    .picture(picture)
                    .emailVerified(emailVerified)
                    .phoneVerified(phoneVerified)
                    .build();

        } catch (FirebaseAuthException e) {
            log.error("Firebase token verification failed: {}", e.getMessage());
            throw new CustomExceptions.InvalidTokenException("Invalid Firebase ID token: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during Firebase token verification: {}", e.getMessage());
            throw new CustomExceptions.InvalidTokenException("Token verification failed");
        }
    }

    public boolean validateFirebaseToken(String firebaseIdToken) {
        try {
            firebaseAuth.verifyIdToken(firebaseIdToken);
            return true;
        } catch (FirebaseAuthException e) {
            log.error("Firebase token validation failed: {}", e.getMessage());
            return false;
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FirebaseUserInfo {
        private String uid;
        private String phoneNumber;
        private String email;
        private String name;
        private String picture;
        private boolean emailVerified;
        private boolean phoneVerified;
    }
}