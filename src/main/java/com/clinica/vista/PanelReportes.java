package com.clinica.vista;

import com.clinica.modelo.EstadoCita;
import com.clinica.modelo.Medico;
import com.clinica.modelo.Paciente;
import com.clinica.modelo.Reporte;
import com.clinica.modelo.TipoOperacion;
import com.clinica.modelo.TipoSangre;
import com.clinica.servicio.ExcepcionValidacion;
import com.clinica.servicio.ExportadorReportes;
import com.clinica.servicio.ServicioBitacora;
import com.clinica.servicio.ServicioMedicos;
import com.clinica.servicio.ServicioPacientes;
import com.clinica.servicio.ServicioReportes;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Pantalla de reportes. Un solo panel para los 15 reportes del enunciado: todos
 * devuelven la misma estructura genérica (Reporte). CardLayout muestra solo los
 * controles del reporte elegido.
 */
public class PanelReportes extends JPanel {

    /** Catálogo de reportes. Agregar uno nuevo = agregar constante + caso en
     *  generar(). */
    private enum TipoReporte {
        PACIENTES_COMPLETO("Pacientes - listado completo", "ninguno"),
        PACIENTES_POR_SANGRE("Pacientes - por tipo de sangre", "sangre"),
        PACIENTES_MAS_CITAS("Pacientes - con mayor cantidad de citas", "ninguno"),
        PACIENTES_SIN_CITAS("Pacientes - que nunca han tenido cita", "ninguno"),

        MEDICOS_COMPLETO("Medicos - listado completo", "ninguno"),
        MEDICOS_POR_ESPECIALIDAD("Medicos - por especialidad", "especialidad"),
        MEDICOS_MAS_CITAS("Medicos - con mayor cantidad de citas", "ninguno"),
        MEDICOS_CITAS_EN_FECHA("Medicos - con citas en una fecha", "fecha"),

        CITAS_COMPLETO("Citas - listado completo", "ninguno"),
        CITAS_POR_RANGO("Citas - por rango de fechas", "rango"),
        CITAS_POR_MEDICO("Citas - por medico", "medico"),
        CITAS_POR_PACIENTE("Citas - por paciente", "paciente"),
        CITAS_POR_ESTADO("Citas - por estado", "estado"),
        CITAS_POR_ESPECIALIDAD("Citas - cantidad por especialidad", "ninguno"),

        BITACORA("Bitacora de operaciones", "ninguno");

        private final String etiqueta;
        private final String tarjeta;

        TipoReporte(String etiqueta, String tarjeta) {
            this.etiqueta = etiqueta;
            this.tarjeta = tarjeta;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    private final ServicioReportes servicio;
    private final ServicioMedicos servicioMedicos;
    private final ServicioPacientes servicioPacientes;
    private final ServicioBitacora bitacora;

    private final JComboBox<TipoReporte> cboReporte =
            new JComboBox<>(TipoReporte.values());

    // --- Parametros ---
    private final CardLayout tarjetas = new CardLayout();
    private final JPanel panelParametros = new JPanel(tarjetas);

    private final JComboBox<TipoSangre> cboSangre = new JComboBox<>(TipoSangre.values());
    private final JComboBox<String> cboEspecialidad = new JComboBox<>();
    private final JComboBox<Medico> cboMedico = new JComboBox<>();
    private final JComboBox<Paciente> cboPaciente = new JComboBox<>();
    private final JComboBox<EstadoCita> cboEstado = new JComboBox<>(EstadoCita.values());
    private final CampoFecha txtFecha = new CampoFecha();
    private final CampoFecha txtDesde = new CampoFecha();
    private final CampoFecha txtHasta = new CampoFecha();

    private final JButton btnGenerar = new JButton("Generar");
    private final JButton btnExportarCsv = new JButton("Exportar CSV");
    private final JButton btnExportarTxt = new JButton("Exportar TXT");

    private final ModeloTablaReporte modeloTabla = new ModeloTablaReporte();
    private final JTable tabla = new JTable(modeloTabla);
    private final JLabel lblEstado = new JLabel(" ");

    public PanelReportes(ServicioReportes servicio, ServicioMedicos servicioMedicos,
                         ServicioPacientes servicioPacientes, ServicioBitacora bitacora) {
        this.servicio = servicio;
        this.servicioMedicos = servicioMedicos;
        this.servicioPacientes = servicioPacientes;
        this.bitacora = bitacora;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(construirBarraSuperior(), BorderLayout.NORTH);

        tabla.setAutoCreateRowSorter(true);
        tabla.setRowHeight(22);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(lblEstado, BorderLayout.SOUTH);

        conectarEventos();
        recargarCombos();
        mostrarParametrosDe((TipoReporte) cboReporte.getSelectedItem());
    }

    // Construcción de la interfaz

    private JPanel construirBarraSuperior() {
        JPanel barra = new JPanel(new BorderLayout(6, 6));

        JPanel seleccion = new JPanel(new FlujoAjustable());
        seleccion.add(new JLabel("Reporte:"));
        seleccion.add(cboReporte);
        seleccion.add(construirPanelParametros());
        seleccion.add(btnGenerar);
        seleccion.setBorder(BorderFactory.createTitledBorder("Seleccion"));

        JPanel exportacion = new JPanel(new FlujoAjustable());
        exportacion.add(btnExportarCsv);
        exportacion.add(btnExportarTxt);
        exportacion.setBorder(BorderFactory.createTitledBorder("Exportar"));

        barra.add(seleccion, BorderLayout.CENTER);
        barra.add(exportacion, BorderLayout.EAST);
        return barra;
    }

    private JPanel construirPanelParametros() {
        cboMedico.setRenderer(new RenderizadorSimple());
        cboPaciente.setRenderer(new RenderizadorSimple());

        panelParametros.add(new JPanel(), "ninguno");
        panelParametros.add(conEtiqueta("Tipo de sangre:", cboSangre), "sangre");
        panelParametros.add(conEtiqueta("Especialidad:", cboEspecialidad), "especialidad");
        panelParametros.add(conEtiqueta("Medico:", cboMedico), "medico");
        panelParametros.add(conEtiqueta("Paciente:", cboPaciente), "paciente");
        panelParametros.add(conEtiqueta("Estado:", cboEstado), "estado");
        panelParametros.add(conEtiqueta("Fecha:", txtFecha), "fecha");

        JPanel rango = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rango.add(new JLabel("Desde:"));
        rango.add(txtDesde);
        rango.add(new JLabel("Hasta:"));
        rango.add(txtHasta);
        panelParametros.add(rango, "rango");

        return panelParametros;
    }

    private JPanel conEtiqueta(String etiqueta, Component control) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.add(new JLabel(etiqueta));
        panel.add(control);
        return panel;
    }

    // Eventos

    private void conectarEventos() {
        cboReporte.addActionListener(e -> {
            recargarCombos();
            mostrarParametrosDe((TipoReporte) cboReporte.getSelectedItem());
        });

        btnGenerar.addActionListener(e -> generar());
        btnExportarCsv.addActionListener(e -> exportar(true));
        btnExportarTxt.addActionListener(e -> exportar(false));
    }

    private void mostrarParametrosDe(TipoReporte tipo) {
        if (tipo != null) {
            tarjetas.show(panelParametros, tipo.tarjeta);
        }
    }

    // Generación

    private void generar() {
        TipoReporte tipo = (TipoReporte) cboReporte.getSelectedItem();
        if (tipo == null) {
            return;
        }

        try {
            Reporte reporte = construir(tipo);
            modeloTabla.establecerReporte(reporte);
            ajustarAnchos();

            lblEstado.setText(reporte.getTitulo() + " — "
                    + reporte.cantidadFilas() + " registros.");

            if (reporte.estaVacio()) {
                lblEstado.setText(reporte.getTitulo()
                        + " — no hay registros que cumplan el criterio.");
            }

        } catch (ExcepcionValidacion ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Parametros incompletos", JOptionPane.WARNING_MESSAGE);
        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    /** Llama al metodo del servicio que corresponde al reporte elegido. */
    private Reporte construir(TipoReporte tipo) throws ExcepcionValidacion, IOException {
        return switch (tipo) {
            case PACIENTES_COMPLETO -> servicio.pacientesCompleto();
            case PACIENTES_POR_SANGRE ->
                    servicio.pacientesPorTipoSangre((TipoSangre) cboSangre.getSelectedItem());
            case PACIENTES_MAS_CITAS -> servicio.pacientesConMasCitas();
            case PACIENTES_SIN_CITAS -> servicio.pacientesSinCitas();

            case MEDICOS_COMPLETO -> servicio.medicosCompleto();
            case MEDICOS_POR_ESPECIALIDAD -> servicio.medicosPorEspecialidad(
                    exigirSeleccion(cboEspecialidad.getSelectedItem(), "una especialidad").toString());
            case MEDICOS_MAS_CITAS -> servicio.medicosConMasCitas();
            case MEDICOS_CITAS_EN_FECHA -> servicio.medicosConCitasEnFecha(
                    ServicioPacientes.interpretarFecha(txtFecha.getTexto(), "La fecha"));

            case CITAS_COMPLETO -> servicio.citasCompleto();
            case CITAS_POR_RANGO -> servicio.citasPorRango(
                    ServicioPacientes.interpretarFecha(txtDesde.getTexto(), "La fecha inicial"),
                    ServicioPacientes.interpretarFecha(txtHasta.getTexto(), "La fecha final"));
            case CITAS_POR_MEDICO -> servicio.citasPorMedico(
                    (Medico) exigirSeleccion(cboMedico.getSelectedItem(), "un medico"));
            case CITAS_POR_PACIENTE -> servicio.citasPorPaciente(
                    (Paciente) exigirSeleccion(cboPaciente.getSelectedItem(), "un paciente"));
            case CITAS_POR_ESTADO ->
                    servicio.citasPorEstado((EstadoCita) cboEstado.getSelectedItem());
            case CITAS_POR_ESPECIALIDAD -> servicio.citasPorEspecialidad();

            case BITACORA -> servicio.bitacora();
        };
    }

    private Object exigirSeleccion(Object valor, String queCosa) throws ExcepcionValidacion {
        if (valor == null) {
            throw new ExcepcionValidacion("Debe seleccionar " + queCosa
                    + ". Si la lista esta vacia, registre primero los datos correspondientes.");
        }
        return valor;
    }

    // Exportación

    private void exportar(boolean comoCsv) {
        Reporte reporte = modeloTabla.getReporte();

        if (reporte.estaVacio()) {
            JOptionPane.showMessageDialog(this,
                    "Genere primero un reporte con datos.",
                    "Nada que exportar", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String extension = comoCsv ? "csv" : "txt";

        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Guardar reporte");
        selector.setSelectedFile(new File(nombreSugerido(reporte) + "." + extension));
        selector.setFileFilter(new FileNameExtensionFilter(
                "Archivo " + extension.toUpperCase(), extension));

        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File destino = selector.getSelectedFile();
        if (!destino.getName().toLowerCase().endsWith("." + extension)) {
            destino = new File(destino.getAbsolutePath() + "." + extension);
        }

        if (destino.exists()) {
            int respuesta = JOptionPane.showConfirmDialog(this,
                    "El archivo ya existe. Desea reemplazarlo?",
                    "Confirmar", JOptionPane.YES_NO_OPTION);
            if (respuesta != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            if (comoCsv) {
                ExportadorReportes.exportarCsv(reporte, destino);
            } else {
                ExportadorReportes.exportarTexto(reporte, destino);
            }

            bitacora.registrar(ServicioBitacora.MODULO_REPORTES, TipoOperacion.EXPORTACION,
                    "Se exporto '" + reporte.getTitulo() + "' a " + destino.getName());

            lblEstado.setText("Reporte exportado a " + destino.getAbsolutePath());
            JOptionPane.showMessageDialog(this,
                    "Reporte exportado correctamente:\n" + destino.getAbsolutePath(),
                    "Exportacion completa", JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo escribir el archivo:\n" + ex.getMessage(),
                    "Error al exportar", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Convierte el titulo del reporte en un nombre de archivo utilizable. */
    private String nombreSugerido(Reporte reporte) {
        String base = reporte.getTitulo()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        return base.isEmpty() ? "reporte" : base;
    }

    // Auxiliares

    /** Vuelve a llenar los combos. Lo usa la carga masiva al terminar. */
    public void recargar() {
        recargarCombos();
    }

    private void recargarCombos() {
        try {
            Object medicoPrevio = cboMedico.getSelectedItem();
            cboMedico.removeAllItems();
            for (Medico m : servicioMedicos.listar()) {
                cboMedico.addItem(m);
            }
            if (medicoPrevio != null) {
                cboMedico.setSelectedItem(medicoPrevio);
            }

            Object pacientePrevio = cboPaciente.getSelectedItem();
            cboPaciente.removeAllItems();
            for (Paciente p : servicioPacientes.listar()) {
                cboPaciente.addItem(p);
            }
            if (pacientePrevio != null) {
                cboPaciente.setSelectedItem(pacientePrevio);
            }

            Object especialidadPrevia = cboEspecialidad.getSelectedItem();
            cboEspecialidad.removeAllItems();
            for (String especialidad : servicioMedicos.especialidades()) {
                cboEspecialidad.addItem(especialidad);
            }
            if (especialidadPrevia != null) {
                cboEspecialidad.setSelectedItem(especialidadPrevia);
            }

        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    /** Da a cada columna un ancho razonable segun su contenido. */
    private void ajustarAnchos() {
        for (int columna = 0; columna < tabla.getColumnCount(); columna++) {
            int ancho = tabla.getColumnName(columna).length() * 9 + 20;

            for (int fila = 0; fila < Math.min(tabla.getRowCount(), 40); fila++) {
                Object valor = tabla.getValueAt(fila, columna);
                if (valor != null) {
                    ancho = Math.max(ancho, valor.toString().length() * 8 + 20);
                }
            }
            tabla.getColumnModel().getColumn(columna)
                    .setPreferredWidth(Math.min(ancho, 380));
        }
    }

    private void mostrarErrorDeArchivo(IOException ex) {
        JOptionPane.showMessageDialog(this,
                "Ocurrio un problema al acceder al archivo de datos:\n" + ex.getMessage(),
                "Error de archivo", JOptionPane.ERROR_MESSAGE);
    }

    /** Muestra medicos y pacientes con un texto legible dentro de los combos. */
    private static class RenderizadorSimple extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> lista, Object valor,
                                                      int indice, boolean seleccionado,
                                                      boolean tieneFoco) {
            super.getListCellRendererComponent(lista, valor, indice, seleccionado, tieneFoco);

            if (valor instanceof Medico medico) {
                setText(medico.getNombreCompleto() + " - " + medico.getEspecialidad());
            } else if (valor instanceof Paciente paciente) {
                setText(paciente.getIdentificacion() + " - " + paciente.getNombreCompleto());
            }
            return this;
        }
    }
}