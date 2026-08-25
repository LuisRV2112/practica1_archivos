package com.clinica.persistencia;

import com.clinica.modelo.Paciente;
import com.clinica.modelo.Sexo;
import com.clinica.modelo.TipoSangre;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Persistencia de pacientes: ARCHIVO DIRECTO (acceso por hash).
 *
 * ---------------------------------------------------------------------------
 * POR QUE ESTA ORGANIZACION
 * ---------------------------------------------------------------------------
 * Al paciente casi siempre se le busca por su numero de identificacion, y se le
 * busca EXACTO: llega a la clinica, da su numero y hay que traer su expediente.
 * No se consulta "los pacientes entre el numero X y el Y", ni hace falta
 * recorrerlos en orden de identificacion.
 *
 * Ese patron —clave exacta, muchas consultas, sin necesidad de orden— es
 * exactamente para lo que sirve un archivo directo: la posicion del registro se
 * CALCULA a partir de la clave mediante una funcion de dispersion, y se llega a
 * el en una sola lectura. Buscar un paciente cuesta O(1) sin importar si hay
 * cien o cien mil.
 *
 * El indice hash vive en un archivo aparte (pacientes.hash); ver
 * {@link IndiceHash} para el detalle de colisiones, lapidas y redispersion.
 *
 * ---------------------------------------------------------------------------
 * REGISTRO DE PACIENTE: 336 bytes
 * ---------------------------------------------------------------------------
 *      byte estadoRegistro     (1)    de la clase base
 *      int  siguienteLibre     (4)    de la clase base
 *      char[15] identificacion (30)   numero de identificacion personal
 *      char[40] nombres        (80)
 *      char[40] apellidos      (80)
 *      long fechaNacimiento    (8)    dias desde 1970-01-01
 *      byte sexo               (1)    codigo del enum Sexo
 *      char[15] telefono       (30)
 *      char[50] correo         (100)
 *      byte tipoSangre         (1)    codigo del enum TipoSangre
 *      byte activo             (1)    estado del paciente en la clinica
 *
 * El identificador no es un UUID generado sino el numero de identidad que trae
 * la persona. Su unicidad la garantiza el indice, que rechaza una clave
 * repetida antes de escribir nada.
 */
public class ArchivoPacientes extends ArchivoBase<String, Paciente> {

    public static final int LARGO_IDENTIFICACION = 15;
    public static final int LARGO_NOMBRES = 40;
    public static final int LARGO_APELLIDOS = 40;
    public static final int LARGO_TELEFONO = 15;
    public static final int LARGO_CORREO = 50;

    private static final int TAM_REGISTRO =
              TAM_ENCABEZADO_REGISTRO
            + UtilArchivo.bytesDeCadena(LARGO_IDENTIFICACION)
            + UtilArchivo.bytesDeCadena(LARGO_NOMBRES)
            + UtilArchivo.bytesDeCadena(LARGO_APELLIDOS)
            + Long.BYTES                                          // fechaNacimiento
            + Byte.BYTES                                          // sexo
            + UtilArchivo.bytesDeCadena(LARGO_TELEFONO)
            + UtilArchivo.bytesDeCadena(LARGO_CORREO)
            + Byte.BYTES                                          // tipoSangre
            + Byte.BYTES;                                         // activo

    /** Tabla hash en archivo: identificacion -> numero de registro. */
    private final IndiceHash indice;

    public ArchivoPacientes(String ruta) throws IOException {
        super(ruta, TAM_REGISTRO);
        this.indice = new IndiceHash(rutaDelIndice(ruta), LARGO_IDENTIFICACION);
        iniciarOrganizacion();
    }

    /** El indice acompana al archivo de datos: pacientes.dat -> pacientes.hash */
    private static String rutaDelIndice(String rutaDatos) {
        int punto = rutaDatos.lastIndexOf('.');
        String base = (punto < 0) ? rutaDatos : rutaDatos.substring(0, punto);
        return base + ".hash";
    }

    @Override
    public String nombreOrganizacion() {
        return "Directo (acceso por hash)";
    }

    // =======================================================================
    // ORGANIZACION: ARCHIVO DIRECTO
    // =======================================================================

    /**
     * Verifica que el indice concuerde con el archivo de datos y, si no, lo
     * reconstruye recorriendo los registros una sola vez.
     *
     * Un indice es informacion DERIVADA: siempre se puede volver a calcular a
     * partir de los datos. Por eso, si el programa se cerro de golpe y el
     * indice quedo a medias, no se pierde nada: se rehace.
     */
    @Override
    protected void prepararIndice() throws IOException {
        if (indice.getCantidad() == cantidad()) {
            return; // el indice esta al dia
        }

        indice.vaciar();
        int total = totalRegistros();

        for (int i = 0; i < total; i++) {
            String identificacion = idEn(i);
            if (identificacion != null) {
                indice.insertar(identificacion, i);
            }
        }
    }

    /** Se calcula la posicion con la funcion de dispersion: O(1). */
    @Override
    protected Integer localizar(String identificacion) throws IOException {
        return indice.buscar(identificacion);
    }

    @Override
    protected void indexarInsercion(String identificacion, int numeroRegistro)
            throws IOException {
        indice.insertar(identificacion, numeroRegistro);
    }

    @Override
    protected void indexarEliminacion(String identificacion, int numeroRegistro)
            throws IOException {
        indice.eliminar(identificacion);
    }

    @Override
    protected void cerrarIndice() throws IOException {
        indice.close();
    }

    /** Capacidad actual de la tabla hash, para el reporte tecnico. */
    public int capacidadIndice() {
        return indice.getCapacidad();
    }

    /** Ocupacion de la tabla hash, entre 0 y 1. */
    public double factorCargaIndice() {
        return indice.factorCarga();
    }

    // =======================================================================
    // FORMATO DE LOS CAMPOS
    // =======================================================================

    @Override
    protected String idDe(Paciente paciente) {
        return paciente.getIdentificacion();
    }

    @Override
    protected String leerId() throws IOException {
        return UtilArchivo.leerCadena(archivo, LARGO_IDENTIFICACION);
    }

    @Override
    protected void escribirCampos(Paciente paciente) throws IOException {
        // El identificador va primero, como en todos los archivos.
        UtilArchivo.escribirCadena(archivo, paciente.getIdentificacion(), LARGO_IDENTIFICACION);

        UtilArchivo.escribirCadena(archivo, paciente.getNombres(), LARGO_NOMBRES);
        UtilArchivo.escribirCadena(archivo, paciente.getApellidos(), LARGO_APELLIDOS);

        UtilArchivo.escribirFecha(archivo, paciente.getFechaNacimiento());

        // Se guarda el codigo del enum, no su ordinal: ver comentario en Sexo.
        archivo.writeByte(paciente.getSexo() == null ? 0 : paciente.getSexo().getCodigo());

        UtilArchivo.escribirCadena(archivo, paciente.getTelefono(), LARGO_TELEFONO);
        UtilArchivo.escribirCadena(archivo, paciente.getCorreo(), LARGO_CORREO);

        archivo.writeByte(paciente.getTipoSangre() == null
                ? 0 : paciente.getTipoSangre().getCodigo());

        archivo.writeByte(paciente.isActivo() ? 1 : 0);
    }

    @Override
    protected Paciente leerCampos() throws IOException {
        // El orden de lectura debe ser EXACTAMENTE el mismo de escribirCampos.
        String identificacion = leerId();

        String nombres = UtilArchivo.leerCadena(archivo, LARGO_NOMBRES);
        String apellidos = UtilArchivo.leerCadena(archivo, LARGO_APELLIDOS);

        LocalDate fechaNacimiento = UtilArchivo.leerFecha(archivo);

        byte codigoSexo = archivo.readByte();
        Sexo sexo = (codigoSexo == 0) ? null : Sexo.porCodigo(codigoSexo);

        String telefono = UtilArchivo.leerCadena(archivo, LARGO_TELEFONO);
        String correo = UtilArchivo.leerCadena(archivo, LARGO_CORREO);

        byte codigoSangre = archivo.readByte();
        TipoSangre tipoSangre = (codigoSangre == 0) ? null : TipoSangre.porCodigo(codigoSangre);

        boolean activo = archivo.readByte() == 1;

        return new Paciente(identificacion, nombres, apellidos, fechaNacimiento,
                sexo, telefono, correo, tipoSangre, activo);
    }
}
