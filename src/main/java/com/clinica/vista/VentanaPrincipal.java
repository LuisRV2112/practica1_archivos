package com.clinica.vista;

import com.clinica.persistencia.ArchivoBitacora;
import com.clinica.persistencia.ArchivoCitas;
import com.clinica.persistencia.ArchivoMedicos;
import com.clinica.persistencia.ArchivoPacientes;
import com.clinica.servicio.ServicioBitacora;
import com.clinica.servicio.ServicioCitas;
import com.clinica.servicio.ServicioCargaMasiva;
import com.clinica.servicio.ResultadoCarga;
import com.clinica.servicio.ServicioReportes;
import com.clinica.servicio.ServicioMedicos;
import com.clinica.servicio.ServicioPacientes;

import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
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
    private final ArchivoBitacora archivoBitacora;
    private final ServicioCargaMasiva servicioCarga;

    private PanelMedicos panelMedicos;
    private PanelPacientes panelPacientes;
    private PanelCitas panelCitas;
    private PanelReportes panelReportes;

    public VentanaPrincipal(ArchivoMedicos archivoMedicos, ServicioMedicos servicioMedicos,
                            ArchivoPacientes archivoPacientes, ServicioPacientes servicioPacientes,
                            ArchivoCitas archivoCitas, ServicioCitas servicioCitas,
                            ArchivoBitacora archivoBitacora, ServicioBitacora servicioBitacora,
                            ServicioReportes servicioReportes,
                            ServicioCargaMasiva servicioCarga) {
        this.servicioCarga = servicioCarga;
        this.archivoMedicos = archivoMedicos;
        this.archivoPacientes = archivoPacientes;
        this.archivoCitas = archivoCitas;
        this.archivoBitacora = archivoBitacora;

        setTitle("Sistema de Gestion de Clinica Medica");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // se cierra a mano, tras cerrar archivos
        setSize(1100, 640);
        setLocationRelativeTo(null);

        panelMedicos = new PanelMedicos(servicioMedicos);
        panelPacientes = new PanelPacientes(servicioPacientes);
        panelCitas = new PanelCitas(servicioCitas, servicioMedicos, servicioPacientes);
        panelReportes = new PanelReportes(servicioReportes, servicioMedicos,
                servicioPacientes, servicioBitacora);

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Medicos", panelMedicos);
        pestanas.addTab("Pacientes", panelPacientes);
        pestanas.addTab("Citas", panelCitas);
        pestanas.addTab("Reportes", panelReportes);

        setJMenuBar(construirMenu());
        add(pestanas, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrarAplicacion();
            }
        });
    }

    /**
     * Cierra todos los archivos abiertos antes de terminar. Se intenta cerrar
     * cada uno aunque otro falle, para no dejar descriptores colgando.
     */
    // =======================================================================
    // MENU DE CARGA MASIVA
    // =======================================================================

    private JMenuBar construirMenu() {
        JMenuBar barra = new JMenuBar();

        JMenu datos = new JMenu("Datos");
        datos.setMnemonic('D');

        JMenuItem medicos = new JMenuItem("Cargar medicos desde CSV...");
        JMenuItem pacientes = new JMenuItem("Cargar pacientes desde CSV...");
        JMenuItem citas = new JMenuItem("Cargar citas desde CSV...");
        JMenuItem plantillas = new JMenuItem("Generar plantillas CSV...");

        medicos.addActionListener(e -> cargar("medicos"));
        pacientes.addActionListener(e -> cargar("pacientes"));
        citas.addActionListener(e -> cargar("citas"));
        plantillas.addActionListener(e -> generarPlantillas());

        datos.add(medicos);
        datos.add(pacientes);
        datos.add(citas);
        datos.addSeparator();
        datos.add(plantillas);

        JMenu ayuda = new JMenu("Ayuda");
        ayuda.setMnemonic('A');
        JMenuItem formatos = new JMenuItem("Formato de los archivos CSV");
        formatos.addActionListener(e -> mostrarFormatos());
        ayuda.add(formatos);

        barra.add(datos);
        barra.add(ayuda);
        return barra;
    }

    /**
     * Pide el archivo y ejecuta la carga.
     *
     * El orden en que se cargan importa: una cita necesita que su paciente y su
     * medico ya existan. Por eso el menu los lista en ese orden y el aviso lo
     * recuerda si la carga de citas falla entera.
     */
    private void cargar(String entidad) {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Seleccione el archivo CSV de " + entidad);
        selector.setFileFilter(new FileNameExtensionFilter("Archivos CSV", "csv"));

        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = selector.getSelectedFile();

        try {
            ResultadoCarga resultado = switch (entidad) {
                case "medicos" -> servicioCarga.cargarMedicos(archivo);
                case "pacientes" -> servicioCarga.cargarPacientes(archivo);
                default -> servicioCarga.cargarCitas(archivo);
            };

            DialogoCarga.mostrar(this, resultado);
            refrescarTodo();

        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo leer el archivo:\n" + ex.getMessage(),
                    "Error al cargar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generarPlantillas() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Elija donde guardar las plantillas");
        selector.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            servicioCarga.generarPlantillas(selector.getSelectedFile());
            JOptionPane.showMessageDialog(this,
                    "Se generaron las tres plantillas en:\n"
                            + selector.getSelectedFile().getAbsolutePath(),
                    "Plantillas generadas", JOptionPane.INFORMATION_MESSAGE);

        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudieron generar las plantillas:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mostrarFormatos() {
        JOptionPane.showMessageDialog(this,
                """
                Los archivos CSV se separan por comas y se guardan en UTF-8.
                La primera fila puede ser el encabezado; el sistema la detecta.

                MEDICOS
                  nombres, apellidos, especialidad, telefono, correo,
                  horaInicio, horaFin, activo
                  Horas en formato HH:mm. "activo" acepta si/no.

                PACIENTES
                  identificacion, nombres, apellidos, nacimiento, sexo,
                  telefono, correo, tipoSangre
                  Fecha dd/mm/aaaa. Sexo M o F. Sangre O+, A-, AB+, etc.

                CITAS
                  identificacionPaciente, medico, fecha, hora, motivo,
                  observaciones
                  La columna "medico" admite su codigo o su nombre completo.
                  Cargue primero medicos y pacientes: una cita necesita que
                  ambos existan.

                Un campo que contenga comas debe ir entre comillas dobles.
                """,
                "Formato de los archivos CSV", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Tras una carga, todas las pantallas deben volver a consultar los archivos. */
    private void refrescarTodo() {
        panelMedicos.recargar();
        panelPacientes.recargar();
        panelCitas.recargar();
        panelReportes.recargar();
    }

    private void cerrarAplicacion() {
        try {
            archivoMedicos.close();
            archivoPacientes.close();
            archivoCitas.close();
            archivoBitacora.close();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo cerrar correctamente el archivo de datos:\n" + ex.getMessage(),
                    "Error al cerrar", JOptionPane.ERROR_MESSAGE);
        }
        dispose();
        System.exit(0);
    }
}