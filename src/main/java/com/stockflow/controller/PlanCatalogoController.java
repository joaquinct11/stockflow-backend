package com.stockflow.controller;

import com.stockflow.dto.PlanCatalogoDTO;
import com.stockflow.dto.PlanPrecioDTO;
import com.stockflow.service.PlanCatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints públicos del catálogo comercial de planes.
 * Todos los endpoints devuelven solo planes activos y no exponen datos de Culqi.
 */
@RestController
@RequestMapping("/auth/planes")
@RequiredArgsConstructor
@Tag(name = "Catálogo de planes", description = "Consulta pública de planes, precios y límites por segmento comercial")
public class PlanCatalogoController {

    private final PlanCatalogoService planCatalogoService;

    @GetMapping("/catalogo")
    @Operation(
        summary = "Planes de un segmento comercial",
        description = "Devuelve los planes Básico y Pro activos para el segmento indicado "
                    + "(e.g. VETERINARIA, FARMACIA_BOTICA, TIENDA_ROPA, TIENDA_BODEGA). "
                    + "Si el segmento no tiene planes activos devuelve lista vacía.")
    public ResponseEntity<List<PlanCatalogoDTO>> porSegmento(
            @RequestParam String segmento) {
        return ResponseEntity.ok(planCatalogoService.listarPorSegmento(segmento));
    }

    @GetMapping("/{codigo}")
    @Operation(
        summary = "Detalle de un plan específico",
        description = "Devuelve precio, límites y metadatos del plan indicado por su código único "
                    + "(e.g. VETERINARIA_BASICO). Devuelve 400 si el plan no existe o está inactivo.")
    public ResponseEntity<PlanCatalogoDTO> porCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(planCatalogoService.obtenerPorCodigo(codigo));
    }

    /**
     * Mantiene compatibilidad con el endpoint anterior consumido por el frontend.
     * Consulta el catálogo por rubro operativo — fuente de verdad única.
     * Prefer GET /auth/planes/catalogo?segmento=X para datos completos.
     */
    @GetMapping("/precios")
    @Operation(
        summary = "Precios de planes por rubro (compatibilidad)",
        description = "Endpoint de compatibilidad con versiones anteriores. "
                    + "Devuelve los precios del plan Básico y Pro para el rubro indicado "
                    + "consultando directamente el catálogo de planes. "
                    + "Prefer GET /auth/planes/catalogo?segmento=X para datos completos.")
    public ResponseEntity<PlanPrecioDTO> preciosPorRubro(
            @RequestParam(defaultValue = "OTRO") String rubro) {
        return ResponseEntity.ok(planCatalogoService.getPreciosPorRubroOperativo(rubro));
    }
}
