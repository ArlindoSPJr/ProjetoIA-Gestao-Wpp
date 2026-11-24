CREATE TABLE empresa (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    razao_social     text NOT NULL,
    nome_fantasia    text NOT NULL,
    documento        text NOT NULL UNIQUE CHECK (documento ~ '^([0-9]{11}|[0-9]{14})$'),
    segmento         text,
    email            text,
    telefone_contato text,
    criado_em        timestamptz NOT NULL DEFAULT now(),
    atualizado_em    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE numero_whatsapp (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id      uuid NOT NULL REFERENCES empresa (id),
    phone_number_id text NOT NULL UNIQUE,
    waba_id         text,
    business_id     text,
    numero_exibicao text,
    apelido         text,
    ativo           boolean NOT NULL DEFAULT true,
    ativado_em      timestamptz NOT NULL DEFAULT now(),
    desativado_em   timestamptz,
    criado_em       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE produto (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id    uuid NOT NULL REFERENCES empresa (id),
    nome          text NOT NULL,
    descricao     text,
    categoria     text,
    ativo         boolean NOT NULL DEFAULT true,
    criado_em     timestamptz NOT NULL DEFAULT now(),
    atualizado_em timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_produto_empresa ON produto (empresa_id);

CREATE TABLE opcao (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    produto_id uuid NOT NULL REFERENCES produto (id) ON DELETE CASCADE,
    nome       text NOT NULL,
    posicao    int NOT NULL DEFAULT 0,
    UNIQUE (produto_id, nome)
);

CREATE TABLE valor_opcao (
    id       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    opcao_id uuid NOT NULL REFERENCES opcao (id) ON DELETE CASCADE,
    valor    text NOT NULL,
    posicao  int NOT NULL DEFAULT 0,
    UNIQUE (opcao_id, valor)
);

CREATE TABLE variante (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    produto_id         uuid NOT NULL REFERENCES produto (id) ON DELETE CASCADE,
    sku                text,
    preco              numeric(12, 2) NOT NULL CHECK (preco > 0),
    quantidade_estoque int NOT NULL CHECK (quantidade_estoque >= 0),
    ativo              boolean NOT NULL DEFAULT true,
    criado_em          timestamptz NOT NULL DEFAULT now(),
    atualizado_em      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE variante_valor (
    variante_id    uuid NOT NULL REFERENCES variante (id) ON DELETE CASCADE,
    valor_opcao_id uuid NOT NULL REFERENCES valor_opcao (id) ON DELETE RESTRICT,
    PRIMARY KEY (variante_id, valor_opcao_id)
);

CREATE TABLE cliente (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id          uuid NOT NULL REFERENCES empresa (id),
    user_id             text NOT NULL,
    user_id_anterior    text,
    telefone            text CHECK (telefone ~ '^[0-9]{10,15}$'),
    nome_perfil         text,
    username            text,
    nome                text,
    email               text,
    aceita_marketing_em timestamptz,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, user_id)
);

CREATE TABLE carrinho (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id    uuid NOT NULL REFERENCES empresa (id),
    cliente_id    uuid NOT NULL REFERENCES cliente (id),
    status        text NOT NULL CHECK (status IN ('ABERTO', 'CONVERTIDO', 'ABANDONADO')),
    criado_em     timestamptz NOT NULL DEFAULT now(),
    atualizado_em timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_carrinho_aberto_por_cliente ON carrinho (cliente_id) WHERE status = 'ABERTO';

CREATE TABLE item_carrinho (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    carrinho_id  uuid NOT NULL REFERENCES carrinho (id) ON DELETE CASCADE,
    variante_id  uuid NOT NULL REFERENCES variante (id) ON DELETE RESTRICT,
    quantidade   int NOT NULL CHECK (quantidade > 0),
    adicionado_em timestamptz NOT NULL DEFAULT now(),
    UNIQUE (carrinho_id, variante_id)
);
