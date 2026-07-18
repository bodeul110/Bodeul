package com.bodeul.core.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DefaultAppointmentServiceWiringTests {

    @Test
    void springContextCreatesServiceWithRuntimeDependencies() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("database");
            context.registerBean(
                    AppointmentRepository.class,
                    () -> mock(AppointmentRepository.class));
            context.registerBean(
                    AppUserProfileRepository.class,
                    () -> mock(AppUserProfileRepository.class));
            context.register(DefaultAppointmentService.class);

            context.refresh();

            assertThat(context.getBean(AppointmentService.class))
                    .isInstanceOf(DefaultAppointmentService.class);
        }
    }
}
