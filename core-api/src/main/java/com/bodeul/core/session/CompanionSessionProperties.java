package com.bodeul.core.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bodeul.session")
public class CompanionSessionProperties {

    private boolean preConsultationEnforcement;

    public boolean isPreConsultationEnforcement() {
        return preConsultationEnforcement;
    }

    public void setPreConsultationEnforcement(boolean preConsultationEnforcement) {
        this.preConsultationEnforcement = preConsultationEnforcement;
    }
}
