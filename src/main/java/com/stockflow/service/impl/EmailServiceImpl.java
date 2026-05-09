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

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
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

    // ── Envío HTTP a Resend ───────────────────────────────────────────────────

    /** Envío simple sin adjunto */
    private void enviar(String destinatario, String asunto, String html) {
        enviarConAdjunto(destinatario, asunto, html, null, null);
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
