-- Ajuste de precios comerciales aprobados para plan_catalogo.
-- Actualiza precioMensual según la tabla comercial vigente de Fluxus.
-- Los culqi_plan_id NO se modifican aquí; requieren re-ejecutar
-- POST /api/culqi/admin/setup-planes desde el entorno correspondiente.

UPDATE plan_catalogo SET precio_mensual = 199.00 WHERE codigo = 'VETERINARIA_PRO';

UPDATE plan_catalogo SET precio_mensual =  69.00 WHERE codigo = 'TIENDA_ROPA_BASICO';
UPDATE plan_catalogo SET precio_mensual = 119.00 WHERE codigo = 'TIENDA_ROPA_PRO';

UPDATE plan_catalogo SET precio_mensual = 139.00 WHERE codigo = 'TIENDA_BODEGA_PRO';

UPDATE plan_catalogo SET precio_mensual =  89.00 WHERE codigo = 'FERRETERIA_BASICO';
UPDATE plan_catalogo SET precio_mensual = 169.00 WHERE codigo = 'FERRETERIA_PRO';

UPDATE plan_catalogo SET precio_mensual =  89.00 WHERE codigo = 'MINIMARKET_BASICO';
UPDATE plan_catalogo SET precio_mensual = 169.00 WHERE codigo = 'MINIMARKET_PRO';

UPDATE plan_catalogo SET precio_mensual =  89.00 WHERE codigo = 'EMPRESA_SERVICIOS_BASICO';
UPDATE plan_catalogo SET precio_mensual = 169.00 WHERE codigo = 'EMPRESA_SERVICIOS_PRO';
