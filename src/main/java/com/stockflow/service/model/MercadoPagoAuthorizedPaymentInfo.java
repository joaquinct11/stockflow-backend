package com.stockflow.service.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MercadoPagoAuthorizedPaymentInfo {

    private String authorizedPaymentId;
    private String preapprovalId;
    /** "processed", "pending", "refunded", "cancelled" */
    private String status;
    /** ID del pago real generado por MP (puede ser null si aún no se procesó) */
    private String paymentId;
    /** "approved", "rejected", etc. (puede ser null) */
    private String paymentStatus;
}
