-- RLS ligado nas tabelas da aplicacao no schema public, sem policies (RN16).
-- Sem FORCE: a aplicacao conecta como dona das tabelas, que ignora o RLS, entao
-- continua com acesso total. Sem policies: os papeis anon e authenticated da API REST
-- do Supabase ficam sem acesso a nada. Toda tabela nova no public liga o RLS na propria migration.
-- A flyway_schema_history nao entra aqui (trava no lock do proprio Flyway); ver afterMigrate.sql.
ALTER TABLE empresa ENABLE ROW LEVEL SECURITY;
ALTER TABLE numero_whatsapp ENABLE ROW LEVEL SECURITY;
ALTER TABLE produto ENABLE ROW LEVEL SECURITY;
ALTER TABLE opcao ENABLE ROW LEVEL SECURITY;
ALTER TABLE valor_opcao ENABLE ROW LEVEL SECURITY;
ALTER TABLE variante ENABLE ROW LEVEL SECURITY;
ALTER TABLE variante_valor ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE carrinho ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_carrinho ENABLE ROW LEVEL SECURITY;
