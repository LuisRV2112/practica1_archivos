package com.clinica.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabla genérica de resultado: título, columnas y filas ya en texto.
 * Todos los reportes devuelven este tipo → una sola pantalla y un solo exportador.
 */
public class Reporte {

    private final String titulo;
    private final String[] columnas;
    private final List<String[]> filas = new ArrayList<>();

    public Reporte(String titulo, String... columnas) {
        this.titulo = titulo;
        this.columnas = columnas;
    }

    /**
     * Agrega una fila. Lanza error si el número de valores no coincide con las columnas.
     */
    public void agregarFila(String... valores) {
        if (valores.length != columnas.length) {
            throw new IllegalArgumentException(
                    "La fila tiene " + valores.length + " valores pero el reporte '"
                            + titulo + "' tiene " + columnas.length + " columnas.");
        }
        filas.add(valores);
    }

    public String getTitulo() {
        return titulo;
    }

    public String[] getColumnas() {
        return columnas;
    }

    public List<String[]> getFilas() {
        return filas;
    }

    public int cantidadFilas() {
        return filas.size();
    }

    public boolean estaVacio() {
        return filas.isEmpty();
    }
}
