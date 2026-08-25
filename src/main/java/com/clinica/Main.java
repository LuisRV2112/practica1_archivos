package com.clinica;

import com.clinica.persistencia.ArchivoBitacora;
import com.clinica.persistencia.ArchivoCitas;
import com.clinica.persistencia.ArchivoMedicos;
import com.clinica.persistencia.ArchivoPacientes;
import com.clinica.servicio.ServicioBitacora;
import com.clinica.servicio.ServicioCitas;
import com.clinica.servicio.ServicioCargaMasiva;
import com.clinica.servicio.ServicioReportes;
import com.clinica.servicio.ServicioMedicos;
import com.clinica.servicio.ServicioPacientes;
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
            // Primero los archivos: los servicios dependen de ellos, nunca al reves.
            ArchivoMedicos archivoMedicos =
                    new ArchivoMedicos(CARPETA_DATOS + "/medicos.dat");
            ArchivoPacientes archivoPacientes =
                    new ArchivoPacientes(CARPETA_DATOS + "/pacientes.dat");
            ArchivoCitas archivoCitas =
                    new ArchivoCitas(CARPETA_DATOS + "/citas.dat");
            ArchivoBitacora archivoBitacora =
                    new ArchivoBitacora(CARPETA_DATOS + "/bitacora.dat");

            // La bitacora se arma primero: los tres servicios escriben en ella.
            ServicioBitacora servicioBitacora = new ServicioBitacora(archivoBitacora);

    // Los servicios reciben ArchivoCitas para validaciones cruzadas (no borrar
    // paciente con citas activas, no dejar citas fuera del horario del médico).
            ServicioMedicos servicioMedicos =
                    new ServicioMedicos(archivoMedicos, archivoCitas, servicioBitacora);
            ServicioPacientes servicioPacientes =
                    new ServicioPacientes(archivoPacientes, archivoCitas, servicioBitacora);
            ServicioCitas servicioCitas =
                    new ServicioCitas(archivoCitas, archivoMedicos, archivoPacientes,
                            servicioBitacora);

            // Reportes: se apoya en los demás servicios, no lee archivos.
            ServicioReportes servicioReportes = new ServicioReportes(
                    servicioMedicos, servicioPacientes, servicioCitas, servicioBitacora);

            ServicioCargaMasiva servicioCarga = new ServicioCargaMasiva(
                    servicioMedicos, servicioPacientes, servicioCitas, servicioBitacora);

            new VentanaPrincipal(archivoMedicos, servicioMedicos,
                    archivoPacientes, servicioPacientes,
                    archivoCitas, servicioCitas,
                    archivoBitacora, servicioBitacora, servicioReportes,
                    servicioCarga).setVisible(true);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "No se pudieron abrir los archivos de datos:\n" + e.getMessage(),
                    "Error al iniciar", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}