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
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.PlanLimitService;
import com.stockflow.service.SucursalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
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
    private final CulqiService          culqiService;
    private final PlanLimitService      planLimitService;
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
        try {
            webhookLogRepository.save(logEntry);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(webhook_id, tipo) — dos requests concurrentes con el mismo eventId
            log.info("ℹ️ [Culqi Webhook] Evento {} ya en proceso (race condition) — ignorando duplicado", eventId);
            return ResponseEntity.ok().build();
        }

        try {
            if (esChargeSucedido(eventType)) {
                handleChargeSucedido(dataMap, logEntry, eventType);
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
     * Cobro exitoso → extender período, mantener ACTIVA, notificar al usuario.
     *
     * - charge.creation.succeeded: confirma que el primer cobro fue procesado.
     *   suscribir() ya envió "suscripción registrada, cobro en proceso"; este webhook
     *   cierra el ciclo con "Tu pago fue procesado correctamente".
     * - subscription.charge.succeeded: confirma renovación mensual exitosa.
     *
     * data puede contener:
     *   { "id": "chr_xxx", "subscription_id": "sxn_xxx", ... }
     * o directamente:
     *   { "id": "sxn_xxx", ... }
     */
    private void handleChargeSucedido(Map<String, Object> dataMap, WebhookLog logEntry, String eventType) {
        Optional<Suscripcion> optSus = buscarSuscripcion(dataMap);
        if (optSus.isEmpty()) {
            log.warn("⚠️ [Culqi] charge.succeeded — suscripción no correlacionada (id={}, customer={}). " +
                    "Marcando SIN_CORRELACION para reintento cuando Culqi reenvíe el evento.",
                    dataMap.get("id"), dataMap.get("customer_id"));
            logEntry.setEstado("SIN_CORRELACION");
            webhookLogRepository.save(logEntry);
            return;
        }

        Suscripcion s = optSus.get();

        // No reactivar una suscripción cuya cancelación ya está confirmada o en curso.
        // Un charge.succeeded tardío (del último ciclo antes de la cancelación) no debe
        // borrar el estado de cancelación.
        if ("CANCELADA".equals(s.getEstado()) || "CANCELACION_PENDIENTE".equals(s.getEstado())) {
            log.info("ℹ️ [Culqi] charge.succeeded ignorado — suscripción id={} ya está {} (no se reactiva)",
                    s.getId(), s.getEstado());
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        // Verificación vía API: si hay chargeId, confirmar con Culqi que el cargo existe
        // y que su subscription_id coincide con la suscripción local.
        String chargeId = extraerChargeId(dataMap);
        if (chargeId != null) {
            Map<String, Object> cargo = culqiService.obtenerCargo(chargeId);
            if (cargo == null) {
                log.warn("⚠️ [Culqi] charge.succeeded — cargo {} no existe en Culqi, evento no procesable", chargeId);
                logEntry.setEstado("PENDIENTE_REVISION");
                webhookLogRepository.save(logEntry);
                return;
            }
            String cargoSubId = cargo.get("subscription_id") instanceof String ss && !ss.isBlank() ? ss : null;
            if (cargoSubId != null && !cargoSubId.equals(s.getPreapprovalId())) {
                log.warn("⚠️ [Culqi] charge.succeeded — subscription_id del cargo ({}) != preapprovalId local ({})",
                        cargoSubId, s.getPreapprovalId());
                logEntry.setEstado("PENDIENTE_REVISION");
                webhookLogRepository.save(logEntry);
                return;
            }
            log.info("✅ [Culqi] Cargo {} verificado en Culqi", chargeId);
        }

        // B — CAS: si el evento trae subscription_id, verificar atómicamente que preapprovalId
        // no fue reemplazado por un upgrade/downgrade concurrente entre la lectura y el save.
        String eventSubIdSucedido = extraerSubscriptionId(dataMap);
        if (eventSubIdSucedido != null) {
            int updated = suscripcionRepository.updateEstadoIfPreapprovalIdMatches(
                    s.getId(), "ACTIVA", eventSubIdSucedido);
            if (updated == 0) {
                log.info("ℹ️ [Culqi] charge.succeeded — preapprovalId ya fue reemplazado " +
                        "(upgrade/downgrade concurrente). Webhook IGNORADO para id={}.", s.getId());
                logEntry.setEstado("IGNORADO");
                webhookLogRepository.save(logEntry);
                return;
            }
        }

        LocalDateTime ahora     = LocalDateTime.now();
        LocalDateTime proxCobro = ahora.plusMonths(1);

        s.setEstado("ACTIVA");
        s.setFechaProximoCobro(proxCobro);
        s.setCurrentPeriodStart(ahora);
        s.setCurrentPeriodEnd(proxCobro);

        // Actualizar datos de tarjeta si vienen en el payload del cargo
        actualizarDatosTarjeta(s, dataMap);

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

        // A — Email ANTES del desbloqueo: si el desbloqueo falla, el email ya fue enviado
        // y el reconciliador puede reintentar solo la branch op sin re-enviar email.
        enviarEmailSuscripcion(s, "ACTIVA");
        boolean esPrimerCobro = eventType != null && eventType.contains("creation");
        if (esPrimerCobro) {
            log.info("✅ [Culqi] charge.creation.succeeded — confirmación de primer cobro enviada al usuario");
        }

        // A — Recuperar sucursales bloqueadas por impago. Si falla → PROCESADO_PARCIAL
        // para que el reconciliador reintente solo el desbloqueo sin re-enviar email.
        try {
            int maxSucursales = planLimitService.getMaxSucursales(s.getTenantId());
            int desbloqueadas = sucursalService.desbloquearSucursalesConLimite(s.getTenantId(), maxSucursales);
            if (desbloqueadas > 0) {
                log.info("🔓 [Culqi] {} sucursal(es) reactivada(s) tras pago exitoso para tenant={}",
                        desbloqueadas, s.getTenantId());
            }
        } catch (Exception e) {
            log.error("❌ [Culqi] Error al desbloquear sucursales tras pago exitoso para tenant={}: {}",
                    s.getTenantId(), e.getMessage(), e);
            logEntry.setEstado("PROCESADO_PARCIAL");
            webhookLogRepository.save(logEntry);
            return;
        }

        logEntry.setEstado("PROCESADO");
        webhookLogRepository.save(logEntry);
    }

    /**
     * Cobro mensual fallido → SUSPENDIDA + email al usuario.
     */
    private void handleChargeFallido(Map<String, Object> dataMap, WebhookLog logEntry) {
        Optional<Suscripcion> optSus = buscarSuscripcion(dataMap);
        if (optSus.isEmpty()) {
            log.warn("⚠️ [Culqi] charge.failed — suscripción no correlacionada (id={}, customer={}). " +
                    "Marcando SIN_CORRELACION para reintento cuando Culqi reenvíe el evento.",
                    dataMap.get("id"), dataMap.get("customer_id"));
            logEntry.setEstado("SIN_CORRELACION");
            webhookLogRepository.save(logEntry);
            return;
        }

        Suscripcion s = optSus.get();

        // No pisar una cancelación voluntaria con SUSPENDIDA por cobro fallido.
        if ("CANCELADA".equals(s.getEstado()) || "CANCELACION_PENDIENTE".equals(s.getEstado())) {
            log.info("ℹ️ [Culqi] charge.failed ignorado — suscripción id={} ya está {} (cancelación voluntaria)",
                    s.getId(), s.getEstado());
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        // Idempotencia: si ya fue suspendida por este u otro evento previo, no re-emitir email.
        if ("SUSPENDIDA".equals(s.getEstado())) {
            log.info("ℹ️ [Culqi] charge.failed idempotente — suscripción id={} ya está SUSPENDIDA", s.getId());
            logEntry.setEstado("PROCESADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        // E.1 — Guard contra eventos tardíos en reconciliación.
        // logEntry.getIntentos() > 0 indica que viene de reconciliarEventosPendientes().
        // Si la suscripción ya está ACTIVA (charge.succeeded llegó antes que este reintento),
        // no se puede determinar de forma fiable si este charge.failed es del ciclo anterior.
        // Se marca PENDIENTE_REVISION para revisión manual en lugar de pisar el estado.
        if ("ACTIVA".equals(s.getEstado()) && logEntry.getIntentos() > 0) {
            log.warn("⚠️ [Culqi] charge.failed reconciliado (intento {}) — suscripción id={} ya está ACTIVA. " +
                    "Posible evento tardío de ciclo anterior. Marcando PENDIENTE_REVISION.",
                    logEntry.getIntentos(), s.getId());
            logEntry.setEstado("PENDIENTE_REVISION");
            webhookLogRepository.save(logEntry);
            return;
        }

        // Verificación vía API: si hay chargeId, confirmar que el cargo existe en Culqi.
        String chargeId = extraerChargeId(dataMap);
        if (chargeId != null) {
            Map<String, Object> cargo = culqiService.obtenerCargo(chargeId);
            if (cargo == null) {
                log.warn("⚠️ [Culqi] charge.failed — cargo {} no existe en Culqi, evento no procesable", chargeId);
                logEntry.setEstado("PENDIENTE_REVISION");
                webhookLogRepository.save(logEntry);
                return;
            }
            String cargoSubId = cargo.get("subscription_id") instanceof String ss && !ss.isBlank() ? ss : null;
            if (cargoSubId != null && !cargoSubId.equals(s.getPreapprovalId())) {
                log.warn("⚠️ [Culqi] charge.failed — subscription_id del cargo ({}) != preapprovalId local ({})",
                        cargoSubId, s.getPreapprovalId());
                logEntry.setEstado("PENDIENTE_REVISION");
                webhookLogRepository.save(logEntry);
                return;
            }
            log.info("✅ [Culqi] Cargo {} verificado en Culqi", chargeId);
        }

        // B — CAS: si el evento trae subscription_id, verificar atómicamente que preapprovalId
        // no fue reemplazado por un upgrade/downgrade concurrente entre la lectura y el save.
        String eventSubIdFallido = extraerSubscriptionId(dataMap);

        // R-B1 — Ruta legacy solo por email: correlación débil sin identificadores verificables.
        // Si no hay subscription_id (CAS no aplica), ni chargeId (sin verificación Culqi), ni
        // customer_id (sin garantía de unidad por buscarPorCustomerIdSeguro), el evento fue
        // correlacionado únicamente por email. Un evento tardío de un ciclo anterior podría
        // suspender erróneamente la suscripción activa. Se marca PENDIENTE_REVISION.
        // Cuando hay customer_id, buscarPorCustomerIdSeguro ya garantiza correlación unívoca
        // (retorna vacío si hay más de 1 suscripción para ese customer), por lo que ese caso
        // puede continuar sin esta protección adicional.
        boolean tieneCustomerId = dataMap.get("customer_id") instanceof String cid && !cid.isBlank();
        if (chargeId == null && eventSubIdFallido == null && !tieneCustomerId) {
            log.warn("⚠️ [Culqi] charge.failed — evento correlacionado solo por email sin chargeId " +
                    "ni subscription_id verificable (email={}). Correlación unívoca no posible → PENDIENTE_REVISION.",
                    dataMap.get("email"));
            logEntry.setEstado("PENDIENTE_REVISION");
            webhookLogRepository.save(logEntry);
            return;
        }

        if (eventSubIdFallido != null) {
            int updated = suscripcionRepository.updateEstadoIfPreapprovalIdMatches(
                    s.getId(), "SUSPENDIDA", eventSubIdFallido);
            if (updated == 0) {
                log.info("ℹ️ [Culqi] charge.failed — preapprovalId ya fue reemplazado " +
                        "(upgrade/downgrade concurrente). Webhook IGNORADO para id={}.", s.getId());
                logEntry.setEstado("IGNORADO");
                webhookLogRepository.save(logEntry);
                return;
            }
        }

        s.setEstado("SUSPENDIDA");

        log.warn("⚠️ [Culqi] charge.failed — suscripción id={} marcada SUSPENDIDA", s.getId());

        // A — Email ANTES del bloqueo: si el bloqueo falla, el email ya fue enviado
        // y el reconciliador puede reintentar solo la branch op sin re-enviar email.
        enviarEmailSuscripcion(s, "SUSPENDIDA");

        // A — Bloquear sucursales adicionales solo en plan PRO. Si falla → PROCESADO_PARCIAL.
        if ("PRO".equalsIgnoreCase(s.getPlanId())) {
            try {
                int bloqueadas = sucursalService.bloquearSucursalesAdicionales(s.getTenantId());
                log.info("🔒 [Culqi] {} sucursal(es) adicional(es) bloqueada(s) por impago para tenant={}",
                        bloqueadas, s.getTenantId());
            } catch (Exception e) {
                log.error("❌ [Culqi] Error al bloquear sucursales por impago para tenant={}: {}",
                        s.getTenantId(), e.getMessage(), e);
                suscripcionRepository.save(s);
                logEntry.setEstado("PROCESADO_PARCIAL");
                webhookLogRepository.save(logEntry);
                return;
            }
        }

        suscripcionRepository.save(s);
        logEntry.setEstado("PROCESADO");
        webhookLogRepository.save(logEntry);
    }

    /**
     * Cancelación confirmada desde Culqi (puede ser iniciada desde el panel de Culqi
     * o como resultado de nuestra llamada DELETE). Respeta el período pagado:
     * si currentPeriodEnd es futuro → CANCELACION_PENDIENTE (acceso conservado);
     * si no hay período activo → CANCELADA inmediata.
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

        // Ya definitivamente cancelada — no hay acción pendiente
        if ("CANCELADA".equals(s.getEstado())) {
            log.info("ℹ️ [Culqi] cancel.succeeded — suscripción {} ya CANCELADA — ignorando webhook", subscriptionId);
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        // Cancelación ya gestionada localmente con período de gracia activo.
        // El webhook confirma la cancelación remota sin adelantar el acceso.
        if ("CANCELACION_PENDIENTE".equals(s.getEstado())) {
            log.info("ℹ️ [Culqi] cancel.succeeded — suscripción {} en CANCELACION_PENDIENTE — " +
                    "cancelación remota confirmada, acceso vigente hasta {}", subscriptionId, s.getCurrentPeriodEnd());
            logEntry.setEstado("PROCESADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        // Verificación vía API: antes de cancelar una suscripción ACTIVA, confirmar el estado
        // remoto. null = 404 (suscripción eliminada en Culqi → proceder). Excepción = API no
        // disponible → PENDIENTE_REVISION (no confirmar cancelación sin verificación).
        Map<String, Object> susRemota;
        try {
            susRemota = culqiService.obtenerSuscripcion(subscriptionId);
        } catch (Exception e) {
            log.warn("⚠️ [Culqi] cancel.succeeded — no se pudo verificar suscripción {} con Culqi ({}), " +
                    "marcando PENDIENTE_REVISION", subscriptionId, e.getMessage());
            logEntry.setEstado("PENDIENTE_REVISION");
            webhookLogRepository.save(logEntry);
            return;
        }

        if (susRemota != null) {
            Object statusObj = susRemota.get("status");
            if ("active".equals(statusObj) || "trialing".equals(statusObj)) {
                log.warn("⚠️ [Culqi] cancel.succeeded — Culqi reporta {} como '{}', posible evento inválido",
                        subscriptionId, statusObj);
                logEntry.setEstado("PENDIENTE_REVISION");
                webhookLogRepository.save(logEntry);
                return;
            }
            log.info("✅ [Culqi] Suscripción {} verificada como no-activa en Culqi (status={})",
                    subscriptionId, statusObj);
        } else {
            log.info("ℹ️ [Culqi] Suscripción {} no encontrada en Culqi (404, ya eliminada), " +
                    "procediendo con cancelación local", subscriptionId);
        }

        // Guard anti-race: confirmar que preapprovalId no fue reemplazado por un
        // downgrade/upgrade concurrente mientras se verificaba con Culqi.
        // Si findByPreapprovalId devuelve vacío o apunta a otra fila, el webhook es
        // tardío (corresponde a la suscripción anterior) y debe ignorarse.
        Optional<Suscripcion> optGuard = suscripcionRepository.findByPreapprovalId(subscriptionId);
        if (optGuard.isEmpty() || !s.getId().equals(optGuard.get().getId())) {
            log.info("ℹ️ [Culqi] cancel.succeeded — preapprovalId {} ya fue reemplazado o reasignado " +
                    "(downgrade/upgrade concurrente). Webhook obsoleto IGNORADO para id={}.",
                    subscriptionId, s.getId());
            logEntry.setEstado("IGNORADO");
            webhookLogRepository.save(logEntry);
            return;
        }

        // Cancelación iniciada desde Culqi sobre suscripción ACTIVA:
        // respetar el período pagado (espejo de la lógica de cancelarMiSuscripcion)
        s.setFechaCancelacion(LocalDateTime.now());
        if (s.getCurrentPeriodEnd() != null && s.getCurrentPeriodEnd().isAfter(LocalDateTime.now())) {
            s.setEstado("CANCELACION_PENDIENTE");
            log.info("✅ [Culqi] cancel.succeeded — suscripción {} → CANCELACION_PENDIENTE (acceso hasta {})",
                    subscriptionId, s.getCurrentPeriodEnd());
        } else {
            s.setEstado("CANCELADA");
            log.info("✅ [Culqi] cancel.succeeded — suscripción {} → CANCELADA (período expirado o sin período)",
                    subscriptionId);
            enviarEmailSuscripcion(s, "CANCELADA");
        }

        suscripcionRepository.save(s);
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

    /** Extrae el chargeId del payload: campo "id" con prefijo "chr_", o campo "chargeId". */
    private String extraerChargeId(Map<String, Object> dataMap) {
        if (dataMap.get("id") instanceof String s && s.startsWith("chr_")) return s;
        if (dataMap.get("chargeId") instanceof String s && !s.isBlank()) return s;
        return null;
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
     * Culqi puede enviar el ID de suscripción en tres formatos distintos:
     * <ol>
     *   <li>{@code data.subscription_id} — eventos de cargo (charge.succeeded / failed)</li>
     *   <li>{@code data.id} empezando por "sxn_" — formato legacy de cancelación/trial</li>
     *   <li>{@code data.message.object.subsId} — formato real de subscription.cancel.succeeded</li>
     * </ol>
     *
     * Solo consideramos IDs que empiecen por "sxn_" para no confundir con IDs de cargo (chr_).
     */
    @SuppressWarnings("unchecked")
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

        // Prioridad 3: message.object.subsId (formato real de subscription.cancel.succeeded)
        // Estructura: { "message": { "object": { "subsId": "sxn_xxx", "planId": "pln_xxx" } }, "action": "..." }
        Object messageObj = data.get("message");
        if (messageObj instanceof Map<?, ?> msgRaw) {
            Map<String, Object> msg = (Map<String, Object>) msgRaw;
            Object objectObj = msg.get("object");
            if (objectObj instanceof Map<?, ?> objRaw) {
                Map<String, Object> obj = (Map<String, Object>) objRaw;
                Object subsId = obj.get("subsId");
                if (subsId instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        }

        return null;
    }

    /**
     * Busca la suscripción local a partir del payload de Culqi.
     *
     * Estrategia:
     * 1. Si hay subscription_id (sxn_xxx) en data → busca directo por preapprovalId
     * 2. Si hay customer_id (cus_xxx) → busca por culqiCustomerId (correlación sin email)
     * 3. Si no → usa el email del cargo para encontrar el usuario y su suscripción activa
     *    (Culqi no incluye subscription_id en charge.creation.succeeded)
     */
    private Optional<Suscripcion> buscarSuscripcion(Map<String, Object> dataMap) {
        // Estrategia 1: por subscription_id en el payload directo
        String subscriptionId = extraerSubscriptionId(dataMap);
        if (subscriptionId != null) {
            return suscripcionRepository.findByPreapprovalId(subscriptionId);
        }

        // Estrategia 2: por customer_id (cus_xxx) — solo si identifica inequívocamente UNA suscripción.
        // Si hay varias suscripciones para el mismo customer_id (ej. cancelada + nueva), no se puede
        // saber a cuál pertenece el evento sin subscription_id → se cae a la siguiente estrategia.
        Object customerIdObj = dataMap.get("customer_id");
        if (customerIdObj instanceof String customerId && !customerId.isBlank()) {
            Optional<Suscripcion> porCustomer = buscarPorCustomerIdSeguro(customerId);
            if (porCustomer.isPresent()) return porCustomer;
        }

        // Estrategia 3: por email directo en el payload
        Optional<Suscripcion> porEmail = buscarPorEmail(dataMap.get("email"));
        if (porEmail.isPresent()) return porEmail;

        // Estrategia 4: charge.creation.failed envía un objeto de error con "chargeId".
        // Consultamos Culqi para obtener el cargo completo y extraer subscription_id o email.
        Object chargeIdObj = dataMap.get("chargeId");
        if (chargeIdObj instanceof String chargeId && !chargeId.isBlank()) {
            log.info("🔍 [Culqi] Consultando cargo {} en Culqi para obtener suscripción...", chargeId);
            Map<String, Object> cargo = culqiService.obtenerCargo(chargeId);
            if (cargo != null) {
                // El cargo puede tener subscription_id directamente
                String subId = extraerSubscriptionId(cargo);
                if (subId != null) {
                    log.info("🔍 [Culqi] subscription_id={} obtenido desde cargo {}", subId, chargeId);
                    return suscripcionRepository.findByPreapprovalId(subId);
                }
                // O el email del cliente asociado al cargo
                Optional<Suscripcion> porEmailCargo = buscarPorEmail(cargo.get("email"));
                if (porEmailCargo.isPresent()) return porEmailCargo;

                // O el customer_id del cargo — solo si correlación unívoca
                Object custIdFromCargo = cargo.get("customer_id");
                if (custIdFromCargo instanceof String cid && !cid.isBlank()) {
                    Optional<Suscripcion> porCustomerEnCargo = buscarPorCustomerIdSeguro(cid);
                    if (porCustomerEnCargo.isPresent()) {
                        log.info("🔍 [Culqi] Suscripción encontrada por culqiCustomerId={} (desde cargo {})", cid, chargeId);
                        return porCustomerEnCargo;
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Busca por customer_id solo cuando la correlación es inequívoca:
     * exactamente UNA suscripción registrada con ese ID de cliente Culqi.
     * Si hay varias (ej. suscripción antigua cancelada + nueva activa), devuelve vacío
     * para que la siguiente estrategia resuelva o el evento quede SIN_CORRELACION.
     * Esto evita que un webhook tardío de la suscripción antigua modifique la nueva.
     */
    private Optional<Suscripcion> buscarPorCustomerIdSeguro(String customerId) {
        List<Suscripcion> todas = suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(customerId);
        if (todas.isEmpty()) return Optional.empty();
        if (todas.size() == 1) {
            log.info("🔍 [Culqi] Suscripción encontrada inequívocamente por culqiCustomerId={}", customerId);
            return Optional.of(todas.get(0));
        }
        log.warn("⚠️ [Culqi] customer_id={} tiene {} suscripciones — correlación ambigua, " +
                "se requiere subscription_id para identificar el evento exacto", customerId, todas.size());
        return Optional.empty();
    }

    private Optional<Suscripcion> buscarPorEmail(Object emailObj) {
        if (!(emailObj instanceof String culqiEmail) || culqiEmail.isBlank()) return Optional.empty();

        // Si el email tiene sufijo tenant-scoped (user+tenantId@domain.com), extraer el tenantId
        // y usar búsqueda directa por tenant — evita ambigüedad multi-tenant.
        String tenantIdExtraido = CulqiController.extraerTenantIdDeEmail(culqiEmail);
        if (tenantIdExtraido != null) {
            Optional<Suscripcion> porTenant = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantIdExtraido);
            if (porTenant.isPresent()) {
                log.info("🔍 [Culqi] Suscripción encontrada por tenantId={} (extraído de email={})",
                        tenantIdExtraido, culqiEmail);
                return porTenant;
            }
            log.warn("⚠️ [Culqi] tenantId={} extraído de email={} no tiene suscripción registrada",
                    tenantIdExtraido, culqiEmail);
        }

        // Fallback: normalizar email (quitar sufijo si lo tiene) y buscar por usuario
        String realEmail = CulqiController.normalizarEmailCulqi(culqiEmail);
        Optional<Usuario> optUsuario = usuarioRepository.findByEmail(realEmail);
        if (optUsuario.isEmpty()) {
            log.warn("⚠️ [Culqi] No se encontró usuario con email={}", realEmail);
            return Optional.empty();
        }
        Long usuarioId = optUsuario.get().getId();
        java.util.List<Suscripcion> suscripciones =
                suscripcionRepository.findAllByUsuarioPrincipalId(usuarioId);
        if (suscripciones.isEmpty()) {
            return Optional.empty();
        }
        if (suscripciones.size() == 1) {
            log.info("🔍 [Culqi] Suscripción encontrada por email={}: id={}", realEmail, suscripciones.get(0).getId());
            return Optional.of(suscripciones.get(0));
        }
        // El usuario pertenece a múltiples tenants y no se pudo resolver por preapprovalId ni por sufijo.
        log.warn("⚠️ [Culqi] Email ambiguo: '{}' tiene {} suscripciones. Se requiere subscription_id para identificar el tenant.",
                realEmail, suscripciones.size());
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

    // ── Reconciliación interna de eventos SIN_CORRELACION ────────────────────

    /** Máximo de reintentos antes de marcar el evento como PENDIENTE_REVISION. */
    static final int MAX_INTENTOS_RECONCILIACION = 3;

    /**
     * Cada 5 minutos busca eventos SIN_CORRELACION recientes con reintentos disponibles
     * y los reintenta con datos enriquecidos desde la API de Culqi.
     *
     * Motivación: el endpoint siempre devuelve HTTP 200, por lo que Culqi no reintenta
     * eventos que quedaron sin correlación. Esta tarea garantiza que ningún cobro
     * legítimo quede sin procesar de forma silenciosa.
     *
     * Idempotencia: los eventos ya en estado PROCESADO no son devueltos por la query,
     * y los handlers internos no modifican suscripciones ya en estado final.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void reconciliarEventosPendientes() {
        LocalDateTime ventana = LocalDateTime.now().minusHours(24);

        // Eventos SIN_CORRELACION: reintento completo con enriquecimiento de datos desde Culqi
        List<WebhookLog> sinCorrelacion = webhookLogRepository
                .findSinCorrelacionPendientes(MAX_INTENTOS_RECONCILIACION, ventana);
        if (!sinCorrelacion.isEmpty()) {
            log.info("🔄 [Reconciliación] {} evento(s) SIN_CORRELACION a reintentar", sinCorrelacion.size());
            for (WebhookLog logEntry : sinCorrelacion) {
                reconciliarEvento(logEntry);
            }
        }

        // A — Eventos PROCESADO_PARCIAL: reintentar solo la branch op (email ya enviado)
        List<WebhookLog> parciales = webhookLogRepository
                .findProcesadoParcialPendientes(MAX_INTENTOS_RECONCILIACION, ventana);
        if (!parciales.isEmpty()) {
            log.info("🔄 [Reconciliación] {} evento(s) PROCESADO_PARCIAL a reintentar branch op",
                    parciales.size());
            for (WebhookLog logEntry : parciales) {
                reconciliarBranchOp(logEntry);
            }
        }
    }

    private void reconciliarEvento(WebhookLog logEntry) {
        logEntry.setIntentos(logEntry.getIntentos() + 1);
        logEntry.setUltimoReintento(LocalDateTime.now());

        if (logEntry.getDetalles() == null || logEntry.getDetalles().isBlank()) {
            log.warn("⚠️ [Reconciliación] Evento {} sin payload almacenado — PENDIENTE_REVISION",
                    logEntry.getWebhookId());
            logEntry.setEstado("PENDIENTE_REVISION");
            webhookLogRepository.save(logEntry);
            return;
        }

        try {
            Map<String, Object> dataMap       = parsearData(logEntry.getDetalles());
            Map<String, Object> dataEnriquecido = enriquecerConCulqi(dataMap);

            Optional<Suscripcion> optSus = buscarSuscripcion(dataEnriquecido);

            if (optSus.isEmpty()) {
                if (logEntry.getIntentos() >= MAX_INTENTOS_RECONCILIACION) {
                    log.warn("⚠️ [Reconciliación] Evento {} agotó {} reintentos sin correlación — " +
                            "marcando PENDIENTE_REVISION para revisión manual",
                            logEntry.getWebhookId(), MAX_INTENTOS_RECONCILIACION);
                    logEntry.setEstado("PENDIENTE_REVISION");
                } else {
                    log.info("ℹ️ [Reconciliación] Evento {} sigue SIN_CORRELACION (intento {}/{})",
                            logEntry.getWebhookId(), logEntry.getIntentos(), MAX_INTENTOS_RECONCILIACION);
                }
                webhookLogRepository.save(logEntry);
                return;
            }

            // Suscripción encontrada — delegar al handler correspondiente.
            // El handler set PROCESADO (o IGNORADO si la suscripción ya está cancelada)
            // y llama webhookLogRepository.save(logEntry) — el campo intentos ya actualizado
            // se persiste en ese mismo save.
            if (esChargeSucedido(logEntry.getTipo())) {
                handleChargeSucedido(dataEnriquecido, logEntry, logEntry.getTipo());
            } else if (esChargeFallido(logEntry.getTipo())) {
                handleChargeFallido(dataEnriquecido, logEntry);
            } else if (esCancelSucedido(logEntry.getTipo())) {
                handleCancelSucedido(dataEnriquecido, logEntry);
            } else {
                log.warn("⚠️ [Reconciliación] Tipo '{}' no manejado — PENDIENTE_REVISION", logEntry.getTipo());
                logEntry.setEstado("PENDIENTE_REVISION");
                webhookLogRepository.save(logEntry);
            }
        } catch (Exception e) {
            log.error("❌ [Reconciliación] Error procesando evento {}: {}",
                    logEntry.getWebhookId(), e.getMessage(), e);
            logEntry.setEstado("ERROR");
            webhookLogRepository.save(logEntry);
        }
    }

    /**
     * Reintenta solo la branch op (bloqueo/desbloqueo de sucursales) para eventos
     * PROCESADO_PARCIAL. El email ya fue enviado en el procesamiento original, por lo
     * que no se re-envía. Si la suscripción ya no está en el estado esperado (resuelta
     * por otro medio), se marca PROCESADO sin reintentar el branch.
     */
    private void reconciliarBranchOp(WebhookLog logEntry) {
        logEntry.setIntentos(logEntry.getIntentos() + 1);
        logEntry.setUltimoReintento(LocalDateTime.now());

        if (logEntry.getIntentos() >= MAX_INTENTOS_RECONCILIACION) {
            log.warn("⚠️ [Reconciliación BranchOp] Evento {} agotó {} reintentos — PENDIENTE_REVISION",
                    logEntry.getWebhookId(), MAX_INTENTOS_RECONCILIACION);
            logEntry.setEstado("PENDIENTE_REVISION");
            webhookLogRepository.save(logEntry);
            return;
        }

        if (logEntry.getDetalles() == null || logEntry.getDetalles().isBlank()) {
            log.warn("⚠️ [Reconciliación BranchOp] Evento {} sin payload — PENDIENTE_REVISION",
                    logEntry.getWebhookId());
            logEntry.setEstado("PENDIENTE_REVISION");
            webhookLogRepository.save(logEntry);
            return;
        }

        try {
            Map<String, Object> dataMap = parsearData(logEntry.getDetalles());
            boolean esFallido = esChargeFallido(logEntry.getTipo());
            boolean esExitoso = esChargeSucedido(logEntry.getTipo());

            Optional<Suscripcion> optSus = buscarSuscripcion(dataMap);
            if (optSus.isEmpty()) {
                log.warn("⚠️ [Reconciliación BranchOp] Suscripción no encontrada para evento {} — PENDIENTE_REVISION",
                        logEntry.getWebhookId());
                logEntry.setEstado("PENDIENTE_REVISION");
                webhookLogRepository.save(logEntry);
                return;
            }

            Suscripcion s = optSus.get();

            if (esFallido) {
                if (!"SUSPENDIDA".equals(s.getEstado())) {
                    log.info("ℹ️ [Reconciliación BranchOp] Suscripción {} ya no está SUSPENDIDA (estado={}). " +
                            "Branch op resuelta por otro medio — PROCESADO.", s.getId(), s.getEstado());
                    logEntry.setEstado("PROCESADO");
                    webhookLogRepository.save(logEntry);
                    return;
                }
                if ("PRO".equalsIgnoreCase(s.getPlanId())) {
                    int bloqueadas = sucursalService.bloquearSucursalesAdicionales(s.getTenantId());
                    log.info("🔒 [Reconciliación BranchOp] {} sucursal(es) bloqueada(s) para tenant={}",
                            bloqueadas, s.getTenantId());
                }
            } else if (esExitoso) {
                if (!"ACTIVA".equals(s.getEstado())) {
                    log.info("ℹ️ [Reconciliación BranchOp] Suscripción {} ya no está ACTIVA (estado={}). " +
                            "Branch op resuelta por otro medio — PROCESADO.", s.getId(), s.getEstado());
                    logEntry.setEstado("PROCESADO");
                    webhookLogRepository.save(logEntry);
                    return;
                }
                int maxSucursales = planLimitService.getMaxSucursales(s.getTenantId());
                int desbloqueadas = sucursalService.desbloquearSucursalesConLimite(s.getTenantId(), maxSucursales);
                log.info("🔓 [Reconciliación BranchOp] {} sucursal(es) desbloqueada(s) para tenant={}",
                        desbloqueadas, s.getTenantId());
            } else {
                log.warn("⚠️ [Reconciliación BranchOp] Tipo '{}' no manejado — PENDIENTE_REVISION",
                        logEntry.getTipo());
                logEntry.setEstado("PENDIENTE_REVISION");
                webhookLogRepository.save(logEntry);
                return;
            }

            logEntry.setEstado("PROCESADO");
            webhookLogRepository.save(logEntry);
        } catch (Exception e) {
            // R-A1 — No marcar ERROR: el evento sigue en el circuito de reintentos.
            // El contador ya fue incrementado al inicio del método y pasó el guard de MAX,
            // así que todavía hay intentos disponibles. PROCESADO_PARCIAL lo mantiene en
            // findProcesadoParcialPendientes para el siguiente ciclo del scheduler.
            // Si se agotan los intentos, el guard al inicio del próximo ciclo lo mueve a
            // PENDIENTE_REVISION, desde donde soporte puede recuperarlo manualmente.
            log.error("❌ [Reconciliación BranchOp] Fallo en branch op para evento {} (intento {}/{}): {}. " +
                    "Se mantiene PROCESADO_PARCIAL para reintento automático.",
                    logEntry.getWebhookId(), logEntry.getIntentos(), MAX_INTENTOS_RECONCILIACION,
                    e.getMessage(), e);
            logEntry.setEstado("PROCESADO_PARCIAL");
            webhookLogRepository.save(logEntry);
        }
    }

    /**
     * Enriquece {@code dataMap} con datos del cargo completo obtenido desde Culqi.
     * Añade subscription_id, customer_id y email si el cargo los contiene.
     * Si no hay chargeId en el mapa o Culqi no responde, devuelve el mapa original sin cambios.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> enriquecerConCulqi(Map<String, Object> dataMap) {
        String chargeId = null;
        if (dataMap.get("id") instanceof String s && s.startsWith("chr_"))   chargeId = s;
        else if (dataMap.get("chargeId") instanceof String s && !s.isBlank()) chargeId = s;
        if (chargeId == null) return dataMap;

        Map<String, Object> cargo;
        try {
            cargo = culqiService.obtenerCargo(chargeId);
        } catch (Exception e) {
            log.warn("⚠️ [Reconciliación] Error consultando cargo {} en Culqi: {}", chargeId, e.getMessage());
            return dataMap;
        }
        if (cargo == null) return dataMap;

        Map<String, Object> enriquecido = new java.util.HashMap<>(dataMap);
        if (cargo.get("subscription_id") instanceof String subId && !subId.isBlank()) {
            enriquecido.put("subscription_id", subId);
            log.info("🔍 [Reconciliación] subscription_id={} obtenido de Culqi para cargo {}", subId, chargeId);
        }
        if (cargo.get("customer_id") instanceof String custId && !custId.isBlank())
            enriquecido.put("customer_id", custId);
        if (cargo.get("email") instanceof String email && !email.isBlank())
            enriquecido.put("email", email);
        return enriquecido;
    }

    /**
     * Extrae last_four y brand del payload de un cargo de Culqi y los persiste en la suscripción.
     * El objeto charge de Culqi incluye los datos de tarjeta en el campo "source".
     * Solo actualiza si los valores vienen presentes en el payload.
     */
    @SuppressWarnings("unchecked")
    private void actualizarDatosTarjeta(Suscripcion s, Map<String, Object> chargeData) {
        try {
            Object sourceObj = chargeData.get("source");
            if (!(sourceObj instanceof Map<?, ?> raw)) return;
            Map<String, Object> source = (Map<String, Object>) raw;

            if (source.get("last_four") instanceof String lf && !lf.isBlank()) {
                s.setUltimos4Digitos(lf);
                log.info("💳 [Culqi Webhook] ultimos4Digitos actualizado: {}", lf);
            }

            // card_brand está en source.iin.card_brand
            String brand = null;
            if (source.get("iin") instanceof Map<?, ?> iinRaw) {
                Map<String, Object> iin = (Map<String, Object>) iinRaw;
                if (iin.get("card_brand") instanceof String b && !b.isBlank()) brand = b;
            }
            if (brand == null && source.get("brand") instanceof String b && !b.isBlank()) brand = b;
            if (brand != null) {
                s.setMetodoPago(brand);
                log.info("💳 [Culqi Webhook] metodoPago actualizado: {}", brand);
            }
        } catch (Exception e) {
            log.warn("⚠️ [Culqi Webhook] No se pudo extraer datos de tarjeta del payload: {}", e.getMessage());
        }
    }
}
