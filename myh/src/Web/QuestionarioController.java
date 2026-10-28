package Web;

import Control.DAOAreaAluno;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
            validar(opcao(objetivo, "ganhar_musculo", "perder_peso", "condicionamento", "definicao", "manter_peso", "saude"), "objetivo");
            validar(opcao(formaAtual, "magro", "media", "pesado", "muito_pesado", "nao_informado"), "forma atual");
            validar(opcao(formaDesejada, "esbelto", "em_forma", "musculoso", "fisiculturismo", "nao_informado"), "forma desejada");
            int vezes = inteiro(frequencia, 1, 6, "frequência de treino");
            int minutos = inteiro(duracao, 30, 120, "duração do treino");
            float peso = decimal(pesoAlvo, 25, 400, "peso-meta");
            LocalDate nascimento;
            try { nascimento = LocalDate.parse(dataNascimento); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("data de nascimento"); }
            if (nascimento.isAfter(LocalDate.now()) || nascimento.isBefore(LocalDate.now().minusYears(120))) throw new IllegalArgumentException("data de nascimento");
            validar(opcao(sexo, "Masculino", "Feminino", "Prefiro não Informar"), "sexo");
            float alturaInformada = decimal(altura, 1.0f, 2.5f, "altura");
            float pesoInformado = decimal(pesoInicial, 25.0f, 400.0f, "peso atual");
            float[] faixaPeso = faixaPesoAlvo(objetivo, pesoInformado);
            validar(pesoMetaValido(objetivo, pesoInformado, peso),
                    "peso-alvo (fora do limite permitido para o objetivo escolhido: " + faixaPeso[0] + " a " + faixaPeso[1] + " kg)");
            validar(opcao(local, "casa", "academia", "hibrido"), "local de treino");
            if (respostas.length() > 8000 || !respostas.startsWith("{") || !respostas.endsWith("}")) throw new IllegalArgumentException("respostas do questionário");
            String meta = switch (objetivo) {
                case "ganhar_musculo" -> "Ganhar Massa";
                case "perder_peso" -> "Emagrecer";
                case "condicionamento" -> "Condicionamento";
                case "definicao" -> "Definicao";
                case "manter_peso" -> "Manter peso";
                default -> "Saúde e disposição";
            };
            boolean treino = areaAluno.finalizarQuestionario((Integer) usuario, meta, peso, minutos, formaAtual, formaDesejada,
                    String.valueOf(vezes), local, respostas, nascimento, sexo, alturaInformada, pesoInformado);
            sessao.setAttribute("questionarioConcluido", true);
            return ResponseEntity.ok(Map.of("sucesso", true, "treinoAtribuido", treino,
                    "mensagem", treino ? "Questionário salvo e treinos personalizados prontos!" : "Perfil salvo. Não foi possível liberar os treinos agora."));
        } catch (IllegalArgumentException e) {
            String campo = e.getMessage();
            String mensagem = campo == null || campo.isBlank()
                    ? "Existem respostas inválidas ou incompletas."
                    : "Confira o campo: " + campo + ".";
            return ResponseEntity.badRequest().body(Map.of("sucesso", false, "mensagem", mensagem));
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("sucesso", false, "mensagem", "Não foi possível salvar o questionário."));
        }
    }

    private boolean opcao(String valor, String... permitidos) { return valor != null && List.of(permitidos).contains(valor); }
    public static boolean pesoMetaValido(String objetivo, float pesoAtual, float pesoMeta) {
        if (!Float.isFinite(pesoAtual) || !Float.isFinite(pesoMeta) || pesoAtual < 25 || pesoAtual > 400) return false;
        if ("perder_peso".equals(chaveObjetivo(objetivo)) && pesoMeta >= pesoAtual) return false;
        float[] faixa = faixaPesoAlvo(objetivo, pesoAtual);
        return pesoMeta >= faixa[0] && pesoMeta <= faixa[1];
    }
    private static String chaveObjetivo(String objetivo) {
        String texto = Normalizer.normalize(objetivo == null ? "" : objetivo.trim().toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('_', ' ');
        return switch (texto) {
            case "ganhar musculo", "ganhar massa" -> "ganhar_musculo";
            case "perder peso", "emagrecer" -> "perder_peso";
            case "manter peso" -> "manter_peso";
            case "condicionamento" -> "condicionamento";
            case "definicao" -> "definicao";
            default -> "saude";
        };
    }
    private static float[] faixaPesoAlvo(String objetivo, float pesoAtual) {
        String chave = chaveObjetivo(objetivo);
        double minimo, maximo;
        switch (chave) {
            case "ganhar_musculo" -> {
                minimo = Math.ceil(pesoAtual * 10.0) / 10.0;
                maximo = Math.min(400, Math.round(pesoAtual * 1.2 * 10.0) / 10.0);
            }
            case "perder_peso" -> {
                minimo = Math.max(25, Math.round(pesoAtual * 0.8 * 10.0) / 10.0);
                maximo = Math.max(minimo, Math.floor((pesoAtual - 0.1) * 10.0) / 10.0);
            }
            case "manter_peso" -> {
                minimo = Math.max(25, Math.round(pesoAtual * 0.95 * 10.0) / 10.0);
                maximo = Math.min(400, Math.round(pesoAtual * 1.05 * 10.0) / 10.0);
            }
            default -> {
                minimo = Math.max(25, Math.round(pesoAtual * 0.9 * 10.0) / 10.0);
                maximo = Math.min(400, Math.round(pesoAtual * 1.1 * 10.0) / 10.0);
            }
        }
        return new float[] { (float) minimo, (float) maximo };
    }
    private void validar(boolean valido, String campo) { if (!valido) throw new IllegalArgumentException(campo); }
    private int inteiro(String valor, int min, int max, String campo) {
        try {
            int numero = Integer.parseInt(valor);
            if (numero < min || numero > max) throw new IllegalArgumentException(campo);
            return numero;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(campo); }
    }
    private float decimal(String valor, float min, float max, String campo) {
        try {
            float numero = Float.parseFloat(valor);
            if (!Float.isFinite(numero) || numero < min || numero > max) throw new IllegalArgumentException(campo);
            return numero;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(campo); }
    }
}
