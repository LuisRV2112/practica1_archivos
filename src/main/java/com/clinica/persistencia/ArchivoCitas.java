package com.clinica.persistencia;

import com.clinica.modelo.Cita;
import com.clinica.modelo.EstadoCita;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistencia de citas: archivo multianillo.
 * Cada cita se encadena con las de su mismo medico y las de su mismo paciente.
 * Consultar "todas las citas de X" cuesta O(k) en vez de O(n).
 *
 * Registro de 588 bytes:
 *   estadoRegistro(1) + siguienteLibre(4) + uuidCita(16) + enlaceMedico(4) +
 *   enlacePaciente(4) + identificacionPaciente(30) + uuidMedico(16) +
 *   fecha(8) + horaInicio(4) + motivo(200) + estado(1) + observaciones(300)
 *
 * Cabezas de anillo en archivos separados:
 *   citas_medico.idx (ordenado): UUID medico -> primera cita
 *   citas_paciente.hash: identificación -> primera cita
 */
public class ArchivoCitas extends ArchivoBase<UUID, Cita> {

    public static final int LARGO_IDENTIFICACION = 15;
    public static final int LARGO_MOTIVO = 100;
    public static final int LARGO_OBSERVACIONES = 150;

    /** Marca de fin de cadena. */
    private static final int FIN_DE_ANILLO = -1;

    private static final int TAM_REGISTRO =
            TAM_ENCABEZADO_REGISTRO
                    + Long.BYTES * 2                                       // uuid cita
                    + Integer.BYTES * 2                                    // los dos enlaces
                    + UtilArchivo.bytesDeCadena(LARGO_IDENTIFICACION)
                    + Long.BYTES * 2                                       // uuid medico
                    + Long.BYTES                                           // fecha
                    + Integer.BYTES                                        // horaInicio
                    + UtilArchivo.bytesDeCadena(LARGO_MOTIVO)
                    + Byte.BYTES                                           // estado
                    + UtilArchivo.bytesDeCadena(LARGO_OBSERVACIONES);

    /** Desplazamiento donde empiezan los dos enlaces. */
    private static final int DESPLAZAMIENTO_ENLACES =
            TAM_ENCABEZADO_REGISTRO + Long.BYTES * 2;

    /** Desplazamiento donde empieza la identificacion del paciente. */
    private static final int DESPLAZAMIENTO_PACIENTE =
            DESPLAZAMIENTO_ENLACES + Integer.BYTES * 2;

    /** Desplazamiento donde empieza el UUID del medico. */
    private static final int DESPLAZAMIENTO_MEDICO =
            DESPLAZAMIENTO_PACIENTE + UtilArchivo.bytesDeCadena(LARGO_IDENTIFICACION);

    /** Cabezas del anillo por medico. */
    private final IndiceOrdenado anillosMedico;

    /** Cabezas del anillo por paciente. */
    private final IndiceHash anillosPaciente;

    public ArchivoCitas(String ruta) throws IOException {
        super(ruta, TAM_REGISTRO);

        String base = sinExtension(ruta);
        this.anillosMedico = new IndiceOrdenado(base + "_medico.idx");
        this.anillosPaciente = new IndiceHash(base + "_paciente.hash", LARGO_IDENTIFICACION);

        iniciarOrganizacion();
    }

    private static String sinExtension(String ruta) {
        int punto = ruta.lastIndexOf('.');
        return (punto < 0) ? ruta : ruta.substring(0, punto);
    }

    @Override
    public String nombreOrganizacion() {
        return "Multianillo (por medico y por paciente)";
    }

    // Organización: multianillo

    /**
     * Reconstruye ambos anillos recorriendo el archivo una vez (atrás hacia
     * adelante para que las cadenas queden en orden de inserción).
     */
    @Override
    protected void prepararIndice() throws IOException {
        anillosMedico.vaciar();
        anillosPaciente.vaciar();

        for (int i = totalRegistros() - 1; i >= 0; i--) {
            archivo.seek(posicionDe(i));
            if (archivo.readByte() != REGISTRO_OCUPADO) {
                continue;
            }
            enlazar(i, medicoEn(i), pacienteEn(i));
        }
    }

    @Override
    protected void indexarInsercion(UUID id, int numeroRegistro) throws IOException {
        enlazar(numeroRegistro, medicoEn(numeroRegistro), pacienteEn(numeroRegistro));
    }

    /**
     * Saca la cita de ambos anillos antes de que su espacio se reutilice.
     */
    @Override
    protected void indexarEliminacion(UUID id, int numeroRegistro) throws IOException {
        UUID medico = medicoEn(numeroRegistro);
        String paciente = pacienteEn(numeroRegistro);

        desenlazar(numeroRegistro, true, medico.toString(), medico, null);
        desenlazar(numeroRegistro, false, paciente, null, paciente);
    }

    /**
     * Reescribe la cita conservando sus enlaces. Se leen antes y restauran
     * después porque escribirCampos deja los enlaces en -1 (el dominio no los
     * conoce).
     */
    @Override
    public boolean actualizar(Cita cita) throws IOException {
        Integer numeroRegistro = localizar(cita.getId());
        if (numeroRegistro == null) {
            return false;
        }

        int siguienteMedico = enlaceMedicoEn(numeroRegistro);
        int siguientePaciente = enlacePacienteEn(numeroRegistro);

        escribirRegistro(numeroRegistro, cita);

        escribirEnlaces(numeroRegistro, siguienteMedico, siguientePaciente);
        return true;
    }

    @Override
    protected void cerrarIndice() throws IOException {
        anillosMedico.close();
        anillosPaciente.close();
    }

    // Recorrido de anillos

    /** Citas de un medico siguiendo su anillo: O(k) lecturas. */
    public List<Cita> citasDelMedico(UUID idMedico) throws IOException {
        List<Cita> resultado = new ArrayList<>();

        Integer actual = anillosMedico.buscar(idMedico);
        while (actual != null && actual != FIN_DE_ANILLO) {
            Cita cita = leerRegistro(actual);
            if (cita != null) {
                resultado.add(cita);
            }
            int siguiente = enlaceMedicoEn(actual);
            actual = (siguiente == FIN_DE_ANILLO) ? null : siguiente;
        }
        return resultado;
    }

    /** Citas de un paciente siguiendo su anillo. */
    public List<Cita> citasDelPaciente(String identificacion) throws IOException {
        List<Cita> resultado = new ArrayList<>();

        Integer actual = anillosPaciente.buscar(identificacion);
        while (actual != null && actual != FIN_DE_ANILLO) {
            Cita cita = leerRegistro(actual);
            if (cita != null) {
                resultado.add(cita);
            }
            int siguiente = enlacePacienteEn(actual);
            actual = (siguiente == FIN_DE_ANILLO) ? null : siguiente;
        }
        return resultado;
    }

    /** Cuántos anillos de médico hay; para reporte técnico. */
    public int anillosDeMedico() {
        return anillosMedico.getCantidad();
    }

    /** Cuántos anillos de paciente hay; para reporte técnico. */
    public int anillosDePaciente() {
        return anillosPaciente.getCantidad();
    }

    // Manejo de enlaces

    /** Mete la cita al frente de ambos anillos. */
    private void enlazar(int numeroRegistro, UUID medico, String paciente) throws IOException {
        Integer cabezaMedico = anillosMedico.buscar(medico);
        Integer cabezaPaciente = anillosPaciente.buscar(paciente);

        escribirEnlaces(numeroRegistro,
                (cabezaMedico == null) ? FIN_DE_ANILLO : cabezaMedico,
                (cabezaPaciente == null) ? FIN_DE_ANILLO : cabezaPaciente);

        anillosMedico.actualizar(medico, numeroRegistro);
        anillosPaciente.actualizar(paciente, numeroRegistro);
    }

    /**
     * Quita una cita de uno de sus anillos. Si era cabeza, la cabeza pasa a
     * ser la siguiente. Si estaba en medio, se salta al anterior.
     */
    private void desenlazar(int numeroRegistro, boolean porMedico,
                            String descripcionClave, UUID claveMedico,
                            String clavePaciente) throws IOException {

        Integer cabeza = porMedico
                ? anillosMedico.buscar(claveMedico)
                : anillosPaciente.buscar(clavePaciente);

        if (cabeza == null) {
            return;
        }

        int siguienteDelQueSale = porMedico
                ? enlaceMedicoEn(numeroRegistro)
                : enlacePacienteEn(numeroRegistro);

        if (cabeza == numeroRegistro) {
            if (siguienteDelQueSale == FIN_DE_ANILLO) {
                // Era la única: el anillo desaparece.
                if (porMedico) {
                    anillosMedico.eliminar(claveMedico);
                } else {
                    anillosPaciente.eliminar(clavePaciente);
                }
            } else if (porMedico) {
                anillosMedico.actualizar(claveMedico, siguienteDelQueSale);
            } else {
                anillosPaciente.actualizar(clavePaciente, siguienteDelQueSale);
            }
            return;
        }

        // Recorre buscando al anterior.
        int anterior = cabeza;
        while (anterior != FIN_DE_ANILLO) {
            int siguiente = porMedico ? enlaceMedicoEn(anterior) : enlacePacienteEn(anterior);

            if (siguiente == numeroRegistro) {
                if (porMedico) {
                    escribirEnlaceMedico(anterior, siguienteDelQueSale);
                } else {
                    escribirEnlacePaciente(anterior, siguienteDelQueSale);
                }
                return;
            }
            anterior = siguiente;
        }
    }

    private void escribirEnlaces(int numeroRegistro, int siguienteMedico,
                                 int siguientePaciente) throws IOException {
        archivo.seek(posicionDe(numeroRegistro) + DESPLAZAMIENTO_ENLACES);
        archivo.writeInt(siguienteMedico);
        archivo.writeInt(siguientePaciente);
    }

    private void escribirEnlaceMedico(int numeroRegistro, int valor) throws IOException {
        archivo.seek(posicionDe(numeroRegistro) + DESPLAZAMIENTO_ENLACES);
        archivo.writeInt(valor);
    }

    private void escribirEnlacePaciente(int numeroRegistro, int valor) throws IOException {
        archivo.seek(posicionDe(numeroRegistro) + DESPLAZAMIENTO_ENLACES + Integer.BYTES);
        archivo.writeInt(valor);
    }

    private int enlaceMedicoEn(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro) + DESPLAZAMIENTO_ENLACES);
        return archivo.readInt();
    }

    private int enlacePacienteEn(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro) + DESPLAZAMIENTO_ENLACES + Integer.BYTES);
        return archivo.readInt();
    }

    /** Lee solo el UUID del médico de un registro. */
    private UUID medicoEn(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro) + DESPLAZAMIENTO_MEDICO);
        long msb = archivo.readLong();
        long lsb = archivo.readLong();
        return new UUID(msb, lsb);
    }

    /** Lee solo la identificación del paciente de un registro. */
    private String pacienteEn(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro) + DESPLAZAMIENTO_PACIENTE);
        return UtilArchivo.leerCadena(archivo, LARGO_IDENTIFICACION);
    }

    // Formato de campos

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
        archivo.writeLong(cita.getId().getMostSignificantBits());
        archivo.writeLong(cita.getId().getLeastSignificantBits());

        // Enlaces nacen vacíos; los coloca el multianillo después de escribir.
        archivo.writeInt(FIN_DE_ANILLO);
        archivo.writeInt(FIN_DE_ANILLO);

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
        // Orden de lectura = orden de escritura en escribirCampos.
        UUID id = leerId();

        archivo.skipBytes(Integer.BYTES * 2); // enlaces no van al dominio

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