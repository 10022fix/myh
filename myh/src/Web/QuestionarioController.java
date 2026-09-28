package Web;

import Control.DAOAreaAluno;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Recebe somente respostas previstas pelo questionario e cria o perfil inicial do aluno. */
@RestController
public class QuestionarioController {
    private final DAOAreaAluno areaAluno = new DAOAreaAluno();

    @PostMapping("/api/questionario/finalizar")
    public ResponseEntity<?> finalizar(@RequestParam String objetivo, @RequestParam String formaAtual,
            @RequestParam String formaDesejada, @RequestParam String frequencia, @RequestParam String duracao,
            @RequestParam String local, @RequestParam String pesoAlvo, @RequestParam String respostas,
            @RequestParam String dataNascimento, @RequestParam String sexo, @RequestParam String altura,
            @RequestParam String pesoInicial, HttpSession sessao) {
        Object usuario = sessao.getAttribute("usuarioId");
        if (!(usuario instanceof Integer)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", "Sua sessao expirou. Faca login novamente."));
        try {
            validar(opcao(objetivo, "ganhar_musculo", "perder_peso"), "objetivo");
            validar(opcao(formaAtual, "magro", "media", "pesado", "muito_pesado"), "forma atual");
            validar(opcao(formaDesejada, "esbelto", "em_forma", "musculoso", "fisiculturismo"), "forma desejada");
            int vezes = inteiro(frequencia, 1, 6); int minutos = inteiro(duracao, 30, 120); float peso = decimal(pesoAlvo, 30, 200);
            LocalDate nascimento = LocalDate.parse(dataNascimento);
            if (nascimento.isAfter(LocalDate.now()) || nascimento.isBefore(LocalDate.now().minusYears(120))) throw new IllegalArgumentException();
            validar(opcao(sexo, "Masculino", "Feminino", "Prefiro não Informar"), "sexo");
            float alturaInformada = decimal(altura, 1.0f, 2.5f); float pesoInformado = decimal(pesoInicial, 25.0f, 400.0f);
            validar(opcao(local, "casa", "academia", "hibrido"), "local de treino");
            if (respostas.length() > 8000 || !respostas.startsWith("{") || !respostas.endsWith("}")) throw new IllegalArgumentException();
            String meta = objetivo.equals("ganhar_musculo") ? "Ganhar Massa" : "Emagrecer";
            boolean treino = areaAluno.finalizarQuestionario((Integer) usuario, meta, peso, minutos, formaAtual, formaDesejada,
                    String.valueOf(vezes), local, respostas, nascimento, sexo, alturaInformada, pesoInformado);
            sessao.setAttribute("questionarioConcluido", true);
            return ResponseEntity.ok(Map.of("sucesso", true, "treinoAtribuido", treino,
                    "mensagem", treino ? "Perfil salvo e treino liberado!" : "Perfil salvo. Um treino será liberado em breve."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("sucesso", false, "mensagem", "Existem respostas inválidas ou incompletas."));
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("sucesso", false, "mensagem", "Não foi possível salvar o questionário."));
        }
    }

    private boolean opcao(String valor, String... permitidos) { return valor != null && List.of(permitidos).contains(valor); }
    private void validar(boolean valido, String campo) { if (!valido) throw new IllegalArgumentException(campo); }
    private int inteiro(String valor, int min, int max) { int numero = Integer.parseInt(valor); if (numero < min || numero > max) throw new IllegalArgumentException(); return numero; }
    private float decimal(String valor, float min, float max) { float numero = Float.parseFloat(valor); if (!Float.isFinite(numero) || numero < min || numero > max) throw new IllegalArgumentException(); return numero; }
}
