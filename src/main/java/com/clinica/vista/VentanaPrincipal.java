package com.clinica.vista;

import com.clinica.persistencia.ArchivoCitas;
import com.clinica.persistencia.ArchivoMedicos;
import com.clinica.persistencia.ArchivoPacientes;
import com.clinica.servicio.ServicioCitas;
import com.clinica.servicio.ServicioMedicos;
import com.clinica.servicio.ServicioPacientes;

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
    private final ArchivoPacientes archivoPacientes;
    private final ArchivoCitas archivoCitas;

    public VentanaPrincipal(ArchivoMedicos archivoMedicos, ServicioMedicos servicioMedicos,
                            ArchivoPacientes archivoPacientes, ServicioPacientes servicioPacientes,
                            ArchivoCitas archivoCitas, ServicioCitas servicioCitas) {
        this.archivoMedicos = archivoMedicos;
        this.archivoPacientes = archivoPacientes;
        this.archivoCitas = archivoCitas;

        setTitle("Sistema de Gestion de Clinica Medica");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // se cierra a mano, tras cerrar archivos
        setSize(1100, 640);
        setLocationRelativeTo(null);

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Medicos", new PanelMedicos(servicioMedicos));
        pestanas.addTab("Pacientes", new PanelPacientes(servicioPacientes));
        pestanas.addTab("Citas", new PanelCitas(servicioCitas, servicioMedicos, servicioPacientes));
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

    /**
     * Cierra todos los archivos abiertos antes de terminar. Se intenta cerrar
     * cada uno aunque otro falle, para no dejar descriptores colgando.
     */
    private void cerrarAplicacion() {
        try {
            archivoMedicos.close();
            archivoPacientes.close();
            archivoCitas.close();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo cerrar correctamente el archivo de datos:\n" + ex.getMessage(),
                    "Error al cerrar", JOptionPane.ERROR_MESSAGE);
        }
        dispose();
        System.exit(0);
    }
}