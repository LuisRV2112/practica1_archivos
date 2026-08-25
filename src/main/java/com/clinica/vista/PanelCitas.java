package com.clinica.vista;

import com.clinica.modelo.Cita;
import com.clinica.modelo.EstadoCita;
import com.clinica.modelo.Medico;
import com.clinica.modelo.Paciente;
import com.clinica.servicio.ExcepcionValidacion;
import com.clinica.servicio.ServicioCitas;
import com.clinica.servicio.ServicioMedicos;
import com.clinica.servicio.ServicioPacientes;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interfaz del modulo de citas.
 *
 * Paciente y medico se eligen de listas desplegables, no se teclean: asi es
 * imposible escribir una referencia a alguien que no existe. La validacion del
 * servicio sigue estando, pero la interfaz evita el error de entrada.
 */
public class PanelCitas extends JPanel {

    private final ServicioCitas servicio;
    private final ServicioMedicos servicioMedicos;
    private final ServicioPacientes servicioPacientes;

    // --- Formulario ---
    private final JComboBox<Paciente> cboPaciente = new JComboBox<>();
    private final JComboBox<Medico> cboMedico = new JComboBox<>();
    private final CampoFecha txtFecha = new CampoFecha();
    private final CampoHora txtHora = new CampoHora(java.time.LocalTime.of(9, 0));
    private final JTextField txtMotivo = new JTextField(18);
    private final JTextArea txtObservaciones = new JTextArea(4, 18);

    private final JButton btnProgramar = new JButton("Programar cita");
    private final JButton btnLimpiar = new JButton("Limpiar");
    private final JButton btnGuardarCambios = new JButton("Guardar motivo/observaciones");
    private final JButton btnAtendida = new JButton("Marcar atendida");
    private final JButton btnCancelar = new JButton("Cancelar cita");
    private final JButton btnEliminar = new JButton("Eliminar cita");

    // --- Filtros ---
    private final CampoFecha txtFiltroFecha = new CampoFecha();
    private final JComboBox<String> cboFiltroEstado =
            new JComboBox<>(new String[]{"Todos", "Programada", "Atendida", "Cancelada"});
    private final JComboBox<Object> cboFiltroMedico = new JComboBox<>();
    private final JComboBox<Object> cboFiltroPaciente = new JComboBox<>();
    private final JButton btnLimpiarFiltros = new JButton("Quitar filtros");

    // --- Tabla ---
    private final ModeloTablaCitas modeloTabla = new ModeloTablaCitas();
    private final JTable tabla = new JTable(modeloTabla);
    private final JLabel lblEstado = new JLabel(" ");

    /** Cita seleccionada, o null si no hay ninguna. */
    private UUID idSeleccionada;

    public PanelCitas(ServicioCitas servicio, ServicioMedicos servicioMedicos,
                      ServicioPacientes servicioPacientes) {
        this.servicio = servicio;
        this.servicioMedicos = servicioMedicos;
        this.servicioPacientes = servicioPacientes;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(construirFormulario(), BorderLayout.WEST);
        add(construirZonaTabla(), BorderLayout.CENTER);

        conectarEventos();
        recargarCombos();
        refrescar();
    }

    // =======================================================================
    // CONSTRUCCION DE LA INTERFAZ
    // =======================================================================

    private JPanel construirFormulario() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Datos de la cita"));

        // Los combos muestran nombres legibles en vez del toString del objeto.
        cboPaciente.setRenderer(new RenderizadorSimple());
        cboMedico.setRenderer(new RenderizadorSimple());

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int fila = 0;
        agregarCampo(panel, g, fila++, "Paciente *", cboPaciente);
        agregarCampo(panel, g, fila++, "Medico *", cboMedico);
        agregarCampo(panel, g, fila++, "Fecha *", txtFecha);
        agregarCampo(panel, g, fila++, "Hora *", txtHora);
        agregarCampo(panel, g, fila++, "Motivo *", txtMotivo);

        txtObservaciones.setLineWrap(true);
        txtObservaciones.setWrapStyleWord(true);
        agregarCampo(panel, g, fila++, "Observaciones",
                new JScrollPane(txtObservaciones));

        JPanel botonesAlta = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        botonesAlta.add(btnProgramar);
        botonesAlta.add(btnLimpiar);

        g.gridx = 0;
        g.gridy = fila++;
        g.gridwidth = 2;
        panel.add(botonesAlta, g);

        g.gridy = fila++;
        panel.add(new JLabel("<html><hr><b>Sobre la cita seleccionada</b></html>"), g);

        g.gridy = fila++;
        panel.add(btnGuardarCambios, g);

        JPanel botonesEstado = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        botonesEstado.add(btnAtendida);
        botonesEstado.add(btnCancelar);

        g.gridy = fila++;
        panel.add(botonesEstado, g);

        g.gridy = fila++;
        panel.add(btnEliminar, g);

        g.gridy = fila++;
        panel.add(new JLabel("<html><i>Las consultas duran "
                + ServicioCitas.DURACION_MINUTOS + " minutos.</i></html>"), g);

        g.gridy = fila;
        g.weighty = 1;
        panel.add(Box.createVerticalGlue(), g);

        return panel;
    }

    private void agregarCampo(JPanel panel, GridBagConstraints g, int fila,
                              String etiqueta, Component campo) {
        g.gridx = 0;
        g.gridy = fila;
        g.gridwidth = 1;
        g.weightx = 0;
        panel.add(new JLabel(etiqueta), g);

        g.gridx = 1;
        g.weightx = 1;
        panel.add(campo, g);
    }

    private JPanel construirZonaTabla() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));

        JPanel filtros = new JPanel(new FlujoAjustable());
        filtros.setBorder(BorderFactory.createTitledBorder("Filtros"));
        filtros.add(new JLabel("Fecha:"));
        filtros.add(txtFiltroFecha);
        filtros.add(new JLabel("Estado:"));
        filtros.add(cboFiltroEstado);
        filtros.add(new JLabel("Medico:"));
        filtros.add(cboFiltroMedico);
        filtros.add(new JLabel("Paciente:"));
        filtros.add(cboFiltroPaciente);
        filtros.add(btnLimpiarFiltros);

        cboFiltroMedico.setRenderer(new RenderizadorSimple());
        cboFiltroPaciente.setRenderer(new RenderizadorSimple());

        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setAutoCreateRowSorter(true);
        tabla.setRowHeight(22);
        tabla.getTableHeader().setReorderingAllowed(false);

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setPreferredSize(new Dimension(760, 400));

        panel.add(filtros, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(lblEstado, BorderLayout.SOUTH);

        return panel;
    }

    // =======================================================================
    // EVENTOS
    // =======================================================================

    private void conectarEventos() {
        btnProgramar.addActionListener(e -> programar());
        btnLimpiar.addActionListener(e -> limpiarFormulario());
        btnGuardarCambios.addActionListener(e -> guardarMotivoYObservaciones());
        btnAtendida.addActionListener(e -> marcarAtendida());
        btnCancelar.addActionListener(e -> cancelarCita());
        btnEliminar.addActionListener(e -> eliminarCita());

        txtFiltroFecha.alPresionarEnter(e -> refrescar());
        cboFiltroEstado.addActionListener(e -> refrescar());
        cboFiltroMedico.addActionListener(e -> refrescar());
        cboFiltroPaciente.addActionListener(e -> refrescar());
        btnLimpiarFiltros.addActionListener(e -> limpiarFiltros());

        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarSeleccionEnFormulario();
            }
        });
    }

    // =======================================================================
    // ACCIONES
    // =======================================================================

    private void programar() {
        try {
            Paciente paciente = (Paciente) cboPaciente.getSelectedItem();
            Medico medico = (Medico) cboMedico.getSelectedItem();

            if (paciente == null) {
                throw new ExcepcionValidacion("Debe seleccionar un paciente. "
                        + "Si la lista esta vacia, registre primero un paciente.");
            }
            if (medico == null) {
                throw new ExcepcionValidacion("Debe seleccionar un medico activo. "
                        + "Si la lista esta vacia, registre o active un medico.");
            }

            LocalDate fecha = ServicioPacientes.interpretarFecha(
                    txtFecha.getTexto(), "La fecha de la cita");
            LocalTime hora = ServicioMedicos.interpretarHora(
                    txtHora.getTexto(), "La hora de la cita");

            Cita cita = new Cita(null, paciente.getIdentificacion(), medico.getId(),
                    fecha, hora, txtMotivo.getText(), null, txtObservaciones.getText());

            servicio.programar(cita);
            informar("Cita programada correctamente.");

            limpiarFormulario();
            refrescar();

        } catch (ExcepcionValidacion ex) {
            advertir(ex);
        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    private void guardarMotivoYObservaciones() {
        if (!haySeleccion()) {
            return;
        }
        try {
            servicio.modificarMotivoYObservaciones(idSeleccionada,
                    txtMotivo.getText(), txtObservaciones.getText());
            informar("Cita actualizada.");
            refrescar();

        } catch (ExcepcionValidacion ex) {
            advertir(ex);
        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    private void marcarAtendida() {
        if (!haySeleccion()) {
            return;
        }
        try {
            servicio.marcarAtendida(idSeleccionada);
            informar("Cita marcada como atendida.");
            refrescar();

        } catch (ExcepcionValidacion ex) {
            advertir(ex);
        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    private void cancelarCita() {
        if (!haySeleccion()) {
            return;
        }
        try {
            servicio.cancelar(idSeleccionada);
            informar("Cita cancelada.");
            refrescar();

        } catch (ExcepcionValidacion ex) {
            advertir(ex);
        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    private void eliminarCita() {
        if (!haySeleccion()) {
            return;
        }

        int respuesta = JOptionPane.showConfirmDialog(this,
                "Eliminar definitivamente esta cita?",
                "Confirmar eliminacion", JOptionPane.YES_NO_OPTION);

        if (respuesta != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            servicio.eliminar(idSeleccionada);
            informar("Cita eliminada.");
            limpiarFormulario();
            refrescar();

        } catch (ExcepcionValidacion ex) {
            advertir(ex);
        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    // =======================================================================
    // CARGA DE DATOS
    // =======================================================================

    /**
     * Vuelve a llenar los combos de seleccion y de filtro.
     *
     * En el combo de programacion solo aparecen medicos ACTIVOS: un medico
     * inactivo no puede recibir citas, asi que ni se ofrece. En el de filtro
     * aparecen todos, porque puede haber citas viejas de un medico que despues
     * se desactivo.
     */
    private void recargarCombos() {
        try {
            Object seleccionPaciente = cboPaciente.getSelectedItem();
            Object seleccionMedico = cboMedico.getSelectedItem();

            cboPaciente.removeAllItems();
            for (Paciente p : servicioPacientes.listar()) {
                cboPaciente.addItem(p);
            }
            if (seleccionPaciente != null) {
                cboPaciente.setSelectedItem(seleccionPaciente);
            }

            cboMedico.removeAllItems();
            for (Medico m : servicioMedicos.listarPorEstado(true)) {
                cboMedico.addItem(m);
            }
            if (seleccionMedico != null) {
                cboMedico.setSelectedItem(seleccionMedico);
            }

            recargarComboFiltro(cboFiltroMedico, servicioMedicos.listar());
            recargarComboFiltro(cboFiltroPaciente, servicioPacientes.listar());

        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    /**
     * Llena un combo de filtro con "Todos" mas la lista dada, sin disparar el
     * evento de refresco mientras se reconstruye.
     */
    private void recargarComboFiltro(JComboBox<Object> combo, List<?> elementos) {
        var oyentes = combo.getActionListeners();
        for (var oyente : oyentes) {
            combo.removeActionListener(oyente);
        }

        Object seleccionPrevia = combo.getSelectedItem();

        combo.removeAllItems();
        combo.addItem("Todos");
        for (Object elemento : elementos) {
            combo.addItem(elemento);
        }

        if (seleccionPrevia != null) {
            combo.setSelectedItem(seleccionPrevia);
        }
        if (combo.getSelectedIndex() < 0) {
            combo.setSelectedIndex(0);
        }

        for (var oyente : oyentes) {
            combo.addActionListener(oyente);
        }
    }

    /**
     * Vuelve a consultar el archivo y a llenar los combos. Lo usa la carga
     * masiva: si se cargaron medicos o pacientes nuevos, las listas de esta
     * pantalla tienen que enterarse.
     */
    public void recargar() {
        recargarCombos();
        refrescar();
    }

    /** Vuelve a consultar el archivo aplicando los filtros activos. */
    private void refrescar() {
        try {
            List<Cita> resultado = servicio.listar();

            // Filtro por fecha (solo si el texto es una fecha valida)
            String textoFecha = txtFiltroFecha.getTexto();
            if (!textoFecha.isEmpty()) {
                try {
                    LocalDate fecha = ServicioPacientes.interpretarFecha(textoFecha, "La fecha");
                    resultado.removeIf(c -> !fecha.equals(c.getFecha()));
                } catch (ExcepcionValidacion ex) {
                    advertir(ex);
                    txtFiltroFecha.limpiar();
                }
            }

            // Filtro por estado
            int indiceEstado = cboFiltroEstado.getSelectedIndex();
            if (indiceEstado > 0) {
                EstadoCita estado = EstadoCita.values()[indiceEstado - 1];
                resultado.removeIf(c -> c.getEstado() != estado);
            }

            // Filtro por medico
            Object filtroMedico = cboFiltroMedico.getSelectedItem();
            if (filtroMedico instanceof Medico medico) {
                resultado.removeIf(c -> !medico.getId().equals(c.getIdMedico()));
            }

            // Filtro por paciente
            Object filtroPaciente = cboFiltroPaciente.getSelectedItem();
            if (filtroPaciente instanceof Paciente paciente) {
                resultado.removeIf(c -> !paciente.getIdentificacion()
                        .equals(c.getIdentificacionPaciente()));
            }

            modeloTabla.establecerDatos(resultado, mapaPacientes(), mapaMedicos());

            lblEstado.setText("Mostrando " + resultado.size()
                    + " de " + servicio.cantidad() + " citas registradas.");

        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    /**
     * Arma el diccionario identificacion -> paciente de una sola pasada.
     * La tabla lo usa para resolver nombres sin volver a leer el archivo en
     * cada celda.
     */
    private Map<String, Paciente> mapaPacientes() throws IOException {
        Map<String, Paciente> mapa = new HashMap<>();
        for (Paciente p : servicioPacientes.listar()) {
            mapa.put(p.getIdentificacion(), p);
        }
        return mapa;
    }

    private Map<UUID, Medico> mapaMedicos() throws IOException {
        Map<UUID, Medico> mapa = new HashMap<>();
        for (Medico m : servicioMedicos.listar()) {
            mapa.put(m.getId(), m);
        }
        return mapa;
    }

    // =======================================================================
    // FORMULARIO
    // =======================================================================

    private void cargarSeleccionEnFormulario() {
        Cita cita = citaSeleccionada();
        if (cita == null) {
            return;
        }

        idSeleccionada = cita.getId();
        txtMotivo.setText(cita.getMotivo());
        txtObservaciones.setText(cita.getObservaciones());
        txtFecha.setFecha(cita.getFecha());
        txtHora.setHora(cita.getHoraInicio());

        informar("Cita seleccionada: " + cita.getEstado() + ".");
    }

    private void limpiarFormulario() {
        idSeleccionada = null;

        txtFecha.limpiar();
        txtHora.setHora(java.time.LocalTime.of(9, 0));
        txtMotivo.setText("");
        txtObservaciones.setText("");

        tabla.clearSelection();
        recargarCombos();
    }

    private void limpiarFiltros() {
        txtFiltroFecha.limpiar();
        cboFiltroEstado.setSelectedIndex(0);
        if (cboFiltroMedico.getItemCount() > 0) {
            cboFiltroMedico.setSelectedIndex(0);
        }
        if (cboFiltroPaciente.getItemCount() > 0) {
            cboFiltroPaciente.setSelectedIndex(0);
        }
        refrescar();
    }

    private Cita citaSeleccionada() {
        int filaVista = tabla.getSelectedRow();
        if (filaVista < 0) {
            return null;
        }
        // La tabla se puede ordenar por columna: hay que convertir el indice.
        return modeloTabla.obtenerEn(tabla.convertRowIndexToModel(filaVista));
    }

    private boolean haySeleccion() {
        if (idSeleccionada == null) {
            JOptionPane.showMessageDialog(this,
                    "Seleccione una cita en la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        return true;
    }

    private void informar(String mensaje) {
        lblEstado.setText(mensaje);
    }

    private void advertir(ExcepcionValidacion ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(),
                "No se pudo completar", JOptionPane.WARNING_MESSAGE);
    }

    private void mostrarErrorDeArchivo(IOException ex) {
        JOptionPane.showMessageDialog(this,
                "Ocurrio un problema al acceder al archivo de datos:\n" + ex.getMessage(),
                "Error de archivo", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Muestra medicos y pacientes con un texto legible dentro de los combos, en
     * lugar del toString completo del objeto.
     */
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