package com.stockflow.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MercadoPagoPreferenceResponse {
    private String preferenceId;
    private String initPoint;
}
