-- V4: cada paciente, recorrência e sessão pertence a uma psicóloga (app_user).
-- O dono é COPIADO para recorrência e sessão (o módulo de agenda só enxerga o paciente pelo id).
-- As chaves estrangeiras compostas (id, psychologist_id) fazem o banco recusar uma sessão ou recorrência
-- cujo dono seja diferente do dono do paciente (ou da recorrência / sessão original a que se ligam).

-- Sem dono não há como preencher: ainda não existe dado real. Em desenvolvimento, zere o banco.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM patient) OR EXISTS (SELECT 1 FROM recurrence)
            OR EXISTS (SELECT 1 FROM therapy_session) THEN
        RAISE EXCEPTION 'V4: existem pacientes, recorrencias ou sessoes sem dono. Em desenvolvimento, zere o banco: docker compose down -v';
    END IF;
END $$;

-- patient
ALTER TABLE patient ADD COLUMN psychologist_id uuid NOT NULL;
ALTER TABLE patient ADD CONSTRAINT fk_patient_psychologist
    FOREIGN KEY (psychologist_id) REFERENCES app_user (id);
ALTER TABLE patient ADD CONSTRAINT uq_patient_id_psychologist UNIQUE (id, psychologist_id);
CREATE INDEX idx_patient_psychologist ON patient (psychologist_id, active);

-- recurrence
ALTER TABLE recurrence ADD COLUMN psychologist_id uuid NOT NULL;
ALTER TABLE recurrence ADD CONSTRAINT fk_recurrence_psychologist
    FOREIGN KEY (psychologist_id) REFERENCES app_user (id);
ALTER TABLE recurrence ADD CONSTRAINT uq_recurrence_id_psychologist UNIQUE (id, psychologist_id);
ALTER TABLE recurrence ADD CONSTRAINT fk_recurrence_patient_owner
    FOREIGN KEY (patient_id, psychologist_id) REFERENCES patient (id, psychologist_id);

-- therapy_session
ALTER TABLE therapy_session ADD COLUMN psychologist_id uuid NOT NULL;
ALTER TABLE therapy_session ADD CONSTRAINT fk_session_psychologist
    FOREIGN KEY (psychologist_id) REFERENCES app_user (id);
ALTER TABLE therapy_session ADD CONSTRAINT uq_session_id_psychologist UNIQUE (id, psychologist_id);
ALTER TABLE therapy_session ADD CONSTRAINT fk_session_patient_owner
    FOREIGN KEY (patient_id, psychologist_id) REFERENCES patient (id, psychologist_id);
-- recurrence_id e rescheduled_from_id são opcionais: com NULL a chave composta não é verificada.
ALTER TABLE therapy_session ADD CONSTRAINT fk_session_recurrence_owner
    FOREIGN KEY (recurrence_id, psychologist_id) REFERENCES recurrence (id, psychologist_id);
ALTER TABLE therapy_session ADD CONSTRAINT fk_session_rescheduled_from_owner
    FOREIGN KEY (rescheduled_from_id, psychologist_id) REFERENCES therapy_session (id, psychologist_id);
CREATE INDEX idx_session_psychologist_starts_at ON therapy_session (psychologist_id, starts_at);
