-- V5: cadastro aberto com aprovação do administrador.
-- Quem se cadastra pela tela pública nasce com approved = false e não entra até ser aprovada.
-- As contas que já existem (o administrador e as criadas antes) continuam aprovadas.

ALTER TABLE app_user ADD COLUMN approved boolean NOT NULL DEFAULT true;
