package com.stockflow.service.impl;

import com.stockflow.dto.superadmin.*;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.*;
import com.stockflow.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminServiceImpl implements SuperAdminService {

    private final TenantRepository       tenantRepository;
    private final SuscripcionRepository  suscripcionRepository;
    private final UsuarioRepository      usuarioRepository;
    private final ProductoRepository     productoRepository;
    private final VentaRepository        ventaRepository;
    private final ComprobanteRepository  comprobanteRepository;

    // ── Listar tenants ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<TenantResumenDTO> listarTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toResumen)
                .toList();
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SuperAdminStatsDTO obtenerStats() {
        List<Tenant> tenants = tenantRepository.findAll();
        long activos = 0, enTrial = 0, vencidos = 0, cancelados = 0, pastDue = 0;
        BigDecimal mrr = BigDecimal.ZERO;

        for (Tenant t : tenants) {
            Optional<Suscripcion> s = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(t.getTenantId());
            if (s.isEmpty()) continue;
            String estado = s.get().getEstado();
            switch (estado) {
                case "ACTIVA"    -> { activos++;    if (s.get().getPrecioMensual() != null) mrr = mrr.add(s.get().getPrecioMensual()); }
                case "TRIAL"     -> enTrial++;
                case "VENCIDA"   -> vencidos++;
                case "CANCELADA" -> cancelados++;
                case "PAST_DUE"  -> pastDue++;
            }
        }

        return SuperAdminStatsDTO.builder()
                .totalTenants(tenants.size())
                .activos(activos).enTrial(enTrial)
                .vencidos(vencidos).cancelados(cancelados)
                .pastDue(pastDue).mrrEstimado(mrr)
                .build();
    }

    // ── Finanzas ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SuperAdminFinanzasDTO obtenerFinanzas() {
        List<Tenant> tenants = tenantRepository.findAll();
        BigDecimal mrr = BigDecimal.ZERO;
        long cantBasico = 0, cantPro = 0, activos = 0, conOse = 0;

        for (Tenant t : tenants) {
            if (t.getOseToken() != null && !t.getOseToken().isBlank()) conOse++;
            Optional<Suscripcion> s = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(t.getTenantId());
            if (s.isEmpty()) continue;
            if ("ACTIVA".equals(s.get().getEstado())) {
                activos++;
                if (s.get().getPrecioMensual() != null) mrr = mrr.add(s.get().getPrecioMensual());
                if ("BASICO".equalsIgnoreCase(s.get().getPlanId())) cantBasico++;
                else if ("PRO".equalsIgnoreCase(s.get().getPlanId())) cantPro++;
            }
        }

        return SuperAdminFinanzasDTO.builder()
                .mrrActual(mrr)
                .mrrAnualProyectado(mrr.multiply(BigDecimal.valueOf(12)))
                .cantBasico(cantBasico)
                .cantPro(cantPro)
                .tenantsActivos(activos)
                .tenantsConOse(conOse)
                .build();
    }

    // ── Historial cobros ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SuscripcionHistorialDTO> obtenerCobros(String tenantId) {
        return suscripcionRepository.findByTenantId(tenantId).stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .map(s -> SuscripcionHistorialDTO.builder()
                        .id(s.getId())
                        .planId(s.getPlanId())
                        .estado(s.getEstado())
                        .precioMensual(s.getPrecioMensual())
                        .metodoPago(s.getMetodoPago())
                        .ultimos4Digitos(s.getUltimos4Digitos())
                        .preapprovalId(s.getPreapprovalId())
                        .fechaInicio(s.getFechaInicio())
                        .fechaProximoCobro(s.getFechaProximoCobro())
                        .trialEndDate(s.getTrialEndDate())
                        .currentPeriodEnd(s.getCurrentPeriodEnd())
                        .createdAt(s.getCreatedAt())
                        .build())
                .toList();
    }

    // ── Actualizar suscripción ───────────────────────────────────────────────

    @Override
    @Transactional
    public TenantResumenDTO actualizarSuscripcion(String tenantId, SuscripcionManualUpdateDTO dto) {
        Suscripcion s = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId)
                .orElseThrow(() -> new BadRequestException("No existe suscripción para el tenant: " + tenantId));
        if (dto.getPlanId()            != null) s.setPlanId(dto.getPlanId());
        if (dto.getEstado()            != null) s.setEstado(dto.getEstado());
        if (dto.getPrecioMensual()     != null) s.setPrecioMensual(dto.getPrecioMensual());
        if (dto.getFechaProximoCobro() != null) s.setFechaProximoCobro(dto.getFechaProximoCobro());
        if (dto.getTrialEndDate()      != null) s.setTrialEndDate(dto.getTrialEndDate());
        if (dto.getCurrentPeriodEnd()  != null) s.setCurrentPeriodEnd(dto.getCurrentPeriodEnd());
        suscripcionRepository.save(s);
        log.info("✅ SA actualizó suscripción tenant={} plan={} estado={}", tenantId, s.getPlanId(), s.getEstado());
        return toResumen(tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException("Tenant no encontrado: " + tenantId)));
    }

    // ── Extender trial ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public TenantResumenDTO extenderTrial(String tenantId, int dias) {
        Suscripcion s = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId)
                .orElseThrow(() -> new BadRequestException("No existe suscripción para el tenant: " + tenantId));
        LocalDateTime base = s.getTrialEndDate() != null && s.getTrialEndDate().isAfter(LocalDateTime.now())
                ? s.getTrialEndDate()
                : LocalDateTime.now();
        s.setTrialEndDate(base.plusDays(dias));
        s.setEstado("TRIAL");
        suscripcionRepository.save(s);
        log.info("✅ SA extendió trial de tenant={} en {} días → hasta {}", tenantId, dias, s.getTrialEndDate());
        return toResumen(tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException("Tenant no encontrado: " + tenantId)));
    }

    // ── Toggle activo ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void toggleActivo(String tenantId, boolean activo) {
        Tenant t = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException("Tenant no encontrado: " + tenantId));
        t.setActivo(activo);
        tenantRepository.save(t);
        log.info("✅ SA {} tenant={}", activo ? "activó" : "desactivó", tenantId);
    }

    // ── OSE ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void actualizarOse(String tenantId, OseUpdateDTO dto) {
        Tenant t = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException("Tenant no encontrado: " + tenantId));
        if (dto.getOseUrl()   != null) t.setOseUrl(dto.getOseUrl());
        if (dto.getOseToken() != null) t.setOseToken(dto.getOseToken());
        tenantRepository.save(t);
        log.info("✅ SA actualizó OSE de tenant={}", tenantId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TenantResumenDTO toResumen(Tenant t) {
        Optional<Suscripcion> sus = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(t.getTenantId());

        TenantResumenDTO.TenantResumenDTOBuilder b = TenantResumenDTO.builder()
                .tenantId(t.getTenantId())
                .nombre(t.getNombre())
                .ruc(t.getRuc())
                .emailContacto(t.getEmailContacto())
                .rubro(t.getRubro())
                .activo(t.getActivo())
                .oseUrl(t.getOseUrl())
                .oseToken(t.getOseToken())
                .totalUsuarios(usuarioRepository.countByTenantId(t.getTenantId()))
                .totalProductos(productoRepository.countByTenantId(t.getTenantId()))
                .totalVentas(ventaRepository.countByTenantId(t.getTenantId()))
                .totalComprobantes(comprobanteRepository.countByTenantId(t.getTenantId()));

        sus.ifPresent(s -> b
                .suscripcionId(s.getId())
                .planId(s.getPlanId())
                .estadoSuscripcion(s.getEstado())
                .precioMensual(s.getPrecioMensual())
                .preapprovalId(s.getPreapprovalId())
                .ultimos4Digitos(s.getUltimos4Digitos())
                .metodoPago(s.getMetodoPago())
                .fechaProximoCobro(s.getFechaProximoCobro())
                .trialEndDate(s.getTrialEndDate())
                .currentPeriodEnd(s.getCurrentPeriodEnd())
                .suscripcionCreadaEn(s.getCreatedAt()));

        return b.build();
    }
}
