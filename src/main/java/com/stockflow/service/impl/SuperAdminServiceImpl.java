package com.stockflow.service.impl;

import com.stockflow.dto.superadmin.*;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.TenantRepository;
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

    private final TenantRepository      tenantRepository;
    private final SuscripcionRepository suscripcionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TenantResumenDTO> listarTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toResumen)
                .toList();
    }

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
                default          -> { /* estado desconocido, ignorar */ }
            }
        }

        return SuperAdminStatsDTO.builder()
                .totalTenants(tenants.size())
                .activos(activos).enTrial(enTrial)
                .vencidos(vencidos).cancelados(cancelados)
                .pastDue(pastDue).mrrEstimado(mrr)
                .build();
    }

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
                String codigo = s.get().getPlanCodigo() != null ? s.get().getPlanCodigo().toUpperCase() : "";
                if (codigo.contains("BASICO")) cantBasico++;
                else if (codigo.contains("PRO")) cantPro++;
            }
        }

        return SuperAdminFinanzasDTO.builder()
                .mrr(mrr).cantBasico(cantBasico).cantPro(cantPro)
                .activos(activos).conOse(conOse)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuscripcionHistorialDTO> obtenerCobros(String tenantId) {
        return suscripcionRepository.findByTenantId(tenantId).stream()
                .map(s -> SuscripcionHistorialDTO.builder()
                        .id(s.getId())
                        .planId(s.getPlanId())
                        .planCodigo(s.getPlanCodigo())
                        .precioMensual(s.getPrecioMensual())
                        .estado(s.getEstado())
                        .fechaInicio(s.getFechaInicio())
                        .fechaProximoCobro(s.getFechaProximoCobro())
                        .fechaCancelacion(s.getFechaCancelacion())
                        .metodoPago(s.getMetodoPago())
                        .ultimos4Digitos(s.getUltimos4Digitos())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public TenantResumenDTO actualizarSuscripcion(String tenantId, SuscripcionManualUpdateDTO dto) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException("Tenant no encontrado: " + tenantId));
        Suscripcion s = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId)
                .orElseThrow(() -> new BadRequestException("Sin suscripción activa para: " + tenantId));
        if (dto.getPlanId()            != null) s.setPlanId(dto.getPlanId());
        if (dto.getPlanCodigo()        != null) s.setPlanCodigo(dto.getPlanCodigo());
        if (dto.getPrecioMensual()     != null) s.setPrecioMensual(dto.getPrecioMensual());
        if (dto.getEstado()            != null) s.setEstado(dto.getEstado());
        if (dto.getFechaProximoCobro() != null) s.setFechaProximoCobro(dto.getFechaProximoCobro());
        suscripcionRepository.save(s);
        log.info("SA actualizó suscripción de tenant={}", tenantId);
        return toResumen(tenant);
    }

    @Override
    @Transactional
    public TenantResumenDTO extenderTrial(String tenantId, int dias) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException("Tenant no encontrado: " + tenantId));
        Suscripcion s = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId)
                .orElseThrow(() -> new BadRequestException("Sin suscripción para: " + tenantId));
        LocalDateTime nuevoFin = (s.getTrialEndDate() != null ? s.getTrialEndDate() : LocalDateTime.now())
                .plusDays(dias);
        s.setTrialEndDate(nuevoFin);
        s.setEstado("TRIAL");
        suscripcionRepository.save(s);
        log.info("SA extendió trial de tenant={} en {} días hasta {}", tenantId, dias, nuevoFin);
        return toResumen(tenant);
    }

    @Override
    @Transactional
    public void toggleActivo(String tenantId, boolean activo) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException("Tenant no encontrado: " + tenantId));
        tenant.setActivo(activo);
        tenantRepository.save(tenant);
        log.info("SA {} tenant={}", activo ? "activó" : "desactivó", tenantId);
    }

    @Override
    @Transactional
    public void actualizarOse(String tenantId, OseUpdateDTO dto) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException("Tenant no encontrado: " + tenantId));
        tenant.setOseUrl(dto.getOseUrl());
        tenant.setOseToken(dto.getOseToken());
        tenantRepository.save(tenant);
        log.info("SA actualizó OSE para tenant={}", tenantId);
    }

    private TenantResumenDTO toResumen(Tenant t) {
        Optional<Suscripcion> s = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(t.getTenantId());
        return TenantResumenDTO.builder()
                .tenantId(t.getTenantId())
                .nombre(t.getNombre())
                .activo(t.getActivo())
                .rubro(t.getRubro())
                .planId(s.map(Suscripcion::getPlanId).orElse(null))
                .planCodigo(s.map(Suscripcion::getPlanCodigo).orElse(null))
                .precioMensual(s.map(Suscripcion::getPrecioMensual).orElse(null))
                .estadoSuscripcion(s.map(Suscripcion::getEstado).orElse(null))
                .fechaProximoCobro(s.map(Suscripcion::getFechaProximoCobro).orElse(null))
                .trialEndDate(s.map(Suscripcion::getTrialEndDate).orElse(null))
                .tieneOse(t.getOseToken() != null && !t.getOseToken().isBlank())
                .build();
    }
}
