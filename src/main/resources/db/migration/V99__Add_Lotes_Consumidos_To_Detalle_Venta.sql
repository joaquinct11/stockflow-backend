-- Registra exactamente qué lotes se consumieron al vender, con su cantidad.
-- Formato JSON: [[loteId1, cantidad1], [loteId2, cantidad2], ...]
-- Permite restaurar los lotes correctos al anular o devolver la venta.
ALTER TABLE detalles_venta
    ADD COLUMN lotes_consumidos_json TEXT;
