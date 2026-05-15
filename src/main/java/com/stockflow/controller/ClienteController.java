package com.stockflow.controller;

import com.stockflow.dto.ClienteDTO;
import com.stockflow.entity.Cliente;
import com.stockflow.mapper.ClienteMapper;
import com.stockflow.service.ClienteService;
import com.stockflow.util.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteMapper clienteMapper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_CLIENTES')")
    public ResponseEntity<List<ClienteDTO>> obtenerTodos() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🏢 Obteniendo clientes para tenant: {}", tenantId);
        return ResponseEntity.ok(
                clienteMapper.toDTOList(clienteService.obtenerClientesPorTenant(tenantId))
        );
    }

    @GetMapping("/activos")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_CLIENTES')")
    public ResponseEntity<List<ClienteDTO>> obtenerActivos() {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(
                clienteMapper.toDTOList(clienteService.obtenerClientesActivosPorTenant(tenantId))
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_CLIENTES')")
    public ResponseEntity<ClienteDTO> obtenerPorId(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return clienteService.obtenerClientePorId(id)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .map(clienteMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_CLIENTES')")
    public ResponseEntity<List<ClienteDTO>> buscarPorNombre(@RequestParam String nombre) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(
                clienteMapper.toDTOList(clienteService.buscarClientesPorNombre(nombre, tenantId))
        );
    }

    @GetMapping("/documento/{numero}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_VER_CLIENTES')")
    public ResponseEntity<List<ClienteDTO>> buscarPorDocumento(@PathVariable String numero) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(
                clienteMapper.toDTOList(clienteService.buscarClientesPorDocumento(numero, tenantId))
        );
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'VENDEDOR') or hasAuthority('PERM_CREAR_CLIENTE')")
    public ResponseEntity<ClienteDTO> crear(@Valid @RequestBody ClienteDTO clienteDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("➕ Creando cliente para tenant: {}", tenantId);
        clienteDTO.setTenantId(tenantId);
        Cliente cliente = clienteMapper.toEntity(clienteDTO);
        Cliente creado = clienteService.crearCliente(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteMapper.toDTO(creado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'VENDEDOR') or hasAuthority('PERM_EDITAR_CLIENTE')")
    public ResponseEntity<ClienteDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ClienteDTO clienteDTO) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("✏️ Actualizando cliente ID: {}", id);
        return clienteService.obtenerClientePorId(id)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .map(existing -> {
                    clienteMapper.updateEntityFromDTO(clienteDTO, existing);
                    Cliente actualizado = clienteService.actualizarCliente(id, existing);
                    return ResponseEntity.ok(clienteMapper.toDTO(actualizado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_CLIENTE')")
    public ResponseEntity<ClienteDTO> activar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return clienteService.obtenerClientePorId(id)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .map(c -> ResponseEntity.ok(clienteMapper.toDTO(clienteService.activarCliente(id))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_CAMBIAR_ESTADO_CLIENTE')")
    public ResponseEntity<ClienteDTO> desactivar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return clienteService.obtenerClientePorId(id)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .map(c -> ResponseEntity.ok(clienteMapper.toDTO(clienteService.desactivarCliente(id))))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_ELIMINAR_CLIENTE')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("🗑️ Eliminando cliente ID: {}", id);
        clienteService.obtenerClientePorId(id)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .ifPresent(c -> clienteService.eliminarCliente(id));
        return ResponseEntity.noContent().build();
    }
}
