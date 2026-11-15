package com.stockflow.service;

import com.stockflow.dto.superadmin.*;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.*;
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

    @Mock private TenantRepository        tenantRepository;
    @Mock private SuscripcionRepository   suscripcionRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private ProductoRepository      productoRepository;
    @Mock private VentaRepository         ventaRepository;
    @Mock private ComprobanteRepository   comprobanteRepository;

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
                .ruc("20123456789").emailContacto("admin@empresa-a.com")
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
                .preapprovalId("pre-123").ultimos4Digitos("4242")
                .metodoPago("TARJETA")
                .currentPeriodEnd(LocalDateTime.of(2026, 12, 1, 0, 0))
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .fechaInicio(LocalDateTime.now().minusDays(30))
                .fechaProximoCobro(LocalDateTime.now().plusDays(1))
                .build();

        susTrialB = Suscripcion.builder()
                .id(20L).tenantId(TENANT_B)
                .planId("plan-basico").planCodigo("MINIMARKET_BASICO")
                .precioMensual(new BigDecimal("89.00")).estado("TRIAL")
                .trialEndDate(LocalDateTime.now().plusDays(7))
                .build();

        // Default stubs for count repos (return 0 unless overridden per test)
        when(usuarioTenantRepository.countByTenantIdAndActivoTrue(anyString())).thenReturn(0L);
        when(productoRepository.countByTenantId(anyString())).thenReturn(0L);
        when(ventaRepository.countByTenantId(anyString())).thenReturn(0L);
        when(comprobanteRepository.countByTenantId(anyString())).thenReturn(0L);
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
    @DisplayName("obtenerFinanzas — MRR actual, MRR anual, cantBasico, tenantsActivos y tenantsConOse correctos")
    void obtenerFinanzas_calculaCorrecto() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A)).thenReturn(Optional.of(susActivaA));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_B)).thenReturn(Optional.of(susTrialB));

        SuperAdminFinanzasDTO fin = service.obtenerFinanzas();

        assertThat(fin.getMrrActual()).isEqualByComparingTo("89.00");
        assertThat(fin.getMrrAnualProyectado()).isEqualByComparingTo("1068.00");
        assertThat(fin.getCantBasico()).isEqualTo(1);
        assertThat(fin.getCantPro()).isEqualTo(0);
        assertThat(fin.getTenantsActivos()).isEqualTo(1);
        assertThat(fin.getTenantsConOse()).isEqualTo(1); // tenantA tiene oseToken
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

    // ── 9. TenantResumenDTO — datos completos (nuevos campos) ────────────────

    @Test
    @DisplayName("toResumen — incluye ruc, emailContacto, oseUrl, oseToken y campos de suscripción extendida")
    void toResumen_datosCompletos() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A)).thenReturn(Optional.of(susActivaA));

        List<TenantResumenDTO> tenants = service.listarTenants();
        TenantResumenDTO dto = tenants.get(0);

        assertThat(dto.getRuc()).isEqualTo("20123456789");
        assertThat(dto.getEmailContacto()).isEqualTo("admin@empresa-a.com");
        assertThat(dto.getOseUrl()).isEqualTo("https://ose.test");
        assertThat(dto.getOseToken()).isEqualTo("tok-ose");
        assertThat(dto.getSuscripcionId()).isEqualTo(10L);
        assertThat(dto.getPreapprovalId()).isEqualTo("pre-123");
        assertThat(dto.getUltimos4Digitos()).isEqualTo("4242");
        assertThat(dto.getMetodoPago()).isEqualTo("TARJETA");
        assertThat(dto.getCurrentPeriodEnd()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(dto.getSuscripcionCreadaEn()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    // ── 10. Conteos por tenant ────────────────────────────────────────────────

    @Test
    @DisplayName("toResumen — totalUsuarios, totalProductos, totalVentas, totalComprobantes correctos")
    void toResumen_conteosPorTenant() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A)).thenReturn(Optional.of(susActivaA));
        when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_A)).thenReturn(5L);
        when(productoRepository.countByTenantId(TENANT_A)).thenReturn(100L);
        when(ventaRepository.countByTenantId(TENANT_A)).thenReturn(200L);
        when(comprobanteRepository.countByTenantId(TENANT_A)).thenReturn(150L);

        TenantResumenDTO dto = service.listarTenants().get(0);

        assertThat(dto.getTotalUsuarios()).isEqualTo(5);
        assertThat(dto.getTotalProductos()).isEqualTo(100);
        assertThat(dto.getTotalVentas()).isEqualTo(200);
        assertThat(dto.getTotalComprobantes()).isEqualTo(150);
    }

    // ── 11. Sin suscripción — campos opcionales como null ────────────────────

    @Test
    @DisplayName("toResumen — tenant sin suscripción tiene campos de suscripción nulos pero conteos presentes")
    void toResumen_sinSuscripcion_camposNulos() {
        Tenant tenantSinSus = Tenant.builder()
                .id(5L).tenantId("sin-sus").nombre("Sin Suscripcion").activo(true).build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenantSinSus));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc("sin-sus")).thenReturn(Optional.empty());
        when(usuarioTenantRepository.countByTenantIdAndActivoTrue("sin-sus")).thenReturn(2L);
        when(productoRepository.countByTenantId("sin-sus")).thenReturn(10L);
        when(ventaRepository.countByTenantId("sin-sus")).thenReturn(0L);
        when(comprobanteRepository.countByTenantId("sin-sus")).thenReturn(0L);

        TenantResumenDTO dto = service.listarTenants().get(0);

        assertThat(dto.getSuscripcionId()).isNull();
        assertThat(dto.getPlanId()).isNull();
        assertThat(dto.getPreapprovalId()).isNull();
        assertThat(dto.getMetodoPago()).isNull();
        assertThat(dto.getTotalUsuarios()).isEqualTo(2);
        assertThat(dto.getTotalProductos()).isEqualTo(10);
    }

    // ── 12. MRR actual y MRR anual proyectado ────────────────────────────────

    @Test
    @DisplayName("obtenerFinanzas — mrrAnualProyectado es mrrActual * 12")
    void obtenerFinanzas_mrrAnual() {
        Suscripcion susActiva = Suscripcion.builder()
                .id(1L).tenantId(TENANT_A).planCodigo("FERRETERIA_PRO")
                .precioMensual(new BigDecimal("89.00")).estado("ACTIVA").build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A)).thenReturn(Optional.of(susActiva));

        SuperAdminFinanzasDTO fin = service.obtenerFinanzas();

        assertThat(fin.getMrrActual()).isEqualByComparingTo("89.00");
        assertThat(fin.getMrrAnualProyectado()).isEqualByComparingTo("1068.00");
    }

    // ── 13. Aislamiento entre tenants ────────────────────────────────────────

    @Test
    @DisplayName("toResumen — countBy se llama con el tenantId correcto para cada tenant")
    void toResumen_aislamientoEntreTenantsEnConteos() {
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_A)).thenReturn(Optional.of(susActivaA));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_B)).thenReturn(Optional.of(susTrialB));
        when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_A)).thenReturn(3L);
        when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_B)).thenReturn(7L);
        when(productoRepository.countByTenantId(TENANT_A)).thenReturn(50L);
        when(productoRepository.countByTenantId(TENANT_B)).thenReturn(80L);

        List<TenantResumenDTO> tenants = service.listarTenants();

        TenantResumenDTO dtoA = tenants.stream().filter(t -> TENANT_A.equals(t.getTenantId())).findFirst().orElseThrow();
        TenantResumenDTO dtoB = tenants.stream().filter(t -> TENANT_B.equals(t.getTenantId())).findFirst().orElseThrow();

        assertThat(dtoA.getTotalUsuarios()).isEqualTo(3);
        assertThat(dtoB.getTotalUsuarios()).isEqualTo(7);
        assertThat(dtoA.getTotalProductos()).isEqualTo(50);
        assertThat(dtoB.getTotalProductos()).isEqualTo(80);

        // Verificar que se usaron los IDs correctos en las llamadas
        verify(usuarioTenantRepository).countByTenantIdAndActivoTrue(TENANT_A);
        verify(usuarioTenantRepository).countByTenantIdAndActivoTrue(TENANT_B);
        verify(productoRepository).countByTenantId(TENANT_A);
        verify(productoRepository).countByTenantId(TENANT_B);
    }
}
