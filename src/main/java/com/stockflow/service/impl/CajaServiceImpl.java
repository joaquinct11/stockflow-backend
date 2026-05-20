package com.stockflow.service.impl;

import com.stockflow.dto.AbrirCajaRequestDTO;
import com.stockflow.dto.CajaDTO;
import com.stockflow.dto.CerrarCajaRequestDTO;
import com.stockflow.dto.RegistrarRetiroRequestDTO;
import com.stockflow.dto.RetiroCajaDTO;
import com.stockflow.entity.Caja;
import com.stockflow.entity.RetiroCaja;
import com.stockflow.entity.Tenant;
import com.stockflow.entity.Venta;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.CajaRepository;
import com.stockflow.repository.RetiroCajaRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.CajaService;
import com.stockflow.service.EmailService;
import com.stockflow.service.NotificacionService;
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
    private final RetiroCajaRepository retiroCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final EmailService emailService;
    private final NotificacionService notificacionService;

    @Override
    public CajaDTO abrir(AbrirCajaRequestDTO request, Long usuarioId, String usuarioNombre, String tenantId) {
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
    public RetiroCajaDTO registrarRetiro(Long cajaId, RegistrarRetiroRequestDTO request,
                                         Long usuarioId, String usuarioNombre, String tenantId) {
        Caja caja = cajaRepository.findByIdAndTenantId(cajaId, tenantId)
                .orElseThrow(() -> new BadRequestException("Caja no encontrada"));

        if (!"ABIERTA".equals(caja.getEstado())) {
            throw new BadRequestException("No se puede registrar un retiro en una caja cerrada");
        }

        BigDecimal monto = request.getMonto().setScale(2, RoundingMode.HALF_UP);

        RetiroCaja retiro = RetiroCaja.builder()
                .tenantId(tenantId)
                .cajaId(cajaId)
                .usuarioId(usuarioId)
                .usuarioNombre(usuarioNombre)
                .monto(monto)
                .motivo(request.getMotivo())
                .fecha(LocalDateTime.now())
                .build();

        RetiroCaja saved = retiroCajaRepository.save(retiro);
        log.info("💸 Retiro parcial registrado: caja={} monto={} usuario={}", cajaId, monto, usuarioNombre);

        return toRetiroDTO(saved);
    }

    @Override
    public CajaDTO cerrar(Long cajaId, CerrarCajaRequestDTO request, String tenantId) {
        Caja caja = cajaRepository.findByIdAndTenantId(cajaId, tenantId)
                .orElseThrow(() -> new BadRequestException("Caja no encontrada"));

        if (!"ABIERTA".equals(caja.getEstado())) {
            throw new BadRequestException("La caja ya está cerrada");
        }

        List<Venta> ventas = ventaRepository.findByCajaIdAndTenantId(cajaId, tenantId);

        BigDecimal totalEfectivo = BigDecimal.ZERO;
        BigDecimal totalTarjeta  = BigDecimal.ZERO;
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
        totalTarjeta  = totalTarjeta.setScale(2, RoundingMode.HALF_UP);
        totalYapePlin = totalYapePlin.setScale(2, RoundingMode.HALF_UP);
        totalIngresos = totalIngresos.setScale(2, RoundingMode.HALF_UP);

        // Total retiros parciales del turno
        List<RetiroCaja> retirosList = retiroCajaRepository.findByCajaIdOrderByFechaAsc(cajaId);
        BigDecimal totalRetiros = retirosList.stream()
                .map(RetiroCaja::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        int cantidadRetiros = retirosList.size();

        BigDecimal montoContado = request.getMontoContado().setScale(2, RoundingMode.HALF_UP);
        // diferencia = contado - (apertura + efectivo ventas - retiros)
        BigDecimal esperadoEnCaja = caja.getMontoApertura().add(totalEfectivo).subtract(totalRetiros);
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

        CajaDTO resultado = toDTO(cajaRepository.save(caja));

        if (diferencia.compareTo(BigDecimal.ZERO) != 0) {
            try {
                String descMsg = diferencia.compareTo(BigDecimal.ZERO) > 0
                        ? "Sobrante de S/ " + diferencia.abs().toPlainString()
                        : "Faltante de S/ " + diferencia.abs().toPlainString();
                notificacionService.notificarRoles(
                        tenantId,
                        List.of("ADMIN", "GERENTE"),
                        "DESCUADRE_CAJA",
                        "⚠️ Descuadre en cierre de caja",
                        String.format("La caja cerrada por %s presenta un %s al momento del cierre.",
                                caja.getUsuarioNombre(), descMsg),
                        cajaId, "CAJA");
            } catch (Exception e) {
                log.warn("No se pudo crear notificación de descuadre: {}", e.getMessage());
            }
        }

        try {
            String empresaNombre = tenantRepository.findByTenantId(tenantId)
                    .map(Tenant::getNombre).orElse(tenantId);

            usuarioRepository.findAdminYGerenteByTenant(tenantId).forEach(u ->
                emailService.enviarResumenCierreCaja(
                        u.getEmail(), empresaNombre, caja.getUsuarioNombre(),
                        caja.getMontoApertura(), caja.getTotalEfectivo(),
                        caja.getTotalTarjeta(), caja.getTotalYapePlin(),
                        caja.getTotalIngresos(), caja.getMontoContado(),
                        caja.getDiferencia(), caja.getCantidadVentas(),
                        totalRetiros, cantidadRetiros)
            );
        } catch (Exception e) {
            log.warn("⚠️ No se pudo enviar email de cierre de caja: {}", e.getMessage());
        }

        return resultado;
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CajaDTO toDTO(Caja c) {
        BigDecimal totalEfectivo = c.getTotalEfectivo();
        BigDecimal totalTarjeta  = c.getTotalTarjeta();
        BigDecimal totalYapePlin = c.getTotalYapePlin();
        BigDecimal totalIngresos = c.getTotalIngresos();
        Integer cantidadVentas   = c.getCantidadVentas();

        if ("ABIERTA".equals(c.getEstado())) {
            List<Venta> ventas = ventaRepository.findByCajaIdAndTenantId(c.getId(), c.getTenantId())
                    .stream()
                    .filter(v -> !"ANULADA".equals(v.getEstado()))
                    .toList();

            totalEfectivo = BigDecimal.ZERO;
            totalTarjeta  = BigDecimal.ZERO;
            totalYapePlin = BigDecimal.ZERO;
            totalIngresos = BigDecimal.ZERO;

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

            totalEfectivo  = totalEfectivo.setScale(2, RoundingMode.HALF_UP);
            totalTarjeta   = totalTarjeta.setScale(2, RoundingMode.HALF_UP);
            totalYapePlin  = totalYapePlin.setScale(2, RoundingMode.HALF_UP);
            totalIngresos  = totalIngresos.setScale(2, RoundingMode.HALF_UP);
            cantidadVentas = ventas.size();
        }

        // Retiros del turno
        List<RetiroCaja> retirosList = retiroCajaRepository.findByCajaIdOrderByFechaAsc(c.getId());
        BigDecimal totalRetiros = retirosList.stream()
                .map(RetiroCaja::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<RetiroCajaDTO> retirosDTO = retirosList.stream()
                .map(this::toRetiroDTO)
                .toList();

        return CajaDTO.builder()
                .id(c.getId())
                .tenantId(c.getTenantId())
                .usuarioId(c.getUsuarioId())
                .usuarioNombre(c.getUsuarioNombre())
                .montoApertura(c.getMontoApertura())
                .totalEfectivo(totalEfectivo)
                .totalTarjeta(totalTarjeta)
                .totalYapePlin(totalYapePlin)
                .totalIngresos(totalIngresos)
                .cantidadVentas(cantidadVentas)
                .totalRetiros(totalRetiros)
                .retiros(retirosDTO)
                .montoContado(c.getMontoContado())
                .diferencia(c.getDiferencia())
                .estado(c.getEstado())
                .observaciones(c.getObservaciones())
                .fechaApertura(c.getFechaApertura())
                .fechaCierre(c.getFechaCierre())
                .build();
    }

    private RetiroCajaDTO toRetiroDTO(RetiroCaja r) {
        return RetiroCajaDTO.builder()
                .id(r.getId())
                .cajaId(r.getCajaId())
                .usuarioId(r.getUsuarioId())
                .usuarioNombre(r.getUsuarioNombre())
                .monto(r.getMonto())
                .motivo(r.getMotivo())
                .fecha(r.getFecha())
                .build();
    }
}
