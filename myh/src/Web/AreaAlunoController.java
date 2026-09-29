package Web;

import Control.DAOAreaAluno;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AreaAlunoController {
    private final DAOAreaAluno areaAluno = new DAOAreaAluno();

    @GetMapping("/api/treinos")
    public ResponseEntity<?> treinos(@RequestParam(defaultValue = "") String busca, HttpSession sessao) { return executar(sessao, id -> areaAluno.listarTreinos(id, busca)); }
    @GetMapping("/api/objetivos")
    public ResponseEntity<?> objetivos(HttpSession sessao) { return executar(sessao, areaAluno::listarObjetivos); }
    @GetMapping("/api/progresso")
    public ResponseEntity<?> progresso(HttpSession sessao) { return executar(sessao, areaAluno::listarProgresso); }
    @GetMapping("/api/painel/resumo")
    public ResponseEntity<?> resumo(HttpSession sessao) { return executar(sessao, areaAluno::resumoPainel); }

    @PostMapping("/api/objetivos")
    public ResponseEntity<?> cadastrarObjetivo(@RequestParam String objetivo, @RequestParam(required = false) Float pesoMeta,
            @RequestParam(required = false) String dataMeta, HttpSession sessao) {
        if (!List.of("Emagrecer", "Ganhar Massa", "Condicionamento", "Definicao", "Saude").contains(objetivo)) return ResponseEntity.badRequest().body(Map.of("mensagem", "Objetivo invalido."));
        LocalDate meta = data(dataMeta);
        if (!numeroEntre(pesoMeta, 25, 400) || (meta != null && meta.isBefore(LocalDate.now()))) return ResponseEntity.badRequest().body(Map.of("mensagem", "Informe uma meta de peso e data válidas."));
        return executar(sessao, id -> { areaAluno.cadastrarObjetivo(id, objetivo, pesoMeta, meta); return Map.of("mensagem", "Objetivo cadastrado com sucesso."); });
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
    private boolean numeroEntre(Float valor, float minimo, float maximo) { return valor == null || (Float.isFinite(valor) && valor >= minimo && valor <= maximo); }
    private ResponseEntity<?> executar(HttpSession sessao, Consulta consulta) {
        Object usuario = sessao.getAttribute("usuarioId");
        if (!(usuario instanceof Integer)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", "Faca login para continuar."));
        try { return ResponseEntity.ok(consulta.executar((Integer) usuario)); }
        catch (SQLException e) {
            System.err.println("Erro SQL ao acessar os dados da area do aluno: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensagem", "Nao foi possivel acessar os dados."));
        }
        catch (RuntimeException e) { return ResponseEntity.badRequest().body(Map.of("mensagem", "Confira os dados informados.")); }
    }
    @FunctionalInterface private interface Consulta { Object executar(int usuarioId) throws SQLException; }
}
