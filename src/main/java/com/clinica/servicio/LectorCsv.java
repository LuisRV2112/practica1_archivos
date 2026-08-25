package com.clinica.servicio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector CSV escrito a mano. Inverso de ExportadorReportes: deshace las reglas
 * del formato al leer. No se usa split(",") porque las comillas encapsulan
 * comillas literales dentro de campos: se recorre carácter a carácter llevando
 * cuenta del estado "dentro/fuera de comillas".
 */
public final class LectorCsv {

    private LectorCsv() {
    }

    /**
     * Lee el archivo como UTF-8 explícitamente: sin indicarlo, Java usaría la
     * codificación del SO y las tildes llegarían rotas desde otra máquina.
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
     * Indica si la fila parece encabezado comparando el primer campo con la
     * columna esperada. Así el archivo funciona con o sin encabezado.
     */
    public static boolean pareceEncabezado(String[] fila, String primeraColumna) {
        return fila.length > 0 && fila[0].equalsIgnoreCase(primeraColumna);
    }

    /** Devuelve el campo en esa posicion, o cadena vacia si la fila es mas corta. */
    public static String campo(String[] fila, int indice) {
        return (indice < fila.length && fila[indice] != null) ? fila[indice].trim() : "";
    }
}
