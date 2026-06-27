package com.stockflow.service.impl;

import com.stockflow.entity.Comprobante;
import com.stockflow.entity.Tenant;
import com.stockflow.repository.ComprobanteRepository;
import com.stockflow.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Consulta periódicamente el estado SUNAT de los comprobantes que quedaron
 * en "PENDIENTE" y los actualiza si ya fueron aceptados/rechazados.
 *
 * Intervalo: cada 5 minutos.
 * SUNAT suele procesar las boletas en segundos; las facturas grandes pueden
 * tardar algunos minutos más en el OSE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SunatStatusScheduler {

    private final ComprobanteRepository comprobanteRepository;
    private final TenantRepository      tenantRepository;
    private final ApiSunatService       apiSunatService;

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void actualizarPendientes() {
        List<Comprobante> pendientes = comprobanteRepository
                .findBySunatEstadoAndEstadoNot("PENDIENTE", "ANULADO");

        if (pendientes.isEmpty()) return;

        log.info("⏰ [SunatScheduler] Consultando {} comprobante(s) PENDIENTE...", pendientes.size());

        // Cargar tenants en un mapa para no hacer N queries
        Map<String, Optional<Tenant>> tenantCache = pendientes.stream()
                .map(Comprobante::getTenantId)
                .distinct()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> tenantRepository.findByTenantId(id)
                ));

        int actualizados = 0;

        for (Comprobante c : pendientes) {
            Optional<Tenant> tenantOpt = tenantCache.get(c.getTenantId());
            if (tenantOpt.isEmpty()) continue;

            Tenant tenant = tenantOpt.get();
            if (tenant.getOseToken() == null || tenant.getOseToken().isBlank()) continue;

            try {
                var resp = apiSunatService.consultarEstado(
                        c.getTipo(), c.getSerie(), c.getCorrelativo(), tenant.getOseToken());

                if (resp.fueAceptado()) {
                    c.setSunatEstado("ACEPTADO");
                    c.setSunatMensaje(resp.resumen());
                    if (resp.getPdfUrl()       != null) c.setPdfUrl(resp.getPdfUrl());
                    if (resp.getPdfTicketUrl() != null) c.setPdfTicketUrl(resp.getPdfTicketUrl());
                    if (resp.getXmlUrl()       != null) c.setXmlUrl(resp.getXmlUrl());
                    if (resp.getPayload()      != null && resp.getPayload().getHash() != null)
                        c.setHash(resp.getPayload().getHash());

                    comprobanteRepository.save(c);
                    actualizados++;
                    log.info("✅ [SunatScheduler] {} → ACEPTADO", c.getNumero());

                } else if (resp.getPayload() != null &&
                           "RECHAZADO".equalsIgnoreCase(resp.getPayload().getEstado())) {
                    c.setSunatEstado("RECHAZADO");
                    c.setSunatMensaje(resp.resumen());
                    comprobanteRepository.save(c);
                    actualizados++;
                    log.warn("❌ [SunatScheduler] {} → RECHAZADO: {}", c.getNumero(), resp.resumen());
                }
                // Si sigue PENDIENTE en ApiSunat, no tocamos nada

            } catch (Exception e) {
                log.warn("⚠️ [SunatScheduler] No se pudo consultar {} ({}): {}",
                        c.getNumero(), c.getTenantId(), e.getMessage());
            }
        }

        if (actualizados > 0) {
            log.info("✅ [SunatScheduler] {} comprobante(s) actualizados de {}",
                    actualizados, pendientes.size());
        }
    }
}
