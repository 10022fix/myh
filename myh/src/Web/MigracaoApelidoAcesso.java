package Web;

import Control.frmConsultas;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Adiciona o identificador unico de acesso sem alterar as contas existentes. */
@Component
public class MigracaoApelidoAcesso implements ApplicationRunner {
    private static final String NOME_INDICE = "uk_usuario_apelido_acesso";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        frmConsultas consultas = new frmConsultas();
        try (Connection conexao = consultas.abreConexao()) {
            if (!existeColuna(conexao)) {
                try (var migracao = conexao.createStatement()) {
                    migracao.executeUpdate(
                            "ALTER TABLE usuario ADD COLUMN apelido_acesso VARCHAR(30) NULL AFTER nome");
                }
            }

            if (!existeIndice(conexao)) {
                try (var migracao = conexao.createStatement()) {
                    migracao.executeUpdate("CREATE UNIQUE INDEX " + NOME_INDICE
                            + " ON usuario (apelido_acesso)");
                }
            }
        } catch (SQLException e) {
            System.err.println("Nao foi possivel preparar o apelido de acesso: " + e.getMessage());
            throw e;
        }
    }

    private boolean existeColuna(Connection conexao) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' "
                + "AND COLUMN_NAME = 'apelido_acesso'";
        try (PreparedStatement consulta = conexao.prepareStatement(sql);
             ResultSet resultado = consulta.executeQuery()) {
            resultado.next();
            return resultado.getInt(1) > 0;
        }
    }

    private boolean existeIndice(Connection conexao) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' "
                + "AND INDEX_NAME = ?";
        try (PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setString(1, NOME_INDICE);
            try (ResultSet resultado = consulta.executeQuery()) {
                resultado.next();
                return resultado.getInt(1) > 0;
            }
        }
    }
}
