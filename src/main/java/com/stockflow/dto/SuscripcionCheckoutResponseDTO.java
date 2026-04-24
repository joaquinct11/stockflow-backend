package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuscripcionCheckoutResponseDTO {
    private String initPoint;
    private String preapprovalId;
    /** @deprecated Usar {@link #preapprovalId} en su lugar. Mantenido por compatibilidad. */
    @Deprecated
    private String preferenceId;
}
