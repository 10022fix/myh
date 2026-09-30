package Web;

import Control.DAOUsuario;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecuperacaoSenhaController {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String RESPOSTA_GENERICA =
            "Se o e-mail estiver cadastrado, enviaremos um link para redefinir a senha.";

    private final DAOUsuario usuarios = new DAOUsuario();
    private final ObjectProvider<JavaMailSender> remetentes;
    private final String urlBase;
    private final String remetente;
    private final boolean smtpConfigurado;

    public RecuperacaoSenhaController(ObjectProvider<JavaMailSender> remetentes,
            @Value("${app.base-url:http://localhost:8080}") String urlBase,
            @Value("${app.mail.from:}") String remetente,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String usuarioSmtp,
            @Value("${spring.mail.password:}") String senhaSmtp) {
        this.remetentes = remetentes;
        this.urlBase = urlBase;
        this.remetente = remetente;
        this.smtpConfigurado = !host.isBlank() && !usuarioSmtp.isBlank() && !senhaSmtp.isBlank();
    }

    @PostMapping("/api/senha/esqueci")
    public ResponseEntity<Map<String, String>> solicitar(@RequestParam String email) {
        String emailNormalizado = email == null ? "" : email.trim().toLowerCase();
        if (!EMAIL.matcher(emailNormalizado).matches()) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Informe um e-mail válido."));
        }

        JavaMailSender sender = remetentes.getIfAvailable();
        if (sender == null || remetente.isBlank() || !smtpConfigurado) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("mensagem", "A recuperação por e-mail ainda não está configurada."));
        }

        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            boolean criado = usuarios.criarTokenRedefinicao(emailNormalizado, hashToken(token), Instant.now().plusSeconds(1800));
            if (criado) {
                String link = urlBase.replaceAll("/+$", "") + "/redefinir-senha.html#token="
                        + URLEncoder.encode(token, StandardCharsets.UTF_8);
                SimpleMailMessage mensagem = new SimpleMailMessage();
                mensagem.setFrom(remetente);
                mensagem.setTo(emailNormalizado);
                mensagem.setSubject("Redefinição de senha - Make Your Health");
                mensagem.setText("Recebemos um pedido para redefinir a senha da sua conta.\n\n"
                        + "Use o link abaixo em até 30 minutos:\n" + link + "\n\n"
                        + "Se você não solicitou essa alteração, ignore este e-mail.");
                sender.send(mensagem);
            }
            return ResponseEntity.ok(Map.of("mensagem", RESPOSTA_GENERICA));
        } catch (Exception e) {
            System.err.println("Falha ao processar pedido de redefinição de senha: " + e.getClass().getSimpleName());
            return ResponseEntity.ok(Map.of("mensagem", RESPOSTA_GENERICA));
        }
    }

    @PostMapping("/api/senha/redefinir")
    public ResponseEntity<Map<String, String>> redefinir(@RequestParam String token,
            @RequestParam String novaSenha, @RequestParam String confirmarSenha) {
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
            if (!usuarios.redefinirSenha(hashToken(token), novaSenha)) {
                return ResponseEntity.badRequest().body(Map.of("mensagem", "O link de redefinição é inválido ou expirou."));
            }
            return ResponseEntity.ok(Map.of("mensagem", "Senha alterada. Você já pode entrar com a nova senha."));
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
}