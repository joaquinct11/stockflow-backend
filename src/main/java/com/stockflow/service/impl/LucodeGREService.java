package com.stockflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.gre.lucode.LucodeGRERequest;
import com.stockflow.dto.gre.lucode.LucodeGRERequest.*;
import com.stockflow.dto.gre.lucode.LucodeGREResponse;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Tenant;
import com.stockflow.entity.gre.GuiaRemision;
import com.stockflow.repository.TenantRepository;
import com.stockflow.entity.gre.GuiaRemisionItem;
import com.stockflow.entity.gre.GuiaRemisionTransporte;
import com.stockflow.entity.gre.ModalidadTransporteGRE;
import com.stockflow.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Encapsula la llamada a POST /api/v3/dispatches de ApiSunat (Lucode) para GRE.
 *
 * Reutiliza la misma base URL y mecanismo Bearer token que ApiSunatService.
 * El tenant configura su oseToken en el onboarding; Fluxus controla la URL por entorno.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LucodeGREService {

    private static final String DISPATCHES_PATH = "/api/v3/dispatches";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    /** Traduce mensajes técnicos del servicio de emisión a mensajes comprensibles para el usuario. */
    static String translateEmissionError(String rawMessage) {
        if (rawMessage == null) return "No se pudo emitir la guía. Revisa los datos ingresados e inténtalo nuevamente.";
        String lower = rawMessage.toLowerCase();
        if (lower.contains("fecha inicio") || lower.contains("fecha de inicio") || lower.contains("fechainiciodetraslado"))
            return "Completa la fecha de inicio del traslado en los datos de transporte e inténtalo nuevamente.";
        if (lower.contains("ubigeo") && lower.contains("partida"))
            return "El ubigeo de partida debe tener exactamente 6 dígitos.";
        if (lower.contains("ubigeo") && lower.contains("llegada"))
            return "El ubigeo de llegada debe tener exactamente 6 dígitos.";
        if (lower.contains("ubigeo"))
            return "Revisa los ubigeos de partida y llegada. Deben tener exactamente 6 dígitos.";
        if (lower.contains("dirección") || lower.contains("direccion"))
            return "Completa la dirección de origen o destino.";
        if (lower.contains("destinatario"))
            return "Revisa los datos del destinatario de la guía.";
        if (lower.contains("placa") || lower.contains("vehículo") || lower.contains("vehiculo"))
            return "Revisa la placa y los datos del vehículo.";
        if (lower.contains("conductor") || lower.contains("licencia"))
            return "Revisa los datos del conductor.";
        if (lower.contains("peso") || lower.contains("bulto"))
            return "Revisa el peso bruto y el número de bultos.";
        if (lower.contains("item") || lower.contains("producto") || lower.contains("cantidad"))
            return "Revisa los productos y cantidades de la guía.";
        return "No se pudo emitir la guía. Revisa los datos ingresados e inténtalo nuevamente.";
    }

    @Value("${apisunat.base-url:https://app.apisunat.pe}")
    private String apiSunatBaseUrl;

    private final TenantRepository tenantRepository;

    private final RestClient http = RestClient.builder()
            .requestFactory(lucodeRequestFactory())
            .build();

    private static org.springframework.http.client.ClientHttpRequestFactory lucodeRequestFactory() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(25_000);
        return f;
    }
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Envía la guía de remisión a Lucode vía POST /api/v3/dispatches.
     *
     * @param guia     entidad GuiaRemision con items y transporte cargados
     * @param oseToken token OSE del tenant
     * @return respuesta de Lucode (success + payload con hash/xml/cdr)
     */
    public LucodeGREResponse emitirGRE(GuiaRemision guia, String oseToken) {
        if (oseToken == null || oseToken.isBlank()) {
            throw new BadRequestException(
                    "La facturación electrónica aún no está activada para este negocio. " +
                    "Contacta a soporte de Fluxus para habilitarla.");
        }

        LucodeGRERequest body = buildRequest(guia);
        String url = apiSunatBaseUrl + DISPATCHES_PATH;

        log.info("📦 Emitiendo GRE {} a Lucode [{}]", guia.getNumeroCompleto(), url);

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
                                String respBody = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                log.error("Lucode GRE error {}: {}", resp.getStatusCode(), respBody);
                                throw new RuntimeException("LUCODE_GRE_ERROR:" + resp.getStatusCode() + ":" + respBody);
                            })
                    .body(String.class);

            log.debug("📨 Lucode GRE raw response para {}: {}", guia.getNumeroCompleto(), rawJson);
            LucodeGREResponse response = mapper.readValue(rawJson, LucodeGREResponse.class);
            log.info("📨 Lucode GRE para {}: success={}, mensaje={}",
                    guia.getNumeroCompleto(), response.isSuccess(), response.getMessage());
            return response;

        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("LUCODE_GRE_ERROR:")) {
                String[] parts = msg.split(":", 3);
                String responseBody = parts.length > 2 ? parts[2] : "";
                try {
                    com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(responseBody);
                    String allErrors = extractLucodeErrors(node);
                    log.error("Emisión GRE {} — error del servicio tributario [{}]: {}", guia.getNumeroCompleto(), parts[1], allErrors);
                    throw new BadRequestException(translateEmissionError(allErrors));
                } catch (BadRequestException be) {
                    throw be;
                } catch (Exception ignored) {}
                log.error("Emisión GRE {} — respuesta inesperada [{}]: {}", guia.getNumeroCompleto(), parts[1], responseBody);
                throw new BadRequestException("No se pudo emitir la guía. Revisa los datos ingresados e inténtalo nuevamente.");
            }
            throw e;
        } catch (Exception e) {
            log.error("Error de conexión al emitir GRE {}: {}", guia.getNumeroCompleto(), e.getMessage(), e);
            throw new BadRequestException("No fue posible conectar con el servicio de emisión. Inténtalo más tarde.");
        }
    }

    /**
     * Consulta el estado de una GRE ya emitida en el OSE (Lucode) usando POST /api/v3/status.
     * Retorna la respuesta del OSE con el estado tributario, hash, xmlUrl, cdrUrl y, si el OSE
     * la incluye, pdfUrl.  Usar únicamente para verificación — nunca como sustituto de emitirGRE().
     *
     * Nota de reconciliación F-08: si el OSE no devuelve hash/xmlUrl/cdrUrl en el endpoint /status,
     * el admin debe obtenerlos directamente del panel del OSE y pasarlos en ReconciliarGRERequest.
     */
    public LucodeGREResponse consultarEstadoGRE(String serie, int numero, String oseToken) {
        if (oseToken == null || oseToken.isBlank()) {
            throw new BadRequestException(
                    "La facturación electrónica aún no está activada para este negocio.");
        }

        String url = apiSunatBaseUrl + "/api/v3/status";
        java.util.Map<String, Object> body = java.util.Map.of(
                "documento", "guia_remision_remitente",
                "serie",     serie,
                "numero",    numero
        );

        log.info("🔍 Consultando estado GRE [{}-{}] en Lucode [{}]", serie, numero, url);

        try {
            String rawJson = http.post()
                    .uri(url)
                    .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + oseToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, resp) -> {
                                byte[] bytes = resp.getBody().readAllBytes();
                                String respBody = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                log.warn("Lucode /status error {}: {}", resp.getStatusCode(), respBody);
                                throw new RuntimeException("LUCODE_STATUS_ERROR:" + resp.getStatusCode() + ":" + respBody);
                            })
                    .body(String.class);

            log.debug("📨 Lucode /status raw para {}-{}: {}", serie, numero, rawJson);
            return mapper.readValue(rawJson, LucodeGREResponse.class);

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error consultando estado GRE {}-{} en Lucode: {}", serie, numero, e.getMessage(), e);
            throw new BadRequestException(
                    "No fue posible consultar el estado de la GRE en el servicio tributario. " +
                    "Verifica el token OSE e inténtalo nuevamente.");
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    LucodeGRERequest buildRequest(GuiaRemision guia) {
        GuiaRemisionTransporte t = guia.getTransporte();
        boolean esPrivado = guia.getModalidadTransporte() == ModalidadTransporteGRE.PRIVADO;

        Tenant tenant = tenantRepository.findByTenantId(guia.getTenantId())
                .orElseThrow(() -> new BadRequestException("Tenant no encontrado: " + guia.getTenantId()));

        String destDir = resolveDestinatarioDireccion(guia);
        if (destDir == null || destDir.isBlank()
                || "-".equals(destDir.trim())
                || "s/d".equalsIgnoreCase(destDir.trim())) {
            throw new BadRequestException(
                    "La dirección del destinatario no está configurada. " +
                    "Debe indicar la dirección real del destinatario.");
        }

        if (guia.getFechaTraslado() == null) {
            throw new BadRequestException(
                    "La fecha de inicio del traslado es requerida. " +
                    "Completa la fecha e intenta nuevamente.");
        }

        LucodeGRERequest.LucodeGRERequestBuilder builder = LucodeGRERequest.builder()
                .documento("guia_remision_remitente")
                .serie(guia.getSerie())
                .numero(guia.getCorrelativo())
                .fechaDeEmision(guia.getFechaTraslado().format(DATE_FMT))
                .horaDeEmision(guia.getCreatedAt()
                        .atZoneSameInstant(LIMA_ZONE)
                        .toLocalTime()
                        .format(TIME_FMT))
                .motivoDeTraslado(guia.getMotivoTrasladoCodigo())
                .modalidadDeTransporte(guia.getModalidadTransporte().codigoSunat())
                .destinatarioTipoDeDocumento(resolveDestinatarioTipoDoc(guia))
                .destinatarioNumeroDeDocumento(resolveDestinatarioNumDoc(guia))
                .destinatarioDenominacion(resolveDestinatarioNombre(guia))
                .destinatarioDireccion(destDir)
                .puntoDePartida(PuntoUbicacion.builder()
                        .ubigeo(resolveUbigeo(
                                t != null ? t.getUbigeoPartida() : null,
                                guia.getSucursalOrigen(), tenant, "partida"))
                        .direccion(resolveDireccionPartida(t, guia.getSucursalOrigen(), tenant))
                        .build())
                .puntoDeLlegada(PuntoUbicacion.builder()
                        .ubigeo(resolveUbigeo(
                                t != null ? t.getUbigeoLlegada() : null,
                                guia.getSucursalDestino(), null, "llegada"))
                        .direccion(resolveDireccionLlegada(t, guia.getSucursalDestino()))
                        .build())
                .pesoBrutoUnidadDeMedida(guia.getUnidadMedidaPeso())
                .numeroDeBultos(guia.getNumeroBultos())
                .observaciones(guia.getObservaciones())
                .items(buildItems(guia));

        if (guia.getPesoBrutoTotal() != null) {
            builder.pesoBrutoTotal(guia.getPesoBrutoTotal().toPlainString());
        }

        // Documento relacionado (venta asociada para delivery)
        if (guia.getVenta() != null) {
            builder.documentosRelacionados(List.of());  // se puede enriquecer con comprobante asociado
        }

        if (esPrivado && t != null) {
            // fechaInicioTraslado en el transporte es opcional desde el formulario;
            // si no está, se usa fechaTraslado de la GRE (mismo concepto en el flujo actual).
            java.time.LocalDate fechaInicio = t.getFechaInicioTraslado() != null
                    ? t.getFechaInicioTraslado()
                    : guia.getFechaTraslado();
            if (fechaInicio == null) {
                throw new BadRequestException(
                        "La fecha de inicio del traslado es requerida. " +
                        "Completa la fecha en los datos de transporte e intenta nuevamente.");
            }
            builder.fechaInicioDeTraslado(fechaInicio.format(DATE_FMT));
            if (t.getVehiculoPlaca() != null) {
                builder.vehiculos(List.of(VehiculoGRE.builder()
                        .vehiculo("principal")
                        .numeroDePlaca(t.getVehiculoPlaca())
                        .build()));
            }
            if (t.getConductorNumDoc() != null) {
                builder.conductores(List.of(ConductorGRE.builder()
                        .conductor("principal")
                        .tipoDeDocumento(t.getConductorTipoDoc())
                        .numeroDeDocumento(t.getConductorNumDoc())
                        .nombres(t.getConductorNombres())
                        .apellidos(t.getConductorApellidos())
                        .numeroLicenciaConducir(t.getConductorLicencia())
                        .build()));
            }
        } else if (!esPrivado && t != null) {
            builder.fechaEntregaATransportista(
                    t.getFechaEntregaTransportista() != null ? t.getFechaEntregaTransportista().format(DATE_FMT) : null);
            if (t.getTransportistaRuc() != null) {
                builder.transportista(TransportistaGRE.builder()
                        .ruc(t.getTransportistaRuc())
                        .denominacion(t.getTransportistaDenominacion())
                        .numeroRegistroMTC(t.getTransportistaNroRegistroMtc())
                        .numeroAutorizacion(t.getTransportistaNroAutorizacion())
                        .codigoEntidadAutorizadora(t.getTransportistaCodigoEntidadAutorizadora())
                        .build());
            }
        }

        return builder.build();
    }

    private List<ItemGRE> buildItems(GuiaRemision guia) {
        return guia.getItems().stream()
                .map(item -> ItemGRE.builder()
                        .codigoInterno(item.getCodigoProducto())
                        .descripcion(item.getDescripcion())
                        .unidadDeMedida(item.getUnidadMedida())
                        .cantidad(item.getCantidad().toPlainString())
                        .build())
                .collect(Collectors.toList());
    }

    private String resolveDestinatarioTipoDoc(GuiaRemision guia) {
        if (guia.getDestinatarioTipoDoc() != null) return guia.getDestinatarioTipoDoc();
        // Para traslado entre sucursales, el destinatario es el propio negocio → RUC
        return "6";
    }

    private String resolveDestinatarioNumDoc(GuiaRemision guia) {
        if (guia.getDestinatarioNumDoc() != null) return guia.getDestinatarioNumDoc();
        return "00000000000";
    }

    private String resolveDestinatarioNombre(GuiaRemision guia) {
        if (guia.getDestinatarioRazonSocial() != null) return guia.getDestinatarioRazonSocial();
        if (guia.getSucursalDestino() != null) return guia.getSucursalDestino().getNombre();
        return "DESTINATARIO";
    }

    private String resolveDestinatarioDireccion(GuiaRemision guia) {
        if (guia.getDestinatarioDireccion() != null) return guia.getDestinatarioDireccion();
        if (guia.getSucursalDestino() != null && guia.getSucursalDestino().getDireccion() != null)
            return guia.getSucursalDestino().getDireccion();
        return null;
    }

    /**
     * Resuelve el ubigeo con prioridad: transporte → sucursal → tenant (solo partida).
     * Para "llegada" el fallback al tenant NO aplica: cada destino debe tener su propio ubigeo.
     * @param tenant null cuando label es "llegada" (no hay fallback al tenant)
     */
    private String resolveUbigeo(String transporteUbigeo, Sucursal sucursal, Tenant tenant, String label) {
        if (transporteUbigeo != null && !transporteUbigeo.isBlank()) return transporteUbigeo;
        if (sucursal != null && sucursal.getUbigeo() != null && !sucursal.getUbigeo().isBlank())
            return sucursal.getUbigeo();
        if ("partida".equals(label) && tenant != null
                && tenant.getUbigeoDespacho() != null && !tenant.getUbigeoDespacho().isBlank())
            return tenant.getUbigeoDespacho();
        // Mensaje diferenciado según la causa
        if ("partida".equals(label)) {
            if (sucursal != null) {
                throw new BadRequestException(
                        "El punto de partida '" + sucursal.getNombre() + "' no tiene ubigeo configurado. " +
                        "Configúralo en Ajustes → Sucursales.");
            }
            throw new BadRequestException(
                    "No hay ubigeo configurado para el punto de partida. " +
                    "Configura el punto de despacho predeterminado en Ajustes → Mi Negocio → GRE.");
        }
        String nombre = sucursal != null ? sucursal.getNombre() : label;
        throw new BadRequestException(
                "El punto de " + label + " '" + nombre + "' no tiene configurado el ubigeo SUNAT. " +
                "Configúralo en la gestión de sucursales antes de emitir la GRE.");
    }

    /** Dirección de partida coherente con la misma fuente que el ubigeo de partida. */
    private String resolveDireccionPartida(GuiaRemisionTransporte t, Sucursal sucursalOrigen, Tenant tenant) {
        if (t != null && t.getDireccionPartida() != null && !t.getDireccionPartida().isBlank())
            return t.getDireccionPartida();
        if (sucursalOrigen != null) {
            String dc = sucursalOrigen.getDireccionCompleta();
            return (dc != null && !dc.isBlank()) ? dc : (sucursalOrigen.getDireccion() != null ? sucursalOrigen.getDireccion() : "");
        }
        return (tenant != null && tenant.getDireccionDespacho() != null) ? tenant.getDireccionDespacho() : "";
    }

    /** Dirección de llegada coherente con la misma fuente que el ubigeo de llegada. */
    private String resolveDireccionLlegada(GuiaRemisionTransporte t, Sucursal sucursalDestino) {
        if (t != null && t.getDireccionLlegada() != null && !t.getDireccionLlegada().isBlank())
            return t.getDireccionLlegada();
        if (sucursalDestino != null) {
            String dc = sucursalDestino.getDireccionCompleta();
            return (dc != null && !dc.isBlank()) ? dc : (sucursalDestino.getDireccion() != null ? sucursalDestino.getDireccion() : "");
        }
        return "";
    }

    /** Extrae todos los errores del JSON 422 de Lucode y los une en un mensaje legible. */
    static String extractLucodeErrors(com.fasterxml.jackson.databind.JsonNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.has("message")) sb.append(node.get("message").asText());
        if (node.has("errors")) {
            com.fasterxml.jackson.databind.JsonNode errors = node.get("errors");
            errors.fieldNames().forEachRemaining(field -> {
                com.fasterxml.jackson.databind.JsonNode msgs = errors.get(field);
                if (msgs.isArray()) {
                    msgs.forEach(m -> sb.append(" | ").append(field).append(": ").append(m.asText()));
                } else {
                    sb.append(" | ").append(field).append(": ").append(msgs.asText());
                }
            });
        }
        return sb.toString();
    }

}
