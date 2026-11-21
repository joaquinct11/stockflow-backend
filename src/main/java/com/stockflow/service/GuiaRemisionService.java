package com.stockflow.service;

import com.stockflow.dto.gre.*;
import com.stockflow.entity.gre.GuiaRemision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GuiaRemisionService {

    /** Crea un borrador sin enviar a SUNAT. */
    GuiaRemisionResponse crearBorrador(CrearGuiaRemisionRequest request);

    /** Edita campos de un borrador (solo BORRADOR o ERROR). */
    GuiaRemisionResponse editarBorrador(Long id, EditarBorradorRequest request);

    /** Elimina definitivamente un borrador (solo BORRADOR o ERROR). */
    void eliminarBorrador(Long id);

    /** Emite la guía a SUNAT vía Lucode. */
    GuiaRemisionResponse emitirGRE(Long id);

    /** Confirma la recepción física del destinatario. */
    GuiaRemisionResponse confirmarRecepcion(Long id, ConfirmarRecepcionRequest request);

    /** Cancela un borrador (no emitido). */
    GuiaRemisionResponse cancelarBorrador(Long id);

    /** Lista paginada del tenant. */
    Page<GuiaRemisionListResponse> listar(Pageable pageable);

    /** Detalle completo. */
    GuiaRemisionResponse obtener(Long id);

    /** Genera PDF y retorna bytes. */
    byte[] generarPDF(Long id);

    /**
     * Recupera una guía atascada en EMITIENDO (interrupción sin respuesta de Lucode)
     * trasladándola a ERROR para permitir edición y reintento.
     * Solo aplicable si lucodeHash, xmlUrl y lucodeId son todos null
     * (es decir, Lucode nunca confirmó la emisión).
     */
    GuiaRemisionResponse recuperarEmitiendo(Long id);

    /**
     * Reconcilia una GRE que quedó en estado ERROR local pero fue aceptada por SUNAT/Lucode
     * (escenario F-08: bug de deserialización de campo pdf.a4 en LucodeGREPayload).
     *
     * Precondiciones:
     *  - La guía debe estar en estado ERROR.
     *  - No debe existir un movimiento de SALIDA previo con referencia="GRE-{id}".
     *  - Se proporcionan los datos tributarios (hash, xmlUrl, cdrUrl) obtenidos del OSE.
     *
     * Efectos:
     *  - Registra los movimientos de SALIDA de inventario que no se registraron por el bug.
     *  - Actualiza el estado a EMITIDA con los datos tributarios correctos.
     *  - Registra un evento RECONCILIACION_F08 en el historial.
     */
    GuiaRemisionResponse reconciliarGREAceptada(Long id, ReconciliarGRERequest request);
}
