package com.clinica.persistencia;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.UUID;

/**
 * Indice ORDENADO sobre archivo, con busqueda binaria. Es lo que convierte al
 * archivo de medicos en un ARCHIVO SECUENCIAL INDEXADO.
 *
 * ---------------------------------------------------------------------------
 * ESTRUCTURA DEL ARCHIVO DE INDICE
 * ---------------------------------------------------------------------------
 *
 *  [ CABECERA: 4 bytes ][ entrada 0 ][ entrada 1 ] ... ordenadas por clave
 *
 *  Cabecera:
 *      int cantidad  (4)  numero de entradas
 *
 *  Entrada (20 bytes):
 *      long claveMsb        (8)  bits altos del UUID
 *      long claveLsb        (8)  bits bajos del UUID
 *      int  numeroRegistro  (4)  posicion en el archivo de datos
 *
 * ---------------------------------------------------------------------------
 * POR QUE ORDENADO
 * ---------------------------------------------------------------------------
 *
 * El archivo de DATOS queda en el orden en que se fueron insertando los
 * medicos, que es lo mejor para recorrerlo entero al generar reportes. El
 * archivo de INDICE, en cambio, se mantiene siempre ordenado por clave, y eso
 * permite localizar un medico por BUSQUEDA BINARIA: se mira la entrada de en
 * medio, se descarta la mitad que no puede contener la clave y se repite.
 *
 * Con 1000 medicos, un barrido secuencial mira hasta 1000 registros; la
 * busqueda binaria mira 10. Ese es el sentido de tener el indice separado y
 * ordenado.
 *
 * El precio esta en la insercion: para que el indice siga ordenado hay que
 * abrir hueco desplazando las entradas posteriores. Es el compromiso clasico de
 * esta organizacion — se paga al escribir para cobrar al leer, y en una clinica
 * se consulta mucho mas de lo que se da de alta.
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

    // =======================================================================
    // OPERACIONES
    // =======================================================================

    /**
     * Busqueda binaria sobre el indice: O(log n).
     *
     * @return el numero de registro, o null si la clave no esta
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
     * Inserta manteniendo el orden.
     *
     * Se busca donde deberia ir y se desplazan hacia el final las entradas
     * posteriores para abrir el hueco. El desplazamiento va de atras hacia
     * adelante para no sobrescribir una entrada antes de haberla copiado.
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

    /** Elimina una clave cerrando el hueco que deja. */
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

    /** Comparaciones que hace como maximo una busqueda binaria; para el reporte. */
    public int comparacionesMaximas() {
        return (cantidad <= 1) ? cantidad : (int) Math.ceil(Math.log(cantidad) / Math.log(2));
    }

    @Override
    public void close() throws IOException {
        archivo.close();
    }

    // =======================================================================
    // INTERNOS
    // =======================================================================

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
            int medio = (inicio + fin) >>> 1; // >>> evita el desbordamiento de (a+b)/2
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

    /** Posicion donde deberia ir la clave para que el indice siga ordenado. */
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
