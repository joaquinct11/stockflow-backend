package com.stockflow.entity.gre;

public enum EstadoLogisticoGRE {
    /** Guía creada, mercadería aún no ha salido del origen. */
    PENDIENTE,
    /** Salida de origen registrada (movimiento SALIDA aplicado), en camino. */
    EN_TRANSITO,
    /** Recepción confirmada en destino (movimiento ENTRADA aplicado). */
    RECIBIDA,
    /** Cancelado antes de emitir; no hubo movimiento de inventario. */
    CANCELADA
}
