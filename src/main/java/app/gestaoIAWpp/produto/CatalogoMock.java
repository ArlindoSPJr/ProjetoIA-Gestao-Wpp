package app.gestaoIAWpp.produto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Dados de exemplo da demo, em memória. Substituído pelo banco em uma fase futura. */
public final class CatalogoMock {

    public static final List<Produto> PRODUTOS = List.of(
            new Produto("P001", "Camisa Básica Preta M", "Camisa de algodão, gola redonda", "camisa", new BigDecimal("49.90")),
            new Produto("P002", "Camisa Básica Preta G", "Camisa de algodão, gola redonda", "camisa", new BigDecimal("49.90")),
            new Produto("P003", "Camisa Básica Branca M", "Camisa de algodão, gola redonda", "camisa", new BigDecimal("49.90")),
            new Produto("P004", "Camisa Polo Azul M", "Camisa polo de piquet", "camisa", new BigDecimal("79.90")),
            new Produto("P005", "Calça Jeans Masculina 42", "Calça jeans reta, lavagem escura", "calça", new BigDecimal("149.90")),
            new Produto("P006", "Calça Jeans Feminina 38", "Calça jeans skinny, lavagem clara", "calça", new BigDecimal("139.90")),
            new Produto("P007", "Tênis Casual Branco 40", "Tênis de couro sintético", "tênis", new BigDecimal("199.90")),
            new Produto("P008", "Boné Preto Ajustável", "Boné de aba curva com regulagem", "acessório", new BigDecimal("39.90")));

    public static final Map<String, Integer> ESTOQUE_INICIAL = Map.of(
            "P001", 12,
            "P002", 0,
            "P003", 8,
            "P004", 5,
            "P005", 3,
            "P006", 6,
            "P007", 4,
            "P008", 15);

    private CatalogoMock() {
    }
}
