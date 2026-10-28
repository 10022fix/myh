package Web;

import Control.DAOAreaAluno;
import Control.GeradorTreinoQuestionario;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
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
    @GetMapping("/api/agenda")
    public ResponseEntity<?> agendaSemanal(HttpSession sessao) {
        return executar(sessao, areaAluno::obterAgendaSemanal);
    }
    @PutMapping("/api/agenda")
    public ResponseEntity<?> salvarAgenda(@RequestParam List<Integer> dias, @RequestParam List<Integer> treinos,
            HttpSession sessao) {
        return executar(sessao, id -> {
            Map<String, Object> atual = areaAluno.obterAgendaSemanal(id);
            int frequencia = ((Number) atual.getOrDefault("frequencia", 0)).intValue();
            if (frequencia < 1 || frequencia > 7 || dias == null || treinos == null
                    || dias.size() != frequencia || treinos.size() != frequencia
                    || new java.util.HashSet<>(dias).size() != frequencia
                    || new java.util.HashSet<>(treinos).size() != frequencia
                    || dias.stream().anyMatch(dia -> dia == null || dia < 1 || dia > 7)) {
                return ResponseEntity.badRequest().body(Map.of("mensagem", "Selecione exatamente " + frequencia + " dias e associe um treino diferente a cada dia."));
            }
            LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
            if (!areaAluno.salvarAgendaSemanal(id, dias, treinos, hoje)) {
                return ResponseEntity.badRequest().body(Map.of("mensagem", "Escolha treinos ativos do seu plano para todos os dias selecionados."));
            }
            return Map.of("mensagem", "Sua programação semanal foi salva.");
        });
    }
    @PutMapping("/api/agenda/frequencia")
    public ResponseEntity<?> alterarFrequenciaSemanal(@RequestParam int frequencia, HttpSession sessao) {
        if (frequencia != 2 && frequencia != 3 && frequencia != 4 && frequencia != 5) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Escolha 2, 3, 4 ou 5 dias por semana, como no questionário."));
        }
        return executar(sessao, id -> {
            if (!areaAluno.atualizarFrequenciaSemanal(id, frequencia)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", "Responda ao questionário e defina uma meta antes de gerar os treinos da nova frequência."));
            }
            Map<String, Object> agenda = areaAluno.obterAgendaSemanal(id);
            return Map.of("mensagem", "Frequência alterada e treinos recomendados recriados com base no seu perfil.", "agenda", agenda);
        });
    }
    @GetMapping("/api/agenda/calendario")
    public ResponseEntity<?> calendarioTreinos(@RequestParam int ano, @RequestParam int mes, HttpSession sessao) {
        final YearMonth periodo;
        try { periodo = YearMonth.of(ano, mes); }
        catch (java.time.DateTimeException erro) { return ResponseEntity.badRequest().body(Map.of("mensagem", "Mês ou ano inválido.")); }
        return executar(sessao, id -> areaAluno.calendarioTreinos(id, periodo));
    }
    @PostMapping("/api/treinos/{treinoId}/concluir")
    public ResponseEntity<?> concluirTreino(@PathVariable int treinoId, HttpSession sessao) {
        if (treinoId <= 0) return ResponseEntity.badRequest().body(Map.of("mensagem", "Treino inválido."));
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        return executar(sessao, id -> areaAluno.registrarTreinoConcluido(id, treinoId, hoje)
                ? Map.of("mensagem", "Treino concluído com sucesso.")
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", "Treino não encontrado no seu plano.")));
    }
    @GetMapping("/api/treinos/recomendados")
    public ResponseEntity<?> recomendarTreinos(HttpSession sessao) {
        Object usuario = sessao.getAttribute("usuarioId");
        if (!(usuario instanceof Integer usuarioId)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", "Faça login para continuar."));
        try {
            var recomendacoes = areaAluno.recomendarTreinos(usuarioId);
            if (recomendacoes.isEmpty()) return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("mensagem", "Conclua o questionário e defina uma meta para receber recomendações personalizadas."));
            String token = UUID.randomUUID().toString();
            sessao.setAttribute("recomendacoesTreino", new RecomendacaoSessao(usuarioId, token, recomendacoes.get()));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("token", token, "treinos", recomendacoes.get().treinos(),
                            "catalogo", recomendacoes.get().exerciciosDisponiveis()));
        } catch (SQLException erro) {
            return erroBanco();
        }
    }
    @PostMapping("/api/treinos/recomendados")
    public ResponseEntity<?> adicionarRecomendado(@RequestParam String token, @RequestParam int indice, HttpSession sessao) {
        return aplicarRecomendacao(sessao, token, indice, null);
    }
    @PostMapping("/api/treinos/personalizado")
    public ResponseEntity<?> salvarTreinoPersonalizado(@RequestParam String token, @RequestParam String nome,
            @RequestParam int duracao, @RequestParam List<String> exercicios, HttpSession sessao) {
        Object usuario = sessao.getAttribute("usuarioId");
        if (!(usuario instanceof Integer usuarioId)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", "Faça login para continuar."));
        Object valor = sessao.getAttribute("recomendacoesTreino");
        if (!(valor instanceof RecomendacaoSessao estado) || estado.usuarioId() != usuarioId || !estado.token().equals(token)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", "As opções expiraram. Gere novas recomendações."));
        }
        String nomeFinal = nome == null ? "" : nome.trim();
        List<String> selecionados = exercicios == null ? List.of() : exercicios.stream().distinct().toList();
        if (nomeFinal.length() < 2 || nomeFinal.length() > 60 || duracao < 10 || duracao > 180
                || selecionados.size() < 3 || selecionados.size() > 12) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Informe um nome, duração e selecione de 3 a 12 exercícios."));
        }
        var catalogo = estado.recomendacoes().exerciciosDisponiveis().stream()
                .map(GeradorTreinoQuestionario.ExercicioDisponivel::nome).collect(java.util.stream.Collectors.toSet());
        if (!catalogo.containsAll(selecionados)) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Selecione apenas exercícios disponíveis para o seu perfil."));
        }
        List<GeradorTreinoQuestionario.ExercicioGerado> exerciciosGerados = selecionados.stream()
                .map(exercicio -> new GeradorTreinoQuestionario.ExercicioGerado(exercicio, 3, 12, 60)).toList();
        var treino = new GeradorTreinoQuestionario.TreinoGerado(nomeFinal,
                "Treino montado pelo aluno com exercícios compatíveis com o perfil informado.", duracao, exerciciosGerados);
        try {
            int novoId = areaAluno.adicionarTreinoPersonalizado(usuarioId, estado.recomendacoes(), treino);
            sessao.removeAttribute("recomendacoesTreino");
            return ResponseEntity.ok(Map.of("mensagem", "Seu treino foi adicionado ao plano.", "treinoId", novoId));
        } catch (SQLException erro) {
            return erroBanco();
        }
    }
    @PostMapping("/api/treinos/personalizado-livre")
    public ResponseEntity<?> salvarTreinoLivre(@RequestParam String token, @RequestParam String nome,
            @RequestParam int duracao, @RequestParam List<String> exercicios,
            @RequestParam List<Integer> series, @RequestParam List<Integer> repeticoes,
            @RequestParam List<Integer> descansos, HttpSession sessao) {
        Object usuario = sessao.getAttribute("usuarioId");
        if (!(usuario instanceof Integer usuarioId)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", "Faça login para continuar."));
        Object valor = sessao.getAttribute("recomendacoesTreino");
        if (!(valor instanceof RecomendacaoSessao estado) || estado.usuarioId() != usuarioId || !estado.token().equals(token)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", "As opções expiraram. Gere novas recomendações."));
        }
        String nomeFinal = nome == null ? "" : nome.trim();
        if (nomeFinal.length() < 2 || nomeFinal.length() > 60 || duracao < 10 || duracao > 180
                || exercicios == null || series == null || repeticoes == null || descansos == null
                || exercicios.isEmpty() || exercicios.size() > 12
                || series.size() != exercicios.size() || repeticoes.size() != exercicios.size()
                || descansos.size() != exercicios.size()) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Confira o nome, duração e os dados dos exercícios (de 1 a 12)."));
        }
        List<GeradorTreinoQuestionario.ExercicioGerado> lista = new java.util.ArrayList<>();
        for (int i = 0; i < exercicios.size(); i++) {
            String exercicio = exercicios.get(i) == null ? "" : exercicios.get(i).trim();
            int serie = series.get(i), repeticao = repeticoes.get(i), descanso = descansos.get(i);
            if (exercicio.isEmpty() || exercicio.length() > 120 || serie < 1 || serie > 10
                    || repeticao < 1 || repeticao > 100 || descanso < 0 || descanso > 300) {
                return ResponseEntity.badRequest().body(Map.of("mensagem", "Confira o nome e a prescrição de cada exercício."));
            }
            lista.add(new GeradorTreinoQuestionario.ExercicioGerado(exercicio, serie, repeticao, descanso));
        }
        var treino = new GeradorTreinoQuestionario.TreinoGerado(nomeFinal,
                "Treino criado pelo aluno. Confira os movimentos e a prescrição com um profissional qualificado.", duracao, lista);
        try {
            int novoId = areaAluno.adicionarTreinoPersonalizado(usuarioId, estado.recomendacoes(), treino);
            sessao.removeAttribute("recomendacoesTreino");
            return ResponseEntity.ok(Map.of("mensagem", "Seu treino foi adicionado ao plano.", "treinoId", novoId));
        } catch (SQLException erro) {
            return erroBanco();
        }
    }
    @PutMapping("/api/treinos/{treinoId}")
    public ResponseEntity<?> substituirTreino(@PathVariable int treinoId, @RequestParam String token,
            @RequestParam int indice, HttpSession sessao) {
        if (treinoId <= 0) return ResponseEntity.badRequest().body(Map.of("mensagem", "Treino inválido."));
        return aplicarRecomendacao(sessao, token, indice, treinoId);
    }
    @DeleteMapping("/api/treinos/{treinoId}")
    public ResponseEntity<?> excluirTreino(@PathVariable int treinoId, HttpSession sessao) {
        if (treinoId <= 0) return ResponseEntity.badRequest().body(Map.of("mensagem", "Treino inválido."));
        return executar(sessao, id -> areaAluno.excluirTreino(id, treinoId)
                ? Map.of("mensagem", "Treino removido do seu plano.")
                : ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", "Este treino está associado a um dia da sua programação. Troque a sessão na sua semana antes de excluí-lo.")));
    }
    @GetMapping("/api/objetivos")
    public ResponseEntity<?> objetivos(HttpSession sessao) { return executar(sessao, areaAluno::listarObjetivos); }
    @GetMapping("/api/progresso")
    public ResponseEntity<?> progresso(HttpSession sessao) { return executar(sessao, areaAluno::listarProgresso); }
    @GetMapping("/api/progresso/cargas")
    public ResponseEntity<?> cargasMes(@RequestParam int ano, @RequestParam int mes, HttpSession sessao) {
        final YearMonth periodo;
        try { periodo = YearMonth.of(ano, mes); }
        catch (java.time.DateTimeException erro) { return ResponseEntity.badRequest().body(Map.of("mensagem", "Mês ou ano inválido.")); }
        return executar(sessao, id -> areaAluno.listarCargasMes(id, periodo));
    }
    @GetMapping("/api/progresso/cargas/historico-dia")
    public ResponseEntity<?> historicoCargasDia(@RequestParam String data, HttpSession sessao) {
        LocalDate dia = data(data);
        if (dia == null || dia.isAfter(LocalDate.now(ZoneId.of("America/Sao_Paulo")))) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Escolha uma data válida, até hoje."));
        }
        return executar(sessao, id -> areaAluno.historicoCargasDia(id, dia));
    }
    @GetMapping("/api/progresso/cargas/opcoes")
    public ResponseEntity<?> opcoesCarga(@RequestParam String data, HttpSession sessao) {
        LocalDate dia = data(data);
        if (dia == null || dia.isAfter(LocalDate.now(ZoneId.of("America/Sao_Paulo")))) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Escolha uma data válida, até hoje."));
        }
        return executar(sessao, id -> areaAluno.opcoesCarga(id, dia));
    }
    @PostMapping("/api/progresso/cargas")
    public ResponseEntity<?> salvarCarga(@RequestParam int treinoId, @RequestParam int exercicioId,
            @RequestParam String data, @RequestParam float cargaKg, @RequestParam(required = false) Integer series,
            @RequestParam(required = false) Integer repeticoes, HttpSession sessao) {
        LocalDate dia = data(data);
        if (treinoId <= 0 || exercicioId <= 0 || dia == null || dia.isAfter(LocalDate.now(ZoneId.of("America/Sao_Paulo")))
                || !Float.isFinite(cargaKg) || cargaKg < 0 || cargaKg > 2000
                || (series != null && (series < 1 || series > 20))
                || (repeticoes != null && (repeticoes < 1 || repeticoes > 500))) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Confira a data, a carga, as séries e as repetições."));
        }
        return executar(sessao, id -> areaAluno.salvarCarga(id, treinoId, exercicioId, dia, cargaKg, series, repeticoes)
                ? Map.of("mensagem", "Carga salva para este exercício.")
                : ResponseEntity.badRequest().body(Map.of("mensagem", "Esse exercício não pertence a um treino programado ou concluído nessa data.")));
    }
    @DeleteMapping("/api/progresso/cargas/{cargaId}")
    public ResponseEntity<?> excluirCarga(@PathVariable long cargaId, HttpSession sessao) {
        if (cargaId <= 0) return ResponseEntity.badRequest().body(Map.of("mensagem", "Registro de carga inválido."));
        return executar(sessao, id -> areaAluno.excluirCarga(id, cargaId)
                ? Map.of("mensagem", "Registro de carga removido.")
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", "Registro de carga não encontrado.")));
    }
    @GetMapping("/api/painel/resumo")
    public ResponseEntity<?> resumo(HttpSession sessao) { return executar(sessao, areaAluno::resumoPainel); }

    @PostMapping("/api/objetivos")
    public ResponseEntity<?> cadastrarObjetivo(@RequestParam String objetivo, @RequestParam(required = false) Float pesoMeta,
            @RequestParam(required = false) String dataMeta, HttpSession sessao) {
        LocalDate meta = validarObjetivo(objetivo, pesoMeta, dataMeta);
        if (meta == null && dataMeta != null && !dataMeta.isBlank() || !objetivoValido(objetivo) || !numeroEntre(pesoMeta, 25, 400)) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "Informe objetivo, peso e data válidos."));
        }
        return executar(sessao, id -> areaAluno.cadastrarObjetivo(id, objetivo, pesoMeta, meta)
                ? Map.of("mensagem", "Meta cadastrada. O plano foi ajustado ao objetivo e ao seu perfil.")
                : ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "mensagem", "Você já possui uma meta. Altere ou exclua a meta atual antes de cadastrar outra.")));
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
                ? Map.of("mensagem", "Meta atualizada. Se você mudou o objetivo, novos treinos foram gerados; se mudou apenas o peso, os exercícios foram mantidos e a intensidade ajustada.") : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", "Meta não encontrada.")));
    }

    @DeleteMapping("/api/objetivos/{objetivoId}")
    public ResponseEntity<?> excluirObjetivo(@PathVariable int objetivoId, HttpSession sessao) {
        if (objetivoId <= 0) return ResponseEntity.badRequest().body(Map.of("mensagem", "Meta inválida."));
        return executar(sessao, id -> areaAluno.excluirObjetivo(id, objetivoId)
                ? Map.of("mensagem", "Meta excluída. Os treinos foram ajustados ao objetivo restante.") : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", "Meta não encontrada.")));
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
        return executar(sessao, id -> areaAluno.cadastrarProgresso(id, peso, gordura, registro, braco, antebraco, cintura, peito, quadril, coxa, panturrilha)
                ? Map.of("mensagem", "Progresso registrado com sucesso.")
                : ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", "Você já tem um registro de progresso nesta data. Exclua o registro existente se precisar corrigi-lo.")));
    }

    @DeleteMapping("/api/progresso/{progressoId}")
    public ResponseEntity<?> excluirProgresso(@PathVariable int progressoId, HttpSession sessao) {
        if (progressoId <= 0) return ResponseEntity.badRequest().body(Map.of("mensagem", "Registro inválido."));
        return executar(sessao, id -> areaAluno.excluirProgresso(id, progressoId)
                ? Map.of("mensagem", "Registro de progresso excluído.")
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", "Registro não encontrado.")));
    }

    private LocalDate data(String valor) { return valor == null || valor.isBlank() ? null : LocalDate.parse(valor); }
    private ResponseEntity<?> aplicarRecomendacao(HttpSession sessao, String token, int indice, Integer substituirId) {
        Object usuario = sessao.getAttribute("usuarioId");
        if (!(usuario instanceof Integer usuarioId)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", "Faça login para continuar."));
        Object valor = sessao.getAttribute("recomendacoesTreino");
        if (!(valor instanceof RecomendacaoSessao estado) || estado.usuarioId() != usuarioId || !estado.token().equals(token)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensagem", "As recomendações expiraram. Gere novas opções."));
        }
        if (indice < 0 || indice >= estado.recomendacoes().treinos().size()) return ResponseEntity.badRequest().body(Map.of("mensagem", "Opção de treino inválida."));
        try {
            int novoId = substituirId == null
                    ? areaAluno.adicionarTreinoRecomendado(usuarioId, estado.recomendacoes(), indice)
                    : areaAluno.substituirTreinoRecomendado(usuarioId, substituirId, estado.recomendacoes(), indice);
            if (novoId == 0) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensagem", "Treino não encontrado no seu plano."));
            sessao.removeAttribute("recomendacoesTreino");
            return ResponseEntity.ok(Map.of("mensagem", substituirId == null ? "Treino recomendado adicionado ao seu plano." : "Treino substituído pela recomendação.", "treinoId", novoId));
        } catch (SQLException erro) {
            return erroBanco();
        }
    }
    private ResponseEntity<?> erroBanco() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("mensagem", "Não foi possível acessar os dados."));
    }
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
    private record RecomendacaoSessao(int usuarioId, String token, DAOAreaAluno.Recomendacoes recomendacoes) { }
}
