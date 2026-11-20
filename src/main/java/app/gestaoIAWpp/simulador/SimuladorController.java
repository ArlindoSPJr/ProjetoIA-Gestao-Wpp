package app.gestaoIAWpp.simulador;

import app.gestaoIAWpp.agente.AgenteAtendimento;
import app.gestaoIAWpp.agente.RespostaAgente;
import app.gestaoIAWpp.atendimento.AtendimentoService;
import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.produto.EstoqueService;
import app.gestaoIAWpp.produto.ProdutoRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API que a página do simulador consome. Não tem regra de negócio: só traduz HTTP para os serviços. */
@RestController
@RequestMapping("/api/simulador")
public class SimuladorController {

    private final AtendimentoService atendimento;
    private final AgenteAtendimento agente;
    private final CarrinhoService carrinho;
    private final EstoqueService estoque;
    private final ProdutoRepository produtos;

    public SimuladorController(AtendimentoService atendimento, AgenteAtendimento agente,
            CarrinhoService carrinho, EstoqueService estoque, ProdutoRepository produtos) {
        this.atendimento = atendimento;
        this.agente = agente;
        this.carrinho = carrinho;
        this.estoque = estoque;
        this.produtos = produtos;
    }

    @PostMapping("/mensagens")
    public RespostaAgente mensagem(@RequestBody MensagemRequest request) {
        return atendimento.processarMensagem(request.telefone(), request.texto());
    }

    @GetMapping("/estado")
    public EstadoSimulador estado(@RequestParam String telefone) {
        List<ItemEstoqueView> itens = produtos.listarTodos().stream()
                .map(p -> new ItemEstoqueView(p.id(), p.nome(), p.preco(), estoque.consultar(p.id())))
                .toList();
        return new EstadoSimulador(carrinho.ver(telefone), itens);
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        agente.reiniciarConversas();
        carrinho.limparTodos();
        estoque.reiniciar();
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> argumentoInvalido(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> requisicaoInvalida() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", "Requisição inválida."));
    }
}
