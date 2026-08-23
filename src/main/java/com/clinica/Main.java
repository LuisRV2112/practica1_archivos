package com.clinica;

import com.clinica.persistencia.ArchivoMedicos;
import com.clinica.servicio.ServicioMedicos;
import com.clinica.vista.VentanaPrincipal;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;

/**
 * Punto de entrada del sistema. Abre los archivos de datos, arma las capas de
 * servicio y levanta la ventana principal.
 */
public class Main {

    /** Carpeta donde viven los archivos binarios, relativa a donde se ejecute. */
    private static final String CARPETA_DATOS = "datos";

    public static void main(String[] args) {
        // Swing exige que la interfaz se construya en el hilo de eventos (EDT).
        SwingUtilities.invokeLater(Main::iniciar);
    }

    private static void iniciar() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) {
            // Si el sistema no ofrece su propia apariencia se usa la de Java.
        }

        try {
            ArchivoMedicos archivoMedicos =
                    new ArchivoMedicos(CARPETA_DATOS + "/medicos.dat");

            ServicioMedicos servicioMedicos = new ServicioMedicos(archivoMedicos);

            new VentanaPrincipal(archivoMedicos, servicioMedicos).setVisible(true);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "No se pudieron abrir los archivos de datos:\n" + e.getMessage(),
                    "Error al iniciar", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
