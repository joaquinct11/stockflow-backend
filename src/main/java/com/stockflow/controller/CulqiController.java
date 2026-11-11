package com.stockflow.controller;

import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.dto.CulqiConfigResponseDTO;
import com.stockflow.dto.CulqiSuscribirRequestDTO;
import com.stockflow.dto.CulqiSuscribirResponseDTO;
import com.stockflow.dto.SetupPlanesResultDTO;
import com.stockflow.entity.PlanCatalogo;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.PlanCatalogoRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.PlanLimitService;
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
import java.util.List;
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
    private final PlanLimitService planLimitService;
    private final PlanCatalogoRepository planCatalogoRepository;

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
        String nivel     = "PRO".equalsIgnoreCase(plan) ? "PRO" : "BASICO";
        boolean esPro    = "PRO".equals(nivel);
        String tenantId  = TenantContext.getCurrentTenant();

        Suscripcion sus = suscripcionRepository
                .findFirstByTenantIdOrderByIdDesc(tenantId)
                .orElse(null);

        BigDecimal precio;
        String culqiPlanId;
        String nombrePlan;

        if (sus != null && sus.getPlanCodigo() != null) {
            String codigo = cambiarNivel(sus.getPlanCodigo(), nivel);
            Optional<PlanCatalogo> catOpt = planCatalogoRepository.findByCodigoAndActivoTrue(codigo);
            precio      = catOpt.map(PlanCatalogo::getPrecioMensual)
                                .orElseGet(() -> esPro ? culqiProperties.getPrecioPro() : culqiProperties.getPrecioBasico());
            culqiPlanId = catOpt.map(PlanCatalogo::getCulqiPlanId)
                                .filter(id -> id != null && !id.isBlank())
                                .orElse("");
            nombrePlan  = catOpt.map(PlanCatalogo::getLabelVisible)
                                .filter(lv -> lv != null && !lv.isBlank())
                                .orElse(esPro ? "Plan Pro" : "Plan Básico");
        } else {
            // Ruta legada
            precio      = esPro ? culqiProperties.getPrecioPro() : culqiProperties.getPrecioBasico();
            culqiPlanId = esPro ? culqiProperties.getPlanIdPro()  : culqiProperties.getPlanIdBasico();
            if (culqiPlanId == null) culqiPlanId = "";
            nombrePlan  = esPro ? "Plan Pro" : "Plan Básico";
        }

        return ResponseEntity.ok(CulqiConfigResponseDTO.builder()
                .publicKey(culqiProperties.getPublicKey())
                .planId(culqiPlanId)
                .precioMensual(precio)
                .nombrePlan(nombrePlan)
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

        // 2. Verificar estado de la suscripción existente
        // La llamada explícita al endpoint ES la acción voluntaria del usuario, incluso durante el TRIAL.
        // Solo se rechaza si ya hay una suscripción ACTIVA (no hace falta contratar dos veces).
        Optional<Suscripcion> existente = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId);
        if (existente.isPresent() && "ACTIVA".equals(existente.get().getEstado())) {
            throw new BadRequestException("Ya tienes una suscripción activa. Cancélala antes de contratar una nueva.");
        }

        // 3. Resolver planId y precio — dual routing: catálogo si hay plan_codigo, legado si no
        boolean    isPro       = "PRO".equalsIgnoreCase(request.getPlanId());
        String     nivel       = isPro ? "PRO" : "BASICO";
        Suscripcion refSus     = existente.orElse(null);
        String     culqiPlanId = resolverCulqiPlanIdOLegado(refSus, nivel, isPro);
        BigDecimal precio      = resolverPrecioOLegado(refSus, nivel, isPro);
        log.info("📋 [Culqi] Usando planId de Culqi: {} ({}) precio={}", culqiPlanId, nivel, precio);

        // 4. Culqi: crear cliente → tarjeta → suscripción (email tenant-scoped para customers independientes por tenant)
        String culqiEmail = buildCulqiEmail(email, tenantId);
        log.info("📡 [Culqi] Creando customer para culqiEmail={}", culqiEmail);
        String customerId = culqiService.crearCliente(culqiEmail, firstName, lastName, phoneNumber);

        log.info("📡 [Culqi] Registrando tarjeta para customerId={}", customerId);
        Map<String, Object> cardData = culqiService.crearTarjeta(customerId, request.getTokenId());
        String cardId = (String) cardData.get("id");
        String[] datosTarjeta = extraerDatosTarjeta(cardData);

        // Bug 1 — Si hay una suscripción SUSPENDIDA con preapprovalId, Culqi la mantiene activa
        // aunque el cobro haya fallado. Crear otra para el mismo customer+plan falla con
        // "ya estás inscrito". Cancelamos la anterior antes de crear la nueva.
        if (existente.isPresent() && "SUSPENDIDA".equals(existente.get().getEstado())
                && existente.get().getPreapprovalId() != null) {
            try {
                culqiService.cancelarSuscripcion(existente.get().getPreapprovalId());
                log.info("🔄 [Culqi] Suscripción suspendida cancelada en Culqi antes de crear nueva: {}",
                        existente.get().getPreapprovalId());
            } catch (Exception e) {
                log.warn("⚠️ [Culqi] No se pudo cancelar suscripción suspendida en Culqi " +
                        "(puede estar ya cancelada o expirada, continuando): {}", e.getMessage());
            }
        }

        log.info("📡 [Culqi] Creando suscripción con cardId={}, planId={}", cardId, culqiPlanId);
        Map<String, String> suscripcionMetadata = Map.of(
                "tenant_id",  tenantId,
                "usuario_id", String.valueOf(usuarioId));
        String culqiSubscriptionId = culqiService.crearSuscripcion(cardId, culqiPlanId, suscripcionMetadata);

        // 5. Persistir / actualizar suscripción local
        LocalDateTime ahora        = LocalDateTime.now();
        LocalDateTime proximoCobro = ahora.plusMonths(1);
        String        planIdLocal  = isPro ? "PRO" : "BASICO";
        // precio ya fue resuelto en el paso 3 (catálogo o legado)

        Suscripcion suscripcion;
        if (existente.isPresent()) {
            // Reactivar la existente (ej: trial expirado o cancelada)
            suscripcion = existente.get();
            suscripcion.setEstado("ACTIVA");
            suscripcion.setPlanId(planIdLocal);
            suscripcion.setPreapprovalId(culqiSubscriptionId);   // reutilizamos campo para el sub_id de Culqi
            suscripcion.setCulqiCustomerId(customerId);
            suscripcion.setFechaInicio(ahora);
            suscripcion.setFechaProximoCobro(proximoCobro);
            suscripcion.setCurrentPeriodStart(ahora);
            suscripcion.setCurrentPeriodEnd(proximoCobro);
            suscripcion.setMetodoPago(datosTarjeta[1] != null ? datosTarjeta[1] : "CULQI");
            suscripcion.setUltimos4Digitos(datosTarjeta[0]);
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
                    .culqiCustomerId(customerId)
                    .fechaInicio(ahora)
                    .fechaProximoCobro(proximoCobro)
                    .currentPeriodStart(ahora)
                    .currentPeriodEnd(proximoCobro)
                    .metodoPago(datosTarjeta[1] != null ? datosTarjeta[1] : "CULQI")
                    .ultimos4Digitos(datosTarjeta[0])
                    .build();
            log.info("✨ [Culqi] Creando nueva suscripción local para tenant={}", tenantId);
        }

        Suscripcion guardada = suscripcionRepository.save(suscripcion);
        log.info("✅ [Culqi] Suscripción activada localmente id={}, culqiSubId={}", guardada.getId(), culqiSubscriptionId);

        // Email de confirmación (usa el email real, nunca el tenant-scoped)
        try {
            emailService.enviarConfirmacionSuscripcionCulqi(
                    email, firstName, tenantId, planIdLocal, precio,
                    ahora, proximoCobro, datosTarjeta[0], datosTarjeta[1], culqiSubscriptionId);
        } catch (Exception e) {
            log.warn("⚠️ [Culqi] No se pudo enviar email de confirmación de suscripción: {}", e.getMessage());
        }

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

        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + usuarioId));

        // Cargar suscripción actual (puede no existir si el tenant es nuevo)
        Suscripcion suscripcionActual = suscripcionRepository
                .findFirstByTenantIdOrderByIdDesc(tenantId)
                .orElse(null);
        String preapprovalIdBasico = suscripcionActual != null ? suscripcionActual.getPreapprovalId() : null;

        // Resolver plan destino PRO — catálogo si hay plan_codigo, legado si no.
        // Falla rápido ANTES de cualquier llamada a Culqi para no dejar el cliente sin suscripción.
        String targetCodigoPro = suscripcionActual != null && suscripcionActual.getPlanCodigo() != null
                ? cambiarNivel(suscripcionActual.getPlanCodigo(), "PRO") : null;
        String     culqiPlanIdPro = resolverCulqiPlanIdOLegado(suscripcionActual, "PRO", true);
        BigDecimal precioPro      = resolverPrecioOLegado(suscripcionActual, "PRO", true);

        // Pre-calcular plan de recuperación BÁSICO del mismo segmento para el fallback
        String     planIdRecuperacion;
        BigDecimal precioRecuperacion;
        if (suscripcionActual != null && suscripcionActual.getPlanCodigo() != null) {
            String baseCodigoBasico = cambiarNivel(suscripcionActual.getPlanCodigo(), "BASICO");
            Optional<PlanCatalogo> catBase = planCatalogoRepository.findByCodigoAndActivoTrue(baseCodigoBasico);
            planIdRecuperacion = catBase.map(PlanCatalogo::getCulqiPlanId)
                                        .filter(id -> id != null && !id.isBlank()).orElse(null);
            precioRecuperacion = catBase.map(PlanCatalogo::getPrecioMensual)
                                        .orElseGet(culqiProperties::getPrecioBasico);
        } else {
            planIdRecuperacion = culqiProperties.getPlanIdBasico();
            precioRecuperacion = culqiProperties.getPrecioBasico();
        }

        // ── FASE 1: Validar todo ANTES de cancelar el Básico ────────────────────
        // Si algún paso falla aquí, el cliente sigue con su Básico activo.

        // 1a. Resolver/crear customer (email tenant-scoped, con URL-encode correcto en búsqueda)
        String culqiEmail = buildCulqiEmail(usuario.getEmail(), tenantId);
        log.info("⬆️  [Culqi] Fase 1 — resolviendo customer para culqiEmail={}", culqiEmail);
        String customerId = culqiService.crearCliente(
                culqiEmail, usuario.getNombre(), usuario.getApellido(), usuario.getNumeroCelular());
        log.info("✅ [Culqi] Customer resuelto: {}", customerId);

        // 1b. Registrar la nueva tarjeta (valida que el token sea válido antes de cancelar)
        log.info("⬆️  [Culqi] Fase 1 — registrando tarjeta para customerId={}", customerId);
        Map<String, Object> cardDataPro = culqiService.crearTarjeta(customerId, request.getTokenId());
        String cardId = (String) cardDataPro.get("id");
        String[] datosTarjetaPro = extraerDatosTarjeta(cardDataPro);
        log.info("✅ [Culqi] Tarjeta registrada: cardId={}", cardId);

        // ── FASE 2: Cancelar Básico ─────────────────────────────────────────────
        // Solo se ejecuta si customer y tarjeta fueron resueltos correctamente.
        if (preapprovalIdBasico != null && suscripcionActual != null && "ACTIVA".equals(suscripcionActual.getEstado())) {
            try {
                culqiService.cancelarSuscripcion(preapprovalIdBasico);
                log.info("✅ [Culqi] Suscripción Básico cancelada en Culqi: {}", preapprovalIdBasico);
            } catch (Exception e) {
                log.warn("⚠️ [Culqi] No se pudo cancelar suscripción anterior en Culqi (continuando): {}", e.getMessage());
            }
        }

        // ── FASE 3: Crear suscripción PRO ───────────────────────────────────────
        // Si falla aquí, el cliente ya canceló el Básico → recuperación automática si es posible.
        LocalDateTime ahora    = LocalDateTime.now();
        LocalDateTime proxCobro = ahora.plusMonths(1);
        // precioPro y culqiPlanIdPro ya fueron resueltos antes del flujo de Culqi
        Map<String, String> metadataPro = Map.of("tenant_id", tenantId, "usuario_id", String.valueOf(usuarioId));

        String culqiSubId;
        try {
            culqiSubId = culqiService.crearSuscripcion(cardId, culqiPlanIdPro, metadataPro);
            log.info("✅ [Culqi] Suscripción PRO creada: {}", culqiSubId);
        } catch (Exception proError) {
            // PRO falló después de cancelar Básico — intentar recuperación creando un Básico nuevo
            log.error("❌ [Culqi] Fallo crítico al crear suscripción PRO para tenant={} tras cancelar Básico. Error: {}",
                    tenantId, proError.getMessage());

            if (planIdRecuperacion != null && !planIdRecuperacion.isBlank()) {
                try {
                    Map<String, String> metadataRecovery = Map.of("tenant_id", tenantId, "usuario_id", String.valueOf(usuarioId));
                    String recoverySubId = culqiService.crearSuscripcion(cardId, planIdRecuperacion, metadataRecovery);
                    log.warn("⚠️ [Culqi] RECUPERACIÓN: nueva suscripción Básico creada tras fallo PRO. subId={}. " +
                             "Tenant={} queda en BASICO ACTIVO con la tarjeta recién registrada.", recoverySubId, tenantId);

                    if (suscripcionActual != null) {
                        suscripcionActual.setEstado("ACTIVA");
                        suscripcionActual.setPlanId("BASICO");
                        suscripcionActual.setPreapprovalId(recoverySubId);
                        suscripcionActual.setFechaInicio(ahora);
                        suscripcionActual.setFechaProximoCobro(proxCobro);
                        suscripcionActual.setCurrentPeriodStart(ahora);
                        suscripcionActual.setCurrentPeriodEnd(proxCobro);
                        suscripcionActual.setMetodoPago(datosTarjetaPro[1] != null ? datosTarjetaPro[1] : "CULQI");
                        suscripcionActual.setUltimos4Digitos(datosTarjetaPro[0]);
                        suscripcionActual.setPrecioMensual(precioRecuperacion);
                        suscripcionRepository.save(suscripcionActual);
                    }
                } catch (Exception recoveryError) {
                    log.error("❌ [Culqi] ESTADO INCONSISTENTE: fallo PRO Y fallo recuperación Básico para tenant={}. " +
                              "El cliente quedó SIN suscripción activa en Culqi. Requiere intervención manual. " +
                              "customerId={}, cardId={}. Error recuperación: {}",
                              tenantId, customerId, cardId, recoveryError.getMessage());
                }
            }

            throw new BadRequestException(
                    "No se pudo activar el plan Pro. Tu suscripción Básico fue cancelada pero el upgrade falló. " +
                    "Se intentó una recuperación automática. Si el problema persiste, contacta a soporte. " +
                    "Detalle técnico: " + proError.getMessage());
        }

        // ── FASE 4: Persistir upgrade PRO localmente ────────────────────────────
        Suscripcion suscripcion = suscripcionActual != null
                ? suscripcionActual
                : Suscripcion.builder().usuarioPrincipal(usuario).tenantId(tenantId).build();

        suscripcion.setPlanId("PRO");
        if (targetCodigoPro != null) suscripcion.setPlanCodigo(targetCodigoPro);
        suscripcion.setEstado("ACTIVA");
        suscripcion.setPreapprovalId(culqiSubId);
        suscripcion.setCulqiCustomerId(customerId);
        suscripcion.setFechaInicio(ahora);
        suscripcion.setFechaProximoCobro(proxCobro);
        suscripcion.setCurrentPeriodStart(ahora);
        suscripcion.setCurrentPeriodEnd(proxCobro);
        suscripcion.setMetodoPago(datosTarjetaPro[1] != null ? datosTarjetaPro[1] : "CULQI");
        suscripcion.setUltimos4Digitos(datosTarjetaPro[0]);
        suscripcion.setPrecioMensual(precioPro);
        Suscripcion guardada = suscripcionRepository.save(suscripcion);

        // Desbloquear sucursales que quedaron bloqueadas por downgrade previo, luego inicializar principal
        int desbloqueadas = sucursalService.desbloquearSucursalesAdicionales(tenantId);
        if (desbloqueadas > 0) {
            log.info("🔓 [Culqi] {} sucursal(es) reactivada(s) para tenant={}", desbloqueadas, tenantId);
        }
        sucursalService.inicializarPrincipal(tenantId);

        // Email de confirmación — SOLO después de que todo el flujo local haya completado exitosamente
        try {
            emailService.enviarConfirmacionSuscripcionCulqi(
                    usuario.getEmail(), usuario.getNombre(), tenantId, "PRO", precioPro,
                    ahora, proxCobro, datosTarjetaPro[0], datosTarjetaPro[1], culqiSubId);
        } catch (Exception e) {
            log.warn("⚠️ [Culqi] No se pudo enviar email de confirmación de upgrade: {}", e.getMessage());
        }

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
                buildCulqiEmail(usuario.getEmail(), tenantId),
                usuario.getNombre(), usuario.getApellido(), usuario.getNumeroCelular());

        Map<String, Object> cardDataCambio = culqiService.crearTarjeta(customerId, tokenId);
        String cardId = (String) cardDataCambio.get("id");
        String[] datosTarjetaCambio = extraerDatosTarjeta(cardDataCambio);

        culqiService.actualizarTarjetaSuscripcion(suscripcion.getPreapprovalId(), cardId);

        // Guardar nuevos datos de tarjeta
        if (datosTarjetaCambio[0] != null) suscripcion.setUltimos4Digitos(datosTarjetaCambio[0]);
        if (datosTarjetaCambio[1] != null) suscripcion.setMetodoPago(datosTarjetaCambio[1]);

        // Si estaba SUSPENDIDA, reactivar localmente (Culqi reintentará el cobro con la nueva tarjeta)
        boolean reactivada = "SUSPENDIDA".equals(suscripcion.getEstado());
        if (reactivada) {
            suscripcion.setEstado("ACTIVA");
            log.info("✅ [Culqi] Suscripción reactivada tras cambio de tarjeta para tenant={}", tenantId);
        }
        suscripcionRepository.save(suscripcion);

        // Recuperar sucursales bloqueadas por impago, respetando el límite del plan vigente.
        if (reactivada) {
            try {
                int maxSucursales = planLimitService.getMaxSucursales(tenantId);
                int desbloqueadas = sucursalService.desbloquearSucursalesConLimite(tenantId, maxSucursales);
                if (desbloqueadas > 0) {
                    log.info("🔓 [Culqi] {} sucursal(es) reactivada(s) tras cambio de tarjeta para tenant={}",
                            desbloqueadas, tenantId);
                }
            } catch (Exception e) {
                log.error("❌ [Culqi] Error al desbloquear sucursales tras cambio de tarjeta para tenant={}: {}",
                        tenantId, e.getMessage(), e);
            }
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

        // Solo se permite si el plan actual es PRO o la cuenta está SUSPENDIDA
        Suscripcion suscripcionActual = suscripcionRepository
                .findFirstByTenantIdOrderByIdDesc(tenantId)
                .orElseThrow(() -> new BadRequestException("No tienes una suscripción activa."));

        // Resolver plan destino BÁSICO — catálogo si hay plan_codigo, legado si no.
        // Falla rápido ANTES de cancelar la suscripción PRO en Culqi.
        String targetCodigoBasico = suscripcionActual.getPlanCodigo() != null
                ? cambiarNivel(suscripcionActual.getPlanCodigo(), "BASICO") : null;
        String     culqiPlanIdBasico = resolverCulqiPlanIdOLegado(suscripcionActual, "BASICO", false);
        BigDecimal precioBasico      = resolverPrecioOLegado(suscripcionActual, "BASICO", false);

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

        // 2. Crear cliente + tarjeta + suscripción BÁSICO en Culqi (cobra S/89 de inmediato; email tenant-scoped)
        String customerId = culqiService.crearCliente(
                buildCulqiEmail(usuario.getEmail(), tenantId),
                usuario.getNombre(), usuario.getApellido(), usuario.getNumeroCelular());
        Map<String, Object> cardDataDown = culqiService.crearTarjeta(customerId, request.getTokenId());
        String cardId = (String) cardDataDown.get("id");
        String[] datosTarjetaDown = extraerDatosTarjeta(cardDataDown);
        Map<String, String> metadataDown = Map.of("tenant_id", tenantId, "usuario_id", String.valueOf(usuarioId));
        String culqiSubId = culqiService.crearSuscripcion(cardId, culqiPlanIdBasico, metadataDown);

        // 3. Actualizar suscripción local a BÁSICO
        LocalDateTime ahora     = LocalDateTime.now();
        LocalDateTime proxCobro = ahora.plusMonths(1);
        // precioBasico y culqiPlanIdBasico ya fueron resueltos antes del flujo de Culqi

        suscripcionActual.setPlanId("BASICO");
        if (targetCodigoBasico != null) suscripcionActual.setPlanCodigo(targetCodigoBasico);
        suscripcionActual.setEstado("ACTIVA");
        suscripcionActual.setPreapprovalId(culqiSubId);
        suscripcionActual.setCulqiCustomerId(customerId);
        suscripcionActual.setFechaInicio(ahora);
        suscripcionActual.setFechaProximoCobro(proxCobro);
        suscripcionActual.setCurrentPeriodStart(ahora);
        suscripcionActual.setCurrentPeriodEnd(proxCobro);
        suscripcionActual.setMetodoPago(datosTarjetaDown[1] != null ? datosTarjetaDown[1] : "CULQI");
        suscripcionActual.setUltimos4Digitos(datosTarjetaDown[0]);
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

    /**
     * POST /api/culqi/admin/setup-planes
     *
     * Setup de plataforma (ONE-TIME por entorno): crea en Culqi los planes recurrentes
     * correspondientes a los 10 precios únicos del catálogo y guarda sus IDs en plan_catalogo.
     *
     * Idempotencia local:
     *   - Si un grupo de precio ya tiene culqi_plan_id en alguna fila → reutiliza ese ID.
     *   - Solo llama a Culqi cuando el grupo completo está sin configurar.
     *
     * Guard de producción:
     *   - Si la secretKey empieza con sk_live_, requiere el header X-Confirm-Production: true.
     *
     * Requiere rol SUPERADMIN (administrador de plataforma Fluxus, distinto del ADMIN de tenant).
     */
    @PostMapping("/admin/setup-planes")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<SetupPlanesResultDTO> setupPlanes(
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "X-Confirm-Production", required = false) String confirmProduction) {

        String secretKey = culqiProperties.getSecretKey();
        boolean esProduccion = secretKey != null && secretKey.startsWith("sk_live_");
        String entorno = esProduccion ? "PRODUCCION" : "INTEGRACION";

        if (esProduccion && !"true".equalsIgnoreCase(confirmProduction)) {
            throw new BadRequestException(
                    "Las credenciales activas son de PRODUCCIÓN (sk_live_*). " +
                    "Si realmente deseas ejecutar el setup en producción, " +
                    "agrega el header X-Confirm-Production: true a la solicitud.");
        }

        log.info("🔧 [Culqi Setup] Iniciando setup de planes. Entorno={}", entorno);

        List<PlanCatalogo> todos = planCatalogoRepository.findAllByActivoTrue();

        // Agrupar por precio_mensual (los 10 importes únicos del catálogo)
        java.util.Map<java.math.BigDecimal, List<PlanCatalogo>> grupos = new java.util.TreeMap<>();
        for (PlanCatalogo p : todos) {
            grupos.computeIfAbsent(p.getPrecioMensual(), k -> new java.util.ArrayList<>()).add(p);
        }

        List<SetupPlanesResultDTO.GrupoCreado>  creados   = new java.util.ArrayList<>();
        List<SetupPlanesResultDTO.GrupoOmitido> omitidos  = new java.util.ArrayList<>();
        List<SetupPlanesResultDTO.GrupoError>   errores   = new java.util.ArrayList<>();

        LocalDateTime ahora = LocalDateTime.now();

        for (java.util.Map.Entry<java.math.BigDecimal, List<PlanCatalogo>> entry : grupos.entrySet()) {
            java.math.BigDecimal precio = entry.getKey();
            List<PlanCatalogo>   grupo  = entry.getValue();
            List<String> codigos = grupo.stream().map(PlanCatalogo::getCodigo).toList();

            // Idempotencia: ¿ya tiene culqi_plan_id alguna fila de este grupo?
            String culqiPlanIdExistente = grupo.stream()
                    .map(PlanCatalogo::getCulqiPlanId)
                    .filter(id -> id != null && !id.isBlank())
                    .findFirst()
                    .orElse(null);

            if (culqiPlanIdExistente != null) {
                // Propagación: asignar el ID a filas del mismo grupo que aún lo tengan vacío
                grupo.stream()
                        .filter(p -> p.getCulqiPlanId() == null || p.getCulqiPlanId().isBlank())
                        .forEach(p -> {
                            p.setCulqiPlanId(culqiPlanIdExistente);
                            p.setCulqiSetupAt(ahora);
                            planCatalogoRepository.save(p);
                            log.info("🔗 [Culqi Setup] {} ← culqiPlanId={} (propagado)", p.getCodigo(), culqiPlanIdExistente);
                        });

                omitidos.add(SetupPlanesResultDTO.GrupoOmitido.builder()
                        .precioMensual(precio)
                        .culqiPlanIdExistente(culqiPlanIdExistente)
                        .codigosYaConfigurados(codigos)
                        .build());
                log.info("⏭️ [Culqi Setup] Grupo S/{} ya configurado. culqiPlanId={}", precio, culqiPlanIdExistente);
                continue;
            }

            // Crear plan en Culqi
            try {
                long montoCentavos = precio.multiply(BigDecimal.valueOf(100)).longValue();
                // Nombre descriptivo para el panel de Culqi
                String nombrePlan = String.format("Fluxus S/%s mensual (PEN)", precio.stripTrailingZeros().toPlainString());
                log.info("📡 [Culqi Setup] Creando plan Culqi: {} → {}c", nombrePlan, montoCentavos);

                String nuevoCulqiPlanId = culqiService.crearPlan(nombrePlan, montoCentavos);
                log.info("✅ [Culqi Setup] Plan creado: {} → culqiPlanId={}", nombrePlan, nuevoCulqiPlanId);

                // Asignar a todas las filas del grupo
                for (PlanCatalogo p : grupo) {
                    p.setCulqiPlanId(nuevoCulqiPlanId);
                    p.setCulqiSetupAt(ahora);
                    planCatalogoRepository.save(p);
                    log.info("💾 [Culqi Setup] {} ← culqiPlanId={}", p.getCodigo(), nuevoCulqiPlanId);
                }

                creados.add(SetupPlanesResultDTO.GrupoCreado.builder()
                        .precioMensual(precio)
                        .culqiPlanId(nuevoCulqiPlanId)
                        .codigosAsignados(codigos)
                        .build());

            } catch (Exception e) {
                log.error("❌ [Culqi Setup] Error creando plan para S/{}: {}", precio, e.getMessage());
                errores.add(SetupPlanesResultDTO.GrupoError.builder()
                        .precioMensual(precio)
                        .error(e.getMessage())
                        .codigosAfectados(codigos)
                        .build());
            }
        }

        SetupPlanesResultDTO resultado = SetupPlanesResultDTO.builder()
                .entorno(entorno)
                .totalPlanesCreados(creados.size())
                .totalPlanesOmitidos(omitidos.size())
                .totalCodigosAsignados(creados.stream()
                        .mapToInt(g -> g.getCodigosAsignados().size()).sum())
                .totalErrores(errores.size())
                .planesCreados(creados)
                .planesOmitidos(omitidos)
                .errores(errores)
                .build();

        log.info("✅ [Culqi Setup] Completado. Creados={}, Omitidos={}, Errores={}",
                resultado.getTotalPlanesCreados(), resultado.getTotalPlanesOmitidos(), resultado.getTotalErrores());

        return ResponseEntity.ok(resultado);
    }

    // ── Dual routing helpers ──────────────────────────────────────────────────

    /**
     * Resuelve el culqi_plan_id para el nivel solicitado.
     * Ruta catálogo (sus.planCodigo != null): busca en plan_catalogo y lanza si falta culqi_plan_id.
     * Ruta legada (sus == null o planCodigo == null): usa las variables de entorno.
     */
    private String resolverCulqiPlanIdOLegado(Suscripcion sus, String nivel, boolean esPro) {
        if (sus != null && sus.getPlanCodigo() != null) {
            String codigo = cambiarNivel(sus.getPlanCodigo(), nivel);
            PlanCatalogo cat = planCatalogoRepository.findByCodigoAndActivoTrue(codigo)
                    .orElseThrow(() -> new BadRequestException(
                            "Plan no encontrado en catálogo: " + codigo + ". Contacta al soporte."));
            if (cat.getCulqiPlanId() == null || cat.getCulqiPlanId().isBlank())
                throw new BadRequestException(
                        "El plan " + codigo + " no tiene culqi_plan_id configurado. " +
                        "Ejecuta POST /api/culqi/admin/setup-planes con credenciales de integración primero.");
            return cat.getCulqiPlanId();
        }
        String planId = esPro ? culqiProperties.getPlanIdPro() : culqiProperties.getPlanIdBasico();
        if (planId == null || planId.isBlank())
            throw new BadRequestException(esPro
                    ? "No hay un plan Pro de Culqi configurado (CULQI_PLAN_ID_PRO)."
                    : "No hay un plan Básico de Culqi configurado (CULQI_PLAN_ID_BASICO).");
        return planId;
    }

    /**
     * Resuelve el precio mensual para el nivel solicitado.
     * Ruta catálogo: precio de plan_catalogo. Ruta legada: variable de entorno.
     */
    private BigDecimal resolverPrecioOLegado(Suscripcion sus, String nivel, boolean esPro) {
        if (sus != null && sus.getPlanCodigo() != null) {
            String codigo = cambiarNivel(sus.getPlanCodigo(), nivel);
            return planCatalogoRepository.findByCodigoAndActivoTrue(codigo)
                    .map(PlanCatalogo::getPrecioMensual)
                    .orElseGet(() -> esPro ? culqiProperties.getPrecioPro() : culqiProperties.getPrecioBasico());
        }
        return esPro ? culqiProperties.getPrecioPro() : culqiProperties.getPrecioBasico();
    }

    /**
     * Cambia el sufijo de nivel en un código de plan del catálogo.
     * "VETERINARIA_BASICO" + "PRO" → "VETERINARIA_PRO"
     */
    private static String cambiarNivel(String planCodigo, String nivel) {
        return planCodigo.replaceAll("_(BASICO|PRO)$", "_" + nivel.toUpperCase());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Construye un email tenant-scoped para crear customers independientes por tenant en Culqi.
     * user@domain.com + tenantId → user+tenantId@domain.com
     * Esto evita que dos tenants del mismo usuario compartan el mismo customer_id en Culqi,
     * lo que causaría HTTP 400 al intentar crear una segunda suscripción al mismo plan.
     */
    static String buildCulqiEmail(String email, String tenantId) {
        if (email == null || tenantId == null || tenantId.isBlank()) return email;
        int atIdx = email.indexOf('@');
        if (atIdx < 0) return email;
        return email.substring(0, atIdx) + "+" + tenantId + email.substring(atIdx);
    }

    /**
     * Normaliza un email tenant-scoped a su forma real.
     * user+tenantId@domain.com → user@domain.com
     * Emails sin sufijo se devuelven sin cambios.
     */
    static String normalizarEmailCulqi(String email) {
        if (email == null) return null;
        int plusIdx = email.indexOf('+');
        int atIdx   = email.indexOf('@');
        if (plusIdx >= 0 && atIdx > plusIdx) {
            return email.substring(0, plusIdx) + email.substring(atIdx);
        }
        return email;
    }

    /**
     * Extrae el tenantId del sufijo de un email tenant-scoped.
     * user+tenantId@domain.com → tenantId
     * Retorna null si el email no tiene sufijo.
     */
    static String extraerTenantIdDeEmail(String email) {
        if (email == null) return null;
        int plusIdx = email.indexOf('+');
        int atIdx   = email.indexOf('@');
        if (plusIdx >= 0 && atIdx > plusIdx) {
            return email.substring(plusIdx + 1, atIdx);
        }
        return null;
    }

    /**
     * Extrae [ultimos4Digitos, brand] de la respuesta de Culqi POST /cards.
     * Culqi devuelve los datos de la tarjeta en source.last_four y source.iin.card_brand.
     * Retorna un array de 2 posiciones — cada elemento puede ser null si no está disponible.
     */
    @SuppressWarnings("unchecked")
    private static String[] extraerDatosTarjeta(Map<String, Object> cardData) {
        if (cardData == null) return new String[]{null, null};

        String lastFour = null;
        String brand    = null;

        // Estructura principal: card.source.last_four y card.source.iin.card_brand
        Object sourceObj = cardData.get("source");
        if (sourceObj instanceof Map<?, ?> raw) {
            Map<String, Object> source = (Map<String, Object>) raw;
            if (source.get("last_four") instanceof String lf) lastFour = lf;
            if (source.get("iin") instanceof Map<?, ?> iinRaw) {
                Map<String, Object> iin = (Map<String, Object>) iinRaw;
                if (iin.get("card_brand") instanceof String b) brand = b;
            }
            // Fallback: brand directo en source
            if (brand == null && source.get("brand") instanceof String b) brand = b;
        }

        // Fallback: last_four y brand en el nivel raíz (algunos eventos de webhook)
        if (lastFour == null && cardData.get("last_four") instanceof String lf) lastFour = lf;
        if (brand == null && cardData.get("brand") instanceof String b) brand = b;

        return new String[]{lastFour, brand};
    }
}
