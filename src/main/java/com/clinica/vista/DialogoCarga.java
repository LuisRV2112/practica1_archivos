package com.clinica.vista;

import com.clinica.servicio.ResultadoCarga;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Muestra el resultado de una carga masiva. Si hubo rechazos, lista las filas
 * con error en un área desplazable para que el usuario sepa qué corregir.
 */
public final class DialogoCarga {

    private DialogoCarga() {
    }

    public static void mostrar(Component padre, ResultadoCarga resultado) {

        if (!resultado.huboErrores()) {
            JOptionPane.showMessageDialog(padre,
                    "Se cargaron " + resultado.getInsertados()
                            + " registros de " + resultado.getEntidad() + " sin errores.",
                    "Carga completa", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialogo = new JDialog(
                javax.swing.SwingUtilities.getWindowAncestor(padre),
                "Resultado de la carga",
                JDialog.ModalityType.APPLICATION_MODAL);

        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));

        JLabel titulo = new JLabel(String.format(
                "<html>Carga de %s:<br>"
                        + "<font color='#1a7f37'><b>%d</b> registros cargados</font> &nbsp;|&nbsp; "
                        + "<font color='#b03030'><b>%d</b> rechazados</font></html>",
                resultado.getEntidad(),
                resultado.getInsertados(),
                resultado.getRechazados()));
        encabezado.add(titulo, BorderLayout.CENTER);

        JTextArea detalle = new JTextArea();
        detalle.setEditable(false);
        detalle.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detalle.setForeground(new Color(70, 30, 30));

        StringBuilder texto = new StringBuilder("Filas que no se pudieron cargar:\n\n");
        for (String error : resultado.getErrores()) {
            texto.append(error).append('\n');
        }
        detalle.setText(texto.toString());
        detalle.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(detalle);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
        scroll.setPreferredSize(new Dimension(620, 260));

        JPanel pie = new JPanel();
        JButton cerrar = new JButton("Cerrar");
        cerrar.addActionListener(e -> dialogo.dispose());
        pie.add(cerrar);

        dialogo.setLayout(new BorderLayout());
        dialogo.add(encabezado, BorderLayout.NORTH);
        dialogo.add(scroll, BorderLayout.CENTER);
        dialogo.add(pie, BorderLayout.SOUTH);

        dialogo.pack();
        dialogo.setLocationRelativeTo(padre);
        dialogo.setVisible(true);
    }
}
