package com.stockflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtPageResponseDTO {

    private List<OtResumenDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    private ResumenDTO resumen;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResumenDTO {
        private long total;
        private long abiertas;
        private long enProceso;
        private long cerradas;
    }
}
