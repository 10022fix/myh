package Web;

import Control.DAOAreaAluno;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AreaAlunoController {
    private final DAOAreaAluno areaAluno = new DAOAreaAluno();

    @GetMapping("/api/treinos")
    public ResponseEntity<?> treinos(@RequestParam(defaultValue = "") String busca, HttpSession sessao) { return executar(sessao, id -> areaAluno.listarTreinos(id, busca)); }
    @GetMapping("/api/treinos/conclusoes-semana")
    public ResponseEntity<?> conclusoesSemana(HttpSession sessao) {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate inicio = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return executar(sessao, id -> areaAluno.listarTreinosConcluidos(id, inicio, inicio.plusDays(6)));
    }
    @PostMapping("/api/treinos/{treinoId}/concluir")
    public ResponseEntity<?> concluirTreino(@PathVariable int treinoId, HttpSession sessao) {
        if (treinoId <= 0) return ResponseEntity.badRequest().body(Map.of("mensagem", "Treino inválido."));
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        return executar(sessao, id -> areaAluno.registrarTreinoConcluido(id, treinoId, hoje)
                ? Map.of("mensagem", "Treino concluído com sucesso.")
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", "Treino não encontrado no seu plano.")));
    }
    @GetMapping("/api/objetivos")
    public ResponseEntity<?> objetivos(HttpSession sessao) { return executar(sessao, areaAluno::listarObjetivos); }
    @GetMapping("/api/progresso")
    public ResponseEntity<?> progresso(HttpSession sessao) { return executar(sessao, areaAluno::listarProgresso); }
    @GetMapping("/api/painel/resumo")
    public ResponseEntity<?> resumo(HttpSession sessao) { return executar(sessao, areaAluno::resumoPainel); }

    @PostMapping("/api/objetivos")
    public ResponseEntity<?> cadastrarObjetivo(@RequestParam String objetivo, @RequestParam(required = false) Float pesoMeta,
            @RequestParam(required = false) String dataMeta, HttpSession sessao) {
        LocalDate meta = validarObjetivo(objetivo, pesoMeta, dataMeta);
        if (meta == null && dataMeta != null && !dataMeta.isBlank() || !objetivoValido(objetivo) || !numeroEntre(pesoMeta, 25, 400)) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Informe objetivo, peso e data válidos."));
        }
        return executar(sessao, id -> { areaAluno.cadastrarObjetivo(id, objetivo, pesoMeta, meta); return Map.of("mensagem", "Objetivo cadastrado com sucesso."); });
    }

    @PutMapping("/api/objetivos/{objetivoId}")
    public ResponseEntity<?> atualizarObjetivo(@PathVariable int objetivoId, @RequestParam String objetivo,
            @RequestParam(required = false) Float pesoMeta, @RequestParam(required = false) String dataMeta, HttpSession sessao) {
        LocalDate meta = validarObjetivo(objetivo, pesoMeta, dataMeta);
        if (objetivoId <= 0 || !objetivoValido(objetivo) || !numeroEntre(pesoMeta, 25, 400)
                || (dataMeta != null && !dataMeta.isBlank() && meta == null)
                || (meta != null && meta.isBefore(LocalDate.now()))) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Informe objetivo, peso e data válidos."));
        }
        return executar(sessao, id -> areaAluno.atualizarObjetivo(id, objetivoId, objetivo, pesoMeta, meta)
                ? Map.of("mensagem", "Meta atualizada com sucesso.") : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", "Meta não encontrada.")));
    }

    @DeleteMapping("/api/objetivos/{objetivoId}")
    public ResponseEntity<?> excluirObjetivo(@PathVariable int objetivoId, HttpSession sessao) {
        if (objetivoId <= 0) return ResponseEntity.badRequest().body(Map.of("mensagem", "Meta inválida."));
        return executar(sessao, id -> areaAluno.excluirObjetivo(id, objetivoId)
                ? Map.of("mensagem", "Meta excluída com sucesso.") : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", "Meta não encontrada.")));
    }

    @PostMapping("/api/progresso")
    public ResponseEntity<?> cadastrarProgresso(@RequestParam float peso, @RequestParam(required = false) Float gordura,
            @RequestParam String dataRegistro, @RequestParam(required = false) Float braco, @RequestParam(required = false) Float antebraco,
            @RequestParam(required = false) Float cintura, @RequestParam(required = false) Float peito, @RequestParam(required = false) Float quadril,
            @RequestParam(required = false) Float coxa, @RequestParam(required = false) Float panturrilha, HttpSession sessao) {
        LocalDate registro = data(dataRegistro);
        if (!Float.isFinite(peso) || peso < 25 || peso > 400 || !numeroEntre(gordura, 0, 100) || registro == null || registro.isAfter(LocalDate.now())
                || !numeroEntre(braco, 0, 200) || !numeroEntre(antebraco, 0, 200) || !numeroEntre(cintura, 0, 300)
                || !numeroEntre(peito, 0, 300) || !numeroEntre(quadril, 0, 300) || !numeroEntre(coxa, 0, 200) || !numeroEntre(panturrilha, 0, 200)) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Confira peso, data e medidas informados."));
        }
        return executar(sessao, id -> { areaAluno.cadastrarProgresso(id, peso, gordura, registro, braco, antebraco, cintura, peito, quadril, coxa, panturrilha); return Map.of("mensagem", "Progresso registrado com sucesso."); });
    }

    private LocalDate data(String valor) { return valor == null || valor.isBlank() ? null : LocalDate.parse(valor); }
    private LocalDate validarObjetivo(String objetivo, Float pesoMeta, String dataMeta) {
        if (!objetivoValido(objetivo) || !numeroEntre(pesoMeta, 25, 400)) return null;
        try {
            LocalDate meta = data(dataMeta);
            return meta != null && meta.isBefore(LocalDate.now()) ? null : meta;
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }
    private boolean objetivoValido(String objetivo) { return List.of("Emagrecer", "Ganhar Massa", "Condicionamento", "Definicao", "Saude").contains(objetivo); }
    private boolean numeroEntre(Float valor, float minimo, float maximo) { return valor == null || (Float.isFinite(valor) && valor >= minimo && valor <= maximo); }
    private ResponseEntity<?> executar(HttpSession sessao, Consulta consulta) {
        Object usuario = sessao.getAttribute("usuarioId");
        if (!(usuario instanceof Integer)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", "Faca login para continuar."));
        try {
            Object resultado = consulta.executar((Integer) usuario);
            if (resultado instanceof ResponseEntity<?> resposta) return resposta;
            return ResponseEntity.ok(resultado);
        }
        catch (SQLException e) {
            System.err.println("Erro SQL ao acessar os dados da area do aluno: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensagem", "Nao foi possivel acessar os dados."));
        }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("mensagem", "Confira os dados informados.")); }
    }
    @FunctionalInterface private interface Consulta { Object executar(int usuarioId) throws SQLException; }
}
