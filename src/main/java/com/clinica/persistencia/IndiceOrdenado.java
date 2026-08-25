package com.clinica.persistencia;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.UUID;

/**
 * Índice ordenado sobre archivo con búsqueda binaria (O(log n)).
 * Convierte al archivo de médicos en secuencial indexado.
 *
 * Cabecera 4 bytes: cantidad(4)
 * Entrada 20 bytes: claveMsb(8) + claveLsb(8) + numeroRegistro(4)
 *
 * El orden se paga al insertar (desplazamiento) para cobrar al leer
 * (búsqueda binaria en vez de barrido completo).
 */
public class IndiceOrdenado implements Closeable {

    private static final int TAM_CABECERA = Integer.BYTES;
    private static final int TAM_ENTRADA = Long.BYTES * 2 + Integer.BYTES;

    private final RandomAccessFile archivo;
    private int cantidad;

    public IndiceOrdenado(String ruta) throws IOException {
        File f = new File(ruta);

        File carpeta = f.getParentFile();
        if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta " + carpeta);
        }

        boolean esNuevo = !f.exists() || f.length() == 0;
        this.archivo = new RandomAccessFile(f, "rw");

        if (esNuevo) {
            this.cantidad = 0;
            escribirCabecera();
        } else {
            archivo.seek(0);
            this.cantidad = archivo.readInt();
        }
    }

    // Operaciones

    /**
     * Búsqueda binaria: O(log n). Devuelve la posición en el índice o -1.
     */
    public Integer buscar(UUID clave) throws IOException {
        int posicion = posicionDeClave(clave);
        if (posicion < 0) {
            return null;
        }
        archivo.seek(posicionDe(posicion) + Long.BYTES * 2);
        return archivo.readInt();
    }

    /**
     * Inserta manteniendo el orden desplazando entradas hacia atrás.
     * El recorrido es de atrás hacia adelante para no sobrescribir antes de
     * copiar.
     */
    public void insertar(UUID clave, int numeroRegistro) throws IOException {
        int posicion = puntoDeInsercion(clave);

        for (int i = cantidad - 1; i >= posicion; i--) {
            archivo.seek(posicionDe(i));
            long msb = archivo.readLong();
            long lsb = archivo.readLong();
            int registro = archivo.readInt();

            archivo.seek(posicionDe(i + 1));
            archivo.writeLong(msb);
            archivo.writeLong(lsb);
            archivo.writeInt(registro);
        }

        archivo.seek(posicionDe(posicion));
        archivo.writeLong(clave.getMostSignificantBits());
        archivo.writeLong(clave.getLeastSignificantBits());
        archivo.writeInt(numeroRegistro);

        cantidad++;
        escribirCabecera();
    }

    /** Actualiza o inserta. Lo usa el multianillo para mover cabezas. */
    public void actualizar(UUID clave, int numeroRegistro) throws IOException {
        int posicion = posicionDeClave(clave);

        if (posicion < 0) {
            insertar(clave, numeroRegistro);
            return;
        }
        archivo.seek(posicionDe(posicion) + Long.BYTES * 2);
        archivo.writeInt(numeroRegistro);
    }

    /** Elimina una clave cerrando el hueco (desplaza hacia adelante). */
    public void eliminar(UUID clave) throws IOException {
        int posicion = posicionDeClave(clave);
        if (posicion < 0) {
            return;
        }

        for (int i = posicion + 1; i < cantidad; i++) {
            archivo.seek(posicionDe(i));
            long msb = archivo.readLong();
            long lsb = archivo.readLong();
            int registro = archivo.readInt();

            archivo.seek(posicionDe(i - 1));
            archivo.writeLong(msb);
            archivo.writeLong(lsb);
            archivo.writeInt(registro);
        }

        cantidad--;
        escribirCabecera();
        archivo.setLength(posicionDe(cantidad));
    }

    /** Vacia el indice, para reconstruirlo desde el archivo de datos. */
    public void vaciar() throws IOException {
        archivo.setLength(0);
        cantidad = 0;
        escribirCabecera();
    }

    public int getCantidad() {
        return cantidad;
    }

    /** Máximo de comparaciones de una búsqueda binaria; para reporte técnico. */
    public int comparacionesMaximas() {
        return (cantidad <= 1) ? cantidad : (int) Math.ceil(Math.log(cantidad) / Math.log(2));
    }

    @Override
    public void close() throws IOException {
        archivo.close();
    }

    // Internos

    private long posicionDe(int entrada) {
        return TAM_CABECERA + (long) entrada * TAM_ENTRADA;
    }

    private void escribirCabecera() throws IOException {
        archivo.seek(0);
        archivo.writeInt(cantidad);
    }

    private UUID claveEn(int entrada) throws IOException {
        archivo.seek(posicionDe(entrada));
        long msb = archivo.readLong();
        long lsb = archivo.readLong();
        return new UUID(msb, lsb);
    }

    /** @return la posicion de la clave, o -1 si no esta */
    private int posicionDeClave(UUID clave) throws IOException {
        int inicio = 0;
        int fin = cantidad - 1;

        while (inicio <= fin) {
            int medio = (inicio + fin) >>> 1; // evita desbordamiento de (a+b)/2
            int comparacion = claveEn(medio).compareTo(clave);

            if (comparacion == 0) {
                return medio;
            }
            if (comparacion < 0) {
                inicio = medio + 1;
            } else {
                fin = medio - 1;
            }
        }
        return -1;
    }

    /** Posición donde debe insertarse para mantener orden. */
    private int puntoDeInsercion(UUID clave) throws IOException {
        int inicio = 0;
        int fin = cantidad - 1;

        while (inicio <= fin) {
            int medio = (inicio + fin) >>> 1;

            if (claveEn(medio).compareTo(clave) < 0) {
                inicio = medio + 1;
            } else {
                fin = medio - 1;
            }
        }
        return inicio;
    }
}