package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tenantId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private Boolean activo = true;

    // ── Configuración del negocio ────────────────────────────────────────────
    @Column(name = "ruc", length = 20)
    private String ruc;

    @Column(name = "direccion", length = 255)
    private String direccion;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "email_contacto", length = 120)
    private String emailContacto;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "logo_base64", columnDefinition = "TEXT")
    private String logoBase64;

    /** Rubro del negocio: BOTICA, FARMACIA, MINIMARKET, FERRETERIA, RESTAURANTE, TIENDA, OTRO */
    @Column(name = "rubro", length = 50)
    @Builder.Default
    private String rubro = "OTRO";

    @Column(name = "moneda", length = 10)
    @Builder.Default
    private String moneda = "S/.";

    @Column(name = "igv_porcentaje")
    @Builder.Default
    private Double igvPorcentaje = 18.0;

    @Column(name = "pie_pagina_pdf", length = 255)
    private String piePaginaPdf;

    @Column(name = "serie_boleta", length = 10)
    @Builder.Default
    private String serieBoleta = "BBB1";

    @Column(name = "serie_factura", length = 10)
    @Builder.Default
    private String serieFactura = "FFF1";

    // ── Integración OSE (Operador de Servicios Electrónicos) ─────────────────
    /** URL base del OSE, ej: https://api.nubefact.com/api/v1/{empresa-slug} */
    @Column(name = "ose_url", length = 500)
    private String oseUrl;

    /** Token API del OSE del tenant. */
    @Column(name = "ose_token", length = 500)
    private String oseToken;
    // ────────────────────────────────────────────────────────────────────────

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}