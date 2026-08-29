package com.bodeul.core.appointment;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
final class AppointmentPublicCodeGenerator {

    static final String PREFIX = "BD-";
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int RANDOM_LENGTH = 6;

    private final SecureRandom secureRandom;

    AppointmentPublicCodeGenerator() {
        this(new SecureRandom());
    }

    AppointmentPublicCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    String nextCode() {
        StringBuilder code = new StringBuilder(PREFIX);
        for (int index = 0; index < RANDOM_LENGTH; index++) {
            code.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
