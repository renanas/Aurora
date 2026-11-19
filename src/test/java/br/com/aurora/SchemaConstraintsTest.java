package br.com.aurora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante que as regras que dependem do banco existem de verdade.
 * Cada teste provoca no máximo UMA violação (no PostgreSQL ela aborta a transação) e é revertido ao final.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class SchemaConstraintsTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 9, 28, 9, 0);

    /** Dona de todos os registros (desde a V4, paciente, recorrência e sessão têm dona). */
    private static final String OWNER = "00000000-0000-0000-0000-00000000a0a0";

    @Autowired
    JdbcTemplate jdbc;

    /** Roda dentro da transação do teste: a conta some no rollback. */
    @BeforeEach
    void createOwner() {
        jdbc.update("INSERT INTO app_user (id, email, password_hash, display_name, role) "
                + "VALUES ('" + OWNER + "', 'dona@aurora.test', 'hash', 'Dona', 'PSYCHOLOGIST')");
    }

    // ---------- helpers ----------

    private UUID patient() {
        return jdbc.queryForObject(
                "INSERT INTO patient (psychologist_id, name, whatsapp, session_price) VALUES ('" + OWNER + "', 'Maria', '5511999998888', 180) RETURNING id",
                UUID.class);
    }

    private UUID recurrence(UUID patientId, LocalDate endsOn) {
        return jdbc.queryForObject(
                "INSERT INTO recurrence (psychologist_id, patient_id, anchor_date, start_time, frequency, ends_on) "
                        + "VALUES ('" + OWNER + "', ?, '2026-09-28', '09:00', 'WEEKLY', ?) RETURNING id",
                UUID.class, patientId, endsOn);
    }

    private UUID session(UUID patientId, LocalDateTime startsAt) {
        return jdbc.queryForObject(
                "INSERT INTO therapy_session (psychologist_id, patient_id, starts_at, amount) VALUES ('" + OWNER + "', ?, ?, 180) RETURNING id",
                UUID.class, patientId, startsAt);
    }

    private UUID rescheduledSession(UUID patientId, UUID originalId, String status) {
        String cancellation = "CANCELLED".equals(status) ? "'EARLY'" : "NULL";
        return jdbc.queryForObject(
                "INSERT INTO therapy_session (psychologist_id, patient_id, starts_at, amount, rescheduled_from_id, status, cancellation_type) "
                        + "VALUES ('" + OWNER + "', ?, '2026-09-30 10:00', 180, ?, ?, " + cancellation + ") RETURNING id",
                UUID.class, patientId, originalId, status);
    }

    // ---------- testes ----------

    @Test
    @DisplayName("ends_at é calculado pelo banco: 09:00 + 50 min = 09:50")
    void endsAtIsStartPlusDuration() {
        UUID id = session(patient(), NINE);

        LocalDateTime endsAt = jdbc.queryForObject(
                "SELECT ends_at FROM therapy_session WHERE id = ?", LocalDateTime.class, id);

        assertThat(endsAt).isEqualTo(NINE.plusMinutes(50));
    }

    @Test
    @DisplayName("sessões no mesmo horário são permitidas (conflito é aviso, não bloqueio)")
    void overlappingSessionsAreAllowed() {
        UUID patientId = patient();
        session(patientId, NINE);
        session(patientId, NINE);

        Integer total = jdbc.queryForObject("SELECT count(*) FROM therapy_session", Integer.class);
        assertThat(total).isEqualTo(2);
    }

    @Test
    @DisplayName("um paciente não pode ter duas recorrências ativas")
    void onlyOneActiveRecurrencePerPatient() {
        UUID patientId = patient();
        recurrence(patientId, null);

        assertThatThrownBy(() -> recurrence(patientId, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("recorrência encerrada não impede uma nova ativa")
    void endedRecurrenceAllowsNewActiveOne() {
        UUID patientId = patient();
        recurrence(patientId, LocalDate.of(2026, 12, 14));

        UUID second = recurrence(patientId, null);

        assertThat(second).isNotNull();
    }

    @Test
    @DisplayName("a mesma ocorrência não pode ser gerada duas vezes (idempotência)")
    void sameOccurrenceCannotBeGeneratedTwice() {
        UUID patientId = patient();
        UUID recurrenceId = recurrence(patientId, null);
        String sql = "INSERT INTO therapy_session (psychologist_id, patient_id, recurrence_id, occurrence_date, starts_at, amount) "
                + "VALUES ('" + OWNER + "', ?, ?, '2026-09-28', '2026-09-28 09:00', 180)";
        jdbc.update(sql, patientId, recurrenceId);

        assertThatThrownBy(() -> jdbc.update(sql, patientId, recurrenceId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("cancelar exige o tipo de cancelamento")
    void cancelledRequiresCancellationType() {
        UUID patientId = patient();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO therapy_session (psychologist_id, patient_id, starts_at, amount, status) "
                        + "VALUES ('" + OWNER + "', ?, ?, 180, 'CANCELLED')", patientId, NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("tipo de cancelamento só existe em sessão cancelada")
    void cancellationTypeOnlyWhenCancelled() {
        UUID patientId = patient();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO therapy_session (psychologist_id, patient_id, starts_at, amount, status, cancellation_type) "
                        + "VALUES ('" + OWNER + "', ?, ?, 180, 'SCHEDULED', 'LATE')", patientId, NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("uma falta pode estar paga: status e pagamento são independentes")
    void noShowCanBePaid() {
        UUID patientId = patient();

        jdbc.update("INSERT INTO therapy_session (psychologist_id, patient_id, starts_at, amount, status, payment_status, paid_at) "
                + "VALUES ('" + OWNER + "', ?, ?, 180, 'NO_SHOW', 'PAID', now())", patientId, NINE);

        String status = jdbc.queryForObject("SELECT status FROM therapy_session", String.class);
        String payment = jdbc.queryForObject("SELECT payment_status FROM therapy_session", String.class);
        assertThat(status).isEqualTo("NO_SHOW");
        assertThat(payment).isEqualTo("PAID");
    }

    @Test
    @DisplayName("pago exige data de pagamento")
    void paidRequiresPaidAt() {
        UUID patientId = patient();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO therapy_session (psychologist_id, patient_id, starts_at, amount, payment_status) "
                        + "VALUES ('" + OWNER + "', ?, ?, 180, 'PAID')", patientId, NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("uma sessão original só pode ter um reagendamento vigente")
    void onlyOneLiveRescheduleAllowed() {
        UUID patientId = patient();
        UUID original = session(patientId, NINE);
        rescheduledSession(patientId, original, "SCHEDULED");

        assertThatThrownBy(() -> rescheduledSession(patientId, original, "SCHEDULED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("reagendamento cancelado (desfeito) não impede um novo reagendamento")
    void cancelledRescheduleDoesNotBlockNewOne() {
        UUID patientId = patient();
        UUID original = session(patientId, NINE);
        rescheduledSession(patientId, original, "CANCELLED");

        UUID second = rescheduledSession(patientId, original, "SCHEDULED");

        assertThat(second).isNotNull();
    }
}
