package Documentacao;

/**
 * GuiaJava e uma classe educativa: ela nao e chamada pelo sistema e existe para
 * explicar como as partes Java deste projeto se comunicam.
 *
 * 1. A pasta Model guarda objetos simples, como Usuario. Eles representam dados.
 * 2. A pasta Control guarda DAO (Data Access Object). DAOUsuario e DAOAreaAluno
 *    usam JDBC, PreparedStatement e ResultSet para ler/gravar no MySQL.
 * 3. A pasta Web guarda controllers do Spring. Um controller recebe uma rota HTTP
 *    como /api/login, valida os dados, chama o DAO e responde JSON ao front-end.
 * 4. HttpSession guarda apenas o id e o nome do usuario autenticado. Toda rota
 *    privada usa esse id da sessao; o navegador nunca escolhe qual usuario consultar.
 * 5. O HTML usa fetch() para chamar as rotas e atualiza a tela com a resposta.
 *
 * Cuidados de seguranca ja utilizados no projeto:
 * - senhas sao armazenadas com PBKDF2 e salt, nunca como texto puro;
 * - PreparedStatement separa SQL dos valores e reduz risco de SQL Injection;
 * - validacoes tambem ficam no controller, pois a validacao apenas no HTML pode
 *   ser ignorada por quem enviar uma requisicao manualmente;
 * - a sessao e renovada depois do login/cadastro para evitar session fixation;
 * - os DAOs usam try-with-resources, fechando conexao, consulta e resultado.
 */
public final class GuiaJava {
    // Construtor privado: ninguem precisa criar um objeto desta classe de documentacao.
    private GuiaJava() { }

    /**
     * Exemplo conceitual do caminho de uma requisicao:
     * navegador -> Controller -> DAO -> MySQL -> DAO -> Controller -> JSON -> navegador.
     *
     * O metodo esta vazio porque a documentacao nao deve alterar dados reais.
     */
    public static void exemploFluxo() {
        // Nenhuma instrucao e executada aqui propositalmente.
    }
}
