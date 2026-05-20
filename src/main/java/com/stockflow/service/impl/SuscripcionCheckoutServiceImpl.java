package com.stockflow.service.impl;

import com.stockflow.dto.MercadoPagoWebhookRequestDTO;
import com.stockflow.dto.SuscripcionCheckoutResponseDTO;
import com.stockflow.dto.SuscripcionEstadoResponseDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.WebhookLog;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.WebhookLogRepository;
import com.stockflow.service.MercadoPagoService;
import com.stockflow.service.SuscripcionCheckoutService;
import com.stockflow.service.UsuarioService;
import com.stockflow.service.model.MercadoPagoAuthorizedPaymentInfo;
import com.stockflow.service.model.MercadoPagoPaymentInfo;
import com.stockflow.service.model.MercadoPagoPreapprovalInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuscripcionCheckoutServiceImpl implements SuscripcionCheckoutService {

    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioService usuarioService;
    private final MercadoPagoService mercadoPagoService;
    private final WebhookLogRepository webhookLogRepository;
    private final com.stockflow.service.EmailService emailService;
    private final com.stockflow.config.properties.MercadoPagoProperties mercadoPagoProperties;

    @Override
    @Transactional
    public SuscripcionCheckoutResponseDTO iniciarCheckout(String planId, String tenantId, Long usuarioId,
                                                          String payerIdentificationType, String payerIdentificationNumber) {
        // Validar que el plan sea válido
        if (!"BASICO".equals(planId)) {
            throw new BadRequestException("Plan inválido. Solo se permite: BASICO");
        }

        BigDecimal precioPlan = mercadoPagoProperties.getPrecioBasico();
        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!tenantId.equals(usuario.getTenantId())) {
            throw new BadRequestException("Usuario no pertenece al tenant actual");
        }

        // Validar suscripción existente
        Optional<Suscripcion> suscripcionExistente = suscripcionRepository
                .findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId);

        if (suscripcionExistente.isPresent()) {
            Suscripcion existente = suscripcionExistente.get();
            String estadoExistente = existente.getEstado();

            // Bloquear si ya está activa (mismo plan o diferente)
            if ("ACTIVA".equals(estadoExistente)) {
                throw new BadRequestException(
                        "Ya tiene una suscripción activa del plan " + existente.getPlanId()
                        + ". Cancele primero su plan actual para suscribirse a otro.");
            }

            // Si ya está PENDIENTE, cancelar el preapproval anterior en MP antes de crear uno nuevo
            // (evita acumulación de preapprovals huérfanos que cobrarían al usuario)
            if ("PENDIENTE".equals(estadoExistente) && existente.getPreapprovalId() != null
                    && !existente.getPreapprovalId().isBlank()) {
                log.info("🔄 Cancelando preapproval anterior huérfano {} para tenant={} antes de crear nuevo",
                        existente.getPreapprovalId(), tenantId);
                cancelarPreapprovalEnMP(existente);
                // Limpiar también la preferencia de prorrateo si había un upgrade pendiente
                existente.setMpPreferenceId(null);
            }
        }

        // Persistir identificación si se envió
        boolean tieneIdentificacion = payerIdentificationType != null && !payerIdentificationType.isBlank()
                && payerIdentificationNumber != null && !payerIdentificationNumber.isBlank();

        if (tieneIdentificacion) {
            // Validar formato de documento
            validarDocumento(payerIdentificationType, payerIdentificationNumber);

            usuario.setTipoDocumento(payerIdentificationType);
            usuario.setNumeroDocumento(payerIdentificationNumber);
            usuarioService.guardarUsuario(usuario);
            log.info("💾 Identificación actualizada para usuario={}: tipo={}", usuarioId, payerIdentificationType);
        }

        String tipoDoc = usuario.getTipoDocumento();
        String numDoc  = usuario.getNumeroDocumento();

        String externalReference = tenantId + ":" + usuarioId;

        MercadoPagoPreapprovalInfo preapproval;
        try {
            preapproval = mercadoPagoService.crearPreapproval(
                    planId, precioPlan, externalReference, usuario.getEmail(), tipoDoc, numDoc);
        } catch (Exception e) {
            log.error("❌ Error creando preapproval en MP para tenant={}, usuario={}", tenantId, usuarioId, e);
            throw new BadRequestException("No se pudo iniciar el checkout. Intente nuevamente.");
        }

        Suscripcion suscripcion = suscripcionExistente.orElseGet(() -> Suscripcion.builder()
                .usuarioPrincipal(usuario)
                .tenantId(tenantId)
                .build());

        // Si el trial sigue vigente Y nunca hubo un pago confirmado, NO bloquear al usuario.
        // Cubre dos casos:
        //   1. estado=TRIAL  → primera vez que hace checkout
        //   2. estado=PENDIENTE + trialEndDate vigente + sin mpPaymentId
        //      → hizo checkout antes, retrocedió sin pagar, lo vuelve a intentar
        // En ambos casos solo guardamos el preapprovalId y esperamos el webhook de MP.
        boolean enTrialVigente = suscripcion.getTrialEndDate() != null
                && suscripcion.getTrialEndDate().isAfter(LocalDateTime.now())
                && (suscripcion.getMpPaymentId() == null
                    || suscripcion.getMpPaymentId().isBlank());

        suscripcion.setPlanId(planId);
        suscripcion.setPrecioMensual(precioPlan);
        suscripcion.setMetodoPago("MERCADOPAGO");
        if (enTrialVigente) {
            // Restaurar a TRIAL para que el usuario siga con acceso mientras no pague
            suscripcion.setEstado("TRIAL");
        } else {
            // Trial vencido o ya hubo un pago: bloquear hasta que MP confirme
            suscripcion.setEstado("PENDIENTE");
        }
        suscripcion.setPreapprovalId(preapproval.getPreapprovalId());

        suscripcionRepository.save(suscripcion);

        log.info("✅ Preapproval creado para tenant={}, usuario={}, preapprovalId={}",
                tenantId, usuarioId, preapproval.getPreapprovalId());

        return SuscripcionCheckoutResponseDTO.builder()
                .initPoint(preapproval.getInitPoint())
                .preapprovalId(preapproval.getPreapprovalId())
                .build();
    }

    @Override
    @Transactional
    public void procesarWebhook(MercadoPagoWebhookRequestDTO webhookRequestDTO) {
        if (webhookRequestDTO == null || webhookRequestDTO.getData() == null ||
                webhookRequestDTO.getData().getId() == null) {
            log.info("ℹ️ Webhook MP ignorado por payload incompleto");
            return;
        }

        String webhookId = webhookRequestDTO.getData().getId();
        String tipo = webhookRequestDTO.getType() != null ?
                webhookRequestDTO.getType() : webhookRequestDTO.getTopic();
        String action = webhookRequestDTO.getAction() != null ? webhookRequestDTO.getAction() : "";

        // Incluir la acción en la clave para diferenciar created/updated del mismo recurso
        String webhookKey = webhookId + (action.isBlank() ? "" : ":" + action);

        // IDEMPOTENCIA: Verificar si ya procesamos este webhook exacto
        if (webhookYaProcesado(webhookKey, tipo)) {
            log.info("ℹ️ Webhook {} tipo {} action={} ya fue procesado anteriormente", webhookId, tipo, action);
            return;
        }

        // Registrar webhook
        registrarWebhook(webhookKey, tipo, "PROCESANDO");

        try {
            if ("subscription_preapproval".equalsIgnoreCase(tipo) ||
                    "preapproval".equalsIgnoreCase(webhookRequestDTO.getEntity())) {
                procesarWebhookPreapproval(webhookId);
            } else if ("subscription_authorized_payment".equalsIgnoreCase(tipo)) {
                procesarWebhookAuthorizedPayment(webhookId);
            } else if ("payment".equalsIgnoreCase(tipo)) {
                procesarWebhookPago(webhookId);
            } else {
                log.info("ℹ️ Webhook ignorado (tipo no soportado): {}", tipo);
                registrarWebhook(webhookKey, tipo, "IGNORADO");
                return;
            }

            // Marcar como procesado exitosamente
            actualizarEstadoWebhook(webhookKey, tipo, "PROCESADO");

        } catch (Exception e) {
            log.error("❌ Error procesando webhook {} tipo {} action={}", webhookId, tipo, action, e);
            actualizarEstadoWebhook(webhookKey, tipo, "ERROR");
            throw e; // Re-lanzar para que MP reintente
        }
    }

    // NUEVO: Validar documento
    private void validarDocumento(String tipo, String numero) {
        if (numero == null || numero.isBlank()) {
            throw new BadRequestException("Número de documento es requerido");
        }

        switch (tipo) {
            case "DNI":
                if (!numero.matches("\\d{8}")) {
                    throw new BadRequestException("DNI debe tener 8 dígitos");
                }
                break;
            case "CE":
                if (!numero.matches("\\d{9}")) {
                    throw new BadRequestException("Carnet de Extranjería debe tener 9 dígitos");
                }
                break;
            case "RUC":
                if (!numero.matches("\\d{11}")) {
                    throw new BadRequestException("RUC debe tener 11 dígitos");
                }
                break;
            default:
                // Otros tipos: permitir formato flexible
                if (numero.length() < 6 || numero.length() > 20) {
                    throw new BadRequestException("Documento debe tener entre 6 y 20 caracteres");
                }
        }
    }

    @Override
    @Transactional
    public void cancelarSuscripcion(Long suscripcionId) {
        Suscripcion suscripcion = suscripcionRepository.findById(suscripcionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

        if ("CANCELADA".equals(suscripcion.getEstado())) {
            throw new BadRequestException("La suscripción ya está cancelada");
        }

        cancelarPreapprovalEnMP(suscripcion);

        suscripcion.setEstado("CANCELADA");
        suscripcion.setFechaCancelacion(LocalDateTime.now());
        suscripcionRepository.save(suscripcion);
        enviarEmailSuscripcionSiAplica(suscripcion, "CANCELADA");
        log.info("✅ Suscripción {} cancelada para tenant={}", suscripcionId, suscripcion.getTenantId());
    }

    @Override
    @Transactional
    public void cancelarMiSuscripcion(String tenantId, Long usuarioId) {
        Suscripcion suscripcion = suscripcionRepository
                .findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe suscripción para este usuario"));

        if ("CANCELADA".equals(suscripcion.getEstado())) {
            throw new BadRequestException("La suscripción ya está cancelada");
        }

        cancelarPreapprovalEnMP(suscripcion);

        suscripcion.setEstado("CANCELADA");
        suscripcion.setFechaCancelacion(LocalDateTime.now());
        suscripcionRepository.save(suscripcion);
        enviarEmailSuscripcionSiAplica(suscripcion, "CANCELADA");
        log.info("✅ Suscripción cancelada para tenant={}, usuario={}", tenantId, usuarioId);
    }

    @Override
    @Transactional
    public SuscripcionEstadoResponseDTO sincronizarDesdeMP(String tenantId, Long usuarioId) {
        Suscripcion suscripcion = suscripcionRepository
                .findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe suscripción para este usuario"));

        if (suscripcion.getPreapprovalId() == null || suscripcion.getPreapprovalId().isBlank()) {
            throw new BadRequestException("La suscripción no tiene preapproval_id para sincronizar");
        }

        MercadoPagoPreapprovalInfo preapproval = mercadoPagoService.obtenerPreapproval(suscripcion.getPreapprovalId());
        String nuevoEstado = mapearEstadoPreapproval(preapproval.getStatus());

        // ── Si la suscripción ya está ACTIVA y el preapproval sigue "pending" ────
        //    Ocurre en upgrades donde el pago del prorrateo activó la suscripción
        //    pero el usuario aún no autorizó el preapproval PRO.
        //
        //    • Período vigente  → mantener ACTIVA sin degradar (el usuario todavía
        //      tiene tiempo de autorizar el preapproval y el cobro recurrente).
        //    • Período vencido  → el cobro del siguiente mes nunca ocurrió porque
        //      el preapproval nunca fue autorizado → SUSPENDER.
        if ("PENDIENTE".equals(nuevoEstado) && "ACTIVA".equals(suscripcion.getEstado())) {
            LocalDateTime ahora = LocalDateTime.now();
            boolean periodoVigente = suscripcion.getCurrentPeriodEnd() == null
                    || suscripcion.getCurrentPeriodEnd().isAfter(ahora);

            if (periodoVigente) {
                log.info("ℹ️ Preapproval pending pero suscripción ACTIVA con período vigente hasta {} — manteniendo estado",
                        suscripcion.getCurrentPeriodEnd());
                return buildEstadoResponse(suscripcion);
            }

            // Período vencido y preapproval aún pending → no hubo cobro mensual
            // El preapproval PRO nunca fue autorizado por el usuario
            log.warn("⚠️ Período vencido ({}) y preapproval aún pending — suspendiendo plan={} tenant={}",
                    suscripcion.getCurrentPeriodEnd(), suscripcion.getPlanId(), tenantId);
            nuevoEstado = "SUSPENDIDA";
            // Continúa al bloque save() más abajo
        }

        // Si el preapproval está "pending" pero el trial sigue vigente y nunca hubo
        // un pago confirmado: NO degradar al usuario — mantener TRIAL con acceso completo.
        // Esto cubre el caso: usuario hace checkout, va a MP, retrocede sin pagar → sincronizar
        // no debe bloquearlo porque su trial aún no expiró.
        if ("PENDIENTE".equals(nuevoEstado)
                && suscripcion.getTrialEndDate() != null
                && suscripcion.getTrialEndDate().isAfter(LocalDateTime.now())
                && (suscripcion.getMpPaymentId() == null || suscripcion.getMpPaymentId().isBlank())) {
            log.info("ℹ️ Preapproval pending pero trial vigente hasta {} — manteniendo TRIAL para tenant={}",
                    suscripcion.getTrialEndDate(), tenantId);
            suscripcion.setEstado("TRIAL");
            suscripcionRepository.save(suscripcion);
            return buildEstadoResponse(suscripcion);
        }

        String estadoAnterior = suscripcion.getEstado();
        suscripcion.setEstado(nuevoEstado);

        if ("ACTIVA".equals(nuevoEstado)) {
            // Solo actualizar fechas de período si es una NUEVA activación (no si ya estaba ACTIVA).
            // Si ya está ACTIVA con período vigente, no resetear las fechas — evita que el usuario
            // extienda su suscripción gratis llamando a sincronizar repetidas veces.
            if (!"ACTIVA".equals(estadoAnterior)) {
                LocalDateTime now = LocalDateTime.now();
                suscripcion.setCurrentPeriodStart(now);
                suscripcion.setCurrentPeriodEnd(now.plusMonths(1));
                if (suscripcion.getFechaInicio() == null) {
                    suscripcion.setFechaInicio(now);
                }
                suscripcion.setFechaProximoCobro(now.plusMonths(1));
                suscripcion.setEnPeriodoPrueba(false);
                log.info("✅ Nueva activación detectada por sync: período establecido hasta {}",
                        suscripcion.getCurrentPeriodEnd());
            } else {
                log.info("ℹ️ Suscripción ya ACTIVA — fechas de período conservadas (no se resetean)");
            }
        } else if ("CANCELADA".equals(nuevoEstado) && suscripcion.getFechaCancelacion() == null) {
            suscripcion.setFechaCancelacion(LocalDateTime.now());
        }

        suscripcionRepository.save(suscripcion);
        log.info("🔄 Suscripción sincronizada desde MP para tenant={}, usuario={}: estado MP={} → local={}",
                tenantId, usuarioId, preapproval.getStatus(), nuevoEstado);

        return buildEstadoResponse(suscripcion);
    }

    /**
     * Cancela el preapproval en Mercado Pago.
     *
     * IMPORTANTE: Si la cancelación en MP falla, se lanza excepción para detener
     * el flujo. Esto evita el caso crítico donde la suscripción queda CANCELADA
     * localmente pero MP sigue cobrando mensualmente al usuario.
     *
     * Única excepción: si el preapproval ya estaba cancelado/no existe en MP
     * (error 404), se considera éxito porque el objetivo (no cobrar) ya se cumplió.
     */
    private void cancelarPreapprovalEnMP(Suscripcion suscripcion) {
        if (suscripcion.getPreapprovalId() == null || suscripcion.getPreapprovalId().isBlank()) {
            return;
        }
        try {
            mercadoPagoService.cancelarPreapproval(suscripcion.getPreapprovalId());
            log.info("✅ Preapproval {} cancelado en MP", suscripcion.getPreapprovalId());
        } catch (BadRequestException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            // Si MP responde 404, el preapproval ya no existe → ok, no hay riesgo de cobro
            if (msg.contains("404")) {
                log.warn("⚠️ Preapproval {} no encontrado en MP (ya cancelado o inexistente) — continuando",
                        suscripcion.getPreapprovalId());
                return;
            }
            // Cualquier otro error de MP: NO continuar con la cancelación local.
            // El usuario podría seguir siendo cobrado si ignoramos este error.
            log.error("❌ Error cancelando preapproval {} en MP — cancelación local BLOQUEADA para proteger al usuario",
                    suscripcion.getPreapprovalId(), e);
            throw new BadRequestException(
                    "No se pudo cancelar la suscripción en Mercado Pago. " +
                    "Intente nuevamente o contacte a soporte. Detalle: " + msg);
        } catch (Exception e) {
            log.error("❌ Error inesperado cancelando preapproval {} en MP", suscripcion.getPreapprovalId(), e);
            throw new BadRequestException(
                    "Error de comunicación con Mercado Pago al cancelar. Intente nuevamente.");
        }
    }

    private boolean webhookYaProcesado(String webhookId, String tipo) {
        return webhookLogRepository.findByWebhookIdAndTipo(webhookId, tipo)
                .map(wl -> "PROCESADO".equals(wl.getEstado()))
                .orElse(false);
    }

    private void registrarWebhook(String webhookId, String tipo, String estado) {
        WebhookLog webhookLog = webhookLogRepository.findByWebhookIdAndTipo(webhookId, tipo)
                .orElseGet(() -> WebhookLog.builder()
                        .webhookId(webhookId)
                        .tipo(tipo)
                        .build());
        webhookLog.setEstado(estado);
        webhookLog.setFechaProcesamiento(LocalDateTime.now());
        webhookLogRepository.save(webhookLog);
    }

    private void actualizarEstadoWebhook(String webhookId, String tipo, String estado) {
        registrarWebhook(webhookId, tipo, estado);
    }

    private void procesarWebhookPreapproval(String preapprovalId) {
        log.info("🔔 Procesando webhook preapproval: {}", preapprovalId);
        MercadoPagoPreapprovalInfo preapproval = mercadoPagoService.obtenerPreapproval(preapprovalId);

        Optional<Suscripcion> optSuscripcion = resolverSuscripcionPorPreapproval(preapproval);
        if (optSuscripcion.isEmpty()) {
            log.warn("⚠️ Webhook preapproval ignorado: no existe suscripción local para preapproval_id={}", preapprovalId);
            return;
        }
        Suscripcion suscripcion = optSuscripcion.get();

        String estadoMp = preapproval.getStatus();
        String nuevoEstado = mapearEstadoPreapproval(estadoMp);
        String estadoAnteriorPreapproval = suscripcion.getEstado();

        suscripcion.setEstado(nuevoEstado);
        suscripcion.setPreapprovalId(preapprovalId);

        if ("ACTIVA".equals(nuevoEstado)) {
            // Solo actualizar fechas si es una NUEVA activación.
            // Si ya estaba ACTIVA (ej. prorrateo del upgrade ya la activó), conservar las fechas
            // para no extender el período gratis ni generar inconsistencias de facturación.
            if (!"ACTIVA".equals(estadoAnteriorPreapproval)) {
                LocalDateTime now = LocalDateTime.now();
                suscripcion.setCurrentPeriodStart(now);
                suscripcion.setCurrentPeriodEnd(now.plusMonths(1));
                if (suscripcion.getFechaInicio() == null) {
                    suscripcion.setFechaInicio(now);
                }
                suscripcion.setFechaProximoCobro(now.plusMonths(1));
                suscripcion.setEnPeriodoPrueba(false);
                log.info("✅ Suscripción {} activada por preapproval webhook MP (status={})", suscripcion.getId(), estadoMp);
            } else {
                log.info("ℹ️ Preapproval webhook MP: suscripción {} ya estaba ACTIVA — fechas conservadas (status={})",
                        suscripcion.getId(), estadoMp);
            }
        } else if ("CANCELADA".equals(nuevoEstado)) {
            suscripcion.setFechaCancelacion(LocalDateTime.now());
            log.info("❌ Suscripción {} cancelada por preapproval webhook MP (status={})", suscripcion.getId(), estadoMp);
        } else {
            log.info("ℹ️ Suscripción {} actualizada a {} por preapproval webhook MP (status={})", suscripcion.getId(), nuevoEstado, estadoMp);
        }

        suscripcionRepository.save(suscripcion);
        enviarEmailSuscripcionSiAplica(suscripcion, nuevoEstado);
    }

    private void procesarWebhookAuthorizedPayment(String authorizedPaymentId) {
        log.info("🔔 Procesando webhook subscription_authorized_payment: {}", authorizedPaymentId);
        MercadoPagoAuthorizedPaymentInfo authPayment = mercadoPagoService.obtenerAuthorizedPayment(authorizedPaymentId);

        // Resolver suscripción por preapproval_id (campo incluido en la respuesta de authorized_payments)
        Optional<Suscripcion> optSuscripcion = Optional.empty();
        if (authPayment.getPreapprovalId() != null && !authPayment.getPreapprovalId().isBlank()) {
            optSuscripcion = suscripcionRepository.findByPreapprovalId(authPayment.getPreapprovalId());
        }

        if (optSuscripcion.isEmpty()) {
            log.warn("⚠️ Webhook authorized_payment ignorado: no existe suscripción local para preapproval_id={}",
                    authPayment.getPreapprovalId());
            return;
        }
        Suscripcion suscripcion = optSuscripcion.get();

        if (authPayment.getPaymentId() != null && !authPayment.getPaymentId().isBlank()) {
            suscripcion.setMpPaymentId(authPayment.getPaymentId());
        }

        // status del authorized_payment: "processed", "pending", "refunded", "cancelled"
        String status = authPayment.getStatus();
        String paymentStatus = authPayment.getPaymentStatus(); // "approved", "rejected", etc.

        boolean pagoAprobado = "processed".equalsIgnoreCase(status) &&
                ("approved".equalsIgnoreCase(paymentStatus) || paymentStatus == null);

        if (pagoAprobado) {
            LocalDateTime now = LocalDateTime.now();
            suscripcion.setEstado("ACTIVA");
            suscripcion.setCurrentPeriodStart(now);
            suscripcion.setCurrentPeriodEnd(now.plusMonths(1));
            if (suscripcion.getFechaInicio() == null) {
                suscripcion.setFechaInicio(now);
            }
            suscripcion.setFechaProximoCobro(now.plusMonths(1));
            suscripcion.setEnPeriodoPrueba(false);  // ya pagó — salir del trial
            log.info("✅ Suscripción {} activada por authorized_payment (status={}, paymentStatus={})",
                    suscripcion.getId(), status, paymentStatus);
        } else if ("refunded".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)
                || "rejected".equalsIgnoreCase(paymentStatus)) {
            suscripcion.setEstado("SUSPENDIDA");
            log.warn("⚠️ Suscripción {} suspendida por authorized_payment (status={}, paymentStatus={})",
                    suscripcion.getId(), status, paymentStatus);
        } else {
            suscripcion.setEstado("PENDIENTE");
            log.info("ℹ️ Suscripción {} permanece PENDIENTE por authorized_payment (status={})", suscripcion.getId(), status);
        }

        suscripcionRepository.save(suscripcion);

        String estadoEmail = pagoAprobado ? "ACTIVA"
                : ("refunded".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)
                        || "rejected".equalsIgnoreCase(paymentStatus)) ? "SUSPENDIDA" : null;
        if (estadoEmail != null) enviarEmailSuscripcionSiAplica(suscripcion, estadoEmail);
    }

    private void procesarWebhookPago(String paymentId) {
        log.info("🔔 Procesando webhook pago: {}", paymentId);
        MercadoPagoPaymentInfo payment = mercadoPagoService.obtenerPago(paymentId);

        Optional<Suscripcion> optSuscripcion = resolverSuscripcionPorPago(payment);
        if (optSuscripcion.isEmpty()) {
            log.warn("⚠️ Webhook pago ignorado: no existe suscripción local para payment_id={}", paymentId);
            return;
        }
        Suscripcion suscripcion = optSuscripcion.get();

        suscripcion.setMpPaymentId(payment.getPaymentId());
        if (payment.getLastFourDigits() != null && !payment.getLastFourDigits().isBlank()) {
            suscripcion.setUltimos4Digitos(payment.getLastFourDigits());
        }

        if ("approved".equalsIgnoreCase(payment.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            suscripcion.setEstado("ACTIVA");
            suscripcion.setCurrentPeriodStart(now);
            suscripcion.setCurrentPeriodEnd(now.plusMonths(1));
            if (suscripcion.getFechaInicio() == null) {
                suscripcion.setFechaInicio(now);
            }
            suscripcion.setFechaProximoCobro(now.plusMonths(1));
            suscripcion.setEnPeriodoPrueba(false);
            // Si era un upgrade, limpiar preferenceId ya consumido
            if (suscripcion.getMpPreferenceId() != null) {
                suscripcion.setMpPreferenceId(null);
            }
            log.info("✅ Suscripción {} activada por webhook pago MP (plan={})", suscripcion.getId(), suscripcion.getPlanId());
        } else if ("rejected".equalsIgnoreCase(payment.getStatus()) || "cancelled".equalsIgnoreCase(payment.getStatus())) {
            suscripcion.setEstado("SUSPENDIDA");
            log.warn("⚠️ Suscripción {} suspendida por estado de pago: {}", suscripcion.getId(), payment.getStatus());
        } else {
            suscripcion.setEstado("PENDIENTE");
            log.info("ℹ️ Suscripción {} permanece PENDIENTE por estado de pago: {}", suscripcion.getId(), payment.getStatus());
        }

        suscripcionRepository.save(suscripcion);

        if ("approved".equalsIgnoreCase(payment.getStatus())) {
            enviarEmailSuscripcionSiAplica(suscripcion, "ACTIVA");
        } else if ("rejected".equalsIgnoreCase(payment.getStatus()) || "cancelled".equalsIgnoreCase(payment.getStatus())) {
            enviarEmailSuscripcionSiAplica(suscripcion, "SUSPENDIDA");
        }
    }

    @Override
    public SuscripcionEstadoResponseDTO obtenerEstadoSuscripcion(String tenantId, Long usuarioId) {
        // Primero buscar por propietario; si no existe (GERENTE u otro rol no-owner)
        // buscar cualquier suscripción del tenant para que puedan verla en modo lectura.
        Optional<Suscripcion> optSuscripcion = suscripcionRepository
                .findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId);

        if (optSuscripcion.isEmpty()) {
            // Para usuarios no-propietarios (ej. GERENTE), devolver la suscripción MÁS RECIENTE
            // del tenant (no la más antigua, que podría estar CANCELADA o ser de un plan viejo).
            optSuscripcion = suscripcionRepository.findFirstByTenantIdOrderByIdDesc(tenantId);
        }

        if (optSuscripcion.isEmpty()) {
            log.info("ℹ️ No existe suscripción para tenant={}, usuario={}", tenantId, usuarioId);
            return SuscripcionEstadoResponseDTO.builder()
                    .estado("SIN_SUSCRIPCION")
                    .build();
        }
        Suscripcion s = optSuscripcion.get();
        return buildEstadoResponse(s);
    }

    private SuscripcionEstadoResponseDTO buildEstadoResponse(Suscripcion s) {
        return SuscripcionEstadoResponseDTO.builder()
                .estado(s.getEstado())
                .planId(s.getPlanId())
                .preapprovalId(s.getPreapprovalId())
                .mpPaymentId(s.getMpPaymentId())
                .fechaProximoCobro(s.getFechaProximoCobro())
                .precioMensual(s.getPrecioMensual())
                .build();
    }

    String mapearEstadoPreapproval(String estadoMp) {
        if (estadoMp == null) return "PENDIENTE";
        return switch (estadoMp.toLowerCase()) {
            case "authorized", "active" -> "ACTIVA";
            case "paused" -> "SUSPENDIDA";
            case "cancelled" -> "CANCELADA";
            default -> "PENDIENTE";
        };
    }

    private Optional<Suscripcion> resolverSuscripcionPorPreapproval(MercadoPagoPreapprovalInfo preapproval) {
        if (preapproval.getPreapprovalId() != null && !preapproval.getPreapprovalId().isBlank()) {
            Optional<Suscripcion> byPreapprovalId = suscripcionRepository.findByPreapprovalId(preapproval.getPreapprovalId());
            if (byPreapprovalId.isPresent()) {
                return byPreapprovalId;
            }
        }

        if (preapproval.getExternalReference() != null && preapproval.getExternalReference().contains(":")) {
            return resolverSuscripcionPorExternalRef(preapproval.getExternalReference());
        }

        return Optional.empty();
    }

    private Optional<Suscripcion> resolverSuscripcionPorPago(MercadoPagoPaymentInfo payment) {
        if (payment.getExternalReference() != null && payment.getExternalReference().contains(":")) {
            Optional<Suscripcion> byRef = resolverSuscripcionPorExternalRef(payment.getExternalReference());
            if (byRef.isPresent()) {
                return byRef;
            }
        }

        if (payment.getPreferenceId() != null && !payment.getPreferenceId().isBlank()) {
            return suscripcionRepository.findFirstByMpPreferenceIdOrderByIdDesc(payment.getPreferenceId());
        }

        return Optional.empty();
    }

    private Optional<Suscripcion> resolverSuscripcionPorExternalRef(String externalReference) {
        String[] parts = externalReference.split(":", 2);
        String tenantId = parts[0];
        Long usuarioId;
        try {
            usuarioId = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("External reference inválida para Mercado Pago: " + externalReference, ex);
        }
        return suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId);
    }

    /** Envía email al usuario principal de la suscripción según el estado. Solo ACTIVA, SUSPENDIDA y CANCELADA. */
    private void enviarEmailSuscripcionSiAplica(Suscripcion suscripcion, String estado) {
        if (suscripcion.getUsuarioPrincipal() == null) return;
        try {
            emailService.enviarEmailSuscripcion(
                    suscripcion.getUsuarioPrincipal().getEmail(),
                    suscripcion.getUsuarioPrincipal().getNombre(),
                    estado,
                    suscripcion.getPlanId()
            );
        } catch (Exception e) {
            log.warn("⚠️ No se pudo enviar email de suscripción ({}): {}", estado, e.getMessage());
        }
    }
}

