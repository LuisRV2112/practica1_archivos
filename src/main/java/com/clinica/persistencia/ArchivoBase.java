package com.clinica.persistencia;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Base comun a todos los archivos de registros de longitud fija.
 *
 * ---------------------------------------------------------------------------
 * QUE APORTA ESTA CLASE
 * ---------------------------------------------------------------------------
 * La MECANICA de almacenamiento, que es identica para todas las entidades:
 * cabecera, registros del mismo tamano, borrado logico, apilo de espacios
 * libres y calculo de posiciones por aritmetica de bytes.
 *
 * ---------------------------------------------------------------------------
 * QUE NO APORTA (y es lo importante)
 * ---------------------------------------------------------------------------
 * La ORGANIZACION, es decir, COMO se localiza un registro a partir de su
 * identificador. Eso lo decide cada subclase segun como se consulte esa
 * entidad, y es lo que diferencia una estructura de archivo de otra:
 *
 *   - Pacientes -> archivo DIRECTO: un indice hash externo calcula la
 *                  posicion. Busqueda O(1).
 *   - Medicos   -> archivo SECUENCIAL INDEXADO: un archivo de indice ordenado
 *                  se recorre con busqueda binaria. Busqueda O(log n).
 *   - Citas     -> archivo MULTIANILLO: cada registro se encadena con los
 *                  demas de su mismo medico y de su mismo paciente.
 *   - Bitacora  -> archivo SECUENCIAL puro: solo se anexa y se lee entero,
 *                  asi que usa la implementacion por omision (barrido O(n)).
 *
 * Por omision, localizar() hace un barrido secuencial. Una subclase que no
 * sobrescriba nada obtiene un archivo secuencial, que es justo lo que conviene
 * a la bitacora.
 *
 * ---------------------------------------------------------------------------
 * ESTRUCTURA DEL ARCHIVO DE DATOS
 * ---------------------------------------------------------------------------
 *
 *  [ CABECERA: 12 bytes ][ registro 0 ][ registro 1 ][ registro 2 ] ...
 *
 *  Cabecera:
 *      int version          (4)  version del formato
 *      int cantidadActivos  (4)  registros vivos
 *      int primerLibre      (4)  cima del apilo de huecos, o -1
 *
 *  Todo registro empieza igual:
 *      byte estadoRegistro  (1)  1 = ocupado, 0 = borrado
 *      int  siguienteLibre  (4)  enlaza el apilo cuando el registro esta libre
 *      ...luego los campos propios, EMPEZANDO POR EL IDENTIFICADOR...
 *
 * Que el identificador sea siempre el primer campo del cuerpo permite leer solo
 * los primeros bytes de un registro para saber a quien pertenece, sin cargarlo
 * completo. De eso se aprovechan tanto el barrido secuencial como la
 * reconstruccion de los indices.
 *
 * @param <ID> tipo del identificador
 * @param <T>  tipo de la entidad almacenada
 */
public abstract class ArchivoBase<ID, T> implements Closeable {

    /**
     * Version del formato. Se sube cada vez que cambia la disposicion de los
     * bytes, para que el sistema RECHACE un archivo viejo en lugar de leerlo
     * mal y mostrar datos corruptos.
     *
     *   v2: distintas organizaciones de archivo y campo "activo" del paciente
     *   v3: enlaces de anillo en el registro de cita (multianillo)
     */
    private static final int VERSION_FORMATO = 3;

    private static final int TAM_CABECERA = Integer.BYTES * 3;

    /** Bytes comunes al inicio de cualquier registro: estado + siguienteLibre. */
    protected static final int TAM_ENCABEZADO_REGISTRO = Byte.BYTES + Integer.BYTES;

    protected static final byte REGISTRO_LIBRE = 0;
    protected static final byte REGISTRO_OCUPADO = 1;

    /** Archivo de datos abierto. Las subclases lo usan para sus campos. */
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
     * Debe llamarse al final del constructor de la subclase, cuando sus propios
     * campos ya estan inicializados. Prepara la organizacion concreta (abrir o
     * reconstruir el indice, rearmar los anillos, etc.).
     *
     * No se hace desde el constructor de esta clase a proposito: en Java, el
     * constructor de la clase base se ejecuta ANTES de que se inicialicen los
     * campos de la subclase, asi que un indice creado aqui aparecería como null
     * al usarlo.
     */
    protected final void iniciarOrganizacion() throws IOException {
        prepararIndice();
    }

    // =======================================================================
    // LO QUE CADA SUBCLASE DEBE DEFINIR: EL FORMATO DE SUS CAMPOS
    // =======================================================================

    /** Escribe los campos propios. El PRIMERO debe ser el identificador. */
    protected abstract void escribirCampos(T objeto) throws IOException;

    /** Lee los campos propios desde la posicion actual. */
    protected abstract T leerCampos() throws IOException;

    /** Lee solo el identificador desde la posicion actual. */
    protected abstract ID leerId() throws IOException;

    /** Identificador de una entidad ya construida. */
    protected abstract ID idDe(T objeto);

    /** Oportunidad de preparar la entidad antes de insertar (generar un UUID). */
    protected void prepararParaInsertar(T objeto) {
        // Las subclases que lo necesiten sobrescriben este metodo.
    }

    // =======================================================================
    // LO QUE CADA SUBCLASE PUEDE REDEFINIR: LA ORGANIZACION
    // =======================================================================

    /** Abre o reconstruye las estructuras de apoyo. Por omision no hay ninguna. */
    protected void prepararIndice() throws IOException {
        // Archivo secuencial: sin indice que preparar.
    }

    /**
     * Devuelve el numero de registro donde vive ese identificador, o null.
     *
     * Implementacion por omision: BARRIDO SECUENCIAL, O(n). Se lee unicamente
     * el estado y el identificador de cada registro, no el registro completo.
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

    // =======================================================================
    // OPERACIONES PUBLICAS
    // =======================================================================

    /**
     * Inserta una entidad nueva. Reutiliza el hueco mas reciente si lo hay; si
     * no, agrega al final del archivo.
     */
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

    /** @return la entidad, o null si no existe */
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
     * Todas las entidades vivas, saltando los huecos.
     *
     * Este recorrido SIEMPRE es secuencial, sin importar la organizacion: para
     * leerlo todo, recorrer el archivo de principio a fin es lo mas rapido que
     * hay. El indice sirve para llegar a UN registro, no para leerlos todos.
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
     * Sobrescribe una entidad existente EN SU MISMA POSICION.
     *
     * No se borra y vuelve a insertar: eso desordenaria el archivo y dejaria un
     * hueco sin motivo. El registro mide siempre lo mismo, asi que se puede
     * reescribir encima sin mover nada.
     */
    public boolean actualizar(T objeto) throws IOException {
        Integer numeroRegistro = localizar(idDe(objeto));
        if (numeroRegistro == null) {
            return false;
        }
        escribirRegistro(numeroRegistro, objeto);
        return true;
    }

    /** Eliminacion logica: marca el registro libre y lo apila entre los huecos. */
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

    // =======================================================================
    // APILO DE ESPACIOS LIBRES
    // =======================================================================

    /**
     * Toma una posicion para un registro nuevo.
     *
     * Los huecos forman un APILO (pila LIFO): la cabecera apunta a la cima y
     * cada hueco guarda, en su campo siguienteLibre, el hueco de abajo. Sacar
     * un hueco es un pop y liberar uno es un push, ambos O(1) y sin recorrer
     * nada. Es la misma estructura LIFO que se ve en clase, aplicada a la
     * administracion del espacio del archivo.
     */
    private int reservarRegistro() throws IOException {
        if (primerLibre == -1) {
            return totalRegistros(); // no hay huecos: crece el archivo
        }

        int reutilizado = primerLibre;
        archivo.seek(posicionDe(reutilizado) + Byte.BYTES);
        primerLibre = archivo.readInt(); // pop: la cima pasa a ser el siguiente
        return reutilizado;
    }

    /** Push: el registro liberado se vuelve la nueva cima del apilo. */
    private void liberarRegistro(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro));
        archivo.writeByte(REGISTRO_LIBRE);
        archivo.writeInt(primerLibre);
        primerLibre = numeroRegistro;
    }

    // =======================================================================
    // LECTURA Y ESCRITURA DE BAJO NIVEL
    // =======================================================================

    /**
     * Posicion en bytes donde empieza el registro numero n.
     *
     * Esta multiplicacion es la razon de ser de los registros de longitud fija:
     * se llega a cualquier registro con un unico seek, sin leer los anteriores.
     */
    protected final long posicionDe(int n) {
        return TAM_CABECERA + (long) n * tamRegistro;
    }

    /** Identificador guardado en esa posicion, o null si es un hueco. */
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
        archivo.writeInt(-1); // siguienteLibre no aplica a un registro vivo
        escribirCampos(objeto);
    }

    /** @return la entidad, o null si esa posicion es un hueco */
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