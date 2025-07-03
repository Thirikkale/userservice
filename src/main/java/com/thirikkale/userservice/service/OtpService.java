package com.thirikkale.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${otp.max-attempts:3}")
    private int maxAttempts;

    private static final String ATTEMPT_PREFIX = "firebase_attempts:";
    private static final String RATE_LIMIT_PREFIX = "firebase_rate_limit:";

    /**
     * Track Firebase verification attempts to prevent abuse
     */
    public void incrementFirebaseAttempts(String phoneNumber) {
        String key = ATTEMPT_PREFIX + phoneNumber;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, otpExpirationMinutes, TimeUnit.MINUTES);
        log.info("Firebase attempt incremented for phone: {}", phoneNumber);
    }

    /**
     * Get current attempt count for Firebase verification
     */
    public int getFirebaseAttempts(String phoneNumber) {
        String key = ATTEMPT_PREFIX + phoneNumber;
        Integer attempts = (Integer) redisTemplate.opsForValue().get(key);
        return attempts != null ? attempts : 0;
    }

    /**
     * Reset Firebase attempts after successful verification
     */
    public void resetFirebaseAttempts(String phoneNumber) {
        String key = ATTEMPT_PREFIX + phoneNumber;
        redisTemplate.delete(key);
        log.info("Firebase attempts reset for phone: {}", phoneNumber);
    }

    /**
     * Check if Firebase verification attempts exceeded
     */
    public boolean hasExceededFirebaseAttempts(String phoneNumber) {
        return getFirebaseAttempts(phoneNumber) >= maxAttempts;
    }

    /**
     * Get remaining Firebase attempts
     */
    public int getRemainingFirebaseAttempts(String phoneNumber) {
        return Math.max(0, maxAttempts - getFirebaseAttempts(phoneNumber));
    }

    /**
     * Set rate limit for Firebase token generation (prevent spam)
     */
    public void setFirebaseRateLimit(String phoneNumber, int limitMinutes) {
        String key = RATE_LIMIT_PREFIX + phoneNumber;
        redisTemplate.opsForValue().set(key, "limited", limitMinutes, TimeUnit.MINUTES);
        log.info("Firebase rate limit set for phone: {} for {} minutes", phoneNumber, limitMinutes);
    }

    /**
     * Check if phone number is rate limited
     */
    public boolean isFirebaseRateLimited(String phoneNumber) {
        String key = RATE_LIMIT_PREFIX + phoneNumber;
        return redisTemplate.hasKey(key);
    }

    /**
     * Get rate limit expiry time in minutes
     */
    public long getFirebaseRateLimitExpiryMinutes(String phoneNumber) {
        String key = RATE_LIMIT_PREFIX + phoneNumber;
        Long expiry = redisTemplate.getExpire(key, TimeUnit.MINUTES);
        return expiry != null ? expiry : 0;
    }

    /**
     * Cache successful Firebase verification temporarily
     */
    public void cacheFirebaseVerification(String phoneNumber, String firebaseUid) {
        String key = "firebase_verified:" + phoneNumber;
        redisTemplate.opsForValue().set(key, firebaseUid, 10, TimeUnit.MINUTES);
        log.info("Firebase verification cached for phone: {}", phoneNumber);
    }

    /**
     * Check if Firebase verification is cached
     */
    public boolean isFirebaseVerificationCached(String phoneNumber) {
        String key = "firebase_verified:" + phoneNumber;
        return redisTemplate.hasKey(key);
    }

    /**
     * Get cached Firebase UID
     */
    public String getCachedFirebaseUid(String phoneNumber) {
        String key = "firebase_verified:" + phoneNumber;
        return (String) redisTemplate.opsForValue().get(key);
    }

    /**
     * Clear cached Firebase verification
     */
    public void clearFirebaseVerificationCache(String phoneNumber) {
        String key = "firebase_verified:" + phoneNumber;
        redisTemplate.delete(key);
        log.info("Firebase verification cache cleared for phone: {}", phoneNumber);
    }
}