package com.bodeul.core.session;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionSessionPropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CompanionSessionConfiguration.class);

    @Test
    void preConsultationEnforcementDefaultsToFalse() {
        contextRunner.run(context -> assertThat(
                context.getBean(CompanionSessionProperties.class)
                        .isPreConsultationEnforcement())
                .isFalse());
    }

    @Test
    void preConsultationEnforcementCanBeEnabledExplicitly() {
        contextRunner
                .withPropertyValues("bodeul.session.pre-consultation-enforcement=true")
                .run(context -> assertThat(
                        context.getBean(CompanionSessionProperties.class)
                                .isPreConsultationEnforcement())
                        .isTrue());
    }

    @Test
    void completionEnforcementDefaultsToFalse() {
        contextRunner.run(context -> assertThat(
                context.getBean(CompanionSessionProperties.class)
                        .isCompletionEnforcement())
                .isFalse());
    }

    @Test
    void completionEnforcementCanBeEnabledExplicitly() {
        contextRunner
                .withPropertyValues("bodeul.session.completion-enforcement=true")
                .run(context -> assertThat(
                        context.getBean(CompanionSessionProperties.class)
                                .isCompletionEnforcement())
                        .isTrue());
    }
}
