package com.thirikkale.userservice.util;

import com.thirikkale.userservice.exception.CustomExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@Slf4j
public class PhoneNumberValidator {

    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    private static final Pattern SRI_LANKA_PATTERN = Pattern.compile("^(?:\\+94|0094|0)?([1-9]\\d{8})$");

    public String formatToE164(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new CustomExceptions.InvalidPhoneNumberException("Phone number cannot be empty");
        }

        String cleanedNumber = phoneNumber.replaceAll("[\\s()-]", "");

        // If already in E164 format
        if (E164_PATTERN.matcher(cleanedNumber).matches()) {
            return cleanedNumber;
        }

        // Handle Sri Lankan numbers
        if (SRI_LANKA_PATTERN.matcher(cleanedNumber).matches()) {
            String nationalNumber = cleanedNumber.replaceAll("^(?:\\+94|0094|0)", "");
            return "+94" + nationalNumber;
        }

        log.error("Invalid phone number format: {}", phoneNumber);
        throw new CustomExceptions.InvalidPhoneNumberException("Invalid phone number format: " + phoneNumber);
    }

    public boolean isValidE164(String phoneNumber) {
        return phoneNumber != null && E164_PATTERN.matcher(phoneNumber).matches();
    }

    public boolean isSriLankanNumber(String phoneNumber) {
        if (phoneNumber == null) return false;
        String cleaned = phoneNumber.replaceAll("[\\s()-]", "");
        return SRI_LANKA_PATTERN.matcher(cleaned).matches() || cleaned.startsWith("+94");
    }
}