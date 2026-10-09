-- Usuário da aplicação: sem DDL, só manipula dados (menor privilégio).
-- Roda uma única vez, na criação do volume.
CREATE ROLE aurora_app LOGIN PASSWORD 'aurora_app_dev';

GRANT CONNECT ON DATABASE aurora TO aurora_app;
GRANT USAGE ON SCHEMA public TO aurora_app;

-- Toda tabela criada pelas migrações já nasce acessível à aplicação.
ALTER DEFAULT PRIVILEGES FOR ROLE aurora_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO aurora_app;
