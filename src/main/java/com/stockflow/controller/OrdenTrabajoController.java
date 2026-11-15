package com.stockflow.controller;

import com.stockflow.dto.ActualizarEstadoOtRequestDTO;
import com.stockflow.dto.ActualizarOtRequestDTO;
import com.stockflow.dto.AsignarTecnicoOtRequestDTO;
import com.stockflow.dto.CierreTecnicoOtRequestDTO;
import com.stockflow.dto.CrearOtRequestDTO;
import com.stockflow.dto.EntregarOtRequestDTO;
import com.stockflow.dto.FacturarOtRequestDTO;
import com.stockflow.dto.OrdenTrabajoDTO;
import com.stockflow.dto.OtConsumoClasificacionDTO;
import com.stockflow.dto.OtConsumoDevolucionDTO;
import com.stockflow.dto.OtConsumoMaterialDTO;
import com.stockflow.dto.OtPageResponseDTO;
import com.stockflow.dto.RegistrarClasificacionOtRequestDTO;
import com.stockflow.dto.RegistrarConsumoOtRequestDTO;
import com.stockflow.dto.RegistrarDevolucionOtRequestDTO;
import java.util.List;
import com.stockflow.service.OrdenTrabajoService;
import com.stockflow.service.OtClasificacionService;
import com.stockflow.service.OtConsumoService;
import com.stockflow.service.OtDevolucionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ordenes-trabajo")
@RequiredArgsConstructor
public class OrdenTrabajoController {

    private final OrdenTrabajoService otService;
    private final OtConsumoService otConsumoService;
    private final OtDevolucionService otDevolucionService;
    private final OtClasificacionService otClasificacionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_OT')")
    public ResponseEntity<OtPageResponseDTO> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long equipoClienteId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(otService.listar(page, size, estado, equipoClienteId, clienteId, search));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_OT')")
    public ResponseEntity<OrdenTrabajoDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(otService.obtenerPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CREAR_OT')")
    public ResponseEntity<OrdenTrabajoDTO> crear(@Valid @RequestBody CrearOtRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(otService.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_EDITAR_OT')")
    public ResponseEntity<OrdenTrabajoDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarOtRequestDTO dto) {
        return ResponseEntity.ok(otService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/tecnico")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ASIGNAR_TECNICO_OT')")
    public ResponseEntity<OrdenTrabajoDTO> asignarTecnico(
            @PathVariable Long id,
            @RequestBody AsignarTecnicoOtRequestDTO dto) {
        return ResponseEntity.ok(otService.asignarTecnico(id, dto));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_OT')")
    public ResponseEntity<OrdenTrabajoDTO> actualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarEstadoOtRequestDTO dto) {
        return ResponseEntity.ok(otService.actualizarEstado(id, dto));
    }

    @PostMapping("/{id}/cierre-tecnico")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CERRAR_TECNICO_OT')")
    public ResponseEntity<OrdenTrabajoDTO> cierreTecnico(
            @PathVariable Long id,
            @Valid @RequestBody CierreTecnicoOtRequestDTO dto) {
        return ResponseEntity.ok(otService.cierreTecnico(id, dto));
    }

    @PostMapping("/{id}/facturar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_FACTURAR_OT')")
    public ResponseEntity<OrdenTrabajoDTO> facturar(
            @PathVariable Long id,
            @Valid @RequestBody FacturarOtRequestDTO req) {
        return ResponseEntity.ok(otService.facturarOt(id, req));
    }

    @PostMapping("/{id}/entrega")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_REGISTRAR_ENTREGA_OT')")
    public ResponseEntity<OrdenTrabajoDTO> entregar(
            @PathVariable Long id,
            @Valid @RequestBody EntregarOtRequestDTO req) {
        return ResponseEntity.ok(otService.entregarOt(id, req));
    }

    @PostMapping("/{id}/anular-factura")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ANULAR_FACTURA_OT')")
    public ResponseEntity<OrdenTrabajoDTO> anularFactura(@PathVariable Long id) {
        return ResponseEntity.ok(otService.anularFacturacionOt(id));
    }

    @GetMapping("/{id}/consumos")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_OT')")
    public ResponseEntity<List<OtConsumoMaterialDTO>> getConsumos(@PathVariable Long id) {
        return ResponseEntity.ok(otConsumoService.getConsumos(id));
    }

    @PostMapping("/{id}/consumos")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_REGISTRAR_CONSUMO_OT')")
    public ResponseEntity<OtConsumoMaterialDTO> registrarConsumo(
            @PathVariable Long id,
            @Valid @RequestBody RegistrarConsumoOtRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(otConsumoService.registrarConsumo(id, dto));
    }

    @GetMapping("/{id}/consumos/{consumoId}/devoluciones")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_OT')")
    public ResponseEntity<List<OtConsumoDevolucionDTO>> getDevoluciones(
            @PathVariable Long id,
            @PathVariable Long consumoId) {
        return ResponseEntity.ok(otDevolucionService.getDevoluciones(id, consumoId));
    }

    @PostMapping("/{id}/consumos/{consumoId}/devoluciones")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_DEVOLVER_CONSUMO_OT')")
    public ResponseEntity<OtConsumoDevolucionDTO> registrarDevolucion(
            @PathVariable Long id,
            @PathVariable Long consumoId,
            @Valid @RequestBody RegistrarDevolucionOtRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(otDevolucionService.registrarDevolucion(id, consumoId, dto));
    }

    @GetMapping("/{id}/consumos/{consumoId}/clasificaciones")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_OT')")
    public ResponseEntity<List<OtConsumoClasificacionDTO>> getClasificaciones(
            @PathVariable Long id,
            @PathVariable Long consumoId) {
        return ResponseEntity.ok(otClasificacionService.getClasificaciones(id, consumoId));
    }

    @PostMapping("/{id}/consumos/{consumoId}/clasificaciones")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CLASIFICAR_CONSUMO_OT')")
    public ResponseEntity<OtConsumoClasificacionDTO> registrarClasificacion(
            @PathVariable Long id,
            @PathVariable Long consumoId,
            @Valid @RequestBody RegistrarClasificacionOtRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(otClasificacionService.registrarClasificacion(id, consumoId, dto));
    }
}
