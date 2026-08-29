package com.bodeul.core.appointment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentPublicCodeGeneratorTests {

    @Test
    void generatedCodeUsesThePublicContract() {
        AppointmentPublicCodeGenerator generator = new AppointmentPublicCodeGenerator();

        for (int index = 0; index < 100; index++) {
            String code = generator.nextCode();
            assertThat(code).matches("^BD-[A-Z0-9]{6}$");
        }
    }
}
