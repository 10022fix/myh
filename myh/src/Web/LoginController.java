package Web;

import Control.DAOUsuario;
import Model.Usuario;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Base64;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private final DAOUsuario usuarios = new DAOUsuario();
    private final ServicoEmailBrevo emailBrevo;
    private final String urlBase;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern APELIDO_ACESSO = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");
    private static final String VERSAO_TERMOS = "2026-10-26-v1";
    private static final String VERSAO_PRIVACIDADE = "2026-10-26-v1";

    public LoginController(ServicoEmailBrevo emailBrevo,
            @Value("${app.base-url:https://myh-j47a.onrender.com}") String urlBase) {
        this.emailBrevo = emailBrevo;
        this.urlBase = urlBase;
    }

    @PostMapping("/api/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam(required = false) String login,
            @RequestParam(required = false) String email,
            @RequestParam String senha,
            HttpSession sessao, HttpServletRequest requisicao) {

        String identificador = login == null || login.isBlank() ? email : login;
        Usuario usuario = usuarios.autenticarPorLogin(identificador, senha);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("sucesso", false,
                            "mensagem", "Login ou senha incorretos. Se acabou de se cadastrar, confirme o e-mail pelo link enviado."));
        }

        iniciarSessao(requisicao, usuario);

        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem", "Login realizado com sucesso.",
                "id", usuario.getId(),
                "nome", usuario.getNome()));
    }

    @GetMapping("/api/sessao")
    public ResponseEntity<Map<String, Object>> consultarSessao(HttpSession sessao) {
        Object valorId = sessao.getAttribute("usuarioId");
        if (!(valorId instanceof Integer usuarioId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("sucesso", false, "mensagem", "Nenhum usuario autenticado."));
        }
        try {
            Map<String, Object> conta = usuarios.consultarSituacaoConta(usuarioId);
            if (conta == null) {
                sessao.invalidate();
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("sucesso", false, "mensagem", "Esta conta não está mais cadastrada."));
            }
            conta.put("sucesso", true);
            return ResponseEntity.ok(conta);
        } catch (SQLException erro) {
            System.err.println("Erro ao validar a sessão do usuário: " + erro.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("sucesso", false, "mensagem", "Não foi possível validar sua sessão."));
        }
    }
    @PostMapping("/api/logout")
    public Map<String, Object> logout(HttpSession sessao) {
        sessao.invalidate();
        return Map.of("sucesso", true, "mensagem", "Logout realizado.");
    }

    @PostMapping("/api/cadastro")
    public ResponseEntity<Map<String, Object>> cadastrar(
            @RequestParam String nome,
            @RequestParam String apelidoAcesso,
            @RequestParam String email,
            @RequestParam String senha,
            @RequestParam(required = false) Boolean aceiteTermos,
            @RequestParam(required = false) Boolean consentimentoSaude,
            HttpServletRequest requisicao) {
        int usuarioPendenteId = 0;
        try {
            if (!Boolean.TRUE.equals(aceiteTermos) || !Boolean.TRUE.equals(consentimentoSaude)) {
                return ResponseEntity.badRequest().body(Map.of("sucesso", false,
                        "mensagem", "Leia e aceite os termos e autorize o uso das respostas de saúde para criar sua conta."));
            }
            nome = nome == null ? "" : nome.trim();
            apelidoAcesso = apelidoAcesso == null ? "" : apelidoAcesso.trim().toLowerCase(java.util.Locale.ROOT);
            email = email == null ? "" : email.trim().toLowerCase();
            if (!EMAIL.matcher(email).matches()) {
                return ResponseEntity.badRequest().body(Map.of("sucesso", false,
                        "mensagem", "Informe um endereço de e-mail válido."));
            }
            if (!email.endsWith("@gmail.com")) {
                return ResponseEntity.badRequest().body(Map.of("sucesso", false,
                        "mensagem", "Somente endereços @gmail.com podem criar uma conta."));
            }
            if (nome.length() < 3 || nome.length() > 150 || !APELIDO_ACESSO.matcher(apelidoAcesso).matches()
                    || senha == null || senha.length() < 8 || senha.length() > 128) {
                return ResponseEntity.badRequest().body(Map.of("sucesso", false,
                        "mensagem", "Confira nome, apelido (3 a 30 caracteres: letras, números, ponto, hífen ou sublinhado), e-mail e senha (mínimo 8 caracteres)."));
            }
            Usuario usuario = new Usuario();
            usuario.setNome(nome);
            usuario.setApelidoAcesso(apelidoAcesso);
            usuario.setEmail(email);
            usuario.setSenha(senha);
            // A tabela atual exige altura e peso. Valores provisórios são substituídos
            // pelos dados reais quando o questionário obrigatório for concluído.
            usuario.setAltura(1.0f);
            usuario.setPeso_inicial(25.0f);

            usuarioPendenteId = usuarios.cadastrarUsuarioPendenteERetornarId(usuario);
            if (usuarioPendenteId <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "sucesso", false,
                        "mensagem", "Não foi possível cadastrar. Confira os dados; o e-mail ou apelido talvez já esteja cadastrado."));
            }

            usuarios.registrarAceiteLegal(usuarioPendenteId, VERSAO_TERMOS, VERSAO_PRIVACIDADE, true);

            String token = novoToken();
            usuarios.criarTokenVerificacaoEmail(usuarioPendenteId, hashToken(token), Instant.now().plusSeconds(86400));
            String link = urlBase.replaceAll("/+$", "") + "/confirmar-email.html#token="
                    + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
            emailBrevo.enviar(email, "Confirme seu e-mail - Make Your Health",
                    "Olá, " + nome + "!\n\nConfirme seu endereço de e-mail clicando neste link (válido por 24 horas):\n"
                            + link + "\n\nSe você não criou esta conta, ignore esta mensagem.");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "sucesso", true,
                    "aguardandoConfirmacao", true,
                    "mensagem", "Enviamos um link de confirmação para seu e-mail. Abra a mensagem para ativar a conta."));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (usuarioPendenteId > 0) usuarios.excluirUsuarioNaoVerificado(usuarioPendenteId);
            System.err.println("Falha ao enviar confirmação de cadastro: " + e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "sucesso", false,
                    "mensagem", "Não foi possível enviar o e-mail de confirmação agora. Tente novamente."));
        } catch (SQLException e) {
            if (usuarioPendenteId > 0) usuarios.excluirUsuarioNaoVerificado(usuarioPendenteId);
            System.err.println("Falha ao preparar confirmação de cadastro: " + e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "sucesso", false,
                    "mensagem", "Não foi possível concluir o cadastro agora."));
        } catch (RuntimeException e) {
            if (usuarioPendenteId > 0) usuarios.excluirUsuarioNaoVerificado(usuarioPendenteId);
            System.err.println("Falha no cadastro: " + e.getClass().getSimpleName());
            return ResponseEntity.badRequest().body(Map.of(
                    "sucesso", false,
                    "mensagem", "Confira os dados informados."));
        }
    }

    @PostMapping("/api/cadastro/confirmar-email")
    public ResponseEntity<Map<String, Object>> confirmarEmail(@RequestParam String token,
            HttpServletRequest requisicao) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("sucesso", false,
                    "mensagem", "O link de confirmação é inválido ou expirou."));
        }
        try {
            int usuarioId = usuarios.verificarEmail(hashToken(token));
            if (usuarioId <= 0) {
                return ResponseEntity.badRequest().body(Map.of("sucesso", false,
                        "mensagem", "O link de confirmação é inválido ou expirou."));
            }
            Usuario usuario = usuarios.buscarPorId(usuarioId);
            if (usuario == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("sucesso", false,
                        "mensagem", "E-mail confirmado, mas não foi possível abrir sua conta."));
            }
            iniciarSessao(requisicao, usuario);
            return ResponseEntity.ok(Map.of("sucesso", true,
                    "mensagem", "Confirmação aprovada. Volte ao site e continue o cadastro.",
                    "email", usuario.getEmail()));
        } catch (SQLException e) {
            System.err.println("Falha ao confirmar e-mail: " + e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("sucesso", false,
                    "mensagem", "Não foi possível confirmar o e-mail agora."));
        }
    }

    private static String novoToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) throws SQLException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new SQLException("Algoritmo de hash indisponível.", e);
        }
    }

    /** Troca o identificador de sessao depois da autenticacao e evita session fixation. */
    static void iniciarSessao(HttpServletRequest requisicao, Usuario usuario) {
        HttpSession anterior = requisicao.getSession(false);
        if (anterior != null) anterior.invalidate();
        HttpSession nova = requisicao.getSession(true);
        nova.setAttribute("usuarioId", usuario.getId());
        nova.setAttribute("usuarioNome", usuario.getNome());
        nova.setAttribute("usuarioEmail", usuario.getEmail());
        nova.setAttribute("csrf", java.util.UUID.randomUUID().toString());
        nova.setMaxInactiveInterval(30 * 60);
    }
}
