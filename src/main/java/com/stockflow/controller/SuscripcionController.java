package com.stockflow.controller;

import com.stockflow.dto.SuscripcionDTO;
import com.stockflow.dto.SuscripcionCheckoutRequestDTO;
import com.stockflow.dto.SuscripcionCheckoutResponseDTO;
import com.stockflow.dto.SuscripcionEstadoResponseDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.mapper.SuscripcionMapper;
import com.stockflow.scheduler.NotificacionScheduler;
import com.stockflow.service.SuscripcionCheckoutService;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
import com.stockflow.util.TenantContext;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/suscripciones")
@RequiredArgsConstructor
public class SuscripcionController {

    private final SuscripcionService suscripcionService;
    private final UsuarioService usuarioService;
    private final SuscripcionMapper suscripcionMapper;
    private final SuscripcionCheckoutService suscripcionCheckoutService;
    private final NotificacionScheduler notificacionScheduler;

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    /**
     * ✅ ACTUALIZADO: Obtiene suscripciones del tenant actual
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_SUSCRIPCIONES')")
    public ResponseEntity<List<SuscripcionDTO>> obtenerTodas() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("💳 Obteniendo suscripciones para tenant: {}", tenantId);

        return ResponseEntity.ok(
                suscripcionMapper.toDTOList(suscripcionService.obtenerSuscripcionesPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_SUSCRIPCIONES')")
    public ResponseEntity<SuscripcionDTO> obtenerPorId(@PathVariable Long id) {
        return suscripcionService.obtenerSuscripcionPorId(id)
                .map(suscripcionMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_SUSCRIPCIONES')")
    public ResponseEntity<SuscripcionDTO> obtenerPorUsuario(@PathVariable Long usuarioId) {
        log.info("👤 Obteniendo suscripción del usuario: {}", usuarioId);
        return suscripcionService.obtenerSuscripcionPorUsuario(usuarioId)
                .map(suscripcionMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/estado/{estado}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_SUSCRIPCIONES')")
    public ResponseEntity<List<SuscripcionDTO>> obtenerPorEstado(@PathVariable String estado) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🔍 Obteniendo suscripciones con estado: {} para tenant: {}", estado, tenantId);

        return ResponseEntity.ok(
                suscripcionMapper.toDTOList(suscripcionService.obtenerSuscripcionesPorEstadoYTenant(estado, tenantId))
        );
    }

    /**
     * ✅ ACTUALIZADO: Setea tenantId automáticamente
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_SUSCRIPCION')")
    public ResponseEntity<SuscripcionDTO> crear(@Valid @RequestBody SuscripcionDTO suscripcionDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("➕ Creando suscripción para tenant: {}", tenantId);

        // Validar usuario principal
        Usuario usuario = usuarioService.obtenerUsuarioPorId(suscripcionDTO.getUsuarioPrincipalId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Validar que no exista otra suscripción activa
        suscripcionService.obtenerSuscripcionPorUsuario(suscripcionDTO.getUsuarioPrincipalId())
                .ifPresent(suscripcion -> {
                    if ("ACTIVA".equals(suscripcion.getEstado())) {
                        throw new BadRequestException("El usuario ya tiene una suscripción activa");
                    }
                });

        // Validar plan
        if (!"BASICO".equals(suscripcionDTO.getPlanId())) {
            throw new BadRequestException("Plan no válido. Use: BASICO");
        }

        // Crear suscripción usando mapper
        Suscripcion suscripcion = suscripcionMapper.toEntity(suscripcionDTO);
        suscripcion.setUsuarioPrincipal(usuario);
        suscripcion.setEstado("ACTIVA");
        suscripcion.setTenantId(tenantId);

        Suscripcion suscripcionCreada = suscripcionService.crearSuscripcion(suscripcion);

        log.info("✅ Suscripción creada: Plan {} para tenant {}", suscripcionCreada.getPlanId(), tenantId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suscripcionMapper.toDTO(suscripcionCreada));
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_SUSCRIPCION')")
    public ResponseEntity<SuscripcionDTO> cancelar(@PathVariable Long id) {
        log.info("🗑️ Cancelando suscripción ID: {}", id);
        suscripcionCheckoutService.cancelarSuscripcion(id);
        return suscripcionService.obtenerSuscripcionPorId(id)
                .map(suscripcionMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));
    }

    @PatchMapping("/cancelar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_SUSCRIPCION')")
    public ResponseEntity<Void> cancelarMiSuscripcion() {
        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("🗑️ Cancelando suscripción para tenant={}, usuario={}", tenantId, usuarioId);
        suscripcionCheckoutService.cancelarMiSuscripcion(tenantId, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_SUSCRIPCION')")
    public ResponseEntity<SuscripcionDTO> activar(@PathVariable Long id) {
        log.info("🗑️ Activando suscripción ID: {}", id);
        Suscripcion suscripcionActivada = suscripcionService.activarSuscripcion(id);
        return ResponseEntity.ok(suscripcionMapper.toDTO(suscripcionActivada));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_SUSCRIPCION')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("🗑️ Eliminando suscripción ID: {}", id);
        suscripcionService.eliminarSuscripcion(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/checkout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuscripcionCheckoutResponseDTO> iniciarCheckout(
            @Valid @RequestBody SuscripcionCheckoutRequestDTO request) {

        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("💳 Iniciando checkout Mercado Pago para tenant {}, usuario {}", tenantId, usuarioId);

        return ResponseEntity.ok(
                suscripcionCheckoutService.iniciarCheckout(
                        request.getPlanId(), tenantId, usuarioId,
                        request.getTipoDocumento(), request.getNumeroDocumento())
        );
    }

    @GetMapping("/estado")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuscripcionEstadoResponseDTO> obtenerEstado() {
        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("📊 Consultando estado de suscripción para tenant {}, usuario {}", tenantId, usuarioId);
        return ResponseEntity.ok(suscripcionCheckoutService.obtenerEstadoSuscripcion(tenantId, usuarioId));
    }

    @PostMapping("/sincronizar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuscripcionEstadoResponseDTO> sincronizar() {
        String tenantId = TenantContext.getCurrentTenant();
        Long usuarioId = TenantContext.getCurrentUserId();
        log.info("🔄 Sincronizando estado desde Mercado Pago para tenant {}, usuario {}", tenantId, usuarioId);
        return ResponseEntity.ok(suscripcionCheckoutService.sincronizarDesdeMP(tenantId, usuarioId));
    }

    // ══════════════════════════════════════════════════════════════════════
    // ENDPOINTS SOLO PARA DESARROLLO — no exponer en producción
    // ══════════════════════════════════════════════════════════════════════

    /**
     * POST /suscripciones/dev/forzar-scheduler-cobros
     *
     * Ejecuta manualmente el scheduler de verificación de cobros MP.
     * Úsalo así para probar el flujo de cobro automático en dev:
     *
     *   1. UPDATE suscripciones SET current_period_end = NOW() - INTERVAL '3 days',
     *                                fecha_proximo_cobro = NOW() - INTERVAL '3 days'
     *      WHERE id = <tu_id>;
     *   2. POST /suscripciones/dev/forzar-scheduler-cobros
     *   3. Verifica en los logs y en BD el resultado.
     */
    @PostMapping("/dev/forzar-scheduler-cobros")
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.context.annotation.Profile("dev")  // No registrado en prod/uat
    public ResponseEntity<String> forzarSchedulerCobros() {
        if (!"dev".equals(activeProfile)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Este endpoint solo está disponible en perfil dev");
        }
        log.warn("🧪 [DEV] Forzando ejecución manual del scheduler de cobros MP...");
        notificacionScheduler.verificarCobrosMensualesMP();
        return ResponseEntity.ok("Scheduler ejecutado. Revisa los logs del backend.");
    }

    /**
     * POST /suscripciones/dev/simular-webhook-pago
     *
     * Simula el webhook subscription_authorized_payment que MP envía
     * cuando cobra mensualmente. Requiere el ID del authorized_payment real de MP
     * (lo puedes buscar en el panel de MP sandbox o via API).
     *
     * Body: { "authorizedPaymentId": "123456789" }
     */
    @PostMapping("/dev/simular-webhook-pago")
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.context.annotation.Profile("dev")  // No registrado en prod/uat
    public ResponseEntity<String> simularWebhookPago(@RequestBody java.util.Map<String, String> body) {
        if (!"dev".equals(activeProfile)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Este endpoint solo está disponible en perfil dev");
        }
        String authorizedPaymentId = body.get("authorizedPaymentId");
        if (authorizedPaymentId == null || authorizedPaymentId.isBlank()) {
            return ResponseEntity.badRequest().body("Falta 'authorizedPaymentId' en el body");
        }
        log.warn("🧪 [DEV] Simulando webhook authorized_payment id={}", authorizedPaymentId);

        com.stockflow.dto.MercadoPagoWebhookRequestDTO webhook = new com.stockflow.dto.MercadoPagoWebhookRequestDTO();
        webhook.setType("subscription_authorized_payment");
        com.stockflow.dto.MercadoPagoWebhookRequestDTO.DataPayload data =
                new com.stockflow.dto.MercadoPagoWebhookRequestDTO.DataPayload();
        data.setId(authorizedPaymentId);
        webhook.setData(data);

        suscripcionCheckoutService.procesarWebhook(webhook);
        return ResponseEntity.ok("Webhook procesado. Revisa los logs y la BD.");
    }
}
