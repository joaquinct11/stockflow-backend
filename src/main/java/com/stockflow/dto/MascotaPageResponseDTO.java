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
public class MascotaPageResponseDTO {

    private List<MascotaResumenDTO> content;
    private int  page;
    private int  size;
    private long totalElements;
    private int  totalPages;
    private ResumenDTO resumen;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResumenDTO {
        private long total;
        private long activas;
        private long inactivas;
    }
}
