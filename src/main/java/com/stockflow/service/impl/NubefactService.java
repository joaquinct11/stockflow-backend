package com.stockflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.NubefactComprobanteRequest;
import com.stockflow.dto.NubefactComprobanteRequest.ItemRequest;
import com.stockflow.dto.NubefactComprobanteResponse;
import com.stockflow.entity.Comprobante;
import com.stockflow.entity.DetalleVenta;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Envía comprobantes al OSE del tenant (Nubefact / Efact / etc.)
 * y devuelve la respuesta normalizada.
 *
 * El tenant debe tener configurados oseUrl y oseToken en su perfil.
 * La clase no persiste nada — solo hace la llamada HTTP; el caller
 * es quien actualiza el estado en BD.
 */
@Slf4j
@Service
public class NubefactService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // RestClient sin estado — seguro para inyección como singleton.
    private final RestClient http = RestClient.create();

    /**
     * Envía el comprobante al OSE configurado en el tenant.
     *
     * @param comprobante  comprobante con venta y detalles cargados
     * @param tenant       tenant con oseUrl y oseToken
     * @return respuesta de Nubefact
     * @throws BadRequestException si falta la configuración OSE
     * @throws RuntimeException    si la llamada HTTP falla
     */
    public NubefactComprobanteResponse enviar(Comprobante comprobante, Tenant tenant) {

        // Validar credenciales OSE
        if (tenant.getOseUrl() == null || tenant.getOseUrl().isBlank()) {
            throw new BadRequestException(
                    "El tenant no tiene URL del OSE configurada. " +
                    "Ve a Configuración → Facturación y agrega tu URL de Nubefact.");
        }
        if (tenant.getOseToken() == null || tenant.getOseToken().isBlank()) {
            throw new BadRequestException(
                    "El tenant no tiene token del OSE configurado. " +
                    "Ve a Configuración → Facturación y agrega tu token de Nubefact.");
        }

        String endpoint = buildEndpoint(tenant.getOseUrl());
        NubefactComprobanteRequest body = buildRequest(comprobante, tenant);

        log.info("🏛️ Enviando {} a OSE [{}]: {}",
                comprobante.getNumero(), endpoint, body.getOperacion());

        try {
            // Primero leemos como String para loguear el JSON crudo y luego deserializamos
            String rawJson = http.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Token " + tenant.getOseToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, resp) -> {
                                byte[] bytes = resp.getBody().readAllBytes();
                                String respBody = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                log.error("OSE error {}: {}", resp.getStatusCode(), respBody);
                                throw new RuntimeException(
                                        "OSE_ERROR:" + resp.getStatusCode() + ":" + respBody);
                            })
                    .body(String.class);

            log.debug("📨 Raw OSE response para {}: {}", comprobante.getNumero(), rawJson);

            NubefactComprobanteResponse response = new ObjectMapper().readValue(rawJson, NubefactComprobanteResponse.class);

            log.info("📨 Respuesta OSE para {}: aceptada={}, codigoSunat={}, registrado={}, mensaje={}",
                    comprobante.getNumero(),
                    response.getAceptadaPorSunat(),
                    response.getCodigoSunat(),
                    response.fueRegistrado(),
                    response.resumen());

            return response;

        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            // Código 23: el documento ya existe en Nubefact (envío previo exitoso)
            // No es un error fatal: el comprobante está registrado, SUNAT lo procesará
            if (msg != null && msg.contains("\"codigo\":23")) {
                log.warn("⚠️ OSE código 23 para {}: documento ya registrado en Nubefact. Marcando como PENDIENTE.",
                        comprobante.getNumero());
                NubefactComprobanteResponse yaExiste = new NubefactComprobanteResponse();
                yaExiste.setYaExistia(true);
                return yaExiste;
            }
            // Re-lanzar con mensaje limpio
            if (msg != null && msg.startsWith("OSE_ERROR:")) {
                String[] parts = msg.split(":", 3);
                throw new RuntimeException("El OSE devolvió error " + parts[1] + ": " + parts[2]);
            }
            throw e;
        } catch (Exception e) {
            log.error("Error llamando al OSE para {}: {}", comprobante.getNumero(), e.getMessage(), e);
            throw new RuntimeException("No se pudo conectar con el OSE: " + e.getMessage(), e);
        }
    }

    // ── Builder del request ───────────────────────────────────────────────────

    private NubefactComprobanteRequest buildRequest(Comprobante c, Tenant tenant) {

        boolean esBoleta  = "BOLETA".equals(c.getTipo());
        double  igvRate   = (tenant.getIgvPorcentaje() != null ? tenant.getIgvPorcentaje() : 18.0) / 100.0;

        return NubefactComprobanteRequest.builder()
                .operacion("generar_comprobante")
                .tipoDeComprobante(esBoleta ? 2 : 1)
                .serie(c.getSerie())
                .numero(c.getCorrelativo())
                .sunatTransaction(1)
                // Receptor
                .clienteTipoDeDocumento(resolveDocType(c.getReceptorDocTipo()))
                .clienteNumeroDeDocumento(
                        notBlank(c.getReceptorDocNumero()) ? c.getReceptorDocNumero() : "00000000")
                .clienteDenominacion(
                        notBlank(c.getReceptorNombre()) ? c.getReceptorNombre() : "CLIENTES VARIOS")
                .clienteDireccion(notBlank(c.getReceptorDireccion()) ? c.getReceptorDireccion() : "")
                .clienteEmail("")
                // Fecha y moneda
                .fechaDeEmision(c.getFechaEmision().format(DATE_FMT))
                .moneda(1)  // 1 = PEN (Soles)
                .tipoDeCambio("")
                // IGV
                .porcentajeDeIgv(tenant.getIgvPorcentaje() != null ? tenant.getIgvPorcentaje() : 18.0)
                // Totales
                .totalGravada(c.getSubtotal())
                .totalInafecta("")
                .totalExonerada("")
                .totalIgv(c.getIgv())
                .totalGratuita("")
                .total(c.getTotal())
                // Opciones
                .enviarAutomaticamenteALaSunat(true)
                .enviarAutomaticamenteAlCliente(false)
                .descuentoGlobal("")
                .observaciones("")
                // Ítems
                .items(buildItems(c, igvRate))
                .build();
    }

    private List<ItemRequest> buildItems(Comprobante c, double igvRate) {
        if (c.getVenta() == null || c.getVenta().getDetalles() == null) {
            return List.of();
        }
        return c.getVenta().getDetalles().stream()
                .map(d -> buildItem(d, igvRate))
                .collect(Collectors.toList());
    }

    private ItemRequest buildItem(DetalleVenta d, double igvRate) {
        // Nuestros precios YA incluyen IGV.
        // Nubefact necesita valor_unitario (sin IGV) y precio_unitario (con IGV).
        BigDecimal precioConIgv  = d.getPrecioUnitario();
        BigDecimal divisor       = BigDecimal.ONE.add(BigDecimal.valueOf(igvRate));
        BigDecimal valorSinIgv   = precioConIgv.divide(divisor, 10, RoundingMode.HALF_UP)
                                               .setScale(6, RoundingMode.HALF_UP);

        BigDecimal cant          = BigDecimal.valueOf(d.getCantidad());
        BigDecimal subtotalItem  = valorSinIgv.multiply(cant).setScale(2, RoundingMode.HALF_UP);
        BigDecimal igvItem       = subtotalItem.multiply(BigDecimal.valueOf(igvRate))
                                               .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalItem     = precioConIgv.multiply(cant).setScale(2, RoundingMode.HALF_UP);

        String codigo = d.getProducto() != null && notBlank(d.getProducto().getCodigoBarras())
                ? d.getProducto().getCodigoBarras()
                : "";
        String nombre = d.getProducto() != null && notBlank(d.getProducto().getNombre())
                ? d.getProducto().getNombre()
                : "Producto";

        return ItemRequest.builder()
                .unidadDeMedida("NIU")           // Unidad de ítem (SUNAT catálogo 3)
                .codigo(codigo)
                .descripcion(nombre)
                .cantidad(cant.setScale(2, RoundingMode.HALF_UP))
                .valorUnitario(valorSinIgv.setScale(6, RoundingMode.HALF_UP))
                .precioUnitario(precioConIgv.setScale(2, RoundingMode.HALF_UP))
                .descuento("")
                .subtotal(subtotalItem)
                .tipoDeIgv(1)                    // 1 = gravado
                .igv(igvItem)
                .total(totalItem)
                .anticipoRegularizacion(false)
                .anticipoDocumentoSerie("")
                .anticipoDocumentoNumero("")
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Normaliza la URL del OSE: Nubefact recibe el POST directamente en la URL
     * de empresa (sin sub-ruta adicional).
     * Ej: "https://api.nubefact.com/api/v1/slug" → "https://api.nubefact.com/api/v1/slug/"
     */
    private String buildEndpoint(String oseUrl) {
        String base = oseUrl.trim();
        return base.endsWith("/") ? base : base + "/";
    }

    /** Mapea tipo de documento al código SUNAT (catálogo 06). */
    private int resolveDocType(String docTipo) {
        if (docTipo == null) return 0;
        return switch (docTipo.toUpperCase()) {
            case "DNI" -> 1;
            case "RUC" -> 6;
            case "CE"  -> 4;  // Carné de extranjería
            case "PAS" -> 7;  // Pasaporte
            default    -> 0;  // Sin documento
        };
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
