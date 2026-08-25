package com.clinica.persistencia;

import com.clinica.modelo.EntradaBitacora;
import com.clinica.modelo.TipoOperacion;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistencia de la bitacora de operaciones.
 *
 * ---------------------------------------------------------------------------
 * REGISTRO DE BITACORA: 370 bytes
 * ---------------------------------------------------------------------------
 *      byte estadoRegistro  (1)    de la clase base
 *      int  siguienteLibre  (4)    de la clase base
 *      long uuidMsb         (8)
 *      long uuidLsb         (8)
 *      long momento         (8)    segundos desde 1970-01-01 (UTC)
 *      char[20] modulo      (40)
 *      byte operacion       (1)    codigo del enum TipoOperacion
 *      char[150] detalle    (300)
 *
 * Hereda de ArchivoBase igual que las demas entidades, aunque en la practica
 * nunca se elimina una entrada: la lista de espacios libres de este archivo
 * siempre queda vacia. Se reutiliza la base por consistencia y porque el indice
 * y la cabecera siguen siendo utiles.
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
     * La bitacora es el caso de libro del archivo SECUENCIAL: solo se anexa al
     * final, nunca se modifica ni se elimina, y se lee entera de principio a
     * fin. No necesita indice, porque nunca se busca una entrada suelta por su
     * identificador; se consulta el historial completo.
     *
     * Como consecuencia, el apilo de espacios libres de este archivo siempre
     * esta vacio, y el orden fisico coincide exactamente con el cronologico.
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
