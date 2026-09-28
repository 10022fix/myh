package Web;

import Control.DAOUsuario;
import Model.Usuario;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {
    private final DAOUsuario usuarios = new DAOUsuario();
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @PostMapping("/api/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam String email,
            @RequestParam String senha,
            HttpSession sessao, HttpServletRequest requisicao) {

        if (!usuarios.validarLogin(email, senha)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("sucesso", false, "mensagem", "E-mail ou senha invalidos."));
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

            int usuarioId = usuarios.cadastrarUsuarioERetornarId(usuario);
            if (usuarioId <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "sucesso", false,
                        "mensagem", "Nao foi possivel cadastrar. O e-mail pode ja existir."));
            }

            usuario.setId(usuarioId);
            iniciarSessao(requisicao, usuario);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "sucesso", true,
                    "mensagem", "Cadastro realizado. Agora responda algumas perguntas.",
                    "redirecionar", "/questionario.html"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "sucesso", false,
                    "mensagem", "Confira os dados informados."));
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
