package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantInfoDTO {
    private String tenantId;
    private String nombre;
    private String rubro;
    private String logoUrl;
    private String rol;
}
