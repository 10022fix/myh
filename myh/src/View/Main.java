package View;

import Control.DAOUsuario;
import Control.frmConsultas;
import Model.Usuario;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

public class Main {
	static Usuario usuarioLogado = null;
    public static void main(String[] args) {
        Scanner entrada = new Scanner(System.in);
        DAOUsuario usuarios = new DAOUsuario();

        boolean sistemaAtivo = true;
        while (sistemaAtivo) {
        	barraCarregamento();
            System.out.println("\n===== MENU INICIAL =====");
            System.out.println("1 - Cadastrar usuario");
            System.out.println("2 - Fazer login");
            System.out.println("0 - Sair");
            System.out.print("Opcao: ");

            switch(lerOpcao(entrada)) {
                case 1:
                    cadastrarUsuario(entrada, usuarios);
                    break;
                case 2:
                    fazerLogin(entrada, usuarios);
                    break;
                case 0:
                    sistemaAtivo = false;
                    break;
                default:
                    System.out.println("Opcao invalida.");
            }
        }
        entrada.close();
    }
    private static void barraCarregamento() {
        System.out.print("Carregando: [");

        for (int i = 0; i < 20; i++) {
            System.out.print("█");

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("] 100% ✓");
    }

    private static void cadastrarUsuario(Scanner entrada, DAOUsuario usuarios) {
        Usuario usuario = new Usuario();
        System.out.println("\n===== CADASTRO =====");
        System.out.print("Nome: ");
        usuario.setNome(entrada.nextLine());
        System.out.print("E-mail: ");
        usuario.setEmail(entrada.nextLine());
        System.out.print("Senha: ");
        usuario.setSenha(entrada.nextLine());
        usuario.setDataNascimento(lerDataOpcional(entrada));
        System.out.print("Sexo (deixe vazio se nao informar): ");
        usuario.setSexo(entrada.nextLine());
        System.out.print("Altura em metros (ex.: 1,75): ");
        usuario.setAltura(lerDecimal(entrada));
        System.out.print("Peso inicial em kg (ex.: 70,50): ");
        usuario.setPeso_inicial(lerDecimal(entrada));
        if (usuarios.CadastrarUsuario(usuario)) {
            Usuario usuarioLogado = usuarios.buscarPorEmail(usuario.getEmail());

            if (usuarioLogado != null) {
                System.out.println("Usuario cadastrado.");
                System.out.println("ID: " + usuarioLogado.getId());

                menuUsuarioLogado(entrada, usuarioLogado);
            }
        } else {
            System.out.println("Nao foi possivel cadastrar o usuario.");
        }
    }

    private static void fazerLogin(Scanner entrada, DAOUsuario usuarios) {
        System.out.println("\n===== LOGIN =====");
        System.out.print("E-mail: ");
        String email = entrada.nextLine();
        System.out.print("Senha: ");
        String senha = entrada.nextLine();

        if (!usuarios.validarLogin(email, senha)) {
            System.out.println("E-mail ou senha invalidos.");
            return;
        }

        Usuario usuarioLogado = usuarios.buscarPorEmail(email);

        if (usuarioLogado == null) {
            System.out.println("Nao foi possivel carregar os dados do usuario.");
            return;
        }

        System.out.println("Login realizado com sucesso!");
        System.out.println("Bem-vindo, " + usuarioLogado.getNome() + ".");

        menuUsuarioLogado(entrada, usuarioLogado);
    }
    private static void objetivoUsuario(Usuario usuarioLogado) {
        DAOUsuario usuarios = new DAOUsuario();
    	Usuario usuario = new Usuario();
    	Scanner entrada = new Scanner(System.in);
    	if (usuarioLogado == null) {
    	    System.out.println("Voce precisa fazer login.");
    	    return;
    	}

    	if (usuarioLogado.getId() <= 0) {
    	    System.out.println("Usuario sem ID valido.");
    	    return;
    	}
    	System.out.println("\n===== OBJETIVOS =====");
        System.out.println("Qual o objetivo do Usuário?");
        System.out.println("1- Emagrecer");
        System.out.println("2- Ganhar Massa Muscular");
        System.out.println("3- Condicionamento");
        System.out.println("4- Definição");
        System.out.println("5- Saúde");
        System.out.print("Sua Opção (Em Número): ");
        int obj = entrada.nextInt();
        entrada.nextLine();
        switch(obj) {
        case(1):
        	usuarioLogado.setObjetivo("Emagrecer");
        	break;
        case(2):
        	usuarioLogado.setObjetivo("Ganhar Massa");
        	break;
        case(3):
        	usuarioLogado.setObjetivo("Condicionamento");
        	break;
        case(4):
        	usuarioLogado.setObjetivo("Definicao");
        	break;
        case(5):
        	usuarioLogado.setObjetivo("Saude");
        	break;
        }
        System.out.print("Qual o peso meta que deseja chegar: ");
        usuarioLogado.setPeso_meta(entrada.nextFloat());
        entrada.nextLine();
        
        usuarioLogado.setData_meta(lerDataGeral(entrada));
        if (usuarios.CadastrarObjetivo(usuarioLogado)) {
            System.out.println("Objetivo cadastrado com sucesso.");
        } else {
            System.out.println("Erro ao cadastrar objetivo.");
        }
    }

    private static void menuUsuarioLogado(Scanner entrada, Usuario usuario) {
        frmConsultas consultas = new frmConsultas();
        boolean logado = true;
        while (logado) {
            System.out.println("\n===== MENU DO USUARIO =====");
            System.out.println("1 - Ver meu treino");
            System.out.println("2 - Consultar usuarios cadastrados");
            System.out.println("3 - Cadastrar Objetivo");
            System.out.println("4 - Logout");
            System.out.print("Opcao: ");

            switch (lerOpcao(entrada)) {
                case 1:
                    consultas.exibirTreinosUsuario(usuario.getId());
                    break;
                case 2:
                    consultas.exibirUsuarios();
                    break;
                case 3:
                	objetivoUsuario(usuario);
                	break;
                case 4:
                    logado = false;
                    System.out.println("Logout realizado.");
                    break;
                
                default:
                    System.out.println("Opcao invalida.");
            }
        }
    }

    private static int lerOpcao(Scanner entrada) {
        try {
            return Integer.parseInt(entrada.nextLine());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static LocalDate lerDataOpcional(Scanner entrada) {
        while (true) {
            System.out.print("Data de nascimento (AAAA-MM-DD; deixe vazio se nao informar): ");
            String texto = entrada.nextLine();
            if (texto.isEmpty()) return null;
            try {
                return LocalDate.parse(texto);
            } catch (DateTimeParseException e) {
                System.out.println("Data invalida. Use o formato AAAA-MM-DD.");
            }
        }
    }
    private static LocalDate lerDataGeral(Scanner entrada) {
        while (true) {
            System.out.print(
                "Data que deseja alcancar seu objetivo (AAAA-MM-DD; deixe vazio se nao informar): "
            );

            String texto = entrada.nextLine();

            if (texto.isEmpty()) {
                return null;
            }

            try {
                return LocalDate.parse(texto);
            } catch (DateTimeParseException e) {
                System.out.println("Data invalida. Use o formato AAAA-MM-DD.");
            }
        }
    }

    private static float lerDecimal(Scanner entrada) {
        while (true) {
            try {
                return Float.parseFloat(entrada.nextLine().replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.print("Valor invalido. Digite novamente: ");
            }
        }
    }
}
