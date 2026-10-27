package Web;

import Control.frmConsultas;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Cria a tabela de evidência de aceite quando o serviço é iniciado. */
@Component
public class MigracaoAceiteLegal implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS usuario_aceite_legal ("
                + "id BIGINT NOT NULL AUTO_INCREMENT, "
                + "usuario_id INT NOT NULL, "
                + "versao_termos VARCHAR(30) NOT NULL, "
                + "versao_privacidade VARCHAR(30) NOT NULL, "
                + "consentimento_dados_saude BOOLEAN NOT NULL, "
                + "aceito_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (id), "
                + "KEY ix_usuario_aceite_legal_usuario (usuario_id), "
                + "CONSTRAINT fk_usuario_aceite_legal_usuario FOREIGN KEY (usuario_id) "
                + "REFERENCES usuario(id) ON DELETE CASCADE"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection conexao = new frmConsultas().abreConexao();
             var comando = conexao.createStatement()) {
            comando.executeUpdate(sql);
        } catch (SQLException e) {
            System.err.println("Não foi possível preparar o registro de aceite legal: " + e.getMessage());
            throw e;
        }
    }
}
