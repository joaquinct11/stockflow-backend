package com.stockflow.entity.gre;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "guia_remision_transporte")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuiaRemisionTransporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guia_remision_id", nullable = false, unique = true)
    private GuiaRemision guiaRemision;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    // ── Direcciones ──────────────────────────────────────────────────────────
    @Column(name = "direccion_partida", nullable = false)
    private String direccionPartida;

    @Column(name = "ubigeo_partida", length = 10)
    private String ubigeoPartida;

    @Column(name = "direccion_llegada", nullable = false)
    private String direccionLlegada;

    @Column(name = "ubigeo_llegada", length = 10)
    private String ubigeoLlegada;

    // ── Transporte PRIVADO (modalidad '02') ──────────────────────────────────
    @Column(name = "vehiculo_placa", length = 20)
    private String vehiculoPlaca;

    @Column(name = "conductor_tipo_doc", length = 5)
    private String conductorTipoDoc;

    @Column(name = "conductor_num_doc", length = 20)
    private String conductorNumDoc;

    @Column(name = "conductor_nombres", length = 200)
    private String conductorNombres;

    @Column(name = "conductor_apellidos", length = 200)
    private String conductorApellidos;

    @Column(name = "conductor_licencia", length = 50)
    private String conductorLicencia;

    @Column(name = "fecha_inicio_traslado")
    private LocalDate fechaInicioTraslado;

    // ── Transporte PÚBLICO (modalidad '01') ──────────────────────────────────
    @Column(name = "transportista_ruc", length = 20)
    private String transportistaRuc;

    @Column(name = "transportista_denominacion")
    private String transportistaDenominacion;

    @Column(name = "transportista_nro_registro_mtc", length = 100)
    private String transportistaNroRegistroMtc;

    @Column(name = "transportista_nro_autorizacion", length = 100)
    private String transportistaNroAutorizacion;

    @Column(name = "transportista_codigo_entidad_autorizadora", length = 10)
    private String transportistaCodigoEntidadAutorizadora;

    @Column(name = "fecha_entrega_transportista")
    private LocalDate fechaEntregaTransportista;
}
