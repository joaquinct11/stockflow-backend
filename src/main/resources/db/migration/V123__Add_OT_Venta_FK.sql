-- FK de integridad referencial para ordenes_trabajo.venta_id → ventas.id
-- La columna venta_id existe desde V121 pero sin constraint.
-- Corresponde a la Fase 2 (facturación desde OT); se registra aquí por integridad del esquema.
ALTER TABLE ordenes_trabajo
    ADD CONSTRAINT fk_ot_venta FOREIGN KEY (venta_id) REFERENCES ventas (id);
