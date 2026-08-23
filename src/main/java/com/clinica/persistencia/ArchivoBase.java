package com.clinica.persistencia;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clase base para todos los archivos de registros de longitud fija.
 *
 * Medicos, pacientes y citas guardan datos distintos, pero la MECANICA de
 * almacenamiento es identica en los tres casos: una cabecera, registros del
 * mismo tamano, borrado logico, lista de espacios libres e indice en memoria.
 * Todo eso vive aqui una sola vez; cada subclase solo describe como se ven sus
 * propios campos en bytes.
 *
 * ---------------------------------------------------------------------------
 * ESTRUCTURA DEL ARCHIVO
 * ---------------------------------------------------------------------------
 *
 *  [ CABECERA: 12 bytes ][ registro 0 ][ registro 1 ][ registro 2 ] ...
 *
 *  Cabecera:
 *      int version          (4)  version del formato
 *      int cantidadActivos  (4)  registros vivos
 *      int primerLibre      (4)  indice del primer hueco reutilizable, o -1
 *
 *  Todo registro, sea de la entidad que sea, empieza igual:
 *      byte estadoRegistro  (1)  1 = ocupado, 0 = borrado
 *      int  siguienteLibre  (4)  encadena los huecos cuando esta borrado
 *      ...luego los campos propios de la entidad, EMPEZANDO POR SU ID...
 *
 *  Que el identificador sea siempre el primer campo del cuerpo no es casual:
 *  permite reconstruir el indice leyendo unicamente los primeros bytes de cada
 *  registro, sin cargar el registro completo.
 *
 * ---------------------------------------------------------------------------
 * DECISIONES DE DISENO
 * ---------------------------------------------------------------------------
 *
 * 1. BORRADO LOGICO. Eliminar solo marca un byte. Un borrado fisico obligaria
 *    a desplazar todos los registros posteriores (costoso en disco) y a
 *    recalcular las posiciones ya conocidas.
 *
 * 2. LISTA DE ESPACIOS LIBRES. Los huecos se encadenan: la cabecera apunta al
 *    primero y cada hueco guarda el indice del siguiente en su campo
 *    siguienteLibre. Al insertar se reutiliza el hueco mas reciente (LIFO), de
 *    modo que el archivo no crece indefinidamente.
 *
 * 3. INDICE EN MEMORIA. Al abrir se recorre el archivo una sola vez y se arma
 *    un Map id -> numero de registro. Buscar por id cuesta entonces un unico
 *    seek en lugar de recorrer todo el archivo.
 *
 * @param <ID> tipo del identificador (UUID para medicos y citas, String para
 *             el numero de identificacion del paciente)
 * @param <T>  tipo de la entidad almacenada
 */
public abstract class ArchivoBase<ID, T> implements Closeable {

    /** Version del formato. Si cambia el layout de bytes, se sube este numero. */
    private static final int VERSION_FORMATO = 1;

    /** version + cantidadActivos + primerLibre */
    private static final int TAM_CABECERA = Integer.BYTES * 3;

    /** Bytes comunes al inicio de cualquier registro: estado + siguienteLibre. */
    protected static final int TAM_ENCABEZADO_REGISTRO = Byte.BYTES + Integer.BYTES;

    protected static final byte REGISTRO_LIBRE = 0;
    protected static final byte REGISTRO_OCUPADO = 1;

    /** Archivo abierto. Las subclases lo usan para leer y escribir sus campos. */
    protected final RandomAccessFile archivo;

    private final int tamRegistro;
    private final Map<ID, Integer> indice = new HashMap<>();

    private int cantidadActivos;
    private int primerLibre;

    /**
     * Abre (o crea) el archivo indicado.
     *
     * @param ruta        ruta del archivo, por ejemplo "datos/medicos.dat"
     * @param tamRegistro tamano en bytes de un registro completo, encabezado incluido
     */
    protected ArchivoBase(String ruta, int tamRegistro) throws IOException {
        this.tamRegistro = tamRegistro;

        File f = new File(ruta);
        File carpeta = f.getParentFile();
        if (carpeta != null && !carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta de datos: " + carpeta);
        }

        boolean esNuevo = !f.exists() || f.length() == 0;
        this.archivo = new RandomAccessFile(f, "rw");

        if (esNuevo) {
            this.cantidadActivos = 0;
            this.primerLibre = -1;
            escribirCabecera();
        } else {
            leerCabecera();
            reconstruirIndice();
        }
    }

    // =======================================================================
    // LO QUE CADA SUBCLASE DEBE DEFINIR
    // =======================================================================

    /**
     * Escribe los campos propios de la entidad en la posicion actual del
     * archivo. El primer campo escrito DEBE ser el identificador.
     */
    protected abstract void escribirCampos(T objeto) throws IOException;

    /** Lee los campos propios de la entidad desde la posicion actual. */
    protected abstract T leerCampos() throws IOException;

    /**
     * Lee unicamente el identificador desde la posicion actual, sin leer el
     * resto del registro. Se usa al reconstruir el indice.
     */
    protected abstract ID leerId() throws IOException;

    /** Devuelve el identificador de una entidad ya construida. */
    protected abstract ID idDe(T objeto);

    /**
     * Oportunidad de preparar la entidad antes de insertarla, por ejemplo
     * generarle un UUID. Por omision no hace nada.
     */
    protected void prepararParaInsertar(T objeto) {
        // Las subclases que lo necesiten sobrescriben este metodo.
    }

    // =======================================================================
    // OPERACIONES PUBLICAS
    // =======================================================================

    /**
     * Inserta una entidad nueva. Reutiliza un hueco libre si lo hay; si no,
     * agrega al final del archivo.
     *
     * @return el identificador con el que quedo guardada
     * @throws IllegalArgumentException si el identificador ya existe
     */
    public ID insertar(T objeto) throws IOException {
        prepararParaInsertar(objeto);
        ID id = idDe(objeto);

        if (indice.containsKey(id)) {
            throw new IllegalArgumentException("Ya existe un registro con el id " + id);
        }

        int numeroRegistro;
        if (primerLibre != -1) {
            // Se toma el primer hueco y la cabecera pasa a apuntar al siguiente.
            numeroRegistro = primerLibre;
            archivo.seek(posicionDe(numeroRegistro) + Byte.BYTES);
            primerLibre = archivo.readInt();
        } else {
            numeroRegistro = totalRegistros();
        }

        escribirRegistro(numeroRegistro, objeto);
        indice.put(id, numeroRegistro);
        cantidadActivos++;
        escribirCabecera();

        return id;
    }

    /**
     * Busca por identificador usando el indice en memoria: un solo seek, sin
     * importar cuantos registros tenga el archivo.
     *
     * @return la entidad, o null si no existe
     */
    public T buscarPorId(ID id) throws IOException {
        Integer numeroRegistro = indice.get(id);
        if (numeroRegistro == null) {
            return null;
        }
        return leerRegistro(numeroRegistro);
    }

    /** Indica si existe un registro vivo con ese identificador. */
    public boolean existe(ID id) {
        return indice.containsKey(id);
    }

    /** Todas las entidades vivas del archivo, saltando los huecos. */
    public List<T> listarTodos() throws IOException {
        List<T> resultado = new ArrayList<>();
        int total = totalRegistros();

        for (int i = 0; i < total; i++) {
            T objeto = leerRegistro(i);
            if (objeto != null) {
                resultado.add(objeto);
            }
        }
        return resultado;
    }

    /**
     * Sobrescribe una entidad existente. El identificador no cambia, asi que el
     * registro se reescribe en su misma posicion.
     *
     * @return true si existia y se actualizo
     */
    public boolean actualizar(T objeto) throws IOException {
        Integer numeroRegistro = indice.get(idDe(objeto));
        if (numeroRegistro == null) {
            return false;
        }
        escribirRegistro(numeroRegistro, objeto);
        return true;
    }

    /**
     * Eliminacion logica: marca el registro como libre y lo agrega al frente de
     * la lista de espacios libres para reutilizarlo despues.
     *
     * @return true si existia y se elimino
     */
    public boolean eliminar(ID id) throws IOException {
        Integer numeroRegistro = indice.remove(id);
        if (numeroRegistro == null) {
            return false;
        }

        archivo.seek(posicionDe(numeroRegistro));
        archivo.writeByte(REGISTRO_LIBRE);
        archivo.writeInt(primerLibre); // este hueco apunta al hueco anterior

        primerLibre = numeroRegistro;  // y la cabecera apunta a este
        cantidadActivos--;
        escribirCabecera();

        return true;
    }

    /** Cantidad de registros vivos. */
    public int cantidad() {
        return cantidadActivos;
    }

    /** Posiciones que ocupa el archivo, incluidos los huecos borrados. */
    public int totalRegistros() throws IOException {
        return (int) ((archivo.length() - TAM_CABECERA) / tamRegistro);
    }

    /** Bytes ocupados por huecos sin reutilizar. */
    public long bytesDesperdiciados() throws IOException {
        return (long) (totalRegistros() - cantidadActivos) * tamRegistro;
    }

    @Override
    public void close() throws IOException {
        archivo.close();
    }

    // =======================================================================
    // INTERNOS
    // =======================================================================

    /** Posicion en bytes donde empieza el registro numero {@code n}. */
    private long posicionDe(int n) {
        return TAM_CABECERA + (long) n * tamRegistro;
    }

    private void escribirCabecera() throws IOException {
        archivo.seek(0);
        archivo.writeInt(VERSION_FORMATO);
        archivo.writeInt(cantidadActivos);
        archivo.writeInt(primerLibre);
    }

    private void leerCabecera() throws IOException {
        archivo.seek(0);
        int version = archivo.readInt();
        if (version != VERSION_FORMATO) {
            throw new IOException("Version de archivo no soportada: " + version);
        }
        this.cantidadActivos = archivo.readInt();
        this.primerLibre = archivo.readInt();
    }

    /**
     * Recorre el archivo una vez al abrirlo y arma el indice, leyendo de cada
     * registro solo el byte de estado y el identificador.
     */
    private void reconstruirIndice() throws IOException {
        indice.clear();
        int total = totalRegistros();

        for (int i = 0; i < total; i++) {
            archivo.seek(posicionDe(i));
            byte estado = archivo.readByte();

            if (estado == REGISTRO_OCUPADO) {
                archivo.skipBytes(Integer.BYTES); // salta siguienteLibre
                indice.put(leerId(), i);
            }
        }
    }

    private void escribirRegistro(int numeroRegistro, T objeto) throws IOException {
        archivo.seek(posicionDe(numeroRegistro));
        archivo.writeByte(REGISTRO_OCUPADO);
        archivo.writeInt(-1); // siguienteLibre no aplica a un registro vivo
        escribirCampos(objeto);
    }

    /** @return la entidad, o null si esa posicion es un hueco borrado */
    private T leerRegistro(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro));

        if (archivo.readByte() != REGISTRO_OCUPADO) {
            return null;
        }
        archivo.skipBytes(Integer.BYTES); // siguienteLibre, no aplica

        return leerCampos();
    }
}
