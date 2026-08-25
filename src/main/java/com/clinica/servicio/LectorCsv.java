package com.clinica.servicio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector de archivos CSV escrito a mano.
 *
 * Es la operacion inversa de {@link ExportadorReportes}: aquel aplica las reglas
 * del formato al escribir y este las deshace al leer.
 *
 * ---------------------------------------------------------------------------
 * POR QUE NO BASTA CON UN split(",")
 * ---------------------------------------------------------------------------
 * Un campo puede contener una coma si va entre comillas. Con split(","), un
 * motivo de consulta como "dolor de cabeza, nauseas" se partiria en dos
 * columnas y todas las siguientes quedarian corridas, produciendo datos
 * silenciosamente equivocados.
 *
 * Por eso se recorre caracter por caracter llevando cuenta de si se esta dentro
 * o fuera de comillas:
 *
 *   - Una coma FUERA de comillas termina el campo.
 *   - Una coma DENTRO de comillas es parte del texto.
 *   - Dos comillas seguidas dentro de un campo representan una comilla real,
 *     que es como el exportador las escribio.
 *   - Un salto de linea dentro de comillas tampoco termina el registro: las
 *     observaciones de una cita pueden ocupar varios renglones.
 */
public final class LectorCsv {

    private LectorCsv() {
    }

    /**
     * Lee el archivo completo y devuelve sus filas ya separadas en campos.
     *
     * Se lee como UTF-8 explicitamente, la misma codificacion con la que
     * exporta el sistema. Sin indicarla, Java usaria la del sistema operativo y
     * las tildes llegarian rotas desde otra maquina.
     */
    public static List<String[]> leer(File archivo) throws IOException {
        String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
        return interpretar(contenido);
    }

    /** Separa un texto CSV completo en filas y campos. */
    public static List<String[]> interpretar(String contenido) {
        List<String[]> filas = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();

        boolean dentroDeComillas = false;
        boolean filaConContenido = false;

        for (int i = 0; i < contenido.length(); i++) {
            char c = contenido.charAt(i);

            if (dentroDeComillas) {
                if (c == '"') {
                    // Dos comillas seguidas son una comilla literal.
                    if (i + 1 < contenido.length() && contenido.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        dentroDeComillas = false;
                    }
                } else {
                    campo.append(c);
                }
                continue;
            }

            switch (c) {
                case '"' -> dentroDeComillas = true;
                case ',' -> {
                    campos.add(campo.toString().trim());
                    campo.setLength(0);
                    filaConContenido = true;
                }
                case '\n' -> {
                    campos.add(campo.toString().trim());
                    campo.setLength(0);

                    if (filaConContenido || !esFilaVacia(campos)) {
                        filas.add(campos.toArray(new String[0]));
                    }
                    campos = new ArrayList<>();
                    filaConContenido = false;
                }
                case '\r' -> {
                    // Fin de linea de Windows: se ignora, el \n hace el trabajo.
                }
                default -> campo.append(c);
            }
        }

        // Ultima fila, si el archivo no termina en salto de linea.
        campos.add(campo.toString().trim());
        if (!esFilaVacia(campos)) {
            filas.add(campos.toArray(new String[0]));
        }

        return filas;
    }

    private static boolean esFilaVacia(List<String> campos) {
        for (String valor : campos) {
            if (!valor.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Indica si la fila parece ser el encabezado de columnas y no un dato.
     *
     * Se compara el primer campo con el nombre esperado de la primera columna.
     * Asi el archivo funciona lo mismo con encabezado que sin el, que es como
     * llegan los CSV del mundo real.
     */
    public static boolean pareceEncabezado(String[] fila, String primeraColumna) {
        return fila.length > 0 && fila[0].equalsIgnoreCase(primeraColumna);
    }

    /** Devuelve el campo en esa posicion, o cadena vacia si la fila es mas corta. */
    public static String campo(String[] fila, int indice) {
        return (indice < fila.length && fila[indice] != null) ? fila[indice].trim() : "";
    }
}
