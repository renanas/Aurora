package br.com.aurora.shared.time;

import java.time.ZoneId;

/** Constantes do sistema. Não são dados: mudar um destes valores é uma decisão de produto. */
public final class SystemConstants {

    /** Único fuso do sistema. Horários de sessão são "horário de parede" neste fuso. */
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    /** Duração fixa de cada sessão (a sessão guarda a duração usada na criação). */
    public static final int SESSION_DURATION_MINUTES = 50;

    /** Até quantas semanas à frente as sessões recorrentes são geradas. */
    public static final int GENERATION_HORIZON_WEEKS = 8;

    /** Maior intervalo aceito na consulta de agenda. */
    public static final int MAX_AGENDA_RANGE_DAYS = 100;

    private SystemConstants() {
    }
}
