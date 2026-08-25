package com.clinica.persistencia;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Base para archivos de registros de longitud fija.
 *
 * Aporta la mecánica (cabecera, borrado lógico, apilo de espacios libres,
 * cálculo de posiciones por aritmética de bytes).
 * No aporta la organización: eso lo decide cada subclase.
 *
 * Estructura del archivo:
 *   [ Cabecera 12 bytes ][ registro 0 ][ registro 1 ] ...
 *
 *   Cabecera: version(4) + cantidadActivos(4) + primerLibre(4)
 *   Registro: estadoRegistro(1) + siguienteLibre(4) + campos propios
 *
 * El identificador siempre es el primer campo del cuerpo para permitir
 * leer solo los primeros bytes y reconstruir índices.
 *
 * @param <ID> tipo del identificador
 * @param <T>  tipo de la entidad
 */
public abstract class ArchivoBase<ID, T> implements Closeable {

    /**
     * Versión del formato. Se sube al cambiar la disposición de bytes para que
     * el sistema rechace archivos viejos en vez de leerlos mal.
     *
     *   v2: distintas organizaciones de archivo y campo "activo" del paciente
     *   v3: enlaces de anillo en el registro de cita (multianillo)
     */
    private static final int VERSION_FORMATO = 3;

    private static final int TAM_CABECERA = Integer.BYTES * 3;

    /** Bytes comunes al inicio de cada registro: estado(1) + siguienteLibre(4). */
    protected static final int TAM_ENCABEZADO_REGISTRO = Byte.BYTES + Integer.BYTES;

    protected static final byte REGISTRO_LIBRE = 0;
    protected static final byte REGISTRO_OCUPADO = 1;

    /** Archivo de datos abierto. Lo usan las subclases para sus campos. */
    protected final RandomAccessFile archivo;

    private final int tamRegistro;
    private int cantidadActivos;
    private int primerLibre;

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
        }
    }

    /**
     * Llamar al final del constructor de la subclase. Prepara la organización
     * concreta (abrir índice, reconstruir anillos, etc.).
     *
     * No se hace aquí porque en Java el constructor de la base se ejecuta
     * ANTES de que existan los campos de la subclase.
     */
    protected final void iniciarOrganizacion() throws IOException {
        prepararIndice();
    }

    // Formato de campos (cada subclase define)

    /** Escribe campos propios. El PRIMERO debe ser el identificador. */
    protected abstract void escribirCampos(T objeto) throws IOException;

    /** Lee los campos propios desde la posicion actual. */
    protected abstract T leerCampos() throws IOException;

    /** Lee solo el identificador desde la posicion actual. */
    protected abstract ID leerId() throws IOException;

    /** Identificador de una entidad ya construida. */
    protected abstract ID idDe(T objeto);

    /** Oportunidad de preparar antes de insertar (generar UUID, etc.). */
    protected void prepararParaInsertar(T objeto) {
        // Las subclases que lo necesiten sobrescriben este metodo.
    }

    // Organización (cada subclase redefine según necesidad)

    /** Abre o reconstruye estructuras de apoyo. Por defecto no hay ninguna. */
    protected void prepararIndice() throws IOException {
        // Archivo secuencial: sin indice que preparar.
    }

    /**
     * Posición del registro que contiene ese id, o null.
     * Por defecto: barrido secuencial O(n). Solo lee estado + id de cada registro.
     */
    protected Integer localizar(ID id) throws IOException {
        int total = totalRegistros();

        for (int i = 0; i < total; i++) {
            ID leido = idEn(i);
            if (leido != null && leido.equals(id)) {
                return i;
            }
        }
        return null;
    }

    /** Avisa a la organizacion que se inserto un registro. */
    protected void indexarInsercion(ID id, int numeroRegistro) throws IOException {
        // Archivo secuencial: nada que indexar.
    }

    /** Avisa a la organizacion que se elimino un registro. */
    protected void indexarEliminacion(ID id, int numeroRegistro) throws IOException {
        // Archivo secuencial: nada que indexar.
    }

    /** Cierra las estructuras de apoyo. */
    protected void cerrarIndice() throws IOException {
        // Archivo secuencial: nada que cerrar.
    }

    /** Nombre de la organizacion, para el manual y los reportes. */
    public abstract String nombreOrganizacion();

    // Operaciones públicas

    /** Inserta una entidad. Reutiliza hueco libre si lo hay; si no, crece al final. */
    public ID insertar(T objeto) throws IOException {
        prepararParaInsertar(objeto);
        ID id = idDe(objeto);

        if (localizar(id) != null) {
            throw new IllegalArgumentException("Ya existe un registro con el id " + id);
        }

        int numeroRegistro = reservarRegistro();
        escribirRegistro(numeroRegistro, objeto);
        indexarInsercion(id, numeroRegistro);

        cantidadActivos++;
        escribirCabecera();

        return id;
    }

    /** Entidad por id, o null. */
    public T buscarPorId(ID id) throws IOException {
        Integer numeroRegistro = localizar(id);
        if (numeroRegistro == null) {
            return null;
        }
        return leerRegistro(numeroRegistro);
    }

    public boolean existe(ID id) throws IOException {
        return localizar(id) != null;
    }

    /**
     * Todas las entidades vivas. Siempre es secuencial: para leerlo todo,
     * barrer de principio a fin es lo más rápido. El índice sirve para llegar
     * a UN registro, no para leerlos todos.
     */
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
     * Sobrescribe una entidad existente en su misma posición. No borra ni
     * reinserta: el registro mide siempre lo mismo, se reescribe encima.
     */
    public boolean actualizar(T objeto) throws IOException {
        Integer numeroRegistro = localizar(idDe(objeto));
        if (numeroRegistro == null) {
            return false;
        }
        escribirRegistro(numeroRegistro, objeto);
        return true;
    }

    /** Eliminación lógica: marca libre y apila entre huecos. */
    public boolean eliminar(ID id) throws IOException {
        Integer numeroRegistro = localizar(id);
        if (numeroRegistro == null) {
            return false;
        }

        liberarRegistro(numeroRegistro);
        indexarEliminacion(id, numeroRegistro);

        cantidadActivos--;
        escribirCabecera();

        return true;
    }

    public int cantidad() {
        return cantidadActivos;
    }

    public int totalRegistros() throws IOException {
        return (int) ((archivo.length() - TAM_CABECERA) / tamRegistro);
    }

    public long bytesDesperdiciados() throws IOException {
        return (long) (totalRegistros() - cantidadActivos) * tamRegistro;
    }

    @Override
    public void close() throws IOException {
        cerrarIndice();
        archivo.close();
    }

    // Apilo de espacios libres (pila LIFO)

    /**
     * Toma una posición para un registro nuevo. La cabecera apunta a la cima
     * del apilo; cada hueco guarda en siguienteLibre el de abajo. Pop O(1).
     */
    private int reservarRegistro() throws IOException {
        if (primerLibre == -1) {
            return totalRegistros(); // no hay huecos: crece el archivo
        }

        int reutilizado = primerLibre;
        archivo.seek(posicionDe(reutilizado) + Byte.BYTES);
        primerLibre = archivo.readInt(); // pop
        return reutilizado;
    }

    /** Push: registro liberado se vuelve la nueva cima del apilo. */
    private void liberarRegistro(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro));
        archivo.writeByte(REGISTRO_LIBRE);
        archivo.writeInt(primerLibre);
        primerLibre = numeroRegistro;
    }

    // Lectura y escritura de bajo nivel

    /**
     * Posición en bytes del registro n. La clave de registros de longitud fija:
     * se llega con un seek() por multiplicación.
     */
    protected final long posicionDe(int n) {
        return TAM_CABECERA + (long) n * tamRegistro;
    }

    /** Identificador en esa posición, o null si es hueco. */
    protected final ID idEn(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro));

        if (archivo.readByte() != REGISTRO_OCUPADO) {
            return null;
        }
        archivo.skipBytes(Integer.BYTES); // siguienteLibre
        return leerId();
    }

    protected final void escribirRegistro(int numeroRegistro, T objeto) throws IOException {
        archivo.seek(posicionDe(numeroRegistro));
        archivo.writeByte(REGISTRO_OCUPADO);
        archivo.writeInt(-1); // siguienteLibre no aplica a registro vivo
        escribirCampos(objeto);
    }

    /** La entidad, o null si es hueco. */
    protected final T leerRegistro(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro));

        if (archivo.readByte() != REGISTRO_OCUPADO) {
            return null;
        }
        archivo.skipBytes(Integer.BYTES);
        return leerCampos();
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
            throw new IOException("El archivo es de la version " + version
                    + " y el sistema usa la " + VERSION_FORMATO
                    + ". Borre la carpeta 'datos' para empezar de nuevo.");
        }
        this.cantidadActivos = archivo.readInt();
        this.primerLibre = archivo.readInt();
    }
}