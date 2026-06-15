package com.stockflow.scheduler;

import com.stockflow.entity.OrdenCompra;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Tenant;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.OrdenCompraRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.service.EmailService;
import com.stockflow.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionScheduler {

    private final TenantRepository               tenantRepository;
    private final ProductoRepository             productoRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final OrdenCompraRepository          ordenCompraRepository;
    private final SuscripcionRepository          suscripcionRepository;
    private final NotificacionService            notificacionService;
    private final EmailService                   emailService;

    private static final List<String> ROLES_INVENTARIO =
            List.of("ADMIN", "GESTOR_INVENTARIO");
    private static final List<String> ROLES_VENCIDO =
            List.of("ADMIN", "GESTOR_INVENTARIO", "VENDEDOR");

    // ── Stock bajo — todos los días a las 8:00 AM Lima ──────────────────────

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Lima")
    public void verificarStockBajo() {
        log.info("⏰ [Scheduler] Verificando stock bajo...");
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            if (!Boolean.TRUE.equals(tenant.getActivo())) continue;
            String tenantId = tenant.getTenantId();

            List<Producto> bajosDeStock = productoRepository.findProductosBajoStock(tenantId);

            for (Producto p : bajosDeStock) {
                for (String rol : ROLES_INVENTARIO) {
                    if (notificacionService.yaExisteNoLeida(tenantId, rol, "STOCK_BAJO", p.getId())) continue;

                    notificacionService.notificarRoles(
                            tenantId,
                            List.of(rol),
                            "STOCK_BAJO",
                            "Stock bajo: " + p.getNombre(),
                            String.format("Quedan %d unidades de '%s' (mínimo: %d). Considera hacer un pedido.",
                                    p.getStockActual(), p.getNombre(), p.getStockMinimo()),
                            p.getId(),
                            "PRODUCTO"
                    );
                }
            }
        }
        log.info("⏰ [Scheduler] Stock bajo verificado.");
    }

    // ── Vencimientos — todos los días a las 8:05 AM Lima ────────────────────

    @Scheduled(cron = "0 5 8 * * *", zone = "America/Lima")
    public void verificarVencimientos() {
        log.info("⏰ [Scheduler] Verificando vencimientos de productos...");
        List<Tenant> tenants = tenantRepository.findAll();
        LocalDate hoy        = LocalDate.now();
        LocalDate en30dias   = hoy.plusDays(90);

        for (Tenant tenant : tenants) {
            if (!Boolean.TRUE.equals(tenant.getActivo())) continue;
            String tenantId = tenant.getTenantId();

            // Lotes ya vencidos con stock > 0 (fechaVencimiento en MovimientoInventario)
            List<Producto> vencidos = movimientoRepository.findProductosConLoteVencido(tenantId, hoy);
            for (Producto p : vencidos) {
                for (String rol : ROLES_VENCIDO) {
                    if (notificacionService.yaExisteNoLeida(tenantId, rol, "PRODUCTO_VENCIDO", p.getId())) continue;
                    notificacionService.notificarRoles(
                            tenantId, List.of(rol),
                            "PRODUCTO_VENCIDO",
                            "🚨 Producto vencido: " + p.getNombre(),
                            String.format("'%s' tiene un lote VENCIDO en stock. Retíralo para evitar venderlo.",
                                    p.getNombre()),
                            p.getId(), "PRODUCTO"
                    );
                }
            }

            // Lotes que vencen en los próximos 90 días
            List<Producto> porVencer = movimientoRepository.findProductosConLotePorVencer(tenantId, hoy, en30dias);
            for (Producto p : porVencer) {
                for (String rol : ROLES_INVENTARIO) {
                    if (notificacionService.yaExisteNoLeida(tenantId, rol, "PRODUCTO_POR_VENCER", p.getId())) continue;
                    notificacionService.notificarRoles(
                            tenantId, List.of(rol),
                            "PRODUCTO_POR_VENCER",
                            "⚠️ Lote por vencer: " + p.getNombre(),
                            String.format("'%s' tiene un lote que vence en los próximos 90 días. Stock: %d unidades.",
                                    p.getNombre(), p.getStockActual()),
                            p.getId(), "PRODUCTO"
                    );
                }
            }
        }
        log.info("⏰ [Scheduler] Vencimientos verificados.");
    }

    // ── Productos sin rotación — todos los días a las 8:10 AM Lima ─────────

    @Scheduled(cron = "0 10 8 * * *", zone = "America/Lima")
    public void verificarProductosSinMovimiento() {
        log.info("⏰ [Scheduler] Verificando productos sin rotación...");
        LocalDateTime hace30dias = LocalDateTime.now().minusDays(30);
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            if (!Boolean.TRUE.equals(tenant.getActivo())) continue;
            String tenantId = tenant.getTenantId();

            List<Producto> slowMovers = productoRepository.findSlowMovers(tenantId, hace30dias);
            for (Producto p : slowMovers) {
                for (String rol : List.of("ADMIN")) {
                    if (notificacionService.yaExisteNoLeida(tenantId, rol, "PRODUCTO_SIN_MOVIMIENTO", p.getId())) continue;
                    notificacionService.notificarRoles(
                            tenantId, List.of(rol),
                            "PRODUCTO_SIN_MOVIMIENTO",
                            "📦 Sin rotación: " + p.getNombre(),
                            String.format("'%s' tiene %d unidades en stock sin salidas en los últimos 30 días. Considera una promoción o devolución al proveedor.",
                                    p.getNombre(), p.getStockActual()),
                            p.getId(), "PRODUCTO"
                    );
                }
            }
        }
        log.info("⏰ [Scheduler] Productos sin rotación verificados.");
    }

    // ── OCs sin recepcionar — todos los días a las 8:15 AM Lima ────────────

    @Scheduled(cron = "0 15 8 * * *", zone = "America/Lima")
    public void verificarOCsSinRecepcionar() {
        log.info("⏰ [Scheduler] Verificando OCs enviadas sin recepcionar...");
        LocalDateTime hace15dias = LocalDateTime.now().minusDays(15);
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            if (!Boolean.TRUE.equals(tenant.getActivo())) continue;
            String tenantId = tenant.getTenantId();

            List<OrdenCompra> pendientes = ordenCompraRepository.findEnviadasSinRecepcionar(tenantId, hace15dias);
            for (OrdenCompra oc : pendientes) {
                for (String rol : List.of("ADMIN", "GESTOR_INVENTARIO")) {
                    if (notificacionService.yaExisteNoLeida(tenantId, rol, "OC_SIN_RECEPCIONAR", oc.getId())) continue;
                    notificacionService.notificarRoles(
                            tenantId, List.of(rol),
                            "OC_SIN_RECEPCIONAR",
                            "⏳ OC pendiente de recepción #" + oc.getId(),
                            String.format("La OC al proveedor '%s' lleva más de 15 días enviada sin recepcionarse. Haz seguimiento.",
                                    oc.getProveedor().getNombre()),
                            oc.getId(), "ORDEN_COMPRA"
                    );
                }
            }
        }
        log.info("⏰ [Scheduler] OCs sin recepcionar verificadas.");
    }

    // ── Trials por vencer — todos los días a las 9:00 AM Lima ──────────────
    // Notifica TRIALS por vencer y suscripciones ACTIVAS sin preapprovalId (cobro manual).

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Lima")
    public void verificarSuscripcionesPorVencer() {
        log.info("⏰ [Scheduler] Verificando trials y suscripciones manuales por vencer...");
        LocalDateTime ahora = LocalDateTime.now();
        int[] diasAviso     = {7, 3, 1};

        for (int dias : diasAviso) {
            LocalDateTime desde = ahora.plusDays(dias).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime hasta = ahora.plusDays(dias).withHour(23).withMinute(59).withSecond(59).withNano(999999999);

            // 1. Trials por vencer
            List<Suscripcion> trials = suscripcionRepository.findTrialsPorVencerEntre(desde, hasta);
            for (Suscripcion s : trials) {
                String tenantId = s.getTenantId();
                if (notificacionService.yaExisteNoLeida(tenantId, "ADMIN", "SUSCRIPCION_POR_VENCER", s.getId())) continue;
                notificacionService.notificarRoles(
                        tenantId, List.of("ADMIN"),
                        "SUSCRIPCION_POR_VENCER",
                        "⏳ Tu período de prueba vence en " + dias + " día" + (dias == 1 ? "" : "s"),
                        String.format("Tu prueba gratuita del plan %s vence el %s. Suscríbete para no perder el acceso.",
                                s.getPlanId(), s.getTrialEndDate().toLocalDate()),
                        s.getId(), "SUSCRIPCION"
                );
                // Email al dueño del tenant — canal más efectivo para conversión
                try {
                    var usuario = s.getUsuarioPrincipal();
                    if (usuario != null && usuario.getEmail() != null) {
                        emailService.enviarEmailTrialPorVencer(
                                usuario.getEmail(),
                                usuario.getNombre(),
                                dias,
                                s.getTrialEndDate().toLocalDate()
                        );
                    }
                } catch (Exception e) {
                    log.warn("No se pudo enviar email de trial por vencer para suscripción {}: {}", s.getId(), e.getMessage());
                }
            }

            // 2. Suscripciones activas con cobro manual (sin preapprovalId de MP)
            List<Suscripcion> manuales = suscripcionRepository.findActivasManualesPorVencerEntre(desde, hasta);
            for (Suscripcion s : manuales) {
                String tenantId = s.getTenantId();
                if (notificacionService.yaExisteNoLeida(tenantId, "ADMIN", "SUSCRIPCION_POR_VENCER", s.getId())) continue;
                notificacionService.notificarRoles(
                        tenantId, List.of("ADMIN"),
                        "SUSCRIPCION_POR_VENCER",
                        "💳 Tu suscripción vence en " + dias + " día" + (dias == 1 ? "" : "s"),
                        String.format("Tu plan %s vencerá el %s. Renuévalo para no perder el acceso.",
                                s.getPlanId(), s.getFechaProximoCobro().toLocalDate()),
                        s.getId(), "SUSCRIPCION"
                );
            }
        }
        log.info("⏰ [Scheduler] Verificación de suscripciones completada.");
    }

}

