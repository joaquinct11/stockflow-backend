package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "recepcion_detalle")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecepcionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "recepcion_id", nullable = false)
    private Recepcion recepcion;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "cantidad_recibida", nullable = false)
    private Integer cantidadRecibida;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;
}
