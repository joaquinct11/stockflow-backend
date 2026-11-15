package com.stockflow.service;

import com.stockflow.entity.Tenant;
import com.stockflow.repository.OrdenCompraRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.service.impl.EmailServiceImpl;
import com.stockflow.util.OcPdfGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService.enviarConfirmacionSuscripcionCulqi()")
class EmailConfirmacionSuscripcionCulqiTest {

    private static final String EMAIL_REAL        = "joaquin@gmail.com";
    private static final String NOMBRE            = "Joaquín";
    private static final String TENANT_ID         = "vet-59ba";
    private static final String NEGOCIO_NOMBRE    = "Veterinaria San Marcos";
    private static final LocalDateTime ACTIVACION = LocalDateTime.of(2026, 10, 4, 10, 0);
    private static final LocalDateTime PROX_COBRO = LocalDateTime.of(2026, 11, 4, 10, 0);

    @Mock private TenantRepository       tenantRepository;
    @Mock private OrdenCompraRepository  ordenCompraRepository;
    @Mock private OcPdfGenerator         ocPdfGenerator;
    @Mock private RestTemplate           restTemplateMock;

    @InjectMocks private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        // Inyectar properties via ReflectionTestUtils (no hay contexto Spring)
        ReflectionTestUtils.setField(emailService, "apiKey",      "re_test_key");
        ReflectionTestUtils.setField(emailService, "fromEmail",   "noreply@fluxus.app");
        ReflectionTestUtils.setField(emailService, "fromName",    "Fluxus");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.fluxus.pe");

        // Reemplazar el RestTemplate interno con el mock
        ReflectionTestUtils.setField(emailService, "restTemplate", restTemplateMock);

        // Configurar tenant por defecto
        Tenant tenant = new Tenant();
        tenant.setNombre(NEGOCIO_NOMBRE);
        lenient().when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenant));
    }

    // ── Helper: captura el body enviado a Resend ──────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturarBodyResend() {
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplateMock).postForEntity(anyString(), captor.capture(), eq(String.class));
        return captor.getValue().getBody();
    }

    private void mockResendOk() {
        when(restTemplateMock.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"id\":\"abc123\"}"));
    }

    private void mockResendFalla() {
        when(restTemplateMock.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Resend timeout"));
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Destinatario es el email real")
    class DestinatarioRealEmail {

        @Test
        @DisplayName("envía al email real — NUNCA al tenant-scoped")
        void enviaPorEmailReal() {
            mockResendOk();

            emailService.enviarConfirmacionSuscripcionCulqi(
                    EMAIL_REAL, NOMBRE, TENANT_ID, "BASICO",
                    new BigDecimal("89.00"), ACTIVACION, PROX_COBRO,
                    "4242", "VISA", "sxn_test_abc");

            Map<String, Object> body = capturarBodyResend();
            @SuppressWarnings("unchecked")
            List<String> to = (List<String>) body.get("to");
            assertThat(to).containsExactly(EMAIL_REAL);
            // No debe contener el sufijo tenant-scoped
            assertThat(to.get(0)).doesNotContain("+");
        }

        @Test
        @DisplayName("no envía al email tenant-scoped joaquin+vet-59ba@gmail.com")
        void noEnviaAlEmailTenantScoped() {
            mockResendOk();
            String emailScoped = "joaquin+vet-59ba@gmail.com";

            emailService.enviarConfirmacionSuscripcionCulqi(
                    EMAIL_REAL, NOMBRE, TENANT_ID, "PRO",
                    new BigDecimal("129.00"), ACTIVACION, PROX_COBRO,
                    "1234", "MASTERCARD", "sxn_test_xyz");

            Map<String, Object> body = capturarBodyResend();
            @SuppressWarnings("unchecked")
            List<String> to = (List<String>) body.get("to");
            assertThat(to).doesNotContain(emailScoped);
        }
    }

    @Nested
    @DisplayName("Contenido del email")
    class ContenidoEmail {

        @Test
        @DisplayName("plan BASICO → asunto y HTML contienen 'Básico'")
        void planBasico_asuntoYHtmlCorrectos() {
            mockResendOk();

            emailService.enviarConfirmacionSuscripcionCulqi(
                    EMAIL_REAL, NOMBRE, TENANT_ID, "BASICO",
                    new BigDecimal("89.00"), ACTIVACION, PROX_COBRO,
                    "4242", "VISA", "sxn_test_abc");

            Map<String, Object> body = capturarBodyResend();
            String asunto = (String) body.get("subject");
            String html   = (String) body.get("html");

            assertThat(asunto).contains("Básico");
            assertThat(html).contains("Básico");
            assertThat(html).contains(NEGOCIO_NOMBRE);
        }

        @Test
        @DisplayName("plan PRO → asunto y HTML contienen 'Pro'")
        void planPro_asuntoYHtmlCorrectos() {
            mockResendOk();

            emailService.enviarConfirmacionSuscripcionCulqi(
                    EMAIL_REAL, NOMBRE, TENANT_ID, "PRO",
                    new BigDecimal("129.00"), ACTIVACION, PROX_COBRO,
                    "9999", "MASTERCARD", "sxn_test_pro");

            Map<String, Object> body = capturarBodyResend();
            String asunto = (String) body.get("subject");
            String html   = (String) body.get("html");

            assertThat(asunto).contains("Pro");
            assertThat(html).contains("Pro");
        }

        @Test
        @DisplayName("HTML incluye nombre del negocio, precio, tarjeta y próximo cobro")
        void htmlContieneDetallesCompletos() {
            mockResendOk();

            emailService.enviarConfirmacionSuscripcionCulqi(
                    EMAIL_REAL, NOMBRE, TENANT_ID, "BASICO",
                    new BigDecimal("89.00"), ACTIVACION, PROX_COBRO,
                    "4242", "VISA", "sxn_test_abc123");

            Map<String, Object> body = capturarBodyResend();
            String html = (String) body.get("html");

            assertThat(html).contains(NEGOCIO_NOMBRE);
            assertThat(html).contains("89.00");
            assertThat(html).contains("4242");          // últimos 4 dígitos
            assertThat(html).contains("VISA");
            assertThat(html).contains("04/11/2026");    // próximo cobro
            assertThat(html).contains("sxn_test_abc123"); // referencia
        }

        @Test
        @DisplayName("nombre del negocio proviene de TenantRepository, no del planId")
        void nombreNegocioDesdeRepo() {
            mockResendOk();

            emailService.enviarConfirmacionSuscripcionCulqi(
                    EMAIL_REAL, NOMBRE, TENANT_ID, "BASICO",
                    new BigDecimal("89.00"), ACTIVACION, PROX_COBRO,
                    null, null, null);

            Map<String, Object> body = capturarBodyResend();
            String html = (String) body.get("html");

            assertThat(html).contains(NEGOCIO_NOMBRE);
            verify(tenantRepository).findByTenantId(TENANT_ID);
        }
    }

    @Nested
    @DisplayName("Resiliencia — el fallo del email no bloquea la suscripción")
    class Resiliencia {

        @Test
        @DisplayName("si Resend lanza excepción, el método termina sin lanzar")
        void resendFalla_noLanzaExcepcion() {
            mockResendFalla();

            // No debe propagar la excepción
            emailService.enviarConfirmacionSuscripcionCulqi(
                    EMAIL_REAL, NOMBRE, TENANT_ID, "BASICO",
                    new BigDecimal("89.00"), ACTIVACION, PROX_COBRO,
                    "4242", "VISA", "sxn_test_abc");
            // Si llega aquí sin excepción → test pasa
        }

        @Test
        @DisplayName("Resend retorna 4xx → se loguea error pero no se lanza excepción")
        void resend4xx_noLanzaExcepcion() {
            when(restTemplateMock.postForEntity(anyString(), any(), eq(String.class)))
                    .thenReturn(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body("error"));

            emailService.enviarConfirmacionSuscripcionCulqi(
                    EMAIL_REAL, NOMBRE, TENANT_ID, "PRO",
                    new BigDecimal("129.00"), ACTIVACION, PROX_COBRO,
                    null, null, null);
        }
    }
}
