package com.bodeul.core.appointment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentRequestsMigrationContractTests {

    @Test
    void appointmentRequestsMigrationCreatesAPrivateReadModel() throws IOException {
        ClassPathResource resource = new ClassPathResource(
                "db/migration/V3__create_appointment_requests.sql"
        );
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("create table bodeul.appointment_requests")
                .contains("references bodeul.app_users (id)")
                .contains("unique (firestore_id)")
                .contains("check (requester_role in ('PATIENT', 'GUARDIAN'))")
                .contains("check (status in ('REQUESTED', 'MATCHED', 'IN_PROGRESS', 'COMPLETED', 'CANCELED'))")
                .contains("check (jsonb_typeof(reminder_stages) = 'array')")
                .contains("revoke all on table bodeul.appointment_requests from public, anon, authenticated, service_role")
                .contains("grant select on table bodeul.appointment_requests to bodeul_core_runtime, bodeul_admin_runtime")
                .contains("alter table bodeul.appointment_requests enable row level security")
                .contains("to bodeul_core_runtime")
                .contains("to bodeul_admin_runtime")
                .doesNotContain("grant insert")
                .doesNotContain("grant update")
                .doesNotContain("grant delete")
                .doesNotContain("password");
    }

    @Test
    void appointmentRequestsMigrationCoversTheCurrentFirestoreContract() throws IOException {
        ClassPathResource resource = new ClassPathResource(
                "db/migration/V3__create_appointment_requests.sql"
        );
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("patient_email text")
                .contains("guardian_email text")
                .contains("appointment_at_epoch_millis bigint")
                .contains("patient_condition_summary text")
                .contains("medication_summary text")
                .contains("mobility_support_code text")
                .contains("trip_type_code text")
                .contains("manager_gender_preference_code text")
                .contains("coupon_code text")
                .contains("payment_provider_label text")
                .contains("reminder_stages jsonb")
                .contains("imported_at timestamptz");
    }
}
