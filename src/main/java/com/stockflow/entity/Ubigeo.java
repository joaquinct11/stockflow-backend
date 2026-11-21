package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "catalogo_ubigeos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ubigeo {

    @Id
    @Column(length = 6)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String departamento;

    @Column(nullable = false, length = 100)
    private String provincia;

    @Column(nullable = false, length = 100)
    private String distrito;
}
