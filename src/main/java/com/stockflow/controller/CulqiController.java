package com.stockflow.controller;

import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.dto.CulqiConfigResponseDTO;
import com.stockflow.dto.CulqiSuscribirRequestDTO;
import com.stockflow.dto.CulqiSuscribirResponseDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.SucursalService;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/culqi")
@RequiredArgsConstructor
public class CulqiController {

    private final CulqiService culqiService;
    private final CulqiProperties culqiProperties;
    private final SuscripcionService suscripcionService;
    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioService usuarioService;
    private final SucursalService sucursalService;
    private final EmailService emailService;

    // ── Config pública ────────────────────────────────────────────────────────

    /**
     * GET /api/culqi/config
     * Devuelve la public key y datos del plan para que el frontend inicialice Culqi.js.
     * Requiere autenticación (el usuario debe estar logueado para suscribirse).
     */
    @GetMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CulqiConfigResponseDTO> obtenerConfig(
            @RequestParam(defaultValue = "BASICO") String plan) {
        boolean isPro = "PRO".equalsIgnoreCase(plan);
        return ResponseEntity.ok(CulqiConfigResponseDTO.builder()
                .publicKey(culqiProperties.getPublicKey())
                .planId(isPro ? culqiProperties.getPlanIdPro() : culqiProperties.getPlanIdBasico())
                .precioMensual(isPro ? culqiProperties.getPrecioPro() : culqiProperties.getPrecioBasico())
                .nombrePlan(isPro ? "Plan Pro" : "Plan Básico")
                .build());
    }

    // ── Suscripción ───────────────────────────────────────────────────────────

    /**
     * POST /api/culqi/suscribir
     * Flujo completo:
     *   1. Recibe token_id generado por Culqi.js en el frontend
     *   2. Crea/reutiliza Customer en Culqi con el email del usuario
     *   3. Registra la tarjeta (Card) en Culqi
     *   4. Crea la Suscripción recurrente en Culqi
     *   5. Activa (o crea) la suscripción local en la tabla suscripciones
     */
    @PostMapping("/suscribir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CulqiSuscribirResponseDTO> suscribir(
            @Valid @RequestBody CulqiSuscribirRequestDTO request) {

        String tenantId  = TenantContext.getCurrentTenant();
        Long   usuarioId = TenantContext.getCurrentUserId();
        log.info("💳 [Culqi] Iniciando suscripción para tenant={}, usuario={}", tenantId, usuarioId);

        // 1. Cargar el usuario para obtener email y nombre
        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + usuarioId));

        String email       = usuario.getEmail();
        String firstName   = usuario.getNombre()     != null ? usuario.getNombre()     : "Cliente";
        String lastName    = usuario.getApellido()   != null ? usuario.getApellido()   : "-";
        String phoneNumber = usuario.getNumeroCelular() != null ? usuario.getNumeroCelular() : null;

        // 2. Verificar si ya existe suscripción activa para este tenant
        Optional<Suscripcion> existente = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId);
        if (existente.isPresent() && "ACTIVA".equals(existente.get().getEstado())) {
            throw new BadRequestException("Ya tienes una suscripción activa. Cancélala antes de contratar una nueva.");
        }

        // 3. Determinar planId de Culqi según el plan solicitado
        boolean isPro = "PRO".equalsIgnoreCase(request.getPlanId());
        String culqiPlanId = isPro ? culqiProperties.getPlanIdPro() : culqiProperties.getPlanIdBasico();
        if (culqiPlanId == null || culqiPlanId.isBlank()) {
            throw new BadRequestException(isPro
                    ? "No hay un plan Pro de Culqi configurado. Ejecuta POST /api/culqi/admin/crear-plan-pro primero y guarda el ID en CULQI_PLAN_ID_PRO."
                    : "No hay un plan Culqi configurado. Ve a Culqi Panel → Suscripciones → Planes y copia el ID en CULQI_PLAN_ID_BASICO.");
        }
        log.info("📋 [Culqi] Usando planId de Culqi: {} ({})", culqiPlanId, isPro ? "PRO" : "BASICO");

        // 4. Culqi: crear cliente → tarjeta → suscripción
        log.info("📡 [Culqi] Creando customer para email={}", email);
        String customerId = culqiService.crearCliente(email, firstName, lastName, phoneNumber);

        log.info("📡 [Culqi] Registrando tarjeta para customerId={}", customerId);
        String cardId = culqiService.crearTarjeta(customerId, request.getTokenId());

        log.info("📡 [Culqi] Creando suscripción con cardId={}, planId={}", cardId, culqiPlanId);
        String culqiSubscriptionId = culqiService.crearSuscripcion(cardId, culqiPlanId);

        // 5. Persistir / actualizar suscripción local
        LocalDateTime ahora        = LocalDateTime.now();
        LocalDateTime proximoCobro = ahora.plusMonths(1);
        String        planIdLocal  = isPro ? "PRO" : "BASICO";
        BigDecimal    precio       = isPro ? culqiProperties.getPrecioPro() : culqiProperties.getPrecioBasico();

        Suscripcion suscripcion;
        if (existente.isPresent()) {
            // Reactivar la existente (ej: trial expirado o cancelada)
            suscripcion = existente.get();
            suscripcion.setEstado("ACTIVA");
            suscripcion.setPlanId(planIdLocal);
            suscripcion.setPreapprovalId(culqiSubscriptionId);   // reutilizamos campo para el sub_id de Culqi
            suscripcion.setFechaInicio(ahora);
            suscripcion.setFechaProximoCobro(proximoCobro);
            suscripcion.setCurrentPeriodStart(ahora);
            suscripcion.setCurrentPeriodEnd(proximoCobro);
            suscripcion.setMetodoPago("CULQI");
            suscripcion.setPrecioMensual(precio);
            suscripcion.setTrialEndDate(null);
            log.info("♻️ [Culqi] Reactivando suscripción existente id={} plan={}", suscripcion.getId(), planIdLocal);
        } else {
            // Crear nueva suscripción local
            suscripcion = Suscripcion.builder()
                    .usuarioPrincipal(usuario)
                    .tenantId(tenantId)
                    .planId(planIdLocal)
                    .precioMensual(precio)
                    .estado("ACTIVA")
                    .preapprovalId(culqiSubscriptionId)   // sub_live_xxx de Culqi
                    .fechaInicio(ahora)
                    .fechaProximoCobro(proximoCobro)
                    .currentPeriodStart(ahora)
                    .currentPeriodEnd(proximoCobro)
                    .metodoPago("CULQI")
                    .build();
            log.info("✨ [Culqi] Creando nueva suscripción local para tenant={}", tenantId);
        }

        Suscripcion guardada = suscripcionRepository.save(suscripcion);
        log.info("✅ [Culqi] Suscripción activada localmente id={}, culqiSubId={}", guardada.getId(), culqiSubscriptionId);

        // Si el plan es PRO, desbloquear sucursales previas e inicializar la principal
        if (isPro) {
            sucursalService.desbloquearSucursalesAdicionales(tenantId);
            sucursalService.inicializarPrincipal(tenantId);
            log.info("🏢 [Culqi] Sucursal principal inicializada para tenant PRO={}", tenantId);
        }

        String mensaje = isPro
                ? "¡Suscripción Pro activada exitosamente! Ya puedes gestionar hasta 5 sucursales."
                : "¡Suscripción activada exitosamente! Tu plan Básico está activo.";

        return ResponseEntity.ok(CulqiSuscribirResponseDTO.builder()
                .suscripcionId(guardada.getId())
                .estado(guardada.getEstado())
                .planId(guardada.getPlanId())
                .precioMensual(guardada.getPrecioMensual())
                .fechaInicio(guardada.getFechaInicio())
                .fechaProximoCobro(guardada.getFechaProximoCobro())
                .culqiSubscriptionId(culqiSubscriptionId)
                .mensaje(mensaje)
                .build());
    }

    // ── Upgrade a PRO ─────────────────────────────────────────────────────────

    /**
     * POST /api/culqi/upgrade-pro
     * Flujo de upgrade Básico → Pro:
     *   1. Cancela la suscripción actual en Culqi
     *   2. Crea nueva suscripción con plan PRO
     *   3. Actualiza plan_id = "PRO" en tabla suscripciones
     *   4. Inicializa la sucursal principal y migra todos los datos existentes
     */
    @PostMapping("/upgrade-pro")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CulqiSuscribirResponseDTO> upgradePro(
            @Valid @RequestBody CulqiSuscribirRequestDTO request) {

        String tenantId  = TenantContext.getCurrentTenant();
        Long   usuarioId = TenantContext.getCurrentUserId();
        log.info("⬆️  [Culqi] Upgrade a PRO para tenant={}", tenantId);

        String culqiPlanIdPro = culqiProperties.getPlanIdPro();
        if (culqiPlanIdPro == null || culqiPlanIdPro.isBlank()) {
            throw new BadRequestException(
                    "El plan Pro no está configurado en el servidor. " +
                    "Ejecuta POST /api/culqi/admin/crear-plan-pro primero y guarda el ID en CULQI_PLAN_ID_PRO.");
        }

        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + usuarioId));

        // Cancelar suscripción Básico vigente en Culqi si existe
        suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId).ifPresent(s -> {
            if (s.getPreapprovalId() != null && "ACTIVA".equals(s.getEstado())) {
                try {
                    culqiService.cancelarSuscripcion(s.getPreapprovalId());
                    log.info("✅ [Culqi] Suscripción Básico cancelada en Culqi: {}", s.getPreapprovalId());
                } catch (Exception e) {
                    log.warn("⚠️ [Culqi] No se pudo cancelar suscripción anterior en Culqi (continuando): {}", e.getMessage());
                }
            }
        });

        // Crear cliente + tarjeta + suscripción PRO en Culqi
        String customerId = culqiService.crearCliente(
                usuario.getEmail(), usuario.getNombre(),
                usuario.getApellido(), usuario.getNumeroCelular());
        String cardId = culqiService.crearTarjeta(customerId, request.getTokenId());
        String culqiSubId = culqiService.crearSuscripcion(cardId, culqiPlanIdPro);

        // Actualizar suscripción local a PRO
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime proxCobro = ahora.plusMonths(1);
        BigDecimal precioPro = culqiProperties.getPrecioPro();

        Suscripcion suscripcion = suscripcionRepository
                .findFirstByTenantIdOrderByIdDesc(tenantId)
                .orElse(Suscripcion.builder().usuarioPrincipal(usuario).tenantId(tenantId).build());

        suscripcion.setPlanId("PRO");
        suscripcion.setEstado("ACTIVA");
        suscripcion.setPreapprovalId(culqiSubId);
        suscripcion.setFechaInicio(ahora);
        suscripcion.setFechaProximoCobro(proxCobro);
        suscripcion.setCurrentPeriodStart(ahora);
        suscripcion.setCurrentPeriodEnd(proxCobro);
        suscripcion.setMetodoPago("CULQI");
        suscripcion.setPrecioMensual(precioPro);
        Suscripcion guardada = suscripcionRepository.save(suscripcion);

        // Desbloquear sucursales que quedaron bloqueadas por downgrade previo, luego inicializar principal
        int desbloqueadas = sucursalService.desbloquearSucursalesAdicionales(tenantId);
        if (desbloqueadas > 0) {
            log.info("🔓 [Culqi] {} sucursal(es) reactivada(s) para tenant={}", desbloqueadas, tenantId);
        }
        sucursalService.inicializarPrincipal(tenantId);

        log.info("✅ [Culqi] Upgrade PRO completado para tenant={}. SubId={}", tenantId, culqiSubId);

        return ResponseEntity.ok(CulqiSuscribirResponseDTO.builder()
                .suscripcionId(guardada.getId())
                .estado(guardada.getEstado())
                .planId(guardada.getPlanId())
                .precioMensual(guardada.getPrecioMensual())
                .fechaInicio(guardada.getFechaInicio())
                .fechaProximoCobro(guardada.getFechaProximoCobro())
                .culqiSubscriptionId(culqiSubId)
                .mensaje("¡Upgrade exitoso! Tu plan Pro está activo. Se creó tu sucursal principal y todos tus datos fueron migrados.")
                .build());
    }

    // ── Cambiar tarjeta ───────────────────────────────────────────────────────

    /**
     * POST /api/culqi/cambiar-tarjeta
     * Permite al usuario registrar una nueva tarjeta en su suscripción activa.
     * Flujo:
     *   1. Recibe el token_id de la nueva tarjeta (generado por Culqi.js)
     *   2. Busca el customer del usuario en Culqi (por email) y registra la nueva tarjeta
     *   3. Hace PATCH /recurrent/subscriptions/{id} para asociar la nueva tarjeta
     *   4. No cancela ni recrea la suscripción — solo actualiza el método de pago
     */
    @PostMapping("/cambiar-tarjeta")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> cambiarTarjeta(@RequestBody Map<String, String> body) {
        String tenantId  = TenantContext.getCurrentTenant();
        Long   usuarioId = TenantContext.getCurrentUserId();
        String tokenId   = body.get("tokenId");

        if (tokenId == null || tokenId.isBlank()) {
            throw new BadRequestException("Se requiere el tokenId de la nueva tarjeta.");
        }

        Suscripcion suscripcion = suscripcionRepository
                .findFirstByTenantIdOrderByIdDesc(tenantId)
                .orElseThrow(() -> new BadRequestException("No tienes una suscripción activa."));

        if (suscripcion.getPreapprovalId() == null) {
            throw new BadRequestException("La suscripción no tiene un ID de Culqi registrado.");
        }

        String[] estadosValidos = {"ACTIVA", "SUSPENDIDA"};
        boolean estadoValido = false;
        for (String e : estadosValidos) if (e.equals(suscripcion.getEstado())) { estadoValido = true; break; }
        if (!estadoValido) {
            throw new BadRequestException("Solo puedes cambiar la tarjeta en suscripciones ACTIVA o SUSPENDIDA.");
        }

        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        log.info("💳 [Culqi] Cambio de tarjeta para tenant={}, subId={}", tenantId, suscripcion.getPreapprovalId());

        String customerId = culqiService.crearCliente(
                usuario.getEmail(), usuario.getNombre(),
                usuario.getApellido(), usuario.getNumeroCelular());

        String cardId = culqiService.crearTarjeta(customerId, tokenId);

        culqiService.actualizarTarjetaSuscripcion(suscripcion.getPreapprovalId(), cardId);

        // Si estaba SUSPENDIDA, la reactivamos localmente (Culqi reintentará el cobro)
        if ("SUSPENDIDA".equals(suscripcion.getEstado())) {
            suscripcion.setEstado("ACTIVA");
            suscripcionRepository.save(suscripcion);
            log.info("✅ [Culqi] Suscripción reactivada tras cambio de tarjeta para tenant={}", tenantId);
        }

        log.info("✅ [Culqi] Tarjeta actualizada correctamente. cardId={}", cardId);
        return ResponseEntity.ok(Map.of("mensaje", "Tarjeta actualizada correctamente. El próximo cobro usará tu nueva tarjeta."));
    }

    // ── Downgrade a BÁSICO ────────────────────────────────────────────────────

    /**
     * POST /api/culqi/downgrade-basico
     * Flujo de downgrade PRO → BÁSICO (también disponible cuando la cuenta está SUSPENDIDA):
     *   1. Cancela la suscripción PRO vigente en Culqi
     *   2. Crea nueva suscripción BÁSICO (cobro inmediato de S/89)
     *   3. Bloquea las sucursales adicionales (no se eliminan, se recuperan al volver a PRO)
     *   4. Actualiza plan_id = "BASICO" en tabla suscripciones
     *   5. Envía email de confirmación de downgrade
     */
    @PostMapping("/downgrade-basico")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CulqiSuscribirResponseDTO> downgradeBasico(
            @Valid @RequestBody CulqiSuscribirRequestDTO request) {

        String tenantId  = TenantContext.getCurrentTenant();
        Long   usuarioId = TenantContext.getCurrentUserId();
        log.info("⬇️  [Culqi] Downgrade a BÁSICO para tenant={}", tenantId);

        String culqiPlanIdBasico = culqiProperties.getPlanIdBasico();
        if (culqiPlanIdBasico == null || culqiPlanIdBasico.isBlank()) {
            throw new BadRequestException("El plan Básico no está configurado en el servidor.");
        }

        // Solo se permite si el plan actual es PRO o la cuenta está SUSPENDIDA
        Suscripcion suscripcionActual = suscripcionRepository
                .findFirstByTenantIdOrderByIdDesc(tenantId)
                .orElseThrow(() -> new BadRequestException("No tienes una suscripción activa."));

        boolean esPro = "PRO".equals(suscripcionActual.getPlanId());
        boolean estaSuspendida = "SUSPENDIDA".equals(suscripcionActual.getEstado());
        if (!esPro && !estaSuspendida) {
            throw new BadRequestException("Solo puedes hacer downgrade si tienes plan Pro o la cuenta está suspendida.");
        }
        if ("BASICO".equals(suscripcionActual.getPlanId()) && !estaSuspendida) {
            throw new BadRequestException("Ya tienes el plan Básico activo.");
        }

        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + usuarioId));

        // 1. Cancelar suscripción PRO en Culqi si tiene preapprovalId
        if (suscripcionActual.getPreapprovalId() != null) {
            try {
                culqiService.cancelarSuscripcion(suscripcionActual.getPreapprovalId());
                log.info("✅ [Culqi] Suscripción PRO cancelada en Culqi: {}", suscripcionActual.getPreapprovalId());
            } catch (Exception e) {
                log.warn("⚠️ [Culqi] No se pudo cancelar suscripción PRO en Culqi (continuando): {}", e.getMessage());
            }
        }

        // 2. Crear cliente + tarjeta + suscripción BÁSICO en Culqi (cobra S/89 de inmediato)
        String customerId = culqiService.crearCliente(
                usuario.getEmail(), usuario.getNombre(),
                usuario.getApellido(), usuario.getNumeroCelular());
        String cardId = culqiService.crearTarjeta(customerId, request.getTokenId());
        String culqiSubId = culqiService.crearSuscripcion(cardId, culqiPlanIdBasico);

        // 3. Actualizar suscripción local a BÁSICO
        LocalDateTime ahora     = LocalDateTime.now();
        LocalDateTime proxCobro = ahora.plusMonths(1);
        BigDecimal precioBasico = culqiProperties.getPrecioBasico();

        suscripcionActual.setPlanId("BASICO");
        suscripcionActual.setEstado("ACTIVA");
        suscripcionActual.setPreapprovalId(culqiSubId);
        suscripcionActual.setFechaInicio(ahora);
        suscripcionActual.setFechaProximoCobro(proxCobro);
        suscripcionActual.setCurrentPeriodStart(ahora);
        suscripcionActual.setCurrentPeriodEnd(proxCobro);
        suscripcionActual.setMetodoPago("CULQI");
        suscripcionActual.setPrecioMensual(precioBasico);
        Suscripcion guardada = suscripcionRepository.save(suscripcionActual);

        // 4. Bloquear sucursales adicionales (la principal queda intacta)
        int bloqueadas = sucursalService.bloquearSucursalesAdicionales(tenantId);
        log.info("🔒 [Culqi] {} sucursal(es) bloqueada(s) para tenant={}", bloqueadas, tenantId);

        // 5. Email de confirmación de downgrade
        try {
            emailService.enviarEmailSuscripcion(usuario.getEmail(), usuario.getNombre(),
                    "DOWNGRADE_EFECTUADO", "BASICO");
        } catch (Exception e) {
            log.warn("⚠️ No se pudo enviar email de downgrade: {}", e.getMessage());
        }

        log.info("✅ [Culqi] Downgrade BÁSICO completado para tenant={}. SubId={}", tenantId, culqiSubId);

        String mensajeSucursales = bloqueadas > 0
                ? String.format(" %d sucursal(es) adicional(es) quedaron bloqueadas y se reactivarán si vuelves a Pro.", bloqueadas)
                : "";

        return ResponseEntity.ok(CulqiSuscribirResponseDTO.builder()
                .suscripcionId(guardada.getId())
                .estado(guardada.getEstado())
                .planId(guardada.getPlanId())
                .precioMensual(guardada.getPrecioMensual())
                .fechaInicio(guardada.getFechaInicio())
                .fechaProximoCobro(guardada.getFechaProximoCobro())
                .culqiSubscriptionId(culqiSubId)
                .mensaje("Plan Básico activado correctamente." + mensajeSucursales)
                .build());
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/culqi/admin/crear-plan
     * Operación de setup ONE-TIME: crea el plan Básico en Culqi.
     */
    @PostMapping("/admin/crear-plan")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> crearPlan() {
        long montoCentavos = culqiProperties.getPrecioBasico()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        log.info("🔧 [Culqi Admin] Creando plan Básico: monto={}c", montoCentavos);
        String planId = culqiService.crearPlan("Plan Básico Fluxus", montoCentavos);
        log.info("✅ [Culqi Admin] Plan Básico creado: {}", planId);

        return ResponseEntity.ok("Plan Básico creado. Guarda este ID en CULQI_PLAN_ID_BASICO: " + planId);
    }

    /**
     * POST /api/culqi/admin/crear-plan-pro
     * Operación de setup ONE-TIME: crea el plan Pro en Culqi.
     */
    @PostMapping("/admin/crear-plan-pro")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> crearPlanPro() {
        long montoCentavos = culqiProperties.getPrecioPro()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        log.info("🔧 [Culqi Admin] Creando plan Pro: monto={}c", montoCentavos);
        String planId = culqiService.crearPlan("Plan Pro Fluxus", montoCentavos);
        log.info("✅ [Culqi Admin] Plan Pro creado: {}", planId);

        return ResponseEntity.ok("Plan Pro creado. Guarda este ID en CULQI_PLAN_ID_PRO: " + planId);
    }
}
