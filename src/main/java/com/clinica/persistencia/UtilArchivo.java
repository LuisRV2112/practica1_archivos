package com.clinica.persistencia;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Utilidades de bajo nivel para escribir y leer campos de LONGITUD FIJA.
 *
 * Todo el diseno del sistema descansa en una idea: si cada registro mide
 * exactamente lo mismo, la posicion de un registro se calcula con una
 * multiplicacion y se llega a el con un unico seek(), sin recorrer el archivo.
 *
 * Por eso NO se usa writeUTF(): ese metodo escribe primero la longitud y luego
 * los bytes, de modo que dos nombres distintos ocupan distinto espacio y el
 * calculo de posiciones se rompe.
 *
 * Las cadenas se guardan con writeChars(): 2 bytes por caracter (UTF-16).
 * Gasta el doble de espacio que ASCII, pero maneja tildes y enies sin riesgo
 * de partir un caracter multibyte a la mitad.
 */
public final class UtilArchivo {

    /** Clase de utilidades: no se instancia. */
    private UtilArchivo() {
    }

    /**
     * Escribe una cadena ocupando siempre {@code longitud} caracteres.
     * Si el texto es mas corto se rellena con espacios; si es mas largo se
     * recorta. Asi el campo siempre ocupa los mismos bytes.
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

    /**
     * Escribe una hora como un entero: los segundos transcurridos desde
     * medianoche. Ocupa 4 bytes fijos. Un valor nulo se guarda como -1.
     */
    public static void escribirHora(RandomAccessFile archivo, LocalTime hora) throws IOException {
        archivo.writeInt(hora == null ? -1 : hora.toSecondOfDay());
    }

    /** Lee una hora guardada con {@link #escribirHora}. */
    public static LocalTime leerHora(RandomAccessFile archivo) throws IOException {
        int segundos = archivo.readInt();
        return (segundos < 0) ? null : LocalTime.ofSecondOfDay(segundos);
    }

    /** Cuantos bytes ocupa un campo de texto de {@code caracteres} de largo. */
    public static int bytesDeCadena(int caracteres) {
        return caracteres * Character.BYTES; // 2 bytes por caracter
    }

    /**
     * Escribe una fecha como los dias transcurridos desde el 1 de enero de 1970.
     * Ocupa 8 bytes fijos y admite fechas anteriores a esa (dias negativos), lo
     * que importa para las fechas de nacimiento.
     *
     * Un valor nulo se guarda como Long.MIN_VALUE, que nunca puede ser una
     * fecha real.
     */
    public static void escribirFecha(RandomAccessFile archivo, LocalDate fecha) throws IOException {
        archivo.writeLong(fecha == null ? Long.MIN_VALUE : fecha.toEpochDay());
    }

    /** Lee una fecha guardada con {@link #escribirFecha}. */
    public static LocalDate leerFecha(RandomAccessFile archivo) throws IOException {
        long dias = archivo.readLong();
        return (dias == Long.MIN_VALUE) ? null : LocalDate.ofEpochDay(dias);
    }
}