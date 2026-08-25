package com.clinica.vista;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * FlowLayout que calcula bien su altura cuando los componentes no caben en una
 * sola fila.
 *
 * ---------------------------------------------------------------------------
 * EL PROBLEMA QUE RESUELVE
 * ---------------------------------------------------------------------------
 * FlowLayout SI acomoda los componentes en varias filas cuando no caben a lo
 * ancho, pero al calcular su tamano preferido siempre responde la altura de UNA
 * sola fila. El resultado es que el contenedor reserva menos espacio del que
 * realmente ocupa y la segunda fila queda cortada o tapada por lo que sigue.
 *
 * Se nota sobre todo en las barras de filtros: al agregar un filtro mas, o al
 * angostar la ventana, los controles se salen de la vista.
 *
 * ---------------------------------------------------------------------------
 * LA SOLUCION
 * ---------------------------------------------------------------------------
 * Se hereda de FlowLayout —de modo que la colocacion real de los componentes,
 * que ya funciona bien, se aprovecha tal cual— y se reescribe unicamente el
 * calculo del tamano: se simula el acomodo fila por fila con el ancho
 * disponible y se suman las alturas.
 */
public class FlujoAjustable extends FlowLayout {

    public FlujoAjustable() {
        super(FlowLayout.LEFT, 6, 4);
    }

    public FlujoAjustable(int alineacion, int separacionH, int separacionV) {
        super(alineacion, separacionH, separacionV);
    }

    @Override
    public Dimension preferredLayoutSize(Container objetivo) {
        return calcularTamano(objetivo, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container objetivo) {
        Dimension minimo = calcularTamano(objetivo, false);
        minimo.width -= (getHgap() + 1);
        return minimo;
    }

    /**
     * Recorre los componentes acomodandolos en filas segun el ancho disponible
     * y devuelve el tamano que realmente hace falta.
     */
    private Dimension calcularTamano(Container objetivo, boolean preferido) {
        synchronized (objetivo.getTreeLock()) {

            int anchoDisponible = anchoDe(objetivo);

            Insets margenes = objetivo.getInsets();
            int margenHorizontal = margenes.left + margenes.right + getHgap() * 2;
            int anchoUtil = anchoDisponible - margenHorizontal;

            Dimension resultado = new Dimension(0, 0);
            int anchoFila = 0;
            int altoFila = 0;

            for (int i = 0; i < objetivo.getComponentCount(); i++) {
                Component componente = objetivo.getComponent(i);
                if (!componente.isVisible()) {
                    continue;
                }

                Dimension tamano = preferido
                        ? componente.getPreferredSize()
                        : componente.getMinimumSize();

                // Si no cabe en la fila actual, se cierra esa fila y se empieza otra.
                if (anchoFila + tamano.width > anchoUtil && anchoFila > 0) {
                    agregarFila(resultado, anchoFila, altoFila);
                    anchoFila = 0;
                    altoFila = 0;
                }

                if (anchoFila > 0) {
                    anchoFila += getHgap();
                }
                anchoFila += tamano.width;
                altoFila = Math.max(altoFila, tamano.height);
            }
            agregarFila(resultado, anchoFila, altoFila);

            resultado.width += margenHorizontal;
            resultado.height += margenes.top + margenes.bottom + getVgap() * 2;

            // Dentro de un JScrollPane hay que devolver un poco menos, o el
            // contenedor crece indefinidamente rebotando contra la barra.
            Container desplazable =
                    SwingUtilities.getAncestorOfClass(JScrollPane.class, objetivo);
            if (desplazable != null && objetivo.isValid()) {
                resultado.width -= (getHgap() + 1);
            }

            return resultado;
        }
    }

    /**
     * Ancho con el que se debe simular el acomodo.
     *
     * Se toma el del contenedor si ya fue dimensionado; si todavia no lo fue
     * (durante el primer armado de la ventana) se usa el maximo, para no
     * calcular una altura enorme basada en un ancho de cero.
     */
    private static int anchoDe(Container objetivo) {
        Container actual = objetivo;

        while (actual.getSize().width == 0 && actual.getParent() != null) {
            actual = actual.getParent();
        }

        int ancho = actual.getSize().width;
        return (ancho == 0) ? Integer.MAX_VALUE : ancho;
    }

    private void agregarFila(Dimension resultado, int anchoFila, int altoFila) {
        resultado.width = Math.max(resultado.width, anchoFila);

        if (resultado.height > 0) {
            resultado.height += getVgap();
        }
        resultado.height += altoFila;
    }
}
