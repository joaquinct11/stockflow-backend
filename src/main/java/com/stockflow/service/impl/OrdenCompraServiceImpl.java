package com.stockflow.service.impl;

import com.stockflow.dto.OrdenCompraItemDTO;
import com.stockflow.dto.OrdenCompraRequestDTO;
import com.stockflow.dto.OrdenCompraResponseDTO;
import com.stockflow.entity.OrdenCompra;
import com.stockflow.entity.OrdenCompraDetalle;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Proveedor;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.OrdenCompraDetalleRepository;
import com.stockflow.repository.OrdenCompraRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.OrdenCompraService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrdenCompraServiceImpl implements OrdenCompraService {

    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraDetalleRepository detalleRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public OrdenCompraResponseDTO crearOrdenCompra(OrdenCompraRequestDTO request,
                                                    Long usuarioCreadorId,
                                                    String tenantId) {
        Proveedor proveedor = proveedorRepository.findById(request.getProveedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));

        Usuario usuario = usuarioRepository.findById(usuarioCreadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        OrdenCompra oc = OrdenCompra.builder()
                .tenantId(tenantId)
                .proveedor(proveedor)
                .usuarioCreador(usuario)
                .estado("BORRADOR")
                .observaciones(request.getObservaciones())
                .build();

        List<OrdenCompraDetalle> detalles = request.getItems().stream().map(item -> {
            Producto producto = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado: " + item.getProductoId()));
            return OrdenCompraDetalle.builder()
                    .ordenCompra(oc)
                    .producto(producto)
                    .cantidadSolicitada(item.getCantidadSolicitada())
                    .precioUnitario(item.getPrecioUnitario())
                    .build();
        }).collect(Collectors.toList());

        oc.getDetalles().addAll(detalles);
        OrdenCompra saved = ordenCompraRepository.save(oc);
        log.info("✅ OC creada id={} tenant={}", saved.getId(), tenantId);
        return toResponseDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrdenCompraResponseDTO> obtenerPorId(Long id) {
        return ordenCompraRepository.findById(id).map(this::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdenCompraResponseDTO> listar(String tenantId, String estado, Long proveedorId) {
        List<OrdenCompra> lista;
        if (estado != null && proveedorId != null) {
            lista = ordenCompraRepository.findByTenantIdAndEstadoAndProveedorId(tenantId, estado, proveedorId);
        } else if (estado != null) {
            lista = ordenCompraRepository.findByTenantIdAndEstado(tenantId, estado);
        } else if (proveedorId != null) {
            lista = ordenCompraRepository.findByTenantIdAndProveedorId(tenantId, proveedorId);
        } else {
            lista = ordenCompraRepository.findByTenantId(tenantId);
        }
        return lista.stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdenCompraItemDTO> obtenerItemsConPendientes(Long ocId) {
        return detalleRepository.findByOrdenCompraId(ocId).stream().map(d -> {
            int recibido = detalleRepository.totalRecibidoPorOcYProducto(ocId, d.getProducto().getId());
            int pendiente = Math.max(0, d.getCantidadSolicitada() - recibido);
            return OrdenCompraItemDTO.builder()
                    .id(d.getId())
                    .productoId(d.getProducto().getId())
                    .productoNombre(d.getProducto().getNombre())
                    .cantidadSolicitada(d.getCantidadSolicitada())
                    .precioUnitario(d.getPrecioUnitario())
                    .cantidadRecibida(recibido)
                    .cantidadPendiente(pendiente)
                    .build();
        }).collect(Collectors.toList());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OrdenCompraResponseDTO toResponseDTO(OrdenCompra oc) {
        List<OrdenCompraItemDTO> items = oc.getDetalles().stream().map(d -> {
            int recibido = detalleRepository.totalRecibidoPorOcYProducto(oc.getId(), d.getProducto().getId());
            int pendiente = Math.max(0, d.getCantidadSolicitada() - recibido);
            return OrdenCompraItemDTO.builder()
                    .id(d.getId())
                    .productoId(d.getProducto().getId())
                    .productoNombre(d.getProducto().getNombre())
                    .cantidadSolicitada(d.getCantidadSolicitada())
                    .precioUnitario(d.getPrecioUnitario())
                    .cantidadRecibida(recibido)
                    .cantidadPendiente(pendiente)
                    .build();
        }).collect(Collectors.toList());

        return OrdenCompraResponseDTO.builder()
                .id(oc.getId())
                .tenantId(oc.getTenantId())
                .proveedorId(oc.getProveedor().getId())
                .proveedorNombre(oc.getProveedor().getNombre())
                .usuarioCreadorId(oc.getUsuarioCreador().getId())
                .usuarioCreadorNombre(oc.getUsuarioCreador().getNombre())
                .estado(oc.getEstado())
                .observaciones(oc.getObservaciones())
                .createdAt(oc.getCreatedAt())
                .updatedAt(oc.getUpdatedAt())
                .items(items)
                .build();
    }

    @Override
    @Transactional
    public OrdenCompraResponseDTO enviar(Long id, String tenantId) {
        OrdenCompra oc = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OC no encontrada"));

        if (!oc.getTenantId().equals(tenantId)) {
            throw new BadRequestException("OC no pertenece al tenant actual");
        }

        if (!"BORRADOR".equals(oc.getEstado())) {
            throw new BadRequestException("Solo se puede enviar una OC en estado BORRADOR");
        }

        oc.setEstado("ENVIADA");
        OrdenCompra saved = ordenCompraRepository.save(oc);
        return toResponseDTO(saved);
    }

    @Override
    @Transactional
    public OrdenCompraResponseDTO cancelar(Long id, String tenantId) {
        OrdenCompra oc = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OC no encontrada"));

        if (!oc.getTenantId().equals(tenantId)) {
            throw new BadRequestException("OC no pertenece al tenant actual");
        }

        // regla simple: cancelar si está en BORRADOR o ENVIADA
        if (!"BORRADOR".equals(oc.getEstado()) && !"ENVIADA".equals(oc.getEstado())) {
            throw new BadRequestException("Solo se puede cancelar una OC en estado BORRADOR o ENVIADA");
        }

        oc.setEstado("CANCELADA");
        OrdenCompra saved = ordenCompraRepository.save(oc);
        return toResponseDTO(saved);
    }
}
