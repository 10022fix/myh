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

    public LoginController(ServicoEmailBrevo emailBrevo,
            @Value("${app.base-url:https://myh-j47a.onrender.com}") String urlBase) {
        this.emailBrevo = emailBrevo;
        this.urlBase = urlBase;
    }

    @PostMapping("/api/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam String email,
            @RequestParam String senha,
            HttpSession sessao, HttpServletRequest requisicao) {

        email = email == null ? "" : email.trim().toLowerCase();
        if (!usuarios.validarLogin(email, senha)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("sucesso", false,
                            "mensagem", "E-mail ou senha inválidos. Se acabou de se cadastrar, confirme o e-mail pelo link enviado."));
        }

        Usuario usuario = usuarios.buscarPorEmail(email);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("sucesso", false, "mensagem", "Nao foi possivel carregar o usuario."));
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
        Object usuarioId = sessao.getAttribute("usuarioId");
        if (usuarioId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("sucesso", false, "mensagem", "Nenhum usuario autenticado."));
        }
        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "id", usuarioId,
                "nome", sessao.getAttribute("usuarioNome")));
    }

    @PostMapping("/api/logout")
    public Map<String, Object> logout(HttpSession sessao) {
        sessao.invalidate();
        return Map.of("sucesso", true, "mensagem", "Logout realizado.");
    }

    @PostMapping("/api/cadastro")
    public ResponseEntity<Map<String, Object>> cadastrar(
            @RequestParam String nome,
            @RequestParam String email,
            @RequestParam String senha,
            HttpServletRequest requisicao) {
        int usuarioPendenteId = 0;
        try {
            nome = nome == null ? "" : nome.trim();
            email = email == null ? "" : email.trim().toLowerCase();
            if (nome.length() < 3 || nome.length() > 150 || !EMAIL.matcher(email).matches()
                    || senha == null || senha.length() < 8 || senha.length() > 128) {
                return ResponseEntity.badRequest().body(Map.of("sucesso", false,
                        "mensagem", "Confira nome, e-mail e senha (mínimo 8 caracteres)."));
            }
            Usuario usuario = new Usuario();
            usuario.setNome(nome);
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
                        "mensagem", "Não foi possível cadastrar. Confira os dados; esse e-mail talvez já esteja cadastrado."));
            }

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
                    "mensagem", "E-mail confirmado! Vamos completar seu cadastro.",
                    "redirecionar", "/questionario.html"));
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
    private void iniciarSessao(HttpServletRequest requisicao, Usuario usuario) {
        HttpSession anterior = requisicao.getSession(false);
        if (anterior != null) anterior.invalidate();
        HttpSession nova = requisicao.getSession(true);
        nova.setAttribute("usuarioId", usuario.getId());
        nova.setAttribute("usuarioNome", usuario.getNome());
        nova.setAttribute("csrf", java.util.UUID.randomUUID().toString());
        nova.setMaxInactiveInterval(30 * 60);
    }
}
