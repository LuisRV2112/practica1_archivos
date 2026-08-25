package com.clinica.vista;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Calendario emergente en Swing puro (sin dependencias externas). Devuelve la
 * fecha elegida o null si se cierra sin seleccionar.
 */
public class SelectorFecha extends JDialog {

    private static final String[] MESES = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    private static final String[] DIAS = {"Do", "Lu", "Ma", "Mi", "Ju", "Vi", "Sa"};

    private final JComboBox<String> cboMes = new JComboBox<>(MESES);
    private final JSpinner spnAnio;
    private final JPanel panelDias = new JPanel(new GridLayout(0, 7, 2, 2));

    private LocalDate seleccionada;

    private SelectorFecha(Component padre, LocalDate inicial) {
        super(javax.swing.SwingUtilities.getWindowAncestor(padre),
                "Seleccionar fecha", ModalityType.APPLICATION_MODAL);

        LocalDate base = (inicial == null) ? LocalDate.now() : inicial;

        cboMes.setSelectedIndex(base.getMonthValue() - 1);
        spnAnio = new JSpinner(new SpinnerNumberModel(base.getYear(), 1900, 2100, 1));
        // Sin separador de miles: un ano no se escribe "2.026".
        spnAnio.setEditor(new JSpinner.NumberEditor(spnAnio, "#"));

        setLayout(new BorderLayout(6, 6));
        add(construirEncabezado(), BorderLayout.NORTH);

        panelDias.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        add(panelDias, BorderLayout.CENTER);
        add(construirPie(base), BorderLayout.SOUTH);

        cboMes.addActionListener(e -> dibujarDias());
        spnAnio.addChangeListener(e -> dibujarDias());

        dibujarDias();

        pack();
        setResizable(false);
        setLocationRelativeTo(padre);
    }

    /**
     * Abre el calendario y devuelve la fecha elegida.
     *
     * @param inicial fecha en la que se posiciona al abrir, o null para hoy
     * @return la fecha seleccionada, o null si se cerro sin elegir
     */
    public static LocalDate mostrar(Component padre, LocalDate inicial) {
        SelectorFecha dialogo = new SelectorFecha(padre, inicial);
        dialogo.setVisible(true);
        return dialogo.seleccionada;
    }

    // Construcción

    private JPanel construirEncabezado() {
        JPanel encabezado = new JPanel();
        encabezado.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        JButton anterior = new JButton("<");
        JButton siguiente = new JButton(">");
        anterior.setMargin(new java.awt.Insets(2, 8, 2, 8));
        siguiente.setMargin(new java.awt.Insets(2, 8, 2, 8));

        anterior.addActionListener(e -> moverMes(-1));
        siguiente.addActionListener(e -> moverMes(1));

        encabezado.add(anterior);
        encabezado.add(cboMes);
        encabezado.add(spnAnio);
        encabezado.add(siguiente);

        return encabezado;
    }

    private JPanel construirPie(LocalDate base) {
        JPanel pie = new JPanel();
        pie.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        JButton hoy = new JButton("Hoy");
        hoy.addActionListener(e -> {
            seleccionada = LocalDate.now();
            dispose();
        });

        JButton limpiar = new JButton("Cancelar");
        limpiar.addActionListener(e -> dispose());

        pie.add(hoy);
        pie.add(limpiar);
        return pie;
    }

    /** Avanza o retrocede un mes, ajustando el ano cuando cruza diciembre o enero. */
    private void moverMes(int desplazamiento) {
        YearMonth actual = YearMonth.of((Integer) spnAnio.getValue(),
                cboMes.getSelectedIndex() + 1);
        YearMonth nuevo = actual.plusMonths(desplazamiento);

        cboMes.setSelectedIndex(nuevo.getMonthValue() - 1);
        spnAnio.setValue(nuevo.getYear());
    }

    /**
     * Redibuja la cuadrícula. Las casillas vacías antes del día 1 alinean los
     * números bajo la columna correcta.
     */
    private void dibujarDias() {
        panelDias.removeAll();

        for (String dia : DIAS) {
            JLabel etiqueta = new JLabel(dia, SwingConstants.CENTER);
            etiqueta.setFont(etiqueta.getFont().deriveFont(Font.BOLD, 11f));
            etiqueta.setForeground(Color.GRAY);
            panelDias.add(etiqueta);
        }

        YearMonth mes = YearMonth.of((Integer) spnAnio.getValue(),
                cboMes.getSelectedIndex() + 1);

        // getDayOfWeek() devuelve LUNES=1..DOMINGO=7, pero la cuadricula empieza
        // en domingo, asi que se convierte con un modulo.
        int huecos = mes.atDay(1).getDayOfWeek().getValue() % 7;
        for (int i = 0; i < huecos; i++) {
            panelDias.add(new JLabel(""));
        }

        LocalDate hoy = LocalDate.now();

        for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
            LocalDate fecha = mes.atDay(dia);

            JButton boton = new JButton(String.valueOf(dia));
            boton.setMargin(new java.awt.Insets(2, 2, 2, 2));
            boton.setFocusPainted(false);
            boton.setPreferredSize(new Dimension(38, 28));

            if (fecha.equals(hoy)) {
                boton.setFont(boton.getFont().deriveFont(Font.BOLD));
                boton.setForeground(new Color(0, 90, 160));
            }
            if (fecha.getDayOfWeek() == DayOfWeek.SATURDAY
                    || fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
                boton.setForeground(new Color(150, 60, 60));
            }

            boton.addActionListener(e -> {
                seleccionada = fecha;
                dispose();
            });

            panelDias.add(boton);
        }

        panelDias.revalidate();
        panelDias.repaint();
        pack();
    }
}
