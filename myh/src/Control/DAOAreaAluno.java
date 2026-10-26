package Control;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas e registros usados na area autenticada do aluno. */
public class DAOAreaAluno {
    private final frmConsultas consultas = new frmConsultas();

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

    public void cadastrarObjetivo(int usuarioId, String objetivo, Float pesoMeta, LocalDate dataMeta) throws SQLException {
        String sql = "INSERT INTO objetivos (usuario_id, objetivo, peso_meta, data_meta) VALUES (?, ?, ?, ?)";
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId);
            comando.setString(2, objetivo);
            if (pesoMeta == null) comando.setNull(3, java.sql.Types.DECIMAL); else comando.setFloat(3, pesoMeta);
            if (dataMeta == null) comando.setNull(4, java.sql.Types.DATE); else comando.setDate(4, Date.valueOf(dataMeta));
            comando.executeUpdate();
        }
    }

    public boolean atualizarObjetivo(int usuarioId, int objetivoId, String objetivo, Float pesoMeta, LocalDate dataMeta) throws SQLException {
        String sql = "UPDATE objetivos SET objetivo = ?, peso_meta = ?, data_meta = ? WHERE id = ? AND usuario_id = ?";
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, objetivo);
            if (pesoMeta == null) comando.setNull(2, java.sql.Types.DECIMAL); else comando.setFloat(2, pesoMeta);
            if (dataMeta == null) comando.setNull(3, java.sql.Types.DATE); else comando.setDate(3, Date.valueOf(dataMeta));
            comando.setInt(4, objetivoId);
            comando.setInt(5, usuarioId);
            return comando.executeUpdate() == 1;
        }
    }

    public boolean excluirObjetivo(int usuarioId, int objetivoId) throws SQLException {
        String sql = "DELETE FROM objetivos WHERE id = ? AND usuario_id = ?";
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, objetivoId);
            comando.setInt(2, usuarioId);
            return comando.executeUpdate() == 1;
        }
    }

    public List<Map<String, Object>> listarProgresso(int usuarioId) throws SQLException {
        String sql = "SELECT p.peso_atual, p.percentual_gordura, p.data_registro, m.braco, m.antebraco, m.cintura, m.peito, m.quadril, m.coxa, m.panturrilha "
                + "FROM progressao p JOIN medidasC m ON m.id = p.medidasC_id WHERE p.usuario_id = ? ORDER BY p.data_registro DESC, p.id DESC";
        List<Map<String, Object>> progresso = new ArrayList<>();
        try (Connection conexao = consultas.abreConexao(); PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    Map<String, Object> registro = new LinkedHashMap<>();
                    registro.put("peso", resultado.getFloat("peso_atual"));
                    registro.put("gordura", resultado.getObject("percentual_gordura"));
                    registro.put("data", resultado.getDate("data_registro").toLocalDate().toString());
                    registro.put("cintura", resultado.getObject("cintura"));
                    registro.put("peito", resultado.getObject("peito"));
                    registro.put("braco", resultado.getObject("braco"));
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
                GeradorTreinoQuestionario.gerar(respostasJson, objetivo, local, duracao);
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
        String sql = "INSERT INTO treinos (nome_treino, descricao, nivel, duracao_treino, objetivo, local_treino, frequencia_semanal) "
                + "VALUES (?, ?, 'Personalizado', ?, ?, ?, ?)";
        try (PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            comando.setString(1, treino.nome());
            comando.setString(2, treino.descricao());
            comando.setInt(3, treino.duracao());
            comando.setString(4, objetivo);
            comando.setString(5, local);
            comando.setInt(6, frequencia);
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

    public void cadastrarProgresso(int usuarioId, float peso, Float gordura, LocalDate data, Float braco, Float antebraco,
            Float cintura, Float peito, Float quadril, Float coxa, Float panturrilha) throws SQLException {
        String medidasSql = "INSERT INTO medidasC (braco, antebraco, cintura, peito, quadril, coxa, panturrilha) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String progressoSql = "INSERT INTO progressao (usuario_id, peso_atual, percentual_gordura, medidasC_id, data_registro) VALUES (?, ?, ?, ?, ?)";
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
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
                conexao.commit();
            } catch (SQLException erro) { conexao.rollback(); throw erro; }
        }
    }

    private void definirNumero(PreparedStatement comando, int indice, Float valor) throws SQLException {
        if (valor == null) comando.setNull(indice, java.sql.Types.DECIMAL); else comando.setFloat(indice, valor);
    }
}
