package com.thirikkale.userservice.util;

import org.springframework.stereotype.Component;

/**
 * Utility class for generating human-readable IDs
 * Examples: R00001, D00001, T00001, etc.
 */
@Component
public class ReadableIdGenerator {

    /**
     * Generate a readable ID with the given prefix and number
     * 
     * @param prefix  The prefix (e.g., "R" for Rider, "D" for Driver)
     * @param number  The sequential number
     * @param padding The total length of the numeric part (default 5)
     * @return Formatted readable ID (e.g., "R00001")
     */
    public static String generate(String prefix, Long number, int padding) {
        String format = String.format("%%s%%0%dd", padding);
        return String.format(format, prefix, number);
    }

    /**
     * Generate a readable ID with default padding of 5 digits
     */
    public static String generate(String prefix, Long number) {
        return generate(prefix, number, 5);
    }

    /**
     * Extract the numeric part from a readable ID
     * 
     * @param readableId The readable ID (e.g., "R00001")
     * @param prefix     The expected prefix
     * @return The numeric value
     */
    public static Long extractNumber(String readableId, String prefix) {
        if (readableId == null || !readableId.startsWith(prefix)) {
            throw new IllegalArgumentException("Invalid readable ID format");
        }
        String numberPart = readableId.substring(prefix.length());
        return Long.parseLong(numberPart);
    }

    /**
     * Validate a readable ID format
     */
    public static boolean isValid(String readableId, String expectedPrefix) {
        if (readableId == null || readableId.isEmpty()) {
            return false;
        }
        if (!readableId.startsWith(expectedPrefix)) {
            return false;
        }
        String numberPart = readableId.substring(expectedPrefix.length());
        try {
            Long.parseLong(numberPart);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
