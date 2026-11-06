-- V3: contas de usuárias (login pelo banco).
-- role e must_change_password já existem aqui para as próximas entregas (contas e troca de senha)
-- não precisarem de nova migração.

CREATE TABLE app_user (
    id                   uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email                text         NOT NULL
                         CONSTRAINT ck_app_user_email
                         CHECK (email = lower(btrim(email)) AND length(email) BETWEEN 3 AND 254),
    password_hash        text         NOT NULL,
    display_name         text         NOT NULL
                         CONSTRAINT ck_app_user_display_name
                         CHECK (length(btrim(display_name)) BETWEEN 1 AND 150),
    role                 text         NOT NULL
                         CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'PSYCHOLOGIST')),
    active               boolean      NOT NULL DEFAULT true,
    must_change_password boolean      NOT NULL DEFAULT false,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);
