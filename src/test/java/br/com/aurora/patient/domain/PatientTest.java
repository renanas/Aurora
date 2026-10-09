package br.com.aurora.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.aurora.shared.error.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PatientTest {

    private static final Instant T1 = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-02T12:00:00Z");

    private Patient maria() {
        return new Patient("  Maria Silva ", "(11) 99999-8888", new BigDecimal("180"), T1);
    }

    @Test
    @DisplayName("novo paciente nasce ativo, com nome aparado, WhatsApp só com dígitos e valor com 2 casas")
    void newPatientIsNormalized() {
        Patient p = maria();

        assertThat(p.getName()).isEqualTo("Maria Silva");
        assertThat(p.getWhatsapp()).isEqualTo("11999998888");
        assertThat(p.getSessionPrice()).isEqualByComparingTo("180.00");
        assertThat(p.getSessionPrice().scale()).isEqualTo(2);
        assertThat(p.isActive()).isTrue();
    }

    @Test
    @DisplayName("nome vazio, nome longo demais e valor negativo são rejeitados")
    void rejectsInvalidData() {
        assertThatThrownBy(() -> new Patient("   ", "11999998888", BigDecimal.TEN, T1))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Patient("a".repeat(151), "11999998888", BigDecimal.TEN, T1))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Patient("Maria", "11999998888", new BigDecimal("-1"), T1))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("update informa se o valor mudou: mesmo valor com outra escala não conta como mudança")
    void updateReportsPriceChange() {
        Patient p = maria();

        assertThat(p.update("Maria S.", "11999998888", new BigDecimal("180.00"), T2)).isFalse();
        assertThat(p.getName()).isEqualTo("Maria S.");
        assertThat(p.update("Maria S.", "11999998888", new BigDecimal("200"), T2)).isTrue();
        assertThat(p.getSessionPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("update inválido não altera nada")
    void invalidUpdateChangesNothing() {
        Patient p = maria();

        assertThatThrownBy(() -> p.update("Outro Nome", "123", new BigDecimal("999"), T2))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(p.getName()).isEqualTo("Maria Silva");
        assertThat(p.getSessionPrice()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("inativar e reativar são idempotentes e dizem se houve mudança")
    void deactivateAndActivateAreIdempotent() {
        Patient p = maria();

        assertThat(p.deactivate(T2)).isTrue();
        assertThat(p.deactivate(T2)).isFalse();
        assertThat(p.isActive()).isFalse();

        assertThat(p.activate(T2)).isTrue();
        assertThat(p.activate(T2)).isFalse();
        assertThat(p.isActive()).isTrue();
    }
}
