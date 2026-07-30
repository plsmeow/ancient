package tech.onetap.util.cloud;

import java.security.SecureRandom;

public final class CodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodeGenerator() {
    }

    public static String randomCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        sb.append('-');
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static boolean isValid(String code) {
        if (code == null || code.length() != 9) return false;
        if (code.charAt(4) != '-') return false;
        for (int i = 0; i < 4; i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) return false;
        }
        for (int i = 5; i < 9; i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) return false;
        }
        return true;
    }
}
