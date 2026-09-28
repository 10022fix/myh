package Control;

import java.util.Scanner;

public class frmLogin {
    public static void main(String[] args) {
        Scanner entrada = new Scanner(System.in);
        DAOUsuario usuarios = new DAOUsuario();
        System.out.println("===== LOGIN =====");
        System.out.print("E-mail: ");
        String email = entrada.nextLine();
        System.out.print("Senha: ");
        String senha = entrada.nextLine();
        if (usuarios.validarLogin(email, senha)) {
            System.out.println("Login realizado com sucesso!");
        } else {
            System.out.println("E-mail ou senha invalidos.");
        }
        entrada.close();
    }
}
