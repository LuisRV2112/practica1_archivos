package com.clinica.servicio;

import com.clinica.modelo.Reporte;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;

/**
 * Exporta un {@link Reporte} a un archivo de texto o CSV.
 *
 * Al ser todos los reportes la misma estructura generica, esta clase sirve para
 * los quince sin conocer ninguno en particular.
 */
public final class ExportadorReportes {

    private ExportadorReportes() {
    }

    /** Escribe el reporte en formato CSV, separado por comas. */
    public static void exportarCsv(Reporte reporte, File destino) throws IOException {
        try (Writer salida = abrir(destino)) {
            salida.write(unirCsv(reporte.getColumnas()));
            salida.write(System.lineSeparator());

            for (String[] fila : reporte.getFilas()) {
                salida.write(unirCsv(fila));
                salida.write(System.lineSeparator());
            }
        }
    }

    /**
     * Escribe el reporte como texto tabulado, con las columnas alineadas.
     * Es lo que conviene para imprimirlo o pegarlo en la documentacion.
     */
    public static void exportarTexto(Reporte reporte, File destino) throws IOException {
        int[] anchos = calcularAnchos(reporte);

        try (Writer salida = abrir(destino)) {
            salida.write(reporte.getTitulo());
            salida.write(System.lineSeparator());
            salida.write("Generado el " + ServicioBitacora.formatearMomento(LocalDateTime.now()));
            salida.write(System.lineSeparator());
            salida.write(System.lineSeparator());

            salida.write(alinear(reporte.getColumnas(), anchos));
            salida.write(System.lineSeparator());
            salida.write(separador(anchos));
            salida.write(System.lineSeparator());

            for (String[] fila : reporte.getFilas()) {
                salida.write(alinear(fila, anchos));
                salida.write(System.lineSeparator());
            }

            salida.write(System.lineSeparator());
            salida.write("Total de registros: " + reporte.cantidadFilas());
            salida.write(System.lineSeparator());
        }
    }

    // =======================================================================
    // INTERNOS
    // =======================================================================

    /**
     * Abre el archivo en UTF-8 explicitamente. Sin indicar la codificacion, Java
     * usaria la del sistema y las tildes se verian mal al abrir el CSV en otra
     * maquina.
     */
    private static Writer abrir(File destino) throws IOException {
        File carpeta = destino.getParentFile();
        if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta " + carpeta);
        }
        return new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(destino.toPath()), StandardCharsets.UTF_8));
    }

    private static String unirCsv(String[] valores) {
        StringBuilder linea = new StringBuilder();
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) {
                linea.append(',');
            }
            linea.append(escaparCsv(valores[i]));
        }
        return linea.toString();
    }

    /**
     * Aplica las reglas del formato CSV: si el valor contiene una coma, una
     * comilla o un salto de linea, se encierra entre comillas dobles y las
     * comillas internas se duplican.
     *
     * Sin esto, un motivo de consulta como "dolor de cabeza, nauseas" partiria
     * la fila en dos columnas al abrirla en Excel.
     */
    private static String escaparCsv(String valor) {
        String texto = (valor == null) ? "" : valor;

        boolean necesitaComillas = texto.contains(",")
                || texto.contains("\"")
                || texto.contains("\n")
                || texto.contains("\r");

        if (!necesitaComillas) {
            return texto;
        }
        return '"' + texto.replace("\"", "\"\"") + '"';
    }

    /** Ancho de cada columna: el del texto mas largo que aparezca en ella. */
    private static int[] calcularAnchos(Reporte reporte) {
        String[] columnas = reporte.getColumnas();
        int[] anchos = new int[columnas.length];

        for (int i = 0; i < columnas.length; i++) {
            anchos[i] = columnas[i].length();
        }
        for (String[] fila : reporte.getFilas()) {
            for (int i = 0; i < fila.length; i++) {
                if (fila[i] != null && fila[i].length() > anchos[i]) {
                    anchos[i] = fila[i].length();
                }
            }
        }
        return anchos;
    }

    private static String alinear(String[] valores, int[] anchos) {
        StringBuilder linea = new StringBuilder();
        for (int i = 0; i < valores.length; i++) {
            String texto = (valores[i] == null) ? "" : valores[i];
            linea.append(texto);
            for (int espacio = texto.length(); espacio < anchos[i]; espacio++) {
                linea.append(' ');
            }
            if (i < valores.length - 1) {
                linea.append("  |  ");
            }
        }
        return linea.toString();
    }

    private static String separador(int[] anchos) {
        StringBuilder linea = new StringBuilder();
        for (int i = 0; i < anchos.length; i++) {
            linea.append("-".repeat(anchos[i]));
            if (i < anchos.length - 1) {
                linea.append("--+--");
            }
        }
        return linea.toString();
    }
}
