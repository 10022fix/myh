package Control;

import javax.swing.table.DefaultTableModel;
import java.time.LocalDate;

public class frmConsultaCadastro {

    public static void main(String[] args) {

        frmConsultas c = new frmConsultas();

        DefaultTableModel dados = c.ConsultaTodosDados();

        if (dados == null) {
            System.out.println("Nenhum dado retornado.");
            return;
        }

        System.out.println("===== CADASTROS =====");

        for (int linha = 0; linha < dados.getRowCount(); linha++) {

            for (int coluna = 0; coluna < dados.getColumnCount(); coluna++) {

                System.out.print(
                    dados.getColumnName(coluna) + ": " +
                    dados.getValueAt(linha, coluna) + " | "
                );

            }

            System.out.println();
        }

        System.out.println("=====================");
    }
}
