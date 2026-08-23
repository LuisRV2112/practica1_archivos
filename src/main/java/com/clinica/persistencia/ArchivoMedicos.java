package com.clinica.persistencia;

import com.clinica.modelo.Medico;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistencia de medicos sobre un archivo binario de registros de longitud fija.
 *
 * ---------------------------------------------------------------------------
 * ESTRUCTURA DEL ARCHIVO
 * ---------------------------------------------------------------------------
 *
 *  [ CABECERA: 12 bytes ][ registro 0 ][ registro 1 ][ registro 2 ] ...
 *
 *  Cabecera:
 *      int version          (4 bytes)  version del formato del archivo
 *      int cantidadActivos  (4 bytes)  cuantos registros estan vivos
 *      int primerLibre      (4 bytes)  indice del primer hueco reutilizable, o -1
 *
 *  Registro (380 bytes):
 *      byte estadoRegistro  (1)   1 = ocupado, 0 = borrado / libre
 *      int  siguienteLibre  (4)   solo significativo si el registro esta libre:
 *                                 encadena la lista de huecos
 *      long uuidMsb         (8)   bits altos del UUID
 *      long uuidLsb         (8)   bits bajos del UUID
 *      char[40] nombres     (80)
 *      char[40] apellidos   (80)
 *      char[30] especialidad(60)
 *      char[15] telefono    (30)
 *      char[50] correo      (100)
 *      int  horaInicio      (4)   segundos desde medianoche
 *      int  horaFin         (4)
 *      byte activo          (1)   estado del medico en la clinica
 *
 * ---------------------------------------------------------------------------
 * DECISIONES DE DISENO
 * ---------------------------------------------------------------------------
 *
 * 1. BORRADO LOGICO. Eliminar no mueve bytes ni reescribe el archivo: solo
 *    marca el registro como libre. Borrar fisicamente obligaria a desplazar
 *    todos los registros posteriores, lo que es O(n) en disco y ademas
 *    invalidaria las posiciones ya calculadas.
 *
 * 2. LISTA DE ESPACIOS LIBRES. Los huecos se encadenan entre si: la cabecera
 *    apunta al primer hueco, y cada hueco guarda en su campo siguienteLibre el
 *    indice del siguiente. Al insertar se reutiliza el primer hueco (LIFO), de
 *    modo que el archivo no crece indefinidamente. Si no hay huecos, el nuevo
 *    registro se agrega al final.
 *
 * 3. INDICE EN MEMORIA. Al abrir el archivo se recorre una sola vez y se arma
 *    un HashMap UUID -> numero de registro. Con eso, buscar un medico por su id
 *    es O(1) y cuesta un unico seek, en lugar de recorrer todo el archivo.
 *    El indice se mantiene sincronizado en cada insercion y eliminacion.
 *
 * 4. UUID COMO DOS LONG. Un UUID guardado como texto ocuparia 36 caracteres
 *    (72 bytes). Guardado como sus dos mitades de 64 bits ocupa 16 bytes y se
 *    reconstruye exacto con new UUID(msb, lsb).
 */
public class ArchivoMedicos implements Closeable {

    // ----- Longitudes de los campos de texto, en caracteres -----
    // Son publicas a proposito: la capa de servicio las usa para validar y
    // rechazar un texto demasiado largo, en lugar de dejar que se recorte en
    // silencio al escribirlo.
    public static final int LARGO_NOMBRES = 40;
    public static final int LARGO_APELLIDOS = 40;
    public static final int LARGO_ESPECIALIDAD = 30;
    public static final int LARGO_TELEFONO = 15;
    public static final int LARGO_CORREO = 50;

    // ----- Cabecera -----
    private static final int VERSION_FORMATO = 1;
    private static final int TAM_CABECERA = Integer.BYTES * 3; // 12 bytes

    // ----- Marcas del byte de estado del registro -----
    private static final byte REGISTRO_LIBRE = 0;
    private static final byte REGISTRO_OCUPADO = 1;

    /**
     * Tamano de un registro. Se calcula sumando los campos en lugar de escribir
     * un numero magico: si manana se agranda un campo, la constante se ajusta sola.
     */
    private static final int TAM_REGISTRO =
              Byte.BYTES                                          // estadoRegistro
            + Integer.BYTES                                       // siguienteLibre
            + Long.BYTES * 2                                      // uuid
            + UtilArchivo.bytesDeCadena(LARGO_NOMBRES)
            + UtilArchivo.bytesDeCadena(LARGO_APELLIDOS)
            + UtilArchivo.bytesDeCadena(LARGO_ESPECIALIDAD)
            + UtilArchivo.bytesDeCadena(LARGO_TELEFONO)
            + UtilArchivo.bytesDeCadena(LARGO_CORREO)
            + Integer.BYTES                                       // horaInicio
            + Integer.BYTES                                       // horaFin
            + Byte.BYTES;                                         // activo

    private final RandomAccessFile archivo;

    /** Indice en memoria: id del medico -> numero de registro dentro del archivo. */
    private final Map<UUID, Integer> indice = new HashMap<>();

    private int cantidadActivos;
    private int primerLibre;

    /**
     * Abre el archivo indicado. Si no existe, lo crea con una cabecera vacia.
     *
     * @param ruta ruta del archivo de datos (por ejemplo "datos/medicos.dat")
     */
    public ArchivoMedicos(String ruta) throws IOException {
        File f = new File(ruta);

        // Crea la carpeta contenedora si hiciera falta.
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
    // OPERACIONES PUBLICAS
    // =======================================================================

    /**
     * Inserta un medico nuevo. Si aun no tiene id, se le genera un UUID.
     * Reutiliza un hueco libre si lo hay; si no, agrega al final del archivo.
     *
     * @return el id asignado al medico
     * @throws IllegalArgumentException si el id ya existe en el archivo
     */
    public UUID insertar(Medico medico) throws IOException {
        if (medico.getId() == null) {
            medico.setId(UUID.randomUUID());
        }
        if (indice.containsKey(medico.getId())) {
            throw new IllegalArgumentException("Ya existe un medico con el id " + medico.getId());
        }

        int numeroRegistro;
        if (primerLibre != -1) {
            // Se reutiliza el primer hueco y la cabecera pasa a apuntar al siguiente.
            numeroRegistro = primerLibre;
            archivo.seek(posicionDe(numeroRegistro) + Byte.BYTES);
            primerLibre = archivo.readInt();
        } else {
            numeroRegistro = totalRegistros();
        }

        escribirRegistro(numeroRegistro, medico);
        indice.put(medico.getId(), numeroRegistro);
        cantidadActivos++;
        escribirCabecera();

        return medico.getId();
    }

    /**
     * Busca un medico por su id usando el indice en memoria.
     * Cuesta un unico seek, sin importar cuantos registros tenga el archivo.
     *
     * @return el medico, o null si no existe
     */
    public Medico buscarPorId(UUID id) throws IOException {
        Integer numeroRegistro = indice.get(id);
        if (numeroRegistro == null) {
            return null;
        }
        return leerRegistro(numeroRegistro);
    }

    /** Devuelve todos los medicos vivos del archivo, saltando los huecos. */
    public List<Medico> listarTodos() throws IOException {
        List<Medico> resultado = new ArrayList<>();
        int total = totalRegistros();

        for (int i = 0; i < total; i++) {
            Medico medico = leerRegistro(i);
            if (medico != null) {
                resultado.add(medico);
            }
        }
        return resultado;
    }

    /**
     * Sobrescribe los datos de un medico existente. El id no cambia, asi que el
     * registro se reescribe en su misma posicion.
     *
     * @return true si el medico existia y se actualizo
     */
    public boolean actualizar(Medico medico) throws IOException {
        Integer numeroRegistro = indice.get(medico.getId());
        if (numeroRegistro == null) {
            return false;
        }
        escribirRegistro(numeroRegistro, medico);
        return true;
    }

    /**
     * Elimina logicamente un medico: marca su registro como libre y lo agrega
     * al frente de la lista de espacios libres para poder reutilizarlo.
     *
     * @return true si el medico existia y se elimino
     */
    public boolean eliminar(UUID id) throws IOException {
        Integer numeroRegistro = indice.remove(id);
        if (numeroRegistro == null) {
            return false;
        }

        archivo.seek(posicionDe(numeroRegistro));
        archivo.writeByte(REGISTRO_LIBRE);
        archivo.writeInt(primerLibre); // el hueco apunta al hueco anterior

        primerLibre = numeroRegistro;  // y la cabecera apunta a este
        cantidadActivos--;
        escribirCabecera();

        return true;
    }

    /** Cantidad de medicos vivos en el archivo. */
    public int cantidad() {
        return cantidadActivos;
    }

    /** Cuantas posiciones tiene el archivo, incluyendo huecos borrados. */
    public int totalRegistros() throws IOException {
        return (int) ((archivo.length() - TAM_CABECERA) / TAM_REGISTRO);
    }

    /**
     * Cuantos bytes se estan desperdiciando en huecos. Sirve para el reporte de
     * mantenimiento y para justificar el borrado logico ante el revisor.
     */
    public long bytesDesperdiciados() throws IOException {
        return (long) (totalRegistros() - cantidadActivos) * TAM_REGISTRO;
    }

    @Override
    public void close() throws IOException {
        archivo.close();
    }

    // =======================================================================
    // LECTURA Y ESCRITURA DE BAJO NIVEL
    // =======================================================================

    /** Posicion en bytes donde empieza el registro numero {@code n}. */
    private long posicionDe(int n) {
        return TAM_CABECERA + (long) n * TAM_REGISTRO;
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
     * Recorre el archivo una sola vez al abrirlo y arma el indice en memoria.
     * Solo lee el estado y el UUID de cada registro, no el registro completo.
     */
    private void reconstruirIndice() throws IOException {
        indice.clear();
        int total = totalRegistros();

        for (int i = 0; i < total; i++) {
            archivo.seek(posicionDe(i));
            byte estado = archivo.readByte();

            if (estado == REGISTRO_OCUPADO) {
                archivo.skipBytes(Integer.BYTES); // salta siguienteLibre
                long msb = archivo.readLong();
                long lsb = archivo.readLong();
                indice.put(new UUID(msb, lsb), i);
            }
        }
    }

    /** Escribe el registro completo en la posicion indicada. */
    private void escribirRegistro(int numeroRegistro, Medico medico) throws IOException {
        archivo.seek(posicionDe(numeroRegistro));

        archivo.writeByte(REGISTRO_OCUPADO);
        archivo.writeInt(-1); // el campo siguienteLibre no aplica a registros vivos

        archivo.writeLong(medico.getId().getMostSignificantBits());
        archivo.writeLong(medico.getId().getLeastSignificantBits());

        UtilArchivo.escribirCadena(archivo, medico.getNombres(), LARGO_NOMBRES);
        UtilArchivo.escribirCadena(archivo, medico.getApellidos(), LARGO_APELLIDOS);
        UtilArchivo.escribirCadena(archivo, medico.getEspecialidad(), LARGO_ESPECIALIDAD);
        UtilArchivo.escribirCadena(archivo, medico.getTelefono(), LARGO_TELEFONO);
        UtilArchivo.escribirCadena(archivo, medico.getCorreo(), LARGO_CORREO);

        UtilArchivo.escribirHora(archivo, medico.getHoraInicio());
        UtilArchivo.escribirHora(archivo, medico.getHoraFin());

        archivo.writeByte(medico.isActivo() ? 1 : 0);
    }

    /**
     * Lee el registro indicado.
     *
     * @return el medico, o null si esa posicion es un hueco borrado
     */
    private Medico leerRegistro(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro));

        byte estado = archivo.readByte();
        if (estado != REGISTRO_OCUPADO) {
            return null;
        }
        archivo.skipBytes(Integer.BYTES); // siguienteLibre, no aplica

        long msb = archivo.readLong();
        long lsb = archivo.readLong();

        String nombres = UtilArchivo.leerCadena(archivo, LARGO_NOMBRES);
        String apellidos = UtilArchivo.leerCadena(archivo, LARGO_APELLIDOS);
        String especialidad = UtilArchivo.leerCadena(archivo, LARGO_ESPECIALIDAD);
        String telefono = UtilArchivo.leerCadena(archivo, LARGO_TELEFONO);
        String correo = UtilArchivo.leerCadena(archivo, LARGO_CORREO);

        LocalTime horaInicio = UtilArchivo.leerHora(archivo);
        LocalTime horaFin = UtilArchivo.leerHora(archivo);

        boolean activo = archivo.readByte() == 1;

        return new Medico(new UUID(msb, lsb), nombres, apellidos, especialidad,
                telefono, correo, horaInicio, horaFin, activo);
    }
}
