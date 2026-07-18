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

    @Test
    void operationalMigrationAddsOnlyTheRequiredCoreWritePrivileges() throws IOException {
        ClassPathResource resource = new ClassPathResource(
                "db/migration/V4__promote_appointment_requests_to_operational.sql"
        );
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("add column name text not null default ''")
                .contains("add column email text not null default ''")
                .contains("add column phone text not null default ''")
                .contains("with participant_profiles as")
                .contains("update bodeul.app_users as app_user")
                .contains("alter column firestore_id drop not null")
                .contains("alter column imported_at drop not null")
                .contains("add column client_request_id uuid")
                .contains("add column version bigint not null default 0")
                .contains("unique index uq_appointment_requests_requester_client_request")
                .contains("grant insert, update on table bodeul.appointment_requests to bodeul_core_runtime")
                .contains("revoke insert, update, delete on table bodeul.appointment_requests from bodeul_admin_runtime")
                .contains("to bodeul_core_runtime")
                .contains("with check (true)")
                .doesNotContain("grant delete")
                .doesNotContain("to anon")
                .doesNotContain("to authenticated")
                .doesNotContain("to service_role")
                .doesNotContain("password");
    }
}
