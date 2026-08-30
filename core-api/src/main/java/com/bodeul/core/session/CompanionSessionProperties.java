package com.bodeul.core.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bodeul.session")
public class CompanionSessionProperties {

    private boolean preConsultationEnforcement;
    private boolean completionEnforcement;

    public boolean isPreConsultationEnforcement() {
        return preConsultationEnforcement;
    }

    public void setPreConsultationEnforcement(boolean preConsultationEnforcement) {
        this.preConsultationEnforcement = preConsultationEnforcement;
    }

    public boolean isCompletionEnforcement() {
        return completionEnforcement;
    }

    public void setCompletionEnforcement(boolean completionEnforcement) {
        this.completionEnforcement = completionEnforcement;
    }
}
