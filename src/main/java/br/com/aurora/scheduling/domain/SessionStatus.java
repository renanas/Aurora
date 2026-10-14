package br.com.aurora.scheduling.domain;

public enum SessionStatus {
    SCHEDULED,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    RESCHEDULED;

    /** Ocupa o horário na agenda: só estas sessões entram na conta de sobreposição. */
    public boolean occupiesSlot() {
        return this == SCHEDULED || this == CONFIRMED || this == COMPLETED || this == NO_SHOW;
    }
}
