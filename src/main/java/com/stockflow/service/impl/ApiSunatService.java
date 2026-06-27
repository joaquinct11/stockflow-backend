package com.stockflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.ApiSunatComprobanteRequest;
import com.stockflow.dto.ApiSunatComprobanteRequest.ItemRequest;
import com.stockflow.dto.ApiSunatComprobanteResponse;
import com.stockflow.entity.Comprobante;
import com.stockflow.entity.DetalleVenta;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Envía comprobantes a ApiSunat (lucode.pe) usando el token de organización
 * que Fluxus asigna a cada tenant tras el onboarding.
 *
 * El tenant NO configura URL — se controla por entorno:
 *   dev/uat → https://sandbox.apisunat.pe  (sin impacto en SUNAT real)
 *   prod    → https://app.apisunat.pe
 */
@Slf4j
@Service
public class ApiSunatService {

    private static final String DOCUMENTS_PATH = "/api/v3/documents";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Value("${apisunat.base-url:https://app.apisunat.pe}")
    private String apiSunatBaseUrl;

    private static final String DAILY_SUMMARY_PATH = "/api/v3/daily-summary";
    private static final String VOIDED_PATH         = "/api/v3/voided";

    private final RestClient http = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Anula un comprobante en SUNAT vía ApiSunat.
     * - BOLETA  → POST /api/v3/daily-summary  (resumen diario con acción "anular")
     * - FACTURA → POST /api/v3/voided         (comunicación de baja)
     */
    public ApiSunatComprobanteResponse anular(Comprobante comprobante, Tenant tenant, String motivo) {
        if (tenant.getOseToken() == null || tenant.getOseToken().isBlank()) {
            throw new BadRequestException(
                    "La facturación electrónica aún no está activada para tu cuenta. " +
                    "Contacta a soporte de Fluxus para habilitarla.");
        }

        boolean esBoleta = "BOLETA".equals(comprobante.getTipo());
        String path = esBoleta ? DAILY_SUMMARY_PATH : VOIDED_PATH;
        String url  = apiSunatBaseUrl + path;

        Object body = esBoleta ? buildAnularBoleta(comprobante) : buildAnularFactura(comprobante, motivo);

        log.info("🗑️ Anulando {} en ApiSunat [{}]", comprobante.getNumero(), url);

        try {
            String rawJson = http.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.getOseToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, resp) -> {
                                byte[] bytes = resp.getBody().readAllBytes();
                                String respBody = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                log.error("ApiSunat anulación error {}: {}", resp.getStatusCode(), respBody);
                                throw new RuntimeException("APISUNAT_ERROR:" + resp.getStatusCode() + ":" + respBody);
                            })
                    .body(String.class);

            log.debug("📨 ApiSunat anulación raw response para {}: {}", comprobante.getNumero(), rawJson);
            ApiSunatComprobanteResponse response = mapper.readValue(rawJson, ApiSunatComprobanteResponse.class);
            log.info("📨 ApiSunat anulación para {}: success={}, estado={}",
                    comprobante.getNumero(), response.getSuccess(),
                    response.getPayload() != null ? response.getPayload().getEstado() : "null");
            return response;

        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("APISUNAT_ERROR:")) {
                String[] parts = msg.split(":", 3);
                throw new RuntimeException("ApiSunat devolvió error " + parts[1] + ": " + parts[2]);
            }
            throw e;
        } catch (Exception e) {
            log.error("Error anulando en ApiSunat {}: {}", comprobante.getNumero(), e.getMessage(), e);
            throw new RuntimeException("No se pudo conectar con ApiSunat: " + e.getMessage(), e);
        }
    }

    private java.util.Map<String, Object> buildAnularBoleta(Comprobante c) {
        return java.util.Map.of(
            "documento", "resumen_diario",
            "documentos_afectados", java.util.List.of(java.util.Map.of(
                "accion_resumen", "anular",
                "documento",      "boleta",
                "serie",          c.getSerie(),
                "numero",         String.valueOf(c.getCorrelativo())
            ))
        );
    }

    private java.util.Map<String, Object> buildAnularFactura(Comprobante c, String motivo) {
        return java.util.Map.of(
            "documento", "comunicacion_baja",
            "motivo",    motivo != null && !motivo.isBlank() ? motivo : "ANULACIÓN DE OPERACIÓN",
            "documento_afectado", java.util.Map.of(
                "documento", "factura",
                "serie",     c.getSerie(),
                "numero",    String.valueOf(c.getCorrelativo())
            )
        );
    }

    /**
     * Envía el comprobante a ApiSunat usando el token del tenant.
     *
     * @param comprobante comprobante con venta y detalles cargados
     * @param tenant      tenant con oseToken configurado por Fluxus
     * @return respuesta de ApiSunat
     * @throws BadRequestException si el tenant no tiene token configurado
     */
    public ApiSunatComprobanteResponse enviar(Comprobante comprobante, Tenant tenant) {

        if (tenant.getOseToken() == null || tenant.getOseToken().isBlank()) {
            throw new BadRequestException(
                    "La facturación electrónica aún no está activada para tu cuenta. " +
                    "Contacta a soporte de Fluxus para habilitarla.");
        }

        ApiSunatComprobanteRequest body = buildRequest(comprobante, tenant);

        String url = apiSunatBaseUrl + DOCUMENTS_PATH;
        log.info("🏛️ Enviando {} a ApiSunat [{}]: {}-{}",
                comprobante.getNumero(), url, body.getDocumento(), body.getSerie());

        try {
            String rawJson = http.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.getOseToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, resp) -> {
                                byte[] bytes = resp.getBody().readAllBytes();
                                String respBody = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                log.error("ApiSunat error {}: {}", resp.getStatusCode(), respBody);
                                throw new RuntimeException("APISUNAT_ERROR:" + resp.getStatusCode() + ":" + respBody);
                            })
                    .body(String.class);

            log.debug("📨 ApiSunat raw response para {}: {}", comprobante.getNumero(), rawJson);

            ApiSunatComprobanteResponse response = mapper.readValue(rawJson, ApiSunatComprobanteResponse.class);

            log.info("📨 ApiSunat para {}: success={}, estado={}, mensaje={}",
                    comprobante.getNumero(),
                    response.getSuccess(),
                    response.getPayload() != null ? response.getPayload().getEstado() : "null",
                    response.resumen());

            return response;

        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("APISUNAT_ERROR:")) {
                String[] parts = msg.split(":", 3);
                String responseBody = parts.length > 2 ? parts[2] : "";
                try {
                    com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(responseBody);
                    if (node.has("message")) {
                        throw new BadRequestException("ApiSunat: " + node.get("message").asText());
                    }
                } catch (BadRequestException be) {
                    throw be;
                } catch (Exception ignored) {}
                throw new BadRequestException("ApiSunat devolvió error " + parts[1] + ": " + responseBody);
            }
            throw e;
        } catch (Exception e) {
            log.error("Error llamando a ApiSunat para {}: {}", comprobante.getNumero(), e.getMessage(), e);
            throw new RuntimeException("No se pudo conectar con ApiSunat: " + e.getMessage(), e);
        }
    }

    /**
     * Consulta el estado actual de un comprobante ya enviado a SUNAT.
     * Usa POST /api/v3/status con { documento, serie, numero }.
     */
    public ApiSunatComprobanteResponse consultarEstado(String tipo, String serie, Integer numero, String oseToken) {
        String url = apiSunatBaseUrl + "/api/v3/status";
        String documento = "BOLETA".equals(tipo) ? "boleta" : "factura";

        java.util.Map<String, Object> body = java.util.Map.of(
                "documento", documento,
                "serie",     serie,
                "numero",    numero
        );

        log.info("🔍 Consultando estado SUNAT [{}-{}] en {}", serie, numero, url);

        try {
            String rawJson = http.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + oseToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, resp) -> {
                                byte[] bytes = resp.getBody().readAllBytes();
                                log.warn("ApiSunat /status error {}: {}", resp.getStatusCode(), new String(bytes));
                                throw new RuntimeException("APISUNAT_STATUS_ERROR:" + resp.getStatusCode());
                            })
                    .body(String.class);

            return mapper.readValue(rawJson, ApiSunatComprobanteResponse.class);

        } catch (Exception e) {
            log.warn("No se pudo consultar estado SUNAT para {}-{}: {}", serie, numero, e.getMessage());
            throw new RuntimeException("Error consultando estado: " + e.getMessage(), e);
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private ApiSunatComprobanteRequest buildRequest(Comprobante c, Tenant tenant) {
        boolean esBoleta = "BOLETA".equals(c.getTipo());
        double igvRate = (tenant.getIgvPorcentaje() != null ? tenant.getIgvPorcentaje() : 18.0) / 100.0;

        String docTipo   = resolveDocType(c.getReceptorDocTipo());
        String docNumero = resolveDocNumero(docTipo, c.getReceptorDocNumero());
        String nombre    = notBlank(c.getReceptorNombre()) ? c.getReceptorNombre().trim() : "CONSUMIDOR FINAL";
        if (nombre.length() < 3) nombre = nombre + "-".repeat(3 - nombre.length());

        // FACTURA requiere RUC válido (11 dígitos)
        if (!esBoleta && !"6".equals(docTipo)) {
            throw new BadRequestException(
                "Para emitir una FACTURA el receptor debe tener RUC. " +
                "Verifica el tipo de documento del cliente.");
        }

        return ApiSunatComprobanteRequest.builder()
                .documento(esBoleta ? "boleta" : "factura")
                .serie(c.getSerie())
                .numero(c.getCorrelativo())
                .fechaDeEmision(c.getFechaEmision().format(DATE_FMT))
                .horaDeEmision(c.getFechaEmision().format(TIME_FMT))
                .moneda("PEN")
                .tipoOperacion("0101")
                .clienteTipoDeDocumento(docTipo)
                .clienteNumeroDeDocumento(docNumero)
                .clienteDenominacion(nombre)
                .clienteDireccion(notBlank(c.getReceptorDireccion()) ? c.getReceptorDireccion() : "-")
                .items(buildItems(c, igvRate))
                .total(c.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .build();
    }

    private List<ItemRequest> buildItems(Comprobante c, double igvRate) {
        if (c.getVenta() == null || c.getVenta().getDetalles() == null) return List.of();
        return c.getVenta().getDetalles().stream()
                .map(d -> buildItem(d, igvRate))
                .collect(Collectors.toList());
    }

    private ItemRequest buildItem(DetalleVenta d, double igvRate) {
        BigDecimal precioConIgv = d.getPrecioUnitario();
        BigDecimal divisor      = BigDecimal.ONE.add(BigDecimal.valueOf(igvRate));
        BigDecimal valorSinIgv  = precioConIgv.divide(divisor, 6, RoundingMode.HALF_UP);

        String codigo = d.getProducto() != null && notBlank(d.getProducto().getCodigoBarras())
                ? d.getProducto().getCodigoBarras() : "";
        String nombre = d.getProducto() != null && notBlank(d.getProducto().getNombre())
                ? d.getProducto().getNombre() : "Producto";

        double igvPct = igvRate * 100;

        return ItemRequest.builder()
                .unidadDeMedida("NIU")
                .codigoInterno(notBlank(codigo) ? codigo : null)
                .descripcion(nombre)
                .cantidad(String.valueOf(d.getCantidad()))
                .valorUnitario(valorSinIgv.toPlainString())
                .porcentajeIgv(String.valueOf((int) igvPct))
                .codigoTipoAfectacionIgv("10")
                .nombreTributo("IGV")
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Garantiza que el número de documento cumpla los mínimos de ApiSunat.
     * DNI (1): 8 dígitos exactos → rellena con ceros a la izquierda o usa "00000000".
     * RUC (6): 11 dígitos.
     * Otros / sin documento: "00000000".
     */
    private String resolveDocNumero(String docTipo, String docNumero) {
        if (!notBlank(docNumero)) {
            return "00000000";
        }
        String num = docNumero.trim();
        return switch (docTipo) {
            case "1" -> num.length() >= 8 ? num : String.format("%08d", Long.parseLong(num.replaceAll("\\D", "0")));
            case "6" -> num.length() >= 11 ? num : "00000000000";
            default  -> num.length() >= 8 ? num : "00000000";
        };
    }

    /** Mapea tipo de documento al código SUNAT catálogo 06. */
    private String resolveDocType(String docTipo) {
        if (docTipo == null) return "-";
        return switch (docTipo.toUpperCase()) {
            case "DNI" -> "1";
            case "RUC" -> "6";
            case "CE"  -> "4";
            case "PAS" -> "7";
            default    -> "-";
        };
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
