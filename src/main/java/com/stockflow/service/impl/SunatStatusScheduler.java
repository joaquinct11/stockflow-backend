package com.stockflow.service.impl;

import com.stockflow.entity.Comprobante;
import com.stockflow.entity.Tenant;
import com.stockflow.repository.ComprobanteRepository;
import com.stockflow.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consulta periódicamente el estado SUNAT de los comprobantes que quedaron
 * en "PENDIENTE" y los actualiza si ya fueron aceptados/rechazados.
 *
 * Estrategia de paginación: cursor por ID (id > :lastId ORDER BY id ASC),
 * lotes de máximo 50. No mantiene transacción abierta durante las llamadas HTTP al OSE:
 * cada save() del repositorio corre en su propia transacción.
 *
 * Concurrencia: si hay múltiples instancias activas, dos instancias pueden consultar
 * el mismo comprobante al OSE de forma simultánea. El resultado es idempotente porque
 * el OSE devuelve el mismo estado (ACEPTADO/RECHAZADO) y ambas instancias escribirían
 * el mismo valor; no hay riesgo de corrupción de datos.
 *
 * Intervalo: cada 5 minutos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SunatStatusScheduler {

    static final int BATCH_SIZE = 50;

    private final ComprobanteRepository comprobanteRepository;
    private final TenantRepository      tenantRepository;
    private final ApiSunatService       apiSunatService;

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void actualizarPendientes() {
        String cicloId      = UUID.randomUUID().toString().substring(0, 8);
        long   inicioMs     = System.currentTimeMillis();
        int    totalConsultados = 0;
        int    totalActualizados = 0;
        int    totalErrores  = 0;
        int    numLotes      = 0;

        log.info("[Sunat-{}] Ciclo iniciado", cicloId);

        Long lastId = 0L;

        while (true) {
            List<Comprobante> lote = comprobanteRepository.findPendientesAfterIdOrderById(
                    lastId, PageRequest.of(0, BATCH_SIZE));

            if (lote.isEmpty()) break;
            numLotes++;

            Map<String, Optional<Tenant>> tenantCache = lote.stream()
                    .map(Comprobante::getTenantId)
                    .distinct()
                    .collect(Collectors.toMap(
                            id -> id,
                            id -> tenantRepository.findByTenantId(id)));

            log.debug("[Sunat-{}] Lote #{}: {} comprobante(s), ids {}-{}",
                    cicloId, numLotes, lote.size(),
                    lote.get(0).getId(), lote.get(lote.size() - 1).getId());

            for (Comprobante c : lote) {
                totalConsultados++;

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
                        totalActualizados++;
                        log.info("[Sunat-{}] {} → ACEPTADO", cicloId, c.getNumero());

                    } else if (resp.getPayload() != null &&
                               "RECHAZADO".equalsIgnoreCase(resp.getPayload().getEstado())) {
                        c.setSunatEstado("RECHAZADO");
                        c.setSunatMensaje(resp.resumen());
                        comprobanteRepository.save(c);
                        totalActualizados++;
                        log.warn("[Sunat-{}] {} → RECHAZADO: {}", cicloId, c.getNumero(), resp.resumen());
                    }

                } catch (Exception e) {
                    totalErrores++;
                    log.warn("[Sunat-{}] Error consultando {} ({}): {}",
                            cicloId, c.getNumero(), c.getTenantId(), e.getMessage());
                }
            }

            lastId = lote.get(lote.size() - 1).getId();
            if (lote.size() < BATCH_SIZE) break;
        }

        long elapsed = System.currentTimeMillis() - inicioMs;
        log.info("[Sunat-{}] Ciclo finalizado — consultados={} actualizados={} errores={} lotes={} tiempo={}ms",
                cicloId, totalConsultados, totalActualizados, totalErrores, numLotes, elapsed);
    }
}
