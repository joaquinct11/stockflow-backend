ALTER TABLE sucursales
    ADD COLUMN bloqueada_por_plan BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN sucursales.bloqueada_por_plan IS
    'TRUE cuando la sucursal fue bloqueada automáticamente por downgrade de plan PRO→BÁSICO. '
    'Se reactiva automáticamente si el tenant vuelve a PRO. '
    'activo=FALSE + bloqueada_por_plan=TRUE = bloqueada (recuperable). '
    'activo=FALSE + bloqueada_por_plan=FALSE = desactivada manualmente (no se reactiva).';
