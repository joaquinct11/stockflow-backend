package com.stockflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuscripcionCheckoutRequestDTO {

    @NotBlank(message = "Plan es requerido")
    @Pattern(regexp = "BASICO|PRO", message = "Plan debe ser BASICO o PRO")
    private String planId;
}
