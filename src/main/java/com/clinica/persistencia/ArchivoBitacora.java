package com.clinica.persistencia;

import com.clinica.modelo.EntradaBitacora;
import com.clinica.modelo.TipoOperacion;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistencia de bitácora. Solo se anexa, nunca se modifica ni elimina.
 * Secuencial puro: se lee entera de principio a fin, sin índice.
 *
 * Registro de 370 bytes:
 *   estadoRegistro(1) + siguienteLibre(4) + uuid(16) + momento(8) +
 *   modulo(40) + operacion(1) + detalle(300)
 */
public class ArchivoBitacora extends ArchivoBase<UUID, EntradaBitacora> {

    public static final int LARGO_MODULO = 20;
    public static final int LARGO_DETALLE = 150;

    private static final int TAM_REGISTRO =
              TAM_ENCABEZADO_REGISTRO
            + Long.BYTES * 2                                   // uuid
            + Long.BYTES                                       // momento
            + UtilArchivo.bytesDeCadena(LARGO_MODULO)
            + Byte.BYTES                                       // operacion
            + UtilArchivo.bytesDeCadena(LARGO_DETALLE);

    public ArchivoBitacora(String ruta) throws IOException {
        super(ruta, TAM_REGISTRO);
        iniciarOrganizacion();
    }

    /**
     * Secuencial puro: solo se anexa, nunca se busca por id. El apilo de
     * espacios libres siempre está vacío y el orden físico = cronológico.
     */
    @Override
    public String nombreOrganizacion() {
        return "Secuencial (solo anexar)";
    }

    @Override
    protected void prepararParaInsertar(EntradaBitacora entrada) {
        if (entrada.getId() == null) {
            entrada.setId(UUID.randomUUID());
        }
        if (entrada.getMomento() == null) {
            entrada.setMomento(LocalDateTime.now());
        }
    }

    @Override
    protected UUID idDe(EntradaBitacora entrada) {
        return entrada.getId();
    }

    @Override
    protected UUID leerId() throws IOException {
        long msb = archivo.readLong();
        long lsb = archivo.readLong();
        return new UUID(msb, lsb);
    }

    @Override
    protected void escribirCampos(EntradaBitacora entrada) throws IOException {
        archivo.writeLong(entrada.getId().getMostSignificantBits());
        archivo.writeLong(entrada.getId().getLeastSignificantBits());

        UtilArchivo.escribirFechaHora(archivo, entrada.getMomento());
        UtilArchivo.escribirCadena(archivo, entrada.getModulo(), LARGO_MODULO);

        archivo.writeByte(entrada.getOperacion() == null
                ? 0 : entrada.getOperacion().getCodigo());

        UtilArchivo.escribirCadena(archivo, entrada.getDetalle(), LARGO_DETALLE);
    }

    @Override
    protected EntradaBitacora leerCampos() throws IOException {
        UUID id = leerId();

        LocalDateTime momento = UtilArchivo.leerFechaHora(archivo);
        String modulo = UtilArchivo.leerCadena(archivo, LARGO_MODULO);

        byte codigo = archivo.readByte();
        TipoOperacion operacion = (codigo == 0) ? null : TipoOperacion.porCodigo(codigo);

        String detalle = UtilArchivo.leerCadena(archivo, LARGO_DETALLE);

        return new EntradaBitacora(id, momento, modulo, operacion, detalle);
    }
}
