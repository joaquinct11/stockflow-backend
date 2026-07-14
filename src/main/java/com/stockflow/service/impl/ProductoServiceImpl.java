package com.stockflow.service.impl;

import com.stockflow.dto.ProductoImportResultDTO;
import com.stockflow.dto.ProductoImportRowDTO;
import com.stockflow.entity.Categoria;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoStockSucursal;
import com.stockflow.entity.ProductoVariante;
import com.stockflow.entity.ProductoVarianteStockSucursal;
import com.stockflow.entity.UnidadMedida;
import com.stockflow.entity.Usuario;
import com.stockflow.repository.CategoriaRepository;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.ProductoVarianteRepository;
import com.stockflow.repository.ProductoVarianteStockSucursalRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.UnidadMedidaRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.PlanLimitService;
import com.stockflow.service.ProductoService;
import com.stockflow.service.StockLoteService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final CategoriaRepository categoriaRepository;
    private final PlanLimitService planLimitService;
    private final SucursalRepository sucursalRepository;
    private final ProductoStockSucursalRepository stockSucursalRepository;
    private final StockLoteService stockLoteService;
    private final ProductoVarianteRepository varianteRepository;
    private final ProductoVarianteStockSucursalRepository varianteStockSucursalRepository;

    @Override
    @Transactional
    public Producto crearProducto(Producto producto) {
        planLimitService.validarLimiteProductos(producto.getTenantId());

        if (producto.getCodigoBarras() != null && producto.getCodigoBarras().isBlank()) {
            producto.setCodigoBarras(null);
        }

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

        // Inicializar stock por sucursal para tenants multi-local (plan Pro)
        int stockInicial = productoCreado.getStockActual() != null ? productoCreado.getStockActual() : 0;
        sucursalRepository.findByTenantIdAndActivoTrueOrderByEsPrincipalDescNombreAsc(productoCreado.getTenantId())
                .forEach(sucursal -> {
                    boolean esPrincipal = Boolean.TRUE.equals(sucursal.getEsPrincipal());
                    stockSucursalRepository.findByProductoIdAndSucursalId(productoCreado.getId(), sucursal.getId())
                            .orElseGet(() -> stockSucursalRepository.save(
                                    ProductoStockSucursal.builder()
                                            .producto(productoCreado)
                                            .sucursal(sucursal)
                                            .tenantId(productoCreado.getTenantId())
                                            .stockActual(esPrincipal ? stockInicial : 0)
                                            .build()
                            ));
                });

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
                    String cb = productoActualizado.getCodigoBarras();
                    producto.setCodigoBarras(cb != null && cb.isBlank() ? null : cb);
                    producto.setCategoriaRef(productoActualizado.getCategoriaRef());
                    producto.setCostoUnitario(productoActualizado.getCostoUnitario());
                    producto.setPrecioVenta(productoActualizado.getPrecioVenta());
                    producto.setUnidadMedida(productoActualizado.getUnidadMedida());
                    producto.setActivo(productoActualizado.getActivo());
                    producto.setStockActual(productoActualizado.getStockActual());
                    producto.setStockMinimo(productoActualizado.getStockMinimo());
                    producto.setStockMaximo(productoActualizado.getStockMaximo());
                    producto.setImagenUrl(productoActualizado.getImagenUrl());
                    producto.setComponentes(productoActualizado.getComponentes());
                    if (productoActualizado.getEsGenerico() != null) {
                        producto.setEsGenerico(productoActualizado.getEsGenerico());
                    }
                    producto.setUnidadesPorCaja(productoActualizado.getUnidadesPorCaja());
                    return productoRepository.save(producto);
                })
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        // Recargar con JOIN FETCH para devolver categoriaRef y unidadMedida completos.
        return productoRepository.findByIdWithJoins(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado tras actualizar"));
    }

    @Override
    @Transactional
    public void eliminarProducto(Long id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new com.stockflow.exception.ResourceNotFoundException("Producto no encontrado"));
        boolean tieneHistorial = productoRepository.tieneRegistrosRelacionados(id);
        if (tieneHistorial) {
            producto.setActivo(false);
            productoRepository.save(producto);
        } else {
            productoRepository.deleteById(id);
        }
    }

    @Override
    @Transactional
    public ProductoImportResultDTO importar(List<ProductoImportRowDTO> filas, String tenantId, Long sucursalId) {
        int creados = 0, actualizados = 0;
        List<ProductoImportResultDTO.FilaError> errores = new ArrayList<>();

        // Rastrear qué códigos/nombres ya se procesaron en este batch para soportar multi-lote / multi-variante
        Set<String> processedInBatch = new HashSet<>();
        // Para productos sin código de barras, guardar referencia al producto recién creado por clave-nombre
        java.util.Map<String, Producto> createdInBatch = new java.util.HashMap<>();

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

            // Clave única para detectar el mismo producto en filas consecutivas (multi-lote / multi-variante)
            String claveProducto = (fila.getCodigoBarras() != null && !fila.getCodigoBarras().isBlank())
                    ? "cod:" + fila.getCodigoBarras().trim().toLowerCase()
                    : "nom:" + fila.getNombre().trim().toLowerCase();
            boolean esFilaAdicional = processedInBatch.contains(claveProducto);
            boolean esVariante = fila.getTalla() != null || fila.getColor() != null || fila.getSkuVariante() != null;

            // ── Resolver unidad de medida ─────────────────────────────────
            UnidadMedida unidad = null;
            if (fila.getUnidadMedida() != null && !fila.getUnidadMedida().isBlank()) {
                List<UnidadMedida> matches = unidadMedidaRepository
                        .findByNombreAndTenantIdOrGlobal(fila.getUnidadMedida().trim(), tenantId);
                if (!matches.isEmpty()) {
                    unidad = matches.get(0);
                } else {
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

            // ── Resolver categoría ────────────────────────────────────────
            Categoria categoria = null;
            if (fila.getCategoria() != null && !fila.getCategoria().isBlank()) {
                String catNombre = fila.getCategoria().trim();
                categoria = categoriaRepository
                        .findByNombreIgnoreCaseAndTenantId(catNombre, tenantId)
                        .orElseGet(() -> categoriaRepository.save(
                                Categoria.builder()
                                        .nombre(catNombre)
                                        .tenantId(tenantId)
                                        .activo(true)
                                        .build()
                        ));
            }

            // ── Crear o actualizar ────────────────────────────────────────
            try {
                Optional<Producto> existente = (fila.getCodigoBarras() != null && !fila.getCodigoBarras().isBlank())
                        ? productoRepository.findByCodigoBarras(fila.getCodigoBarras().trim())
                        : Optional.empty();

                Long userId = TenantContext.getCurrentUserId();
                Usuario usuario = usuarioRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));

                // Para filas adicionales sin código de barras, recuperar el producto creado en este batch
                Optional<Producto> existenteEfectivo = existente.isPresent()
                        ? existente
                        : Optional.ofNullable(createdInBatch.get(claveProducto));

                if (esFilaAdicional && existenteEfectivo.isPresent()) {
                    // ── FILA ADICIONAL (lote extra o variante extra) ──────
                    Producto p = existenteEfectivo.get();
                    if (esVariante) {
                        crearOActualizarVariante(fila, p.getId(), tenantId, sucursalId);
                        syncVarianteStock(p.getId(), tenantId);
                        int stockVar = fila.getStockVariante() != null ? fila.getStockVariante() : 0;
                        if (stockVar > 0) {
                            BigDecimal costoVar = fila.getCostoUnitario() != null ? fila.getCostoUnitario()
                                    : (p.getCostoUnitario() != null ? p.getCostoUnitario() : BigDecimal.ZERO);
                            movimientoInventarioRepository.save(MovimientoInventario.builder()
                                    .producto(p)
                                    .usuario(usuario)
                                    .tipo("SALDO_INICIAL")
                                    .cantidad(stockVar)
                                    .descripcion("Saldo inicial — importación masiva" +
                                            (fila.getTalla() != null || fila.getColor() != null
                                                    ? " (" + (fila.getTalla() != null ? fila.getTalla() : "") +
                                                      (fila.getColor() != null ? " " + fila.getColor() : "") + ")" : ""))
                                    .referencia("IMPORTACION")
                                    .tenantId(tenantId)
                                    .sucursalId(sucursalId)
                                    .costoUnitario(costoVar)
                                    .build());
                        }
                    } else {
                        int cantidadLote = fila.getStockActual() != null ? fila.getStockActual() : 0;
                        if (cantidadLote > 0) {
                            p.setStockActual((p.getStockActual() != null ? p.getStockActual() : 0) + cantidadLote);
                            Producto saved = productoRepository.save(p);
                            BigDecimal costo = fila.getCostoUnitario() != null ? fila.getCostoUnitario()
                                    : (saved.getCostoUnitario() != null ? saved.getCostoUnitario() : BigDecimal.ZERO);
                            MovimientoInventario mov = MovimientoInventario.builder()
                                    .producto(saved)
                                    .usuario(usuario)
                                    .tipo("SALDO_INICIAL")
                                    .cantidad(cantidadLote)
                                    .descripcion("Saldo inicial — importación masiva (lote adicional: " +
                                            (fila.getLote() != null ? fila.getLote() : "sin lote") + ")")
                                    .referencia("IMPORTACION")
                                    .tenantId(tenantId)
                                    .costoUnitario(costo)
                                    .lote(fila.getLote())
                                    .fechaVencimiento(fila.getFechaVencimiento())
                                    .registroSanitario(fila.getRegistroSanitario())
                                    .build();
                            MovimientoInventario movGuardado = movimientoInventarioRepository.save(mov);
                            if (fila.getFechaVencimiento() != null) {
                                try {
                                    stockLoteService.registrarLote(tenantId, movGuardado.getId(),
                                            saved.getId(), sucursalId, fila.getLote(),
                                            fila.getFechaVencimiento(), cantidadLote);
                                } catch (Exception e) {
                                    log.warn("⚠️ No se pudo registrar stock_lote para fila {}: {}", numFila, e.getMessage());
                                }
                            }
                            if (sucursalId != null) {
                                actualizarStockSucursal(saved.getId(), sucursalId, cantidadLote);
                            }
                        }
                    }
                    actualizados++;

                } else if (existenteEfectivo.isPresent()) {
                    // ── UPDATE — primera aparición de producto ya en BD ────
                    Producto p = existenteEfectivo.get();
                    if (!p.getTenantId().equals(tenantId)) {
                        errores.add(err(numFila, fila.getNombre(), "Código de barras pertenece a otro tenant"));
                        continue;
                    }

                    p.setNombre(fila.getNombre().trim());
                    p.setPrecioVenta(fila.getPrecioVenta());
                    if (fila.getCostoUnitario() != null) p.setCostoUnitario(fila.getCostoUnitario());
                    if (fila.getStockMinimo() != null)   p.setStockMinimo(fila.getStockMinimo());
                    if (fila.getStockMaximo() != null)   p.setStockMaximo(fila.getStockMaximo());
                    p.setUnidadMedida(unidad);
                    if (categoria != null) p.setCategoriaRef(categoria);

                    if (esVariante) {
                        // Stock calculado desde variantes — no sobreescribir directamente
                        Producto saved = productoRepository.save(p);
                        crearOActualizarVariante(fila, saved.getId(), tenantId, sucursalId);
                        syncVarianteStock(saved.getId(), tenantId);
                        int stockVar = fila.getStockVariante() != null ? fila.getStockVariante() : 0;
                        if (stockVar > 0) {
                            BigDecimal costoVar = fila.getCostoUnitario() != null ? fila.getCostoUnitario()
                                    : (saved.getCostoUnitario() != null ? saved.getCostoUnitario() : BigDecimal.ZERO);
                            movimientoInventarioRepository.save(MovimientoInventario.builder()
                                    .producto(saved)
                                    .usuario(usuario)
                                    .tipo("SALDO_INICIAL")
                                    .cantidad(stockVar)
                                    .descripcion("Saldo inicial — importación masiva" +
                                            (fila.getTalla() != null || fila.getColor() != null
                                                    ? " (" + (fila.getTalla() != null ? fila.getTalla() : "") +
                                                      (fila.getColor() != null ? " " + fila.getColor() : "") + ")" : ""))
                                    .referencia("IMPORTACION")
                                    .tenantId(tenantId)
                                    .sucursalId(sucursalId)
                                    .costoUnitario(costoVar)
                                    .build());
                        }
                    } else {
                        int stockAnterior = p.getStockActual() != null ? p.getStockActual() : 0;
                        BigDecimal costoAnterior = p.getCostoUnitario() != null ? p.getCostoUnitario() : BigDecimal.ZERO;
                        if (fila.getStockActual() != null) p.setStockActual(fila.getStockActual());
                        Producto saved = productoRepository.save(p);

                        int stockNuevo = saved.getStockActual() != null ? saved.getStockActual() : 0;
                        int diferencia = stockNuevo - stockAnterior;
                        if (diferencia != 0) {
                            BigDecimal costoFinal = saved.getCostoUnitario() != null ? saved.getCostoUnitario() : costoAnterior;
                            String tipoAjuste = diferencia > 0 ? "ENTRADA" : "SALIDA";
                            MovimientoInventario ajuste = MovimientoInventario.builder()
                                    .producto(saved)
                                    .usuario(usuario)
                                    .tipo(tipoAjuste)
                                    .cantidad(Math.abs(diferencia))
                                    .descripcion("Ajuste de stock — importación masiva" +
                                            " (" + stockAnterior + " → " + stockNuevo + ")")
                                    .referencia("IMPORTACION")
                                    .tenantId(tenantId)
                                    .costoUnitario(costoFinal)
                                    .lote(fila.getLote())
                                    .fechaVencimiento(fila.getFechaVencimiento())
                                    .registroSanitario(fila.getRegistroSanitario())
                                    .build();
                            MovimientoInventario ajusteGuardado = movimientoInventarioRepository.save(ajuste);
                            if (fila.getFechaVencimiento() != null && "ENTRADA".equals(tipoAjuste)) {
                                try {
                                    stockLoteService.registrarLote(tenantId, ajusteGuardado.getId(),
                                            saved.getId(), sucursalId, fila.getLote(),
                                            fila.getFechaVencimiento(), Math.abs(diferencia));
                                } catch (Exception e) {
                                    log.warn("⚠️ No se pudo registrar stock_lote para fila {}: {}", numFila, e.getMessage());
                                }
                            }
                            if (sucursalId != null) {
                                int delta = "ENTRADA".equals(tipoAjuste) ? Math.abs(diferencia) : -Math.abs(diferencia);
                                actualizarStockSucursal(saved.getId(), sucursalId, delta);
                            }
                        }
                    }
                    actualizados++;

                } else {
                    // ── CREATE — producto nuevo ────────────────────────────
                    BigDecimal costo = fila.getCostoUnitario() != null ? fila.getCostoUnitario() : BigDecimal.ZERO;

                    if (esVariante) {
                        Producto p = Producto.builder()
                                .nombre(fila.getNombre().trim())
                                .codigoBarras(fila.getCodigoBarras() != null && !fila.getCodigoBarras().isBlank()
                                        ? fila.getCodigoBarras().trim() : null)
                                .precioVenta(fila.getPrecioVenta())
                                .costoUnitario(costo)
                                .stockActual(0)
                                .stockMinimo(fila.getStockMinimo() != null ? fila.getStockMinimo() : 0)
                                .stockMaximo(fila.getStockMaximo() != null ? fila.getStockMaximo() : 500)
                                .unidadMedida(unidad)
                                .categoriaRef(categoria)
                                .activo(true)
                                .tenantId(tenantId)
                                .build();
                        Producto saved = productoRepository.save(p);
                        createdInBatch.put(claveProducto, saved);
                        crearOActualizarVariante(fila, saved.getId(), tenantId, sucursalId);
                        syncVarianteStock(saved.getId(), tenantId);
                        int stockVar = fila.getStockVariante() != null ? fila.getStockVariante() : 0;
                        if (stockVar > 0) {
                            movimientoInventarioRepository.save(MovimientoInventario.builder()
                                    .producto(saved)
                                    .usuario(usuario)
                                    .tipo("SALDO_INICIAL")
                                    .cantidad(stockVar)
                                    .descripcion("Saldo inicial — importación masiva" +
                                            (fila.getTalla() != null || fila.getColor() != null
                                                    ? " (" + (fila.getTalla() != null ? fila.getTalla() : "") +
                                                      (fila.getColor() != null ? " " + fila.getColor() : "") + ")" : ""))
                                    .referencia("IMPORTACION")
                                    .tenantId(tenantId)
                                    .sucursalId(sucursalId)
                                    .costoUnitario(costo)
                                    .build());
                        }
                    } else {
                        int stockInicial = fila.getStockActual() != null ? fila.getStockActual() : 0;
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
                                .categoriaRef(categoria)
                                .activo(true)
                                .tenantId(tenantId)
                                .build();
                        Producto saved = productoRepository.save(p);
                        createdInBatch.put(claveProducto, saved);

                        MovimientoInventario mov = MovimientoInventario.builder()
                                .producto(saved)
                                .usuario(usuario)
                                .tipo("SALDO_INICIAL")
                                .cantidad(stockInicial)
                                .descripcion("Saldo inicial — importación masiva" +
                                        (fila.getLote() != null && !fila.getLote().isBlank()
                                                ? " (lote: " + fila.getLote() + ")" : ""))
                                .referencia("IMPORTACION")
                                .tenantId(tenantId)
                                .costoUnitario(costo)
                                .lote(fila.getLote())
                                .fechaVencimiento(fila.getFechaVencimiento())
                                .registroSanitario(fila.getRegistroSanitario())
                                .build();
                        MovimientoInventario movGuardado = movimientoInventarioRepository.save(mov);
                        if (fila.getFechaVencimiento() != null && stockInicial > 0) {
                            try {
                                stockLoteService.registrarLote(tenantId, movGuardado.getId(),
                                        saved.getId(), sucursalId, fila.getLote(),
                                        fila.getFechaVencimiento(), stockInicial);
                            } catch (Exception e) {
                                log.warn("⚠️ No se pudo registrar stock_lote para fila {}: {}", numFila, e.getMessage());
                            }
                        }
                        if (sucursalId != null && stockInicial > 0) {
                            crearStockSucursal(saved.getId(), sucursalId, stockInicial, tenantId);
                        }
                    }
                    creados++;
                }

                processedInBatch.add(claveProducto);

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

    private void crearOActualizarVariante(ProductoImportRowDTO fila, Long productoId, String tenantId, Long sucursalId) {
        String talla = fila.getTalla() != null ? fila.getTalla().trim() : null;
        String color = fila.getColor() != null ? fila.getColor().trim() : null;
        String sku   = fila.getSkuVariante() != null ? fila.getSkuVariante().trim() : null;
        int stock    = fila.getStockVariante() != null ? fila.getStockVariante() : 0;
        int stockMin = fila.getStockMinimoVariante() != null ? fila.getStockMinimoVariante() : 0;

        // Buscar variante existente por talla+color+sku para no duplicar
        ProductoVariante variante = varianteRepository.findByProductoIdAndTenantId(productoId, tenantId).stream()
                .filter(v -> Boolean.TRUE.equals(v.getActivo()))
                .filter(v -> equals(v.getTalla(), talla) && equals(v.getColor(), color) && equals(v.getSku(), sku))
                .findFirst()
                .map(v -> {
                    v.setStockActual(stock);
                    v.setStockMinimo(stockMin);
                    return varianteRepository.save(v);
                })
                .orElseGet(() -> varianteRepository.save(ProductoVariante.builder()
                        .productoId(productoId)
                        .talla(talla)
                        .color(color)
                        .sku(sku)
                        .stockActual(stock)
                        .stockMinimo(stockMin)
                        .activo(true)
                        .tenantId(tenantId)
                        .createdAt(java.time.LocalDateTime.now())
                        .build()));

        // Si hay sucursal seleccionada (plan PRO multi-local), registrar stock por sucursal
        if (sucursalId != null) {
            List<com.stockflow.entity.Sucursal> sucursales =
                    sucursalRepository.findByTenantIdAndActivoTrueOrderByEsPrincipalDescNombreAsc(tenantId);
            final Long varianteId = variante.getId();
            for (com.stockflow.entity.Sucursal suc : sucursales) {
                int stockSuc = suc.getId().equals(sucursalId) ? stock : 0;
                varianteStockSucursalRepository.findByVarianteIdAndSucursalId(varianteId, suc.getId())
                        .ifPresentOrElse(
                                entry -> {
                                    entry.setStockActual(entry.getStockActual() + stockSuc);
                                    varianteStockSucursalRepository.save(entry);
                                },
                                () -> varianteStockSucursalRepository.save(
                                        ProductoVarianteStockSucursal.builder()
                                                .varianteId(varianteId)
                                                .sucursalId(suc.getId())
                                                .tenantId(tenantId)
                                                .stockActual(stockSuc)
                                                .stockMinimo(0)
                                                .build())
                        );
            }
        }
    }

    private void syncVarianteStock(Long productoId, String tenantId) {
        int total = varianteRepository.findByProductoIdAndActivoTrueAndTenantId(productoId, tenantId)
                .stream().mapToInt(v -> v.getStockActual() != null ? v.getStockActual() : 0).sum();
        productoRepository.findById(productoId).ifPresent(p -> {
            p.setStockActual(total);
            productoRepository.save(p);
        });
    }

    private static boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private void crearStockSucursal(Long productoId, Long sucursalId, int stock, String tenantId) {
        boolean existe = stockSucursalRepository.findByProductoIdAndSucursalId(productoId, sucursalId).isPresent();
        if (!existe) {
            sucursalRepository.findById(sucursalId).ifPresent(suc ->
                    productoRepository.findById(productoId).ifPresent(prod -> {
                        ProductoStockSucursal entry = ProductoStockSucursal.builder()
                                .producto(prod)
                                .sucursal(suc)
                                .tenantId(tenantId)
                                .stockActual(stock)
                                .build();
                        stockSucursalRepository.save(entry);
                    })
            );
        }
    }

    private void actualizarStockSucursal(Long productoId, Long sucursalId, int delta) {
        stockSucursalRepository.findByProductoIdAndSucursalId(productoId, sucursalId).ifPresent(entry -> {
            entry.setStockActual((entry.getStockActual() != null ? entry.getStockActual() : 0) + delta);
            stockSucursalRepository.save(entry);
        });
    }
}