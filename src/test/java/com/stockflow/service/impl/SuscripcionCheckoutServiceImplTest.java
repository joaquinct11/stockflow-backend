package com.stockflow.service.impl;

import com.stockflow.dto.MercadoPagoWebhookRequestDTO;
import com.stockflow.dto.SuscripcionCheckoutResponseDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.MercadoPagoService;
import com.stockflow.service.UsuarioService;
import com.stockflow.service.model.MercadoPagoPaymentInfo;
import com.stockflow.service.model.MercadoPagoPreapprovalInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuscripcionCheckoutServiceImplTest {

    @Mock
    private SuscripcionRepository suscripcionRepository;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private MercadoPagoService mercadoPagoService;

    @InjectMocks
    private SuscripcionCheckoutServiceImpl suscripcionCheckoutService;

    // ── precio plan mapping ──────────────────────────────────────────────────

    @Test
    void obtenerPrecioPlan_basico_retorna4999() {
        assertThat(suscripcionCheckoutService.obtenerPrecioPlanPagado("BASICO"))
                .isEqualByComparingTo(new BigDecimal("49.99"));
    }

    @Test
    void obtenerPrecioPlan_pro_retorna9999() {
        assertThat(suscripcionCheckoutService.obtenerPrecioPlanPagado("PRO"))
                .isEqualByComparingTo(new BigDecimal("99.99"));
    }

    @Test
    void obtenerPrecioPlan_free_lanzaExcepcion() {
        assertThatThrownBy(() -> suscripcionCheckoutService.obtenerPrecioPlanPagado("FREE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("planes pagos");
    }

    // ── status mapping preapproval ───────────────────────────────────────────

    @Test
    void mapearEstadoPreapproval_authorized_retornaActiva() {
        assertThat(suscripcionCheckoutService.mapearEstadoPreapproval("authorized")).isEqualTo("ACTIVA");
    }

    @Test
    void mapearEstadoPreapproval_active_retornaActiva() {
        assertThat(suscripcionCheckoutService.mapearEstadoPreapproval("active")).isEqualTo("ACTIVA");
    }

    @Test
    void mapearEstadoPreapproval_paused_retornaSuspendida() {
        assertThat(suscripcionCheckoutService.mapearEstadoPreapproval("paused")).isEqualTo("SUSPENDIDA");
    }

    @Test
    void mapearEstadoPreapproval_cancelled_retornaCancelada() {
        assertThat(suscripcionCheckoutService.mapearEstadoPreapproval("cancelled")).isEqualTo("CANCELADA");
    }

    @Test
    void mapearEstadoPreapproval_desconocido_retornaPendiente() {
        assertThat(suscripcionCheckoutService.mapearEstadoPreapproval("pending")).isEqualTo("PENDIENTE");
    }

    @Test
    void mapearEstadoPreapproval_null_retornaPendiente() {
        assertThat(suscripcionCheckoutService.mapearEstadoPreapproval(null)).isEqualTo("PENDIENTE");
    }

    // ── iniciarCheckout ──────────────────────────────────────────────────────

    @Test
    void iniciarCheckout_basico_guardaPendienteConPreapprovalId() {
        Usuario usuario = Usuario.builder().id(10L).tenantId("tenant-a").build();

        when(usuarioService.obtenerUsuarioPorId(10L)).thenReturn(Optional.of(usuario));
        when(mercadoPagoService.crearPreapproval(any(), any(), any(), any(), any(), any())).thenReturn(
                MercadoPagoPreapprovalInfo.builder()
                        .preapprovalId("2c938084abc123")
                        .initPoint("https://mp.test/subscriptions/authorize")
                        .status("pending")
                        .build()
        );
        when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId("tenant-a", 10L)).thenReturn(Optional.empty());
        when(suscripcionRepository.save(any(Suscripcion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuscripcionCheckoutResponseDTO response = suscripcionCheckoutService.iniciarCheckout("BASICO", "tenant-a", 10L, null, null);

        assertThat(response.getPreapprovalId()).isEqualTo("2c938084abc123");
        assertThat(response.getInitPoint()).isEqualTo("https://mp.test/subscriptions/authorize");

        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepository).save(captor.capture());

        Suscripcion suscripcionGuardada = captor.getValue();
        assertThat(suscripcionGuardada.getPrecioMensual()).isEqualByComparingTo(new BigDecimal("49.99"));
        assertThat(suscripcionGuardada.getEstado()).isEqualTo("PENDIENTE");
        assertThat(suscripcionGuardada.getMetodoPago()).isEqualTo("MERCADOPAGO");
        assertThat(suscripcionGuardada.getPreapprovalId()).isEqualTo("2c938084abc123");
    }

    @Test
    void iniciarCheckout_planInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> suscripcionCheckoutService.iniciarCheckout("FREE", "tenant-a", 1L, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("planes pagos");
    }

    // ── procesarWebhook preapproval ──────────────────────────────────────────

    @Test
    void procesarWebhook_preapprovalAuthorized_activaSuscripcion() {
        Suscripcion suscripcion = Suscripcion.builder()
                .id(99L)
                .estado("PENDIENTE")
                .preapprovalId("2c938084abc123")
                .tenantId("tenant-a")
                .build();

        when(mercadoPagoService.obtenerPreapproval("2c938084abc123")).thenReturn(
                MercadoPagoPreapprovalInfo.builder()
                        .preapprovalId("2c938084abc123")
                        .status("authorized")
                        .externalReference("tenant-a:10")
                        .build()
        );
        when(suscripcionRepository.findByPreapprovalId("2c938084abc123")).thenReturn(Optional.of(suscripcion));
        when(suscripcionRepository.save(any(Suscripcion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MercadoPagoWebhookRequestDTO webhook = new MercadoPagoWebhookRequestDTO();
        webhook.setType("subscription_preapproval");
        webhook.setEntity("preapproval");
        webhook.setData(new MercadoPagoWebhookRequestDTO.DataPayload("2c938084abc123"));

        suscripcionCheckoutService.procesarWebhook(webhook);

        assertThat(suscripcion.getEstado()).isEqualTo("ACTIVA");
        assertThat(suscripcion.getCurrentPeriodStart()).isNotNull();
        assertThat(suscripcion.getCurrentPeriodEnd()).isNotNull();
    }

    @Test
    void procesarWebhook_preapprovalCancelled_cancelaSuscripcion() {
        Suscripcion suscripcion = Suscripcion.builder()
                .id(99L)
                .estado("ACTIVA")
                .preapprovalId("2c938084abc123")
                .tenantId("tenant-a")
                .build();

        when(mercadoPagoService.obtenerPreapproval("2c938084abc123")).thenReturn(
                MercadoPagoPreapprovalInfo.builder()
                        .preapprovalId("2c938084abc123")
                        .status("cancelled")
                        .externalReference("tenant-a:10")
                        .build()
        );
        when(suscripcionRepository.findByPreapprovalId("2c938084abc123")).thenReturn(Optional.of(suscripcion));
        when(suscripcionRepository.save(any(Suscripcion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MercadoPagoWebhookRequestDTO webhook = new MercadoPagoWebhookRequestDTO();
        webhook.setType("subscription_preapproval");
        webhook.setEntity("preapproval");
        webhook.setData(new MercadoPagoWebhookRequestDTO.DataPayload("2c938084abc123"));

        suscripcionCheckoutService.procesarWebhook(webhook);

        assertThat(suscripcion.getEstado()).isEqualTo("CANCELADA");
        assertThat(suscripcion.getFechaCancelacion()).isNotNull();
    }

    @Test
    void procesarWebhook_preapprovalPaused_suspendeSuscripcion() {
        Suscripcion suscripcion = Suscripcion.builder()
                .id(99L)
                .estado("ACTIVA")
                .preapprovalId("2c938084abc123")
                .tenantId("tenant-a")
                .build();

        when(mercadoPagoService.obtenerPreapproval("2c938084abc123")).thenReturn(
                MercadoPagoPreapprovalInfo.builder()
                        .preapprovalId("2c938084abc123")
                        .status("paused")
                        .externalReference("tenant-a:10")
                        .build()
        );
        when(suscripcionRepository.findByPreapprovalId("2c938084abc123")).thenReturn(Optional.of(suscripcion));
        when(suscripcionRepository.save(any(Suscripcion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MercadoPagoWebhookRequestDTO webhook = new MercadoPagoWebhookRequestDTO();
        webhook.setType("subscription_preapproval");
        webhook.setEntity("preapproval");
        webhook.setData(new MercadoPagoWebhookRequestDTO.DataPayload("2c938084abc123"));

        suscripcionCheckoutService.procesarWebhook(webhook);

        assertThat(suscripcion.getEstado()).isEqualTo("SUSPENDIDA");
    }

    // ── procesarWebhook pago (retrocompatibilidad) ───────────────────────────

    @Test
    void procesarWebhook_pagoAprobado_activaSuscripcion() {
        Suscripcion suscripcion = Suscripcion.builder()
                .id(99L)
                .estado("PENDIENTE")
                .tenantId("tenant-a")
                .build();

        when(mercadoPagoService.obtenerPago("777")).thenReturn(
                MercadoPagoPaymentInfo.builder()
                        .paymentId("777")
                        .status("approved")
                        .externalReference("tenant-a:10")
                        .lastFourDigits("1234")
                        .build()
        );
        when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId("tenant-a", 10L))
                .thenReturn(Optional.of(suscripcion));
        when(suscripcionRepository.save(any(Suscripcion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MercadoPagoWebhookRequestDTO webhook = new MercadoPagoWebhookRequestDTO();
        webhook.setType("payment");
        webhook.setData(new MercadoPagoWebhookRequestDTO.DataPayload("777"));

        suscripcionCheckoutService.procesarWebhook(webhook);

        assertThat(suscripcion.getEstado()).isEqualTo("ACTIVA");
        assertThat(suscripcion.getMpPaymentId()).isEqualTo("777");
        assertThat(suscripcion.getUltimos4Digitos()).isEqualTo("1234");
        assertThat(suscripcion.getCurrentPeriodStart()).isNotNull();
        assertThat(suscripcion.getCurrentPeriodEnd()).isNotNull();
    }
}

