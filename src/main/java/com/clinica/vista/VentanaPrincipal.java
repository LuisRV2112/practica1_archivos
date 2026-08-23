package com.clinica.vista;

import com.clinica.persistencia.ArchivoMedicos;
import com.clinica.servicio.ServicioMedicos;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * Ventana principal de la aplicacion. Cada modulo del enunciado ocupa una
 * pestana.
 *
 * Tambien se encarga de cerrar los archivos al salir: RandomAccessFile mantiene
 * un descriptor abierto del sistema operativo, y dejarlo sin cerrar puede hacer
 * que los ultimos datos escritos no lleguen a disco.
 */
public class VentanaPrincipal extends JFrame {

    private final ArchivoMedicos archivoMedicos;

    public VentanaPrincipal(ArchivoMedicos archivoMedicos, ServicioMedicos servicioMedicos) {
        this.archivoMedicos = archivoMedicos;

        setTitle("Sistema de Gestion de Clinica Medica");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // se cierra a mano, tras cerrar archivos
        setSize(1100, 640);
        setLocationRelativeTo(null);

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Medicos", new PanelMedicos(servicioMedicos));
        pestanas.addTab("Pacientes", panelPendiente("Modulo de pacientes"));
        pestanas.addTab("Citas", panelPendiente("Modulo de citas"));
        pestanas.addTab("Reportes", panelPendiente("Modulo de reportes"));

        add(pestanas, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrarAplicacion();
            }
        });
    }

    /** Marcador temporal para los modulos que todavia no se han construido. */
    private JPanel panelPendiente(String nombre) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(nombre + " — en construccion", SwingConstants.CENTER),
                BorderLayout.CENTER);
        return panel;
    }

    private void cerrarAplicacion() {
        try {
            archivoMedicos.close();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo cerrar correctamente el archivo de datos:\n" + ex.getMessage(),
                    "Error al cerrar", JOptionPane.ERROR_MESSAGE);
        }
        dispose();
        System.exit(0);
    }
}
