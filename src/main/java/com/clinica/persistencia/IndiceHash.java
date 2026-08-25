package com.clinica.persistencia;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Índice hash sobre archivo. Convierte a pacientes en archivo directo O(1).
 *
 * Estructura: [ Cabecera 8 bytes ][ cubeta 0 ][ cubeta 1 ] ...
 *   Cabecera: capacidad(4) + cantidad(4)
 *   Cubeta:   estado(1) + clave(largoClave) + numeroRegistro(4)
 *
 * Colisiones: sondeo lineal. Borradoss: lápida (no libre) para no interrumpir
 * búsquedas. Redispersión al superar 70% de ocupación.
 */
public class IndiceHash implements Closeable {

    private static final int TAM_CABECERA = Integer.BYTES * 2;

    private static final byte CUBETA_LIBRE = 0;
    private static final byte CUBETA_OCUPADA = 1;
    private static final byte CUBETA_BORRADA = 2; // lapida

    /** Umbral de ocupación para redispersar. */
    private static final double FACTOR_CARGA_MAXIMO = 0.70;

    private static final int CAPACIDAD_INICIAL = 61; // primo para mejor distribución

    private final File ruta;
    private final int largoClave;
    private final int tamCubeta;

    private RandomAccessFile archivo;
    private int capacidad;
    private int cantidad;

    public IndiceHash(String ruta, int largoClave) throws IOException {
        this.ruta = new File(ruta);
        this.largoClave = largoClave;
        this.tamCubeta = Byte.BYTES
                + UtilArchivo.bytesDeCadena(largoClave)
                + Integer.BYTES;

        File carpeta = this.ruta.getParentFile();
        if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta " + carpeta);
        }

        boolean esNuevo = !this.ruta.exists() || this.ruta.length() == 0;
        this.archivo = new RandomAccessFile(this.ruta, "rw");

        if (esNuevo) {
            formatear(CAPACIDAD_INICIAL);
        } else {
            archivo.seek(0);
            this.capacidad = archivo.readInt();
            this.cantidad = archivo.readInt();
        }
    }

    // Operaciones

    /**
     * Busca la posición de una clave: O(1) promedio. Lápida = sigue buscando,
     * libre = no está.
     */
    public Integer buscar(String clave) throws IOException {
        String llave = normalizar(clave);
        int cubeta = cubetaDe(llave);

        // Avanza mientras haya algo (ocupada o lápida). Libre = no existe.
        for (int intento = 0; intento < capacidad; intento++) {
            int actual = (cubeta + intento) % capacidad;

            archivo.seek(posicionDe(actual));
            byte estado = archivo.readByte();

            if (estado == CUBETA_LIBRE) {
                return null;
            }
            if (estado == CUBETA_OCUPADA) {
                String guardada = UtilArchivo.leerCadena(archivo, largoClave);
                if (guardada.equals(llave)) {
                    return archivo.readInt();
                }
            }
        }
        return null;
    }

    /** Asocia clave con posición. Redispersa si la tabla se llena. */
    public void insertar(String clave, int numeroRegistro) throws IOException {
        if ((cantidad + 1) > capacidad * FACTOR_CARGA_MAXIMO) {
            redispersar();
        }
        colocar(normalizar(clave), numeroRegistro);

        cantidad++;
        escribirCabecera();
    }

    /**
     * Actualiza el valor de una clave, o la inserta si no existía.
     * Lo usa el multianillo para mover cabezas.
     */
    public void actualizar(String clave, int numeroRegistro) throws IOException {
        String llave = normalizar(clave);
        int cubeta = cubetaDe(llave);

        for (int intento = 0; intento < capacidad; intento++) {
            int actual = (cubeta + intento) % capacidad;

            archivo.seek(posicionDe(actual));
            byte estado = archivo.readByte();

            if (estado == CUBETA_LIBRE) {
                break; // no estaba
            }
            if (estado == CUBETA_OCUPADA) {
                String guardada = UtilArchivo.leerCadena(archivo, largoClave);
                if (guardada.equals(llave)) {
                    archivo.writeInt(numeroRegistro);
                    return;
                }
            }
        }
        insertar(llave, numeroRegistro);
    }

    /** Marca como borrada dejando lápida. */
    public void eliminar(String clave) throws IOException {
        String llave = normalizar(clave);
        int cubeta = cubetaDe(llave);

        for (int intento = 0; intento < capacidad; intento++) {
            int actual = (cubeta + intento) % capacidad;

            archivo.seek(posicionDe(actual));
            byte estado = archivo.readByte();

            if (estado == CUBETA_LIBRE) {
                return; // no estaba
            }
            if (estado == CUBETA_OCUPADA) {
                String guardada = UtilArchivo.leerCadena(archivo, largoClave);
                if (guardada.equals(llave)) {
                    archivo.seek(posicionDe(actual));
                    archivo.writeByte(CUBETA_BORRADA);
                    cantidad--;
                    escribirCabecera();
                    return;
                }
            }
        }
    }

    /** Vacia el índice para reconstruirlo desde el archivo de datos. */
    public void vaciar() throws IOException {
        formatear(CAPACIDAD_INICIAL);
    }

    public int getCapacidad() {
        return capacidad;
    }

    public int getCantidad() {
        return cantidad;
    }

    /** Porcentaje de ocupación; para reporte técnico. */
    public double factorCarga() {
        return (capacidad == 0) ? 0 : (double) cantidad / capacidad;
    }

    @Override
    public void close() throws IOException {
        archivo.close();
    }

    // Internos

    /**
     * Hash propio: acumula caracteres multiplicando por 31 (primo impar,
     * misma idea que String.hashCode(), escrito a mano por exigencia del enunciado).
     */
    private int dispersar(String clave) {
        int valor = 7;
        for (int i = 0; i < clave.length(); i++) {
            valor = valor * 31 + clave.charAt(i);
        }
        return valor;
    }

    /** Cubeta inicial. Se limpia bit de signo antes del módulo para evitar negativos. */
    private int cubetaDe(String clave) {
        return (dispersar(clave) & 0x7FFFFFFF) % capacidad;
    }

    private long posicionDe(int cubeta) {
        return TAM_CABECERA + (long) cubeta * tamCubeta;
    }

    /** Escribe entrada en la primera cubeta libre u honeada desde su posición. */
    private void colocar(String clave, int numeroRegistro) throws IOException {
        int cubeta = cubetaDe(clave);

        for (int intento = 0; intento < capacidad; intento++) {
            int actual = (cubeta + intento) % capacidad;

            archivo.seek(posicionDe(actual));
            byte estado = archivo.readByte();

            // Lápida se puede reutilizar.
            if (estado == CUBETA_LIBRE || estado == CUBETA_BORRADA) {
                archivo.seek(posicionDe(actual));
                archivo.writeByte(CUBETA_OCUPADA);
                UtilArchivo.escribirCadena(archivo, clave, largoClave);
                archivo.writeInt(numeroRegistro);
                return;
            }
        }
        throw new IOException("El indice hash esta lleno; no se pudo insertar " + clave);
    }

    /**
     * Duplica capacidad y recoloca claves. Costoso pero ocurre pocas veces
     * y evita que colisiones degraden la búsqueda a O(n).
     */
    private void redispersar() throws IOException {
        List<String> claves = new ArrayList<>();
        List<Integer> posiciones = new ArrayList<>();

        for (int i = 0; i < capacidad; i++) {
            archivo.seek(posicionDe(i));
            if (archivo.readByte() == CUBETA_OCUPADA) {
                claves.add(UtilArchivo.leerCadena(archivo, largoClave));
                posiciones.add(archivo.readInt());
            }
        }

        formatear(siguientePrimo(capacidad * 2));

        for (int i = 0; i < claves.size(); i++) {
            colocar(claves.get(i), posiciones.get(i));
        }
        cantidad = claves.size();
        escribirCabecera();
    }

    /** Formatea el archivo con la capacidad indicada y todas las cubetas libres. */
    private void formatear(int nuevaCapacidad) throws IOException {
        this.capacidad = nuevaCapacidad;
        this.cantidad = 0;

        archivo.setLength(0);
        escribirCabecera();

        // Escribe tabla completa de una vez en memoria (más rápido que muchas escrituras).
        byte[] vacia = new byte[capacidad * tamCubeta];
        archivo.seek(TAM_CABECERA);
        archivo.write(vacia);
    }

    private void escribirCabecera() throws IOException {
        archivo.seek(0);
        archivo.writeInt(capacidad);
        archivo.writeInt(cantidad);
    }

    /** Primo mayor o igual a n; capacidad prima → mejor distribución. */
    private static int siguientePrimo(int n) {
        int candidato = Math.max(n, 3);
        if (candidato % 2 == 0) {
            candidato++;
        }
        while (!esPrimo(candidato)) {
            candidato += 2;
        }
        return candidato;
    }

    private static boolean esPrimo(int n) {
        if (n < 2) {
            return false;
        }
        if (n % 2 == 0) {
            return n == 2;
        }
        for (int divisor = 3; (long) divisor * divisor <= n; divisor += 2) {
            if (n % divisor == 0) {
                return false;
            }
        }
        return true;
    }

    private static String normalizar(String clave) {
        return (clave == null) ? "" : clave.trim();
    }
}