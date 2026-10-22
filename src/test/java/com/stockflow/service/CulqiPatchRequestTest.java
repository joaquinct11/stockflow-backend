package com.stockflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.exception.BadRequestException;
import com.stockflow.service.impl.CulqiHttpClient;
import com.stockflow.service.impl.CulqiServiceImpl;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifica que CulqiServiceImpl envía peticiones PATCH correctamente
 * y procesa respuestas exitosas y no exitosas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CulqiService — peticiones PATCH")
class CulqiPatchRequestTest {

    private static final String BASE_URL    = "https://api.culqi.com/v2";
    private static final String SECRET_KEY  = "sk_test_xxx";
    private static final String CUSTOMER_ID = "cus_test_abc123";
    private static final String SUB_ID      = "sub_test_xyz789";
    private static final String CARD_ID     = "crd_test_new456";

    @Mock private CulqiProperties culqiProperties;
    @Mock private ObjectMapper    objectMapper;
    @Mock private CulqiHttpClient httpClient;

    @InjectMocks private CulqiServiceImpl culqiService;

    @BeforeEach
    void setUp() throws Exception {
        when(culqiProperties.getBaseUrl()).thenReturn(BASE_URL);
        when(culqiProperties.getSecretKey()).thenReturn(SECRET_KEY);
        // Stub general para writeValueAsString — evita NPE en HttpRequest.BodyPublishers.ofString(null)
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"stub\":true}");
        ReflectionTestUtils.setField(culqiService, "httpClient", httpClient);
    }

    // ── actualizarTarjetaSuscripcion — PATCH /recurrent/subscriptions/{id} ──

    @Nested
    @DisplayName("actualizarTarjetaSuscripcion — PATCH /recurrent/subscriptions/{id}")
    class ActualizarTarjetaSuscripcion {

        @Test
        @DisplayName("envía PATCH con el método correcto al endpoint de suscripción")
        void enviaPatchAlEndpointDeSuscripcion() throws Exception {
            HttpResponse<String> patchResp = mockResponse(200, "{\"id\":\"" + SUB_ID + "\"}");
            when(objectMapper.readValue(anyString(),
                    any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(Map.of("id", SUB_ID));

            doReturn(patchResp).when(httpClient).send(any(HttpRequest.class));

            culqiService.actualizarTarjetaSuscripcion(SUB_ID, CARD_ID);

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient, times(1)).send(captor.capture());

            HttpRequest req = captor.getValue();
            assertThat(req.method()).isEqualTo("PATCH");
            assertThat(req.uri().toString())
                    .isEqualTo(BASE_URL + "/recurrent/subscriptions/" + SUB_ID);
            assertThat(req.headers().firstValue("Authorization"))
                    .hasValue("Bearer " + SECRET_KEY);
        }

        @Test
        @DisplayName("respuesta 4xx lanza BadRequestException con el mensaje de Culqi")
        void respuestaErrorLanzaBadRequestException() throws Exception {
            HttpResponse<String> errorResp = mockResponse(404,
                    "{\"merchant_message\":\"Suscripci\\u00f3n no encontrada\"}");
            when(objectMapper.readValue(anyString(),
                    any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(Map.of("merchant_message", "Suscripción no encontrada"));

            doReturn(errorResp).when(httpClient).send(any(HttpRequest.class));

            assertThatThrownBy(() -> culqiService.actualizarTarjetaSuscripcion(SUB_ID, CARD_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Suscripción no encontrada");
        }

        @Test
        @DisplayName("respuesta 422 lanza BadRequestException — cuerpo de error procesado")
        void respuesta422LanzaExcepcionConMensaje() throws Exception {
            HttpResponse<String> errorResp = mockResponse(422, "{\"user_message\":\"Tarjeta inv\\u00e1lida\"}");
            when(objectMapper.readValue(anyString(),
                    any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(Map.of("user_message", "Tarjeta inválida"));

            doReturn(errorResp).when(httpClient).send(any(HttpRequest.class));

            assertThatThrownBy(() -> culqiService.actualizarTarjetaSuscripcion(SUB_ID, CARD_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Tarjeta inválida");
        }
    }

    // ── actualizarCliente via crearCliente (find-or-create) ──────────────────

    @Nested
    @DisplayName("actualizarCliente — PATCH /customers/{id} cuando ya existe")
    class ActualizarCliente {

        @Test
        @DisplayName("envía PATCH al endpoint /customers/{id} cuando existe cliente")
        void enviaPatchCuandoClienteExiste() throws Exception {
            HttpResponse<String> searchResp = mockResponse(200,
                    "{\"data\":[{\"id\":\"" + CUSTOMER_ID + "\",\"email\":\"test@test.com\"}]}");
            HttpResponse<String> patchResp = mockResponse(200, "{}");

            // GET /customers devuelve cliente existente
            when(objectMapper.readValue(
                    eq("{\"data\":[{\"id\":\"" + CUSTOMER_ID + "\",\"email\":\"test@test.com\"}]}"),
                    any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(Map.of("data", List.of(Map.of("id", CUSTOMER_ID, "email", "test@test.com"))));
            // PATCH /customers/{id} devuelve vacío
            when(objectMapper.readValue(eq("{}"),
                    any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(Map.of());

            doReturn(searchResp).doReturn(patchResp)
                    .when(httpClient).send(any(HttpRequest.class));

            String result = culqiService.crearCliente("test@test.com", "Juan", "Pérez", "51987654321");
            assertThat(result).isEqualTo(CUSTOMER_ID);

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient, times(2)).send(captor.capture());

            HttpRequest patchReq = captor.getAllValues().stream()
                    .filter(r -> "PATCH".equals(r.method()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No se envió ninguna petición PATCH"));

            assertThat(patchReq.uri().toString())
                    .isEqualTo(BASE_URL + "/customers/" + CUSTOMER_ID);
        }

        @Test
        @DisplayName("respuesta 4xx del PATCH no bloquea — devuelve ID del cliente existente")
        void patchErrorNoBloquea() throws Exception {
            HttpResponse<String> searchResp = mockResponse(200,
                    "{\"data\":[{\"id\":\"" + CUSTOMER_ID + "\",\"email\":\"test@test.com\"}]}");
            HttpResponse<String> patchError = mockResponse(422, "{\"merchant_message\":\"Error\"}");

            when(objectMapper.readValue(
                    eq("{\"data\":[{\"id\":\"" + CUSTOMER_ID + "\",\"email\":\"test@test.com\"}]}"),
                    any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(Map.of("data", List.of(Map.of("id", CUSTOMER_ID, "email", "test@test.com"))));

            doReturn(searchResp).doReturn(patchError)
                    .when(httpClient).send(any(HttpRequest.class));

            // actualizarCliente captura la respuesta 4xx con warn; crearCliente debe retornar el ID
            String result = culqiService.crearCliente("test@test.com", "Juan", "Pérez", null);
            assertThat(result).isEqualTo(CUSTOMER_ID);
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int status, String body) throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        return resp;
    }
}
