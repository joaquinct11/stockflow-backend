package com.stockflow.service.impl;

import com.stockflow.dto.ProductoImportResultDTO;
import com.stockflow.dto.ProductoImportRowDTO;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.entity.UnidadMedida;
import com.stockflow.entity.Usuario;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.UnidadMedidaRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.PlanLimitService;
import com.stockflow.service.ProductoService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final PlanLimitService planLimitService;

    @Override
    @Transactional
    public Producto crearProducto(Producto producto) {
        // ── Validar límite de plan ───────────────────────────────────────────
        planLimitService.validarLimiteProductos(producto.getTenantId());

        Producto productoCreado = productoRepository.save(producto);

        Long userId = TenantContext.getCurrentUserId();
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));

        MovimientoInventario mov = MovimientoInventario.builder()
                .producto(productoCreado)
                .usuario(usuario)
                .tipo("SALDO_INICIAL")
                .cantidad(productoCreado.getStockActual() != null ? productoCreado.getStockActual() : 0)
                .descripcion("Saldo inicial al crear producto")
                .referencia("CREACION_PRODUCTO")
                .tenantId(productoCreado.getTenantId())
                .costoUnitario(productoCreado.getCostoUnitario())
                .build();

        movimientoInventarioRepository.save(mov);

        // Recargar con JOIN FETCH para que categoriaRef y unidadMedida estén
        // completamente inicializados en el DTO de respuesta.
        return productoRepository.findByIdWithJoins(productoCreado.getId())
                .orElse(productoCreado);
    }

    @Override
    public Optional<Producto> obtenerProductoPorId(Long id) {
        return productoRepository.findById(id);
    }

    @Override
    public Optional<Producto> obtenerProductoPorCodigoBarras(String codigoBarras) {
        return productoRepository.findByCodigoBarras(codigoBarras);
    }

    @Override
    public List<Producto> buscarProductosPorNombre(String nombre, String tenantId) {
        return productoRepository.findByNombreContainingIgnoreCaseAndTenantId(nombre, tenantId);
    }

    @Override
    public List<Producto> obtenerProductosPorTenant(String tenantId) {
        return productoRepository.findByTenantId(tenantId);
    }

    @Override
    public List<Producto> obtenerProductosActivos() {
        return productoRepository.findByActivoTrue();
    }

    @Override
    public List<Producto> obtenerProductosBajoStock(String tenantId) {
        return productoRepository.findProductosBajoStock(tenantId);
    }

    @Override
    @Transactional
    public Producto actualizarProducto(Long id, Producto productoActualizado) {
        productoRepository.findById(id)
                .map(producto -> {
                    producto.setNombre(productoActualizado.getNombre());
                    producto.setCodigoBarras(productoActualizado.getCodigoBarras());
                    producto.setCategoriaRef(productoActualizado.getCategoriaRef());
                    producto.setCostoUnitario(productoActualizado.getCostoUnitario());
                    producto.setPrecioVenta(productoActualizado.getPrecioVenta());
                    producto.setUnidadMedida(productoActualizado.getUnidadMedida());
                    producto.setActivo(productoActualizado.getActivo());
                    producto.setStockActual(productoActualizado.getStockActual());
                    producto.setStockMinimo(productoActualizado.getStockMinimo());
                    producto.setStockMaximo(productoActualizado.getStockMaximo());
                    return productoRepository.save(producto);
                })
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        // Recargar con JOIN FETCH para devolver categoriaRef y unidadMedida completos.
        return productoRepository.findByIdWithJoins(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado tras actualizar"));
    }

    @Override
    public void eliminarProducto(Long id) {
        productoRepository.deleteById(id);
    }

    @Override
    @Transactional
    public ProductoImportResultDTO importar(List<ProductoImportRowDTO> filas, String tenantId) {
        int creados = 0, actualizados = 0;
        List<ProductoImportResultDTO.FilaError> errores = new ArrayList<>();

        // Unidad fallback: primera del tenant o global
        UnidadMedida unidadFallback = unidadMedidaRepository.findFirstByTenantIdOrGlobal(tenantId).orElse(null);

        for (int i = 0; i < filas.size(); i++) {
            ProductoImportRowDTO fila = filas.get(i);
            int numFila = i + 1;

            // ── Validaciones básicas ──────────────────────────────────────
            if (fila.getNombre() == null || fila.getNombre().isBlank()) {
                errores.add(err(numFila, fila.getNombre(), "El nombre es obligatorio"));
                continue;
            }
            if (fila.getPrecioVenta() == null || fila.getPrecioVenta().compareTo(BigDecimal.ZERO) <= 0) {
                errores.add(err(numFila, fila.getNombre(), "El precio de venta debe ser mayor a 0"));
                continue;
            }

            // ── Resolver unidad de medida ─────────────────────────────────
            UnidadMedida unidad = null;
            if (fila.getUnidadMedida() != null && !fila.getUnidadMedida().isBlank()) {
                List<UnidadMedida> matches = unidadMedidaRepository
                        .findByNombreAndTenantIdOrGlobal(fila.getUnidadMedida().trim(), tenantId);
                if (!matches.isEmpty()) {
                    unidad = matches.get(0); // prioriza la del tenant
                } else {
                    // Último intento: buscar por abreviatura o nombre parcial
                    unidad = unidadMedidaRepository.findAll().stream()
                            .filter(u -> u.getTenantId() == null || u.getTenantId().equals(tenantId))
                            .filter(u -> u.getNombre() != null &&
                                    u.getNombre().equalsIgnoreCase(fila.getUnidadMedida().trim()))
                            .findFirst().orElse(null);
                }
                if (unidad == null) {
                    errores.add(err(numFila, fila.getNombre(),
                            "Unidad de medida '" + fila.getUnidadMedida() + "' no encontrada. " +
                            "Usa exactamente el nombre configurado en el sistema."));
                    continue;
                }
            } else if (unidadFallback != null) {
                unidad = unidadFallback;
            } else {
                errores.add(err(numFila, fila.getNombre(), "No hay unidad de medida disponible"));
                continue;
            }

            // ── Crear o actualizar ────────────────────────────────────────
            try {
                Optional<Producto> existente = (fila.getCodigoBarras() != null && !fila.getCodigoBarras().isBlank())
                        ? productoRepository.findByCodigoBarras(fila.getCodigoBarras().trim())
                        : Optional.empty();

                if (existente.isPresent()) {
                    // UPDATE
                    Producto p = existente.get();
                    if (!p.getTenantId().equals(tenantId)) {
                        errores.add(err(numFila, fila.getNombre(), "Código de barras pertenece a otro tenant"));
                        continue;
                    }

                    int stockAnterior = p.getStockActual() != null ? p.getStockActual() : 0;
                    BigDecimal costoAnterior = p.getCostoUnitario() != null ? p.getCostoUnitario() : BigDecimal.ZERO;

                    p.setNombre(fila.getNombre().trim());
                    p.setPrecioVenta(fila.getPrecioVenta());
                    if (fila.getCostoUnitario() != null) p.setCostoUnitario(fila.getCostoUnitario());
                    if (fila.getStockActual() != null)   p.setStockActual(fila.getStockActual());
                    if (fila.getStockMinimo() != null)   p.setStockMinimo(fila.getStockMinimo());
                    if (fila.getStockMaximo() != null)   p.setStockMaximo(fila.getStockMaximo());
                    p.setUnidadMedida(unidad);
                    Producto saved = productoRepository.save(p);

                    // Registrar ajuste de stock si cambió
                    int stockNuevo = saved.getStockActual() != null ? saved.getStockActual() : 0;
                    int diferencia = stockNuevo - stockAnterior;
                    if (diferencia != 0) {
                        Long userId = TenantContext.getCurrentUserId();
                        Usuario usuario = usuarioRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));
                        BigDecimal costoFinal = saved.getCostoUnitario() != null ? saved.getCostoUnitario() : costoAnterior;
                        MovimientoInventario ajuste = MovimientoInventario.builder()
                                .producto(saved)
                                .usuario(usuario)
                                .tipo(diferencia > 0 ? "ENTRADA" : "SALIDA")
                                .cantidad(Math.abs(diferencia))
                                .descripcion("Ajuste de stock — importación masiva" +
                                        " (" + stockAnterior + " → " + stockNuevo + ")")
                                .referencia("IMPORTACION")
                                .tenantId(tenantId)
                                .costoUnitario(costoFinal)
                                .build();
                        movimientoInventarioRepository.save(ajuste);
                    }
                    actualizados++;

                } else {
                    // CREATE — nuevo producto + movimiento kardex de saldo inicial
                    int stockInicial = fila.getStockActual() != null ? fila.getStockActual() : 0;
                    BigDecimal costo = fila.getCostoUnitario() != null ? fila.getCostoUnitario() : BigDecimal.ZERO;

                    Producto p = Producto.builder()
                            .nombre(fila.getNombre().trim())
                            .codigoBarras(fila.getCodigoBarras() != null && !fila.getCodigoBarras().isBlank()
                                    ? fila.getCodigoBarras().trim() : null)
                            .precioVenta(fila.getPrecioVenta())
                            .costoUnitario(costo)
                            .stockActual(stockInicial)
                            .stockMinimo(fila.getStockMinimo() != null ? fila.getStockMinimo() : 10)
                            .stockMaximo(fila.getStockMaximo() != null ? fila.getStockMaximo() : 500)
                            .unidadMedida(unidad)
                            .activo(true)
                            .tenantId(tenantId)
                            .build();
                    Producto saved = productoRepository.save(p);

                    // Registrar movimiento kardex de saldo inicial
                    Long userId = TenantContext.getCurrentUserId();
                    Usuario usuario = usuarioRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));
                    MovimientoInventario mov = MovimientoInventario.builder()
                            .producto(saved)
                            .usuario(usuario)
                            .tipo("SALDO_INICIAL")
                            .cantidad(stockInicial)
                            .descripcion("Saldo inicial — importación masiva")
                            .referencia("IMPORTACION")
                            .tenantId(tenantId)
                            .costoUnitario(costo)
                            .build();
                    movimientoInventarioRepository.save(mov);
                    creados++;
                }
            } catch (Exception ex) {
                log.warn("⚠️ Error importando fila {}: {}", numFila, ex.getMessage());
                errores.add(err(numFila, fila.getNombre(), "Error inesperado: " + ex.getMessage()));
            }
        }

        log.info("📥 Importación tenant={}: {} creados, {} actualizados, {} errores",
                tenantId, creados, actualizados, errores.size());

        return ProductoImportResultDTO.builder()
                .total(filas.size())
                .creados(creados)
                .actualizados(actualizados)
                .errores(errores.size())
                .filaErrores(errores)
                .build();
    }

    private ProductoImportResultDTO.FilaError err(int fila, String nombre, String motivo) {
        return ProductoImportResultDTO.FilaError.builder()
                .fila(fila)
                .nombre(nombre != null ? nombre : "—")
                .motivo(motivo)
                .build();
    }
}