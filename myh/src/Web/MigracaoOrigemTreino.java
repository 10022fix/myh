package Web;

import Control.frmConsultas;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Identifica treinos recomendados e treinos criados pelo próprio aluno. */
@Component
public class MigracaoOrigemTreino implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conexao = new frmConsultas().abreConexao()) {
            if (!existeColuna(conexao)) {
                try (var migracao = conexao.createStatement()) {
                    migracao.executeUpdate(
                            "ALTER TABLE treinos ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'RECOMENDADO' AFTER frequencia_semanal");
                }
            }
        } catch (SQLException erro) {
            System.err.println("Não foi possível preparar a origem dos treinos: " + erro.getMessage());
            throw erro;
        }
    }

    private boolean existeColuna(Connection conexao) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='treinos' AND COLUMN_NAME='origem'";
        try (PreparedStatement consulta = conexao.prepareStatement(sql);
             ResultSet resultado = consulta.executeQuery()) {
            resultado.next();
            return resultado.getInt(1) > 0;
        }
    }
}