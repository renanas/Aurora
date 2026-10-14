package br.com.aurora.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OccurrenceCalculatorTest {

    // 2026-09-28 é uma segunda-feira.
    private static final LocalDate ANCHOR = LocalDate.of(2026, 9, 28);

    private static LocalDate d(int month, int day) {
        return LocalDate.of(2026, month, day);
    }

    @Test
    @DisplayName("semanal: uma data a cada 7 dias, começando na âncora")
    void weekly() {
        assertThat(OccurrenceCalculator.occurrences(ANCHOR, Frequency.WEEKLY, ANCHOR, d(10, 19), null))
                .containsExactly(d(9, 28), d(10, 5), d(10, 12), d(10, 19));
    }

    @Test
    @DisplayName("quinzenal: uma data a cada 14 dias, contadas a partir da âncora")
    void biweekly() {
        assertThat(OccurrenceCalculator.occurrences(ANCHOR, Frequency.BIWEEKLY, ANCHOR, d(11, 9), null))
                .containsExactly(d(9, 28), d(10, 12), d(10, 26), d(11, 9));
    }

    @Test
    @DisplayName("quinzenal mantém o alinhamento da âncora mesmo quando a consulta começa depois dela")
    void biweeklyKeepsAnchorAlignment() {
        // Quarta 07/10 está entre as ocorrências 28/09 e 12/10: a próxima é 12/10, não 14/10 nem 05/10.
        assertThat(OccurrenceCalculator.occurrences(ANCHOR, Frequency.BIWEEKLY, d(10, 7), d(10, 31), null))
                .containsExactly(d(10, 12), d(10, 26));
    }

    @Test
    @DisplayName("os limites são inclusivos: uma ocorrência exatamente em 'from' ou em 'to' entra")
    void inclusiveBounds() {
        assertThat(OccurrenceCalculator.occurrences(ANCHOR, Frequency.WEEKLY, d(10, 5), d(10, 12), null))
                .containsExactly(d(10, 5), d(10, 12));
    }

    @Test
    @DisplayName("nunca devolve datas antes da âncora")
    void neverBeforeAnchor() {
        assertThat(OccurrenceCalculator.occurrences(ANCHOR, Frequency.WEEKLY, d(9, 1), d(10, 5), null))
                .containsExactly(d(9, 28), d(10, 5));
    }

    @Test
    @DisplayName("âncora depois do fim da janela: nada a gerar")
    void anchorAfterWindow() {
        assertThat(OccurrenceCalculator.occurrences(ANCHOR, Frequency.WEEKLY, d(9, 1), d(9, 20), null)).isEmpty();
    }

    @Test
    @DisplayName("a data de encerramento limita a geração (inclusive)")
    void endsOnLimits() {
        assertThat(OccurrenceCalculator.occurrences(ANCHOR, Frequency.WEEKLY, ANCHOR, d(10, 31), d(10, 5)))
                .containsExactly(d(9, 28), d(10, 5));
    }

    @Test
    @DisplayName("encerrada antes do início da janela: nada a gerar")
    void endedBeforeWindow() {
        assertThat(OccurrenceCalculator.occurrences(ANCHOR, Frequency.WEEKLY, d(10, 20), d(10, 31), d(10, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("âncora no passado distante não percorre o histórico: só devolve datas da janela")
    void farPastAnchor() {
        LocalDate oldAnchor = LocalDate.of(2020, 1, 6); // segunda-feira
        assertThat(OccurrenceCalculator.occurrences(oldAnchor, Frequency.WEEKLY, d(10, 5), d(10, 19), null))
                .containsExactly(d(10, 5), d(10, 12), d(10, 19));
    }
}
