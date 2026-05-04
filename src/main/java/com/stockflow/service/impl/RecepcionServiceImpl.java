package com.stockflow.service.impl;

import com.stockflow.dto.ComprobanteProveedorDTO;
import com.stockflow.dto.RecepcionDetalleRequestDTO;
import com.stockflow.dto.RecepcionDetalleResponseDTO;
import com.stockflow.dto.RecepcionRequestDTO;
import com.stockflow.dto.RecepcionResponseDTO;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.OrdenCompra;
import com.stockflow.entity.OrdenCompraDetalle;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Proveedor;
import com.stockflow.entity.Recepcion;
import com.stockflow.entity.RecepcionDetalle;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.OrdenCompraDetalleRepository;
import com.stockflow.repository.OrdenCompraRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.repository.RecepcionDetalleRepository;
import com.stockflow.repository.RecepcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.RecepcionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecepcionServiceImpl implements RecepcionService {

    private final RecepcionRepository recepcionRepository;
    private final RecepcionDetalleRepository detalleRepository;
    private final OrdenCompraRepository ocRepository;
    private final OrdenCompraDetalleRepository ocDetalleRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    @Override
    @Transactional
    public RecepcionResponseDTO crearRecepcion(RecepcionRequestDTO request,
                                               Long usuarioReceptorId,
                                               String tenantId) {
        OrdenCompra oc;
        Proveedor proveedor;

        if (request.getOcId() != null) {
            oc = ocRepository.findById(request.getOcId())
                    .orElseThrow(() -> new ResourceNotFoundException("Orden de Compra no encontrada"));
            if ("CANCELADA".equals(oc.getEstado())) {
                throw new BadRequestException("No se puede recepcionar una OC cancelada");
            }
            if ("BORRADOR".equals(oc.getEstado())) {
                throw new BadRequestException("La OC debe ser enviada antes de recepcionar");
            }
            if ("RECIBIDA".equals(oc.getEstado())) {
                throw new BadRequestException("Esta OC ya fue recibida completamente");
            }
            proveedor = oc.getProveedor();
        } else {
            oc = null;
            if (request.getProveedorId() == null) {
                throw new BadRequestException("Se requiere proveedorId cuando no se indica ocId");
            }
            proveedor = proveedorRepository.findById(request.getProveedorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
        }

        Usuario receptor = usuarioRepository.findById(usuarioReceptorId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Recepcion recepcion = Recepcion.builder()
                .tenantId(tenantId)
                .ordenCompra(oc)
                .proveedor(proveedor)
                .usuarioReceptor(receptor)
                .estado("BORRADOR")
                .observaciones(request.getObservaciones())
                .build();

        Recepcion saved = recepcionRepository.save(recepcion);
        // ✅ Si viene de una OC, pre-cargar detalles en recepcion_detalle
        if (oc != null) {
            List<OrdenCompraDetalle> ocDetalles = ocDetalleRepository.findByOrdenCompraId(oc.getId());

            List<RecepcionDetalle> detalles = ocDetalles.stream()
                    .map(ocDet -> {
                        int yaRecibido = ocDetalleRepository.totalRecibidoPorOcYProducto(
                                oc.getId(), ocDet.getProducto().getId());
                        int pendiente = Math.max(0, ocDet.getCantidadSolicitada() - yaRecibido);
                        return RecepcionDetalle.builder()
                                .recepcion(saved)
                                .producto(ocDet.getProducto())
                                .cantidadEsperada(pendiente)
                                .cantidadRecibida(0)
                                .build();
                    })
                    .toList();

            detalleRepository.saveAll(detalles);
        }

        log.info("✅ Recepción creada id={} tenant={}", saved.getId(), tenantId);
        return toResponseDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecepcionResponseDTO> obtenerPorId(Long id) {
        return recepcionRepository.findWithDetallesById(id).map(this::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecepcionResponseDTO> listar(String tenantId) {
        return recepcionRepository.findByTenantId(tenantId).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RecepcionDetalleResponseDTO upsertItem(Long recepcionId, RecepcionDetalleRequestDTO request) {
        Recepcion recepcion = recepcionRepository.findById(recepcionId)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada"));

        if ("CONFIRMADA".equals(recepcion.getEstado())) {
            throw new BadRequestException("No se pueden modificar ítems de una recepción ya confirmada");
        }

        Producto producto = productoRepository.findById(request.getProductoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        // Validate against OC pending quantities when linked
        if (recepcion.getOrdenCompra() != null) {
            Long ocId = recepcion.getOrdenCompra().getId();
            List<OrdenCompraDetalle> ocDetalles = ocDetalleRepository.findByOrdenCompraId(ocId);
            OrdenCompraDetalle ocDetalle = ocDetalles.stream()
                    .filter(d -> d.getProducto().getId().equals(request.getProductoId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "El producto " + request.getProductoId() + " no está en la Orden de Compra"));

            // Total already received across all CONFIRMED recepciones for this OC+producto.
            // Draft (BORRADOR) recepciones are excluded by the query, so this recepcion's
            // existing quantity (if any) does NOT count toward the limit.
            int yaRecibidoConfirmado = ocDetalleRepository.totalRecibidoPorOcYProducto(ocId, request.getProductoId());
            int pendiente = ocDetalle.getCantidadSolicitada() - yaRecibidoConfirmado;
            if (request.getCantidadRecibida() > pendiente) {
                throw new BadRequestException(
                        "Cantidad excede lo pendiente (" + pendiente + ") para este producto en la OC");
            }
        }

        // Upsert
        RecepcionDetalle detalle = detalleRepository
                .findByRecepcionIdAndProductoId(recepcionId, request.getProductoId())
                .orElse(RecepcionDetalle.builder()
                        .recepcion(recepcion)
                        .producto(producto)
                        .build());

        detalle.setCantidadRecibida(request.getCantidadRecibida());
        detalle.setFechaVencimiento(request.getFechaVencimiento());
        detalle.setLote(request.getLote());
        RecepcionDetalle saved = detalleRepository.save(detalle);

        return toDetalleResponseDTO(saved);
    }

    @Override
    @Transactional
    public RecepcionResponseDTO guardarComprobante(Long recepcionId, ComprobanteProveedorDTO dto) {
        Recepcion recepcion = recepcionRepository.findById(recepcionId)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada"));

        if ("CONFIRMADA".equals(recepcion.getEstado())) {
            throw new BadRequestException("No se puede modificar el comprobante de una recepción confirmada");
        }

        String tipo = dto.getTipoComprobante().toUpperCase();
        if (!tipo.equals("FACTURA") && !tipo.equals("BOLETA")) {
            throw new BadRequestException("Tipo de comprobante inválido. Use: FACTURA, BOLETA");
        }

        recepcion.setTipoComprobante(tipo);
        recepcion.setSerie(dto.getSerie());
        recepcion.setNumero(dto.getNumero());
        recepcion.setUrlAdjunto(dto.getUrlAdjunto());

        return toResponseDTO(recepcionRepository.save(recepcion));
    }

    @Override
    @Transactional
    public RecepcionResponseDTO confirmar(Long recepcionId, Long usuarioId) {
        Recepcion recepcion = recepcionRepository.findById(recepcionId)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada"));

        // Idempotence guard
        if ("CONFIRMADA".equals(recepcion.getEstado())) {
            throw new BadRequestException("La recepción ya fue confirmada");
        }

        List<RecepcionDetalle> detalles = detalleRepository.findByRecepcionId(recepcionId);
        if (detalles.isEmpty()) {
            throw new BadRequestException("La recepción no tiene ítems. Agregue al menos un ítem antes de confirmar");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Generate inventory movements and update product stock
        List<OrdenCompraDetalle> ocDetalles = recepcion.getOrdenCompra() != null
                ? ocDetalleRepository.findByOrdenCompraId(recepcion.getOrdenCompra().getId())
                : List.of();

        for (RecepcionDetalle detalle : detalles) {
            if (detalle.getCantidadRecibida() == null || detalle.getCantidadRecibida() <= 0) continue;

            Producto producto = detalle.getProducto();

            BigDecimal costoUnitario = ocDetalles.stream()
                    .filter(d -> d.getProducto().getId().equals(producto.getId()))
                    .map(OrdenCompraDetalle::getPrecioUnitario)
                    .filter(p -> p != null)
                    .findFirst()
                    .orElse(producto.getCostoUnitario());

            MovimientoInventario mov = MovimientoInventario.builder()
                    .producto(producto)
                    .usuario(usuario)
                    .tipo("ENTRADA")
                    .cantidad(detalle.getCantidadRecibida())
                    .descripcion("Recepción #" + recepcionId)
                    .referencia("RECEPCION-" + recepcionId)
                    .tenantId(recepcion.getTenantId())
                    .proveedorId(recepcion.getProveedor().getId())
                    .fechaVencimiento(detalle.getFechaVencimiento())
                    .lote(detalle.getLote())
                    .costoUnitario(costoUnitario)
                    .build();

            movimientoRepository.save(mov);

            producto.setStockActual(producto.getStockActual() + detalle.getCantidadRecibida());
            productoRepository.save(producto);
        }

        recepcion.setEstado("CONFIRMADA");
        recepcion.setFechaConfirmacion(LocalDateTime.now());
        Recepcion saved = recepcionRepository.save(recepcion);

        // Update OC state if linked
        if (recepcion.getOrdenCompra() != null) {
            actualizarEstadoOC(recepcion.getOrdenCompra());
        }

        log.info("✅ Recepción #{} confirmada", recepcionId);
        return toResponseDTO(saved);
    }

    @Override
    @Transactional
    public void anular(Long id, String tenantId) {
        Recepcion recepcion = recepcionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada"));

        if (!recepcion.getTenantId().equals(tenantId)) {
            throw new BadRequestException("No tienes acceso a esta recepción");
        }

        if ("CONFIRMADA".equals(recepcion.getEstado())) {
            throw new BadRequestException("No se puede anular una recepción ya confirmada — el stock ya fue actualizado");
        }

        recepcion.setEstado("ANULADA");
        recepcionRepository.save(recepcion);
        log.info("🚫 Recepción #{} anulada", id);
    }

    @Override
    @Transactional
    public void removeItem(Long recepcionId, Long itemId) {
        Recepcion recepcion = recepcionRepository.findById(recepcionId)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada"));

        if ("CONFIRMADA".equals(recepcion.getEstado())) {
            throw new BadRequestException("No se pueden eliminar ítems de una recepción ya confirmada");
        }

        RecepcionDetalle detalle = detalleRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Ítem no encontrado"));

        if (!detalle.getRecepcion().getId().equals(recepcionId)) {
            throw new BadRequestException("El ítem no pertenece a esta recepción");
        }

        detalleRepository.delete(detalle);
        log.info("🗑️ Ítem #{} eliminado de recepción #{}", itemId, recepcionId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void actualizarEstadoOC(OrdenCompra oc) {
        List<OrdenCompraDetalle> ocDetalles = ocDetalleRepository.findByOrdenCompraId(oc.getId());
        boolean todosCompletos = ocDetalles.stream().allMatch(d -> {
            int recibido = ocDetalleRepository.totalRecibidoPorOcYProducto(oc.getId(), d.getProducto().getId());
            return recibido >= d.getCantidadSolicitada();
        });
        oc.setEstado(todosCompletos ? "RECIBIDA" : "RECIBIDA_PARCIAL");
        ocRepository.save(oc);
    }

    private RecepcionResponseDTO toResponseDTO(Recepcion r) {
        List<RecepcionDetalleResponseDTO> detallesDTO = detalleRepository
                .findByRecepcionId(r.getId()).stream()
                .map(this::toDetalleResponseDTO)
                .collect(Collectors.toList());

        return RecepcionResponseDTO.builder()
                .id(r.getId())
                .tenantId(r.getTenantId())
                .ocId(r.getOrdenCompra() != null ? r.getOrdenCompra().getId() : null)
                .proveedorId(r.getProveedor().getId())
                .proveedorNombre(r.getProveedor().getNombre())
                .usuarioReceptorId(r.getUsuarioReceptor().getId())
                .usuarioReceptorNombre(r.getUsuarioReceptor().getNombre())
                .estado(r.getEstado())
                .tipoComprobante(r.getTipoComprobante())
                .serie(r.getSerie())
                .numero(r.getNumero())
                .urlAdjunto(r.getUrlAdjunto())
                .observaciones(r.getObservaciones())
                .createdAt(r.getCreatedAt())
                .fechaConfirmacion(r.getFechaConfirmacion())
                .items(detallesDTO)
                .build();
    }

    private RecepcionDetalleResponseDTO toDetalleResponseDTO(RecepcionDetalle d) {
        return RecepcionDetalleResponseDTO.builder()
                .id(d.getId())
                .productoId(d.getProducto().getId())
                .productoNombre(d.getProducto().getNombre())
                .codigoBarras(d.getProducto().getCodigoBarras())
                .cantidadEsperada(d.getCantidadEsperada())
                .cantidadRecibida(d.getCantidadRecibida())
                .fechaVencimiento(d.getFechaVencimiento())
                .lote(d.getLote())
                .build();
    }
}
