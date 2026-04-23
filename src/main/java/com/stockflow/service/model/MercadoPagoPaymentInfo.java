package com.stockflow.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MercadoPagoPaymentInfo {
    private String paymentId;
    private String status;
    private String preferenceId;
    private String externalReference;
    private String lastFourDigits;
}
