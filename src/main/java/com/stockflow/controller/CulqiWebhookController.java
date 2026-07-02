package com.stockflow.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.CulqiWebhookDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.WebhookLog;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.WebhookLogRepository;
import com.stockflow.service.EmailService;
import com.stockflow.service.SucursalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Maneja los webhooks enviados por Culqi.
 *
 * URL a configurar en Culqi Panel → Desarrollo → Webhooks:
 *   https://tu-dominio/api/webhooks/culqi
 *
 * Eventos soportados:
 *  - subscription.charge.succeeded → renueva fechaProximoCobro, mantiene ACTIVA
 *  - subscription.charge.failed    → marca SUSPENDIDA, notifica por email
 *  - subscription.cancel.succeeded → marca CANCELADA (cancelación desde panel Culqi)
 *  - subscription.trial.end        → marca PENDIENTE para que el usuario contrate
 *
 * Este endpoint está en permitAll() en SecurityConfig — Culqi no envía tokens JWT.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/culqi")
@RequiredArgsConstructor
public class CulqiWebhookController {

    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioRepository     usuarioRepository;
    private final WebhookLogRepository  webhookLogRepository;
    private final EmailService          emailService;
    private final SucursalService       sucursalService;
    private final ObjectMapper          objectMapper;

    // ── Endpoint principal ────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Void> recibirWebhook(@RequestBody CulqiWebhookDTO payload) {

        String eventId   = payload.getId()   != null ? payload.getId()   : "unknown";
        String eventType = payload.getType() != null ? payload.getType() : "unknown";

        log.info("📥 [Culqi Webhook] Evento recibido: type={}, id={}", eventType, eventId);

        // Idempotencia: ignorar eventos ya procesados
        if (webhookLogRepository.existsByWebhookIdAndEstado(eventId, "PROCESADO")) {
            log.info("⚠️ [Culqi Webhook] Evento {} ya procesado — ignorando", eventId);
            return ResponseEntity.ok().build();
        }

        // Parsear data: Culqi lo envía como String JSON serializado
        Map<String, Object> dataMap = parsearData(payload.getData());

        // Registrar como PROCESANDO
        WebhookLog logEntry = WebhookLog.builder()
                .webhookId(eventId)
                .tipo(eventType)
                .estado("PROCESANDO")
                .fechaProcesamiento(LocalDateTime.now())
                .detalles(payload.getData())
                .build();
        webhookLogRepository.save(logEntry);

        try {
            if (esChargeSucedido(eventType)) {
                handleChargeSucedido(dataMap, logEntry);
            } else if (esChargeFallido(eventType)) {
                handleChargeFallido(dataMap, logEntry);
            } else if (esCancelSucedido(eventType)) {
                handleCancelSucedido(dataMap, logEntry);
            } else if (esTrialEnd(eventType)) {
                handleTrialEnd(dataMap, logEntry);
            } else {
                log.info("ℹ️ [Culqi Webhook] Evento '{}' no mapeado — data={}", eventType, payload.getData());
                logEntry.setEstado("IGNORADO");
                webhookLogRepository.save(logEntry);
            }
        } catch (Exception e) {
            log.error("❌ [Culqi Webhook] Error procesando evento {}: {}", eventId, e.getMessage(), e);
            logEntry.setEstado("ERROR");
            logEntry.setDetalles("Error: " + e.getMessage());
            webhookLogRepository.save(logEntry);
        }

        // Culqi espera siempre 200 OK (reintenta si recibe otro código)
        return ResponseEntity.ok().build();
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    /**
     * Cobro mensual exitoso → extender período, asegurar estado ACTIVA.
     *
     * data puede contener:
     *   { "id": "chr_xxx", "subscription_id": "sxn_xxx", ... }
     * o directamente:
     *   { "id": "sxn_xxx", ... }
     */
    private void handleChargeSucedido(Map<String, Object> dataMap, WebhookLog logEntry) {
        Optional<Suscripcion> optSus = buscarSuscripcion(dataMap);
        if (optSus.isEmpty()) {
            log.warn("⚠️ [Culqi] charge.succeeded — no se encontró suscripción para data={}", dataMap.get("email"));
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        Suscripcion s = optSus.get();
        LocalDateTime ahora   = LocalDateTime.now();
        LocalDateTime proxCobro = ahora.plusMonths(1);

        s.setEstado("ACTIVA");
        s.setFechaProximoCobro(proxCobro);
        s.setCurrentPeriodStart(ahora);
        s.setCurrentPeriodEnd(proxCobro);
        suscripcionRepository.save(s);

        log.info("✅ [Culqi] charge.succeeded — suscripción id={} renovada. Próximo cobro: {}",
                s.getId(), proxCobro);

        // Safety net: si es plan PRO e inicializarPrincipal no se ejecutó aún, hacerlo ahora
        if ("PRO".equalsIgnoreCase(s.getPlanId())) {
            try {
                sucursalService.inicializarPrincipal(s.getTenantId());
                log.info("✅ [Culqi] Sucursal principal verificada/inicializada para tenant={}", s.getTenantId());
            } catch (Exception e) {
                log.warn("⚠️ [Culqi] No se pudo inicializar sucursal principal (ya existe o error): {}", e.getMessage());
            }
        }

        // Email opcional de confirmación de renovación
        enviarEmailSuscripcion(s, "ACTIVA");

        logEntry.setEstado("PROCESADO");
        webhookLogRepository.save(logEntry);
    }

    /**
     * Cobro mensual fallido → SUSPENDIDA + email al usuario.
     */
    private void handleChargeFallido(Map<String, Object> dataMap, WebhookLog logEntry) {
        Optional<Suscripcion> optSus = buscarSuscripcion(dataMap);
        if (optSus.isEmpty()) {
            log.warn("⚠️ [Culqi] charge.failed — no se encontró suscripción para data={}", dataMap.get("email"));
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        Suscripcion s = optSus.get();

        // No pisar una cancelación voluntaria con SUSPENDIDA por cobro fallido.
        // Si el usuario ya canceló (CANCELADA o CANCELACION_PENDIENTE), este charge.failed
        // es consecuencia de la cancelación, no un fallo de pago real.
        if ("CANCELADA".equals(s.getEstado()) || "CANCELACION_PENDIENTE".equals(s.getEstado())) {
            log.info("ℹ️ [Culqi] charge.failed ignorado — suscripción id={} ya está {} (cancelación voluntaria)",
                    s.getId(), s.getEstado());
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        s.setEstado("SUSPENDIDA");
        suscripcionRepository.save(s);

        log.warn("⚠️ [Culqi] charge.failed — suscripción id={} marcada SUSPENDIDA", s.getId());

        enviarEmailSuscripcion(s, "SUSPENDIDA");

        logEntry.setEstado("PROCESADO");
        webhookLogRepository.save(logEntry);
    }

    /**
     * Cancelación confirmada desde Culqi (puede ser iniciada desde el panel de Culqi
     * o como resultado de nuestra llamada DELETE). Marca la suscripción local CANCELADA.
     *
     * data: { "id": "sxn_xxx", ... }
     */
    private void handleCancelSucedido(Map<String, Object> dataMap, WebhookLog logEntry) {
        String subscriptionId = extraerSubscriptionId(dataMap);
        if (subscriptionId == null) {
            log.warn("⚠️ [Culqi] cancel.succeeded sin id en data={}", dataMap);
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        Optional<Suscripcion> optSus = suscripcionRepository.findByPreapprovalId(subscriptionId);
        if (optSus.isEmpty()) {
            log.warn("⚠️ [Culqi] cancel.succeeded — suscripción {} no encontrada localmente", subscriptionId);
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        Suscripcion s = optSus.get();

        // Si ya está CANCELADA o CANCELACION_PENDIENTE (cancelación iniciada desde nuestro sistema),
        // ignorar — el estado ya está gestionado localmente con período de gracia.
        if ("CANCELADA".equals(s.getEstado()) || "CANCELACION_PENDIENTE".equals(s.getEstado())) {
            log.info("ℹ️ [Culqi] cancel.succeeded — suscripción {} ya estaba {} localmente — ignorando webhook",
                    subscriptionId, s.getEstado());
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        s.setEstado("CANCELADA");
        s.setFechaCancelacion(LocalDateTime.now());
        suscripcionRepository.save(s);

        log.info("✅ [Culqi] cancel.succeeded — suscripción {} marcada CANCELADA (desde panel Culqi)", subscriptionId);

        enviarEmailSuscripcion(s, "CANCELADA");

        logEntry.setEstado("PROCESADO");
        webhookLogRepository.save(logEntry);
    }

    /**
     * Fin del período de prueba → PENDIENTE, el usuario debe contratar un plan.
     *
     * data: { "id": "sxn_xxx", ... }
     */
    private void handleTrialEnd(Map<String, Object> dataMap, WebhookLog logEntry) {
        String subscriptionId = extraerSubscriptionId(dataMap);
        if (subscriptionId == null) {
            log.warn("⚠️ [Culqi] trial.end sin id en data={}", dataMap);
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        Optional<Suscripcion> optSus = suscripcionRepository.findByPreapprovalId(subscriptionId);
        if (optSus.isEmpty()) {
            log.warn("⚠️ [Culqi] trial.end — suscripción {} no encontrada localmente", subscriptionId);
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        Suscripcion s = optSus.get();
        s.setEstado("PENDIENTE");
        suscripcionRepository.save(s);

        log.info("⏰ [Culqi] trial.end — suscripción {} marcada PENDIENTE", subscriptionId);

        logEntry.setEstado("PROCESADO");
        webhookLogRepository.save(logEntry);
    }

    // ── Clasificadores de eventos ─────────────────────────────────────────────
    // Culqi puede enviar distintos formatos de type dependiendo de la versión:
    //   "charge.succeeded", "charge.creation.succeeded", "subscription.charge.succeeded"
    // Usamos contains() para ser tolerantes a variaciones.

    private boolean esChargeSucedido(String type) {
        return type.contains("charge") && type.contains("succeeded")
                && !type.contains("cancel");
    }

    private boolean esChargeFallido(String type) {
        return type.contains("charge") && type.contains("failed");
    }

    private boolean esCancelSucedido(String type) {
        // "suscription.cancel.succeeded" o "subscription.cancel.succeeded"
        return (type.contains("suscription") || type.contains("subscription"))
                && type.contains("cancel") && type.contains("succeeded");
    }

    private boolean esTrialEnd(String type) {
        return type.contains("trial");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extrae el ID de la suscripción Culqi del mapa {@code data}.
     *
     * Culqi puede enviar el ID de suscripción de dos formas:
     * <ol>
     *   <li>En {@code data.subscription_id} (eventos de cargo: charge.succeeded / failed)</li>
     *   <li>En {@code data.id} directamente (eventos de suscripción: cancel, trial.end)</li>
     * </ol>
     *
     * Solo consideramos IDs que empiecen por "sxn_" para no confundir con IDs de cargo (chr_).
     */
    private String extraerSubscriptionId(Map<String, Object> data) {
        if (data == null) return null;

        // Prioridad 1: campo explícito subscription_id
        Object subId = data.get("subscription_id");
        if (subId instanceof String s && !s.isBlank()) {
            return s;
        }

        // Prioridad 2: campo id si empieza por "sxn_"
        Object id = data.get("id");
        if (id instanceof String s && s.startsWith("sxn_")) {
            return s;
        }

        return null;
    }

    /**
     * Busca la suscripción local a partir del payload de Culqi.
     *
     * Estrategia:
     * 1. Si hay subscription_id (sxn_xxx) en data → busca directo por preapprovalId
     * 2. Si no → usa el email del cargo para encontrar el usuario y su suscripción activa
     *    (Culqi no incluye subscription_id en charge.creation.succeeded)
     */
    private Optional<Suscripcion> buscarSuscripcion(Map<String, Object> dataMap) {
        // Estrategia 1: por subscription_id
        String subscriptionId = extraerSubscriptionId(dataMap);
        if (subscriptionId != null) {
            return suscripcionRepository.findByPreapprovalId(subscriptionId);
        }

        // Estrategia 2: por email del cargo
        Object emailObj = dataMap.get("email");
        if (emailObj instanceof String email && !email.isBlank()) {
            Optional<Usuario> optUsuario = usuarioRepository.findByEmail(email);
            if (optUsuario.isPresent()) {
                Long usuarioId = optUsuario.get().getId();
                // Busca la suscripción más reciente del usuario (activa o no, para poder actualizar estado)
                Optional<Suscripcion> susPorUsuario = suscripcionRepository.findByUsuarioPrincipalId(usuarioId);
                if (susPorUsuario.isPresent()) {
                    log.info("🔍 [Culqi] Suscripción encontrada por email={}: id={}", email, susPorUsuario.get().getId());
                    return susPorUsuario;
                }
                // Si no encontró por usuarioId exacto, busca la más reciente del tenant
                String tenantId = optUsuario.get().getTenantId();
                if (tenantId != null) {
                    return suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId);
                }
            }
            log.warn("⚠️ [Culqi] No se encontró usuario con email={}", email);
        }

        return Optional.empty();
    }

    /**
     * Culqi envía el campo "data" como un String JSON serializado.
     * Este método lo parsea a Map. Si falla, devuelve un mapa vacío para no romper el flujo.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsearData(String dataStr) {
        if (dataStr == null || dataStr.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(dataStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("⚠️ [Culqi Webhook] No se pudo parsear data como JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void enviarEmailSuscripcion(Suscripcion s, String estado) {
        if (s.getUsuarioPrincipal() == null) return;
        try {
            emailService.enviarEmailSuscripcion(
                    s.getUsuarioPrincipal().getEmail(),
                    s.getUsuarioPrincipal().getNombre(),
                    estado,
                    s.getPlanId()
            );
        } catch (Exception e) {
            log.warn("⚠️ No se pudo enviar email de suscripción ({}): {}", estado, e.getMessage());
        }
    }
}
