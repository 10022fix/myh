package Web;

import Control.DAOUsuario;
import Model.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecuperacaoSenhaController {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10)).build();
    private static final String RESPOSTA_GENERICA =
            "Se o e-mail estiver cadastrado, enviaremos um link para redefinir a senha.";

    private final DAOUsuario usuarios = new DAOUsuario();
    private final String urlBase;
    private final String remetente;
    private final String chaveBrevo;

    public RecuperacaoSenhaController(@Value("${app.base-url:http://localhost:8080}") String urlBase,
            @Value("${app.mail.from:}") String remetente,
            @Value("${app.mail.brevo-api-key:}") String chaveBrevo) {
        this.urlBase = urlBase;
        this.remetente = remetente;
        this.chaveBrevo = chaveBrevo;
    }

    @PostMapping("/api/senha/esqueci")
    public ResponseEntity<Map<String, String>> solicitar(@RequestParam String email) {
        String emailNormalizado = email == null ? "" : email.trim().toLowerCase();
        if (!EMAIL.matcher(emailNormalizado).matches()) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Informe um e-mail válido."));
        }

        if (chaveBrevo.isBlank() || remetente.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("mensagem", "A recuperação por e-mail ainda não está configurada."));
        }

        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            boolean criado = usuarios.criarTokenRedefinicao(emailNormalizado, hashToken(token), Instant.now().plusSeconds(1800));
            if (criado) {
                String link = urlBase.replaceAll("/+$", "") + "/esqueci-senha.html#token="
                        + URLEncoder.encode(token, StandardCharsets.UTF_8);
                String texto = "Recebemos um pedido para redefinir a senha da sua conta.\n\n"
                        + "Use o link abaixo em até 30 minutos:\n" + link + "\n\n"
                        + "Se você não solicitou essa alteração, ignore este e-mail.";
                enviarPeloBrevo(emailNormalizado, texto);
            }
            return ResponseEntity.ok(Map.of("mensagem", RESPOSTA_GENERICA));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("Falha ao processar pedido de redefinição de senha: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " - " + e.getMessage()));
            return ResponseEntity.ok(Map.of("mensagem", RESPOSTA_GENERICA));
        }
    }

    @PostMapping("/api/senha/validar-token")
    public ResponseEntity<Map<String, Object>> validarToken(@RequestParam(required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("sucesso", false,
                    "mensagem", "O link de redefinição é inválido ou expirou."));
        }
        try {
            if (!usuarios.tokenRedefinicaoValido(hashToken(token))) {
                return ResponseEntity.badRequest().body(Map.of("sucesso", false,
                        "mensagem", "O link de redefinição é inválido ou expirou."));
            }
            return ResponseEntity.ok(Map.of("sucesso", true,
                    "mensagem", "Confirmação aprovada. Agora crie uma nova senha."));
        } catch (Exception e) {
            System.err.println("Falha ao validar link de redefinição de senha: " + e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("sucesso", false,
                    "mensagem", "Não foi possível confirmar o link agora."));
        }
    }

    @PostMapping("/api/senha/redefinir")
    public ResponseEntity<Map<String, String>> redefinir(@RequestParam String token,
            @RequestParam String novaSenha, @RequestParam String confirmarSenha,
            HttpServletRequest requisicao) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "O link de redefinição é inválido ou expirou."));
        }
        if (novaSenha == null || novaSenha.length() < 8 || novaSenha.length() > 128) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "A senha precisa ter entre 8 e 128 caracteres."));
        }
        if (!novaSenha.equals(confirmarSenha)) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "As senhas não são iguais."));
        }
        try {
            Usuario usuario = usuarios.redefinirSenha(hashToken(token), novaSenha);
            if (usuario == null) {
                return ResponseEntity.badRequest().body(Map.of("mensagem", "O link de redefinição é inválido ou expirou."));
            }
            LoginController.iniciarSessao(requisicao, usuario);
            return ResponseEntity.ok(Map.of(
                    "mensagem", "Senha trocada com sucesso. Bem-vindo, " + usuario.getNome() + "! Estamos levando você para a área do aluno.",
                    "redirecionar", "/areadoaluno.html"));
        } catch (Exception e) {
            System.err.println("Falha ao redefinir senha: " + e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("mensagem", "Não foi possível alterar a senha agora."));
        }
    }

    private String hashToken(String token) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private void enviarPeloBrevo(String destinatario, String texto) throws IOException, InterruptedException {
        String corpo = "{\"sender\":{\"name\":\"Make Your Health\",\"email\":" + json(remetente)
                + "},\"to\":[{\"email\":" + json(destinatario)
                + "}],\"subject\":\"Redefinição de senha - Make Your Health\",\"textContent\":"
                + json(texto) + "}";
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("accept", "application/json")
                .header("api-key", chaveBrevo)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resposta = HTTP_CLIENT.send(pedido,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) {
            throw new IllegalStateException("Brevo respondeu HTTP " + resposta.statusCode());
        }
    }

    private static String json(String valor) {
        StringBuilder resultado = new StringBuilder("\"");
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> resultado.append("\\\"");
                case '\\' -> resultado.append("\\\\");
                case '\n' -> resultado.append("\\n");
                case '\r' -> resultado.append("\\r");
                case '\t' -> resultado.append("\\t");
                default -> {
                    if (c < 0x20) resultado.append(String.format("\\u%04x", (int) c));
                    else resultado.append(c);
                }
            }
        }
        return resultado.append('"').toString();
    }
}
