package com.stockflow.service.impl;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Personalization;
import com.stockflow.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final SendGrid sendGrid;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Value("${sendgrid.from-name}")
    private String fromName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void enviarEmailRecuperacionContraseña(String email, String nombre, String token) {
        log.info("📧 Enviando email de recuperación a: {}", email);
        log.info("📤 From: {} <{}>", fromName, fromEmail);

        String resetLink = String.format("%s/reset-password?token=%s", frontendUrl, token);

        String htmlContent = String.format("""
            <html>
                <body style="font-family: Arial, sans-serif; background-color: #f5f5f5;">
                    <div style="max-width: 600px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px;">
                        <h2 style="color: #333;">Hola %s,</h2>
                        <p style="color: #666;">Recibimos una solicitud para recuperar tu contraseña en StockFlow.</p>
                        
                        <p style="color: #666;">Haz click en el siguiente botón para crear una nueva contraseña:</p>
                        
                        <a href="%s" style="display: inline-block; background-color: #3b82f6; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; margin: 20px 0;">
                            Recuperar Contraseña
                        </a>
                        
                        <p style="color: #999; font-size: 12px;">O copia este link en tu navegador:</p>
                        <p style="color: #999; font-size: 12px; word-break: break-all;">%s</p>
                        
                        <hr style="border: none; border-top: 1px solid #ddd; margin: 20px 0;">
                        
                        <p style="color: #999; font-size: 12px;">Este link expira en 1 hora.</p>
                        <p style="color: #999; font-size: 12px;">Si no solicitaste recuperar tu contraseña, ignora este email.</p>
                        
                        <footer style="margin-top: 20px; padding-top: 20px; border-top: 1px solid #ddd; text-align: center; color: #999; font-size: 12px;">
                            <p>© 2026 StockFlow. Todos los derechos reservados.</p>
                        </footer>
                    </div>
                </body>
            </html>
            """, nombre, resetLink, resetLink);

        try {
            Mail mail = new Mail();
            mail.setFrom(new Email(fromEmail, fromName));

            Personalization personalization = new Personalization();
            personalization.addTo(new Email(email));
            personalization.setSubject("Recupera tu contraseña de StockFlow");
            mail.addPersonalization(personalization);

            mail.addContent(new Content("text/html", htmlContent));

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            log.info("📨 Enviando request a SendGrid...");
            log.info("📦 Payload: {}", mail.build());

            com.sendgrid.Response response = sendGrid.api(request);

            log.info("📬 Status Code SendGrid: {}", response.getStatusCode());
            log.info("📬 Headers: {}", response.getHeaders());
            log.info("📬 Body: {}", response.getBody());

            if (response.getStatusCode() == 202) {
                log.info("✅ Email de recuperación enviado exitosamente a: {}", email);
            } else {
                log.error("❌ Error enviando email. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ Error al enviar email de recuperación: {}", e.getMessage(), e);
            throw new RuntimeException("Error enviando email de recuperación");
        }
    }

    @Override
    public void enviarEmailVerificacion(String email, String nombre, String token) {
        log.info("📧 Enviando email de verificación a: {}", email);

        String verifyLink = String.format("%s/verify-email?token=%s", frontendUrl, token);

        String htmlContent = String.format("""
                <html>
                    <body style="font-family: Arial, sans-serif; background-color: #f5f5f5;">
                        <div style="max-width: 600px; margin: 0 auto; background-color: white; padding: 20px; border-radius: 8px;">
                            <h2 style="color: #333;">Hola %s,</h2>
                            <p style="color: #666;">¡Bienvenido a StockFlow! Verifica tu email para completar tu registro.</p>
                            
                            <a href="%s" style="display: inline-block; background-color: #10b981; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; margin: 20px 0;">
                                Verificar Email
                            </a>
                            
                            <p style="color: #999; font-size: 12px;">O copia este link:</p>
                            <p style="color: #999; font-size: 12px; word-break: break-all;">%s</p>
                            
                            <hr style="border: none; border-top: 1px solid #ddd; margin: 20px 0;">
                            
                            <p style="color: #999; font-size: 12px;">Este link expira en 24 horas.</p>
                            
                            <footer style="margin-top: 20px; padding-top: 20px; border-top: 1px solid #ddd; text-align: center; color: #999; font-size: 12px;">
                                <p>© 2026 StockFlow. Todos los derechos reservados.</p>
                            </footer>
                        </div>
                    </body>
                </html>
                """, nombre, verifyLink, verifyLink);

        try {
            Mail mail = new Mail();
            mail.setFrom(new Email(fromEmail, fromName));

            Personalization personalization = new Personalization();
            personalization.addTo(new Email(email));
            personalization.setSubject("Verifica tu email en StockFlow");
            mail.addPersonalization(personalization);

            mail.addContent(new Content("text/html", htmlContent));

            // ✅ CORREGIDO: Usar Request e Method correctos
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            com.sendgrid.Response response = sendGrid.api(request);

            if (response.getStatusCode() == 202) {
                log.info("✅ Email de verificación enviado exitosamente a: {}", email);
            } else {
                log.error("❌ Error enviando email: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ Error al enviar email de verificación: {}", e.getMessage(), e);
            throw new RuntimeException("Error enviando email de verificación");
        }
    }
}