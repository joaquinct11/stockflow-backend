package com.stockflow.service.impl;

import com.stockflow.entity.OrdenCompra;
import com.stockflow.entity.OrdenCompraDetalle;
import com.stockflow.entity.Tenant;
import com.stockflow.repository.OrdenCompraRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.service.EmailService;
import com.stockflow.util.OcPdfGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación usando Resend REST API (https://resend.com).
 * No requiere dominio propio para testing — usa onboarding@resend.dev.
 * Free tier: 3,000 emails/mes · 100/día.
 *
 * ⚠️  Limitación sin dominio verificado:
 *     Solo puede enviar al email con el que te registraste en Resend.
 *     Para enviar a cualquier destinatario → verifica un dominio en resend.com.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final OrdenCompraRepository ordenCompraRepository;
    private final TenantRepository tenantRepository;
    private final OcPdfGenerator ocPdfGenerator;

    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate = new RestTemplate();  // no es bean — instancia local OK

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Value("${resend.from-name}")
    private String fromName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ── Métodos públicos ───────────────────────────────────────────────────────

    @Override
    @Async
    public void enviarEmailRecuperacionContraseña(String email, String nombre, String token) {
        log.info("📧 Enviando email de recuperación a: {}", email);
        String link = frontendUrl + "/reset-password?token=" + token;
        String html = buildHtml(
                "Recuperar contraseña",
                "Hola " + nombre + ",",
                "Recibimos una solicitud para <b>recuperar tu contraseña</b> en Fluxus.<br>"
                + "Si fuiste tú, haz clic en el botón para crear una nueva contraseña.",
                link,
                "Recuperar contraseña",
                "Este enlace expira en <b>1 hora</b>. Si no lo solicitaste, ignora este correo."
        );
        enviar(email, "Recupera tu contraseña — Fluxus", html);
    }

    @Override
    @Async
    public void enviarEmailVerificacion(String email, String nombre, String token) {
        log.info("📧 Enviando email de verificación a: {}", email);
        String link = frontendUrl + "/verify-email?token=" + token;
        String html = buildHtml(
                "Verificar cuenta",
                "¡Bienvenido a Fluxus, " + nombre + "!",
                "Gracias por registrarte. <b>Verifica tu email</b> para activar tu cuenta y empezar a gestionar tu negocio.",
                link,
                "Verificar mi cuenta",
                "Este enlace expira en <b>24 horas</b>. Si no creaste esta cuenta, ignora este correo."
        );
        enviar(email, "Verifica tu cuenta — Fluxus", html);
    }

    @Override
    @Async
    public void enviarBienvenida(String email, String nombreEmpresa, String nombreUsuario) {
        log.info("📧 Enviando email de bienvenida a: {}", email);
        String html = buildHtml(
                "¡Bienvenido!",
                "¡Hola " + nombreUsuario + "! 🎉",
                "Tu empresa <b>" + nombreEmpresa + "</b> ya está activa en Fluxus.<br>"
                + "Ahora puedes gestionar tu inventario, ventas, compras y mucho más desde un solo lugar.",
                frontendUrl + "/dashboard",
                "Ir a mi panel",
                "Si tienes preguntas, responde a este correo y te ayudamos con gusto."
        );
        enviar(email, "¡Bienvenido a Fluxus, " + nombreEmpresa + "!", html);
    }

    // ── Bienvenida usuario nuevo ──────────────────────────────────────────────

    @Override
    @Async
    public void enviarBienvenidaUsuarioNuevo(String email, String nombre, String tenantId, String token) {
        log.info("📧 Enviando email de activación a nuevo usuario: {}", email);

        // Resolver nombre de empresa
        String empresaNombre = tenantRepository.findByTenantId(tenantId)
                .map(t -> t.getNombre() != null ? t.getNombre() : tenantId)
                .orElse(tenantId);

        String link = frontendUrl + "/activate?token=" + token;
        String html = buildHtml(
                "Activar cuenta",
                "¡Hola " + nombre + "! Te damos la bienvenida 👋",
                "El equipo de <b>" + empresaNombre + "</b> acaba de crearte una cuenta en Fluxus.<br>"
                + "Para comenzar, establece tu contraseña haciendo clic en el botón de abajo.",
                link,
                "Activar mi cuenta",
                "Este enlace expira en <b>48 horas</b>. Si no esperabas este correo, puedes ignorarlo."
        );
        enviar(email, "Activa tu cuenta en Fluxus — " + empresaNombre, html);
    }

    // ── Resumen cierre de caja ────────────────────────────────────────────────

    @Override
    @Async
    public void enviarResumenCierreCaja(String email, String empresaNombre, String usuarioCierre,
                                         BigDecimal montoApertura, BigDecimal totalEfectivo,
                                         BigDecimal totalTarjeta, BigDecimal totalYapePlin,
                                         BigDecimal totalIngresos, BigDecimal montoContado,
                                         BigDecimal diferencia, Integer cantidadVentas,
                                         BigDecimal totalRetiros, Integer cantidadRetiros) {
        log.info("📧 Enviando resumen de cierre de caja a: {}", email);

        String diferenciaTexto = diferencia == null ? "—"
                : (diferencia.compareTo(BigDecimal.ZERO) >= 0
                        ? "+" + String.format("S/ %.2f", diferencia)
                        : String.format("S/ %.2f", diferencia));
        String diferenciaColor = diferencia == null ? "#374151"
                : (diferencia.compareTo(BigDecimal.ZERO) >= 0 ? "#059669" : "#dc2626");

        String html = """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 16px;">
                <tr><td align="center">
                  <table width="580" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,.08);max-width:580px;">

                    <!-- HEADER -->
                    <tr>
                      <td style="background:#4f46e5;padding:24px 36px;">
                        <p style="margin:0;color:#fff;font-size:20px;font-weight:700;">🏦 %s</p>
                        <p style="margin:4px 0 0;color:#c7d2fe;font-size:12px;">Resumen de cierre de caja</p>
                      </td>
                    </tr>

                    <!-- CUERPO -->
                    <tr>
                      <td style="padding:28px 36px 8px;">
                        <p style="margin:0 0 6px;color:#374151;font-size:14px;">
                          Cerrada por: <b>%s</b>
                        </p>
                        <p style="margin:0;color:#6b7280;font-size:12px;">%s</p>
                      </td>
                    </tr>

                    <!-- DESGLOSE -->
                    <tr>
                      <td style="padding:16px 36px;">
                        <table width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                          <tr style="background:#f9fafb;">
                            <td style="padding:10px 16px;font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:.5px;">Concepto</td>
                            <td style="padding:10px 16px;font-size:12px;color:#6b7280;text-transform:uppercase;letter-spacing:.5px;text-align:right;">Monto</td>
                          </tr>
                          <tr>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;border-top:1px solid #e5e7eb;">Apertura de caja</td>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;text-align:right;border-top:1px solid #e5e7eb;">S/ %.2f</td>
                          </tr>
                          <tr>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;border-top:1px solid #e5e7eb;">Efectivo</td>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;text-align:right;border-top:1px solid #e5e7eb;">S/ %.2f</td>
                          </tr>
                          <tr>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;border-top:1px solid #e5e7eb;">Tarjeta</td>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;text-align:right;border-top:1px solid #e5e7eb;">S/ %.2f</td>
                          </tr>
                          <tr>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;border-top:1px solid #e5e7eb;">Yape / Plin</td>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;text-align:right;border-top:1px solid #e5e7eb;">S/ %.2f</td>
                          </tr>
                          <tr style="background:#f0fdf4;">
                            <td style="padding:12px 16px;font-size:14px;font-weight:700;color:#111827;border-top:2px solid #e5e7eb;">Total ingresos</td>
                            <td style="padding:12px 16px;font-size:14px;font-weight:700;color:#059669;text-align:right;border-top:2px solid #e5e7eb;">S/ %.2f</td>
                          </tr>
                          <tr>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;border-top:1px solid #e5e7eb;">Ventas realizadas</td>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;text-align:right;border-top:1px solid #e5e7eb;">%d</td>
                          </tr>
                          <tr>
                            <td style="padding:10px 16px;font-size:14px;color:#ea580c;border-top:1px solid #e5e7eb;">Retiros parciales (%d)</td>
                            <td style="padding:10px 16px;font-size:14px;color:#ea580c;text-align:right;border-top:1px solid #e5e7eb;">-S/ %.2f</td>
                          </tr>
                          <tr>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;border-top:1px solid #e5e7eb;">Monto contado</td>
                            <td style="padding:10px 16px;font-size:14px;color:#374151;text-align:right;border-top:1px solid #e5e7eb;">S/ %.2f</td>
                          </tr>
                          <tr style="background:#fafafa;">
                            <td style="padding:12px 16px;font-size:14px;font-weight:700;color:#111827;border-top:2px solid #e5e7eb;">Diferencia</td>
                            <td style="padding:12px 16px;font-size:14px;font-weight:700;text-align:right;border-top:2px solid #e5e7eb;color:%s;">%s</td>
                          </tr>
                        </table>
                      </td>
                    </tr>

                    <!-- FOOTER -->
                    <tr>
                      <td style="padding:16px 36px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center;">
                        <p style="margin:0;color:#9ca3af;font-size:11px;">%s &middot; Powered by Fluxus</p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(
                empresaNombre,
                usuarioCierre,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                montoApertura != null ? montoApertura : BigDecimal.ZERO,
                totalEfectivo != null ? totalEfectivo : BigDecimal.ZERO,
                totalTarjeta != null ? totalTarjeta : BigDecimal.ZERO,
                totalYapePlin != null ? totalYapePlin : BigDecimal.ZERO,
                totalIngresos != null ? totalIngresos : BigDecimal.ZERO,
                cantidadVentas != null ? cantidadVentas : 0,
                cantidadRetiros != null ? cantidadRetiros : 0,
                totalRetiros != null ? totalRetiros : BigDecimal.ZERO,
                montoContado != null ? montoContado : BigDecimal.ZERO,
                diferenciaColor, diferenciaTexto,
                empresaNombre
        );

        enviar(email, "Resumen de cierre de caja — " + empresaNombre, html);
    }

    // ── Email suscripción ─────────────────────────────────────────────────────

    @Override
    @Async
    public void enviarEmailSuscripcion(String email, String nombre, String estado, String planId) {
        log.info("📧 Enviando email de suscripción ({}) a: {}", estado, email);

        String plan = "PRO".equalsIgnoreCase(planId) ? "Pro" : "Básico";

        String tag, titulo, cuerpo, btnUrl, btnTexto, nota;

        switch (estado) {
            case "ACTIVA" -> {
                tag     = "Suscripción activada ✅";
                titulo  = "¡Tu suscripción está activa!";
                cuerpo  = "Hola <b>" + nombre + "</b>,<br><br>"
                        + "Tu pago fue procesado correctamente. Tu plan <b>" + plan + "</b> ya está activo.<br>"
                        + "Disfruta de todas las funcionalidades de Fluxus.";
                btnUrl  = frontendUrl + "/dashboard";
                btnTexto = "Ir a mi panel";
                nota    = "Si tienes preguntas sobre tu suscripción, responde a este correo.";
            }
            case "SUSPENDIDA" -> {
                tag     = "Pago rechazado ❌";
                titulo  = "No pudimos procesar tu pago";
                cuerpo  = "Hola <b>" + nombre + "</b>,<br><br>"
                        + "El pago de tu plan <b>" + plan + "</b> fue rechazado o no pudo procesarse.<br>"
                        + "Intenta con otro método de pago para mantener el acceso a Fluxus.";
                btnUrl  = frontendUrl + "/checkout/culqi";
                btnTexto = "Reintentar pago";
                nota    = "Si el problema persiste, contacta a tu banco o escríbenos.";
            }
            case "CANCELADA" -> {
                tag     = "Suscripción cancelada";
                titulo  = "Tu suscripción ha sido cancelada";
                cuerpo  = "Hola <b>" + nombre + "</b>,<br><br>"
                        + "Tu plan <b>" + plan + "</b> en Fluxus ha sido cancelado.<br>"
                        + "Puedes volver a suscribirte en cualquier momento.";
                btnUrl  = frontendUrl + "/checkout/culqi";
                btnTexto = "Reactivar suscripción";
                nota    = "¡Te esperamos de vuelta cuando quieras!";
            }
            case "DOWNGRADE_PROGRAMADO" -> {
                tag     = "Cambio de plan programado 📅";
                titulo  = "Tu cambio a plan Básico está programado";
                cuerpo  = "Hola <b>" + nombre + "</b>,<br><br>"
                        + "Confirmamos que programaste el cambio de tu plan <b>Pro</b> a <b>Básico</b>.<br>"
                        + "Seguirás con acceso completo al plan <b>Pro</b> hasta que venza tu período actual.<br>"
                        + "Al vencer, tu suscripción pasará automáticamente a plan <b>Básico</b>.";
                btnUrl  = frontendUrl + "/dashboard/suscripciones";
                btnTexto = "Ver mi suscripción";
                nota    = "¿Cambiaste de opinión? Puedes cancelar este cambio desde tu panel de suscripción antes de que venza el período.";
            }
            case "DOWNGRADE_EFECTUADO" -> {
                tag     = "Plan actualizado";
                titulo  = "Tu plan cambió a Básico";
                cuerpo  = "Hola <b>" + nombre + "</b>,<br><br>"
                        + "Tu período Pro venció y tu plan ahora es <b>Básico</b>.<br>"
                        + "Para continuar usando Fluxus activa tu plan Básico haciendo clic en el botón.";
                btnUrl  = frontendUrl + "/checkout/culqi";
                btnTexto = "Activar plan Básico";
                nota    = "¿Quieres volver a Pro? Puedes hacer el upgrade en cualquier momento desde tu panel.";
            }
            default -> {
                log.info("ℹ️ Estado '{}' no requiere email de suscripción", estado);
                return;
            }
        }

        String html = buildHtml(tag, titulo, cuerpo, btnUrl, btnTexto, nota);
        String asunto = switch (estado) {
            case "ACTIVA"               -> "✅ Suscripción activa — Plan " + plan + " · Fluxus";
            case "SUSPENDIDA"           -> "❌ Pago rechazado — Fluxus";
            case "CANCELADA"            -> "Tu suscripción fue cancelada — Fluxus";
            case "DOWNGRADE_PROGRAMADO" -> "📅 Cambio a plan Básico programado — Fluxus";
            case "DOWNGRADE_EFECTUADO"  -> "Tu plan cambió a Básico — Fluxus";
            default                     -> "Actualización de tu suscripción — Fluxus";
        };
        enviar(email, asunto, html);
    }

    // ── Trial por vencer ──────────────────────────────────────────────────────

    @Override
    @Async
    public void enviarEmailTrialPorVencer(String email, String nombre, int diasRestantes, LocalDate fechaVencimiento) {
        log.info("📧 Enviando aviso de trial por vencer ({} días) a: {}", diasRestantes, email);

        String diasTexto  = diasRestantes == 1 ? "mañana" : "en " + diasRestantes + " días";
        String fechaTexto = fechaVencimiento.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        String html = buildHtml(
                "Tu período de prueba está por vencer",
                "⏳ Tu prueba gratuita vence " + diasTexto,
                "Hola <b>" + nombre + "</b>,<br><br>"
                + "Tu período de prueba en Fluxus vence el <b>" + fechaTexto + "</b>.<br>"
                + "Suscríbete ahora para seguir gestionando tu negocio sin interrupciones: "
                + "inventario, ventas, compras y mucho más.",
                frontendUrl + "/dashboard/suscripciones",
                "Ver planes y suscribirme",
                "Si ya te suscribiste, ignora este correo. ¿Tienes dudas? Responde a este mensaje y te ayudamos."
        );

        String asunto = diasRestantes == 1
                ? "⏳ Tu prueba vence mañana — Fluxus"
                : "⏳ Tu prueba vence en " + diasRestantes + " días — Fluxus";

        enviar(email, asunto, html);
    }

    // ── Confirmación cambio de contraseña ─────────────────────────────────────

    @Override
    @Async
    public void enviarConfirmacionCambioContraseña(String email, String nombre) {
        log.info("📧 Enviando confirmación de cambio de contraseña a: {}", email);

        String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm"));

        String html = buildHtml(
                "Cambio de contraseña",
                "Tu contraseña fue cambiada",
                "Hola <b>" + nombre + "</b>,<br><br>"
                + "Te confirmamos que la contraseña de tu cuenta en Fluxus fue cambiada exitosamente el <b>" + fecha + "</b>.<br><br>"
                + "Si fuiste tú, no necesitas hacer nada más.<br>"
                + "Si <b>no reconoces este cambio</b>, restablece tu contraseña de inmediato.",
                frontendUrl + "/forgot-password",
                "No fui yo — restablecer contraseña",
                "Por tu seguridad, todos tus dispositivos fueron desconectados. Inicia sesión nuevamente."
        );

        enviar(email, "Cambio de contraseña en tu cuenta — Fluxus", html);
    }

    // ── OC al proveedor ───────────────────────────────────────────────────────

    @Override
    @Async
    @Transactional(readOnly = true)
    public void enviarOCAlProveedor(Long ocId, String tenantId) {
        // Cargamos todo fresco en este hilo async con su propia transacción
        OrdenCompra oc = ordenCompraRepository.findById(ocId).orElse(null);
        if (oc == null) {
            log.warn("⚠️ OC #{} no encontrada al intentar enviar email", ocId);
            return;
        }
        Tenant tenant = tenantRepository.findByTenantId(tenantId).orElse(null);
        if (tenant == null) {
            log.warn("⚠️ Tenant '{}' no encontrado al intentar enviar email de OC #{}", tenantId, ocId);
            return;
        }

        String emailProveedor = oc.getProveedor().getEmail();
        if (emailProveedor == null || emailProveedor.isBlank()) {
            log.warn("⚠️ Proveedor '{}' no tiene email — OC #{} no enviada por correo",
                    oc.getProveedor().getNombre(), oc.getId());
            return;
        }

        String nombreNegocio  = tenant.getNombre() != null ? tenant.getNombre() : "Fluxus";
        String rucNegocio     = tenant.getRuc() != null ? " · RUC " + tenant.getRuc() : "";
        String fechaEmision   = oc.getCreatedAt() != null
                ? oc.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "—";

        // ── Tabla de ítems ────────────────────────────────────────────────────
        List<OrdenCompraDetalle> detalles = oc.getDetalles() != null
                ? oc.getDetalles() : List.of();

        StringBuilder filas = new StringBuilder();
        BigDecimal total = BigDecimal.ZERO;
        for (OrdenCompraDetalle d : detalles) {
            String nombre  = d.getProducto() != null ? d.getProducto().getNombre() : "—";
            String codigo  = d.getProducto() != null && d.getProducto().getCodigoBarras() != null
                    ? d.getProducto().getCodigoBarras() : "—";
            int    cant    = d.getCantidadSolicitada();
            BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal sub = precio.multiply(BigDecimal.valueOf(cant));
            total = total.add(sub);

            filas.append(String.format("""
                <tr>
                  <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;color:#374151;">%s</td>
                  <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;color:#6b7280;text-align:center;">%s</td>
                  <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;text-align:center;">%d</td>
                  <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;text-align:right;">S/ %.2f</td>
                  <td style="padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;font-weight:600;text-align:right;">S/ %.2f</td>
                </tr>
                """, nombre, codigo, cant, precio, sub));
        }

        // ── HTML del email ────────────────────────────────────────────────────
        String html = """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 16px;">
                <tr><td align="center">
                  <table width="620" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,.08);max-width:620px;">

                    <!-- HEADER -->
                    <tr>
                      <td style="background:#4f46e5;padding:24px 36px;">
                        <p style="margin:0;color:#fff;font-size:20px;font-weight:700;">📦 %s</p>
                        <p style="margin:4px 0 0;color:#c7d2fe;font-size:12px;">Orden de Compra · OC #%d</p>
                      </td>
                    </tr>

                    <!-- INFO -->
                    <tr>
                      <td style="padding:24px 36px 12px;">
                        <p style="margin:0;color:#374151;font-size:14px;">Estimado/a <b>%s</b>,</p>
                        <p style="margin:10px 0 0;color:#4b5563;font-size:14px;line-height:1.6;">
                          Le enviamos la siguiente orden de compra para su revisión y atención.
                        </p>
                        <table style="margin-top:16px;width:100%%;" cellpadding="0" cellspacing="0">
                          <tr>
                            <td style="font-size:13px;color:#6b7280;">Fecha de emisión:</td>
                            <td style="font-size:13px;font-weight:600;color:#111827;padding-left:12px;">%s</td>
                            <td style="font-size:13px;color:#6b7280;padding-left:24px;">Estado:</td>
                            <td style="font-size:13px;font-weight:600;color:#4f46e5;padding-left:12px;">ENVIADA</td>
                          </tr>
                        </table>
                      </td>
                    </tr>

                    <!-- TABLA DE ÍTEMS -->
                    <tr>
                      <td style="padding:12px 36px 24px;">
                        <table width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                          <thead>
                            <tr style="background:#f9fafb;">
                              <th style="padding:10px 12px;text-align:left;font-size:11px;color:#6b7280;text-transform:uppercase;letter-spacing:.5px;">Producto</th>
                              <th style="padding:10px 12px;text-align:center;font-size:11px;color:#6b7280;text-transform:uppercase;letter-spacing:.5px;">Código</th>
                              <th style="padding:10px 12px;text-align:center;font-size:11px;color:#6b7280;text-transform:uppercase;letter-spacing:.5px;">Cant.</th>
                              <th style="padding:10px 12px;text-align:right;font-size:11px;color:#6b7280;text-transform:uppercase;letter-spacing:.5px;">P. Unit.</th>
                              <th style="padding:10px 12px;text-align:right;font-size:11px;color:#6b7280;text-transform:uppercase;letter-spacing:.5px;">Subtotal</th>
                            </tr>
                          </thead>
                          <tbody>
                            %s
                          </tbody>
                          <tfoot>
                            <tr style="background:#f9fafb;">
                              <td colspan="4" style="padding:12px;text-align:right;font-size:14px;font-weight:700;color:#111827;">Total estimado:</td>
                              <td style="padding:12px;text-align:right;font-size:14px;font-weight:700;color:#4f46e5;">S/ %.2f</td>
                            </tr>
                          </tfoot>
                        </table>

                        %s
                      </td>
                    </tr>

                    <!-- FOOTER -->
                    <tr>
                      <td style="padding:16px 36px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center;">
                        <p style="margin:0;color:#9ca3af;font-size:11px;">
                          %s%s &middot; Powered by Fluxus
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(
                nombreNegocio, oc.getId(),
                oc.getProveedor().getNombre(),
                fechaEmision,
                filas.toString(),
                total,
                oc.getObservaciones() != null && !oc.getObservaciones().isBlank()
                    ? "<p style=\"margin:16px 0 0;padding:12px 16px;background:#fafafa;border-left:3px solid #4f46e5;font-size:13px;color:#4b5563;\"><b>Observaciones:</b> " + oc.getObservaciones() + "</p>"
                    : "",
                nombreNegocio, rucNegocio
        );

        // Generar PDF adjunto
        byte[] pdfBytes = null;
        try {
            pdfBytes = ocPdfGenerator.generar(oc, tenant);
            log.info("📄 PDF de OC #{} generado ({} bytes)", oc.getId(), pdfBytes.length);
        } catch (Exception e) {
            log.warn("⚠️ No se pudo generar el PDF de OC #{}, se enviará solo el HTML: {}", oc.getId(), e.getMessage());
        }

        String nombreArchivo = String.format("OC-%05d.pdf", oc.getId());
        enviarConAdjunto(emailProveedor,
                "Orden de Compra #" + oc.getId() + " — " + nombreNegocio,
                html, pdfBytes, nombreArchivo);

        log.info("📧 OC #{} enviada por email a proveedor: {}", oc.getId(), emailProveedor);
    }

    // ── Libro de Reclamaciones ────────────────────────────────────────────────

    @Override
    @Async
    public void enviarReclamacion(String tipo, String nombre, String apellido,
                                   String dni, String correoConsumidor, String telefono,
                                   String pedido, String monto, String descripcion,
                                   List<MultipartFile> archivos) {

        String fecha      = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String nombreCompleto = nombre + " " + apellido;
        String CONTACTO   = "contacto@fluxus.pe";

        // ── 1. Email interno a Fluxus ─────────────────────────────────────────
        String htmlInterno = """
            <!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"></head>
            <body style="font-family:Arial,sans-serif;background:#f4f4f5;padding:24px;">
              <div style="max-width:560px;margin:auto;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                <div style="background:#dc2626;padding:20px 32px;">
                  <p style="margin:0;color:#fff;font-size:18px;font-weight:700;">📋 Libro de Reclamaciones</p>
                  <p style="margin:4px 0 0;color:#fecaca;font-size:12px;">%s recibido · %s</p>
                </div>
                <div style="padding:28px 32px;">
                  <table style="width:100%%;border-collapse:collapse;font-size:14px;">
                    <tr><td style="padding:6px 0;color:#6b7280;width:40%%;">Tipo</td><td style="padding:6px 0;font-weight:600;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#6b7280;">Nombre</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#6b7280;">DNI</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#6b7280;">Correo</td><td style="padding:6px 0;"><a href="mailto:%s">%s</a></td></tr>
                    <tr><td style="padding:6px 0;color:#6b7280;">Teléfono</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#6b7280;">Pedido/Suscripción</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#6b7280;">Monto (S/)</td><td style="padding:6px 0;">%s</td></tr>
                  </table>
                  <div style="margin-top:16px;background:#fef2f2;border:1px solid #fecaca;border-radius:8px;padding:14px;">
                    <p style="margin:0 0 6px;font-size:12px;font-weight:600;color:#991b1b;">Descripción:</p>
                    <p style="margin:0;font-size:14px;color:#374151;line-height:1.6;">%s</p>
                  </div>
                  <p style="margin:20px 0 0;font-size:12px;color:#9ca3af;">
                    Debes responder al consumidor en un plazo máximo de <strong>15 días hábiles</strong>.
                  </p>
                </div>
              </div>
            </body></html>
            """.formatted(tipo, fecha, tipo, nombreCompleto, dni,
                          correoConsumidor, correoConsumidor,
                          (telefono != null && !telefono.isBlank()) ? telefono : "—",
                          (pedido   != null && !pedido.isBlank())   ? pedido   : "—",
                          (monto    != null && !monto.isBlank())    ? "S/ " + monto : "—",
                          descripcion);

        // Convertir MultipartFiles a adjuntos Resend [{filename, content}]
        List<Map<String, Object>> adjuntos = new ArrayList<>();
        if (archivos != null) {
            for (MultipartFile f : archivos) {
                if (f != null && !f.isEmpty()) {
                    try {
                        adjuntos.add(Map.of(
                            "filename", f.getOriginalFilename() != null ? f.getOriginalFilename() : "adjunto",
                            "content",  Base64.getEncoder().encodeToString(f.getBytes())
                        ));
                    } catch (IOException ex) {
                        log.warn("⚠️ No se pudo leer adjunto '{}': {}", f.getOriginalFilename(), ex.getMessage());
                    }
                }
            }
        }

        log.info("📧 Enviando reclamación interna a: {} ({} adjuntos)", CONTACTO, adjuntos.size());
        enviarConAdjuntos(CONTACTO, "[Libro de Reclamaciones] " + tipo + " de " + nombreCompleto, htmlInterno, adjuntos);

        // ── 2. Acuse de recibo al consumidor ──────────────────────────────────
        String htmlAcuse = """
            <!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"></head>
            <body style="font-family:Arial,sans-serif;background:#f4f4f5;padding:24px;">
              <div style="max-width:560px;margin:auto;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08);">
                <div style="background:#2563eb;padding:20px 32px;">
                  <p style="margin:0;color:#fff;font-size:18px;font-weight:700;">📦 Fluxus</p>
                  <p style="margin:4px 0 0;color:#bfdbfe;font-size:12px;">Reclamación registrada ✅</p>
                </div>
                <div style="padding:28px 32px;">
                  <p style="font-size:15px;color:#111827;">Hola <strong>%s</strong>,</p>
                  <p style="font-size:14px;color:#4b5563;line-height:1.65;">
                    Hemos recibido tu <strong>%s</strong> el día <strong>%s</strong>.
                    Te responderemos en un plazo máximo de <strong>15 días hábiles</strong>
                    a este correo.
                  </p>
                  <div style="margin:20px 0;background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;padding:14px;font-size:13px;color:#6b7280;">
                    <p style="margin:0 0 4px;"><strong>Resumen de tu %s:</strong></p>
                    <p style="margin:0;">%s</p>
                  </div>
                  <p style="font-size:13px;color:#6b7280;">
                    Si tienes dudas, responde este correo o escríbenos a
                    <a href="mailto:%s" style="color:#2563eb;">%s</a>.
                  </p>
                </div>
                <div style="padding:14px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center;">
                  <p style="margin:0;font-size:11px;color:#9ca3af;">
                    © %d Joaquin Castillo Tello (Fluxus) · RUC 10769109566 · Magdalena del Mar, Lima
                  </p>
                </div>
              </div>
            </body></html>
            """.formatted(nombre, tipo.toLowerCase(), fecha,
                          tipo.toLowerCase(),
                          descripcion.length() > 120 ? descripcion.substring(0, 120) + "…" : descripcion,
                          CONTACTO, CONTACTO,
                          java.time.LocalDate.now().getYear());

        log.info("📧 Enviando acuse de reclamación al consumidor: {}", correoConsumidor);
        enviar(correoConsumidor, "Hemos recibido tu " + tipo.toLowerCase() + " — Fluxus", htmlAcuse);
    }

    // ── Envío HTTP a Resend ───────────────────────────────────────────────────

    /** Envío simple sin adjunto */
    private void enviar(String destinatario, String asunto, String html) {
        enviarConAdjunto(destinatario, asunto, html, null, null);
    }

    /** Envío con lista de adjuntos ya codificados en Base64 [{filename, content}] */
    private void enviarConAdjuntos(String destinatario, String asunto, String html,
                                    List<Map<String, Object>> adjuntos) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("from",    fromName + " <" + fromEmail + ">");
            body.put("to",      List.of(destinatario));
            body.put("subject", asunto);
            body.put("html",    html);
            if (adjuntos != null && !adjuntos.isEmpty()) {
                body.put("attachments", adjuntos);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(RESEND_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Email enviado correctamente a: {}", destinatario);
            } else {
                log.error("❌ Resend rechazó el email. Status: {} | Body: {}",
                        response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ Error al enviar email a {}: {}", destinatario, e.getMessage(), e);
        }
    }

    /** Envío con adjunto PDF opcional (pdfBytes == null → sin adjunto) */
    private void enviarConAdjunto(String destinatario, String asunto, String html,
                                   byte[] pdfBytes, String nombreArchivo) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("from",    fromName + " <" + fromEmail + ">");
            body.put("to",      List.of(destinatario));
            body.put("subject", asunto);
            body.put("html",    html);

            if (pdfBytes != null && pdfBytes.length > 0) {
                body.put("attachments", List.of(Map.of(
                        "filename", nombreArchivo,
                        "content",  Base64.getEncoder().encodeToString(pdfBytes)
                )));
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(RESEND_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Email enviado correctamente a: {}", destinatario);
            } else {
                log.error("❌ Resend rechazó el email. Status: {} | Body: {}",
                        response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            // No se lanza excepción para no romper el flujo principal de la app
            log.error("❌ Error al enviar email a {}: {}", destinatario, e.getMessage(), e);
        }
    }

    // ── Template HTML base ────────────────────────────────────────────────────

    /**
     * Genera un email HTML profesional con header azul, cuerpo, botón CTA y footer.
     *
     * @param tag      Subtítulo del header (ej. "Recuperar contraseña")
     * @param titulo   Título principal del cuerpo
     * @param cuerpo   Párrafo principal (permite HTML básico como <b>)
     * @param btnUrl   URL del botón de acción
     * @param btnTexto Texto del botón
     * @param nota     Nota al pie dentro del card (expiraciones, avisos)
     */
    private String buildHtml(String tag, String titulo, String cuerpo,
                              String btnUrl, String btnTexto, String nota) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Fluxus</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 16px;">
                    <tr><td align="center">
                      <table width="580" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);max-width:580px;">

                        <!-- HEADER -->
                        <tr>
                          <td style="background:#2563eb;padding:28px 40px;">
                            <p style="margin:0;color:#ffffff;font-size:20px;font-weight:700;letter-spacing:-0.3px;">📦 Fluxus</p>
                            <p style="margin:4px 0 0;color:#bfdbfe;font-size:12px;text-transform:uppercase;letter-spacing:0.8px;">%s</p>
                          </td>
                        </tr>

                        <!-- CUERPO -->
                        <tr>
                          <td style="padding:36px 40px 28px;">
                            <h2 style="margin:0 0 14px;color:#111827;font-size:20px;font-weight:600;">%s</h2>
                            <p style="margin:0 0 28px;color:#4b5563;font-size:15px;line-height:1.65;">%s</p>

                            <!-- BOTÓN -->
                            <table cellpadding="0" cellspacing="0">
                              <tr>
                                <td style="border-radius:8px;background:#2563eb;">
                                  <a href="%s"
                                     style="display:inline-block;padding:13px 28px;color:#ffffff;font-size:15px;font-weight:600;text-decoration:none;border-radius:8px;">
                                    %s &rarr;
                                  </a>
                                </td>
                              </tr>
                            </table>

                            <!-- LINK PLANO -->
                            <p style="margin:22px 0 0;color:#9ca3af;font-size:12px;line-height:1.5;">
                              Si el botón no funciona, copia y pega este enlace en tu navegador:<br>
                              <a href="%s" style="color:#2563eb;word-break:break-all;font-size:11px;">%s</a>
                            </p>
                          </td>
                        </tr>

                        <!-- NOTA -->
                        <tr>
                          <td style="padding:16px 40px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                            <p style="margin:0;color:#6b7280;font-size:12px;line-height:1.6;">%s</p>
                          </td>
                        </tr>

                        <!-- FOOTER -->
                        <tr>
                          <td style="padding:16px 40px;border-top:1px solid #e5e7eb;text-align:center;">
                            <p style="margin:0;color:#9ca3af;font-size:11px;">
                              © 2026 Fluxus &middot; Todos los derechos reservados.
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(tag, titulo, cuerpo, btnUrl, btnTexto, btnUrl, btnUrl, nota);
    }
}
