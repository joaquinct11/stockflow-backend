package com.stockflow.service.impl;

import com.stockflow.dto.ComprobanteDTO;
import com.stockflow.dto.EmitirComprobanteRequest;
import com.stockflow.entity.Comprobante;
import com.stockflow.entity.ComprobanteSerie;
import com.stockflow.entity.DetalleVenta;
import com.stockflow.entity.Venta;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.dto.ApiSunatComprobanteResponse;
import com.stockflow.entity.Tenant;
import com.stockflow.repository.ComprobanteRepository;
import com.stockflow.repository.ComprobanteSerieRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.ComprobanteService;
import com.stockflow.service.VentaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComprobanteServiceImpl implements ComprobanteService {

    private final ComprobanteRepository     comprobanteRepository;
    private final ComprobanteSerieRepository comprobanteSerieRepository;
    private final VentaRepository            ventaRepository;
    private final TenantRepository           tenantRepository;
    private final ApiSunatService            apiSunatService;
    private final VentaService               ventaService;

    @Override
    @Transactional
    public ComprobanteDTO emitir(EmitirComprobanteRequest request, String tenantId) {
        String tipo = request.getTipo().toUpperCase();
        if (!tipo.equals("BOLETA") && !tipo.equals("FACTURA")) {
            throw new BadRequestException("Tipo de comprobante inválido. Use BOLETA o FACTURA.");
        }

        // Validate venta exists and belongs to tenant
        Venta venta = ventaRepository.findById(request.getVentaId())
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Venta no encontrada: " + request.getVentaId()));

        // Enforce 1:1 – no emitted comprobante yet for this venta
        boolean yaEmitido = comprobanteRepository.existsByVentaIdAndTenantIdAndEstadoNot(
                venta.getId(), tenantId, "ANULADO");
        if (yaEmitido) {
            throw new ConflictException("La venta ya tiene un comprobante emitido.");
        }

        // Resolve serie — use tenant config if not explicitly provided in the request
        String defaultSerie = tenantRepository.findByTenantId(tenantId)
                .map(t -> tipo.equals("BOLETA")
                        ? (t.getSerieBoleta() != null ? t.getSerieBoleta() : "BBB1")
                        : (t.getSerieFactura() != null ? t.getSerieFactura() : "FFF1"))
                .orElse(tipo.equals("BOLETA") ? "BBB1" : "FFF1");

        String serie = (request.getSerie() != null && !request.getSerie().isBlank())
                ? request.getSerie().toUpperCase()
                : defaultSerie;

        // Lock the series row and increment correlativo
        ComprobanteSerie serieRow = comprobanteSerieRepository
                .findByTenantIdAndTipoAndSerieForUpdate(tenantId, tipo, serie)
                .orElseGet(() -> {
                    log.info("⚙️ Creando serie {} {} para tenant {}", tipo, serie, tenantId);
                    ComprobanteSerie newSerie = ComprobanteSerie.builder()
                            .tenantId(tenantId)
                            .tipo(tipo)
                            .serie(serie)
                            .ultimoCorrelativo(0)
                            .build();
                    return comprobanteSerieRepository.save(newSerie);
                });

        int correlativo = serieRow.getUltimoCorrelativo() + 1;
        serieRow.setUltimoCorrelativo(correlativo);
        comprobanteSerieRepository.save(serieRow);

        String numero = serie + "-" + String.format("%08d", correlativo);

        // Los precios ya incluyen IGV → total = suma directa de subtotales.
        // Base e IGV se extraen para el desglose: base = total/1.18, igv = total - base
        BigDecimal total = venta.getDetalles().stream()
                .map(DetalleVenta::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = total.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotal).setScale(2, RoundingMode.HALF_UP);

        Comprobante comprobante = Comprobante.builder()
                .tenantId(tenantId)
                .venta(venta)
                .tipo(tipo)
                .serie(serie)
                .correlativo(correlativo)
                .numero(numero)
                .fechaEmision(LocalDateTime.now())
                .estado("EMITIDO")
                .subtotal(subtotal)
                .igv(igv)
                .total(total)
                .receptorDocTipo(request.getReceptorDocTipo())
                .receptorDocNumero(request.getReceptorDocNumero())
                .receptorNombre(request.getReceptorNombre())
                .receptorDireccion(request.getReceptorDireccion())
                .createdAt(LocalDateTime.now())
                .build();

        Comprobante saved = comprobanteRepository.save(comprobante);
        log.info("✅ Comprobante emitido: {} para venta {} (tenant {})", numero, venta.getId(), tenantId);
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ComprobanteDTO> obtenerPorId(Long id, String tenantId) {
        return comprobanteRepository.findById(id)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ComprobanteDTO> obtenerPorVenta(Long ventaId, String tenantId) {
        return comprobanteRepository.findByVentaIdAndTenantId(ventaId, tenantId)
                .map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComprobanteDTO> listar(String tenantId, Long vendedorId, String tipo, String estado,
                                       LocalDateTime from, LocalDateTime to,
                                       Long ventaId, String search) {
        search = (search == null) ? "" : search.trim();
        tipo = (tipo == null || tipo.isBlank()) ? null : tipo.trim();
        estado = (estado == null || estado.isBlank()) ? null : estado.trim();

        return comprobanteRepository
                .findFiltered(tenantId, vendedorId, tipo, estado, from, to, ventaId, search)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ComprobanteDTO anular(Long id, String tenantId) {
        Comprobante comprobante = comprobanteRepository.findById(id)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado: " + id));

        if ("ANULADO".equals(comprobante.getEstado())) {
            throw new ConflictException("El comprobante ya se encuentra anulado.");
        }

        // Si fue aceptado por SUNAT, hay que comunicar la baja a ApiSunat también
        if ("ACEPTADO".equals(comprobante.getSunatEstado())) {
            Tenant tenant = tenantRepository.findByTenantId(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));
            try {
                ApiSunatComprobanteResponse resp = apiSunatService.anular(comprobante, tenant, null);
                comprobante.setSunatEstado("ANULADO");
                comprobante.setSunatMensaje(resp.resumen());
                log.info("🗑️ SUNAT baja comunicada para {}: {}", comprobante.getNumero(), resp.resumen());
            } catch (Exception e) {
                log.error("⚠️ No se pudo comunicar baja a SUNAT para {}: {}", comprobante.getNumero(), e.getMessage());
                // Continuamos con la anulación local aunque falle SUNAT
                comprobante.setSunatEstado("ANULADO_LOCAL");
                comprobante.setSunatMensaje("Anulado localmente — error al comunicar baja a SUNAT: " + e.getMessage());
            }
        }

        comprobante.setEstado("ANULADO");
        log.info("🗑️ Comprobante anulado: {} (tenant {})", comprobante.getNumero(), tenantId);
        ComprobanteDTO result = toDTO(comprobanteRepository.save(comprobante));

        // Anular la venta subyacente → repone stock automáticamente
        Venta venta = comprobante.getVenta();
        if (venta != null && !"ANULADA".equals(venta.getEstado())) {
            try {
                ventaService.anularVenta(venta.getId(), tenantId);
                log.info("📦 Stock repuesto por anulación de comprobante {} (venta #{})",
                        comprobante.getNumero(), venta.getId());
            } catch (Exception e) {
                log.warn("No se pudo anular la venta #{} al anular comprobante {}: {}",
                        venta.getId(), comprobante.getNumero(), e.getMessage());
            }
        }

        return result;
    }

    // ── SUNAT / OSE ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ComprobanteDTO enviarASunat(Long id, String tenantId) {

        Comprobante comprobante = comprobanteRepository.findById(id)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado: " + id));

        if ("ANULADO".equals(comprobante.getEstado())) {
            throw new BadRequestException("No se puede enviar a SUNAT un comprobante anulado.");
        }
        if ("ACEPTADO".equals(comprobante.getSunatEstado())) {
            throw new ConflictException("El comprobante ya fue aceptado por SUNAT.");
        }

        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Marcar como pendiente antes de llamar al OSE
        comprobante.setSunatEstado("PENDIENTE");
        comprobanteRepository.save(comprobante);

        try {
            ApiSunatComprobanteResponse resp = apiSunatService.enviar(comprobante, tenant);

            String nuevoEstado;
            if (resp.fueAceptado()) {
                nuevoEstado = "ACEPTADO";
            } else if (resp.fueRegistrado()) {
                nuevoEstado = "PENDIENTE";
            } else {
                nuevoEstado = "RECHAZADO";
            }
            comprobante.setSunatEstado(nuevoEstado);
            comprobante.setSunatMensaje(resp.resumen());
            if (resp.getPdfUrl()       != null) comprobante.setPdfUrl(resp.getPdfUrl());
            if (resp.getPdfTicketUrl() != null) comprobante.setPdfTicketUrl(resp.getPdfTicketUrl());
            if (resp.getXmlUrl()       != null) comprobante.setXmlUrl(resp.getXmlUrl());
            if (resp.getPayload()      != null && resp.getPayload().getHash() != null)
                comprobante.setHash(resp.getPayload().getHash());

            log.info("🏛️ SUNAT {} para {}: {}",
                    comprobante.getSunatEstado(), comprobante.getNumero(), resp.resumen());

        } catch (Exception e) {
            comprobante.setSunatEstado("ERROR");
            comprobanteRepository.save(comprobante);
            log.error("❌ Error enviando {} a SUNAT: {}", comprobante.getNumero(), e.getMessage());
            throw e;
        }

        return toDTO(comprobanteRepository.save(comprobante));
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    private ComprobanteDTO toDTO(Comprobante c) {
        List<ComprobanteDTO.ItemComprobanteDTO> items = c.getVenta() != null && c.getVenta().getDetalles() != null
                ? c.getVenta().getDetalles().stream()
                        .map(d -> ComprobanteDTO.ItemComprobanteDTO.builder()
                                .productoId(d.getProducto() != null ? d.getProducto().getId() : null)
                                .productoNombre(d.getProducto() != null ? d.getProducto().getNombre() : null)
                                .codigoBarras(d.getProducto() != null ? d.getProducto().getCodigoBarras() : null)
                                .cantidad(d.getCantidad())
                                .precioUnitario(d.getPrecioUnitario())
                                .subtotal(d.getSubtotal())
                                .build())
                        .collect(Collectors.toList())
                : List.of();

        return ComprobanteDTO.builder()
                .id(c.getId())
                .tenantId(c.getTenantId())
                .ventaId(c.getVenta().getId())
                .tipo(c.getTipo())
                .serie(c.getSerie())
                .correlativo(c.getCorrelativo())
                .numero(c.getNumero())
                .fechaEmision(c.getFechaEmision())
                .estado(c.getEstado())
                .subtotal(c.getSubtotal())
                .igv(c.getIgv())
                .total(c.getTotal())
                .receptorDocTipo(c.getReceptorDocTipo())
                .receptorDocNumero(c.getReceptorDocNumero())
                .receptorNombre(c.getReceptorNombre())
                .receptorDireccion(c.getReceptorDireccion())
                .sunatEstado(c.getSunatEstado())
                .sunatMensaje(c.getSunatMensaje())
                .sunatTicket(c.getSunatTicket())
                .hash(c.getHash())
                .qr(c.getQr())
                .pdfUrl(c.getPdfUrl())
                .pdfTicketUrl(c.getPdfTicketUrl())
                .xmlUrl(c.getXmlUrl())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .items(items)
                .build();
    }
}
