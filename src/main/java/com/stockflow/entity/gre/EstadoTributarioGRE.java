package com.stockflow.entity.gre;

public enum EstadoTributarioGRE {
    /** Creada en Fluxus, no enviada a Lucode/SUNAT. */
    BORRADOR,
    /** Enviando a Lucode en este momento — bloquea reintentos. */
    EMITIENDO,
    /** Lucode devolvió respuesta exitosa con hash/XML/CDR. */
    EMITIDA,
    /** Lucode devolvió error o hubo timeout no resuelto. */
    ERROR,
    /** Borrador cancelado antes de emitir (no tiene efecto tributario). */
    CANCELADA
    // NOTA: La anulación de GRE ya emitidas no está documentada por Lucode.
    // Implementar cuando Lucode confirme soporte vía /api/v3/voided o endpoint específico.
}
