-- ============================================================
-- V70: Multi-local (Sucursales) — Fase 1: Estructura base
-- ============================================================
-- REGLA: Todos los nuevos campos son NULLABLE.
-- Los tenants en plan BÁSICO no se ven afectados en absoluto;
-- sus registros existentes tendrán sucursal_id = NULL y
-- el sistema seguirá funcionando exactamente igual.
-- Solo los tenants que activen el plan PRO usarán estas columnas.
-- ============================================================

-- ── 1. Tabla sucursales ──────────────────────────────────────────────────────
CREATE TABLE sucursales (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(100)    NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    nombre          VARCHAR(255)    NOT NULL,
    direccion       VARCHAR(500),
    telefono        VARCHAR(30),
    email           VARCHAR(150),
    es_principal    BOOLEAN         NOT NULL DEFAULT false,
    activo          BOOLEAN         NOT NULL DEFAULT true,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sucursales_tenant_id     ON sucursales(tenant_id);
CREATE INDEX idx_sucursales_tenant_activo ON sucursales(tenant_id, activo);
CREATE INDEX idx_sucursales_tenant_principal ON sucursales(tenant_id, es_principal);

-- ── 2. Stock por producto por sucursal ───────────────────────────────────────
-- Plan PRO: cada local maneja su propio stock.
-- El campo productos.stock_actual pasa a ser el total agregado de todos los locales.
-- Plan BÁSICO: esta tabla permanece vacía; se sigue usando productos.stock_actual.
CREATE TABLE producto_stock_sucursal (
    id              BIGSERIAL       PRIMARY KEY,
    producto_id     BIGINT          NOT NULL REFERENCES productos(id)   ON DELETE CASCADE,
    sucursal_id     BIGINT          NOT NULL REFERENCES sucursales(id)  ON DELETE CASCADE,
    tenant_id       VARCHAR(100)    NOT NULL,
    stock_actual    INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT uq_producto_sucursal UNIQUE (producto_id, sucursal_id)
);

CREATE INDEX idx_prod_stock_suc_producto ON producto_stock_sucursal(producto_id);
CREATE INDEX idx_prod_stock_suc_sucursal ON producto_stock_sucursal(sucursal_id);
CREATE INDEX idx_prod_stock_suc_tenant   ON producto_stock_sucursal(tenant_id);

-- ── 3. sucursal_id en tablas operativas (todas NULLABLE) ────────────────────

-- Ventas
ALTER TABLE ventas
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL REFERENCES sucursales(id);
CREATE INDEX idx_ventas_sucursal_id
    ON ventas(sucursal_id) WHERE sucursal_id IS NOT NULL;

-- Movimientos de inventario
ALTER TABLE movimientos_inventario
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL REFERENCES sucursales(id);
CREATE INDEX idx_mov_inv_sucursal_id
    ON movimientos_inventario(sucursal_id) WHERE sucursal_id IS NOT NULL;

-- Gastos
ALTER TABLE gastos
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL REFERENCES sucursales(id);
CREATE INDEX idx_gastos_sucursal_id
    ON gastos(sucursal_id) WHERE sucursal_id IS NOT NULL;

-- Cajas
ALTER TABLE cajas
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL REFERENCES sucursales(id);
CREATE INDEX idx_cajas_sucursal_id
    ON cajas(sucursal_id) WHERE sucursal_id IS NOT NULL;

-- Devoluciones
ALTER TABLE devoluciones
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL REFERENCES sucursales(id);
CREATE INDEX idx_devoluciones_sucursal_id
    ON devoluciones(sucursal_id) WHERE sucursal_id IS NOT NULL;

-- Notas de crédito
ALTER TABLE notas_credito
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL REFERENCES sucursales(id);
CREATE INDEX idx_notas_credito_sucursal_id
    ON notas_credito(sucursal_id) WHERE sucursal_id IS NOT NULL;

-- Órdenes de compra
ALTER TABLE orden_compra
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL REFERENCES sucursales(id);
CREATE INDEX idx_orden_compra_sucursal_id
    ON orden_compra(sucursal_id) WHERE sucursal_id IS NOT NULL;

-- Recepciones de mercadería
ALTER TABLE recepcion
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL REFERENCES sucursales(id);
CREATE INDEX idx_recepcion_sucursal_id
    ON recepcion(sucursal_id) WHERE sucursal_id IS NOT NULL;

-- Comprobantes electrónicos (boletas/facturas)
ALTER TABLE comprobantes
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL REFERENCES sucursales(id);
CREATE INDEX idx_comprobantes_sucursal_id
    ON comprobantes(sucursal_id) WHERE sucursal_id IS NOT NULL;

-- ── 4. Usuarios: asignación de local ────────────────────────────────────────
-- NULL = ADMIN (ve todos los locales) o plan BÁSICO (sin sucursales).
-- Vendedor/almacenero tendrá un sucursal_id asignado por el admin.
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT NULL REFERENCES sucursales(id);
CREATE INDEX idx_usuarios_sucursal_id
    ON usuarios(sucursal_id) WHERE sucursal_id IS NOT NULL;

-- ── 5. Permisos de sucursales (idempotentes) ─────────────────────────────────
INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('VER_SUCURSALES',    'Ver listado de sucursales del tenant',          NOW()),
    ('CREAR_SUCURSAL',    'Crear nuevas sucursales (plan PRO)',            NOW()),
    ('EDITAR_SUCURSAL',   'Editar datos de una sucursal existente',        NOW()),
    ('ELIMINAR_SUCURSAL', 'Desactivar o eliminar una sucursal',            NOW())
ON CONFLICT (nombre) DO NOTHING;
