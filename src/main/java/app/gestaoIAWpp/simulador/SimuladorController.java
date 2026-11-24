package app.gestaoIAWpp.simulador;

import app.gestaoIAWpp.ai.AgenteAtendimento;
import app.gestaoIAWpp.ai.dto.AgenteResponse;
import app.gestaoIAWpp.atendimento.AtendimentoService;
import app.gestaoIAWpp.carrinho.CarrinhoResumo;
import app.gestaoIAWpp.carrinho.CarrinhoService;
import app.gestaoIAWpp.cliente.ClienteService;
import app.gestaoIAWpp.produto.EstoqueService;
import app.gestaoIAWpp.produto.ProdutoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
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

    private static final String PREFIXO_USER_ID = "BR.SIM";

    private final String phoneNumberId;
    private final AtendimentoService atendimento;
    private final ClienteService clientes;
    private final AgenteAtendimento agente;
    private final CarrinhoService carrinho;
    private final EstoqueService estoque;
    private final ProdutoRepository produtos;

    public SimuladorController(@Value("${simulador.phone-number-id}") String phoneNumberId,
            AtendimentoService atendimento, ClienteService clientes, AgenteAtendimento agente,
            CarrinhoService carrinho, EstoqueService estoque, ProdutoRepository produtos) {
        this.phoneNumberId = phoneNumberId;
        this.clientes = clientes;
        this.atendimento = atendimento;
        this.agente = agente;
        this.carrinho = carrinho;
        this.estoque = estoque;
        this.produtos = produtos;
    }

    @PostMapping("/mensagens")
    public AgenteResponse mensagem(@RequestBody MensagemRequest request) {
        String userId = userIdDoSimulador(request.telefone());
        String telefone = userId == null ? null : userId.substring(PREFIXO_USER_ID.length());
        return atendimento.processarMensagem(phoneNumberId, userId, telefone, request.texto());
    }

    @GetMapping("/estado")
    public EstadoSimulador estado(@RequestParam String telefone) {
        List<ItemEstoqueView> itens = produtos.listarTodos().stream()
                .map(p -> new ItemEstoqueView(p.id(), p.nome(), p.preco(), estoque.consultar(p.id())))
                .toList();
        String userId = userIdDoSimulador(telefone);
        if (userId == null) {
            throw new IllegalArgumentException("Telefone e texto são obrigatórios.");
        }
        UUID empresaId = atendimento.empresaDoNumero(phoneNumberId);
        CarrinhoResumo resumo = clientes.buscar(empresaId, userId)
                .map(c -> carrinho.ver(c.getId().toString()))
                .orElseGet(() -> new CarrinhoResumo(List.of(), new BigDecimal("0.00")));
        return new EstadoSimulador(resumo, itens);
    }

    /** Só dígitos, de 10 a 15; nulo se em branco. O user_id do simulador leva o prefixo BR.SIM. */
    static String userIdDoSimulador(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() < 10 || digitos.length() > 15) {
            throw new IllegalArgumentException("Telefone inválido: use só números com DDI, ex.: 5511999990000.");
        }
        return PREFIXO_USER_ID + digitos;
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        agente.reiniciarConversas();
        carrinho.reiniciarTudo();
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
