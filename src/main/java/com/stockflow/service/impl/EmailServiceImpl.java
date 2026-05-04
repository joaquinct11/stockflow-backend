package com.stockflow.service.impl;

import com.stockflow.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
public class EmailServiceImpl implements EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate = new RestTemplate();

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

    // ── Envío HTTP a Resend ───────────────────────────────────────────────────

    private void enviar(String destinatario, String asunto, String html) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "from",    fromName + " <" + fromEmail + ">",
                    "to",      List.of(destinatario),
                    "subject", asunto,
                    "html",    html
            );

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
