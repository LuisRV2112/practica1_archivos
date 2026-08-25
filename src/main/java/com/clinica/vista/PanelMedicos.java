package com.clinica.vista;

import com.clinica.modelo.Medico;
import com.clinica.servicio.ExcepcionValidacion;
import com.clinica.servicio.ServicioMedicos;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Interfaz del modulo de medicos: formulario de captura, filtros y tabla.
 *
 * Este panel no sabe nada de RandomAccessFile: todo lo pide a ServicioMedicos.
 * Su unica responsabilidad es mostrar datos y recoger lo que el usuario teclea.
 */
public class PanelMedicos extends JPanel {

    private final ServicioMedicos servicio;

    // --- Formulario ---
    private final JTextField txtNombres = new JTextField(18);
    private final JTextField txtApellidos = new JTextField(18);
    private final JTextField txtEspecialidad = new JTextField(18);
    private final JTextField txtTelefono = new JTextField(18);
    private final JTextField txtCorreo = new JTextField(18);
    private final CampoHora txtHoraInicio = new CampoHora(java.time.LocalTime.of(8, 0));
    private final CampoHora txtHoraFin = new CampoHora(java.time.LocalTime.of(16, 0));
    private final JCheckBox chkActivo = new JCheckBox("Medico activo", true);

    private final JButton btnGuardar = new JButton("Guardar");
    private final JButton btnNuevo = new JButton("Limpiar");
    private final JButton btnCambiarEstado = new JButton("Activar / Desactivar");

    // --- Filtros ---
    private final JTextField txtBuscar = new JTextField(20);
    private final JComboBox<String> cboEstado =
            new JComboBox<>(new String[]{"Todos", "Activos", "Inactivos"});
    private final JComboBox<String> cboEspecialidad = new JComboBox<>();

    // --- Tabla ---
    private final ModeloTablaMedicos modeloTabla = new ModeloTablaMedicos();
    private final JTable tabla = new JTable(modeloTabla);
    private final JLabel lblEstado = new JLabel(" ");

    /**
     * Id del medico que se esta editando. Si es null, el formulario esta en
     * modo "alta"; si tiene valor, en modo "modificacion". Es lo que decide si
     * Guardar inserta o actualiza.
     */
    private UUID idEnEdicion;

    public PanelMedicos(ServicioMedicos servicio) {
        this.servicio = servicio;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(construirFormulario(), BorderLayout.WEST);
        add(construirZonaTabla(), BorderLayout.CENTER);

        conectarEventos();
        refrescar();
    }

    // =======================================================================
    // CONSTRUCCION DE LA INTERFAZ
    // =======================================================================

    private JPanel construirFormulario() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Datos del medico"));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int fila = 0;
        agregarCampo(panel, g, fila++, "Nombres *", txtNombres);
        agregarCampo(panel, g, fila++, "Apellidos *", txtApellidos);
        agregarCampo(panel, g, fila++, "Especialidad *", txtEspecialidad);
        agregarCampo(panel, g, fila++, "Telefono", txtTelefono);
        agregarCampo(panel, g, fila++, "Correo", txtCorreo);
        agregarCampo(panel, g, fila++, "Hora inicio *", txtHoraInicio);
        agregarCampo(panel, g, fila++, "Hora fin *", txtHoraFin);

        g.gridx = 0;
        g.gridy = fila++;
        g.gridwidth = 2;
        panel.add(chkActivo, g);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        botones.add(btnGuardar);
        botones.add(btnNuevo);

        g.gridy = fila++;
        panel.add(botones, g);

        g.gridy = fila++;
        panel.add(btnCambiarEstado, g);

        g.gridy = fila++;
        panel.add(new JLabel("<html><i>Los campos con * son obligatorios.</i></html>"), g);

        // Empuja todo hacia arriba para que el formulario no quede centrado.
        g.gridy = fila;
        g.weighty = 1;
        panel.add(Box.createVerticalGlue(), g);

        return panel;
    }

    private void agregarCampo(JPanel panel, GridBagConstraints g, int fila,
                              String etiqueta, java.awt.Component campo) {
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
        filtros.setBorder(BorderFactory.createTitledBorder("Busqueda y filtros"));
        filtros.add(new JLabel("Buscar:"));
        filtros.add(txtBuscar);
        filtros.add(new JLabel("Estado:"));
        filtros.add(cboEstado);
        filtros.add(new JLabel("Especialidad:"));
        filtros.add(cboEspecialidad);

        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setAutoCreateRowSorter(true);
        tabla.setRowHeight(22);
        tabla.getTableHeader().setReorderingAllowed(false);

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setPreferredSize(new Dimension(700, 400));

        panel.add(filtros, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(lblEstado, BorderLayout.SOUTH);

        return panel;
    }

    // =======================================================================
    // EVENTOS
    // =======================================================================

    private void conectarEventos() {
        btnGuardar.addActionListener(e -> guardar());
        btnNuevo.addActionListener(e -> limpiarFormulario());
        btnCambiarEstado.addActionListener(e -> cambiarEstadoSeleccionado());

        // La busqueda se dispara con Enter, no en cada tecla: evita releer el
        // archivo entero en cada pulsacion.
        txtBuscar.addActionListener(e -> refrescar());

        cboEstado.addActionListener(e -> refrescar());
        cboEspecialidad.addActionListener(e -> refrescar());

        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                cargarSeleccionEnFormulario();
            }
        });
    }

    // =======================================================================
    // ACCIONES
    // =======================================================================

    private void guardar() {
        try {
            Medico medico = leerFormulario();

            if (idEnEdicion == null) {
                servicio.registrar(medico);
                informar("Medico registrado correctamente.");
            } else {
                medico.setId(idEnEdicion);
                servicio.modificar(medico);
                informar("Medico actualizado correctamente.");
            }

            limpiarFormulario();
            refrescar();

        } catch (ExcepcionValidacion ex) {
            // Error del usuario: se le explica y se le deja corregir.
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
        } catch (IOException ex) {
            // Error del archivo: es un problema tecnico, no del usuario.
            mostrarErrorDeArchivo(ex);
        }
    }

    private void cambiarEstadoSeleccionado() {
        Medico seleccionado = medicoSeleccionado();
        if (seleccionado == null) {
            JOptionPane.showMessageDialog(this,
                    "Seleccione un medico en la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            boolean nuevoEstado = !seleccionado.isActivo();
            servicio.cambiarEstado(seleccionado.getId(), nuevoEstado);
            informar("El medico quedo " + (nuevoEstado ? "activo." : "inactivo."));
            refrescar();

        } catch (ExcepcionValidacion ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Aviso", JOptionPane.WARNING_MESSAGE);
        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    /** Vuelve a consultar el archivo aplicando los filtros activos. */
    private void refrescar() {
        try {
            List<Medico> resultado = servicio.buscar(txtBuscar.getText());

            // Filtro por estado
            int estado = cboEstado.getSelectedIndex();
            if (estado == 1) {
                resultado.removeIf(m -> !m.isActivo());
            } else if (estado == 2) {
                resultado.removeIf(Medico::isActivo);
            }

            // Filtro por especialidad
            Object especialidad = cboEspecialidad.getSelectedItem();
            if (especialidad != null && !"Todas".equals(especialidad)) {
                resultado.removeIf(m -> !m.getEspecialidad()
                        .equalsIgnoreCase(especialidad.toString()));
            }

            modeloTabla.establecerDatos(resultado);
            actualizarComboEspecialidades();

            lblEstado.setText("Mostrando " + resultado.size()
                    + " de " + servicio.cantidad() + " medicos registrados.");

        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    /**
     * Rellena el combo de especialidades con las que existan en el archivo.
     * Se desconecta el evento mientras se reconstruye para no disparar un
     * refresco recursivo.
     */
    private void actualizarComboEspecialidades() throws IOException {
        Object seleccionPrevia = cboEspecialidad.getSelectedItem();

        var oyentes = cboEspecialidad.getActionListeners();
        for (var oyente : oyentes) {
            cboEspecialidad.removeActionListener(oyente);
        }

        cboEspecialidad.removeAllItems();
        cboEspecialidad.addItem("Todas");
        for (String especialidad : servicio.especialidades()) {
            cboEspecialidad.addItem(especialidad);
        }

        if (seleccionPrevia != null) {
            cboEspecialidad.setSelectedItem(seleccionPrevia);
        }
        if (cboEspecialidad.getSelectedIndex() < 0) {
            cboEspecialidad.setSelectedIndex(0);
        }

        for (var oyente : oyentes) {
            cboEspecialidad.addActionListener(oyente);
        }
    }

    // =======================================================================
    // FORMULARIO
    // =======================================================================

    private Medico leerFormulario() throws ExcepcionValidacion {
        // El componente no permite construir una hora invalida, pero se sigue
        // pasando por el servicio para que la validacion viva en un solo lugar.
        LocalTime inicio = ServicioMedicos.interpretarHora(
                txtHoraInicio.getTexto(), "La hora de inicio");
        LocalTime fin = ServicioMedicos.interpretarHora(
                txtHoraFin.getTexto(), "La hora de finalizacion");

        return new Medico(
                null,
                txtNombres.getText(),
                txtApellidos.getText(),
                txtEspecialidad.getText(),
                txtTelefono.getText(),
                txtCorreo.getText(),
                inicio,
                fin,
                chkActivo.isSelected());
    }

    private void cargarSeleccionEnFormulario() {
        Medico m = medicoSeleccionado();
        if (m == null) {
            return;
        }

        idEnEdicion = m.getId();
        txtNombres.setText(m.getNombres());
        txtApellidos.setText(m.getApellidos());
        txtEspecialidad.setText(m.getEspecialidad());
        txtTelefono.setText(m.getTelefono());
        txtCorreo.setText(m.getCorreo());
        txtHoraInicio.setHora(m.getHoraInicio());
        txtHoraFin.setHora(m.getHoraFin());
        chkActivo.setSelected(m.isActivo());

        btnGuardar.setText("Guardar cambios");
    }

    private void limpiarFormulario() {
        idEnEdicion = null;

        txtNombres.setText("");
        txtApellidos.setText("");
        txtEspecialidad.setText("");
        txtTelefono.setText("");
        txtCorreo.setText("");
        txtHoraInicio.setHora(java.time.LocalTime.of(8, 0));
        txtHoraFin.setHora(java.time.LocalTime.of(16, 0));
        chkActivo.setSelected(true);

        btnGuardar.setText("Guardar");
        tabla.clearSelection();
        txtNombres.requestFocusInWindow();
    }

    /** Medico correspondiente a la fila seleccionada, o null si no hay ninguna. */
    private Medico medicoSeleccionado() {
        int filaVista = tabla.getSelectedRow();
        if (filaVista < 0) {
            return null;
        }
        // La tabla permite ordenar por columna, asi que el indice visible no
        // coincide con el del modelo: hay que convertirlo.
        return modeloTabla.obtenerEn(tabla.convertRowIndexToModel(filaVista));
    }

    private void informar(String mensaje) {
        lblEstado.setText(mensaje);
    }

    private void mostrarErrorDeArchivo(IOException ex) {
        JOptionPane.showMessageDialog(this,
                "Ocurrio un problema al acceder al archivo de datos:\n" + ex.getMessage(),
                "Error de archivo", JOptionPane.ERROR_MESSAGE);
    }
}