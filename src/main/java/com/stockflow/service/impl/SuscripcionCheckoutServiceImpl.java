package com.stockflow.service.impl;

import com.stockflow.dto.MercadoPagoWebhookRequestDTO;
import com.stockflow.dto.SuscripcionCheckoutResponseDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.MercadoPagoService;
import com.stockflow.service.SuscripcionCheckoutService;
import com.stockflow.service.UsuarioService;
import com.stockflow.service.model.MercadoPagoPaymentInfo;
import com.stockflow.service.model.MercadoPagoPreferenceResponse;
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
    public SuscripcionCheckoutResponseDTO iniciarCheckout(String planId, String tenantId, Long usuarioId) {
        BigDecimal precioPlan = obtenerPrecioPlanPagado(planId);
        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!tenantId.equals(usuario.getTenantId())) {
            throw new BadRequestException("Usuario no pertenece al tenant actual");
        }

        String externalReference = tenantId + ":" + usuarioId;
        MercadoPagoPreferenceResponse preference = mercadoPagoService.crearPreferencia(planId, precioPlan, externalReference);

        Suscripcion suscripcion = suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId)
                .orElseGet(() -> Suscripcion.builder()
                        .usuarioPrincipal(usuario)
                        .tenantId(tenantId)
                        .build());

        suscripcion.setPlanId(planId);
        suscripcion.setPrecioMensual(precioPlan);
        suscripcion.setMetodoPago("MERCADOPAGO");
        suscripcion.setEstado("PENDIENTE");
        suscripcion.setMpPreferenceId(preference.getPreferenceId());

        suscripcionRepository.save(suscripcion);

        return SuscripcionCheckoutResponseDTO.builder()
                .initPoint(preference.getInitPoint())
                .preferenceId(preference.getPreferenceId())
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
        if (tipo == null || !"payment".equalsIgnoreCase(tipo)) {
            log.info("ℹ️ Webhook ignorado (tipo no soportado): {}", tipo);
            return;
        }

        String paymentId = webhookRequestDTO.getData().getId();
        MercadoPagoPaymentInfo payment = mercadoPagoService.obtenerPago(paymentId);

        Suscripcion suscripcion = resolverSuscripcion(payment)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró suscripción para el pago " + paymentId));

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
            log.info("✅ Suscripción {} activada por webhook MP", suscripcion.getId());
        } else if ("rejected".equalsIgnoreCase(payment.getStatus()) || "cancelled".equalsIgnoreCase(payment.getStatus())) {
            suscripcion.setEstado("SUSPENDIDA");
            log.warn("⚠️ Suscripción {} suspendida por estado de pago: {}", suscripcion.getId(), payment.getStatus());
        } else {
            suscripcion.setEstado("PENDIENTE");
            log.info("ℹ️ Suscripción {} permanece PENDIENTE por estado de pago: {}", suscripcion.getId(), payment.getStatus());
        }

        suscripcionRepository.save(suscripcion);
    }

    BigDecimal obtenerPrecioPlanPagado(String planId) {
        return switch (planId) {
            case "BASICO" -> new BigDecimal("49.99");
            case "PRO" -> new BigDecimal("99.99");
            default -> throw new BadRequestException("Solo se permite checkout para planes pagos: BASICO o PRO");
        };
    }

    private Optional<Suscripcion> resolverSuscripcion(MercadoPagoPaymentInfo payment) {
        if (payment.getExternalReference() != null && payment.getExternalReference().contains(":")) {
            String[] parts = payment.getExternalReference().split(":", 2);
            String tenantId = parts[0];
            Long usuarioId;
            try {
                usuarioId = Long.parseLong(parts[1]);
            } catch (NumberFormatException ex) {
                throw new BadRequestException("External reference inválida para Mercado Pago: " + payment.getExternalReference(), ex);
            }
            Optional<Suscripcion> byTenantUser = suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(tenantId, usuarioId);
            if (byTenantUser.isPresent()) {
                return byTenantUser;
            }
        }

        if (payment.getPreferenceId() != null && !payment.getPreferenceId().isBlank()) {
            return suscripcionRepository.findFirstByMpPreferenceIdOrderByIdDesc(payment.getPreferenceId());
        }

        return Optional.empty();
    }
}
