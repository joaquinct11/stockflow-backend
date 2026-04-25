package com.stockflow.service.impl;

import com.stockflow.dto.MercadoPagoWebhookRequestDTO;
import com.stockflow.dto.SuscripcionCheckoutResponseDTO;
import com.stockflow.dto.SuscripcionEstadoResponseDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.MercadoPagoService;
import com.stockflow.service.SuscripcionCheckoutService;
import com.stockflow.service.UsuarioService;
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

    @Override
    @Transactional
    public SuscripcionCheckoutResponseDTO iniciarCheckout(String planId, String tenantId, Long usuarioId,
                                                          String payerIdentificationType, String payerIdentificationNumber) {
        BigDecimal precioPlan = obtenerPrecioPlanPagado(planId);
        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new BadRequestException("Usuario no encontrado"));

        if (!tenantId.equals(usuario.getTenantId())) {
            throw new BadRequestException("Usuario no pertenece al tenant actual");
        }

        // Persistir identificación si se envió en este request
        boolean tieneIdentificacion = payerIdentificationType != null && !payerIdentificationType.isBlank()
                && payerIdentificationNumber != null && !payerIdentificationNumber.isBlank();
        if (tieneIdentificacion) {
            usuario.setTipoDocumento(payerIdentificationType);
            usuario.setNumeroDocumento(payerIdentificationNumber);
            usuarioService.guardarUsuario(usuario);
            log.info("💾 Identificación del pagador actualizada para usuario={}: tipo={}", usuarioId, payerIdentificationType);
        }

        // Leer la identificación desde la entidad (que ya tiene el valor actualizado o el guardado previamente)
        String tipoDoc = usuario.getTipoDocumento();
        String numDoc  = usuario.getNumeroDocumento();

        String externalReference = tenantId + ":" + usuarioId;
        MercadoPagoPreapprovalInfo preapproval = mercadoPagoService.crearPreapproval(
                planId, precioPlan, externalReference, usuario.getEmail(), tipoDoc, numDoc);

        Suscripcion suscripcion = suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId)
                .orElseGet(() -> Suscripcion.builder()
                        .usuarioPrincipal(usuario)
                        .tenantId(tenantId)
                        .build());

        suscripcion.setPlanId(planId);
        suscripcion.setPrecioMensual(precioPlan);
        suscripcion.setMetodoPago("MERCADOPAGO");
        suscripcion.setEstado("PENDIENTE");
        suscripcion.setPreapprovalId(preapproval.getPreapprovalId());

        suscripcionRepository.save(suscripcion);

        log.info("✅ Preapproval creado para tenant={}, usuario={}, preapprovalId={}", tenantId, usuarioId, preapproval.getPreapprovalId());

        return SuscripcionCheckoutResponseDTO.builder()
                .initPoint(preapproval.getInitPoint())
                .preapprovalId(preapproval.getPreapprovalId())
                .build();
    }

    @Override
    @Transactional
    public void procesarWebhook(MercadoPagoWebhookRequestDTO webhookRequestDTO) {
        if (webhookRequestDTO == null || webhookRequestDTO.getData() == null || webhookRequestDTO.getData().getId() == null) {
            log.info("ℹ️ Webhook Mercado Pago ignorado por payload incompleto");
            return;
        }

        String tipo = webhookRequestDTO.getType() != null ? webhookRequestDTO.getType() : webhookRequestDTO.getTopic();

        if ("subscription_preapproval".equalsIgnoreCase(tipo) || "preapproval".equalsIgnoreCase(webhookRequestDTO.getEntity())) {
            procesarWebhookPreapproval(webhookRequestDTO.getData().getId());
        } else if ("subscription_authorized_payment".equalsIgnoreCase(tipo)) {
            procesarWebhookAuthorizedPayment(webhookRequestDTO.getData().getId());
        } else if ("payment".equalsIgnoreCase(tipo)) {
            procesarWebhookPago(webhookRequestDTO.getData().getId());
        } else {
            log.info("ℹ️ Webhook ignorado (tipo no soportado): {}", tipo);
        }
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

        suscripcion.setEstado(nuevoEstado);
        suscripcion.setPreapprovalId(preapprovalId);

        if ("ACTIVA".equals(nuevoEstado)) {
            LocalDateTime now = LocalDateTime.now();
            suscripcion.setCurrentPeriodStart(now);
            suscripcion.setCurrentPeriodEnd(now.plusMonths(1));
            if (suscripcion.getFechaInicio() == null) {
                suscripcion.setFechaInicio(now);
            }
            suscripcion.setFechaProximoCobro(now.plusMonths(1));
            log.info("✅ Suscripción {} activada por preapproval webhook MP (status={})", suscripcion.getId(), estadoMp);
        } else if ("CANCELADA".equals(nuevoEstado)) {
            suscripcion.setFechaCancelacion(LocalDateTime.now());
            log.info("❌ Suscripción {} cancelada por preapproval webhook MP (status={})", suscripcion.getId(), estadoMp);
        } else {
            log.info("ℹ️ Suscripción {} actualizada a {} por preapproval webhook MP (status={})", suscripcion.getId(), nuevoEstado, estadoMp);
        }

        suscripcionRepository.save(suscripcion);
    }

    private void procesarWebhookAuthorizedPayment(String paymentId) {
        log.info("🔔 Procesando webhook subscription_authorized_payment: {}", paymentId);
        MercadoPagoPaymentInfo payment = mercadoPagoService.obtenerPago(paymentId);

        Optional<Suscripcion> optSuscripcion = resolverSuscripcionPorPago(payment);
        if (optSuscripcion.isEmpty()) {
            log.warn("⚠️ Webhook authorized_payment ignorado: no existe suscripción local para payment_id={}", paymentId);
            return;
        }
        Suscripcion suscripcion = optSuscripcion.get();

        suscripcion.setMpPaymentId(payment.getPaymentId());
        if (payment.getLastFourDigits() != null && !payment.getLastFourDigits().isBlank()) {
            suscripcion.setUltimos4Digitos(payment.getLastFourDigits());
        }

        String statusPago = payment.getStatus();
        if ("approved".equalsIgnoreCase(statusPago) || "authorized".equalsIgnoreCase(statusPago)) {
            LocalDateTime now = LocalDateTime.now();
            suscripcion.setEstado("ACTIVA");
            suscripcion.setCurrentPeriodStart(now);
            suscripcion.setCurrentPeriodEnd(now.plusMonths(1));
            if (suscripcion.getFechaInicio() == null) {
                suscripcion.setFechaInicio(now);
            }
            suscripcion.setFechaProximoCobro(now.plusMonths(1));
            log.info("✅ Suscripción {} activada por webhook authorized_payment MP (status={})", suscripcion.getId(), statusPago);
        } else if ("rejected".equalsIgnoreCase(statusPago) || "cancelled".equalsIgnoreCase(statusPago)) {
            suscripcion.setEstado("SUSPENDIDA");
            log.warn("⚠️ Suscripción {} suspendida por authorized_payment rechazado/cancelado (status={})", suscripcion.getId(), statusPago);
        } else {
            suscripcion.setEstado("PENDIENTE");
            log.info("ℹ️ Suscripción {} permanece PENDIENTE por authorized_payment (status={})", suscripcion.getId(), statusPago);
        }

        suscripcionRepository.save(suscripcion);
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
            log.info("✅ Suscripción {} activada por webhook pago MP", suscripcion.getId());
        } else if ("rejected".equalsIgnoreCase(payment.getStatus()) || "cancelled".equalsIgnoreCase(payment.getStatus())) {
            suscripcion.setEstado("SUSPENDIDA");
            log.warn("⚠️ Suscripción {} suspendida por estado de pago: {}", suscripcion.getId(), payment.getStatus());
        } else {
            suscripcion.setEstado("PENDIENTE");
            log.info("ℹ️ Suscripción {} permanece PENDIENTE por estado de pago: {}", suscripcion.getId(), payment.getStatus());
        }

        suscripcionRepository.save(suscripcion);
    }

    @Override
    public SuscripcionEstadoResponseDTO obtenerEstadoSuscripcion(String tenantId, Long usuarioId) {
        Optional<Suscripcion> optSuscripcion = suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId);
        if (optSuscripcion.isEmpty()) {
            log.info("ℹ️ No existe suscripción para tenant={}, usuario={}", tenantId, usuarioId);
            return SuscripcionEstadoResponseDTO.builder()
                    .estado("SIN_SUSCRIPCION")
                    .build();
        }
        Suscripcion s = optSuscripcion.get();
        return SuscripcionEstadoResponseDTO.builder()
                .estado(s.getEstado())
                .planId(s.getPlanId())
                .preapprovalId(s.getPreapprovalId())
                .mpPaymentId(s.getMpPaymentId())
                .fechaProximoCobro(s.getFechaProximoCobro())
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

    BigDecimal obtenerPrecioPlanPagado(String planId) {
        return switch (planId) {
            case "BASICO" -> new BigDecimal("2.50");
            case "PRO" -> new BigDecimal("99.99");
            default -> throw new BadRequestException("Solo se permite checkout para planes pagos: BASICO o PRO");
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
}

