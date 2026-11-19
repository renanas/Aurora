package br.com.aurora.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.aurora.shared.error.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatientTest {
    private static final UUID OWNER = UUID.randomUUID();


    private static final Instant T1 = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-02T12:00:00Z");

    private Patient maria() {
        return new Patient(OWNER, "  Maria Silva ", "(11) 99999-8888", new BigDecimal("180"), T1);
    }

    @Test
    @DisplayName("novo paciente nasce ativo, com nome aparado, WhatsApp só com dígitos e prefixo 55 e valor com 2 casas")
    void newPatientIsNormalized() {
        Patient patient = maria();

        assertThat(patient.getName()).isEqualTo("Maria Silva");
        assertThat(patient.getWhatsapp()).isEqualTo("5511999998888");
        assertThat(patient.getSessionPrice()).isEqualByComparingTo("180.00");
        assertThat(patient.getSessionPrice().scale()).isEqualTo(2);
        assertThat(patient.isActive()).isTrue();
    }

    @Test
    @DisplayName("nome vazio, nome longo demais e valor negativo são rejeitados")
    void rejectsInvalidData() {
        assertThatThrownBy(() -> new Patient(OWNER, "   ", "11999998888", BigDecimal.TEN, T1))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Patient(OWNER, "a".repeat(151), "11999998888", BigDecimal.TEN, T1))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Patient(OWNER, "Maria", "11999998888", new BigDecimal("-1"), T1))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("update informa se o valor mudou: mesmo valor com outra escala não conta como mudança")
    void updateReportsPriceChange() {
        Patient patient = maria();

        assertThat(patient.update("Maria S.", "11999998888", new BigDecimal("180.00"), T2)).isFalse();
        assertThat(patient.getName()).isEqualTo("Maria S.");
        assertThat(patient.update("Maria S.", "11999998888", new BigDecimal("200"), T2)).isTrue();
        assertThat(patient.getSessionPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("update inválido não altera nada")
    void invalidUpdateChangesNothing() {
        Patient patient = maria();

        assertThatThrownBy(() -> patient.update("Outro Nome", "123", new BigDecimal("999"), T2))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(patient.getName()).isEqualTo("Maria Silva");
        assertThat(patient.getSessionPrice()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("inativar e reativar são idempotentes e dizem se houve mudança")
    void deactivateAndActivateAreIdempotent() {
        Patient patient = maria();

        assertThat(patient.deactivate(T2)).isTrue();
        assertThat(patient.deactivate(T2)).isFalse();
        assertThat(patient.isActive()).isFalse();

        assertThat(patient.activate(T2)).isTrue();
        assertThat(patient.activate(T2)).isFalse();
        assertThat(patient.isActive()).isTrue();
    }

    @Test
    @DisplayName("o paciente guarda a psicóloga dona e não existe sem ela")
    void keepsTheOwner() {
        assertThat(maria().getPsychologistId()).isEqualTo(OWNER);
        assertThatThrownBy(() -> new Patient(null, "Maria", "11999998888", BigDecimal.TEN, T1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("atualizar os dados não muda a dona")
    void updateKeepsTheOwner() {
        Patient patient = maria();

        patient.update("Maria Souza", "11988887777", new BigDecimal("200"), T2);

        assertThat(patient.getPsychologistId()).isEqualTo(OWNER);
    }
}
