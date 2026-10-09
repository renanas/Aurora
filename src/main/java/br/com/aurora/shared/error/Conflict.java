package br.com.aurora.shared.error;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Uma sessão que se sobrepõe ao horário pedido. O nome do paciente existe aqui só para
 * ir no corpo da resposta ao cliente; por isso o toString o omite, para que ele não vaze em logs.
 */
public record Conflict(UUID sessionId, String patientName, LocalDateTime startsAt, LocalDateTime endsAt) {

    @Override
    public String toString() {
        return "Conflict[sessionId=" + sessionId + "]";
    }
}
