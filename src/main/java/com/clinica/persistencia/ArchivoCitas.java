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
 * Persistencia de citas: ARCHIVO MULTIANILLO.
 *
 * ---------------------------------------------------------------------------
 * POR QUE ESTA ORGANIZACION
 * ---------------------------------------------------------------------------
 * A una cita casi nunca se le busca por su propio identificador. Lo que el
 * sistema pregunta todo el tiempo es "todas las citas de ESTE medico" y "todas
 * las citas de ESTE paciente": la agenda del dia, el historial del expediente,
 * la validacion de traslapes al programar, y varios reportes del enunciado.
 *
 * Con un archivo secuencial, cada una de esas consultas obliga a leer el
 * archivo completo y descartar la mayoria de los registros. El multianillo lo
 * resuelve ENCADENANDO los registros que pertenecen al mismo grupo: cada cita
 * guarda en que posicion esta la siguiente cita de su mismo medico, y en cual
 * la siguiente de su mismo paciente. Recuperar la agenda de un medico deja de
 * costar O(n) sobre todo el archivo y pasa a costar O(k), donde k son
 * unicamente SUS citas.
 *
 * Se le llama "multi" anillo porque cada registro pertenece a VARIAS cadenas a
 * la vez: la del medico y la del paciente. Son dos recorridos independientes
 * sobre los mismos registros, sin duplicar un solo byte de informacion.
 *
 * ---------------------------------------------------------------------------
 * DONDE EMPIEZA CADA ANILLO
 * ---------------------------------------------------------------------------
 * La cadena necesita una cabeza. Esas cabezas viven en dos archivos de indice
 * aparte, que reutilizan las mismas estructuras que ya usan las otras
 * entidades:
 *
 *   citas_medico.idx    indice ordenado: UUID del medico    -> primera cita
 *   citas_paciente.hash indice hash:     identificacion     -> primera cita
 *
 * Las citas nuevas se enlazan AL FRENTE del anillo, igual que el apilo de
 * espacios libres: enlazar cuesta O(1) y no hay que recorrer la cadena hasta el
 * final para agregar.
 *
 * ---------------------------------------------------------------------------
 * REGISTRO DE CITA: 588 bytes
 * ---------------------------------------------------------------------------
 *      byte estadoRegistro        (1)    de la clase base
 *      int  siguienteLibre        (4)    de la clase base
 *      long citaMsb               (8)    UUID de la cita
 *      long citaLsb               (8)
 *      int  siguienteDelMedico    (4)    anillo 1: -1 marca el final
 *      int  siguienteDelPaciente  (4)    anillo 2: -1 marca el final
 *      char[15] identificacionPac (30)   referencia al paciente
 *      long medicoMsb             (8)    UUID del medico
 *      long medicoLsb             (8)
 *      long fecha                 (8)    dias desde 1970-01-01
 *      int  horaInicio            (4)    segundos desde medianoche
 *      char[100] motivo           (200)
 *      byte estado                (1)    codigo del enum EstadoCita
 *      char[150] observaciones    (300)
 *
 * Las dos referencias se guardan tal cual, sin copiar nombres ni
 * especialidades: duplicar esos datos obligaria a actualizarlos en dos lugares
 * y tarde o temprano quedarian desincronizados.
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

    /** Desplazamiento, dentro del registro, donde empiezan los dos enlaces. */
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

    // =======================================================================
    // ORGANIZACION: MULTIANILLO
    // =======================================================================

    /**
     * Rearma los dos anillos desde cero recorriendo el archivo una vez.
     *
     * Se reconstruye siempre al abrir, no solo cuando parece descuadrado: los
     * anillos son informacion derivada y rehacerlos es barato comparado con el
     * riesgo de arrastrar una cadena rota tras un cierre abrupto.
     *
     * Se recorre de atras hacia adelante porque cada cita se enlaza al frente
     * de su anillo; asi las cadenas quedan en el mismo orden que tendrian si se
     * hubieran ido insertando una por una.
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
     * Saca la cita de sus dos anillos antes de que su espacio se reutilice.
     *
     * Los datos del registro siguen legibles: la liberacion solo cambia el byte
     * de estado y el enlace del apilo, no borra el cuerpo.
     */
    @Override
    protected void indexarEliminacion(UUID id, int numeroRegistro) throws IOException {
        UUID medico = medicoEn(numeroRegistro);
        String paciente = pacienteEn(numeroRegistro);

        desenlazar(numeroRegistro, true, medico.toString(), medico, null);
        desenlazar(numeroRegistro, false, paciente, null, paciente);
    }

    /**
     * Reescribe la cita conservando sus enlaces.
     *
     * Hay que leerlos antes y restaurarlos despues porque escribirCampos deja
     * los enlaces en -1: el objeto Cita del dominio no los conoce, y no deberia
     * conocerlos. Son un detalle de como se guarda el archivo, no del negocio.
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

    // =======================================================================
    // RECORRIDO DE LOS ANILLOS
    // =======================================================================

    /**
     * Citas de un medico siguiendo su anillo.
     *
     * Solo se leen SUS registros: si el medico tiene 5 citas entre 10000, se
     * hacen 5 lecturas, no 10000.
     */
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

    /** Cuantos anillos de medico hay activos; para el reporte tecnico. */
    public int anillosDeMedico() {
        return anillosMedico.getCantidad();
    }

    /** Cuantos anillos de paciente hay activos; para el reporte tecnico. */
    public int anillosDePaciente() {
        return anillosPaciente.getCantidad();
    }

    // =======================================================================
    // MANEJO DE LOS ENLACES
    // =======================================================================

    /** Mete la cita al frente de sus dos anillos. */
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
     * Quita una cita de uno de sus anillos.
     *
     * Si era la cabeza, la cabeza pasa a ser la siguiente. Si estaba en medio,
     * se busca al anterior y se le hace apuntar a la siguiente, saltandose la
     * que se va. Es el desenlace clasico de una lista simplemente enlazada.
     *
     * @param porMedico true para el anillo del medico, false para el del paciente
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
                // Era la unica: el anillo desaparece.
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

        // Se recorre buscando al anterior.
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

    /** Lee solo el UUID del medico de un registro, sin cargarlo completo. */
    private UUID medicoEn(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro) + DESPLAZAMIENTO_MEDICO);
        long msb = archivo.readLong();
        long lsb = archivo.readLong();
        return new UUID(msb, lsb);
    }

    /** Lee solo la identificacion del paciente de un registro. */
    private String pacienteEn(int numeroRegistro) throws IOException {
        archivo.seek(posicionDe(numeroRegistro) + DESPLAZAMIENTO_PACIENTE);
        return UtilArchivo.leerCadena(archivo, LARGO_IDENTIFICACION);
    }

    // =======================================================================
    // FORMATO DE LOS CAMPOS
    // =======================================================================

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

        // Los enlaces nacen vacios; los coloca el multianillo despues de
        // escribir, porque el objeto Cita no los conoce ni debe conocerlos.
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
        // El orden de lectura debe ser EXACTAMENTE el mismo de escribirCampos.
        UUID id = leerId();

        archivo.skipBytes(Integer.BYTES * 2); // los enlaces no van al dominio

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