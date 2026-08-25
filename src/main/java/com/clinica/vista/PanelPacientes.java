package com.clinica.vista;

import com.clinica.modelo.Paciente;
import com.clinica.modelo.Sexo;
import com.clinica.modelo.TipoSangre;
import com.clinica.servicio.ExcepcionValidacion;
import com.clinica.servicio.ServicioPacientes;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
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
import java.time.LocalDate;
import java.util.List;

/**
 * Interfaz del modulo de pacientes.
 *
 * Se parece mucho a PanelMedicos, con una diferencia importante: el numero de
 * identificacion es la llave del registro, asi que al editar un paciente ese
 * campo se bloquea. Cambiarlo equivaldria a crear otro paciente distinto.
 */
public class PanelPacientes extends JPanel {

    private final ServicioPacientes servicio;

    // --- Formulario ---
    private final JTextField txtIdentificacion = new JTextField(18);
    private final JTextField txtNombres = new JTextField(18);
    private final JTextField txtApellidos = new JTextField(18);
    private final JTextField txtNacimiento = new JTextField(18);
    private final JComboBox<Sexo> cboSexo = new JComboBox<>(Sexo.values());
    private final JTextField txtTelefono = new JTextField(18);
    private final JTextField txtCorreo = new JTextField(18);
    private final JComboBox<TipoSangre> cboTipoSangre = new JComboBox<>(TipoSangre.values());

    private final JButton btnGuardar = new JButton("Guardar");
    private final JButton btnLimpiar = new JButton("Limpiar");
    private final JButton btnBaja = new JButton("Dar de baja / Reactivar");

    // --- Filtros ---
    private final JTextField txtBuscar = new JTextField(20);
    private final JComboBox<String> cboFiltroSangre = new JComboBox<>();
    private final JComboBox<String> cboFiltroEstado =
            new JComboBox<>(new String[]{"Todos", "Activos", "De baja"});

    // --- Tabla ---
    private final ModeloTablaPacientes modeloTabla = new ModeloTablaPacientes();
    private final JTable tabla = new JTable(modeloTabla);
    private final JLabel lblEstado = new JLabel(" ");

    /**
     * Identificacion del paciente que se esta editando, o null si el formulario
     * esta en modo alta. Decide si Guardar inserta o actualiza.
     */
    private String idEnEdicion;

    public PanelPacientes(ServicioPacientes servicio) {
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
        panel.setBorder(BorderFactory.createTitledBorder("Datos del paciente"));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int fila = 0;
        agregarCampo(panel, g, fila++, "Identificacion *", txtIdentificacion);
        agregarCampo(panel, g, fila++, "Nombres *", txtNombres);
        agregarCampo(panel, g, fila++, "Apellidos *", txtApellidos);
        agregarCampo(panel, g, fila++, "Nacimiento * (dd/MM/aaaa)", txtNacimiento);
        agregarCampo(panel, g, fila++, "Sexo *", cboSexo);
        agregarCampo(panel, g, fila++, "Telefono", txtTelefono);
        agregarCampo(panel, g, fila++, "Correo", txtCorreo);
        agregarCampo(panel, g, fila++, "Tipo de sangre *", cboTipoSangre);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        botones.add(btnGuardar);
        botones.add(btnLimpiar);

        g.gridx = 0;
        g.gridy = fila++;
        g.gridwidth = 2;
        panel.add(botones, g);

        g.gridy = fila++;
        panel.add(btnBaja, g);

        g.gridy = fila++;
        panel.add(new JLabel("<html><i>Los campos con * son obligatorios.</i></html>"), g);

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

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtros.setBorder(BorderFactory.createTitledBorder("Busqueda y filtros"));
        filtros.add(new JLabel("Buscar (identificacion, nombre o apellido):"));
        filtros.add(txtBuscar);
        filtros.add(new JLabel("Tipo de sangre:"));
        filtros.add(cboFiltroSangre);
        filtros.add(new JLabel("Estado:"));
        filtros.add(cboFiltroEstado);

        cboFiltroSangre.addItem("Todos");
        for (TipoSangre tipo : TipoSangre.values()) {
            cboFiltroSangre.addItem(tipo.getEtiqueta());
        }

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
        btnLimpiar.addActionListener(e -> limpiarFormulario());
        btnBaja.addActionListener(e -> alternarEstadoSeleccionado());

        txtBuscar.addActionListener(e -> refrescar());
        cboFiltroSangre.addActionListener(e -> refrescar());
        cboFiltroEstado.addActionListener(e -> refrescar());

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
            Paciente paciente = leerFormulario();

            if (idEnEdicion == null) {
                servicio.registrar(paciente);
                informar("Paciente registrado correctamente.");
            } else {
                paciente.setIdentificacion(idEnEdicion); // la llave no cambia
                servicio.modificar(paciente);
                informar("Paciente actualizado correctamente.");
            }

            limpiarFormulario();
            refrescar();

        } catch (ExcepcionValidacion ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    /**
     * Da de baja o reactiva al paciente seleccionado.
     *
     * No hay eliminacion fisica a proposito: el expediente se conserva para no
     * dejar huerfanas las citas que lo referencian.
     */
    private void alternarEstadoSeleccionado() {
        Paciente seleccionado = pacienteSeleccionado();
        if (seleccionado == null) {
            JOptionPane.showMessageDialog(this,
                    "Seleccione un paciente en la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean darDeBaja = seleccionado.isActivo();

        int respuesta = JOptionPane.showConfirmDialog(this,
                (darDeBaja ? "Dar de baja a " : "Reactivar a ")
                        + seleccionado.getNombreCompleto() + "?",
                "Confirmar", JOptionPane.YES_NO_OPTION);

        if (respuesta != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            if (darDeBaja) {
                servicio.darDeBaja(seleccionado.getIdentificacion());
                informar("Paciente dado de baja. Su expediente se conserva.");
            } else {
                servicio.reactivar(seleccionado.getIdentificacion());
                informar("Paciente reactivado.");
            }
            limpiarFormulario();
            refrescar();

        } catch (ExcepcionValidacion ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Aviso", JOptionPane.WARNING_MESSAGE);
        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    private void refrescar() {
        try {
            List<Paciente> resultado = servicio.buscar(txtBuscar.getText());

            Object filtroSangre = cboFiltroSangre.getSelectedItem();
            if (filtroSangre != null && !"Todos".equals(filtroSangre)) {
                resultado.removeIf(p -> p.getTipoSangre() == null
                        || !p.getTipoSangre().getEtiqueta().equals(filtroSangre.toString()));
            }

            int filtroEstado = cboFiltroEstado.getSelectedIndex();
            if (filtroEstado == 1) {
                resultado.removeIf(p -> !p.isActivo());
            } else if (filtroEstado == 2) {
                resultado.removeIf(Paciente::isActivo);
            }

            modeloTabla.establecerDatos(resultado);

            lblEstado.setText("Mostrando " + resultado.size()
                    + " de " + servicio.cantidad() + " pacientes registrados.");

        } catch (IOException ex) {
            mostrarErrorDeArchivo(ex);
        }
    }

    // =======================================================================
    // FORMULARIO
    // =======================================================================

    private Paciente leerFormulario() throws ExcepcionValidacion {
        LocalDate nacimiento = ServicioPacientes.interpretarFecha(
                txtNacimiento.getText(), "La fecha de nacimiento");

        return new Paciente(
                txtIdentificacion.getText(),
                txtNombres.getText(),
                txtApellidos.getText(),
                nacimiento,
                (Sexo) cboSexo.getSelectedItem(),
                txtTelefono.getText(),
                txtCorreo.getText(),
                (TipoSangre) cboTipoSangre.getSelectedItem());
    }

    private void cargarSeleccionEnFormulario() {
        Paciente p = pacienteSeleccionado();
        if (p == null) {
            return;
        }

        idEnEdicion = p.getIdentificacion();

        txtIdentificacion.setText(p.getIdentificacion());
        txtIdentificacion.setEditable(false); // la llave no se puede cambiar
        txtNombres.setText(p.getNombres());
        txtApellidos.setText(p.getApellidos());
        txtNacimiento.setText(ServicioPacientes.formatearFecha(p.getFechaNacimiento()));
        cboSexo.setSelectedItem(p.getSexo());
        txtTelefono.setText(p.getTelefono());
        txtCorreo.setText(p.getCorreo());
        cboTipoSangre.setSelectedItem(p.getTipoSangre());

        btnGuardar.setText("Guardar cambios");
    }

    private void limpiarFormulario() {
        idEnEdicion = null;

        txtIdentificacion.setText("");
        txtIdentificacion.setEditable(true);
        txtNombres.setText("");
        txtApellidos.setText("");
        txtNacimiento.setText("");
        cboSexo.setSelectedIndex(0);
        txtTelefono.setText("");
        txtCorreo.setText("");
        cboTipoSangre.setSelectedIndex(0);

        btnGuardar.setText("Guardar");
        tabla.clearSelection();
        txtIdentificacion.requestFocusInWindow();
    }

    private Paciente pacienteSeleccionado() {
        int filaVista = tabla.getSelectedRow();
        if (filaVista < 0) {
            return null;
        }
        // La tabla se puede ordenar por columna, asi que el indice visible no
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