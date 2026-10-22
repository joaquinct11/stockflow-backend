package com.stockflow.service;

import com.stockflow.entity.PlanCatalogo;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.PlanCatalogoRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Valida los límites y restricciones según el plan de suscripción del tenant.
 *
 * Orden de resolución de límites:
 *  1. Si la suscripción tiene plan_codigo → límites del catálogo.
 *  2. Si plan_codigo es NULL → comportamiento legado (constantes según planId).
 *
 * Los límites legado se conservan intactos para no alterar suscripciones existentes.
 *
 * BÁSICO legado : max 5 usuarios · max 2000 productos · 1 sucursal
 * PRO legado    : max 15 usuarios · max 5000 productos · 5 sucursales
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanLimitService {

    private final SuscripcionRepository    suscripcionRepository;
    private final UsuarioRepository        usuarioRepository;
    private final ProductoRepository       productoRepository;
    private final UsuarioTenantRepository  usuarioTenantRepository;
    private final TenantRepository         tenantRepository;
    private final PlanCatalogoRepository   planCatalogoRepository;

    // ── Límites legado (suscripciones sin plan_codigo) ───────────────────────
    public static final int         BASICO_MAX_USUARIOS           = 5;
    public static final int         BASICO_MAX_PRODUCTOS          = 2000;
    public static final int         PRO_MAX_USUARIOS              = 15;
    public static final int         PRO_MAX_PRODUCTOS             = 5000;
    public static final Set<String> BASICO_ROLES_PERMITIDOS       = Set.of("ADMIN", "VENDEDOR", "GESTOR_INVENTARIO");
    public static final Set<String> ROLES_EXCLUSIVOS_VETERINARIA  = Set.of(
            "VETERINARIO", "RECEPCIONISTA", "AUXILIAR_VETERINARIO", "GROOMER"
    );

    // ── Resolución de plan ───────────────────────────────────────────────────

    public String getPlanId(String tenantId) {
        return suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId)
                .map(s -> s.getPlanId() != null ? s.getPlanId().toUpperCase() : "BASICO")
                .orElse("BASICO");
    }

    public boolean isBasico(String tenantId) {
        return "BASICO".equals(getPlanId(tenantId));
    }

    // ── Resolución de límites desde catálogo con fallback legado ─────────────

    public int getMaxUsuarios(String tenantId) {
        return suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId)
                .flatMap(s -> s.getPlanCodigo() != null
                        ? planCatalogoRepository.findByCodigoAndActivoTrue(s.getPlanCodigo())
                        : java.util.Optional.empty())
                .map(PlanCatalogo::getMaxUsuarios)
                .orElseGet(() -> isBasico(tenantId) ? BASICO_MAX_USUARIOS : PRO_MAX_USUARIOS);
    }

    public int getMaxProductos(String tenantId) {
        return suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId)
                .flatMap(s -> s.getPlanCodigo() != null
                        ? planCatalogoRepository.findByCodigoAndActivoTrue(s.getPlanCodigo())
                        : java.util.Optional.empty())
                .map(PlanCatalogo::getMaxProductos)
                .orElseGet(() -> isBasico(tenantId) ? BASICO_MAX_PRODUCTOS : PRO_MAX_PRODUCTOS);
    }

    public int getMaxSucursales(String tenantId) {
        return suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId)
                .flatMap(s -> s.getPlanCodigo() != null
                        ? planCatalogoRepository.findByCodigoAndActivoTrue(s.getPlanCodigo())
                        : java.util.Optional.empty())
                .map(PlanCatalogo::getMaxSucursales)
                .orElseGet(() -> isBasico(tenantId) ? 1 : 5);
    }

    // ── Validaciones ─────────────────────────────────────────────────────────

    public void validarLimiteUsuarios(String tenantId) {
        int limite = getMaxUsuarios(tenantId);
        long total = usuarioTenantRepository.countByTenantIdAndActivoTrue(tenantId);
        log.debug("Usuarios: {} / {} (tenantId={})", total, limite, tenantId);
        if (total >= limite) {
            String nivel = "BASICO".equals(getPlanId(tenantId)) ? "Básico" : "Pro";
            throw new BadRequestException(
                    "El plan " + nivel + " permite máximo " + limite + " usuarios. "
                    + "Actualiza al plan Pro para agregar más.");
        }
    }

    public void validarLimiteProductos(String tenantId) {
        int limite = getMaxProductos(tenantId);
        long total = productoRepository.countByTenantId(tenantId);
        log.debug("Productos: {} / {} (tenantId={})", total, limite, tenantId);
        if (total >= limite) {
            String nivel = "BASICO".equals(getPlanId(tenantId)) ? "Básico" : "Pro";
            throw new BadRequestException(
                    "El plan " + nivel + " permite máximo " + limite + " productos. "
                    + "Actualiza tu suscripción para agregar más.");
        }
    }

    public String getRubro(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .map(t -> t.getRubro() != null ? t.getRubro().toUpperCase() : "OTRO")
                .orElse("OTRO");
    }

    public boolean isVeterinaria(String tenantId) {
        return "VETERINARIA".equals(getRubro(tenantId));
    }

    /**
     * Lanza BadRequestException si el tenant BÁSICO intenta asignar un rol no permitido.
     * Los roles veterinarios se permiten en plan BÁSICO únicamente cuando el tenant tiene rubro VETERINARIA.
     */
    public void validarRolPermitido(String tenantId, String rolNombre) {
        if (!isBasico(tenantId)) return;
        if (BASICO_ROLES_PERMITIDOS.contains(rolNombre)) return;
        if (ROLES_EXCLUSIVOS_VETERINARIA.contains(rolNombre) && isVeterinaria(tenantId)) return;
        throw new BadRequestException(
                "El plan Básico solo permite los roles Administrador, Vendedor y Almacenero."
        );
    }
}
