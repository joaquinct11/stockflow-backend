package com.stockflow.service.impl;

import com.stockflow.dto.AbrirCajaRequestDTO;
import com.stockflow.dto.CajaDTO;
import com.stockflow.dto.CerrarCajaRequestDTO;
import com.stockflow.entity.Caja;
import com.stockflow.entity.Venta;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.CajaRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.CajaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CajaServiceImpl implements CajaService {

    private final CajaRepository cajaRepository;
    private final VentaRepository ventaRepository;

    @Override
    public CajaDTO abrir(AbrirCajaRequestDTO request, Long usuarioId, String usuarioNombre, String tenantId) {
        // Verificar que no haya ya una caja abierta en este tenant (compartida por todos)
        cajaRepository.findFirstByTenantIdAndEstadoOrderByFechaAperturaDesc(tenantId, "ABIERTA")
                .ifPresent(c -> {
                    throw new BadRequestException("Ya hay una caja abierta por " + c.getUsuarioNombre() + ". Ciérrala antes de abrir una nueva.");
                });

        BigDecimal montoApertura = request.getMontoApertura() != null
                ? request.getMontoApertura().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Caja caja = Caja.builder()
                .tenantId(tenantId)
                .usuarioId(usuarioId)
                .usuarioNombre(usuarioNombre)
                .montoApertura(montoApertura)
                .estado("ABIERTA")
                .observaciones(request.getObservaciones())
                .fechaApertura(LocalDateTime.now())
                .cantidadVentas(0)
                .build();

        return toDTO(cajaRepository.save(caja));
    }

    @Override
    public CajaDTO cerrar(Long cajaId, CerrarCajaRequestDTO request, String tenantId) {
        Caja caja = cajaRepository.findByIdAndTenantId(cajaId, tenantId)
                .orElseThrow(() -> new BadRequestException("Caja no encontrada"));

        if (!"ABIERTA".equals(caja.getEstado())) {
            throw new BadRequestException("La caja ya está cerrada");
        }

        // Obtener ventas vinculadas a esta caja
        List<Venta> ventas = ventaRepository.findByCajaIdAndTenantId(cajaId, tenantId);

        BigDecimal totalEfectivo = BigDecimal.ZERO;
        BigDecimal totalTarjeta = BigDecimal.ZERO;
        BigDecimal totalYapePlin = BigDecimal.ZERO;
        BigDecimal totalIngresos = BigDecimal.ZERO;

        for (Venta v : ventas) {
            BigDecimal t = v.getTotal() != null ? v.getTotal() : BigDecimal.ZERO;
            totalIngresos = totalIngresos.add(t);
            if ("EFECTIVO".equalsIgnoreCase(v.getMetodoPago())) {
                totalEfectivo = totalEfectivo.add(t);
            } else if ("TARJETA".equalsIgnoreCase(v.getMetodoPago())) {
                totalTarjeta = totalTarjeta.add(t);
            } else if ("YAPE_PLIN".equalsIgnoreCase(v.getMetodoPago())) {
                totalYapePlin = totalYapePlin.add(t);
            }
        }

        totalEfectivo = totalEfectivo.setScale(2, RoundingMode.HALF_UP);
        totalTarjeta = totalTarjeta.setScale(2, RoundingMode.HALF_UP);
        totalYapePlin = totalYapePlin.setScale(2, RoundingMode.HALF_UP);
        totalIngresos = totalIngresos.setScale(2, RoundingMode.HALF_UP);

        BigDecimal montoContado = request.getMontoContado().setScale(2, RoundingMode.HALF_UP);
        // diferencia = lo que el cajero dice que tiene - (apertura + ventas en efectivo)
        BigDecimal esperadoEnCaja = caja.getMontoApertura().add(totalEfectivo);
        BigDecimal diferencia = montoContado.subtract(esperadoEnCaja).setScale(2, RoundingMode.HALF_UP);

        caja.setTotalEfectivo(totalEfectivo);
        caja.setTotalTarjeta(totalTarjeta);
        caja.setTotalYapePlin(totalYapePlin);
        caja.setTotalIngresos(totalIngresos);
        caja.setCantidadVentas(ventas.size());
        caja.setMontoContado(montoContado);
        caja.setDiferencia(diferencia);
        caja.setEstado("CERRADA");
        caja.setFechaCierre(LocalDateTime.now());
        if (request.getObservaciones() != null) {
            caja.setObservaciones(request.getObservaciones());
        }

        return toDTO(cajaRepository.save(caja));
    }

    @Override
    public Optional<CajaDTO> getActiva(String tenantId) {
        return cajaRepository.findFirstByTenantIdAndEstadoOrderByFechaAperturaDesc(tenantId, "ABIERTA")
                .map(this::toDTO);
    }

    @Override
    public List<CajaDTO> getAll(String tenantId) {
        return cajaRepository.findByTenantIdOrderByFechaAperturaDesc(tenantId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public Optional<CajaDTO> getById(Long id, String tenantId) {
        return cajaRepository.findByIdAndTenantId(id, tenantId).map(this::toDTO);
    }

    private CajaDTO toDTO(Caja c) {
        return CajaDTO.builder()
                .id(c.getId())
                .tenantId(c.getTenantId())
                .usuarioId(c.getUsuarioId())
                .usuarioNombre(c.getUsuarioNombre())
                .montoApertura(c.getMontoApertura())
                .totalEfectivo(c.getTotalEfectivo())
                .totalTarjeta(c.getTotalTarjeta())
                .totalYapePlin(c.getTotalYapePlin())
                .totalIngresos(c.getTotalIngresos())
                .cantidadVentas(c.getCantidadVentas())
                .montoContado(c.getMontoContado())
                .diferencia(c.getDiferencia())
                .estado(c.getEstado())
                .observaciones(c.getObservaciones())
                .fechaApertura(c.getFechaApertura())
                .fechaCierre(c.getFechaCierre())
                .build();
    }
}
