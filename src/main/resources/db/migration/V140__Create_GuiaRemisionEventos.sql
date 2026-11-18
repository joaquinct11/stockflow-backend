-- V140: Historial de eventos de cada guía (trazabilidad de estados).
CREATE TABLE guia_remision_eventos (
    id                  BIGSERIAL PRIMARY KEY,
    guia_remision_id    BIGINT       NOT NULL REFERENCES guias_remision(id),
    tenant_id           VARCHAR(100) NOT NULL,
    tipo_evento         VARCHAR(50)  NOT NULL,  -- CREADA | EMISION_INICIADA | EMITIDA | ERROR_LUCODE | RECEPCION_CONFIRMADA | CANCELADA
    descripcion         TEXT,
    datos_adicionales   TEXT,  -- JSON opcional con detalles del evento
    usuario             VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gre_guia ON guia_remision_eventos(guia_remision_id);
CREATE INDEX idx_gre_tenant ON guia_remision_eventos(tenant_id);
