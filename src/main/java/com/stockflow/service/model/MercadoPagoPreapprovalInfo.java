package com.stockflow.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MercadoPagoPreapprovalInfo {
    private String preapprovalId;
    private String status;
    private String initPoint;
    private String externalReference;
}
