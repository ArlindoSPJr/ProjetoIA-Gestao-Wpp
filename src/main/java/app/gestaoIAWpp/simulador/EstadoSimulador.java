package app.gestaoIAWpp.simulador;

import app.gestaoIAWpp.carrinho.CarrinhoResumo;
import java.util.List;

public record EstadoSimulador(CarrinhoResumo carrinho, List<ItemEstoqueView> estoque) {
}
