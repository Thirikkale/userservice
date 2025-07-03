package com.thirikkale.userservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    public void sendOtp(String phoneNumber, String otp) {
        // In production, integrate with SMS providers like:
        // - Twilio
        // - AWS SNS
        // - Firebase Cloud Messaging
        // - Local SMS gateways

        log.info("📱 SMS SENT TO: {} | OTP: {} | Message: Your Thirikkale verification code is: {}",
                phoneNumber, otp, otp);

        // Mock implementation - in real scenario, call external SMS API
        // Example for Twilio:
        // twilioClient.sendMessage(phoneNumber, "Your Thirikkale verification code is: " + otp);
    }
}