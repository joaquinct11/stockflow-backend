-- V48: Tabla de retiros de caja (arqueos parciales / retiros de efectivo durante turno)
CREATE TABLE retiros_caja (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   VARCHAR(100)   NOT NULL,
    caja_id     BIGINT         NOT NULL REFERENCES cajas(id),
    usuario_id  BIGINT         NOT NULL REFERENCES usuarios(id),
    usuario_nombre VARCHAR(200) NOT NULL,
    monto       DECIMAL(12,2)  NOT NULL,
    motivo      VARCHAR(300),
    fecha       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_retiros_caja_tenant    ON retiros_caja(tenant_id);
CREATE INDEX idx_retiros_caja_caja_id   ON retiros_caja(caja_id);
CREATE INDEX idx_retiros_caja_tenant_fecha ON retiros_caja(tenant_id, fecha);
