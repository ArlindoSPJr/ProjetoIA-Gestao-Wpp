package app.gestaoIAWpp.carrinho;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.gestaoIAWpp.produto.VarianteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link CarrinhoRepository} mockado com estado em memória, para os testes unitários (sem banco).
 * Imita o que o SQL faz: um carrinho ABERTO por cliente, upsert que soma a quantidade do item e
 * exclusão em lote. As variantes dos itens vêm do {@code findById} do {@link VarianteRepository} dado.
 */
public final class CarrinhoTeste {

    private final List<Carrinho> carrinhos = new ArrayList<>();
    private final VarianteRepository variantes;

    private CarrinhoTeste(VarianteRepository variantes) {
        this.variantes = variantes;
    }

    public static CarrinhoRepository repositorio(VarianteRepository variantes) {
        return new CarrinhoTeste(variantes).criarMock();
    }

    private CarrinhoRepository criarMock() {
        CarrinhoRepository repo = mock(CarrinhoRepository.class);
        when(repo.criarAbertoSeNaoExiste(any(), any())).thenAnswer(inv -> criarAberto(inv.getArgument(0),
                inv.getArgument(1)));
        when(repo.findByClienteIdAndStatus(any(), any())).thenAnswer(inv -> doCliente(inv.getArgument(0),
                inv.getArgument(1)));
        // Sem concorrência na memória, travar é o mesmo que buscar.
        when(repo.travarDoCliente(any(), any())).thenAnswer(inv -> doCliente(inv.getArgument(0),
                inv.getArgument(1)));
        when(repo.travarDaEmpresa(any(), any())).thenAnswer(inv -> carrinhos.stream()
                .filter(c -> c.getEmpresaId().equals(inv.getArgument(0)) && c.getStatus() == inv.getArgument(1))
                .toList());
        when(repo.somarItem(any(), any(), anyInt())).thenAnswer(inv -> somarItem(inv.getArgument(0),
                inv.getArgument(1), inv.getArgument(2)));
        doAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            ids.forEach(id -> carrinhos.removeIf(c -> c.getId().equals(id)));
            return null;
        }).when(repo).deleteAllByIdInBatch(any());
        return repo;
    }

    private Optional<Carrinho> doCliente(UUID clienteId, StatusCarrinho status) {
        return carrinhos.stream()
                .filter(c -> c.getClienteId().equals(clienteId) && c.getStatus() == status)
                .findFirst();
    }

    private int criarAberto(UUID empresaId, UUID clienteId) {
        boolean existe = carrinhos.stream()
                .anyMatch(c -> c.getClienteId().equals(clienteId) && c.getStatus() == StatusCarrinho.ABERTO);
        if (existe) {
            return 0;
        }
        carrinhos.add(new Carrinho(UUID.randomUUID(), empresaId, clienteId, StatusCarrinho.ABERTO));
        return 1;
    }

    private int somarItem(UUID carrinhoId, UUID varianteId, int quantidade) {
        Carrinho carrinho = carrinhos.stream().filter(c -> c.getId().equals(carrinhoId)).findFirst().orElseThrow();
        Optional<ItemCarrinho> item = carrinho.item(varianteId);
        if (item.isPresent()) {
            item.get().somar(quantidade);
        } else {
            carrinho.incluir(new ItemCarrinho(UUID.randomUUID(), carrinho,
                    variantes.findById(varianteId).orElseThrow(), quantidade));
        }
        return 1;
    }
}
