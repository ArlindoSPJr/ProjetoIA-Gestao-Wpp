-- Callback do Flyway (executa apos cada migrate, a cada subida da aplicacao).
-- Liga o RLS na flyway_schema_history (RN16). Nao pode ser uma migration versionada:
-- o Flyway segura lock nessa tabela durante a migration e o ALTER travaria sozinho.
-- Aqui o lock ja foi liberado. E idempotente.
ALTER TABLE flyway_schema_history ENABLE ROW LEVEL SECURITY;
