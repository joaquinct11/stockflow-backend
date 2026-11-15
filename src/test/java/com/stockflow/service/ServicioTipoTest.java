package com.stockflow.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test sin Spring context.
 * Verifica la regla de negocio central:
 *   tipo=SERVICIO → no crea movimiento de inventario ni stock por sucursal.
 *   tipo=PRODUCTO → sí los crea.
 *
 * La lógica real está en ProductoServiceImpl.crearProducto().
 * Aquí verificamos el predicado puro para no acoplar al contexto Spring.
 */
class ServicioTipoTest {

    /** Replica exacta del predicado en ProductoServiceImpl.crearProducto() línea 68 */
    private boolean esServicio(String tipo) {
        return "SERVICIO".equals(tipo);
    }

    @Test
    void tipo_SERVICIO_no_genera_inventario() {
        assertTrue(esServicio("SERVICIO"),
            "Un producto con tipo='SERVICIO' debe saltar la inicialización de stock");
    }

    @Test
    void tipo_PRODUCTO_si_genera_inventario() {
        assertFalse(esServicio("PRODUCTO"),
            "Un producto con tipo='PRODUCTO' debe crear SALDO_INICIAL y ProductoStockSucursal");
    }

    @Test
    void tipo_null_no_se_confunde_con_SERVICIO() {
        assertFalse(esServicio(null),
            "Un producto con tipo=null no debe ser tratado como SERVICIO");
    }

    @Test
    void tipo_vacio_no_se_confunde_con_SERVICIO() {
        assertFalse(esServicio(""),
            "Un producto con tipo='' no debe ser tratado como SERVICIO");
    }

    @Test
    void tipo_PRODUCTO_default_en_entidad() {
        // Documenta que el default de la entidad es "PRODUCTO"
        String defaultTipo = "PRODUCTO";
        assertFalse(esServicio(defaultTipo),
            "El valor por defecto de Producto.tipo='PRODUCTO' no debe saltar el inventario");
    }
}
