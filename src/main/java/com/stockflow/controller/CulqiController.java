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

    // ── Config pública ────────────────────────────────────────────────────────

    /**
     * GET /api/culqi/config
     * Devuelve la public key y datos del plan para que el frontend inicialice Culqi.js.
     * Requiere autenticación (el usuario debe estar logueado para suscribirse).
     */
    @GetMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CulqiConfigResponseDTO> obtenerConfig() {
        return ResponseEntity.ok(CulqiConfigResponseDTO.builder()
                .publicKey(culqiProperties.getPublicKey())
                .planId(culqiProperties.getPlanIdBasico())
                .precioMensual(culqiProperties.getPrecioBasico())
                .nombrePlan("Plan Básico")
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

        // 3. Determinar planId de Culqi (siempre usar el configurado en el servidor)
        // El frontend puede enviar "BASICO"/"PRO" como identificador interno,
        // pero Culqi necesita el ID real: pln_test_xxx / pln_live_xxx
        String culqiPlanId = culqiProperties.getPlanIdBasico();
        if (culqiPlanId == null || culqiPlanId.isBlank()) {
            throw new BadRequestException("No hay un plan Culqi configurado en el servidor. " +
                    "Ve a Culqi Panel → Suscripciones → Planes y copia el ID en CULQI_PLAN_ID_BASICO.");
        }
        log.info("📋 [Culqi] Usando planId de Culqi: {}", culqiPlanId);

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
        BigDecimal    precio       = culqiProperties.getPrecioBasico();

        Suscripcion suscripcion;
        if (existente.isPresent()) {
            // Reactivar la existente (ej: trial expirado o cancelada)
            suscripcion = existente.get();
            suscripcion.setEstado("ACTIVA");
            suscripcion.setPreapprovalId(culqiSubscriptionId);   // reutilizamos campo para el sub_id de Culqi
            suscripcion.setFechaInicio(ahora);
            suscripcion.setFechaProximoCobro(proximoCobro);
            suscripcion.setCurrentPeriodStart(ahora);
            suscripcion.setCurrentPeriodEnd(proximoCobro);
            suscripcion.setMetodoPago("CULQI");
            suscripcion.setPrecioMensual(precio);
            suscripcion.setTrialEndDate(null);
            log.info("♻️ [Culqi] Reactivando suscripción existente id={}", suscripcion.getId());
        } else {
            // Crear nueva suscripción local
            suscripcion = Suscripcion.builder()
                    .usuarioPrincipal(usuario)
                    .tenantId(tenantId)
                    .planId("BASICO")
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

        return ResponseEntity.ok(CulqiSuscribirResponseDTO.builder()
                .suscripcionId(guardada.getId())
                .estado(guardada.getEstado())
                .planId(guardada.getPlanId())
                .precioMensual(guardada.getPrecioMensual())
                .fechaInicio(guardada.getFechaInicio())
                .fechaProximoCobro(guardada.getFechaProximoCobro())
                .culqiSubscriptionId(culqiSubscriptionId)
                .mensaje("¡Suscripción activada exitosamente! Tu plan Básico está activo.")
                .build());
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/culqi/admin/crear-plan
     * Operación de setup ONE-TIME: crea el plan en Culqi y devuelve su ID.
     * Guarda ese ID en application.yml / variables de entorno como culqi.plan-id-basico.
     *
     * Solo accesible por ROL ADMIN.
     */
    @PostMapping("/admin/crear-plan")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> crearPlan() {
        long montoCentavos = culqiProperties.getPrecioBasico()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        log.info("🔧 [Culqi Admin] Creando plan: nombre='Plan Básico Fluxus', monto={}c", montoCentavos);
        String planId = culqiService.crearPlan("Plan Básico Fluxus", montoCentavos);
        log.info("✅ [Culqi Admin] Plan creado: {}", planId);

        return ResponseEntity.ok("Plan creado exitosamente. Guarda este ID en culqi.plan-id-basico: " + planId);
    }
}
