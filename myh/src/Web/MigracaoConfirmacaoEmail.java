package Web;

import Control.frmConsultas;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Mantém as contas que já existiam confirmadas ao adicionar a confirmação por e-mail. */
@Component
public class MigracaoConfirmacaoEmail implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        frmConsultas consultas = new frmConsultas();
        try (Connection conexao = consultas.abreConexao();
             PreparedStatement consulta = conexao.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.COLUMNS "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' "
                             + "AND COLUMN_NAME = 'email_verificado'")) {
            try (ResultSet resultado = consulta.executeQuery()) {
                resultado.next();
                if (resultado.getInt(1) == 0) {
                    try (var migracao = conexao.createStatement()) {
                        migracao.executeUpdate("ALTER TABLE usuario ADD COLUMN "
                                + "email_verificado BOOLEAN NOT NULL DEFAULT TRUE");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Não foi possível preparar a confirmação de e-mail: " + e.getMessage());
            throw e;
        }
    }
}
