package com.stockflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoDTO {

    private Long id;

    @NotBlank(message = "El nombre del producto es requerido")
    private String nombre;

    private String codigoBarras;

    private Long categoriaId;

    /** Solo lectura — nombre de la categoría para mostrar en frontend. */
    private String categoriaNombre;

    @Min(value = 0, message = "El stock actual no puede ser negativo")
    private Integer stockActual;

    @Min(value = 0, message = "El stock mínimo no puede ser negativo")
    private Integer stockMinimo;

    @Min(value = 0, message = "El stock máximo no puede ser negativo")
    private Integer stockMaximo;

    @DecimalMin(value = "0.00", inclusive = true, message = "El costo no puede ser negativo")
    private BigDecimal costoUnitario;

    @NotNull(message = "El precio de venta es requerido")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    private BigDecimal precioVenta;

    private Long unidadMedidaId;

    private Boolean activo;

    /** URL de imagen del producto (opcional). */
    private String imagenUrl;

    /** Composición o contenido del producto (ej: "paracetamol 500mg, amoxicilina 250mg"). Opcional. */
    private String componentes;

    /** True si el producto es genérico (sin marca). */
    private Boolean esGenerico = false;

    /** Cuántas unidades trae cada caja/presentación (ej: 100 tabletas/caja). Informativo. */
    private Integer unidadesPorCaja;

    /** Talla del producto (ej: S, M, L, XL). Solo para rubros de ropa. */
    private String talla;

    /** Color del producto (ej: Azul, Rojo). Solo para rubros de ropa. */
    private String color;

    private String tenantId;

    /** PRODUCTO (default) | SERVICIO. Los servicios no descuentan stock. */
    private String tipo = "PRODUCTO";

    /** Fecha del lote más próximo a vencer (calculado en el servidor, solo lectura). */
    private LocalDate proximaFechaVencimiento;

    /**
     * Stock disponible no vencido (FEFO). Solo presente si el producto tiene lotes
     * con fechaVencimiento registrada. El POS debe usar este valor cuando no es null.
     */
    private Integer stockVigente;
}