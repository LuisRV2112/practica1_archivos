package com.clinica.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de un reporte, en forma de tabla generica: un titulo, los nombres
 * de las columnas y las filas ya convertidas a texto.
 *
 * Que TODOS los reportes devuelvan este mismo tipo es lo que permite tener una
 * sola pantalla y un solo exportador para los quince reportes del enunciado, en
 * lugar de quince pantallas casi identicas.
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
     * Agrega una fila.
     *
     * @throws IllegalArgumentException si la fila no trae tantos valores como
     *         columnas tiene el reporte; es un error de programacion, no del
     *         usuario, y conviene que salte de inmediato
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
