package com.clinica.persistencia;

import com.clinica.modelo.Paciente;
import com.clinica.modelo.Sexo;
import com.clinica.modelo.TipoSangre;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Persistencia de pacientes: archivo directo (hash O(1)).
 * La posición se calcula con función de dispersión sobre la identificación.
 *
 * Registro de 336 bytes:
 *   estadoRegistro(1) + siguienteLibre(4) + identificacion(30) + nombres(80) +
 *   apellidos(80) + fechaNacimiento(8) + sexo(1) + telefono(30) +
 *   correo(100) + tipoSangre(1) + activo(1)
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

    // Organización: archivo directo (hash)

    /**
     * Verifica el índice contra el archivo y lo reconstruye si no coincide.
     * El índice es información derivada: siempre se puede recalcular.
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

    /** Posición calculada con hash: O(1). */
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

    /** Capacidad actual de la tabla hash; para reporte técnico. */
    public int capacidadIndice() {
        return indice.getCapacidad();
    }

    /** Factor de ocupación de la tabla hash (0.0 - 1.0). */
    public double factorCargaIndice() {
        return indice.factorCarga();
    }

    // Formato de campos

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
        UtilArchivo.escribirCadena(archivo, paciente.getIdentificacion(), LARGO_IDENTIFICACION);

        UtilArchivo.escribirCadena(archivo, paciente.getNombres(), LARGO_NOMBRES);
        UtilArchivo.escribirCadena(archivo, paciente.getApellidos(), LARGO_APELLIDOS);

        UtilArchivo.escribirFecha(archivo, paciente.getFechaNacimiento());

        archivo.writeByte(paciente.getSexo() == null ? 0 : paciente.getSexo().getCodigo());

        UtilArchivo.escribirCadena(archivo, paciente.getTelefono(), LARGO_TELEFONO);
        UtilArchivo.escribirCadena(archivo, paciente.getCorreo(), LARGO_CORREO);

        archivo.writeByte(paciente.getTipoSangre() == null
                ? 0 : paciente.getTipoSangre().getCodigo());

        archivo.writeByte(paciente.isActivo() ? 1 : 0);
    }

    @Override
    protected Paciente leerCampos() throws IOException {
        // Orden de lectura = orden de escritura en escribirCampos.
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
