package com.stockflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.config.properties.CulqiProperties;
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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifica que buscarClientePorEmail() URL-encode correctamente el email
 * antes de construir la URL de la petición GET a Culqi.
 *
 * El bug original: el '+' del email tenant-scoped se concatenaba sin codificar,
 * viajando como espacio → Culqi devolvía 400 "email inválido o vacío".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CulqiService — URL-encoding del email en búsqueda de customer")
class CulqiClienteEmailEncodingTest {

    private static final String BASE_URL     = "https://api.culqi.com/v2";
    private static final String SECRET_KEY   = "sk_test_xxx";
    private static final String EMAIL_SCOPED = "joaquincastillotello2001+vet-59ba@gmail.com";
    private static final String EMAIL_SIMPLE = "usuario@gmail.com";
    private static final String CUSTOMER_ID  = "cus_test_HczFohTtZcIM75HT";

    @Mock private CulqiProperties culqiProperties;
    @Mock private ObjectMapper    objectMapper;
    @Mock private HttpClient      httpClient;

    @InjectMocks private CulqiServiceImpl culqiService;

    @BeforeEach
    void setUp() throws Exception {
        when(culqiProperties.getBaseUrl()).thenReturn(BASE_URL);
        when(culqiProperties.getSecretKey()).thenReturn(SECRET_KEY);
        ReflectionTestUtils.setField(culqiService, "httpClient", httpClient);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponseWithCustomer(String customerId) throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        String body = "{\"data\":[{\"id\":\"" + customerId + "\",\"email\":\"" + EMAIL_SCOPED + "\"}]}";
        when(resp.body()).thenReturn(body);
        when(objectMapper.readValue(eq(body), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Map.of("data", List.of(Map.of("id", customerId, "email", EMAIL_SCOPED))));
        return resp;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponseEmpty() throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        String body = "{\"data\":[]}";
        when(resp.body()).thenReturn(body);
        when(objectMapper.readValue(eq(body), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Map.of("data", List.of()));
        return resp;
    }

    // ── Tests de URL encoding ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Email con '+' → URL debe contener '%2B'")
    class EmailConMas {

        @Test
        @DisplayName("el '+' viaja como '%2B' y NO como espacio")
        void plusEsEncodedComoPorcentaje2B() throws Exception {
            HttpResponse<String> searchResp = mockResponseWithCustomer(CUSTOMER_ID);
            doReturn(searchResp)
                    .when(httpClient).send(any(HttpRequest.class), any());

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

            culqiService.crearCliente(EMAIL_SCOPED, "Joaquín", "Castillo", null);

            verify(httpClient, atLeastOnce()).send(captor.capture(), any());

            // La primera llamada debe ser el GET /customers?email=...
            HttpRequest getRequest = captor.getAllValues().stream()
                    .filter(r -> r.method().equals("GET"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No se encontró la llamada GET a /customers"));

            String url = getRequest.uri().toString();
            assertThat(url).contains("%2B");
            assertThat(url).doesNotContain("vet-59ba@gmail.com"); // '@' también debe estar encoded
            assertThat(url).contains("%40gmail.com");
        }

        @Test
        @DisplayName("URL NO contiene el '+' sin codificar")
        void urlNoContieneSignoMasLiteral() throws Exception {
            HttpResponse<String> searchResp = mockResponseWithCustomer(CUSTOMER_ID);
            doReturn(searchResp)
                    .when(httpClient).send(any(HttpRequest.class), any());

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

            culqiService.crearCliente(EMAIL_SCOPED, "Joaquín", "Castillo", null);

            verify(httpClient, atLeastOnce()).send(captor.capture(), any());

            HttpRequest getRequest = captor.getAllValues().stream()
                    .filter(r -> r.method().equals("GET"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No se encontró la llamada GET"));

            String queryString = getRequest.uri().getQuery();
            // La query String decodificada por la JVM (URI.getQuery decodes %XX)
            // — la URL raw (getRawQuery) NO debe tener '+' literal
            String rawQuery = getRequest.uri().getRawQuery();
            assertThat(rawQuery).doesNotContain("+");
        }
    }

    @Nested
    @DisplayName("Reutilización de customer existente — sin duplicados")
    class ReutilizacionCustomer {

        @Test
        @DisplayName("si Culqi devuelve customer existente, no llama POST /customers")
        void customerExistente_noCreaNuevo() throws Exception {
            HttpResponse<String> searchResp = mockResponseWithCustomer(CUSTOMER_ID);

            // GET /customers → found
            // PATCH /customers/{id} → update (puede no llamarse si update falla silenciosamente)
            HttpResponse<String> patchResp = mock(HttpResponse.class);
            when(patchResp.statusCode()).thenReturn(200);
            when(patchResp.body()).thenReturn("{}");
            when(objectMapper.readValue(eq("{}"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(Map.of());

            doReturn(searchResp).doReturn(patchResp)
                    .when(httpClient).send(any(HttpRequest.class), any());

            String result = culqiService.crearCliente(EMAIL_SCOPED, "Joaquín", "Castillo", null);

            assertThat(result).isEqualTo(CUSTOMER_ID);

            // Verificar que NO se llamó POST /customers
            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient, atLeastOnce()).send(captor.capture(), any());

            long postCustomersCount = captor.getAllValues().stream()
                    .filter(r -> r.method().equals("POST") && r.uri().getPath().endsWith("/customers"))
                    .count();
            assertThat(postCustomersCount).isZero();
        }

        @Test
        @DisplayName("retorna el ID del customer existente cus_test_HczFohTtZcIM75HT")
        void retornaIdCustomerExistente() throws Exception {
            HttpResponse<String> searchResp = mockResponseWithCustomer(CUSTOMER_ID);
            HttpResponse<String> patchResp  = mock(HttpResponse.class);
            when(patchResp.statusCode()).thenReturn(200);
            when(patchResp.body()).thenReturn("{}");
            when(objectMapper.readValue(eq("{}"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(Map.of());

            doReturn(searchResp).doReturn(patchResp)
                    .when(httpClient).send(any(HttpRequest.class), any());

            String customerId = culqiService.crearCliente(EMAIL_SCOPED, "Joaquín", "Castillo", null);
            assertThat(customerId).isEqualTo("cus_test_HczFohTtZcIM75HT");
        }

        @Test
        @DisplayName("email sin '+' también se codifica correctamente (@)")
        void emailSimple_arrobaEsCodificado() throws Exception {
            HttpResponse<String> searchResp = mock(HttpResponse.class);
            when(searchResp.statusCode()).thenReturn(200);
            String body = "{\"data\":[]}";
            when(searchResp.body()).thenReturn(body);
            when(objectMapper.readValue(eq(body), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(Map.of("data", List.of()));

            // POST /customers response
            HttpResponse<String> postResp = mock(HttpResponse.class);
            when(postResp.statusCode()).thenReturn(200);
            String postBody = "{\"id\":\"cus_nuevo\"}";
            when(postResp.body()).thenReturn(postBody);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(objectMapper.readValue(eq(postBody), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(Map.of("id", "cus_nuevo"));

            doReturn(searchResp).doReturn(postResp)
                    .when(httpClient).send(any(HttpRequest.class), any());

            culqiService.crearCliente(EMAIL_SIMPLE, "User", "Test", null);

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient, atLeastOnce()).send(captor.capture(), any());

            HttpRequest getRequest = captor.getAllValues().stream()
                    .filter(r -> r.method().equals("GET"))
                    .findFirst()
                    .orElseThrow();

            // '@' debe estar encoded como %40
            assertThat(getRequest.uri().getRawQuery()).contains("%40gmail.com");
        }
    }
}
