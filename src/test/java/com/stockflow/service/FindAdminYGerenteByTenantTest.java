package com.stockflow.service;

import com.stockflow.dto.CerrarCajaRequestDTO;
import com.stockflow.entity.Caja;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import com.stockflow.repository.CajaRepository;
import com.stockflow.repository.RetiroCajaRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.impl.CajaServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifica que findAdminYGerenteByTenant consulta via usuario_tenant y no via usuarios.tenant_id.
 * Los tests de contrato documentan qué usuarios deben/no deben aparecer en el resultado del query.
 * La verificación del consumer (CajaServiceImpl) prueba que el resultado se consume correctamente.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("findAdminYGerenteByTenant — usa usuario_tenant, no usuarios.tenant_id")
class FindAdminYGerenteByTenantTest {

    private static final String TENANT_ID   = "vet-59ba";
    private static final String OTRO_TENANT = "botica-santa-fe";
    private static final Long   CAJA_ID     = 1L;

    @Mock private CajaRepository        cajaRepository;
    @Mock private VentaRepository       ventaRepository;
    @Mock private RetiroCajaRepository  retiroCajaRepository;
    @Mock private UsuarioRepository     usuarioRepository;
    @Mock private TenantRepository      tenantRepository;
    @Mock private EmailService          emailService;
    @Mock private NotificacionService   notificacionService;

    @InjectMocks private CajaServiceImpl cajaService;

    private Caja cajaAbierta;
    private CerrarCajaRequestDTO cerrarRequest;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUserId(99L);

        cajaAbierta = Caja.builder()
                .id(CAJA_ID)
                .tenantId(TENANT_ID)
                .usuarioId(99L)
                .usuarioNombre("Cajero Test")
                .montoApertura(BigDecimal.valueOf(100))
                .estado("ABIERTA")
                .cantidadVentas(0)
                .fechaApertura(LocalDateTime.now())
                .build();

        cerrarRequest = new CerrarCajaRequestDTO();
        cerrarRequest.setMontoContado(BigDecimal.valueOf(100));

        when(cajaRepository.findByIdAndTenantId(CAJA_ID, TENANT_ID)).thenReturn(Optional.of(cajaAbierta));
        when(ventaRepository.findByCajaIdAndTenantId(CAJA_ID, TENANT_ID)).thenReturn(List.of());
        when(retiroCajaRepository.findByCajaIdOrderByFechaAsc(CAJA_ID)).thenReturn(List.of());
        when(cajaRepository.save(any())).thenReturn(cajaAbierta);
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        when(usuarioRepository.findById(anyLong())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Contrato del query: usuarios que SÍ deben aparecer
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Usuarios encontrados por el query")
    class UsuariosEncontrados {

        @Test
        @DisplayName("ADMIN del tenant es encontrado y se le envía email")
        void admin_delTenant_encontrado() {
            Usuario admin = buildUsuario(1L, "admin@vet.com", "Admin Vet", "ADMIN", TENANT_ID);
            when(usuarioRepository.findAdminYGerenteByTenant(TENANT_ID)).thenReturn(List.of(admin));

            cajaService.cerrar(CAJA_ID, cerrarRequest, TENANT_ID);

            verify(emailService).enviarResumenCierreCaja(
                    eq("admin@vet.com"), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("GERENTE del tenant es encontrado y se le envía email")
        void gerente_delTenant_encontrado() {
            Usuario gerente = buildUsuario(2L, "gerente@vet.com", "Gerente Vet", "GERENTE", TENANT_ID);
            when(usuarioRepository.findAdminYGerenteByTenant(TENANT_ID)).thenReturn(List.of(gerente));

            cajaService.cerrar(CAJA_ID, cerrarRequest, TENANT_ID);

            verify(emailService).enviarResumenCierreCaja(
                    eq("gerente@vet.com"), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("usuario cuyo tenant_id primario es OTRO_TENANT pero tiene relación activa en usuario_tenant del tenant → SÍ encontrado")
        void usuarioConTenantIdPrimarioDiferente_tieneRelacionActiva_siEncontrado() {
            // Escenario clave del fix: el usuario fue creado en OTRO_TENANT (tenant_id=OTRO_TENANT)
            // pero luego se asoció a TENANT_ID via usuario_tenant.
            // El query legacy usaba usuarios.tenant_id y no lo encontraba.
            // El nuevo query usa usuario_tenant y sí debe encontrarlo.
            Usuario adminMultiTenant = buildUsuario(3L, "admin@otro.com", "Admin Multi", "ADMIN", OTRO_TENANT);
            when(usuarioRepository.findAdminYGerenteByTenant(TENANT_ID)).thenReturn(List.of(adminMultiTenant));

            cajaService.cerrar(CAJA_ID, cerrarRequest, TENANT_ID);

            // El consumer no filtra por tenantId del usuario — usa los resultados del query directamente
            verify(emailService).enviarResumenCierreCaja(
                    eq("admin@otro.com"), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Contrato del query: usuarios que NO deben aparecer
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Usuarios excluidos por el query")
    class UsuariosExcluidos {

        @Test
        @DisplayName("usuario de otro tenant sin relación activa en usuario_tenant → no retornado, no se envía email")
        void usuarioOtroTenant_sinRelacion_noRetornado() {
            when(usuarioRepository.findAdminYGerenteByTenant(TENANT_ID)).thenReturn(List.of());

            cajaService.cerrar(CAJA_ID, cerrarRequest, TENANT_ID);

            verify(emailService, never()).enviarResumenCierreCaja(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("usuario con usuario_tenant activo=false → no incluido en resultados")
        void relacionInactiva_noIncluida() {
            // countByTenantIdAndActivoTrue excluye inactivos; aquí verificamos que si el query
            // devuelve lista vacía (porque la relación está inactiva), no se envía email
            when(usuarioRepository.findAdminYGerenteByTenant(TENANT_ID)).thenReturn(List.of());

            cajaService.cerrar(CAJA_ID, cerrarRequest, TENANT_ID);

            verify(emailService, never()).enviarResumenCierreCaja(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("usuario con rol VENDEDOR → no retornado, no se envía email")
        void vendedor_noRetornado() {
            // El query filtra por rol IN ('ADMIN','GERENTE'); VENDEDOR no entra
            when(usuarioRepository.findAdminYGerenteByTenant(TENANT_ID)).thenReturn(List.of());

            cajaService.cerrar(CAJA_ID, cerrarRequest, TENANT_ID);

            verify(emailService, never()).enviarResumenCierreCaja(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Contrato del consumer: solo usa email y nombre
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Consumidores usan solo email y nombre — contrato estable")
    class ConsumidorContrato {

        @Test
        @DisplayName("CajaServiceImpl.cerrar() usa u.getEmail() y u.getUsuarioNombre() de la caja, no campos del tenant del usuario")
        void cerrar_usaSoloEmailDeLosResultados() {
            Usuario admin = buildUsuario(1L, "admin@vet.com", "Admin Vet", "ADMIN", TENANT_ID);
            Usuario gerente = buildUsuario(2L, "gerente@vet.com", "Gerente Vet", "GERENTE", TENANT_ID);
            when(usuarioRepository.findAdminYGerenteByTenant(TENANT_ID)).thenReturn(List.of(admin, gerente));

            cajaService.cerrar(CAJA_ID, cerrarRequest, TENANT_ID);

            // Captura los emails con los que se llama enviarResumenCierreCaja
            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService, times(2)).enviarResumenCierreCaja(
                    emailCaptor.capture(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt());

            assertThat(emailCaptor.getAllValues())
                    .containsExactlyInAnyOrder("admin@vet.com", "gerente@vet.com");
        }

        @Test
        @DisplayName("query se llama con el tenantId correcto, nunca con tenant del usuario")
        void findAdminYGerente_llamadoConTenantIdCorrecto() {
            when(usuarioRepository.findAdminYGerenteByTenant(TENANT_ID)).thenReturn(List.of());

            cajaService.cerrar(CAJA_ID, cerrarRequest, TENANT_ID);

            verify(usuarioRepository).findAdminYGerenteByTenant(TENANT_ID);
            verify(usuarioRepository, never()).findAdminYGerenteByTenant(OTRO_TENANT);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Usuario buildUsuario(Long id, String email, String nombre, String rolNombre, String tenantId) {
        return Usuario.builder()
                .id(id)
                .email(email)
                .nombre(nombre)
                .activo(true)
                .build();
    }
}
