package app.gestaoIAWpp.carrinho;

/** Ciclo de vida do carrinho. Um cliente tem no máximo um {@code ABERTO} (RN04). */
public enum StatusCarrinho {
    ABERTO,
    CONVERTIDO,
    ABANDONADO
}
