package Control;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import javax.swing.table.DefaultTableModel;

/** Centraliza conexao e consultas da tabela usuario. */
public class frmConsultas {
private static final String URL = System.getenv()
        .getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/TCC");
private static final String USUARIO = System.getenv()
        .getOrDefault("DB_USER", "root");
private static final String SENHA = System.getenv()
        .getOrDefault("DB_PASSWORD", "");

    public Connection abreConexao() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver do MySQL nao encontrado.", e);
        }
        return DriverManager.getConnection(URL, USUARIO, SENHA);
    }

    public void fechaConexao(Connection conexao) {
        if (conexao == null) return;
        try { conexao.close(); }
        catch (SQLException e) { System.err.println("Nao foi possivel fechar a conexao: " + e.getMessage()); }
    }

    public DefaultTableModel ConsultaTodosDados() {
        DefaultTableModel tabela = new DefaultTableModel();
        String sql = "SELECT * FROM usuario";
        try (Connection conexao = abreConexao();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {
            ResultSetMetaData metadados = resultado.getMetaData();
            int quantidadeColunas = metadados.getColumnCount();
            for (int coluna = 1; coluna <= quantidadeColunas; coluna++) tabela.addColumn(metadados.getColumnLabel(coluna));
            while (resultado.next()) {
                Object[] linha = new Object[quantidadeColunas];
                for (int coluna = 1; coluna <= quantidadeColunas; coluna++) linha[coluna - 1] = resultado.getObject(coluna);
                tabela.addRow(linha);
            }
            return tabela;
        } catch (SQLException e) {
            System.err.println("Erro ao consultar usuarios: " + e.getMessage());
            return null;
        }
    }

    public DefaultTableModel consultarTreinosUsuario(int usuarioId) {
        DefaultTableModel tabela = new DefaultTableModel();
        String sql = "SELECT t.nome_treino AS treino, t.descricao, t.nivel, t.duracao_treino, "
                + "e.nome AS exercicio, te.series, te.repeticoes, te.tempo_descanso "
                + "FROM usuario_treinos ut "
                + "JOIN treinos t ON t.id = ut.treino_id "
                + "LEFT JOIN treino_exercicios te ON te.treino_id = t.id "
                + "LEFT JOIN exercicios e ON e.id = te.exercicio_id "
                + "WHERE ut.usuario_id = ? AND ut.ativo = TRUE "
                + "ORDER BY t.nome_treino, e.nome";
        try (Connection conexao = abreConexao();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuarioId);
            try (ResultSet resultado = comando.executeQuery()) {
                ResultSetMetaData metadados = resultado.getMetaData();
                int quantidadeColunas = metadados.getColumnCount();
                for (int coluna = 1; coluna <= quantidadeColunas; coluna++) {
                    tabela.addColumn(metadados.getColumnLabel(coluna));
                }
                while (resultado.next()) {
                    Object[] linha = new Object[quantidadeColunas];
                    for (int coluna = 1; coluna <= quantidadeColunas; coluna++) {
                        linha[coluna - 1] = resultado.getObject(coluna);
                    }
                    tabela.addRow(linha);
                }
            }
            return tabela;
        } catch (SQLException e) {
            System.err.println("Erro ao consultar treino: " + e.getMessage());
            return null;
        }
    }

    public void exibirTreinosUsuario(int usuarioId) {
        DefaultTableModel tabela = consultarTreinosUsuario(usuarioId);
        if (tabela == null || tabela.getRowCount() == 0) {
            System.out.println(tabela == null ? "Nao foi possivel consultar o treino." : "Nenhum treino ativo encontrado.");
            return;
        }
        System.out.println("===== MEU TREINO =====");
        for (int linha = 0; linha < tabela.getRowCount(); linha++) {
            for (int coluna = 0; coluna < tabela.getColumnCount(); coluna++) {
                System.out.print(tabela.getColumnName(coluna) + ": " + tabela.getValueAt(linha, coluna) + " | ");
            }
            System.out.println();
        }
    }

    public void exibirUsuarios() {
        DefaultTableModel tabela = ConsultaTodosDados();
        if (tabela == null || tabela.getRowCount() == 0) {
            System.out.println(tabela == null ? "Nao foi possivel consultar os usuarios." : "Nenhum usuario cadastrado.");
            return;
        }
        for (int linha = 0; linha < tabela.getRowCount(); linha++) {
            for (int coluna = 0; coluna < tabela.getColumnCount(); coluna++)
                System.out.print(tabela.getColumnName(coluna) + ": " + tabela.getValueAt(linha, coluna) + " | ");
            System.out.println();
        }
    }

    public static void main(String[] args) { new frmConsultas().exibirUsuarios(); }
}
