package Control;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/** Consultas e registros usados na area autenticada do aluno. */
public class DAOAreaAluno {
    private final frmConsultas consultas = new frmConsultas();

    public record Recomendacoes(String objetivo, String local, int frequencia,
            List<GeradorTreinoQuestionario.TreinoGerado> treinos,
            List<GeradorTreinoQuestionario.ExercicioDisponivel> exerciciosDisponiveis) { }

    /** Gera opções novas usando o perfil do questionário e a meta mais recente do aluno. */
    public Optional<Recomendacoes> recomendarTreinos(int usuarioId) throws SQLException {
        String sql = "SELECT q.respostas, q.local_treino, q.frequencia_semanal, q.duracao_preferida, "
                + "(SELECT o.objetivo FROM objetivos o WHERE o.usuario_id=q.usuario_id ORDER BY o.id DESC LIMIT 1) objetivo "
                + "FROM questionario_usuario q WHERE q.usuario_id=?";
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId);
            String respostas;
            String objetivo;
            String local;
            int frequencia;
            int duracao;
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next() || resultado.getString("objetivo") == null) return Optional.empty();
                respostas = resultado.getString("respostas");
                objetivo = resultado.getString("objetivo");
                local = resultado.getString("local_treino");
                frequencia = resultado.getInt("frequencia_semanal");
                duracao = resultado.getInt("duracao_preferida");
            }
            Set<String> exerciciosAtivos = new HashSet<>();
            String exerciciosSql = "SELECT DISTINCT e.nome FROM usuario_treinos ut "
                    + "JOIN treino_exercicios te ON te.treino_id=ut.treino_id "
                    + "JOIN exercicios e ON e.id=te.exercicio_id WHERE ut.usuario_id=? AND ut.ativo=TRUE";
            try (PreparedStatement ativos = conexao.prepareStatement(exerciciosSql)) {
                ativos.setInt(1, usuarioId);
                try (ResultSet resultadoAtivos = ativos.executeQuery()) {
                    while (resultadoAtivos.next()) exerciciosAtivos.add(resultadoAtivos.getString("nome"));
                }
            }
            List<GeradorTreinoQuestionario.TreinoGerado> treinos = GeradorTreinoQuestionario.gerar(
                    respostas, objetivo, local, duracao, exerciciosAtivos);
            if (treinos.isEmpty()) return Optional.empty();
            List<GeradorTreinoQuestionario.ExercicioDisponivel> catalogo =
                    GeradorTreinoQuestionario.exerciciosDisponiveis(respostas, objetivo, local, duracao);
            return Optional.of(new Recomendacoes(objetivo, local, frequencia, treinos, catalogo));
        }
    }

    public int adicionarTreinoRecomendado(int usuarioId, Recomendacoes recomendacoes, int indice) throws SQLException {
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                int id = persistirRecomendacao(conexao, usuarioId, recomendacoes, indice);
                conexao.commit();
                return id;
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    public int adicionarTreinoPersonalizado(int usuarioId, Recomendacoes recomendacoes,
            GeradorTreinoQuestionario.TreinoGerado treino) throws SQLException {
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                int id = persistirTreino(conexao, usuarioId, recomendacoes, treino, "PROPRIO");
                conexao.commit();
                return id;
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    /** Substitui somente o vínculo ativo do usuário; o treino antigo e conclusões ficam no histórico. */
    public int substituirTreinoRecomendado(int usuarioId, int treinoAntigoId,
            Recomendacoes recomendacoes, int indice) throws SQLException {
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                try (PreparedStatement desativar = conexao.prepareStatement(
                        "UPDATE usuario_treinos SET ativo=FALSE WHERE usuario_id=? AND treino_id=? AND ativo=TRUE")) {
                    desativar.setInt(1, usuarioId);
                    desativar.setInt(2, treinoAntigoId);
                    if (desativar.executeUpdate() != 1) {
                        conexao.rollback();
                        return 0;
                    }
                }
                int id = persistirRecomendacao(conexao, usuarioId, recomendacoes, indice);
                try (PreparedStatement atual = conexao.prepareStatement(
                        "SELECT id FROM agenda_treino_semana WHERE usuario_id=? AND vigente_desde<=CURDATE() ORDER BY vigente_desde DESC,id DESC LIMIT 1")) {
                    atual.setInt(1, usuarioId);
                    try (ResultSet rs = atual.executeQuery()) {
                        if (rs.next()) try (PreparedStatement trocar = conexao.prepareStatement(
                                "UPDATE agenda_treino_dia SET treino_id=? WHERE agenda_id=? AND treino_id=?")) {
                            trocar.setInt(1, id); trocar.setLong(2, rs.getLong("id")); trocar.setInt(3, treinoAntigoId); trocar.executeUpdate();
                        }
                    }
                }
                conexao.commit();
                return id;
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    /** Exclusão lógica individual: mantém o treino e as conclusões para preservar o histórico. */
    public boolean excluirTreino(int usuarioId, int treinoId) throws SQLException {
        try (Connection conexao = consultas.abreConexao()) {
            try (PreparedStatement agendado = conexao.prepareStatement(
                    "SELECT 1 FROM agenda_treino_dia d JOIN agenda_treino_semana a ON a.id=d.agenda_id "
                            + "WHERE a.id=(SELECT id FROM agenda_treino_semana WHERE usuario_id=? AND vigente_desde<=CURDATE() "
                            + "ORDER BY vigente_desde DESC,id DESC LIMIT 1) AND d.treino_id=? LIMIT 1")) {
                agendado.setInt(1, usuarioId); agendado.setInt(2, treinoId);
                try (ResultSet rs = agendado.executeQuery()) { if (rs.next()) return false; }
            }
            try (PreparedStatement comando = conexao.prepareStatement(
                    "UPDATE usuario_treinos SET ativo=FALSE WHERE usuario_id=? AND treino_id=? AND ativo=TRUE")) {
                comando.setInt(1, usuarioId); comando.setInt(2, treinoId);
                return comando.executeUpdate() == 1;
            }
        }
    }

    private int persistirRecomendacao(Connection conexao, int usuarioId, Recomendacoes recomendacoes, int indice) throws SQLException {
        if (indice < 0 || indice >= recomendacoes.treinos().size()) throw new SQLException("Recomendação inválida.");
        return persistirTreino(conexao, usuarioId, recomendacoes, recomendacoes.treinos().get(indice), "RECOMENDADO");
    }

    private int persistirTreino(Connection conexao, int usuarioId, Recomendacoes recomendacoes,
            GeradorTreinoQuestionario.TreinoGerado treino, String origem) throws SQLException {
        int treinoId = inserirTreino(conexao, treino, recomendacoes.objetivo(), recomendacoes.local(), recomendacoes.frequencia(), origem);
        for (GeradorTreinoQuestionario.ExercicioGerado exercicio : treino.exercicios()) {
            int exercicioId = obterOuCriarExercicio(conexao, exercicio.nome());
            try (PreparedStatement associar = conexao.prepareStatement(
                    "INSERT INTO treino_exercicios (treino_id, exercicio_id, series, repeticoes, tempo_descanso) VALUES (?, ?, ?, ?, ?)")) {
                associar.setInt(1, treinoId);
                associar.setInt(2, exercicioId);
                associar.setInt(3, exercicio.series());
                associar.setInt(4, exercicio.repeticoes());
                associar.setInt(5, exercicio.descanso());
                associar.executeUpdate();
            }
        }
        try (PreparedStatement vincular = conexao.prepareStatement(
                "INSERT INTO usuario_treinos (usuario_id, treino_id, data_inicio, ativo) VALUES (?, ?, CURDATE(), TRUE)")) {
            vincular.setInt(1, usuarioId);
            vincular.setInt(2, treinoId);
            vincular.executeUpdate();
        }
        return treinoId;
    }

    public List<Map<String, Object>> listarTreinos(int usuarioId, String busca) throws SQLException {
        String filtro = busca == null ? "" : busca.trim();
        String sql = "SELECT DISTINCT t.id, t.nome_treino, t.descricao, t.nivel, t.duracao_treino "
                + "FROM usuario_treinos ut JOIN treinos t ON t.id = ut.treino_id "
                + "WHERE ut.usuario_id = ? AND ut.ativo = TRUE "
                + "AND (t.nome_treino LIKE ? OR t.descricao LIKE ? OR EXISTS ("
                + "SELECT 1 FROM treino_exercicios te JOIN exercicios e ON e.id = te.exercicio_id "
                + "WHERE te.treino_id = t.id AND e.nome LIKE ?)) ORDER BY t.nome_treino";
        List<Map<String, Object>> treinos = new ArrayList<>();
        try (Connection conexao = consultas.abreConexao();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId);
            String termo = "%" + filtro + "%";
            comando.setString(2, termo);
            comando.setString(3, termo);
            comando.setString(4, termo);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    Map<String, Object> treino = new LinkedHashMap<>();
                    int treinoId = resultado.getInt("id");
                    treino.put("id", treinoId);
                    treino.put("nome", resultado.getString("nome_treino"));
                    treino.put("descricao", resultado.getString("descricao"));
                    treino.put("nivel", resultado.getString("nivel"));
                    treino.put("duracao", resultado.getInt("duracao_treino"));
                    treino.put("exercicios", listarExercicios(conexao, treinoId));
                    treinos.add(treino);
                }
            }
        }
        return treinos;
    }

    private List<Map<String, Object>> listarExercicios(Connection conexao, int treinoId) throws SQLException {
        String sql = "SELECT e.nome, te.series, te.repeticoes, te.tempo_descanso "
                + "FROM treino_exercicios te JOIN exercicios e ON e.id = te.exercicio_id "
                + "WHERE te.treino_id = ? ORDER BY te.id";
        List<Map<String, Object>> exercicios = new ArrayList<>();
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, treinoId);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    Map<String, Object> exercicio = new LinkedHashMap<>();
                    exercicio.put("nome", resultado.getString("nome"));
                    exercicio.put("series", resultado.getInt("series"));
                    exercicio.put("repeticoes", resultado.getInt("repeticoes"));
                    exercicio.put("descanso", resultado.getInt("tempo_descanso"));
                    exercicios.add(exercicio);
                }
            }
        }
        return exercicios;
    }

    public List<Map<String, Object>> listarObjetivos(int usuarioId) throws SQLException {
        String sql = "SELECT id, objetivo, peso_meta, data_meta FROM objetivos WHERE usuario_id = ? ORDER BY id DESC";
        List<Map<String, Object>> objetivos = new ArrayList<>();
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    Map<String, Object> objetivo = new LinkedHashMap<>();
                    objetivo.put("id", resultado.getInt("id"));
                    objetivo.put("objetivo", resultado.getString("objetivo"));
                    objetivo.put("pesoMeta", resultado.getObject("peso_meta"));
                    objetivo.put("dataMeta", resultado.getObject("data_meta") == null ? null : resultado.getDate("data_meta").toLocalDate().toString());
                    objetivos.add(objetivo);
                }
            }
        }
        return objetivos;
    }

    public boolean registrarTreinoConcluido(int usuarioId, int treinoId, LocalDate dataConclusao) throws SQLException {
        String agendaAtual = "SELECT d.treino_id FROM agenda_treino_semana a JOIN agenda_treino_dia d ON d.agenda_id=a.id "
                + "WHERE a.id=(SELECT id FROM agenda_treino_semana WHERE usuario_id=? AND vigente_desde<=? "
                + "ORDER BY vigente_desde DESC,id DESC LIMIT 1) AND d.dia_semana=? LIMIT 1";
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(agendaAtual)) {
            comando.setInt(1, usuarioId); comando.setDate(2, Date.valueOf(dataConclusao));
            comando.setInt(3, dataConclusao.getDayOfWeek().getValue());
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next() || resultado.getInt("treino_id") != treinoId) return false;
            }
        }
        String verificar = "SELECT 1 FROM usuario_treinos WHERE usuario_id = ? AND treino_id = ? AND ativo = TRUE LIMIT 1";
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(verificar)) {
            comando.setInt(1, usuarioId);
            comando.setInt(2, treinoId);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) return false;
            }
        }

        String inserir = "INSERT INTO treinos_concluidos (usuario_id, treino_id, data_conclusao) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)";
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(inserir)) {
            comando.setInt(1, usuarioId);
            comando.setInt(2, treinoId);
            comando.setDate(3, Date.valueOf(dataConclusao));
            comando.executeUpdate();
            return true;
        }
    }

    public List<Map<String, Object>> listarTreinosConcluidos(int usuarioId, LocalDate inicio, LocalDate fim) throws SQLException {
        String sql = "SELECT tc.treino_id, t.nome_treino, tc.data_conclusao FROM treinos_concluidos tc "
                + "JOIN treinos t ON t.id = tc.treino_id WHERE tc.usuario_id = ? "
                + "AND tc.data_conclusao BETWEEN ? AND ? ORDER BY tc.data_conclusao, tc.id";
        List<Map<String, Object>> concluidos = new ArrayList<>();
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId);
            comando.setDate(2, Date.valueOf(inicio));
            comando.setDate(3, Date.valueOf(fim));
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    Map<String, Object> treino = new LinkedHashMap<>();
                    treino.put("treinoId", resultado.getInt("treino_id"));
                    treino.put("nome", resultado.getString("nome_treino"));
                    treino.put("data", resultado.getDate("data_conclusao").toLocalDate().toString());
                    concluidos.add(treino);
                }
            }
        }
        return concluidos;
    }

    public boolean cadastrarObjetivo(int usuarioId, String objetivo, Float pesoMeta, LocalDate dataMeta) throws SQLException {
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                // Serializa novas metas por usuário, inclusive quando ainda não existe uma linha em objetivos.
                try (PreparedStatement bloquearUsuario = conexao.prepareStatement(
                        "SELECT id FROM usuario WHERE id=? FOR UPDATE")) {
                    bloquearUsuario.setInt(1, usuarioId);
                    try (ResultSet usuario = bloquearUsuario.executeQuery()) {
                        if (!usuario.next()) { conexao.rollback(); return false; }
                    }
                }
                try (PreparedStatement existente = conexao.prepareStatement(
                        "SELECT id FROM objetivos WHERE usuario_id=? LIMIT 1 FOR UPDATE")) {
                    existente.setInt(1, usuarioId);
                    try (ResultSet meta = existente.executeQuery()) {
                        if (meta.next()) { conexao.rollback(); return false; }
                    }
                }
                MetaObjetivo anterior = buscarMetaObjetivo(conexao, usuarioId, null);
                try (PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO objetivos (usuario_id, objetivo, peso_meta, data_meta) VALUES (?, ?, ?, ?)")) {
                    comando.setInt(1, usuarioId);
                    comando.setString(2, objetivo);
                    if (pesoMeta == null) comando.setNull(3, java.sql.Types.DECIMAL); else comando.setFloat(3, pesoMeta);
                    if (dataMeta == null) comando.setNull(4, java.sql.Types.DATE); else comando.setDate(4, Date.valueOf(dataMeta));
                    comando.executeUpdate();
                }
                MetaObjetivo atual = buscarMetaObjetivo(conexao, usuarioId, null);
                ajustarTreinosAposMudancaMeta(conexao, usuarioId, anterior, atual);
                conexao.commit();
                return true;
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    public boolean atualizarObjetivo(int usuarioId, int objetivoId, String objetivo, Float pesoMeta, LocalDate dataMeta) throws SQLException {
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                MetaObjetivo anterior = buscarMetaObjetivo(conexao, usuarioId, objetivoId);
                if (anterior == null) { conexao.rollback(); return false; }
                MetaObjetivo objetivoAtivoAntes = buscarMetaObjetivo(conexao, usuarioId, null);
                try (PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE objetivos SET objetivo=?, peso_meta=?, data_meta=? WHERE id=? AND usuario_id=?")) {
                    comando.setString(1, objetivo);
                    if (pesoMeta == null) comando.setNull(2, java.sql.Types.DECIMAL); else comando.setFloat(2, pesoMeta);
                    if (dataMeta == null) comando.setNull(3, java.sql.Types.DATE); else comando.setDate(3, Date.valueOf(dataMeta));
                    comando.setInt(4, objetivoId);
                    comando.setInt(5, usuarioId);
                    if (comando.executeUpdate() != 1) { conexao.rollback(); return false; }
                }
                if (objetivoAtivoAntes != null && objetivoAtivoAntes.id() == objetivoId) {
                    MetaObjetivo atual = buscarMetaObjetivo(conexao, usuarioId, null);
                    ajustarTreinosAposMudancaMeta(conexao, usuarioId, anterior, atual);
                }
                conexao.commit();
                return true;
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    public boolean excluirObjetivo(int usuarioId, int objetivoId) throws SQLException {
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                MetaObjetivo anterior = buscarMetaObjetivo(conexao, usuarioId, null);
                try (PreparedStatement comando = conexao.prepareStatement(
                        "DELETE FROM objetivos WHERE id=? AND usuario_id=?")) {
                    comando.setInt(1, objetivoId);
                    comando.setInt(2, usuarioId);
                    if (comando.executeUpdate() != 1) { conexao.rollback(); return false; }
                }
                if (anterior != null && anterior.id() == objetivoId) {
                    MetaObjetivo atual = buscarMetaObjetivo(conexao, usuarioId, null);
                    ajustarTreinosAposMudancaMeta(conexao, usuarioId, anterior, atual);
                }
                conexao.commit();
                return true;
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    private MetaObjetivo buscarMetaObjetivo(Connection conexao, int usuarioId, Integer objetivoId) throws SQLException {
        String sql = "SELECT id,objetivo,peso_meta FROM objetivos WHERE usuario_id=?"
                + (objetivoId == null ? " ORDER BY id DESC LIMIT 1" : " AND id=?") + " FOR UPDATE";
        try (PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setInt(1, usuarioId);
            if (objetivoId != null) consulta.setInt(2, objetivoId);
            try (ResultSet rs = consulta.executeQuery()) {
                if (!rs.next()) return null;
                Number peso = (Number) rs.getObject("peso_meta");
                return new MetaObjetivo(rs.getInt("id"), rs.getString("objetivo"),
                        peso == null ? null : peso.floatValue());
            }
        }
    }

    private void ajustarTreinosAposMudancaMeta(Connection conexao, int usuarioId,
            MetaObjetivo anterior, MetaObjetivo atual) throws SQLException {
        if (atual == null) {
            regenerarTreinosDaMetaAtual(conexao, usuarioId, null);
            return;
        }
        if (anterior == null) {
            regenerarTreinosDaMetaAtual(conexao, usuarioId, null);
            return;
        }
        if (!categoriaObjetivo(anterior.objetivo()).equals(categoriaObjetivo(atual.objetivo()))) {
            if (!regenerarTreinosDaMetaAtual(conexao, usuarioId, null, true)) {
                throw new SQLException("Não foi possível gerar um novo plano com base no questionário.");
            }
            return;
        }
        boolean mudouPesoAlvo = atual.pesoMeta() != null
                && (anterior.pesoMeta() == null || Float.compare(anterior.pesoMeta(), atual.pesoMeta()) != 0);
        if (mudouPesoAlvo && !intensificarTreinosRecomendados(conexao, usuarioId)) {
            regenerarTreinosDaMetaAtual(conexao, usuarioId, null);
        }
    }

    private String categoriaObjetivo(String objetivo) {
        if (objetivo == null) return "";
        String chave = Normalizer.normalize(objetivo.trim().toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('_', ' ');
        return switch (chave) {
            case "saude", "saude e disposicao" -> "saude";
            case "manter meu peso", "manter peso" -> "manter_peso";
            case "definicao" -> "definicao";
            default -> chave;
        };
    }

    /** Mantém os exercícios e sobe o volume em um passo, com limite de 4 séries e 15 repetições. */
    private boolean intensificarTreinosRecomendados(Connection conexao, int usuarioId) throws SQLException {
        int ativos;
        try (PreparedStatement consulta = conexao.prepareStatement(
                "SELECT COUNT(DISTINCT ut.treino_id) FROM usuario_treinos ut "
                        + "JOIN treinos t ON t.id=ut.treino_id "
                        + "WHERE ut.usuario_id=? AND ut.ativo=TRUE AND t.origem='RECOMENDADO'")) {
            consulta.setInt(1, usuarioId);
            try (ResultSet rs = consulta.executeQuery()) { rs.next(); ativos = rs.getInt(1); }
        }
        if (ativos == 0) return false;
        try (PreparedStatement atualizarRepeticoes = conexao.prepareStatement(
                "UPDATE treino_exercicios te "
                        + "JOIN usuario_treinos ut ON ut.treino_id=te.treino_id AND ut.usuario_id=? AND ut.ativo=TRUE "
                        + "JOIN treinos t ON t.id=te.treino_id "
                        + "SET te.repeticoes=LEAST(te.repeticoes+1,15) "
                        + "WHERE t.origem='RECOMENDADO' AND te.repeticoes>0 AND te.series>=4")) {
            atualizarRepeticoes.setInt(1, usuarioId);
            atualizarRepeticoes.executeUpdate();
        }
        try (PreparedStatement atualizarSeries = conexao.prepareStatement(
                "UPDATE treino_exercicios te "
                        + "JOIN usuario_treinos ut ON ut.treino_id=te.treino_id AND ut.usuario_id=? AND ut.ativo=TRUE "
                        + "JOIN treinos t ON t.id=te.treino_id "
                        + "SET te.series=LEAST(te.series+1,4) "
                        + "WHERE t.origem='RECOMENDADO' AND te.repeticoes>0")) {
            atualizarSeries.setInt(1, usuarioId);
            atualizarSeries.executeUpdate();
        }
        return true;
    }
    public List<Map<String, Object>> listarProgresso(int usuarioId) throws SQLException {
        String sql = "SELECT p.id, p.peso_atual, p.percentual_gordura, p.data_registro, m.braco, m.antebraco, m.cintura, m.peito, m.quadril, m.coxa, m.panturrilha "
                + "FROM progressao p JOIN medidasC m ON m.id = p.medidasC_id WHERE p.usuario_id = ? ORDER BY p.data_registro DESC, p.id DESC";
        List<Map<String, Object>> progresso = new ArrayList<>();
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    Map<String, Object> registro = new LinkedHashMap<>();
                    registro.put("id", resultado.getInt("id"));
                    registro.put("peso", resultado.getFloat("peso_atual"));
                    registro.put("gordura", resultado.getObject("percentual_gordura"));
                    registro.put("data", resultado.getDate("data_registro").toLocalDate().toString());
                    registro.put("cintura", resultado.getObject("cintura"));
                    registro.put("peito", resultado.getObject("peito"));
                    registro.put("braco", resultado.getObject("braco"));
                    registro.put("antebraco", resultado.getObject("antebraco"));
                    registro.put("quadril", resultado.getObject("quadril"));
                    registro.put("coxa", resultado.getObject("coxa"));
                    registro.put("panturrilha", resultado.getObject("panturrilha"));
                    progresso.add(registro);
                }
            }
        }
        return progresso;
    }

    /** Salva o questionario, cria a meta e persiste as sessões geradas para o usuário. */
    public boolean finalizarQuestionario(int usuarioId, String objetivo, float pesoMeta, int duracao,
            String formaAtual, String formaDesejada, String frequencia, String local, String respostasJson,
            LocalDate nascimento, String sexo, float altura, float pesoInicial) throws SQLException {
        String perfilSql = "INSERT INTO questionario_usuario (usuario_id, forma_atual, forma_desejada, frequencia_semanal, local_treino, duracao_preferida, respostas) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE forma_atual=VALUES(forma_atual), forma_desejada=VALUES(forma_desejada), frequencia_semanal=VALUES(frequencia_semanal), local_treino=VALUES(local_treino), duracao_preferida=VALUES(duracao_preferida), respostas=VALUES(respostas)";
        String objetivoSql = "INSERT INTO objetivos (usuario_id, objetivo, peso_meta) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM objetivos WHERE usuario_id = ?)";
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                // Usa a mesma trava do cadastro de meta para impedir duplicidade entre
                // a finalização do questionário e o formulário Minhas Metas.
                try (PreparedStatement bloquearUsuario = conexao.prepareStatement(
                        "SELECT id FROM usuario WHERE id=? FOR UPDATE")) {
                    bloquearUsuario.setInt(1, usuarioId);
                    try (ResultSet usuario = bloquearUsuario.executeQuery()) {
                        if (!usuario.next()) throw new SQLException("Usuário não encontrado.");
                    }
                }
                try (PreparedStatement perfil = conexao.prepareStatement(perfilSql);
                     PreparedStatement meta = conexao.prepareStatement(objetivoSql);
                     PreparedStatement dados = conexao.prepareStatement(
                             "UPDATE usuario SET data_nascimento=?, sexo=?, altura=?, peso_inicial=? WHERE id=?")) {
                    dados.setDate(1, Date.valueOf(nascimento));
                    dados.setString(2, sexo);
                    dados.setFloat(3, altura);
                    dados.setFloat(4, pesoInicial);
                    dados.setInt(5, usuarioId);
                    if (dados.executeUpdate() != 1) throw new SQLException("Usuario nao encontrado.");

                    perfil.setInt(1, usuarioId);
                    perfil.setString(2, formaAtual);
                    perfil.setString(3, formaDesejada);
                    perfil.setInt(4, Integer.parseInt(frequencia));
                    perfil.setString(5, local);
                    perfil.setInt(6, duracao);
                    perfil.setString(7, respostasJson);
                    perfil.executeUpdate();

                    meta.setInt(1, usuarioId);
                    meta.setString(2, objetivo);
                    meta.setFloat(3, pesoMeta);
                    meta.setInt(4, usuarioId);
                    meta.executeUpdate();
                }

                boolean treinoAtribuido = gerarEAtribuirTreinos(
                        conexao, usuarioId, objetivo, local, Integer.parseInt(frequencia), duracao, respostasJson);
                conexao.commit();
                return treinoAtribuido;
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    private boolean gerarEAtribuirTreinos(Connection conexao, int usuarioId, String objetivo,
            String local, int frequencia, int duracao, String respostasJson) throws SQLException {
        try (PreparedStatement ativos = conexao.prepareStatement(
                "SELECT 1 FROM usuario_treinos ut JOIN treinos t ON t.id = ut.treino_id "
                        + "WHERE ut.usuario_id = ? AND ut.ativo = TRUE AND t.nivel = 'Personalizado' LIMIT 1")) {
            ativos.setInt(1, usuarioId);
            try (ResultSet resultado = ativos.executeQuery()) {
                // Uma repetição do envio não deve criar outra cópia das sessões geradas.
                if (resultado.next()) return true;
            }
        }

        // Na primeira conclusão, troca um treino genérico eventualmente associado por sessões personalizadas.
        try (PreparedStatement desativar = conexao.prepareStatement(
                "UPDATE usuario_treinos SET ativo = FALSE WHERE usuario_id = ? AND ativo = TRUE")) {
            desativar.setInt(1, usuarioId);
            desativar.executeUpdate();
        }

        List<GeradorTreinoQuestionario.TreinoGerado> treinos =
                GeradorTreinoQuestionario.gerar(respostasJson, objetivo, local, duracao, frequencia, Set.of());
        if (treinos.isEmpty()) throw new SQLException("O gerador não produziu sessões de treino.");

        for (GeradorTreinoQuestionario.TreinoGerado treino : treinos) {
            int treinoId = inserirTreino(conexao, treino, objetivo, local, frequencia);
            for (GeradorTreinoQuestionario.ExercicioGerado exercicio : treino.exercicios()) {
                int exercicioId = obterOuCriarExercicio(conexao, exercicio.nome());
                try (PreparedStatement associar = conexao.prepareStatement(
                        "INSERT INTO treino_exercicios (treino_id, exercicio_id, series, repeticoes, tempo_descanso) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    associar.setInt(1, treinoId);
                    associar.setInt(2, exercicioId);
                    associar.setInt(3, exercicio.series());
                    associar.setInt(4, exercicio.repeticoes());
                    associar.setInt(5, exercicio.descanso());
                    associar.executeUpdate();
                }
            }
            try (PreparedStatement vincular = conexao.prepareStatement(
                    "INSERT INTO usuario_treinos (usuario_id, treino_id, data_inicio, ativo) "
                            + "VALUES (?, ?, CURDATE(), TRUE)")) {
                vincular.setInt(1, usuarioId);
                vincular.setInt(2, treinoId);
                vincular.executeUpdate();
            }
        }
        return true;
    }

    private int inserirTreino(Connection conexao, GeradorTreinoQuestionario.TreinoGerado treino,
            String objetivo, String local, int frequencia) throws SQLException {
        return inserirTreino(conexao, treino, objetivo, local, frequencia, "RECOMENDADO");
    }

    private int inserirTreino(Connection conexao, GeradorTreinoQuestionario.TreinoGerado treino,
            String objetivo, String local, int frequencia, String origem) throws SQLException {
        String sql = "INSERT INTO treinos (nome_treino, descricao, nivel, duracao_treino, objetivo, local_treino, frequencia_semanal, origem) "
                + "VALUES (?, ?, 'Personalizado', ?, ?, ?, ?, ?)";
        try (PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            comando.setString(1, treino.nome());
            comando.setString(2, treino.descricao());
            comando.setInt(3, treino.duracao());
            comando.setString(4, objetivo);
            comando.setString(5, local);
            comando.setInt(6, frequencia);
            comando.setString(7, origem);
            comando.executeUpdate();
            try (ResultSet chaves = comando.getGeneratedKeys()) {
                if (!chaves.next()) throw new SQLException("Não foi possível criar a sessão de treino.");
                return chaves.getInt(1);
            }
        }
    }

    private int obterOuCriarExercicio(Connection conexao, String nome) throws SQLException {
        try (PreparedStatement buscar = conexao.prepareStatement(
                "SELECT id FROM exercicios WHERE nome = ? ORDER BY id LIMIT 1")) {
            buscar.setString(1, nome);
            try (ResultSet resultado = buscar.executeQuery()) {
                if (resultado.next()) return resultado.getInt("id");
            }
        }
        try (PreparedStatement inserir = conexao.prepareStatement(
                "INSERT INTO exercicios (nome) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            inserir.setString(1, nome);
            inserir.executeUpdate();
            try (ResultSet chaves = inserir.getGeneratedKeys()) {
                if (!chaves.next()) throw new SQLException("Não foi possível cadastrar o exercício.");
                return chaves.getInt(1);
            }
        }
    }
    
    

    /** Reune peso inicial, ultimo registro e meta para o resumo da pagina principal. */
    public Map<String, Object> resumoPainel(int usuarioId) throws SQLException {
        Map<String, Object> resumo = new LinkedHashMap<>();
        String sql = "SELECT u.peso_inicial, (SELECT p.peso_atual FROM progressao p WHERE p.usuario_id=u.id ORDER BY p.data_registro DESC,p.id DESC LIMIT 1) peso_atual, "
                + "(SELECT o.objetivo FROM objetivos o WHERE o.usuario_id=u.id ORDER BY o.id DESC LIMIT 1) objetivo, "
                + "(SELECT o.peso_meta FROM objetivos o WHERE o.usuario_id=u.id ORDER BY o.id DESC LIMIT 1) peso_meta FROM usuario u WHERE u.id=?";
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId); try (ResultSet rs = comando.executeQuery()) {
                if (!rs.next()) return resumo;
                float inicial = rs.getFloat("peso_inicial"); Object atual = rs.getObject("peso_atual"); Object meta = rs.getObject("peso_meta");
                resumo.put("pesoInicial", inicial); resumo.put("pesoAtual", atual == null ? inicial : atual); resumo.put("pesoMeta", meta); resumo.put("objetivo", rs.getString("objetivo"));
            }
        }
        return resumo;
    }

    public boolean cadastrarProgresso(int usuarioId, float peso, Float gordura, LocalDate data, Float braco, Float antebraco,
            Float cintura, Float peito, Float quadril, Float coxa, Float panturrilha) throws SQLException {
        String medidasSql = "INSERT INTO medidasC (braco, antebraco, cintura, peito, quadril, coxa, panturrilha) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String progressoSql = "INSERT INTO progressao (usuario_id, peso_atual, percentual_gordura, medidasC_id, data_registro) VALUES (?, ?, ?, ?, ?)";
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                // Serializa gravações do mesmo usuário e impede dois registros na mesma data.
                try (PreparedStatement bloquearUsuario = conexao.prepareStatement(
                        "SELECT id FROM usuario WHERE id = ? FOR UPDATE")) {
                    bloquearUsuario.setInt(1, usuarioId);
                    try (ResultSet usuario = bloquearUsuario.executeQuery()) {
                        if (!usuario.next()) throw new SQLException("Usuario do progresso nao encontrado.");
                    }
                }
                try (PreparedStatement existente = conexao.prepareStatement(
                        "SELECT id FROM progressao WHERE usuario_id = ? AND data_registro = ? LIMIT 1")) {
                    existente.setInt(1, usuarioId);
                    existente.setDate(2, Date.valueOf(data));
                    try (ResultSet resultado = existente.executeQuery()) {
                        if (resultado.next()) {
                            conexao.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement medidas = conexao.prepareStatement(medidasSql, Statement.RETURN_GENERATED_KEYS)) {
                    definirNumero(medidas, 1, braco); definirNumero(medidas, 2, antebraco); definirNumero(medidas, 3, cintura);
                    definirNumero(medidas, 4, peito); definirNumero(medidas, 5, quadril); definirNumero(medidas, 6, coxa); definirNumero(medidas, 7, panturrilha);
                    medidas.executeUpdate();
                    try (ResultSet chaves = medidas.getGeneratedKeys()) {
                        if (!chaves.next()) throw new SQLException("Nao foi possivel criar as medidas.");
                        try (PreparedStatement progresso = conexao.prepareStatement(progressoSql)) {
                            progresso.setInt(1, usuarioId); progresso.setFloat(2, peso); definirNumero(progresso, 3, gordura);
                            progresso.setInt(4, chaves.getInt(1)); progresso.setDate(5, Date.valueOf(data)); progresso.executeUpdate();
                        }
                    }
                }
                conexao.commit();
                return true;
            } catch (SQLException erro) { conexao.rollback(); throw erro; }
        }
    }

    /** Remove o registro do usuário e apaga as medidas associadas se não forem compartilhadas. */
    public boolean excluirProgresso(int usuarioId, int progressoId) throws SQLException {
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                int medidasId;
                try (PreparedStatement buscar = conexao.prepareStatement(
                        "SELECT medidasC_id FROM progressao WHERE id = ? AND usuario_id = ? FOR UPDATE")) {
                    buscar.setInt(1, progressoId);
                    buscar.setInt(2, usuarioId);
                    try (ResultSet resultado = buscar.executeQuery()) {
                        if (!resultado.next()) {
                            conexao.rollback();
                            return false;
                        }
                        medidasId = resultado.getInt("medidasC_id");
                    }
                }
                try (PreparedStatement excluir = conexao.prepareStatement(
                        "DELETE FROM progressao WHERE id = ? AND usuario_id = ?")) {
                    excluir.setInt(1, progressoId);
                    excluir.setInt(2, usuarioId);
                    if (excluir.executeUpdate() != 1) {
                        conexao.rollback();
                        return false;
                    }
                }
                try (PreparedStatement limparMedidas = conexao.prepareStatement(
                        "DELETE FROM medidasC WHERE id = ? AND NOT EXISTS (SELECT 1 FROM progressao WHERE medidasC_id = ?)")) {
                    limparMedidas.setInt(1, medidasId);
                    limparMedidas.setInt(2, medidasId);
                    limparMedidas.executeUpdate();
                }
                conexao.commit();
                return true;
            } catch (SQLException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    private void garantirSessoesSemana(int usuarioId) throws SQLException {
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                try (PreparedStatement bloquear = conexao.prepareStatement("SELECT id FROM usuario WHERE id=? FOR UPDATE")) {
                    bloquear.setInt(1, usuarioId);
                    try (ResultSet usuario = bloquear.executeQuery()) { if (!usuario.next()) return; }
                }
                String perfilSql = "SELECT q.respostas,q.local_treino,q.frequencia_semanal,q.duracao_preferida,"
                        + "(SELECT o.objetivo FROM objetivos o WHERE o.usuario_id=q.usuario_id ORDER BY o.id DESC LIMIT 1) objetivo "
                        + "FROM questionario_usuario q WHERE q.usuario_id=?";
                String respostas, local, objetivo;
                int frequencia, duracao;
                try (PreparedStatement perfil = conexao.prepareStatement(perfilSql)) {
                    perfil.setInt(1, usuarioId);
                    try (ResultSet rs = perfil.executeQuery()) {
                        if (!rs.next() || rs.getString("objetivo") == null) { conexao.commit(); return; }
                        respostas = rs.getString("respostas"); local = rs.getString("local_treino");
                        frequencia = Math.max(1, Math.min(7, rs.getInt("frequencia_semanal")));
                        duracao = rs.getInt("duracao_preferida"); objetivo = rs.getString("objetivo");
                    }
                }
                int quantidadeAtiva = 0;
                Set<String> exerciciosAtivos = new HashSet<>();
                try (PreparedStatement ativos = conexao.prepareStatement(
                        "SELECT ut.treino_id,e.nome FROM usuario_treinos ut "
                                + "JOIN treinos t ON t.id=ut.treino_id "
                                + "LEFT JOIN treino_exercicios te ON te.treino_id=ut.treino_id "
                                + "LEFT JOIN exercicios e ON e.id=te.exercicio_id "
                                + "WHERE ut.usuario_id=? AND ut.ativo=TRUE AND t.origem='RECOMENDADO' ORDER BY ut.id")) {
                    ativos.setInt(1, usuarioId);
                    try (ResultSet rs = ativos.executeQuery()) {
                        Set<Integer> ids = new HashSet<>();
                        while (rs.next()) {
                            ids.add(rs.getInt("treino_id"));
                            String nome = rs.getString("nome");
                            if (nome != null) exerciciosAtivos.add(nome);
                        }
                        quantidadeAtiva = ids.size();
                    }
                }
                if (quantidadeAtiva >= frequencia) { conexao.commit(); return; }

                List<GeradorTreinoQuestionario.TreinoGerado> sessoes = GeradorTreinoQuestionario.gerar(
                        respostas, objetivo, local, duracao, frequencia, exerciciosAtivos);
                for (int indice = quantidadeAtiva; indice < frequencia; indice++) {
                    GeradorTreinoQuestionario.TreinoGerado treino = sessoes.get(indice);
                    int treinoId = inserirTreino(conexao, treino, objetivo, local, frequencia);
                    for (GeradorTreinoQuestionario.ExercicioGerado exercicio : treino.exercicios()) {
                        int exercicioId = obterOuCriarExercicio(conexao, exercicio.nome());
                        try (PreparedStatement associar = conexao.prepareStatement(
                                "INSERT INTO treino_exercicios (treino_id,exercicio_id,series,repeticoes,tempo_descanso) VALUES (?,?,?,?,?)")) {
                            associar.setInt(1, treinoId); associar.setInt(2, exercicioId); associar.setInt(3, exercicio.series());
                            associar.setInt(4, exercicio.repeticoes()); associar.setInt(5, exercicio.descanso()); associar.executeUpdate();
                        }
                    }
                    try (PreparedStatement vincular = conexao.prepareStatement(
                            "INSERT INTO usuario_treinos (usuario_id,treino_id,data_inicio,ativo) VALUES (?,?,CURDATE(),TRUE)")) {
                        vincular.setInt(1, usuarioId); vincular.setInt(2, treinoId); vincular.executeUpdate();
                    }
                }
                conexao.commit();
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    public boolean atualizarFrequenciaSemanal(int usuarioId, int frequencia) throws SQLException {
        if (frequencia < 2 || frequencia > 5) return false;
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                boolean atualizado = regenerarTreinosDaMetaAtual(conexao, usuarioId, frequencia);
                if (!atualizado) { conexao.rollback(); return false; }
                conexao.commit();
                return true;
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    /**
     * Atualiza o conjunto recomendado e a versão da agenda sem apagar treinos próprios,
     * conclusões ou cargas já registradas.
     */
    private boolean regenerarTreinosDaMetaAtual(Connection conexao, int usuarioId, Integer novaFrequencia) throws SQLException {
        return regenerarTreinosDaMetaAtual(conexao, usuarioId, novaFrequencia, false);
    }

    /** Ao trocar de objetivo, troca o conjunto ativo inteiro e começa a geração sem excluir exercícios antigos. */
    private boolean regenerarTreinosDaMetaAtual(Connection conexao, int usuarioId, Integer novaFrequencia,
            boolean substituirTodosOsTreinosAtivos) throws SQLException {
        String respostas;
        String local;
        int duracao;
        int frequencia;
        try (PreparedStatement perfil = conexao.prepareStatement(
                "SELECT respostas,local_treino,duracao_preferida,frequencia_semanal "
                        + "FROM questionario_usuario WHERE usuario_id=? FOR UPDATE")) {
            perfil.setInt(1, usuarioId);
            try (ResultSet rs = perfil.executeQuery()) {
                if (!rs.next()) return false;
                respostas = rs.getString("respostas");
                local = rs.getString("local_treino");
                duracao = rs.getInt("duracao_preferida");
                frequencia = novaFrequencia == null ? rs.getInt("frequencia_semanal") : novaFrequencia;
            }
        }
        if (frequencia < 2 || frequencia > 5) return false;

        String objetivo = null;
        try (PreparedStatement meta = conexao.prepareStatement(
                "SELECT objetivo FROM objetivos WHERE usuario_id=? ORDER BY id DESC LIMIT 1")) {
            meta.setInt(1, usuarioId);
            try (ResultSet rs = meta.executeQuery()) { if (rs.next()) objetivo = rs.getString(1); }
        }

        if (novaFrequencia != null && (objetivo == null || objetivo.isBlank())) return false;
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        List<Integer> diasPreferidos = new ArrayList<>();
        try (PreparedStatement agenda = conexao.prepareStatement(
                "SELECT d.dia_semana FROM agenda_treino_semana a "
                        + "JOIN agenda_treino_dia d ON d.agenda_id=a.id "
                        + "WHERE a.id=(SELECT id FROM agenda_treino_semana WHERE usuario_id=? AND vigente_desde<=? "
                        + "ORDER BY vigente_desde DESC,id DESC LIMIT 1) ORDER BY d.dia_semana")) {
            agenda.setInt(1, usuarioId);
            agenda.setDate(2, Date.valueOf(hoje));
            try (ResultSet rs = agenda.executeQuery()) {
                Set<Integer> vistos = new HashSet<>();
                while (rs.next()) {
                    int dia = rs.getInt(1);
                    if (vistos.add(dia)) diasPreferidos.add(dia);
                }
            }
        }

        if (novaFrequencia != null) {
            try (PreparedStatement atualizar = conexao.prepareStatement(
                    "UPDATE questionario_usuario SET frequencia_semanal=? WHERE usuario_id=?")) {
                atualizar.setInt(1, frequencia);
                atualizar.setInt(2, usuarioId);
                if (atualizar.executeUpdate() != 1) return false;
            }
        }

        Set<String> exerciciosAnteriores = new HashSet<>();
        if (!substituirTodosOsTreinosAtivos) {
            try (PreparedStatement buscarExercicios = conexao.prepareStatement(
                    "SELECT DISTINCT e.nome FROM usuario_treinos ut JOIN treinos t ON t.id=ut.treino_id "
                            + "JOIN treino_exercicios te ON te.treino_id=t.id JOIN exercicios e ON e.id=te.exercicio_id "
                            + "WHERE ut.usuario_id=? AND ut.ativo=TRUE AND t.origem='RECOMENDADO'")) {
                buscarExercicios.setInt(1, usuarioId);
                try (ResultSet rs = buscarExercicios.executeQuery()) {
                    while (rs.next()) exerciciosAnteriores.add(rs.getString(1));
                }
            }
        }
        String desativarSql = substituirTodosOsTreinosAtivos
                ? "UPDATE usuario_treinos SET ativo=FALSE WHERE usuario_id=? AND ativo=TRUE"
                : "UPDATE usuario_treinos ut JOIN treinos t ON t.id=ut.treino_id "
                        + "SET ut.ativo=FALSE WHERE ut.usuario_id=? AND ut.ativo=TRUE AND t.origem='RECOMENDADO'";
        try (PreparedStatement desativar = conexao.prepareStatement(desativarSql)) {
            desativar.setInt(1, usuarioId);
            desativar.executeUpdate();
        }

        List<Integer> novosTreinos = new ArrayList<>();
        if (objetivo != null && !objetivo.isBlank()) {
            List<GeradorTreinoQuestionario.TreinoGerado> sessoes =
                    GeradorTreinoQuestionario.gerar(respostas, objetivo, local, duracao, frequencia, exerciciosAnteriores);
            if (sessoes.size() < frequencia) throw new SQLException("Não foi possível gerar todas as sessões recomendadas.");
            for (int indice = 0; indice < frequencia; indice++) {
                GeradorTreinoQuestionario.TreinoGerado treino = sessoes.get(indice);
                int treinoId = inserirTreino(conexao, treino, objetivo, local, frequencia, "RECOMENDADO");
                for (GeradorTreinoQuestionario.ExercicioGerado exercicio : treino.exercicios()) {
                    int exercicioId = obterOuCriarExercicio(conexao, exercicio.nome());
                    try (PreparedStatement associar = conexao.prepareStatement(
                            "INSERT INTO treino_exercicios (treino_id,exercicio_id,series,repeticoes,tempo_descanso) VALUES (?,?,?,?,?)")) {
                        associar.setInt(1, treinoId);
                        associar.setInt(2, exercicioId);
                        associar.setInt(3, exercicio.series());
                        associar.setInt(4, exercicio.repeticoes());
                        associar.setInt(5, exercicio.descanso());
                        associar.executeUpdate();
                    }
                }
                try (PreparedStatement vincular = conexao.prepareStatement(
                        "INSERT INTO usuario_treinos (usuario_id,treino_id,data_inicio,ativo) VALUES (?,?,?,TRUE)")) {
                    vincular.setInt(1, usuarioId);
                    vincular.setInt(2, treinoId);
                    vincular.setDate(3, Date.valueOf(hoje));
                    vincular.executeUpdate();
                }
                novosTreinos.add(treinoId);
            }
        }

        List<Integer> dias = new ArrayList<>();
        Set<Integer> diasUsados = new HashSet<>();
        for (int dia : diasPreferidos) {
            if (dias.size() >= novosTreinos.size()) break;
            if (diasUsados.add(dia)) dias.add(dia);
        }
        for (int dia = 1; dia <= 7 && dias.size() < novosTreinos.size(); dia++) {
            if (diasUsados.add(dia)) dias.add(dia);
        }
        salvarVersaoAgenda(conexao, usuarioId, frequencia, hoje, dias, novosTreinos);
        return true;
    }

    private void salvarVersaoAgenda(Connection conexao, int usuarioId, int frequencia, LocalDate vigenteDesde,
            List<Integer> dias, List<Integer> treinos) throws SQLException {
        try (PreparedStatement remover = conexao.prepareStatement(
                "DELETE FROM agenda_treino_semana WHERE usuario_id=? AND vigente_desde=?")) {
            remover.setInt(1, usuarioId);
            remover.setDate(2, Date.valueOf(vigenteDesde));
            remover.executeUpdate();
        }
        long agendaId;
        try (PreparedStatement criar = conexao.prepareStatement(
                "INSERT INTO agenda_treino_semana (usuario_id,vigente_desde,frequencia_semanal) VALUES (?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            criar.setInt(1, usuarioId);
            criar.setDate(2, Date.valueOf(vigenteDesde));
            criar.setInt(3, frequencia);
            criar.executeUpdate();
            try (ResultSet chaves = criar.getGeneratedKeys()) {
                if (!chaves.next()) throw new SQLException("Não foi possível criar a nova programação semanal.");
                agendaId = chaves.getLong(1);
            }
        }
        for (int indice = 0; indice < treinos.size(); indice++) {
            try (PreparedStatement inserir = conexao.prepareStatement(
                    "INSERT INTO agenda_treino_dia (agenda_id,dia_semana,ordem_treino,treino_id) VALUES (?,?,?,?)")) {
                inserir.setLong(1, agendaId);
                inserir.setInt(2, dias.get(indice));
                inserir.setInt(3, indice + 1);
                inserir.setInt(4, treinos.get(indice));
                inserir.executeUpdate();
            }
        }
    }
    public Map<String, Object> obterAgendaSemanal(int usuarioId) throws SQLException {
        garantirSessoesSemana(usuarioId);
        Map<String, Object> agenda = new LinkedHashMap<>();
        int frequencia = 0;
        try (Connection conexao = consultas.abreConexao(); PreparedStatement perfil = conexao.prepareStatement(
                "SELECT frequencia_semanal FROM questionario_usuario WHERE usuario_id=?")) {
            perfil.setInt(1, usuarioId);
            try (ResultSet rs = perfil.executeQuery()) { if (rs.next()) frequencia = rs.getInt(1); }
        }
        agenda.put("frequencia", frequencia);
        agenda.put("treinos", listarTreinos(usuarioId, ""));
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        List<Map<String, Object>> dias = new ArrayList<>();
        try (Connection conexao = consultas.abreConexao(); PreparedStatement buscar = conexao.prepareStatement(
                "SELECT id,vigente_desde FROM agenda_treino_semana WHERE usuario_id=? AND vigente_desde<=? ORDER BY vigente_desde DESC,id DESC LIMIT 1")) {
            buscar.setInt(1, usuarioId); buscar.setDate(2, Date.valueOf(hoje));
            try (ResultSet rs = buscar.executeQuery()) {
                if (rs.next()) {
                    long agendaId = rs.getLong("id");
                    agenda.put("configurada", true);
                    agenda.put("vigenteDesde", rs.getDate("vigente_desde").toLocalDate().toString());
                    try (PreparedStatement itens = conexao.prepareStatement(
                            "SELECT d.dia_semana,d.ordem_treino,d.treino_id,t.nome_treino FROM agenda_treino_dia d "
                                    + "JOIN treinos t ON t.id=d.treino_id WHERE d.agenda_id=? ORDER BY d.dia_semana")) {
                        itens.setLong(1, agendaId);
                        try (ResultSet item = itens.executeQuery()) {
                            while (item.next()) {
                                Map<String, Object> dia = new LinkedHashMap<>();
                                dia.put("diaSemana", item.getInt("dia_semana"));
                                dia.put("ordem", item.getInt("ordem_treino"));
                                dia.put("treinoId", item.getInt("treino_id"));
                                dia.put("treino", item.getString("nome_treino"));
                                dias.add(dia);
                            }
                        }
                    }
                } else agenda.put("configurada", false);
            }
        }
        agenda.put("dias", dias);
        return agenda;
    }

    public boolean salvarAgendaSemanal(int usuarioId, List<Integer> dias, List<Integer> treinos, LocalDate vigenteDesde) throws SQLException {
        if (dias == null || treinos == null || dias.size() != treinos.size() || dias.isEmpty() || dias.size() > 7
                || new HashSet<>(dias).size() != dias.size() || new HashSet<>(treinos).size() != treinos.size()
                || dias.stream().anyMatch(dia -> dia == null || dia < 1 || dia > 7)
                || treinos.stream().anyMatch(treino -> treino == null || treino <= 0)) return false;
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try {
                int frequencia;
                try (PreparedStatement perfil = conexao.prepareStatement(
                        "SELECT frequencia_semanal FROM questionario_usuario WHERE usuario_id=? FOR UPDATE")) {
                    perfil.setInt(1, usuarioId);
                    try (ResultSet rs = perfil.executeQuery()) {
                        if (!rs.next()) { conexao.rollback(); return false; }
                        frequencia = rs.getInt(1);
                    }
                }
                if (dias.size() != frequencia) { conexao.rollback(); return false; }
                for (int treinoId : treinos) {
                    try (PreparedStatement validar = conexao.prepareStatement(
                            "SELECT 1 FROM usuario_treinos WHERE usuario_id=? AND treino_id=? AND ativo=TRUE LIMIT 1")) {
                        validar.setInt(1, usuarioId); validar.setInt(2, treinoId);
                        try (ResultSet rs = validar.executeQuery()) { if (!rs.next()) { conexao.rollback(); return false; } }
                    }
                }
                try (PreparedStatement remover = conexao.prepareStatement(
                        "DELETE FROM agenda_treino_semana WHERE usuario_id=? AND vigente_desde=?")) {
                    remover.setInt(1, usuarioId); remover.setDate(2, Date.valueOf(vigenteDesde)); remover.executeUpdate();
                }
                long agendaId;
                try (PreparedStatement criar = conexao.prepareStatement(
                        "INSERT INTO agenda_treino_semana (usuario_id,vigente_desde,frequencia_semanal) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    criar.setInt(1, usuarioId); criar.setDate(2, Date.valueOf(vigenteDesde)); criar.setInt(3, frequencia); criar.executeUpdate();
                    try (ResultSet chaves = criar.getGeneratedKeys()) {
                        if (!chaves.next()) throw new SQLException("Não foi possível criar a programação semanal.");
                        agendaId = chaves.getLong(1);
                    }
                }
                for (int indice = 0; indice < dias.size(); indice++) {
                    try (PreparedStatement inserir = conexao.prepareStatement(
                            "INSERT INTO agenda_treino_dia (agenda_id,dia_semana,ordem_treino,treino_id) VALUES (?,?,?,?)")) {
                        inserir.setLong(1, agendaId); inserir.setInt(2, dias.get(indice));
                        inserir.setInt(3, indice + 1); inserir.setInt(4, treinos.get(indice)); inserir.executeUpdate();
                    }
                }
                conexao.commit();
                return true;
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    public Map<String, Object> calendarioTreinos(int usuarioId, YearMonth mes) throws SQLException {
        LocalDate inicio = mes.atDay(1);
        LocalDate fim = mes.atEndOfMonth();
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        TreeMap<LocalDate, Map<Integer, Map<String, Object>>> versoes = new TreeMap<>();
        Map<LocalDate, List<Map<String, Object>>> concluidos = new HashMap<>();
        Map<LocalDate, List<Map<String, Object>>> cargas = new HashMap<>();
        try (Connection conexao = consultas.abreConexao()) {
            String agendaSql = "SELECT a.vigente_desde,d.dia_semana,d.ordem_treino,d.treino_id,t.nome_treino "
                    + "FROM agenda_treino_semana a JOIN agenda_treino_dia d ON d.agenda_id=a.id "
                    + "JOIN treinos t ON t.id=d.treino_id WHERE a.usuario_id=? AND a.vigente_desde<=? "
                    + "ORDER BY a.vigente_desde,d.dia_semana";
            try (PreparedStatement comando = conexao.prepareStatement(agendaSql)) {
                comando.setInt(1, usuarioId); comando.setDate(2, Date.valueOf(fim));
                try (ResultSet rs = comando.executeQuery()) {
                    while (rs.next()) {
                        LocalDate desde = rs.getDate("vigente_desde").toLocalDate();
                        Map<Integer, Map<String, Object>> porDia = versoes.computeIfAbsent(desde, ignorado -> new HashMap<>());
                        Map<String, Object> sessao = new LinkedHashMap<>();
                        sessao.put("treinoId", rs.getInt("treino_id")); sessao.put("treino", rs.getString("nome_treino"));
                        sessao.put("ordem", rs.getInt("ordem_treino"));
                        porDia.put(rs.getInt("dia_semana"), sessao);
                    }
                }
            }
            String concluidosSql = "SELECT tc.treino_id,t.nome_treino,tc.data_conclusao FROM treinos_concluidos tc "
                    + "JOIN treinos t ON t.id=tc.treino_id WHERE tc.usuario_id=? AND tc.data_conclusao BETWEEN ? AND ? "
                    + "ORDER BY tc.data_conclusao,tc.id";
            try (PreparedStatement comando = conexao.prepareStatement(concluidosSql)) {
                comando.setInt(1, usuarioId); comando.setDate(2, Date.valueOf(inicio)); comando.setDate(3, Date.valueOf(fim));
                try (ResultSet rs = comando.executeQuery()) {
                    while (rs.next()) {
                        LocalDate data = rs.getDate("data_conclusao").toLocalDate();
                        Map<String, Object> treino = new LinkedHashMap<>();
                        treino.put("treinoId", rs.getInt("treino_id")); treino.put("treino", rs.getString("nome_treino"));
                        concluidos.computeIfAbsent(data, ignorado -> new ArrayList<>()).add(treino);
                    }
                }
            }
            String cargasSql = "SELECT c.id,c.treino_id,t.nome_treino,c.exercicio_id,e.nome,c.data_registro,c.carga_kg,c.series,c.repeticoes "
                    + "FROM cargas_exercicio c JOIN treinos t ON t.id=c.treino_id JOIN exercicios e ON e.id=c.exercicio_id "
                    + "WHERE c.usuario_id=? AND c.data_registro BETWEEN ? AND ? ORDER BY c.data_registro,c.id";
            try (PreparedStatement comando = conexao.prepareStatement(cargasSql)) {
                comando.setInt(1, usuarioId); comando.setDate(2, Date.valueOf(inicio)); comando.setDate(3, Date.valueOf(fim));
                try (ResultSet rs = comando.executeQuery()) {
                    while (rs.next()) {
                        LocalDate data = rs.getDate("data_registro").toLocalDate();
                        Map<String, Object> carga = new LinkedHashMap<>();
                        carga.put("id", rs.getLong("id")); carga.put("treinoId", rs.getInt("treino_id"));
                        carga.put("treino", rs.getString("nome_treino")); carga.put("exercicioId", rs.getInt("exercicio_id"));
                        carga.put("exercicio", rs.getString("nome")); carga.put("cargaKg", rs.getBigDecimal("carga_kg"));
                        carga.put("series", rs.getObject("series")); carga.put("repeticoes", rs.getObject("repeticoes"));
                        cargas.computeIfAbsent(data, ignorado -> new ArrayList<>()).add(carga);
                    }
                }
            }
        }
        List<Map<String, Object>> dias = new ArrayList<>();
        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
            Map.Entry<LocalDate, Map<Integer, Map<String, Object>>> versao = versoes.floorEntry(data);
            Map<String, Object> previsto = versao == null ? null : versao.getValue().get(data.getDayOfWeek().getValue());
            List<Map<String, Object>> concluidosNoDia = concluidos.getOrDefault(data, List.of());
            String estado;
            if (!concluidosNoDia.isEmpty()) estado = "concluido";
            else if (previsto != null && data.isBefore(hoje)) estado = "pendente";
            else if (previsto != null && data.isEqual(hoje)) estado = "hoje";
            else if (previsto != null) estado = "futuro";
            else if (versao != null) estado = "descanso";
            else estado = "sem-plano";

            Map<String, Object> dia = new LinkedHashMap<>();
            dia.put("data", data.toString()); dia.put("estado", estado);
            dia.put("treinoPrevisto", previsto);
            dia.put("treinosConcluidos", concluidosNoDia);
            dia.put("cargas", cargas.getOrDefault(data, List.of()));
            dias.add(dia);
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("ano", mes.getYear()); resultado.put("mes", mes.getMonthValue()); resultado.put("dias", dias);
        return resultado;
    }

    public List<Map<String, Object>> opcoesCarga(int usuarioId, LocalDate data) throws SQLException {
        Map<Integer, Map<String, Object>> treinos = new LinkedHashMap<>();
        try (Connection conexao = consultas.abreConexao()) {
            String agendado = "SELECT DISTINCT t.id,t.nome_treino FROM agenda_treino_semana a "
                    + "JOIN agenda_treino_dia d ON d.agenda_id=a.id JOIN treinos t ON t.id=d.treino_id "
                    + "WHERE a.id=(SELECT id FROM agenda_treino_semana WHERE usuario_id=? AND vigente_desde<=? "
                    + "ORDER BY vigente_desde DESC,id DESC LIMIT 1) AND d.dia_semana=?";
            try (PreparedStatement comando = conexao.prepareStatement(agendado)) {
                comando.setInt(1, usuarioId); comando.setDate(2, Date.valueOf(data)); comando.setInt(3, data.getDayOfWeek().getValue());
                try (ResultSet rs = comando.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> treino = new LinkedHashMap<>();
                        treino.put("treinoId", rs.getInt("id")); treino.put("treino", rs.getString("nome_treino"));
                        treinos.put(rs.getInt("id"), treino);
                    }
                }
            }
            String realizado = "SELECT DISTINCT t.id,t.nome_treino FROM treinos_concluidos tc JOIN treinos t ON t.id=tc.treino_id "
                    + "WHERE tc.usuario_id=? AND tc.data_conclusao=?";
            try (PreparedStatement comando = conexao.prepareStatement(realizado)) {
                comando.setInt(1, usuarioId); comando.setDate(2, Date.valueOf(data));
                try (ResultSet rs = comando.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> treino = new LinkedHashMap<>();
                        treino.put("treinoId", rs.getInt("id")); treino.put("treino", rs.getString("nome_treino"));
                        treinos.putIfAbsent(rs.getInt("id"), treino);
                    }
                }
            }
            for (Map<String, Object> treino : treinos.values()) {
                List<Map<String, Object>> exercicios = new ArrayList<>();
                try (PreparedStatement comando = conexao.prepareStatement(
                        "SELECT e.id,e.nome FROM treino_exercicios te JOIN exercicios e ON e.id=te.exercicio_id WHERE te.treino_id=? ORDER BY te.id")) {
                    comando.setInt(1, (Integer) treino.get("treinoId"));
                    try (ResultSet rs = comando.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> exercicio = new LinkedHashMap<>();
                            exercicio.put("exercicioId", rs.getInt("id")); exercicio.put("exercicio", rs.getString("nome"));
                            exercicios.add(exercicio);
                        }
                    }
                }
                treino.put("exercicios", exercicios);
            }
        }
        return new ArrayList<>(treinos.values());
    }

    /** Retorna a evolução dos exercícios que têm carga no dia selecionado, limitada até esse dia. */
    public List<Map<String, Object>> historicoCargasDia(int usuarioId, LocalDate dia) throws SQLException {
        String sql = "SELECT c.exercicio_id,e.nome,c.treino_id,t.nome_treino,c.data_registro,c.carga_kg,c.series,c.repeticoes "
                + "FROM cargas_exercicio c JOIN treinos t ON t.id=c.treino_id JOIN exercicios e ON e.id=c.exercicio_id "
                + "WHERE c.usuario_id=? AND c.data_registro<=? AND c.exercicio_id IN ("
                + "SELECT DISTINCT do_dia.exercicio_id FROM cargas_exercicio do_dia WHERE do_dia.usuario_id=? AND do_dia.data_registro=?) "
                + "ORDER BY c.exercicio_id,c.data_registro,c.id";
        Map<Integer, Map<String, Object>> porExercicio = new LinkedHashMap<>();
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId); comando.setDate(2, Date.valueOf(dia));
            comando.setInt(3, usuarioId); comando.setDate(4, Date.valueOf(dia));
            try (ResultSet rs = comando.executeQuery()) {
                while (rs.next()) {
                    int exercicioId = rs.getInt("exercicio_id");
                    Map<String, Object> exercicio = porExercicio.get(exercicioId);
                    if (exercicio == null) {
                        Map<String, Object> novo = new LinkedHashMap<>();
                        novo.put("exercicioId", exercicioId); novo.put("exercicio", rs.getString("nome"));
                        novo.put("pontos", new ArrayList<Map<String, Object>>());
                        porExercicio.put(exercicioId, novo);
                        exercicio = novo;
                    }
                    Map<String, Object> ponto = new LinkedHashMap<>();
                    ponto.put("data", rs.getDate("data_registro").toLocalDate().toString());
                    ponto.put("cargaKg", rs.getBigDecimal("carga_kg"));
                    ponto.put("series", rs.getObject("series")); ponto.put("repeticoes", rs.getObject("repeticoes"));
                    ((List<Map<String, Object>>) exercicio.get("pontos")).add(ponto);
                }
            }
        }
        return new ArrayList<>(porExercicio.values());
    }

    public List<Map<String, Object>> listarCargasMes(int usuarioId, YearMonth mes) throws SQLException {
        String sql = "SELECT c.id,c.treino_id,t.nome_treino,c.exercicio_id,e.nome,c.data_registro,c.carga_kg,c.series,c.repeticoes "
                + "FROM cargas_exercicio c JOIN treinos t ON t.id=c.treino_id JOIN exercicios e ON e.id=c.exercicio_id "
                + "WHERE c.usuario_id=? AND c.data_registro BETWEEN ? AND ? ORDER BY c.data_registro DESC,c.id DESC";
        List<Map<String, Object>> cargas = new ArrayList<>();
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId); comando.setDate(2, Date.valueOf(mes.atDay(1))); comando.setDate(3, Date.valueOf(mes.atEndOfMonth()));
            try (ResultSet rs = comando.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> carga = new LinkedHashMap<>();
                    carga.put("id", rs.getLong("id")); carga.put("treinoId", rs.getInt("treino_id"));
                    carga.put("treino", rs.getString("nome_treino")); carga.put("exercicioId", rs.getInt("exercicio_id"));
                    carga.put("exercicio", rs.getString("nome")); carga.put("data", rs.getDate("data_registro").toLocalDate().toString());
                    carga.put("cargaKg", rs.getBigDecimal("carga_kg")); carga.put("series", rs.getObject("series"));
                    carga.put("repeticoes", rs.getObject("repeticoes")); cargas.add(carga);
                }
            }
        }
        return cargas;
    }

    public boolean salvarCarga(int usuarioId, int treinoId, int exercicioId, LocalDate data,
            float cargaKg, Integer series, Integer repeticoes) throws SQLException {
        if (!opcoesCarga(usuarioId, data).stream().anyMatch(treino -> ((Integer) treino.get("treinoId")) == treinoId
                && ((List<Map<String, Object>>) treino.get("exercicios")).stream()
                        .anyMatch(exercicio -> ((Integer) exercicio.get("exercicioId")) == exercicioId))) return false;
        String sql = "INSERT INTO cargas_exercicio (usuario_id,treino_id,exercicio_id,data_registro,carga_kg,series,repeticoes) "
                + "VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE carga_kg=VALUES(carga_kg),series=VALUES(series),repeticoes=VALUES(repeticoes)";
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId); comando.setInt(2, treinoId); comando.setInt(3, exercicioId);
            comando.setDate(4, Date.valueOf(data)); comando.setFloat(5, cargaKg);
            if (series == null) comando.setNull(6, java.sql.Types.TINYINT); else comando.setInt(6, series);
            if (repeticoes == null) comando.setNull(7, java.sql.Types.SMALLINT); else comando.setInt(7, repeticoes);
            comando.executeUpdate();
            return true;
        }
    }

    public boolean excluirCarga(int usuarioId, long cargaId) throws SQLException {
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(
                "DELETE FROM cargas_exercicio WHERE id=? AND usuario_id=?")) {
            comando.setLong(1, cargaId); comando.setInt(2, usuarioId);
            return comando.executeUpdate() == 1;
        }
    }

    private void definirNumero(PreparedStatement comando, int indice, Float valor) throws SQLException {
        if (valor == null) comando.setNull(indice, java.sql.Types.DECIMAL); else comando.setFloat(indice, valor);
    }
    private record MetaObjetivo(int id, String objetivo, Float pesoMeta) { }
}
