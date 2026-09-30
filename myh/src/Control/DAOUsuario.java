package Control;

import Model.Usuario;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.swing.table.DefaultTableModel;

/** Metodos de cadastro e autenticacao de usuarios. */
public class DAOUsuario {
    private static final int ITERACOES_HASH = 120000;
    private static final int TAMANHO_SALT = 16;
    private static final int TAMANHO_HASH_BITS = 256;
    private final frmConsultas consultas = new frmConsultas();

    public DefaultTableModel consultaUsuario() {
        return consultas.ConsultaTodosDados();
    }
    public boolean CadastrarObjetivo(Usuario usuario) {
    	String sql = "INSERT INTO objetivos "
                + "(usuario_id, objetivo, peso_meta, data_meta) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection conexao = consultas.abreConexao();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, usuario.getId());
            comando.setString(2, usuario.getObjetivo());
            comando.setFloat(3, usuario.getPeso_meta());
            if (usuario.getData_meta() == null) {
                comando.setNull(4, java.sql.Types.DATE);
            } else {
                comando.setDate(4, Date.valueOf(usuario.getData_meta()));
            }
            return comando.executeUpdate() == 1;
        } catch (SQLException ex) {
            System.err.println("Erro ao cadastrar objetivo: " + ex.getMessage());
            return false;
        }
    }
    public boolean CadastrarUsuario(Usuario usuario) {
        return cadastrarUsuarioERetornarId(usuario) > 0;
    }

    /** Cria o usuario e devolve o id gerado para iniciar a sessao com seguranca. */
    public int cadastrarUsuarioERetornarId(Usuario usuario) {
        String sql = "INSERT INTO usuario "
                + "(nome, email, senha, data_nascimento, sexo, altura, peso_inicial) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conexao = consultas.abreConexao();
             PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            comando.setString(1, usuario.getNome());
            comando.setString(2, usuario.getEmail());
            comando.setString(3, gerarHashSenha(usuario.getSenha()));
            if (usuario.getDataNascimento() == null) {
                comando.setNull(4, java.sql.Types.DATE);
            } else {
                comando.setDate(4, Date.valueOf(usuario.getDataNascimento()));
            }
            comando.setString(5, usuario.getSexo());
            comando.setFloat(6, usuario.getAltura());
            comando.setFloat(7, usuario.getPeso_inicial());
            if (comando.executeUpdate() != 1) return 0;
            try (ResultSet chaves = comando.getGeneratedKeys()) {
                return chaves.next() ? chaves.getInt(1) : 0;
            }
        } catch (SQLException | GeneralSecurityException ex) {
        	System.err.println("Erro ao cadastrar usuario: " + ex.getMessage());
        	ex.printStackTrace();
            return 0;
        }
    }

    public boolean validarLogin(String email, String senha) {
        String sql = "SELECT senha FROM usuario WHERE email = ?";
        try (Connection conexao = consultas.abreConexao();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, email);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() && verificarSenha(senha, resultado.getString("senha"));
            }
        } catch (SQLException | GeneralSecurityException | IllegalArgumentException ex) {
            System.err.println("Erro ao validar login: " + ex.getMessage());
            return false;
        }
    }

    public Usuario buscarPorEmail(String email) {
        String sql = "SELECT id, nome, email FROM usuario WHERE email = ?";
        try (Connection conexao = consultas.abreConexao();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, email);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) return null;
                Usuario usuario = new Usuario();
                usuario.setId(resultado.getInt("id"));
                usuario.setNome(resultado.getString("nome"));
                usuario.setEmail(resultado.getString("email"));
                return usuario;
            }
        } catch (SQLException ex) {
            System.err.println("Erro ao buscar usuario: " + ex.getMessage());
            return null;
        }
    }

    /** Guarda somente o hash do token, nunca o token enviado no link. */
    public boolean criarTokenRedefinicao(String email, String tokenHash, Instant expiraEm) throws SQLException {
        try (Connection conexao = consultas.abreConexao();
             PreparedStatement buscar = conexao.prepareStatement("SELECT id FROM usuario WHERE email = ?")) {
            buscar.setString(1, email);
            try (ResultSet resultado = buscar.executeQuery()) {
                if (!resultado.next()) return false;
                int usuarioId = resultado.getInt("id");
                try (PreparedStatement limite = conexao.prepareStatement(
                        "SELECT COUNT(*) FROM senha_redefinicao WHERE usuario_id = ? "
                                + "AND criada_em > CURRENT_TIMESTAMP - INTERVAL 1 MINUTE")) {
                    limite.setInt(1, usuarioId);
                    try (ResultSet recentes = limite.executeQuery()) {
                        if (recentes.next() && recentes.getInt(1) > 0) return false;
                    }
                }
                try (PreparedStatement apagar = conexao.prepareStatement(
                        "DELETE FROM senha_redefinicao WHERE usuario_id = ?")) {
                    apagar.setInt(1, usuarioId);
                    apagar.executeUpdate();
                }
                try (PreparedStatement inserir = conexao.prepareStatement(
                        "INSERT INTO senha_redefinicao (usuario_id, token_hash, expira_em) VALUES (?, ?, ?)")) {
                    inserir.setInt(1, usuarioId);
                    inserir.setString(2, tokenHash);
                    inserir.setTimestamp(3, Timestamp.from(expiraEm));
                    return inserir.executeUpdate() == 1;
                }
            }
        }
    }

    /** Valida e consome o link em uma transação; cada token só pode ser usado uma vez. */
    public boolean redefinirSenha(String tokenHash, String novaSenha) throws SQLException, GeneralSecurityException {
        try (Connection conexao = consultas.abreConexao()) {
            boolean autoCommitAnterior = conexao.getAutoCommit();
            conexao.setAutoCommit(false);
            try {
                int usuarioId;
                try (PreparedStatement buscar = conexao.prepareStatement(
                        "SELECT usuario_id FROM senha_redefinicao "
                                + "WHERE token_hash = ? AND expira_em > CURRENT_TIMESTAMP FOR UPDATE")) {
                    buscar.setString(1, tokenHash);
                    try (ResultSet resultado = buscar.executeQuery()) {
                        if (!resultado.next()) {
                            conexao.rollback();
                            return false;
                        }
                        usuarioId = resultado.getInt("usuario_id");
                    }
                }
                String senhaHash = gerarHashSenha(novaSenha);
                try (PreparedStatement atualizar = conexao.prepareStatement(
                        "UPDATE usuario SET senha = ? WHERE id = ?")) {
                    atualizar.setString(1, senhaHash);
                    atualizar.setInt(2, usuarioId);
                    if (atualizar.executeUpdate() != 1) {
                        conexao.rollback();
                        return false;
                    }
                }
                try (PreparedStatement consumir = conexao.prepareStatement(
                        "DELETE FROM senha_redefinicao WHERE usuario_id = ?")) {
                    consumir.setInt(1, usuarioId);
                    consumir.executeUpdate();
                }
                conexao.commit();
                return true;
            } catch (SQLException | GeneralSecurityException | RuntimeException e) {
                conexao.rollback();
                throw e;
            } finally {
                conexao.setAutoCommit(autoCommitAnterior);
            }
        }
    }

    private String gerarHashSenha(String senha) throws GeneralSecurityException {
        byte[] salt = new byte[TAMANHO_SALT];
        new SecureRandom().nextBytes(salt);
        byte[] hash = gerarPbkdf2(senha.toCharArray(), salt, ITERACOES_HASH);
        return ITERACOES_HASH + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verificarSenha(String senha, String senhaHash) throws GeneralSecurityException {
        String[] partes = senhaHash.split(":");
        if (partes.length != 3) return false;
        int iteracoes = Integer.parseInt(partes[0]);
        byte[] salt = Base64.getDecoder().decode(partes[1]);
        byte[] hashEsperado = Base64.getDecoder().decode(partes[2]);
        byte[] hashRecebido = gerarPbkdf2(senha.toCharArray(), salt, iteracoes);
        return comparacaoTempoConstante(hashEsperado, hashRecebido);
    }

    private byte[] gerarPbkdf2(char[] senha, byte[] salt, int iteracoes) throws GeneralSecurityException {
        PBEKeySpec especificacao = new PBEKeySpec(senha, salt, iteracoes, TAMANHO_HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacao).getEncoded();
        } finally {
            especificacao.clearPassword();
        }
    }

    private boolean comparacaoTempoConstante(byte[] primeiro, byte[] segundo) {
        if (primeiro.length != segundo.length) return false;
        int diferenca = 0;
        for (int indice = 0; indice < primeiro.length; indice++) diferenca |= primeiro[indice] ^ segundo[indice];
        return diferenca == 0;
    }
}
