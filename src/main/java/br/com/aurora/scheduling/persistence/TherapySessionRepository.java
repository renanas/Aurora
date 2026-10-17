package br.com.aurora.scheduling.persistence;

import br.com.aurora.scheduling.domain.PaymentStatus;
import br.com.aurora.scheduling.domain.SessionStatus;
import br.com.aurora.scheduling.domain.TherapySession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TherapySessionRepository extends JpaRepository<TherapySession, UUID> {

    /** Sessões que começam em [from, to). */
    List<TherapySession> findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            LocalDateTime from, LocalDateTime to);

    long countByRecurrenceId(UUID recurrenceId);

    /** O reagendamento vigente (não cancelado) de uma sessão original, se houver. */
    Optional<TherapySession> findFirstByRescheduledFromIdAndStatusNot(UUID originalId, SessionStatus status);

    @Query("""
            select s.occurrenceDate from TherapySession s
            where s.recurrenceId = :recurrenceId
              and s.occurrenceDate between :fromDate and :toDate
            """)
    List<LocalDate> findOccurrenceDates(@Param("recurrenceId") UUID recurrenceId,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** Candidatas a serem apagadas ao trocar/encerrar uma recorrência (o horário original é conferido em Java). */
    @Query("""
            select s from TherapySession s
            where s.recurrenceId = :recurrenceId
              and s.occurrenceDate >= :fromDate
              and s.startsAt > :after
              and s.status = :status
              and s.paymentStatus = :paymentStatus
            """)
    List<TherapySession> findFutureUntouched(@Param("recurrenceId") UUID recurrenceId,
            @Param("fromDate") LocalDate fromDate, @Param("after") LocalDateTime after,
            @Param("status") SessionStatus status, @Param("paymentStatus") PaymentStatus paymentStatus);

    /** Sessões futuras de um paciente cujo valor acompanha o valor do paciente. */
    @Query("""
            select s from TherapySession s
            where s.patientId = :patientId
              and s.startsAt > :after
              and s.status in :statuses
              and s.paymentStatus = :paymentStatus
            """)
    List<TherapySession> findPriceUpdatable(@Param("patientId") UUID patientId,
            @Param("after") LocalDateTime after, @Param("statuses") List<SessionStatus> statuses,
            @Param("paymentStatus") PaymentStatus paymentStatus);
}
