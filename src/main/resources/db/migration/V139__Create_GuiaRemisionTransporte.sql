-- V139: Datos de transporte de cada guía (1:1 con guias_remision).
-- Almacena campos de transporte privado Y público; los no aplicables quedan null.
CREATE TABLE guia_remision_transporte (
    id                       BIGSERIAL PRIMARY KEY,
    guia_remision_id         BIGINT       NOT NULL UNIQUE REFERENCES guias_remision(id) ON DELETE CASCADE,
    tenant_id                VARCHAR(100) NOT NULL,

    -- Dirección de partida y llegada
    direccion_partida        VARCHAR(500) NOT NULL,
    ubigeo_partida           VARCHAR(10),   -- código UBIGEO 6 dígitos SUNAT
    direccion_llegada        VARCHAR(500) NOT NULL,
    ubigeo_llegada           VARCHAR(10),

    -- Transporte PRIVADO (modalidad '02')
    vehiculo_placa           VARCHAR(20),
    conductor_tipo_doc       VARCHAR(5),   -- '1' DNI, etc.
    conductor_num_doc        VARCHAR(20),
    conductor_nombres        VARCHAR(200),
    conductor_apellidos      VARCHAR(200),
    conductor_licencia       VARCHAR(50),

    -- Transporte PÚBLICO (modalidad '01')
    transportista_ruc        VARCHAR(20),
    transportista_denominacion VARCHAR(255),
    transportista_nro_registro_mtc VARCHAR(100),
    transportista_nro_autorizacion VARCHAR(100),
    transportista_codigo_entidad_autorizadora VARCHAR(10),

    -- Fecha específica según modalidad
    fecha_inicio_traslado    DATE,          -- requerido para transporte privado
    fecha_entrega_transportista DATE         -- requerido para transporte público
);
