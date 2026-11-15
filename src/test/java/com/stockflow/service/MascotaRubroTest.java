package com.stockflow.service;

import com.stockflow.entity.Tenant;
import com.stockflow.exception.ForbiddenException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.TenantRepository;
import com.stockflow.service.impl.TenantServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantServiceImpl — requireRubro bloquea módulo Veterinaria por rubro")
class MascotaRubroTest {

    private static final String TENANT_ID = "test-tenant-1234";

    @Mock private TenantRepository       tenantRepository;
    @Mock private com.stockflow.repository.UsuarioRepository          usuarioRepository;
    @Mock private com.stockflow.repository.UsuarioTenantRepository    usuarioTenantRepository;
    @Mock private com.stockflow.repository.ProductoRepository         productoRepository;
    @Mock private com.stockflow.repository.VentaRepository            ventaRepository;
    @Mock private com.stockflow.repository.ProveedorRepository        proveedorRepository;
    @Mock private com.stockflow.repository.SuscripcionRepository      suscripcionRepository;

    @InjectMocks private TenantServiceImpl tenantService;

    private Tenant tenantConRubro(String rubro) {
        return Tenant.builder().tenantId(TENANT_ID).rubro(rubro).activo(true).build();
    }

    // ── VETERINARIA → acceso permitido ───────────────────────────────────────

    @Test
    @DisplayName("VETERINARIA → requireRubro no lanza excepción")
    void requireRubro_veterinaria_permitido() {
        when(tenantRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(tenantConRubro("VETERINARIA")));

        assertThatCode(() -> tenantService.requireRubro(TENANT_ID, "VETERINARIA"))
                .doesNotThrowAnyException();
    }

    // ── Rubros no veterinaria → ForbiddenException ───────────────────────────

    @Test
    @DisplayName("BOTICA → requireRubro lanza ForbiddenException")
    void requireRubro_botica_lanzaForbidden() {
        when(tenantRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(tenantConRubro("BOTICA")));

        assertThatThrownBy(() -> tenantService.requireRubro(TENANT_ID, "VETERINARIA"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("BOTICA");
    }

    @Test
    @DisplayName("FARMACIA → requireRubro lanza ForbiddenException")
    void requireRubro_farmacia_lanzaForbidden() {
        when(tenantRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(tenantConRubro("FARMACIA")));

        assertThatThrownBy(() -> tenantService.requireRubro(TENANT_ID, "VETERINARIA"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("FARMACIA");
    }

    @Test
    @DisplayName("FERRETERIA → requireRubro lanza ForbiddenException")
    void requireRubro_ferreteria_lanzaForbidden() {
        when(tenantRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(tenantConRubro("FERRETERIA")));

        assertThatThrownBy(() -> tenantService.requireRubro(TENANT_ID, "VETERINARIA"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("FERRETERIA");
    }

    @Test
    @DisplayName("OTRO → requireRubro lanza ForbiddenException")
    void requireRubro_otro_lanzaForbidden() {
        when(tenantRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(tenantConRubro("OTRO")));

        assertThatThrownBy(() -> tenantService.requireRubro(TENANT_ID, "VETERINARIA"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("OTRO");
    }

    @Test
    @DisplayName("rubro NULL → tratado como OTRO → lanza ForbiddenException")
    void requireRubro_rubroNull_tratadoComoOtro_lanzaForbidden() {
        when(tenantRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(tenantConRubro(null)));

        assertThatThrownBy(() -> tenantService.requireRubro(TENANT_ID, "VETERINARIA"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("OTRO");
    }

    @Test
    @DisplayName("MINIMARKET → requireRubro lanza ForbiddenException")
    void requireRubro_minimarket_lanzaForbidden() {
        when(tenantRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.of(tenantConRubro("MINIMARKET")));

        assertThatThrownBy(() -> tenantService.requireRubro(TENANT_ID, "VETERINARIA"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("MINIMARKET");
    }

    @Test
    @DisplayName("tenant inexistente → ResourceNotFoundException")
    void requireRubro_tenantNoExiste_lanzaNotFound() {
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.requireRubro(TENANT_ID, "VETERINARIA"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
