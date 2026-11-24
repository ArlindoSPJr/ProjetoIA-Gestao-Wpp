package app.gestaoIAWpp.carrinho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import app.gestaoIAWpp.TestcontainersConfiguration;
import app.gestaoIAWpp.produto.EstoqueInsuficienteException;
import app.gestaoIAWpp.produto.ProdutoService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({TestcontainersConfiguration.class, CarrinhoService.class, ProdutoService.class})
class CarrinhoServiceIT {

	@Autowired
	CarrinhoService service;

	@Autowired
	ProdutoService produtos;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	private UUID lojaDemo() {
		return jdbc.queryForObject("SELECT id FROM empresa WHERE documento = '11222333000181'", UUID.class);
	}

	private UUID novoCliente(UUID empresa) {
		return jdbc.queryForObject("INSERT INTO cliente (empresa_id, user_id) VALUES (?, ?) RETURNING id",
				UUID.class, empresa, "u-" + UUID.randomUUID());
	}

	/** Variante da Camisa Básica pelo estoque do seed: 12 = Preta/M, 0 = Preta/G, 8 = Branca/M. */
	private UUID camisaBasica(int estoqueDoSeed) {
		return jdbc.queryForObject("""
				SELECT v.id FROM variante v
				JOIN produto p ON p.id = v.produto_id
				WHERE p.nome = 'Camisa Básica' AND v.quantidade_estoque = ?
				""", UUID.class, estoqueDoSeed);
	}

	private int saldo(UUID varianteId) {
		return jdbc.queryForObject("SELECT quantidade_estoque FROM variante WHERE id = ?", Integer.class, varianteId);
	}

	private int carrinhosAbertos(UUID empresa) {
		return jdbc.queryForObject("SELECT count(*) FROM carrinho WHERE empresa_id = ? AND status = 'ABERTO'",
				Integer.class, empresa);
	}

	private List<Integer> quantidadesNoBanco(UUID cliente) {
		return jdbc.queryForList("""
				SELECT i.quantidade FROM item_carrinho i JOIN carrinho c ON c.id = i.carrinho_id
				WHERE c.cliente_id = ? AND c.status = 'ABERTO'
				""", Integer.class, cliente);
	}

	private Map<UUID, Integer> estoques() {
		return jdbc.queryForList("SELECT id, quantidade_estoque FROM variante").stream()
				.collect(Collectors.toMap(l -> (UUID) l.get("id"), l -> (Integer) l.get("quantidade_estoque")));
	}

	@Test
	@DisplayName("T07 estoque insuficiente não cria carrinho nem mexe no estoque")
	void deveNaoCriarCarrinhoNemMexerEstoque_quandoEstoqueInsuficiente() {
		UUID empresa = lojaDemo();
		UUID cliente = novoCliente(empresa);
		UUID pretaG = camisaBasica(0);

		assertThatThrownBy(() -> service.adicionar(empresa, cliente, pretaG, 1))
				.isInstanceOf(EstoqueInsuficienteException.class)
				.hasMessage("Estoque insuficiente para Camisa Básica (Preta / G): solicitado 1, disponível 0.");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM carrinho WHERE cliente_id = ?", Integer.class, cliente))
				.isZero();
		assertThat(saldo(pretaG)).isZero();
	}

	@Test
	@DisplayName("CA05 adicionar baixa o estoque e grava o item; adicionar de novo soma")
	void deveBaixarEstoqueEGravarItem_quandoAdiciona() {
		UUID empresa = lojaDemo();
		UUID cliente = novoCliente(empresa);
		UUID pretaM = camisaBasica(12);

		CarrinhoResumo resumo = service.adicionar(empresa, cliente, pretaM, 2);

		assertThat(saldo(pretaM)).isEqualTo(10);
		assertThat(quantidadesNoBanco(cliente)).containsExactly(2);
		assertThat(resumo.itens()).containsExactly(
				new ItemCarrinhoResumo(pretaM, "Camisa Básica (Preta / M)", new BigDecimal("49.90"), 2));
		assertThat(resumo.itens().get(0).subtotal()).isEqualTo(new BigDecimal("99.80"));
		assertThat(resumo.total()).isEqualTo(new BigDecimal("99.80"));

		resumo = service.adicionar(empresa, cliente, pretaM, 1);

		assertThat(resumo.itens()).singleElement().satisfies(i -> assertThat(i.quantidade()).isEqualTo(3));
		assertThat(saldo(pretaM)).isEqualTo(9);
		assertThat(quantidadesNoBanco(cliente)).containsExactly(3);
		assertThat(service.ver(cliente)).isEqualTo(resumo);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DisplayName("Review Focus: depois do UPDATE nativo, a mesma transação lê o estoque novo")
	void deveMostrarEstoqueNovo_quandoConsultaNaMesmaTransacaoDepoisDeAdicionar() {
		UUID empresa = lojaDemo();
		UUID cliente = novoCliente(empresa);
		UUID pretaM = camisaBasica(12);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		try {
			Integer disponivel = tx.execute(status -> {
				produtos.consultarDisponibilidade(empresa, pretaM);
				service.adicionar(empresa, cliente, pretaM, 2);
				return produtos.consultarDisponibilidade(empresa, pretaM).quantidadeDisponivel();
			});

			assertThat(disponivel).isEqualTo(10);
		} finally {
			service.reiniciarDemo(empresa);
			jdbc.update("DELETE FROM cliente WHERE id = ?", cliente);
		}
	}

	@Test
	@DisplayName("T21 reset devolve o estoque e apaga só os carrinhos abertos da empresa")
	void deveDevolverEstoqueEApagarCarrinhos_quandoReiniciaDemo() {
		UUID empresa = lojaDemo();
		Map<UUID, Integer> doSeed = estoques();
		UUID pretaM = camisaBasica(12);
		UUID brancaM = camisaBasica(8);
		UUID ana = novoCliente(empresa);
		UUID bia = novoCliente(empresa);
		service.adicionar(empresa, ana, pretaM, 2);
		service.adicionar(empresa, ana, brancaM, 1);
		service.adicionar(empresa, bia, pretaM, 3);
		UUID outraEmpresa = jdbc.queryForObject("""
				INSERT INTO empresa (razao_social, nome_fantasia, documento)
				VALUES ('Outra LTDA', 'Outra', '99888777000166') RETURNING id
				""", UUID.class);
		UUID deOutraEmpresa = novoCliente(outraEmpresa);
		jdbc.update("INSERT INTO carrinho (empresa_id, cliente_id, status) VALUES (?, ?, 'ABERTO')",
				outraEmpresa, deOutraEmpresa);
		assertThat(saldo(pretaM)).isEqualTo(7);
		assertThat(carrinhosAbertos(empresa)).isEqualTo(2);
		assertThat(quantidadesNoBanco(ana)).containsExactly(2, 1);

		service.reiniciarDemo(empresa);

		assertThat(estoques()).isEqualTo(doSeed);
		assertThat(carrinhosAbertos(empresa)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM item_carrinho", Integer.class)).isZero();
		assertThat(carrinhosAbertos(outraEmpresa)).isEqualTo(1);
		assertThat(service.ver(ana).itens()).isEmpty();

		service.reiniciarDemo(empresa);

		assertThat(estoques()).isEqualTo(doSeed);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DisplayName("T21 RN14 reset espera o adicionar em andamento no mesmo carrinho e devolve tudo")
	void deveDevolverTodoEstoque_quandoResetConcorreComAdicionar() throws Exception {
		UUID empresa = lojaDemo();
		Map<UUID, Integer> doSeed = estoques();
		UUID cliente = novoCliente(empresa);
		UUID pretaM = camisaBasica(12);
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		CountDownLatch adicionou = new CountDownLatch(1);
		CountDownLatch podeCommitar = new CountDownLatch(1);
		ExecutorService threads = Executors.newFixedThreadPool(2);
		try {
			service.adicionar(empresa, cliente, pretaM, 2);

			// Thread 1: adicionar dentro de uma transação que só commita quando o teste mandar.
			Future<?> adicionar = threads.submit(() -> tx.executeWithoutResult(status -> {
				service.adicionar(empresa, cliente, pretaM, 1);
				adicionou.countDown();
				aguardar(podeCommitar);
			}));
			assertThat(adicionou.await(10, TimeUnit.SECONDS)).isTrue();

			// Thread 2: o reset começa enquanto o adicionar ainda não commitou.
			Future<?> reset = threads.submit(() -> service.reiniciarDemo(empresa));
			assertThatThrownBy(() -> reset.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

			podeCommitar.countDown();
			adicionar.get(10, TimeUnit.SECONDS);
			reset.get(10, TimeUnit.SECONDS);

			assertThat(estoques()).isEqualTo(doSeed);
			assertThat(carrinhosAbertos(empresa)).isZero();
		} finally {
			podeCommitar.countDown();
			threads.shutdownNow();
			assertThat(threads.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
			service.reiniciarDemo(empresa);
			// Se o reset perdeu estoque, devolve os valores do seed para não contaminar os outros testes.
			doSeed.forEach((id, qtd) -> jdbc.update("UPDATE variante SET quantidade_estoque = ? WHERE id = ?", qtd, id));
			jdbc.update("DELETE FROM cliente WHERE id = ?", cliente);
		}
	}

	private static void aguardar(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("O teste não liberou o commit em 10 s.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	@Test
	@DisplayName("T17 remover parcial no banco diminui o item e devolve o estoque")
	void deveDiminuirItemEDevolverEstoque_quandoRemoveParte() {
		UUID empresa = lojaDemo();
		UUID cliente = novoCliente(empresa);
		UUID pretaM = camisaBasica(12);
		service.adicionar(empresa, cliente, pretaM, 3);

		CarrinhoResumo resumo = service.remover(empresa, cliente, pretaM, 1);

		assertThat(resumo.itens()).singleElement().satisfies(i -> assertThat(i.quantidade()).isEqualTo(2));
		assertThat(quantidadesNoBanco(cliente)).containsExactly(2);
		assertThat(saldo(pretaM)).isEqualTo(10);

		resumo = service.remover(empresa, cliente, pretaM, null);

		assertThat(resumo.itens()).isEmpty();
		assertThat(resumo.total()).isEqualTo(new BigDecimal("0.00"));
		assertThat(saldo(pretaM)).isEqualTo(12);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM item_carrinho", Integer.class)).isZero();
	}

}
