package br.com.aurora.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.aurora.shared.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WhatsappTest {

    @Test
    @DisplayName("guarda só os dígitos, ignorando a formatação digitada")
    void keepsOnlyDigits() {
        assertThat(Whatsapp.of("+55 (11) 99999-8888").digits()).isEqualTo("5511999998888");
    }

    @Test
    @DisplayName("aceita de 10 a 15 dígitos")
    void acceptsBoundaries() {
        assertThat(Whatsapp.of("1199998888").digits()).hasSize(10);
        assertThat(Whatsapp.of("551199998888777").digits()).hasSize(15);
    }

    @Test
    @DisplayName("rejeita 9 e 16 dígitos")
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> Whatsapp.of("119999888")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Whatsapp.of("5511999988887777")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("rejeita vazio e nulo")
    void rejectsBlank() {
        assertThatThrownBy(() -> Whatsapp.of("")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Whatsapp.of(null)).isInstanceOf(BusinessRuleException.class);
    }
}
