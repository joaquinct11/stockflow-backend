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
import com.stockflow.service.model.MercadoPagoPreferenceResponse;
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

    @Test
    void iniciarCheckout_basico_guardaPendienteConPreferenceId() {
        Usuario usuario = Usuario.builder().id(10L).tenantId("tenant-a").build();

        when(usuarioService.obtenerUsuarioPorId(10L)).thenReturn(Optional.of(usuario));
        when(mercadoPagoService.crearPreferencia(any(), any(), any())).thenReturn(
                MercadoPagoPreferenceResponse.builder()
                        .preferenceId("pref_123")
                        .initPoint("https://mp.test/checkout")
                        .build()
        );
        when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId("tenant-a", 10L)).thenReturn(Optional.empty());
        when(suscripcionRepository.save(any(Suscripcion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuscripcionCheckoutResponseDTO response = suscripcionCheckoutService.iniciarCheckout("BASICO", "tenant-a", 10L);

        assertThat(response.getPreferenceId()).isEqualTo("pref_123");
        assertThat(response.getInitPoint()).isEqualTo("https://mp.test/checkout");

        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepository).save(captor.capture());

        Suscripcion suscripcionGuardada = captor.getValue();
        assertThat(suscripcionGuardada.getPrecioMensual()).isEqualByComparingTo(new BigDecimal("49.99"));
        assertThat(suscripcionGuardada.getEstado()).isEqualTo("PENDIENTE");
        assertThat(suscripcionGuardada.getMetodoPago()).isEqualTo("MERCADOPAGO");
        assertThat(suscripcionGuardada.getMpPreferenceId()).isEqualTo("pref_123");
    }

    @Test
    void iniciarCheckout_planInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> suscripcionCheckoutService.iniciarCheckout("FREE", "tenant-a", 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("planes pagos");
    }

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
