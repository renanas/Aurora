#!/bin/sh
# Usuário da aplicação no modo casa: sem DDL, só manipula dados (menor privilégio).
# Igual ao docker/initdb do desenvolvimento, mas com a senha vinda do .env.casa.
# Roda uma única vez, na criação do volume.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v app_password="$AURORA_APP_DB_PASSWORD" <<'EOSQL'
CREATE ROLE aurora_app LOGIN PASSWORD :'app_password';

GRANT CONNECT ON DATABASE aurora TO aurora_app;
GRANT USAGE ON SCHEMA public TO aurora_app;

ALTER DEFAULT PRIVILEGES FOR ROLE aurora_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO aurora_app;
EOSQL
