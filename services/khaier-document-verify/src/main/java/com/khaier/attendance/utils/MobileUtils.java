package com.khaier.attendance.utils;

public final class MobileUtils {

    private MobileUtils() {}

    public static String normalizeSaudiMobile(String mobile) {
        if (mobile == null) return "";
        String digits = mobile.replaceAll("[^0-9]", "");

        if (digits.startsWith("00966")) {
            digits = digits.substring(2);
        }

        if (digits.startsWith("966") && digits.length() == 12) {
            return digits;
        }

        if (digits.startsWith("05") && digits.length() == 10) {
            return "966" + digits.substring(1);
        }

        if (digits.startsWith("5") && digits.length() == 9) {
            return "966" + digits;
        }

        return digits;
    }

    public static String last4(String normalizedMobile) {
        if (normalizedMobile == null || normalizedMobile.length() < 4) return null;
        return normalizedMobile.substring(normalizedMobile.length() - 4);
    }
}
