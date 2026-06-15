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
 * El tenant NO configura URL — está hardcodeada. Solo necesita oseToken.
 */
@Slf4j
@Service
public class ApiSunatService {

    private static final String APISUNAT_URL  = "https://app.apisunat.pe/api/v3/documents";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final RestClient http = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

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

        log.info("🏛️ Enviando {} a ApiSunat: {}-{}",
                comprobante.getNumero(), body.getDocumento(), body.getSerie());

        try {
            String rawJson = http.post()
                    .uri(APISUNAT_URL)
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

            log.info("📨 ApiSunat para {}: aceptada={}, codigo={}, mensaje={}",
                    comprobante.getNumero(),
                    response.getAceptadaPorSunat(),
                    response.getSunatCodigoRespuesta(),
                    response.resumen());

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
            log.error("Error llamando a ApiSunat para {}: {}", comprobante.getNumero(), e.getMessage(), e);
            throw new RuntimeException("No se pudo conectar con ApiSunat: " + e.getMessage(), e);
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private ApiSunatComprobanteRequest buildRequest(Comprobante c, Tenant tenant) {
        boolean esBoleta = "BOLETA".equals(c.getTipo());
        double igvRate = (tenant.getIgvPorcentaje() != null ? tenant.getIgvPorcentaje() : 18.0) / 100.0;

        return ApiSunatComprobanteRequest.builder()
                .documento(esBoleta ? "boleta" : "factura")
                .serie(c.getSerie())
                .numero(c.getCorrelativo())
                .fechaDeEmision(c.getFechaEmision().format(DATE_FMT))
                .horaDeEmision(c.getFechaEmision().format(TIME_FMT))
                .moneda("PEN")
                .tipoOperacion("0101")
                .clienteTipoDeDocumento(resolveDocType(c.getReceptorDocTipo()))
                .clienteNumeroDeDocumento(
                        notBlank(c.getReceptorDocNumero()) ? c.getReceptorDocNumero() : "00000000")
                .clienteDenominacion(
                        notBlank(c.getReceptorNombre()) ? c.getReceptorNombre() : "CLIENTES VARIOS")
                .clienteDireccion(notBlank(c.getReceptorDireccion()) ? c.getReceptorDireccion() : "")
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
