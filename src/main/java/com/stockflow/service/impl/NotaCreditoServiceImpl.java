package com.stockflow.service.impl;

import com.stockflow.dto.NotaCreditoDTO;
import com.stockflow.dto.ValidarNotaCreditoResponseDTO;
import com.stockflow.entity.Devolucion;
import com.stockflow.entity.NotaCredito;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.NotaCreditoRepository;
import com.stockflow.service.NotaCreditoService;
import com.stockflow.util.NotaCreditoPdfGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotaCreditoServiceImpl implements NotaCreditoService {

    private final NotaCreditoRepository notaCreditoRepository;
    private final NotaCreditoPdfGenerator pdfGenerator;

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public synchronized NotaCredito generarParaDevolucion(Devolucion devolucion, String tenantId) {
        String year = String.valueOf(LocalDateTime.now().getYear());
        int maxSeq = notaCreditoRepository.findMaxSecuenciaAnio(tenantId, year);
        int nextSeq = maxSeq + 1;
        String codigo = String.format("NC-%s-%05d", year, nextSeq);

        LocalDateTime ahora = LocalDateTime.now();
        NotaCredito nc = NotaCredito.builder()
                .tenantId(tenantId)
                .codigo(codigo)
                .devolucion(devolucion)
                .montoTotal(devolucion.getTotalDevuelto())
                .estado("PENDIENTE")
                .fechaEmision(ahora)
                .fechaVencimiento(ahora.plusDays(90))
                .sucursalId(devolucion.getSucursalId())
                .build();

        NotaCredito saved = notaCreditoRepository.save(nc);
        log.info("Nota de credito generada: {} para devolucion {} (tenant={})",
                codigo, devolucion.getId(), tenantId);
        return saved;
    }

    @Override
    @Transactional
    public ValidarNotaCreditoResponseDTO validar(String codigo, String tenantId) {
        return notaCreditoRepository.findByCodigoAndTenantId(codigo, tenantId)
                .map(nc -> {
                    if (!"PENDIENTE".equals(nc.getEstado())) {
                        String fechaUsoStr = nc.getFechaUso() != null
                                ? nc.getFechaUso().format(FMT_FECHA) : "fecha desconocida";
                        return ValidarNotaCreditoResponseDTO.builder()
                                .codigo(nc.getCodigo())
                                .montoTotal(nc.getMontoTotal())
                                .estado(nc.getEstado())
                                .fechaVencimiento(nc.getFechaVencimiento())
                                .valida(false)
                                .mensaje("Esta nota ya fue utilizada el " + fechaUsoStr)
                                .build();
                    }

                    if (nc.getFechaVencimiento().isBefore(LocalDateTime.now())) {
                        nc.setEstado("ANULADA");
                        notaCreditoRepository.save(nc);
                        return ValidarNotaCreditoResponseDTO.builder()
                                .codigo(nc.getCodigo())
                                .montoTotal(nc.getMontoTotal())
                                .estado("ANULADA")
                                .fechaVencimiento(nc.getFechaVencimiento())
                                .valida(false)
                                .mensaje("Esta nota vencio el " + nc.getFechaVencimiento().format(FMT_FECHA))
                                .build();
                    }

                    return ValidarNotaCreditoResponseDTO.builder()
                            .codigo(nc.getCodigo())
                            .montoTotal(nc.getMontoTotal())
                            .estado(nc.getEstado())
                            .fechaVencimiento(nc.getFechaVencimiento())
                            .valida(true)
                            .mensaje("Nota de credito valida")
                            .build();
                })
                .orElseGet(() -> ValidarNotaCreditoResponseDTO.builder()
                        .codigo(codigo)
                        .valida(false)
                        .mensaje("Codigo no valido")
                        .build());
    }

    @Override
    @Transactional
    public void restaurarPorVentaAnulada(Long notaCreditoId, String tenantId) {
        notaCreditoRepository.findByIdAndTenantId(notaCreditoId, tenantId)
                .ifPresent(nc -> {
                    if ("USADA".equals(nc.getEstado())) {
                        nc.setEstado("PENDIENTE");
                        nc.setFechaUso(null);
                        nc.setVentaUsoId(null);
                        notaCreditoRepository.save(nc);
                        log.info("NC {} restaurada a PENDIENTE por anulacion de venta", nc.getCodigo());
                    }
                });
    }

    @Override
    @Transactional
    public NotaCredito aplicar(String codigo, Long ventaId, String tenantId) {
        ValidarNotaCreditoResponseDTO validacion = validar(codigo, tenantId);
        if (!validacion.isValida()) {
            throw new BadRequestException(validacion.getMensaje());
        }

        NotaCredito nc = notaCreditoRepository.findByCodigoAndTenantId(codigo, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Nota de credito no encontrada"));

        nc.setEstado("USADA");
        nc.setFechaUso(LocalDateTime.now());
        nc.setVentaUsoId(ventaId);
        NotaCredito saved = notaCreditoRepository.save(nc);
        log.info("Nota de credito {} aplicada a venta {} (tenant={})", codigo, ventaId, tenantId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotaCreditoDTO> getAll(String tenantId, Long sucursalId) {
        List<NotaCredito> list = sucursalId != null
                ? notaCreditoRepository.findByTenantIdAndSucursalId(tenantId, sucursalId)
                : notaCreditoRepository.findByTenantIdOrderByFechaEmisionDesc(tenantId);
        return list.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generarPdf(Long id, String tenantId, Tenant tenant, String formato) {
        NotaCredito nc = notaCreditoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Nota de credito no encontrada: " + id));
        return "TICKET".equalsIgnoreCase(formato)
                ? pdfGenerator.generarTicket(nc, tenant)
                : pdfGenerator.generar(nc, tenant);
    }

    private NotaCreditoDTO toDTO(NotaCredito nc) {
        Long ventaOrigenId = null;
        if (nc.getDevolucion() != null && nc.getDevolucion().getVenta() != null) {
            ventaOrigenId = nc.getDevolucion().getVenta().getId();
        }
        return NotaCreditoDTO.builder()
                .id(nc.getId())
                .codigo(nc.getCodigo())
                .devolucionId(nc.getDevolucion() != null ? nc.getDevolucion().getId() : null)
                .ventaOrigenId(ventaOrigenId)
                .montoTotal(nc.getMontoTotal())
                .estado(nc.getEstado())
                .fechaEmision(nc.getFechaEmision())
                .fechaVencimiento(nc.getFechaVencimiento())
                .fechaUso(nc.getFechaUso())
                .ventaUsoId(nc.getVentaUsoId())
                .tenantId(nc.getTenantId())
                .build();
    }
}
