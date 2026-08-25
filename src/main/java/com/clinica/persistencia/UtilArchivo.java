package com.clinica.persistencia;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Utilidades de bajo nivel para campos de longitud fija.
 * NO se usa writeUTF() porque rompe el cálculo de posiciones por aritmética
 * de bytes. Las cadenas se guardan con writeChars(): 2 bytes/caracter (UTF-16),
 * suficiente para tildes y ñ.
 */
public final class UtilArchivo {

    /** Clase de utilidades: no se instancia. */
    private UtilArchivo() {
    }

    /**
     * Escribe una cadena con longitud fija. Si es más corta rellena con espacios;
     * si es más larga, recorta.
     */
    public static void escribirCadena(RandomAccessFile archivo, String valor, int longitud)
            throws IOException {
        String texto = (valor == null) ? "" : valor;

        if (texto.length() > longitud) {
            texto = texto.substring(0, longitud);
        }

        StringBuilder relleno = new StringBuilder(texto);
        while (relleno.length() < longitud) {
            relleno.append(' ');
        }

        archivo.writeChars(relleno.toString());
    }

    /**
     * Lee una cadena de {@code longitud} caracteres y le quita el relleno.
     */
    public static String leerCadena(RandomAccessFile archivo, int longitud) throws IOException {
        char[] buffer = new char[longitud];
        for (int i = 0; i < longitud; i++) {
            buffer[i] = archivo.readChar();
        }
        return new String(buffer).trim();
    }

    /** Escribe hora como segundos desde medianoche (4 bytes). Nulo = -1. */
    public static void escribirHora(RandomAccessFile archivo, LocalTime hora) throws IOException {
        archivo.writeInt(hora == null ? -1 : hora.toSecondOfDay());
    }

    /** Lee hora escrita con {@link #escribirHora}. */
    public static LocalTime leerHora(RandomAccessFile archivo) throws IOException {
        int segundos = archivo.readInt();
        return (segundos < 0) ? null : LocalTime.ofSecondOfDay(segundos);
    }

    /** Bytes que ocupa un campo de texto de {@code caracteres} caracteres. */
    public static int bytesDeCadena(int caracteres) {
        return caracteres * Character.BYTES; // 2 bytes por caracter
    }

    /**
     * Escribe fecha como días desde epoch (8 bytes). Admite fechas anteriores
     * a 1970 (días negativos). Nulo = Long.MIN_VALUE.
     */
    public static void escribirFecha(RandomAccessFile archivo, LocalDate fecha) throws IOException {
        archivo.writeLong(fecha == null ? Long.MIN_VALUE : fecha.toEpochDay());
    }

    /** Lee fecha escrita con {@link #escribirFecha}. */
    public static LocalDate leerFecha(RandomAccessFile archivo) throws IOException {
        long dias = archivo.readLong();
        return (dias == Long.MIN_VALUE) ? null : LocalDate.ofEpochDay(dias);
    }

    /**
     * Escribe LocalDateTime como segundos desde epoch en UTC (8 bytes).
     * Se usa UTC fijo para que un cambio de zona horaria no corra las marcas
     * ya guardadas.
     */
    public static void escribirFechaHora(RandomAccessFile archivo, LocalDateTime momento)
            throws IOException {
        archivo.writeLong(momento == null
                ? Long.MIN_VALUE
                : momento.toEpochSecond(ZoneOffset.UTC));
    }

    /** Lee marca de tiempo escrita con {@link #escribirFechaHora}. */
    public static LocalDateTime leerFechaHora(RandomAccessFile archivo) throws IOException {
        long segundos = archivo.readLong();
        return (segundos == Long.MIN_VALUE)
                ? null
                : LocalDateTime.ofEpochSecond(segundos, 0, ZoneOffset.UTC);
    }
}