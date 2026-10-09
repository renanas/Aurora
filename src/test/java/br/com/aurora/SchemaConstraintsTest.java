package br.com.aurora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
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

    @Autowired
    JdbcTemplate jdbc;

    // ---------- helpers ----------

    private UUID patient() {
        return jdbc.queryForObject(
                "INSERT INTO patient (name, whatsapp, session_price) VALUES ('Maria', '5511999998888', 180) RETURNING id",
                UUID.class);
    }

    private UUID recurrence(UUID patientId, LocalDate endsOn) {
        return jdbc.queryForObject(
                "INSERT INTO recurrence (patient_id, anchor_date, start_time, frequency, ends_on) "
                        + "VALUES (?, '2026-09-28', '09:00', 'WEEKLY', ?) RETURNING id",
                UUID.class, patientId, endsOn);
    }

    private UUID session(UUID patientId, LocalDateTime startsAt) {
        return jdbc.queryForObject(
                "INSERT INTO therapy_session (patient_id, starts_at, amount) VALUES (?, ?, 180) RETURNING id",
                UUID.class, patientId, startsAt);
    }

    private UUID rescheduledSession(UUID patientId, UUID originalId, String status) {
        String cancellation = "CANCELLED".equals(status) ? "'EARLY'" : "NULL";
        return jdbc.queryForObject(
                "INSERT INTO therapy_session (patient_id, starts_at, amount, rescheduled_from_id, status, cancellation_type) "
                        + "VALUES (?, '2026-09-30 10:00', 180, ?, ?, " + cancellation + ") RETURNING id",
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
        UUID p = patient();
        session(p, NINE);
        session(p, NINE);

        Integer total = jdbc.queryForObject("SELECT count(*) FROM therapy_session", Integer.class);
        assertThat(total).isEqualTo(2);
    }

    @Test
    @DisplayName("um paciente não pode ter duas recorrências ativas")
    void onlyOneActiveRecurrencePerPatient() {
        UUID p = patient();
        recurrence(p, null);

        assertThatThrownBy(() -> recurrence(p, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("recorrência encerrada não impede uma nova ativa")
    void endedRecurrenceAllowsNewActiveOne() {
        UUID p = patient();
        recurrence(p, LocalDate.of(2026, 12, 14));

        UUID second = recurrence(p, null);

        assertThat(second).isNotNull();
    }

    @Test
    @DisplayName("a mesma ocorrência não pode ser gerada duas vezes (idempotência)")
    void sameOccurrenceCannotBeGeneratedTwice() {
        UUID p = patient();
        UUID r = recurrence(p, null);
        String sql = "INSERT INTO therapy_session (patient_id, recurrence_id, occurrence_date, starts_at, amount) "
                + "VALUES (?, ?, '2026-09-28', '2026-09-28 09:00', 180)";
        jdbc.update(sql, p, r);

        assertThatThrownBy(() -> jdbc.update(sql, p, r))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("cancelar exige o tipo de cancelamento")
    void cancelledRequiresCancellationType() {
        UUID p = patient();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO therapy_session (patient_id, starts_at, amount, status) "
                        + "VALUES (?, ?, 180, 'CANCELLED')", p, NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("tipo de cancelamento só existe em sessão cancelada")
    void cancellationTypeOnlyWhenCancelled() {
        UUID p = patient();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO therapy_session (patient_id, starts_at, amount, status, cancellation_type) "
                        + "VALUES (?, ?, 180, 'SCHEDULED', 'LATE')", p, NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("uma falta pode estar paga: status e pagamento são independentes")
    void noShowCanBePaid() {
        UUID p = patient();

        jdbc.update("INSERT INTO therapy_session (patient_id, starts_at, amount, status, payment_status, paid_at) "
                + "VALUES (?, ?, 180, 'NO_SHOW', 'PAID', now())", p, NINE);

        String status = jdbc.queryForObject("SELECT status FROM therapy_session", String.class);
        String payment = jdbc.queryForObject("SELECT payment_status FROM therapy_session", String.class);
        assertThat(status).isEqualTo("NO_SHOW");
        assertThat(payment).isEqualTo("PAID");
    }

    @Test
    @DisplayName("pago exige data de pagamento")
    void paidRequiresPaidAt() {
        UUID p = patient();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO therapy_session (patient_id, starts_at, amount, payment_status) "
                        + "VALUES (?, ?, 180, 'PAID')", p, NINE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("uma sessão original só pode ter um reagendamento vigente")
    void onlyOneLiveRescheduleAllowed() {
        UUID p = patient();
        UUID original = session(p, NINE);
        rescheduledSession(p, original, "SCHEDULED");

        assertThatThrownBy(() -> rescheduledSession(p, original, "SCHEDULED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("reagendamento cancelado (desfeito) não impede um novo reagendamento")
    void cancelledRescheduleDoesNotBlockNewOne() {
        UUID p = patient();
        UUID original = session(p, NINE);
        rescheduledSession(p, original, "CANCELLED");

        UUID second = rescheduledSession(p, original, "SCHEDULED");

        assertThat(second).isNotNull();
    }
}
