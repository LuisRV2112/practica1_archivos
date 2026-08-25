package com.clinica.persistencia;

import com.clinica.modelo.Medico;

import java.io.IOException;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Persistencia de médicos: archivo secuencial indexado.
 * Datos secuenciales (óptimo para reportes) + índice ordenado para búsqueda binaria O(log n).
 *
 * Registro de 380 bytes:
 *   estadoRegistro(1) + siguienteLibre(4) + uuid(16) + nombres(80) +
 *   apellidos(80) + especialidad(60) + telefono(30) + correo(100) +
 *   horaInicio(4) + horaFin(4) + activo(1)
 */
public class ArchivoMedicos extends ArchivoBase<UUID, Medico> {

    // Longitudes de campos de texto (en caracteres). Públicas para que
    // servicio las use en validaciones de longitud.
    public static final int LARGO_NOMBRES = 40;
    public static final int LARGO_APELLIDOS = 40;
    public static final int LARGO_ESPECIALIDAD = 30;
    public static final int LARGO_TELEFONO = 15;
    public static final int LARGO_CORREO = 50;

    /** Tamaño del registro. Se calcula sumando campos, no con un número mágico. */
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

    /** Indice ordenado en archivo: UUID -> numero de registro. */
    private final IndiceOrdenado indice;

    public ArchivoMedicos(String ruta) throws IOException {
        super(ruta, TAM_REGISTRO);
        this.indice = new IndiceOrdenado(rutaDelIndice(ruta));
        iniciarOrganizacion();
    }

    /** El indice acompana al archivo de datos: medicos.dat -> medicos.idx */
    private static String rutaDelIndice(String rutaDatos) {
        int punto = rutaDatos.lastIndexOf('.');
        String base = (punto < 0) ? rutaDatos : rutaDatos.substring(0, punto);
        return base + ".idx";
    }

    @Override
    public String nombreOrganizacion() {
        return "Secuencial indexado (busqueda binaria)";
    }

    // Organización: secuencial indexado

    /** Reconstruye el índice si no concuerda con el archivo de datos. */
    @Override
    protected void prepararIndice() throws IOException {
        if (indice.getCantidad() == cantidad()) {
            return;
        }

        indice.vaciar();
        int total = totalRegistros();

        for (int i = 0; i < total; i++) {
            UUID id = idEn(i);
            if (id != null) {
                indice.insertar(id, i);
            }
        }
    }

    /** Búsqueda binaria en índice ordenado: O(log n). */
    @Override
    protected Integer localizar(UUID id) throws IOException {
        return indice.buscar(id);
    }

    @Override
    protected void indexarInsercion(UUID id, int numeroRegistro) throws IOException {
        indice.insertar(id, numeroRegistro);
    }

    @Override
    protected void indexarEliminacion(UUID id, int numeroRegistro) throws IOException {
        indice.eliminar(id);
    }

    @Override
    protected void cerrarIndice() throws IOException {
        indice.close();
    }

    /** Comparaciones máximas de búsqueda binaria; para reporte técnico. */
    public int comparacionesMaximas() {
        return indice.comparacionesMaximas();
    }

    // Formato de campos

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
        // UUID primero para que la clase base pueda reconstruir el índice.
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
        // Orden de lectura = orden de escritura en escribirCampos.
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
