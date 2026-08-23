package com.clinica.persistencia;

import com.clinica.modelo.Medico;

import java.io.IOException;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Persistencia de medicos.
 *
 * Toda la mecanica de almacenamiento (cabecera, borrado logico, lista de
 * espacios libres, indice) la aporta {@link ArchivoBase}. Aqui solo se describe
 * como se ve un medico convertido a bytes.
 *
 * ---------------------------------------------------------------------------
 * REGISTRO DE MEDICO: 380 bytes
 * ---------------------------------------------------------------------------
 *      byte estadoRegistro  (1)    de la clase base
 *      int  siguienteLibre  (4)    de la clase base
 *      long uuidMsb         (8)    bits altos del UUID
 *      long uuidLsb         (8)    bits bajos del UUID
 *      char[40] nombres     (80)
 *      char[40] apellidos   (80)
 *      char[30] especialidad(60)
 *      char[15] telefono    (30)
 *      char[50] correo      (100)
 *      int  horaInicio      (4)    segundos desde medianoche
 *      int  horaFin         (4)
 *      byte activo          (1)    estado del medico en la clinica
 *
 * El UUID se guarda como sus dos mitades de 64 bits (16 bytes) en lugar de como
 * texto (36 caracteres, 72 bytes), y se reconstruye exacto con new UUID(msb, lsb).
 */
public class ArchivoMedicos extends ArchivoBase<UUID, Medico> {

    // Longitudes de los campos de texto, en caracteres.
    // Son publicas a proposito: la capa de servicio las usa para validar y
    // rechazar un texto demasiado largo, en lugar de dejar que se recorte en
    // silencio al escribirlo.
    public static final int LARGO_NOMBRES = 40;
    public static final int LARGO_APELLIDOS = 40;
    public static final int LARGO_ESPECIALIDAD = 30;
    public static final int LARGO_TELEFONO = 15;
    public static final int LARGO_CORREO = 50;

    /**
     * Tamano del registro. Se calcula sumando los campos en vez de escribir un
     * numero magico: si manana se agranda un campo, la constante se ajusta sola.
     */
    private static final int TAM_REGISTRO =
            TAM_ENCABEZADO_REGISTRO
                    + Long.BYTES * 2                                      // uuid
                    + UtilArchivo.bytesDeCadena(LARGO_NOMBRES)
                    + UtilArchivo.bytesDeCadena(LARGO_APELLIDOS)
                    + UtilArchivo.bytesDeCadena(LARGO_ESPECIALIDAD)
                    + UtilArchivo.bytesDeCadena(LARGO_TELEFONO)
                    + UtilArchivo.bytesDeCadena(LARGO_CORREO)
                    + Integer.BYTES                                       // horaInicio
                    + Integer.BYTES                                       // horaFin
                    + Byte.BYTES;                                         // activo

    public ArchivoMedicos(String ruta) throws IOException {
        super(ruta, TAM_REGISTRO);
    }

    /** El UUID lo genera el sistema, nunca el usuario. */
    @Override
    protected void prepararParaInsertar(Medico medico) {
        if (medico.getId() == null) {
            medico.setId(UUID.randomUUID());
        }
    }

    @Override
    protected UUID idDe(Medico medico) {
        return medico.getId();
    }

    @Override
    protected UUID leerId() throws IOException {
        long msb = archivo.readLong();
        long lsb = archivo.readLong();
        return new UUID(msb, lsb);
    }

    @Override
    protected void escribirCampos(Medico medico) throws IOException {
        // El identificador va primero: asi la clase base puede reconstruir el
        // indice leyendo solo los primeros bytes de cada registro.
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

    @Override
    protected Medico leerCampos() throws IOException {
        // El orden de lectura debe ser EXACTAMENTE el mismo de escribirCampos.
        UUID id = leerId();

        String nombres = UtilArchivo.leerCadena(archivo, LARGO_NOMBRES);
        String apellidos = UtilArchivo.leerCadena(archivo, LARGO_APELLIDOS);
        String especialidad = UtilArchivo.leerCadena(archivo, LARGO_ESPECIALIDAD);
        String telefono = UtilArchivo.leerCadena(archivo, LARGO_TELEFONO);
        String correo = UtilArchivo.leerCadena(archivo, LARGO_CORREO);

        LocalTime horaInicio = UtilArchivo.leerHora(archivo);
        LocalTime horaFin = UtilArchivo.leerHora(archivo);

        boolean activo = archivo.readByte() == 1;

        return new Medico(id, nombres, apellidos, especialidad,
                telefono, correo, horaInicio, horaFin, activo);
    }
}