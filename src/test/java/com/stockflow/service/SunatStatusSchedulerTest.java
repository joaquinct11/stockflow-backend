package com.stockflow.service;

import com.stockflow.dto.ApiSunatComprobanteResponse;
import com.stockflow.dto.ApiSunatComprobanteResponse.Payload;
import com.stockflow.entity.Comprobante;
import com.stockflow.entity.Tenant;
import com.stockflow.repository.ComprobanteRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.service.impl.ApiSunatService;
import com.stockflow.service.impl.SunatStatusScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SunatStatusScheduler — paginación cursor + manejo de estados")
class SunatStatusSchedulerTest {

    @Mock private ComprobanteRepository comprobanteRepository;
    @Mock private TenantRepository      tenantRepository;
    @Mock private ApiSunatService       apiSunatService;

    private SunatStatusScheduler scheduler;

    private static final String TENANT_ID  = "tenant-test";
    private static final String OSE_TOKEN  = "token-ose-test";

    @BeforeEach
    void setUp() {
        scheduler = new SunatStatusScheduler(comprobanteRepository, tenantRepository, apiSunatService);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Comprobante comprobante(long id) {
        return Comprobante.builder()
                .id(id)
                .tenantId(TENANT_ID)
                .tipo("BOLETA")
                .serie("B001")
                .correlativo((int) id)
                .numero("B001-" + String.format("%08d", id))
                .sunatEstado("PENDIENTE")
                .estado("EMITIDO")
                .build();
    }

    private List<Comprobante> comprobantes(long desde, long hasta) {
        List<Comprobante> lista = new ArrayList<>();
        for (long i = desde; i <= hasta; i++) lista.add(comprobante(i));
        return lista;
    }

    private Tenant tenantConToken() {
        Tenant t = new Tenant();
        t.setOseToken(OSE_TOKEN);
        return t;
    }

    private ApiSunatComprobanteResponse respuestaAceptado() {
        Payload p = new Payload();
        p.setEstado("ACEPTADO");
        p.setHash("abc123");
        ApiSunatComprobanteResponse r = new ApiSunatComprobanteResponse();
        r.setSuccess(true);
        r.setMessage("Aceptado por SUNAT");
        r.setPayload(p);
        return r;
    }

    private ApiSunatComprobanteResponse respuestaRechazado() {
        Payload p = new Payload();
        p.setEstado("RECHAZADO");
        ApiSunatComprobanteResponse r = new ApiSunatComprobanteResponse();
        r.setSuccess(true);
        r.setMessage("Rechazado por SUNAT");
        r.setPayload(p);
        return r;
    }

    private ApiSunatComprobanteResponse respuestaPendienteOse() {
        Payload p = new Payload();
        p.setEstado("PENDIENTE");
        ApiSunatComprobanteResponse r = new ApiSunatComprobanteResponse();
        r.setSuccess(false);
        r.setPayload(p);
        return r;
    }

    // ── Test cases ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SCH-01: Sin comprobantes PENDIENTES → no llama al OSE, no guarda nada")
    void sch01_sinPendientes_noCalls() {
        when(comprobanteRepository.findPendientesAfterIdOrderById(anyLong(), any(Pageable.class)))
                .thenReturn(List.of());

        scheduler.actualizarPendientes();

        verify(apiSunatService, never()).consultarEstado(any(), any(), any(), any());
        verify(comprobanteRepository, never()).save(any());
    }

    @Test
    @DisplayName("SCH-02: 1 PENDIENTE, OSE responde ACEPTADO → guarda con estado ACEPTADO")
    void sch02_unPendienteAceptado_guardaAceptado() {
        Comprobante c = comprobante(1L);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(c));
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenantConToken()));
        when(apiSunatService.consultarEstado(any(), any(), any(), any()))
                .thenReturn(respuestaAceptado());

        scheduler.actualizarPendientes();

        ArgumentCaptor<Comprobante> cap = ArgumentCaptor.forClass(Comprobante.class);
        verify(comprobanteRepository).save(cap.capture());
        assertThat(cap.getValue().getSunatEstado()).isEqualTo("ACEPTADO");
        assertThat(cap.getValue().getSunatMensaje()).isEqualTo("Aceptado por SUNAT");
    }

    @Test
    @DisplayName("SCH-03: 1 PENDIENTE, OSE responde RECHAZADO → guarda con estado RECHAZADO")
    void sch03_unPendienteRechazado_guardaRechazado() {
        Comprobante c = comprobante(1L);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(c));
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenantConToken()));
        when(apiSunatService.consultarEstado(any(), any(), any(), any()))
                .thenReturn(respuestaRechazado());

        scheduler.actualizarPendientes();

        ArgumentCaptor<Comprobante> cap = ArgumentCaptor.forClass(Comprobante.class);
        verify(comprobanteRepository).save(cap.capture());
        assertThat(cap.getValue().getSunatEstado()).isEqualTo("RECHAZADO");
    }

    @Test
    @DisplayName("SCH-04: 1 PENDIENTE, OSE sigue diciendo PENDIENTE → no guarda")
    void sch04_oseDevuelvePendiente_noGuarda() {
        Comprobante c = comprobante(1L);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(c));
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenantConToken()));
        when(apiSunatService.consultarEstado(any(), any(), any(), any()))
                .thenReturn(respuestaPendienteOse());

        scheduler.actualizarPendientes();

        verify(comprobanteRepository, never()).save(any());
    }

    @Test
    @DisplayName("SCH-05: Tenant sin oseToken → no llama al OSE")
    void sch05_tenantSinToken_noLlamaOse() {
        Comprobante c = comprobante(1L);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(c));
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());
        Tenant sinToken = new Tenant();
        sinToken.setOseToken(null);
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(sinToken));

        scheduler.actualizarPendientes();

        verify(apiSunatService, never()).consultarEstado(any(), any(), any(), any());
        verify(comprobanteRepository, never()).save(any());
    }

    @Test
    @DisplayName("SCH-06: OSE lanza excepción → continúa con el siguiente, incrementa errorCount")
    void sch06_oseExcepcion_continuaConSiguiente() {
        Comprobante c1 = comprobante(1L);
        Comprobante c2 = comprobante(2L);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(c1, c2));
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(2L), any(Pageable.class)))
                .thenReturn(List.of());
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenantConToken()));
        when(apiSunatService.consultarEstado(any(), any(), eq(1), any()))
                .thenThrow(new RuntimeException("Timeout OSE"));
        when(apiSunatService.consultarEstado(any(), any(), eq(2), any()))
                .thenReturn(respuestaAceptado());

        scheduler.actualizarPendientes();

        // c1 falló pero c2 se guardó
        ArgumentCaptor<Comprobante> cap = ArgumentCaptor.forClass(Comprobante.class);
        verify(comprobanteRepository, times(1)).save(cap.capture());
        assertThat(cap.getValue().getId()).isEqualTo(2L);
        assertThat(cap.getValue().getSunatEstado()).isEqualTo("ACEPTADO");
    }

    @Test
    @DisplayName("SCH-07: 55 comprobantes → 2 lotes (50 + 5), cursor avanza correctamente")
    void sch07_55comprobantes_dosLotes() {
        List<Comprobante> lote1 = comprobantes(1, 50);
        List<Comprobante> lote2 = comprobantes(51, 55);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(lote1);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(50L), any(Pageable.class)))
                .thenReturn(lote2);
        // lote2.size()=5 < BATCH_SIZE=50 → el loop termina sin una tercera llamada
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenantConToken()));
        when(apiSunatService.consultarEstado(any(), any(), any(), any()))
                .thenReturn(respuestaAceptado());

        scheduler.actualizarPendientes();

        // 2 lotes procesados → se consultó el OSE 55 veces
        verify(apiSunatService, times(55)).consultarEstado(any(), any(), any(), any());
        // cursor correcto: solo 2 llamadas al repositorio (lote1 → lote2 menor que batch → break)
        verify(comprobanteRepository, times(2))
                .findPendientesAfterIdOrderById(anyLong(), any(Pageable.class));
        verify(comprobanteRepository).findPendientesAfterIdOrderById(eq(0L), any(Pageable.class));
        verify(comprobanteRepository).findPendientesAfterIdOrderById(eq(50L), any(Pageable.class));
    }

    @Test
    @DisplayName("SCH-08: Tenant no encontrado en DB → se salta, no llama al OSE")
    void sch08_tenantNoEncontrado_saltaComprobante() {
        Comprobante c = comprobante(1L);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(c));
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        scheduler.actualizarPendientes();

        verify(apiSunatService, never()).consultarEstado(any(), any(), any(), any());
        verify(comprobanteRepository, never()).save(any());
    }

    @Test
    @DisplayName("SCH-09: 100 comprobantes exactos → 2 lotes de 50, tercer lote vacío termina el ciclo")
    void sch09_100comprobantesExactos_tresCalls() {
        List<Comprobante> lote1 = comprobantes(1, 50);
        List<Comprobante> lote2 = comprobantes(51, 100);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(lote1);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(50L), any(Pageable.class)))
                .thenReturn(lote2);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(100L), any(Pageable.class)))
                .thenReturn(List.of());
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenantConToken()));
        when(apiSunatService.consultarEstado(any(), any(), any(), any()))
                .thenReturn(respuestaAceptado());

        scheduler.actualizarPendientes();

        verify(apiSunatService, times(100)).consultarEstado(any(), any(), any(), any());
        // lote vacío en lastId=100 → 3 calls a findPendientes
        verify(comprobanteRepository, times(3))
                .findPendientesAfterIdOrderById(anyLong(), any(Pageable.class));
    }

    @Test
    @DisplayName("SCH-10: Mezcla ACEPTADO + RECHAZADO + excepción + skip → contadores correctos")
    void sch10_mezclaEstados_contadoresCorrectos() {
        Comprobante c1 = comprobante(1L); // ACEPTADO
        Comprobante c2 = comprobante(2L); // RECHAZADO
        Comprobante c3 = comprobante(3L); // excepción OSE
        Comprobante c4 = comprobante(4L); // skip: tenant sin token

        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(c1, c2, c3, c4));
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(4L), any(Pageable.class)))
                .thenReturn(List.of());
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenantConToken()));

        when(apiSunatService.consultarEstado(any(), any(), eq(1), any())).thenReturn(respuestaAceptado());
        when(apiSunatService.consultarEstado(any(), any(), eq(2), any())).thenReturn(respuestaRechazado());
        when(apiSunatService.consultarEstado(any(), any(), eq(3), any()))
                .thenThrow(new RuntimeException("Error OSE"));
        // c4 tiene oseToken vacío
        Tenant sinToken = new Tenant();
        sinToken.setOseToken("  ");
        // override solo para c4: necesitamos tenantId distinto o usar thenAnswer
        // usamos tenantId distinto para c4
        c4.setTenantId("tenant-sin-token");
        when(tenantRepository.findByTenantId("tenant-sin-token")).thenReturn(Optional.of(sinToken));

        scheduler.actualizarPendientes();

        // c1 y c2 guardados, c3 y c4 no
        verify(comprobanteRepository, times(2)).save(any());
        // OSE consultado 3 veces (c4 se salta antes de llegar al OSE)
        verify(apiSunatService, times(3)).consultarEstado(any(), any(), any(), any());
    }

    @Test
    @DisplayName("SCH-11: ACEPTADO con pdfUrl + xmlUrl → URLs se copian al comprobante")
    void sch11_aceptadoConUrls_copiaUrls() {
        Comprobante c = comprobante(1L);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(c));
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenantConToken()));

        ApiSunatComprobanteResponse.Payload.Pdf pdf = new ApiSunatComprobanteResponse.Payload.Pdf();
        pdf.setA4("https://ose.pe/pdf/B001-00000001.pdf");
        pdf.setTicket("https://ose.pe/pdf/ticket-B001.pdf");
        Payload payload = new Payload();
        payload.setEstado("ACEPTADO");
        payload.setXml("https://ose.pe/xml/B001-00000001.xml");
        payload.setPdf(pdf);
        ApiSunatComprobanteResponse resp = new ApiSunatComprobanteResponse();
        resp.setSuccess(true);
        resp.setMessage("Aceptado");
        resp.setPayload(payload);
        when(apiSunatService.consultarEstado(any(), any(), any(), any())).thenReturn(resp);

        scheduler.actualizarPendientes();

        ArgumentCaptor<Comprobante> cap = ArgumentCaptor.forClass(Comprobante.class);
        verify(comprobanteRepository).save(cap.capture());
        Comprobante guardado = cap.getValue();
        assertThat(guardado.getSunatEstado()).isEqualTo("ACEPTADO");
        assertThat(guardado.getPdfUrl()).isEqualTo("https://ose.pe/pdf/B001-00000001.pdf");
        assertThat(guardado.getPdfTicketUrl()).isEqualTo("https://ose.pe/pdf/ticket-B001.pdf");
        assertThat(guardado.getXmlUrl()).isEqualTo("https://ose.pe/xml/B001-00000001.xml");
    }

    @Test
    @DisplayName("SCH-12: Excepción en comprobante 2 no interrumpe comprobantes 1 y 3 en el mismo lote")
    void sch12_excepcionEnMedio_otrosComprobantesSeProcesan() {
        Comprobante c1 = comprobante(1L);
        Comprobante c2 = comprobante(2L);
        Comprobante c3 = comprobante(3L);
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(0L), any(Pageable.class)))
                .thenReturn(List.of(c1, c2, c3));
        when(comprobanteRepository.findPendientesAfterIdOrderById(eq(3L), any(Pageable.class)))
                .thenReturn(List.of());
        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenantConToken()));

        when(apiSunatService.consultarEstado(any(), any(), eq(1), any())).thenReturn(respuestaAceptado());
        when(apiSunatService.consultarEstado(any(), any(), eq(2), any()))
                .thenThrow(new RuntimeException("Error en medio del lote"));
        when(apiSunatService.consultarEstado(any(), any(), eq(3), any())).thenReturn(respuestaAceptado());

        scheduler.actualizarPendientes();

        // c1 y c3 guardados, c2 no
        ArgumentCaptor<Comprobante> cap = ArgumentCaptor.forClass(Comprobante.class);
        verify(comprobanteRepository, times(2)).save(cap.capture());
        List<Long> idsGuardados = cap.getAllValues().stream()
                .map(Comprobante::getId).toList();
        assertThat(idsGuardados).containsExactlyInAnyOrder(1L, 3L);
    }
}
