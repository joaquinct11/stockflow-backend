package com.stockflow.service;

import com.stockflow.config.RolePermissionDefaults;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que TECNICO_SERVICIO recibe exactamente los permisos operativos de OT
 * y NO recibe permisos de caja, facturación, administración de usuarios ni configuración.
 */
class TecnicoServicioPermisosTest {

    private final RolePermissionDefaults defaults = new RolePermissionDefaults();

    @Test
    void TS1_tecnicoServicio_tienePermisosOperativosOT() {
        Set<String> permisos = defaults.getBasePermissions("TECNICO_SERVICIO");

        assertThat(permisos).contains(
                "VER_DASHBOARD",
                "VER_OT", "CAMBIAR_ESTADO_OT",
                "REGISTRAR_CONSUMO_OT", "DEVOLVER_CONSUMO_OT", "CLASIFICAR_CONSUMO_OT",
                "CERRAR_TECNICO_OT",
                "VER_EQUIPOS",
                "VER_CLIENTES",
                "VER_PRODUCTOS",
                "VER_SUCURSALES"
        );
    }

    @Test
    void TS2_tecnicoServicio_noTienePermisoCaja() {
        Set<String> permisos = defaults.getBasePermissions("TECNICO_SERVICIO");

        assertThat(permisos).doesNotContain(
                "VER_CAJA", "ABRIR_CAJA", "CERRAR_CAJA", "RETIRO_CAJA"
        );
    }

    @Test
    void TS3_tecnicoServicio_noTienePermisoFacturacion() {
        Set<String> permisos = defaults.getBasePermissions("TECNICO_SERVICIO");

        assertThat(permisos).doesNotContain(
                "FACTURAR_OT", "REGISTRAR_ENTREGA_OT", "ANULAR_FACTURA_OT",
                "VER_FACTURACION", "EMITIR_COMPROBANTE", "ANULAR_COMPROBANTE"
        );
    }

    @Test
    void TS4_tecnicoServicio_noTienePermisoAdminUsuarios() {
        Set<String> permisos = defaults.getBasePermissions("TECNICO_SERVICIO");

        assertThat(permisos).doesNotContain(
                "VER_USUARIOS", "CREAR_USUARIO", "EDITAR_USUARIO", "ELIMINAR_USUARIO",
                "CAMBIAR_ESTADO_USUARIO", "VER_PERMISOS"
        );
    }

    @Test
    void TS5_tecnicoServicio_noTienePermisoVentas() {
        Set<String> permisos = defaults.getBasePermissions("TECNICO_SERVICIO");

        assertThat(permisos).doesNotContain(
                "CREAR_VENTA", "VER_VENTAS", "ANULAR_VENTA",
                "CREAR_OT", "EDITAR_OT", "ASIGNAR_TECNICO_OT"
        );
    }

    @Test
    void TS6_tecnicoServicio_noTienePermisoSuscripciones() {
        Set<String> permisos = defaults.getBasePermissions("TECNICO_SERVICIO");

        assertThat(permisos).doesNotContain(
                "VER_SUSCRIPCIONES", "CREAR_SUSCRIPCION", "EDITAR_SUSCRIPCION",
                "ELIMINAR_SUSCRIPCION", "CAMBIAR_ESTADO_SUSCRIPCION"
        );
    }

    @Test
    void TS7_rolDesconocido_devuelveSetVacio() {
        Set<String> permisos = defaults.getBasePermissions("ROL_INEXISTENTE");
        assertThat(permisos).isEmpty();
    }
}
