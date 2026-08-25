package com.clinica.persistencia;

import com.clinica.modelo.Cita;
import com.clinica.modelo.EstadoCita;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Persistencia de citas medicas.
 *
 * ---------------------------------------------------------------------------
 * REGISTRO DE CITA: 580 bytes
 * ---------------------------------------------------------------------------
 *      byte estadoRegistro       (1)    de la clase base
 *      int  siguienteLibre       (4)    de la clase base
 *      long citaMsb              (8)    UUID de la cita
 *      long citaLsb              (8)
 *      char[15] identificacionPac(30)   referencia al paciente
 *      long medicoMsb            (8)    UUID del medico
 *      long medicoLsb            (8)
 *      long fecha                (8)    dias desde 1970-01-01
 *      int  horaInicio           (4)    segundos desde medianoche
 *      char[100] motivo          (200)
 *      byte estado               (1)    codigo del enum EstadoCita
 *      char[150] observaciones   (300)
 *
 * Las dos referencias (identificacion del paciente y UUID del medico) se
 * guardan tal cual, sin copiar nombres ni especialidades. Duplicar esos datos
 * significaria tener que actualizarlos en dos lugares cada vez que cambien, y
 * tarde o temprano quedarian desincronizados.
 */
public class ArchivoCitas extends ArchivoBase<UUID, Cita> {

    public static final int LARGO_IDENTIFICACION = 15;
    public static final int LARGO_MOTIVO = 100;
    public static final int LARGO_OBSERVACIONES = 150;

    private static final int TAM_REGISTRO =
              TAM_ENCABEZADO_REGISTRO
            + Long.BYTES * 2                                       // uuid cita
            + UtilArchivo.bytesDeCadena(LARGO_IDENTIFICACION)
            + Long.BYTES * 2                                       // uuid medico
            + Long.BYTES                                           // fecha
            + Integer.BYTES                                        // horaInicio
            + UtilArchivo.bytesDeCadena(LARGO_MOTIVO)
            + Byte.BYTES                                           // estado
            + UtilArchivo.bytesDeCadena(LARGO_OBSERVACIONES);

    public ArchivoCitas(String ruta) throws IOException {
        super(ruta, TAM_REGISTRO);
        iniciarOrganizacion();
    }

    @Override
    public String nombreOrganizacion() {
        return "Secuencial";
    }

    /** El UUID de la cita lo genera el sistema, nunca el usuario. */
    @Override
    protected void prepararParaInsertar(Cita cita) {
        if (cita.getId() == null) {
            cita.setId(UUID.randomUUID());
        }
    }

    @Override
    protected UUID idDe(Cita cita) {
        return cita.getId();
    }

    @Override
    protected UUID leerId() throws IOException {
        long msb = archivo.readLong();
        long lsb = archivo.readLong();
        return new UUID(msb, lsb);
    }

    @Override
    protected void escribirCampos(Cita cita) throws IOException {
        // El identificador va primero, como en todos los archivos.
        archivo.writeLong(cita.getId().getMostSignificantBits());
        archivo.writeLong(cita.getId().getLeastSignificantBits());

        UtilArchivo.escribirCadena(archivo, cita.getIdentificacionPaciente(),
                LARGO_IDENTIFICACION);

        archivo.writeLong(cita.getIdMedico().getMostSignificantBits());
        archivo.writeLong(cita.getIdMedico().getLeastSignificantBits());

        UtilArchivo.escribirFecha(archivo, cita.getFecha());
        UtilArchivo.escribirHora(archivo, cita.getHoraInicio());

        UtilArchivo.escribirCadena(archivo, cita.getMotivo(), LARGO_MOTIVO);

        archivo.writeByte(cita.getEstado() == null ? 0 : cita.getEstado().getCodigo());

        UtilArchivo.escribirCadena(archivo, cita.getObservaciones(), LARGO_OBSERVACIONES);
    }

    @Override
    protected Cita leerCampos() throws IOException {
        // El orden de lectura debe ser EXACTAMENTE el mismo de escribirCampos.
        UUID id = leerId();

        String identificacionPaciente =
                UtilArchivo.leerCadena(archivo, LARGO_IDENTIFICACION);

        long medicoMsb = archivo.readLong();
        long medicoLsb = archivo.readLong();
        UUID idMedico = new UUID(medicoMsb, medicoLsb);

        LocalDate fecha = UtilArchivo.leerFecha(archivo);
        LocalTime horaInicio = UtilArchivo.leerHora(archivo);

        String motivo = UtilArchivo.leerCadena(archivo, LARGO_MOTIVO);

        byte codigoEstado = archivo.readByte();
        EstadoCita estado = (codigoEstado == 0) ? null : EstadoCita.porCodigo(codigoEstado);

        String observaciones = UtilArchivo.leerCadena(archivo, LARGO_OBSERVACIONES);

        return new Cita(id, identificacionPaciente, idMedico, fecha, horaInicio,
                motivo, estado, observaciones);
    }
}
