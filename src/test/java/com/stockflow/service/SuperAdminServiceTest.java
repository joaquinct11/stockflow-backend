package com.stockflow.service;

import com.stockflow.dto.superadmin.*;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.service.impl.SuperAdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SuperAdminServiceImpl — operaciones globales de administración")
class SuperAdminServiceTest {

    @Mock private TenantRepository      tenantRepository;
    @Mock private SuscripcionRepository suscripcionRepository;

    @InjectMocks private SuperAdminServiceImpl service;

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private Tenant tenantA;
    private Tenant tenantB;
    private Suscripcion susActivaA;
    private Suscripcion susTrialB;

    @BeforeEach
    void setUp() {
        tenantA = Tenant.builder()
                .id(1L).tenantId(TENANT_A).nombre("Empresa A")
                .activo(true).rubro("FERRETERIA")
                .oseUrl("https://ose.test").oseToken("tok-ose")
                .build();

        tenantB = Tenant.builder()
                .id(2L).tenantId(TENANT_B).nombre("Empresa B")
                .activo(true).rubro("MINIMARKET")
                .build();

        susActivaA = Suscripcion.builder()
                .id(10L).tenantId(TENANT_A)
                .planId("plan-basico").planCodigo("FERRETERIA_BASICO")
                .precioMensual(new BigDecimal("89.00")).estado("ACTIVA")
                .fechaInicio(LocalDateTime.now().minusDays(30))
                .fechaProximoCobro(LocalDateTime.now().plusDays(1))
                .build();

        susTrialB = Suscripcion.builder()
                .id(20L).tenantId(TENANT_B)
                .planId("plan-basico").planCodigo("MINIMARKET_BASICO")
                .precioMensual(new BigDecimal("89.00")).estado("TRIAL")
                .trialEndDate(LocalDateTime.now().plusDays(7))
                .build();
    }

    // ── 1. obtenerStats ───────────────────────────────────────────────────────

    @Test
    @DisplayName("obtenerStats — suma activos, trial y MRR correctamente")
    void obtenerStats_sumaCorrecto() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A)).thenReturn(Optional.of(susActivaA));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_B)).thenReturn(Optional.of(susTrialB));

        SuperAdminStatsDTO stats = service.obtenerStats();

        assertThat(stats.getTotalTenants()).isEqualTo(2);
        assertThat(stats.getActivos()).isEqualTo(1);
        assertThat(stats.getEnTrial()).isEqualTo(1);
        assertThat(stats.getMrrEstimado()).isEqualByComparingTo("89.00");
    }

    @Test
    @DisplayName("obtenerStats — tenant sin suscripción se ignora")
    void obtenerStats_sinSuscripcion_seIgnora() {
        Tenant tenantSinSus = Tenant.builder().id(3L).tenantId("sin-sus").nombre("Sin Sub").activo(true).build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenantSinSus));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc("sin-sus")).thenReturn(Optional.empty());

        SuperAdminStatsDTO stats = service.obtenerStats();

        assertThat(stats.getTotalTenants()).isEqualTo(1);
        assertThat(stats.getActivos()).isEqualTo(0);
        assertThat(stats.getMrrEstimado()).isEqualByComparingTo("0.00");
    }

    // ── 2. obtenerFinanzas ────────────────────────────────────────────────────

    @Test
    @DisplayName("obtenerFinanzas — MRR, cantBasico y conOse correctos")
    void obtenerFinanzas_calculaCorrecto() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A)).thenReturn(Optional.of(susActivaA));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_B)).thenReturn(Optional.of(susTrialB));

        SuperAdminFinanzasDTO fin = service.obtenerFinanzas();

        assertThat(fin.getMrr()).isEqualByComparingTo("89.00");
        assertThat(fin.getCantBasico()).isEqualTo(1);
        assertThat(fin.getCantPro()).isEqualTo(0);
        assertThat(fin.getActivos()).isEqualTo(1);
        assertThat(fin.getConOse()).isEqualTo(1); // tenantA tiene oseToken
    }

    // ── 3. listarTenants ──────────────────────────────────────────────────────

    @Test
    @DisplayName("listarTenants — retorna todos los tenants como resumen")
    void listarTenants_retornaTodos() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A)).thenReturn(Optional.of(susActivaA));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_B)).thenReturn(Optional.of(susTrialB));

        List<TenantResumenDTO> tenants = service.listarTenants();

        assertThat(tenants).hasSize(2);
        assertThat(tenants.get(0).getTenantId()).isEqualTo(TENANT_A);
        assertThat(tenants.get(0).isTieneOse()).isTrue();
        assertThat(tenants.get(1).isTieneOse()).isFalse();
    }

    // ── 4. obtenerCobros ──────────────────────────────────────────────────────

    @Test
    @DisplayName("obtenerCobros — retorna historial de suscripciones del tenant")
    void obtenerCobros_retornaHistorial() {
        when(suscripcionRepository.findByTenantId(TENANT_A)).thenReturn(List.of(susActivaA));

        List<SuscripcionHistorialDTO> cobros = service.obtenerCobros(TENANT_A);

        assertThat(cobros).hasSize(1);
        assertThat(cobros.get(0).getEstado()).isEqualTo("ACTIVA");
        assertThat(cobros.get(0).getPrecioMensual()).isEqualByComparingTo("89.00");
    }

    // ── 5. actualizarSuscripcion ──────────────────────────────────────────────

    @Test
    @DisplayName("actualizarSuscripcion — actualiza campos no nulos")
    void actualizarSuscripcion_actualizaCampos() {
        when(tenantRepository.findByTenantId(TENANT_A)).thenReturn(Optional.of(tenantA));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A)).thenReturn(Optional.of(susActivaA));
        when(suscripcionRepository.save(any())).thenReturn(susActivaA);

        SuscripcionManualUpdateDTO dto = new SuscripcionManualUpdateDTO();
        dto.setEstado("VENCIDA");
        dto.setPrecioMensual(new BigDecimal("99.00"));

        service.actualizarSuscripcion(TENANT_A, dto);

        ArgumentCaptor<Suscripcion> cap = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepository).save(cap.capture());
        assertThat(cap.getValue().getEstado()).isEqualTo("VENCIDA");
        assertThat(cap.getValue().getPrecioMensual()).isEqualByComparingTo("99.00");
        assertThat(cap.getValue().getPlanId()).isEqualTo("plan-basico"); // sin cambio
    }

    @Test
    @DisplayName("actualizarSuscripcion — tenant no encontrado lanza BadRequestException")
    void actualizarSuscripcion_tenantNoExiste_lanzaExcepcion() {
        when(tenantRepository.findByTenantId("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarSuscripcion("no-existe", new SuscripcionManualUpdateDTO()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no-existe");
    }

    // ── 6. extenderTrial ──────────────────────────────────────────────────────

    @Test
    @DisplayName("extenderTrial — suma días al trialEndDate existente")
    void extenderTrial_sumaDias() {
        LocalDateTime trialOriginal = LocalDateTime.now().plusDays(5);
        susTrialB = Suscripcion.builder().id(20L).tenantId(TENANT_B)
                .planCodigo("MINIMARKET_BASICO").estado("TRIAL")
                .trialEndDate(trialOriginal).build();

        when(tenantRepository.findByTenantId(TENANT_B)).thenReturn(Optional.of(tenantB));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_B)).thenReturn(Optional.of(susTrialB));
        when(suscripcionRepository.save(any())).thenReturn(susTrialB);

        service.extenderTrial(TENANT_B, 10);

        ArgumentCaptor<Suscripcion> cap = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepository).save(cap.capture());
        assertThat(cap.getValue().getTrialEndDate()).isEqualTo(trialOriginal.plusDays(10));
        assertThat(cap.getValue().getEstado()).isEqualTo("TRIAL");
    }

    // ── 7. toggleActivo ───────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleActivo — desactiva el tenant correctamente")
    void toggleActivo_desactivaTenant() {
        when(tenantRepository.findByTenantId(TENANT_A)).thenReturn(Optional.of(tenantA));
        when(tenantRepository.save(any())).thenReturn(tenantA);

        service.toggleActivo(TENANT_A, false);

        ArgumentCaptor<Tenant> cap = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(cap.capture());
        assertThat(cap.getValue().getActivo()).isFalse();
    }

    @Test
    @DisplayName("toggleActivo — tenant no encontrado lanza BadRequestException")
    void toggleActivo_tenantNoExiste_lanzaExcepcion() {
        when(tenantRepository.findByTenantId("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleActivo("x", true))
                .isInstanceOf(BadRequestException.class);
    }

    // ── 8. actualizarOse ──────────────────────────────────────────────────────

    @Test
    @DisplayName("actualizarOse — actualiza oseUrl y oseToken del tenant")
    void actualizarOse_actualizaCampos() {
        when(tenantRepository.findByTenantId(TENANT_A)).thenReturn(Optional.of(tenantA));
        when(tenantRepository.save(any())).thenReturn(tenantA);

        OseUpdateDTO dto = new OseUpdateDTO();
        dto.setOseUrl("https://nuevo-ose.test");
        dto.setOseToken("nuevo-tok");

        service.actualizarOse(TENANT_A, dto);

        ArgumentCaptor<Tenant> cap = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(cap.capture());
        assertThat(cap.getValue().getOseUrl()).isEqualTo("https://nuevo-ose.test");
        assertThat(cap.getValue().getOseToken()).isEqualTo("nuevo-tok");
    }

    @Test
    @DisplayName("actualizarOse — tenant no encontrado lanza BadRequestException")
    void actualizarOse_tenantNoExiste_lanzaExcepcion() {
        when(tenantRepository.findByTenantId("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarOse("no-existe", new OseUpdateDTO()))
                .isInstanceOf(BadRequestException.class);
    }
}
