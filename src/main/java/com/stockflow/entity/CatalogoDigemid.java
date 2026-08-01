package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "catalogo_digemid")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogoDigemid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cod_prod", nullable = false, columnDefinition = "TEXT")
    private String codProd;

    @Column(name = "nom_prod", nullable = false, columnDefinition = "TEXT")
    private String nomProd;

    @Column(name = "concent", columnDefinition = "TEXT")
    private String concent;

    @Column(name = "nom_form_farm", columnDefinition = "TEXT")
    private String nomFormFarm;

    @Column(name = "presentac", columnDefinition = "TEXT")
    private String presentac;

    @Column(name = "fraccion", precision = 10, scale = 2)
    private BigDecimal fraccion;

    @Column(name = "num_reg_san", columnDefinition = "TEXT")
    private String numRegSan;

    @Column(name = "nom_titular", columnDefinition = "TEXT")
    private String nomTitular;

    @Column(name = "nom_fabricante", columnDefinition = "TEXT")
    private String nomFabricante;

    @Column(name = "nom_ifa", columnDefinition = "TEXT")
    private String nomIfa;

    @Column(name = "nom_rubro", columnDefinition = "TEXT")
    private String nomRubro;

    @Column(name = "situacion", columnDefinition = "TEXT")
    private String situacion;
}
