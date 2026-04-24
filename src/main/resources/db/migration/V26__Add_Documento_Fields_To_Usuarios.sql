-- Agrega campos de identificación del pagador a la tabla de usuarios.
-- Requerido para pre-rellenar el formulario de Mercado Pago Suscripciones
-- (preapproval) y habilitar el botón "Confirmar" en el checkout.

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS tipo_documento  VARCHAR(20)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS numero_documento VARCHAR(50) DEFAULT NULL;
