-- V46: Garantizar que cada par (tenant_id, usuario_principal_id) tenga solo UNA suscripción.
-- Esto evita que Spring Data lance IncorrectResultSizeDataAccessException cuando
-- findByTenantIdAndUsuarioPrincipalId retorna más de un resultado por un bug previo.
--
-- IMPORTANTE: Antes de aplicar, limpiar duplicados si existen.
-- Si Flyway falla aquí, ejecutar primero:
--   DELETE FROM suscripciones s1
--   USING suscripciones s2
--   WHERE s1.id < s2.id
--     AND s1.tenant_id = s2.tenant_id
--     AND s1.usuario_principal_id = s2.usuario_principal_id;

ALTER TABLE suscripciones
    ADD CONSTRAINT uq_suscripciones_tenant_usuario
    UNIQUE (tenant_id, usuario_principal_id);
