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
import com.stockflow.repository.ComprobanteRepository;
import com.stockflow.repository.ComprobanteSerieRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.ComprobanteService;
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

    private static final BigDecimal IGV_RATE = new BigDecimal("0.18");

    private final ComprobanteRepository comprobanteRepository;
    private final ComprobanteSerieRepository comprobanteSerieRepository;
    private final VentaRepository ventaRepository;

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

        // Resolve serie – default B001 / F001 if not provided
        String serie = (request.getSerie() != null && !request.getSerie().isBlank())
                ? request.getSerie().toUpperCase()
                : (tipo.equals("BOLETA") ? "B001" : "F001");

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

        // Compute financial amounts from venta detalles
        BigDecimal subtotal = venta.getDetalles().stream()
                .map(DetalleVenta::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal igv = subtotal.multiply(IGV_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(igv).setScale(2, RoundingMode.HALF_UP);

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
                .sunatEstado("PENDIENTE")
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
    public List<ComprobanteDTO> listar(String tenantId, String tipo, String estado,
                                       LocalDateTime from, LocalDateTime to,
                                       Long ventaId, String search) {
        search = (search == null) ? "" : search.trim();
        tipo = (tipo == null || tipo.isBlank()) ? null : tipo.trim();
        estado = (estado == null || estado.isBlank()) ? null : estado.trim();

        return comprobanteRepository
                .findFiltered(tenantId, tipo, estado, from, to, ventaId, search)
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

        comprobante.setEstado("ANULADO");
        log.info("🗑️ Comprobante anulado: {} (tenant {})", comprobante.getNumero(), tenantId);
        return toDTO(comprobanteRepository.save(comprobante));
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    private ComprobanteDTO toDTO(Comprobante c) {
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
                .sunatTicket(c.getSunatTicket())
                .hash(c.getHash())
                .qr(c.getQr())
                .pdfUrl(c.getPdfUrl())
                .xmlUrl(c.getXmlUrl())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
