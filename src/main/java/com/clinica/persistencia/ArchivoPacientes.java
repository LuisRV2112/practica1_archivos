package com.clinica.persistencia;

import com.clinica.modelo.Paciente;
import com.clinica.modelo.Sexo;
import com.clinica.modelo.TipoSangre;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Persistencia de pacientes.
 *
 * ---------------------------------------------------------------------------
 * REGISTRO DE PACIENTE: 335 bytes
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
 *
 * A diferencia de los medicos, el identificador es una cadena escrita por el
 * usuario y no un UUID generado. Que sea unico lo garantiza el indice de la
 * clase base, que rechaza insertar un id repetido.
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
            + Byte.BYTES;                                         // tipoSangre

    public ArchivoPacientes(String ruta) throws IOException {
        super(ruta, TAM_REGISTRO);
    }

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
        // El identificador va primero, igual que en todos los archivos.
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

        return new Paciente(identificacion, nombres, apellidos, fechaNacimiento,
                sexo, telefono, correo, tipoSangre);
    }
}
