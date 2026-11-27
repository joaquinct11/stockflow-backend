package com.stockflow.entity.gre;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "gre_serie_contador")
@IdClass(GreSerieContador.GreSerieContadorId.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GreSerieContador {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Id
    @Column(name = "serie", nullable = false, length = 10)
    private String serie;

    @Column(name = "ultimo", nullable = false)
    private int ultimo;

    @Data
    @NoArgsConstructor @AllArgsConstructor
    public static class GreSerieContadorId implements Serializable {
        private String tenantId;
        private String serie;
    }
}
