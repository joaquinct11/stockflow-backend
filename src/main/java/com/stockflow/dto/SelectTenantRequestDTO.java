package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SelectTenantRequestDTO {

    @NotBlank(message = "El tenantId es obligatorio")
    private String tenantId;
}
