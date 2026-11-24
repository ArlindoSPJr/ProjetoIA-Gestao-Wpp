package app.gestaoIAWpp.produto;

import java.util.UUID;

public record DisponibilidadeVariante(UUID varianteId, String nome, String opcoes, boolean emEstoque,
        int quantidadeDisponivel) {
}
