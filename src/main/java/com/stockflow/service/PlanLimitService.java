package com.stockflow.service;

import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Valida los límites y restricciones según el plan de suscripción del tenant.
 *
 * BÁSICO : max 3 usuarios · max 500 productos · roles Admin y Vendedor
 * PRO    : sin límites de usuarios ni productos · todos los roles
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanLimitService {

    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioRepository     usuarioRepository;
    private final ProductoRepository    productoRepository;

    // ── Límites del plan Básico ──────────────────────────────────────────────
    public static final int         BASICO_MAX_USUARIOS        = 3;
    public static final int         BASICO_MAX_PRODUCTOS       = 500;
    public static final Set<String> BASICO_ROLES_PERMITIDOS    = Set.of("ADMIN", "VENDEDOR");

    // ── Resolución de plan ───────────────────────────────────────────────────

    public String getPlanId(String tenantId) {
        // Usar la suscripción más reciente (mayor ID) para reflejar upgrades/downgrades
        return suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId)
                .map(s -> s.getPlanId() != null ? s.getPlanId().toUpperCase() : "BASICO")
                .orElse("BASICO");
    }

    public boolean isBasico(String tenantId) {
        return "BASICO".equals(getPlanId(tenantId));
    }

    // ── Validaciones ─────────────────────────────────────────────────────────

    /**
     * Lanza BadRequestException si el tenant BÁSICO ya alcanzó el límite de usuarios.
     */
    public void validarLimiteUsuarios(String tenantId) {
        if (!isBasico(tenantId)) return;
        long total = usuarioRepository.countByTenantId(tenantId);
        log.debug("Plan BASICO — usuarios actuales: {} / {}", total, BASICO_MAX_USUARIOS);
        if (total >= BASICO_MAX_USUARIOS) {
            throw new BadRequestException(
                    "El plan Básico permite máximo " + BASICO_MAX_USUARIOS + " usuarios. " +
                    "Actualiza al plan Pro para agregar más."
            );
        }
    }

    /**
     * Lanza BadRequestException si el tenant BÁSICO ya alcanzó el límite de productos.
     */
    public void validarLimiteProductos(String tenantId) {
        if (!isBasico(tenantId)) return;
        long total = productoRepository.countByTenantId(tenantId);
        log.debug("Plan BASICO — productos actuales: {} / {}", total, BASICO_MAX_PRODUCTOS);
        if (total >= BASICO_MAX_PRODUCTOS) {
            throw new BadRequestException(
                    "El plan Básico permite máximo " + BASICO_MAX_PRODUCTOS + " productos. " +
                    "Actualiza al plan Pro para agregar más."
            );
        }
    }

    /**
     * Lanza BadRequestException si el tenant BÁSICO intenta asignar un rol no permitido.
     */
    public void validarRolPermitido(String tenantId, String rolNombre) {
        if (!isBasico(tenantId)) return;
        if (!BASICO_ROLES_PERMITIDOS.contains(rolNombre)) {
            throw new BadRequestException(
                    "El plan Básico solo permite los roles Admin y Vendedor. " +
                    "Actualiza al plan Pro para usar roles avanzados (Gerente, Gestor de Inventario)."
            );
        }
    }
}
