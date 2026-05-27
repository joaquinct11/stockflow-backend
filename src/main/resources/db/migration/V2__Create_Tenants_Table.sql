CREATE TABLE tenants (
                         id BIGSERIAL PRIMARY KEY,
                         tenant_id VARCHAR(100) NOT NULL UNIQUE,
                         nombre VARCHAR(255) NOT NULL,
                         activo BOOLEAN DEFAULT true,
                         ose_url VARCHAR(500),
                         ose_token VARCHAR(500),
                         deleted_at TIMESTAMP DEFAULT NULL,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- V30: OSE (Operador de Servicios Electrónicos) config por tenant.
-- Cada cliente configura su propio token y URL de OSE (ej: Nubefact, Efact).
-- Fluxus usa esas credenciales para enviar comprobantes a SUNAT en su nombre.

COMMENT ON COLUMN tenants.ose_url
    IS 'URL base del OSE del tenant, ej: https://api.nubefact.com/api/v1/{empresa-slug}';
COMMENT ON COLUMN tenants.ose_token
    IS 'Token API del OSE del tenant (Nubefact / Efact / etc)';

CREATE INDEX idx_tenants_tenant_id ON tenants(tenant_id);
CREATE INDEX idx_tenants_nombre ON tenants(nombre);
CREATE INDEX idx_tenants_activo ON tenants(activo);
CREATE INDEX idx_tenants_deleted_at ON tenants(deleted_at);
