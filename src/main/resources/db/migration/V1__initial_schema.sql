-- V1: pacientes, recorrências e sessões

CREATE TABLE patient (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           text         NOT NULL
                   CONSTRAINT ck_patient_name CHECK (length(btrim(name)) BETWEEN 1 AND 150),
    whatsapp       text         NOT NULL
                   CONSTRAINT ck_patient_whatsapp CHECK (whatsapp ~ '^[0-9]{10,15}$'),
    session_price  numeric(10,2) NOT NULL
                   CONSTRAINT ck_patient_price CHECK (session_price >= 0),
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0
);

CREATE TABLE recurrence (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id   uuid        NOT NULL REFERENCES patient(id),
    anchor_date  date        NOT NULL,   -- 1ª ocorrência; define o dia da semana
    start_time   time        NOT NULL,
    frequency    text        NOT NULL
                 CONSTRAINT ck_recurrence_frequency CHECK (frequency IN ('WEEKLY','BIWEEKLY')),
    ends_on      date,                   -- NULL = ativa
    created_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_recurrence_period CHECK (ends_on IS NULL OR ends_on >= anchor_date)
);

-- No máximo uma recorrência ativa por paciente
CREATE UNIQUE INDEX uq_recurrence_one_active_per_patient
    ON recurrence (patient_id) WHERE ends_on IS NULL;

CREATE INDEX idx_recurrence_patient ON recurrence (patient_id);

CREATE TABLE therapy_session (
    id                  uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          uuid          NOT NULL REFERENCES patient(id),
    recurrence_id       uuid          REFERENCES recurrence(id),
    occurrence_date     date,
    rescheduled_from_id uuid          REFERENCES therapy_session(id),

    starts_at           timestamp     NOT NULL,   -- horário local (America/Sao_Paulo)
    duration_minutes    smallint      NOT NULL DEFAULT 50
                        CONSTRAINT ck_session_duration CHECK (duration_minutes > 0),
    ends_at             timestamp     GENERATED ALWAYS AS
                        (starts_at + make_interval(mins => duration_minutes::int)) STORED,

    status              text          NOT NULL DEFAULT 'SCHEDULED'
                        CONSTRAINT ck_session_status CHECK (status IN
                          ('SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW','RESCHEDULED')),
    cancellation_type   text
                        CONSTRAINT ck_session_cancel_type CHECK (cancellation_type IN ('EARLY','LATE')),

    payment_status      text          NOT NULL DEFAULT 'PENDING'
                        CONSTRAINT ck_session_payment CHECK (payment_status IN ('PENDING','PAID')),
    paid_at             timestamptz,
    amount              numeric(10,2) NOT NULL
                        CONSTRAINT ck_session_amount CHECK (amount >= 0),

    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    version             bigint        NOT NULL DEFAULT 0,

    -- sessão gerada: os dois campos juntos ou nenhum
    CONSTRAINT ck_session_generated_pair
        CHECK ((recurrence_id IS NULL) = (occurrence_date IS NULL)),
    -- tipo de cancelamento obrigatório se, e somente se, CANCELLED
    CONSTRAINT ck_session_cancel_consistency
        CHECK ((status = 'CANCELLED') = (cancellation_type IS NOT NULL)),
    -- paid_at preenchido se, e somente se, PAID
    CONSTRAINT ck_session_paid_consistency
        CHECK ((payment_status = 'PAID') = (paid_at IS NOT NULL)),
    -- origem é exclusiva: gerada OU resultado de reagendamento OU avulsa
    CONSTRAINT ck_session_single_origin
        CHECK (recurrence_id IS NULL OR rescheduled_from_id IS NULL),
    CONSTRAINT ck_session_not_self_reschedule
        CHECK (rescheduled_from_id IS DISTINCT FROM id),

    -- idempotência da geração (NULLs não colidem: avulsas ficam livres)
    CONSTRAINT uq_session_recurrence_occurrence UNIQUE (recurrence_id, occurrence_date)
);

-- Uma original só pode ter UM reagendamento vigente
CREATE UNIQUE INDEX uq_session_one_live_reschedule
    ON therapy_session (rescheduled_from_id)
    WHERE rescheduled_from_id IS NOT NULL AND status <> 'CANCELLED';

CREATE INDEX idx_session_starts_at         ON therapy_session (starts_at);
CREATE INDEX idx_session_patient_starts_at ON therapy_session (patient_id, starts_at);
CREATE INDEX idx_session_rescheduled_from  ON therapy_session (rescheduled_from_id);
