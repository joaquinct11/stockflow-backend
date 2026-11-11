package com.stockflow.controller;

import com.stockflow.dto.SuscripcionDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.mapper.SuscripcionMapper;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SuscripcionController.obtenerPorUsuario() — aislamiento multi-tenant")
class SuscripcionControllerObtenerPorUsuarioTest {

    private static final Long   USUARIO_ID    = 5L;
    private static final String TENANT_VET    = "vet-59ba";
    private static final String TENANT_BOTICA = "botica-santa-fe-596f";

    @Mock private SuscripcionService      suscripcionService;
    @Mock private UsuarioService          usuarioService;
    @Mock private SuscripcionMapper       suscripcionMapper;
    @Mock private SuscripcionRepository   suscripcionRepository;
    @Mock private SucursalRepository      sucursalRepository;
    @Mock private ProductoRepository      productoRepository;
    @Mock private UsuarioRepository       usuarioRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private CulqiService            culqiService;
    @Mock private EmailService            emailService;
    @Mock private CulqiProperties         culqiProperties;

    @InjectMocks private SuscripcionController controller;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Aislamiento por tenant ────────────────────────────────────────────────

    @Nested
    @DisplayName("Devuelve únicamente la suscripción del tenant activo")
    class AislamientoTenant {

        @Test
        @DisplayName("tenant=vet-59ba → retorna suscripción de Veterinaria, no la de Botica")
        void tenantVet_devuelveSuscripcionVet() {
            TenantContext.setCurrentTenant(TENANT_VET);
            Suscripcion susVet = Suscripcion.builder()
                    .id(2L).tenantId(TENANT_VET).planId("BASICO").estado("TRIAL").build();
            SuscripcionDTO dtoVet = new SuscripcionDTO();
            dtoVet.setId(2L);

            when(suscripcionService.obtenerSuscripcionPorUsuarioYTenant(USUARIO_ID, TENANT_VET))
                    .thenReturn(Optional.of(susVet));
            when(suscripcionMapper.toDTO(susVet)).thenReturn(dtoVet);

            ResponseEntity<SuscripcionDTO> response = controller.obtenerPorUsuario(USUARIO_ID);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("tenant=botica → retorna suscripción de Botica, no la de Veterinaria")
        void tenantBotica_devuelveSuscripcionBotica() {
            TenantContext.setCurrentTenant(TENANT_BOTICA);
            Suscripcion susBotica = Suscripcion.builder()
                    .id(1L).tenantId(TENANT_BOTICA).planId("BASICO").estado("ACTIVA").build();
            SuscripcionDTO dtoBotica = new SuscripcionDTO();
            dtoBotica.setId(1L);

            when(suscripcionService.obtenerSuscripcionPorUsuarioYTenant(USUARIO_ID, TENANT_BOTICA))
                    .thenReturn(Optional.of(susBotica));
            when(suscripcionMapper.toDTO(susBotica)).thenReturn(dtoBotica);

            ResponseEntity<SuscripcionDTO> response = controller.obtenerPorUsuario(USUARIO_ID);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("sin suscripción en el tenant activo → 404")
        void sinSuscripcionEnTenant_devuelve404() {
            TenantContext.setCurrentTenant(TENANT_VET);

            when(suscripcionService.obtenerSuscripcionPorUsuarioYTenant(USUARIO_ID, TENANT_VET))
                    .thenReturn(Optional.empty());

            ResponseEntity<SuscripcionDTO> response = controller.obtenerPorUsuario(USUARIO_ID);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
        }
    }

    // ── Regresiones ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Regresiones")
    class Regresiones {

        @Test
        @DisplayName("NO usa obtenerSuscripcionPorUsuario() — método sin tenant scope")
        void noUsaMetodoSinTenant() {
            TenantContext.setCurrentTenant(TENANT_VET);
            when(suscripcionService.obtenerSuscripcionPorUsuarioYTenant(any(), any()))
                    .thenReturn(Optional.empty());

            controller.obtenerPorUsuario(USUARIO_ID);

            verify(suscripcionService, never()).obtenerSuscripcionPorUsuario(any());
            verify(suscripcionService).obtenerSuscripcionPorUsuarioYTenant(eq(USUARIO_ID), eq(TENANT_VET));
        }

        @Test
        @DisplayName("tenantId proviene de TenantContext, no del path ni del body")
        void tenantIdVieneDeTenantContext() {
            TenantContext.setCurrentTenant(TENANT_VET);
            when(suscripcionService.obtenerSuscripcionPorUsuarioYTenant(USUARIO_ID, TENANT_VET))
                    .thenReturn(Optional.empty());

            controller.obtenerPorUsuario(USUARIO_ID);

            // Verificar que se usó exactamente el tenantId del contexto
            verify(suscripcionService).obtenerSuscripcionPorUsuarioYTenant(USUARIO_ID, TENANT_VET);
        }
    }
}
