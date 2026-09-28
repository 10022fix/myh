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

    /** Salva a conclusao do questionario, cria a meta e associa um treino pronto uma unica vez. */
    
    public boolean finalizarQuestionario(int usuarioId, String objetivo, float pesoMeta, int duracao,
            String formaAtual, String formaDesejada, String frequencia, String local, String respostasJson,
            LocalDate nascimento, String sexo, float altura, float pesoInicial) throws SQLException {
        String perfilSql = "INSERT INTO questionario_usuario (usuario_id, forma_atual, forma_desejada, frequencia_semanal, local_treino, duracao_preferida, respostas) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE forma_atual=VALUES(forma_atual), forma_desejada=VALUES(forma_desejada), frequencia_semanal=VALUES(frequencia_semanal), local_treino=VALUES(local_treino), duracao_preferida=VALUES(duracao_preferida), respostas=VALUES(respostas)";
        String objetivoSql = "INSERT INTO objetivos (usuario_id, objetivo, peso_meta) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM objetivos WHERE usuario_id = ?)";
        try (Connection conexao = consultas.abreConexao()) {
            conexao.setAutoCommit(false);
            try (PreparedStatement perfil = conexao.prepareStatement(perfilSql); PreparedStatement meta = conexao.prepareStatement(objetivoSql);
                 PreparedStatement dados = conexao.prepareStatement("UPDATE usuario SET data_nascimento=?, sexo=?, altura=?, peso_inicial=? WHERE id=?")) {
                dados.setDate(1, Date.valueOf(nascimento)); dados.setString(2, sexo); dados.setFloat(3, altura); dados.setFloat(4, pesoInicial); dados.setInt(5, usuarioId);
                if (dados.executeUpdate() != 1) throw new SQLException("Usuario nao encontrado.");
                perfil.setInt(1, usuarioId); perfil.setString(2, formaAtual); perfil.setString(3, formaDesejada);
                perfil.setInt(4, Integer.parseInt(frequencia)); perfil.setString(5, local); perfil.setInt(6, duracao); perfil.setString(7, respostasJson); perfil.executeUpdate();
                meta.setInt(1, usuarioId); meta.setString(2, objetivo); meta.setFloat(3, pesoMeta); meta.setInt(4, usuarioId); meta.executeUpdate();
                boolean treinoAtribuido = atribuirTreinoPronto(
                        conexao,
                        usuarioId,
                        objetivo,
                        local,
                        Integer.parseInt(frequencia),
                        duracao
                );
                conexao.commit();
                return treinoAtribuido;
            }
        }
    }
    private boolean atribuirTreinoPronto(
            Connection conexao,
            int usuarioId,
            String objetivo,
            String local,
            int frequencia,
            int duracao
    ) throws SQLException {

        try (PreparedStatement existe = conexao.prepareStatement(
                "SELECT 1 FROM usuario_treinos "
                + "WHERE usuario_id = ? AND ativo = TRUE LIMIT 1")) {

            existe.setInt(1, usuarioId);

            try (ResultSet rs = existe.executeQuery()) {
                if (rs.next()) return true;
            }
        }

        String treinoSql =
                "SELECT id FROM treinos "
                + "WHERE nivel = 'Iniciante' "
                + "AND objetivo = ? "
                + "ORDER BY "
                + "CASE WHEN local_treino = ? THEN 0 ELSE 1 END, "
                + "ABS(frequencia_semanal - ?), "
                + "ABS(duracao_treino - ?), "
                + "id "
                + "LIMIT 1";

        try (PreparedStatement buscar = conexao.prepareStatement(treinoSql)) {
            buscar.setString(1, objetivo);
            buscar.setString(2, local);
            buscar.setInt(3, frequencia);
            buscar.setInt(4, duracao);

            try (ResultSet rs = buscar.executeQuery()) {
                if (!rs.next()) return false;

                try (PreparedStatement inserir = conexao.prepareStatement(
                        "INSERT INTO usuario_treinos "
                        + "(usuario_id, treino_id, data_inicio, ativo) "
                        + "VALUES (?, ?, CURDATE(), TRUE)")) {

                    inserir.setInt(1, usuarioId);
                    inserir.setInt(2, rs.getInt("id"));
                    inserir.executeUpdate();
                    return true;
                }
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
