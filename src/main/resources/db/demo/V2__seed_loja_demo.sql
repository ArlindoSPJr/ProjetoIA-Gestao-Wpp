INSERT INTO empresa (razao_social, nome_fantasia, documento, segmento)
VALUES ('Loja Demo LTDA', 'Loja Demo', '11222333000181', 'vestuário');

INSERT INTO numero_whatsapp (empresa_id, phone_number_id, numero_exibicao, apelido)
SELECT id, 'simulador-loja-demo', '5511900000000', 'Vendas' FROM empresa WHERE documento = '11222333000181';

-- Camisa Básica
INSERT INTO produto (empresa_id, nome, descricao, categoria)
SELECT id, 'Camisa Básica', 'Camisa de algodão, gola redonda', 'camisa' FROM empresa WHERE documento = '11222333000181';
INSERT INTO opcao (produto_id, nome, posicao) VALUES ((SELECT id FROM produto WHERE nome = 'Camisa Básica'), 'Cor', 0);
INSERT INTO valor_opcao (opcao_id, valor, posicao)
SELECT id, 'Preta', 0 FROM opcao WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Camisa Básica') AND nome = 'Cor';
INSERT INTO valor_opcao (opcao_id, valor, posicao)
SELECT id, 'Branca', 1 FROM opcao WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Camisa Básica') AND nome = 'Cor';
INSERT INTO opcao (produto_id, nome, posicao) VALUES ((SELECT id FROM produto WHERE nome = 'Camisa Básica'), 'Tamanho', 1);
INSERT INTO valor_opcao (opcao_id, valor, posicao)
SELECT id, 'M', 0 FROM opcao WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Camisa Básica') AND nome = 'Tamanho';
INSERT INTO valor_opcao (opcao_id, valor, posicao)
SELECT id, 'G', 1 FROM opcao WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Camisa Básica') AND nome = 'Tamanho';
WITH nova AS (
    INSERT INTO variante (produto_id, preco, quantidade_estoque) VALUES ((SELECT id FROM produto WHERE nome = 'Camisa Básica'), 49.90, 12) RETURNING id
)
INSERT INTO variante_valor (variante_id, valor_opcao_id)
SELECT nova.id, vo.id FROM nova, valor_opcao vo JOIN opcao op ON op.id = vo.opcao_id
WHERE op.produto_id = (SELECT id FROM produto WHERE nome = 'Camisa Básica') AND ((op.nome = 'Cor' AND vo.valor = 'Preta') OR (op.nome = 'Tamanho' AND vo.valor = 'M'));
WITH nova AS (
    INSERT INTO variante (produto_id, preco, quantidade_estoque) VALUES ((SELECT id FROM produto WHERE nome = 'Camisa Básica'), 49.90, 0) RETURNING id
)
INSERT INTO variante_valor (variante_id, valor_opcao_id)
SELECT nova.id, vo.id FROM nova, valor_opcao vo JOIN opcao op ON op.id = vo.opcao_id
WHERE op.produto_id = (SELECT id FROM produto WHERE nome = 'Camisa Básica') AND ((op.nome = 'Cor' AND vo.valor = 'Preta') OR (op.nome = 'Tamanho' AND vo.valor = 'G'));
WITH nova AS (
    INSERT INTO variante (produto_id, preco, quantidade_estoque) VALUES ((SELECT id FROM produto WHERE nome = 'Camisa Básica'), 49.90, 8) RETURNING id
)
INSERT INTO variante_valor (variante_id, valor_opcao_id)
SELECT nova.id, vo.id FROM nova, valor_opcao vo JOIN opcao op ON op.id = vo.opcao_id
WHERE op.produto_id = (SELECT id FROM produto WHERE nome = 'Camisa Básica') AND ((op.nome = 'Cor' AND vo.valor = 'Branca') OR (op.nome = 'Tamanho' AND vo.valor = 'M'));

-- Camisa Polo
INSERT INTO produto (empresa_id, nome, descricao, categoria)
SELECT id, 'Camisa Polo', 'Camisa polo de piquet', 'camisa' FROM empresa WHERE documento = '11222333000181';
INSERT INTO opcao (produto_id, nome, posicao) VALUES ((SELECT id FROM produto WHERE nome = 'Camisa Polo'), 'Cor', 0);
INSERT INTO valor_opcao (opcao_id, valor, posicao)
SELECT id, 'Azul', 0 FROM opcao WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Camisa Polo') AND nome = 'Cor';
INSERT INTO opcao (produto_id, nome, posicao) VALUES ((SELECT id FROM produto WHERE nome = 'Camisa Polo'), 'Tamanho', 1);
INSERT INTO valor_opcao (opcao_id, valor, posicao)
SELECT id, 'M', 0 FROM opcao WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Camisa Polo') AND nome = 'Tamanho';
WITH nova AS (
    INSERT INTO variante (produto_id, preco, quantidade_estoque) VALUES ((SELECT id FROM produto WHERE nome = 'Camisa Polo'), 79.90, 5) RETURNING id
)
INSERT INTO variante_valor (variante_id, valor_opcao_id)
SELECT nova.id, vo.id FROM nova, valor_opcao vo JOIN opcao op ON op.id = vo.opcao_id
WHERE op.produto_id = (SELECT id FROM produto WHERE nome = 'Camisa Polo') AND ((op.nome = 'Cor' AND vo.valor = 'Azul') OR (op.nome = 'Tamanho' AND vo.valor = 'M'));

-- Calça Jeans Masculina
INSERT INTO produto (empresa_id, nome, descricao, categoria)
SELECT id, 'Calça Jeans Masculina', 'Calça jeans reta, lavagem escura', 'calça' FROM empresa WHERE documento = '11222333000181';
INSERT INTO opcao (produto_id, nome, posicao) VALUES ((SELECT id FROM produto WHERE nome = 'Calça Jeans Masculina'), 'Tamanho', 0);
INSERT INTO valor_opcao (opcao_id, valor, posicao)
SELECT id, '42', 0 FROM opcao WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Calça Jeans Masculina') AND nome = 'Tamanho';
WITH nova AS (
    INSERT INTO variante (produto_id, preco, quantidade_estoque) VALUES ((SELECT id FROM produto WHERE nome = 'Calça Jeans Masculina'), 149.90, 3) RETURNING id
)
INSERT INTO variante_valor (variante_id, valor_opcao_id)
SELECT nova.id, vo.id FROM nova, valor_opcao vo JOIN opcao op ON op.id = vo.opcao_id
WHERE op.produto_id = (SELECT id FROM produto WHERE nome = 'Calça Jeans Masculina') AND ((op.nome = 'Tamanho' AND vo.valor = '42'));

-- Calça Jeans Feminina
INSERT INTO produto (empresa_id, nome, descricao, categoria)
SELECT id, 'Calça Jeans Feminina', 'Calça jeans skinny, lavagem clara', 'calça' FROM empresa WHERE documento = '11222333000181';
INSERT INTO opcao (produto_id, nome, posicao) VALUES ((SELECT id FROM produto WHERE nome = 'Calça Jeans Feminina'), 'Tamanho', 0);
INSERT INTO valor_opcao (opcao_id, valor, posicao)
SELECT id, '38', 0 FROM opcao WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Calça Jeans Feminina') AND nome = 'Tamanho';
WITH nova AS (
    INSERT INTO variante (produto_id, preco, quantidade_estoque) VALUES ((SELECT id FROM produto WHERE nome = 'Calça Jeans Feminina'), 139.90, 6) RETURNING id
)
INSERT INTO variante_valor (variante_id, valor_opcao_id)
SELECT nova.id, vo.id FROM nova, valor_opcao vo JOIN opcao op ON op.id = vo.opcao_id
WHERE op.produto_id = (SELECT id FROM produto WHERE nome = 'Calça Jeans Feminina') AND ((op.nome = 'Tamanho' AND vo.valor = '38'));

-- Tênis Casual
INSERT INTO produto (empresa_id, nome, descricao, categoria)
SELECT id, 'Tênis Casual', 'Tênis de couro sintético', 'tênis' FROM empresa WHERE documento = '11222333000181';
INSERT INTO opcao (produto_id, nome, posicao) VALUES ((SELECT id FROM produto WHERE nome = 'Tênis Casual'), 'Cor', 0);
INSERT INTO valor_opcao (opcao_id, valor, posicao)
SELECT id, 'Branco', 0 FROM opcao WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Tênis Casual') AND nome = 'Cor';
INSERT INTO opcao (produto_id, nome, posicao) VALUES ((SELECT id FROM produto WHERE nome = 'Tênis Casual'), 'Tamanho', 1);
INSERT INTO valor_opcao (opcao_id, valor, posicao)
SELECT id, '40', 0 FROM opcao WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Tênis Casual') AND nome = 'Tamanho';
WITH nova AS (
    INSERT INTO variante (produto_id, preco, quantidade_estoque) VALUES ((SELECT id FROM produto WHERE nome = 'Tênis Casual'), 199.90, 4) RETURNING id
)
INSERT INTO variante_valor (variante_id, valor_opcao_id)
SELECT nova.id, vo.id FROM nova, valor_opcao vo JOIN opcao op ON op.id = vo.opcao_id
WHERE op.produto_id = (SELECT id FROM produto WHERE nome = 'Tênis Casual') AND ((op.nome = 'Cor' AND vo.valor = 'Branco') OR (op.nome = 'Tamanho' AND vo.valor = '40'));

-- Boné Ajustável
INSERT INTO produto (empresa_id, nome, descricao, categoria)
SELECT id, 'Boné Ajustável', 'Boné preto de aba curva com regulagem', 'acessório' FROM empresa WHERE documento = '11222333000181';
INSERT INTO variante (produto_id, preco, quantidade_estoque) VALUES ((SELECT id FROM produto WHERE nome = 'Boné Ajustável'), 39.90, 15);
