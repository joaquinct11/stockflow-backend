package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Respuesta paginada de citas. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitaPageResponseDTO {
    private List<CitaResumenDTO> content;
    private int                  page;
    private int                  size;
    private long                 totalElements;
    private int                  totalPages;
}
