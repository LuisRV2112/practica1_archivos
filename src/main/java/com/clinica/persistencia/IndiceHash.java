package com.clinica.persistencia;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Indice HASH sobre archivo. Es lo que convierte al archivo de pacientes en un
 * ARCHIVO DIRECTO: la posicion de un registro se CALCULA a partir de su clave,
 * no se busca.
 *
 * ---------------------------------------------------------------------------
 * ESTRUCTURA DEL ARCHIVO DE INDICE
 * ---------------------------------------------------------------------------
 *
 *  [ CABECERA: 8 bytes ][ cubeta 0 ][ cubeta 1 ] ... [ cubeta capacidad-1 ]
 *
 *  Cabecera:
 *      int capacidad  (4)  cuantas cubetas tiene la tabla
 *      int cantidad   (4)  cuantas estan ocupadas
 *
 *  Cubeta (fija):
 *      byte estado          (1)  0 = libre, 1 = ocupada, 2 = borrada
 *      char[largoClave]          la clave
 *      int  numeroRegistro  (4)  posicion en el archivo de datos
 *
 * ---------------------------------------------------------------------------
 * COMO FUNCIONA
 * ---------------------------------------------------------------------------
 *
 * 1. La funcion de dispersion convierte la clave en un numero, y el resto de
 *    dividirlo entre la capacidad da la cubeta donde deberia estar.
 *
 * 2. COLISIONES: dos claves distintas pueden caer en la misma cubeta. Se
 *    resuelven por SONDEO LINEAL: si la cubeta esta ocupada por otra clave, se
 *    prueba la siguiente, y asi sucesivamente.
 *
 * 3. BORRADOS: una cubeta borrada NO se marca como libre, sino con una marca
 *    propia (una "lapida"). Si se marcara libre, una busqueda que llegara ahi
 *    se detendria y no encontraria claves que se guardaron mas adelante por
 *    sondeo. La lapida dice "aqui no esta, pero sigue buscando".
 *
 * 4. FACTOR DE CARGA: cuando la tabla se llena por encima del 70% las
 *    colisiones se disparan y el O(1) deja de cumplirse. Al pasar ese umbral la
 *    tabla se REDISPERSA: se duplica la capacidad y se recolocan todas las
 *    claves.
 */
public class IndiceHash implements Closeable {

    private static final int TAM_CABECERA = Integer.BYTES * 2;

    private static final byte CUBETA_LIBRE = 0;
    private static final byte CUBETA_OCUPADA = 1;
    private static final byte CUBETA_BORRADA = 2; // lapida

    /** Umbral de ocupacion a partir del cual se redispersa. */
    private static final double FACTOR_CARGA_MAXIMO = 0.70;

    private static final int CAPACIDAD_INICIAL = 61; // primo: reparte mejor

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

    // =======================================================================
    // OPERACIONES
    // =======================================================================

    /**
     * Busca la posicion asociada a una clave.
     *
     * Cuesta O(1) en promedio: se calcula la cubeta y, salvo colision, se
     * acierta al primer intento.
     *
     * @return el numero de registro, o null si la clave no esta
     */
    public Integer buscar(String clave) throws IOException {
        String llave = normalizar(clave);
        int cubeta = cubetaDe(llave);

        // Se avanza mientras haya algo (ocupada o lapida). Una cubeta LIBRE
        // significa que la clave nunca se guardo: no hay nada mas alla.
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

    /** Asocia una clave con una posicion. Redispersa si la tabla se llena. */
    public void insertar(String clave, int numeroRegistro) throws IOException {
        if ((cantidad + 1) > capacidad * FACTOR_CARGA_MAXIMO) {
            redispersar();
        }
        colocar(normalizar(clave), numeroRegistro);

        cantidad++;
        escribirCabecera();
    }

    /** Marca la clave como borrada dejando una lapida. */
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

    /** Vacia el indice por completo, para reconstruirlo desde el archivo de datos. */
    public void vaciar() throws IOException {
        formatear(CAPACIDAD_INICIAL);
    }

    public int getCapacidad() {
        return capacidad;
    }

    public int getCantidad() {
        return cantidad;
    }

    /** Porcentaje de ocupacion de la tabla, util para el reporte tecnico. */
    public double factorCarga() {
        return (capacidad == 0) ? 0 : (double) cantidad / capacidad;
    }

    @Override
    public void close() throws IOException {
        archivo.close();
    }

    // =======================================================================
    // INTERNOS
    // =======================================================================

    /**
     * Funcion de dispersion propia: acumula los caracteres multiplicando por 31
     * en cada paso.
     *
     * El 31 no es casual: es primo e impar, lo que evita que caracteres
     * distintos se anulen entre si y reparte mejor las claves. Es la misma idea
     * detras del hashCode de String en Java, escrita aqui a mano porque el
     * enunciado pide que el manejo del archivo sea codigo propio.
     */
    private int dispersar(String clave) {
        int valor = 7;
        for (int i = 0; i < clave.length(); i++) {
            valor = valor * 31 + clave.charAt(i);
        }
        return valor;
    }

    /**
     * Cubeta inicial de una clave.
     *
     * Se limpia el bit de signo antes del modulo porque el acumulado puede
     * desbordar a negativo, y un indice negativo reventaria la lectura.
     */
    private int cubetaDe(String clave) {
        return (dispersar(clave) & 0x7FFFFFFF) % capacidad;
    }

    private long posicionDe(int cubeta) {
        return TAM_CABECERA + (long) cubeta * tamCubeta;
    }

    /** Escribe la entrada en la primera cubeta disponible desde su posicion natural. */
    private void colocar(String clave, int numeroRegistro) throws IOException {
        int cubeta = cubetaDe(clave);

        for (int intento = 0; intento < capacidad; intento++) {
            int actual = (cubeta + intento) % capacidad;

            archivo.seek(posicionDe(actual));
            byte estado = archivo.readByte();

            // Una lapida se puede reutilizar: ya no hay nadie ahi.
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
     * Duplica la capacidad y recoloca todas las claves.
     *
     * Es costoso, pero ocurre pocas veces (cada vez que el archivo duplica su
     * tamano) y evita que las colisiones degraden la busqueda a O(n).
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

    /** Deja el archivo con la capacidad indicada y todas las cubetas libres. */
    private void formatear(int nuevaCapacidad) throws IOException {
        this.capacidad = nuevaCapacidad;
        this.cantidad = 0;

        archivo.setLength(0);
        escribirCabecera();

        // Se escribe la tabla completa de una vez, en memoria, en lugar de
        // hacer miles de escrituras sueltas al archivo.
        byte[] vacia = new byte[capacidad * tamCubeta];
        archivo.seek(TAM_CABECERA);
        archivo.write(vacia);
    }

    private void escribirCabecera() throws IOException {
        archivo.seek(0);
        archivo.writeInt(capacidad);
        archivo.writeInt(cantidad);
    }

    /** Primer numero primo mayor o igual a n; una capacidad prima reparte mejor. */
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
