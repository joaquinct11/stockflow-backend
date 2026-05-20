package com.stockflow.service.impl;

import com.stockflow.dto.reportes.*;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.repository.GastoRepository;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.RecepcionRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.ReportesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportesServiceImpl implements ReportesService {

    private final ProductoRepository             productoRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final RecepcionRepository            recepcionRepository;
    private final VentaRepository                ventaRepository;
    private final GastoRepository                gastoRepository;

    @Override
    @Transactional(readOnly = true)
    public ReportesResumenDTO obtenerResumen(String tenantId, LocalDate desde, LocalDate hasta) {
        log.info("📊 Generando resumen de reportes para tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        return ReportesResumenDTO.builder()
                .rango(RangoDTO.builder().desde(desde).hasta(hasta).build())
                .inventario(buildInventario(tenantId))
                .movimientos(buildMovimientos(tenantId, inicio, fin))
                .comprasRecepciones(buildComprasRecepciones(tenantId, inicio, fin))
                .ventas(buildVentas(tenantId, inicio, fin))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VentaTendenciaDTO> tendenciaVentas(String tenantId, LocalDate desde, LocalDate hasta,
                                                    String agrupacion) {
        log.info("📈 Tendencia ventas tenant={} rango=[{}, {}] agrupacion={}", tenantId, desde, hasta, agrupacion);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Object[]> raw = switch (agrupacion.toUpperCase()) {
            case "SEMANA" -> ventaRepository.findTendenciaSemanal(tenantId, inicio, fin);
            case "MES"    -> ventaRepository.findTendenciaMensual(tenantId, inicio, fin);
            default       -> ventaRepository.findTendenciaDiaria(tenantId, inicio, fin);
        };

        return raw.stream()
                .map(row -> VentaTendenciaDTO.builder()
                        .periodo((String) row[0])
                        .ventasCount(((Number) row[1]).longValue())
                        .ingresosTotal(toBigDecimal(row[2]))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VentaVendedorDTO> ventasPorVendedor(String tenantId, LocalDate desde, LocalDate hasta, int limit) {
        log.info("👤 Ventas por vendedor tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Object[]> raw = ventaRepository.findVentasPorVendedor(tenantId, inicio, fin);

        return raw.stream()
                .limit(limit)
                .map(row -> {
                    long count = ((Number) row[2]).longValue();
                    BigDecimal ingresos = (BigDecimal) row[3];
                    BigDecimal ticket = count > 0
                            ? ingresos.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return VentaVendedorDTO.builder()
                            .vendedorId(((Number) row[0]).longValue())
                            .vendedorNombre((String) row[1])
                            .ventasCount(count)
                            .ingresosTotal(ingresos)
                            .ticketPromedio(ticket)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VentaCategoriaDTO> ventasPorCategoria(String tenantId, LocalDate desde, LocalDate hasta, int limit) {
        log.info("🏷️ Ventas por categoría tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Object[]> raw = ventaRepository.findVentasPorCategoria(tenantId, inicio, fin);

        return raw.stream()
                .limit(limit)
                .map(row -> VentaCategoriaDTO.builder()
                        .categoria((String) row[0])
                        .unidades(((Number) row[1]).longValue())
                        .ingresosTotal(toBigDecimal(row[2]))
                        .ventasCount(((Number) row[3]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VentaMetodoPagoDTO> ventasPorMetodoPago(String tenantId, LocalDate desde, LocalDate hasta) {
        log.info("💳 Ventas por método de pago tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Object[]> raw = ventaRepository.findVentasPorMetodoPago(tenantId, inicio, fin);

        BigDecimal totalIngresos = raw.stream()
                .map(row -> toBigDecimal(row[2]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return raw.stream()
                .map(row -> {
                    BigDecimal ingresos = toBigDecimal(row[2]);
                    BigDecimal pct = totalIngresos.compareTo(BigDecimal.ZERO) > 0
                            ? ingresos.multiply(BigDecimal.valueOf(100))
                                      .divide(totalIngresos, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return VentaMetodoPagoDTO.builder()
                            .metodoPago((String) row[0])
                            .ventasCount(((Number) row[1]).longValue())
                            .ingresosTotal(ingresos)
                            .porcentaje(pct)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoVentaDTO> productosVendidos(String tenantId, LocalDate desde, LocalDate hasta,
                                                     int limit, String orden, String metrica) {
        log.info("📦 Productos vendidos tenant={} rango=[{}, {}] orden={} metrica={}", tenantId, desde, hasta, orden, metrica);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        boolean esMas = "MAS".equalsIgnoreCase(orden);
        boolean esIngresos = "INGRESOS".equalsIgnoreCase(metrica);

        List<Object[]> raw;
        if (esMas && esIngresos) {
            raw = ventaRepository.findTopProductosVendidosPorIngresos(tenantId, inicio, fin);
        } else if (esMas) {
            raw = ventaRepository.findTopProductosVendidos(tenantId, inicio, fin);
        } else if (esIngresos) {
            raw = ventaRepository.findBottomProductosVendidosPorIngresos(tenantId, inicio, fin);
        } else {
            raw = ventaRepository.findBottomProductosVendidosPorUnidades(tenantId, inicio, fin);
        }

        return raw.stream()
                .limit(limit)
                .map(row -> ProductoVentaDTO.builder()
                        .productoId(((Number) row[0]).longValue())
                        .nombre((String) row[1])
                        .cantidad(((Number) row[2]).longValue())
                        .ingresos((BigDecimal) row[3])
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoAbcDTO> clasificacionAbc(String tenantId, LocalDate desde, LocalDate hasta, int limit) {
        log.info("🔡 Clasificación ABC tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        // Obtener todos los productos ordenados por ingresos desc (sin límite para calcular % correctos)
        List<Object[]> raw = ventaRepository.findTopProductosVendidosPorIngresos(tenantId, inicio, fin);

        if (raw.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal totalIngresos = raw.stream()
                .map(row -> (BigDecimal) row[3])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalIngresos.compareTo(BigDecimal.ZERO) == 0) {
            return Collections.emptyList();
        }

        List<ProductoAbcDTO> result = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;

        for (Object[] row : raw) {
            BigDecimal ingresos = (BigDecimal) row[3];
            BigDecimal pct = ingresos.multiply(BigDecimal.valueOf(100))
                    .divide(totalIngresos, 2, RoundingMode.HALF_UP);
            acumulado = acumulado.add(pct);

            String clasificacion;
            if (acumulado.compareTo(BigDecimal.valueOf(80)) <= 0) {
                clasificacion = "A";
            } else if (acumulado.compareTo(BigDecimal.valueOf(95)) <= 0) {
                clasificacion = "B";
            } else {
                clasificacion = "C";
            }

            result.add(ProductoAbcDTO.builder()
                    .productoId(((Number) row[0]).longValue())
                    .nombre((String) row[1])
                    .ingresos(ingresos)
                    .porcentaje(pct)
                    .porcentajeAcumulado(acumulado)
                    .clasificacion(clasificacion)
                    .build());
        }

        return result.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlowMoverDTO> slowMovers(String tenantId, int diasSinSalida, int limit) {
        log.info("🐢 Slow movers tenant={} diasSinSalida={}", tenantId, diasSinSalida);

        LocalDateTime desde = LocalDateTime.now().minusDays(diasSinSalida);
        List<Producto> productos = productoRepository.findSlowMovers(tenantId, desde);

        return productos.stream()
                .limit(limit)
                .map(p -> {
                    BigDecimal costoTotal = p.getCostoUnitario() != null
                            ? p.getCostoUnitario().multiply(BigDecimal.valueOf(p.getStockActual()))
                            : null;
                    return SlowMoverDTO.builder()
                            .productoId(p.getId())
                            .nombre(p.getNombre())
                            .stockActual(p.getStockActual())
                            .costoUnitario(p.getCostoUnitario())
                            .costoTotal(costoTotal)
                            .diasSinSalida(diasSinSalida)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoberturaProductoDTO> coberturaInventario(String tenantId, LocalDate desde, LocalDate hasta, int limit) {
        log.info("📐 Cobertura inventario tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Object[]> salidasRaw = movimientoRepository.findSalidasPorProductoEnRango(tenantId, inicio, fin);

        long diasRango = ChronoUnit.DAYS.between(desde, hasta) + 1;

        List<CoberturaProductoDTO> result = salidasRaw.stream()
                .map(row -> {
                    Long productoId = ((Number) row[0]).longValue();
                    String nombre = (String) row[1];
                    Integer stockActual = ((Number) row[2]).intValue();
                    long totalSalidas = ((Number) row[3]).longValue();

                    BigDecimal promedio = BigDecimal.valueOf(totalSalidas)
                            .divide(BigDecimal.valueOf(diasRango), 4, RoundingMode.HALF_UP);

                    BigDecimal diasCobertura = null;
                    if (promedio.compareTo(BigDecimal.ZERO) > 0 && stockActual > 0) {
                        diasCobertura = BigDecimal.valueOf(stockActual)
                                .divide(promedio, 1, RoundingMode.HALF_UP);
                    }

                    return CoberturaProductoDTO.builder()
                            .productoId(productoId)
                            .nombre(nombre)
                            .stockActual(stockActual)
                            .promedioSalidasDiarias(promedio)
                            .diasCobertura(diasCobertura)
                            .build();
                })
                .sorted((a, b) -> {
                    if (a.getDiasCobertura() == null && b.getDiasCobertura() == null) return 0;
                    if (a.getDiasCobertura() == null) return 1;
                    if (b.getDiasCobertura() == null) return -1;
                    return a.getDiasCobertura().compareTo(b.getDiasCobertura());
                })
                .limit(limit)
                .collect(Collectors.toList());

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompraProveedorDTO> comprasPorProveedor(String tenantId, LocalDate desde, LocalDate hasta, int limit) {
        log.info("🛒 Compras por proveedor tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.atTime(LocalTime.MAX);

        List<Object[]> raw = recepcionRepository.findComprasPorProveedor(tenantId, inicio, fin);

        return raw.stream()
                .limit(limit)
                .map(row -> CompraProveedorDTO.builder()
                        .proveedorId(((Number) row[0]).longValue())
                        .proveedorNombre((String) row[1])
                        .recepcionesCount(((Number) row[2]).longValue())
                        .unidadesRecibidas(((Number) row[3]).longValue())
                        .montoEstimado((BigDecimal) row[4])
                        .build())
                .collect(Collectors.toList());
    }

    // ── Financiero (P&L) ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public FinancieroDTO getFinanciero(String tenantId, LocalDate desde, LocalDate hasta) {
        log.info("💰 Financiero tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin    = hasta.atTime(LocalTime.MAX);

        // Ingresos
        long       ventasCount    = ventaRepository.countByTenantIdAndPeriodo(tenantId, inicio, fin);
        BigDecimal ingresosVentas = ventaRepository.sumTotalByTenantIdAndPeriodo(tenantId, inicio, fin);
        if (ingresosVentas == null) ingresosVentas = BigDecimal.ZERO;

        // Costo de ventas
        BigDecimal costoVentas = ventaRepository.sumCostoVentasByTenantIdAndPeriodo(tenantId, inicio, fin);
        if (costoVentas == null) costoVentas = BigDecimal.ZERO;

        // Utilidad bruta
        BigDecimal utilidadBruta = ingresosVentas.subtract(costoVentas);
        BigDecimal margenBruto   = ingresosVentas.compareTo(BigDecimal.ZERO) > 0
                ? utilidadBruta.multiply(BigDecimal.valueOf(100))
                               .divide(ingresosVentas, 2, RoundingMode.HALF_UP)
                : null;

        // Gastos operativos del período
        List<com.stockflow.entity.Gasto> gastos = gastoRepository
                .findByTenantIdAndFechaGastoBetween(tenantId, desde, hasta);

        BigDecimal gastosTotales = gastos.stream()
                .map(com.stockflow.entity.Gasto::getMonto)
                .filter(m -> m != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long gastosCount = gastos.size();

        // Gastos por categoría
        Map<String, List<com.stockflow.entity.Gasto>> porCategoria = gastos.stream()
                .collect(Collectors.groupingBy(g -> g.getCategoria() != null ? g.getCategoria() : "OTROS"));

        List<GastoCategoriaDTO> gastosPorCategoria = porCategoria.entrySet().stream()
                .map(e -> {
                    BigDecimal montoCateg = e.getValue().stream()
                            .map(com.stockflow.entity.Gasto::getMonto)
                            .filter(m -> m != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal pct = gastosTotales.compareTo(BigDecimal.ZERO) > 0
                            ? montoCateg.multiply(BigDecimal.valueOf(100))
                                        .divide(gastosTotales, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return GastoCategoriaDTO.builder()
                            .categoria(e.getKey())
                            .monto(montoCateg)
                            .porcentaje(pct)
                            .count((long) e.getValue().size())
                            .build();
                })
                .sorted(Comparator.comparing(GastoCategoriaDTO::getMonto).reversed())
                .collect(Collectors.toList());

        // Utilidad neta
        BigDecimal utilidadNeta = utilidadBruta.subtract(gastosTotales);
        BigDecimal margenNeto   = ingresosVentas.compareTo(BigDecimal.ZERO) > 0
                ? utilidadNeta.multiply(BigDecimal.valueOf(100))
                              .divide(ingresosVentas, 2, RoundingMode.HALF_UP)
                : null;

        return FinancieroDTO.builder()
                .desde(desde)
                .hasta(hasta)
                .ingresosVentas(ingresosVentas)
                .ventasCount(ventasCount)
                .costoVentas(costoVentas)
                .utilidadBruta(utilidadBruta)
                .margenBruto(margenBruto)
                .gastosTotales(gastosTotales)
                .gastosCount(gastosCount)
                .gastosPorCategoria(gastosPorCategoria)
                .utilidadNeta(utilidadNeta)
                .margenNeto(margenNeto)
                .build();
    }

    // ── Vencimientos en riesgo ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public VencimientosRiesgoDTO getVencimientosRiesgo(String tenantId) {
        log.info("⚠️ Vencimientos en riesgo tenant={}", tenantId);

        LocalDate hoy = LocalDate.now();

        List<MovimientoInventario> movimientos =
                movimientoRepository.findEntradasConVencimientoPorTenant(tenantId);

        BigDecimal capitalVencido  = BigDecimal.ZERO;
        BigDecimal capitalRiesgo7d = BigDecimal.ZERO;
        BigDecimal capitalRiesgo30d = BigDecimal.ZERO;
        BigDecimal capitalRiesgo90d = BigDecimal.ZERO;
        long lotesVencidos = 0, lotes7d = 0, lotes30d = 0, lotes90d = 0;

        List<LoteRiesgoDetalleDTO> detalles = new ArrayList<>();

        for (MovimientoInventario m : movimientos) {
            long diasRestantes = ChronoUnit.DAYS.between(hoy, m.getFechaVencimiento());
            BigDecimal costo   = m.getProducto().getCostoUnitario() != null
                    ? m.getProducto().getCostoUnitario() : BigDecimal.ZERO;
            BigDecimal valor   = costo.multiply(BigDecimal.valueOf(m.getCantidad() != null ? m.getCantidad() : 0));

            LoteRiesgoDetalleDTO detalle = LoteRiesgoDetalleDTO.builder()
                    .productoId(m.getProducto().getId())
                    .productoNombre(m.getProducto().getNombre())
                    .lote(m.getLote())
                    .fechaVencimiento(m.getFechaVencimiento())
                    .diasRestantes(diasRestantes)
                    .cantidad(m.getCantidad())
                    .costoUnitario(costo)
                    .valorRiesgo(valor)
                    .build();
            detalles.add(detalle);

            if (diasRestantes < 0) {
                capitalVencido = capitalVencido.add(valor);
                lotesVencidos++;
            } else if (diasRestantes <= 7) {
                capitalRiesgo7d = capitalRiesgo7d.add(valor);
                lotes7d++;
            } else if (diasRestantes <= 30) {
                capitalRiesgo30d = capitalRiesgo30d.add(valor);
                lotes30d++;
            } else if (diasRestantes <= 90) {
                capitalRiesgo90d = capitalRiesgo90d.add(valor);
                lotes90d++;
            }
        }

        // Los 15 más urgentes: primero los vencidos (diasRestantes ASC) luego próximos
        List<LoteRiesgoDetalleDTO> urgentes = detalles.stream()
                .filter(d -> d.getDiasRestantes() <= 90)
                .sorted(Comparator.comparing(LoteRiesgoDetalleDTO::getDiasRestantes))
                .limit(15)
                .collect(Collectors.toList());

        BigDecimal totalCritico = capitalVencido.add(capitalRiesgo7d).add(capitalRiesgo30d);

        return VencimientosRiesgoDTO.builder()
                .capitalVencido(capitalVencido)
                .lotesVencidos(lotesVencidos)
                .capitalRiesgo7d(capitalRiesgo7d)
                .lotesRiesgo7d(lotes7d)
                .capitalRiesgo30d(capitalRiesgo30d)
                .lotesRiesgo30d(lotes30d)
                .capitalRiesgo90d(capitalRiesgo90d)
                .lotesRiesgo90d(lotes90d)
                .totalCapitalCritico(totalCritico)
                .lotesUrgentes(urgentes)
                .build();
    }

    // ── Top clientes ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ClienteReporteDTO> getTopClientes(String tenantId, LocalDate desde, LocalDate hasta, int limit) {
        log.info("👤 Top clientes tenant={} rango=[{}, {}]", tenantId, desde, hasta);

        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin    = hasta.atTime(LocalTime.MAX);

        List<Object[]> raw = ventaRepository.findTopClientesPorGasto(tenantId, inicio, fin);

        return raw.stream()
                .limit(limit)
                .map(row -> {
                    long  count     = ((Number) row[2]).longValue();
                    BigDecimal total = toBigDecimal(row[3]);
                    BigDecimal ticket = count > 0
                            ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    LocalDateTime ultima = null;
                    if (row[4] instanceof java.sql.Timestamp ts) {
                        ultima = ts.toLocalDateTime();
                    } else if (row[4] instanceof LocalDateTime ldt) {
                        ultima = ldt;
                    }
                    return ClienteReporteDTO.builder()
                            .clienteId(((Number) row[0]).longValue())
                            .clienteNombre((String) row[1])
                            .ventasCount(count)
                            .totalComprado(total)
                            .ticketPromedio(ticket)
                            .ultimaCompra(ultima)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private InventarioResumenDTO buildInventario(String tenantId) {
        long totalProductos = productoRepository.countByTenantId(tenantId);

        List<Producto> bajoStock = productoRepository.findProductosBajoStock(tenantId);
        List<ProductoBajoStockDTO> productosBajoStock = bajoStock.stream()
                .limit(10)
                .map(p -> ProductoBajoStockDTO.builder()
                        .productoId(p.getId())
                        .nombre(p.getNombre())
                        .stockActual(p.getStockActual())
                        .stockMinimo(p.getStockMinimo())
                        .build())
                .toList();

        BigDecimal valorizacion = productoRepository.calcularValorizacionStock(tenantId);

        return InventarioResumenDTO.builder()
                .totalProductos(totalProductos)
                .productosBajoStock(productosBajoStock)
                .valorizacionStock(valorizacion)
                .build();
    }

    private MovimientosResumenDTO buildMovimientos(String tenantId, LocalDateTime inicio, LocalDateTime fin) {
        long entradas = movimientoRepository.sumCantidadByTenantIdAndTipoAndPeriodo(tenantId, "ENTRADA", inicio, fin);
        long salidas = movimientoRepository.sumCantidadByTenantIdAndTipoAndPeriodo(tenantId, "SALIDA", inicio, fin);

        List<Object[]> rawTop = movimientoRepository.findTopMovimientosProductos(tenantId, inicio, fin);
        List<TopMovimientoProductoDTO> topMovimientos = new ArrayList<>();
        int limitMov = Math.min(rawTop.size(), 10);
        for (int i = 0; i < limitMov; i++) {
            Object[] row = rawTop.get(i);
            topMovimientos.add(TopMovimientoProductoDTO.builder()
                    .productoId(((Number) row[0]).longValue())
                    .nombre((String) row[1])
                    .tipo((String) row[2])
                    .cantidadTotal(((Number) row[3]).longValue())
                    .build());
        }

        return MovimientosResumenDTO.builder()
                .entradasCantidad(entradas)
                .salidasCantidad(salidas)
                .topMovimientosProductos(topMovimientos)
                .build();
    }

    private ComprasRecepcionesResumenDTO buildComprasRecepciones(String tenantId, LocalDateTime inicio, LocalDateTime fin) {
        long recepcionesConfirmadas = recepcionRepository.countConfirmadasByTenantIdAndPeriodo(tenantId, inicio, fin);
        long unidadesRecibidas = recepcionRepository.sumUnidadesRecibidasByTenantIdAndPeriodo(tenantId, inicio, fin);
        BigDecimal montoCompras = recepcionRepository.sumMontoComprasByTenantIdAndPeriodo(tenantId, inicio, fin);

        return ComprasRecepcionesResumenDTO.builder()
                .recepcionesConfirmadasCount(recepcionesConfirmadas)
                .unidadesRecibidas(unidadesRecibidas)
                .montoComprasEstimado(montoCompras)
                .build();
    }

    private VentasResumenDTO buildVentas(String tenantId, LocalDateTime inicio, LocalDateTime fin) {
        long ventasCount = ventaRepository.countByTenantIdAndPeriodo(tenantId, inicio, fin);

        if (ventasCount == 0) {
            return null;
        }

        BigDecimal ingresosTotal = ventaRepository.sumTotalByTenantIdAndPeriodo(tenantId, inicio, fin);
        if (ingresosTotal == null) ingresosTotal = BigDecimal.ZERO;

        BigDecimal ticketPromedio = ventasCount > 0
                ? ingresosTotal.divide(BigDecimal.valueOf(ventasCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Object[]> rawTop = ventaRepository.findTopProductosVendidos(tenantId, inicio, fin);
        List<TopProductoVendidoDTO> topProductos = new ArrayList<>();
        int limitTop = Math.min(rawTop.size(), 10);
        for (int i = 0; i < limitTop; i++) {
            Object[] row = rawTop.get(i);
            topProductos.add(TopProductoVendidoDTO.builder()
                    .productoId(((Number) row[0]).longValue())
                    .nombre((String) row[1])
                    .cantidadVendida(((Number) row[2]).longValue())
                    .ingresos((BigDecimal) row[3])
                    .build());
        }

        BigDecimal costoTotal = ventaRepository.sumCostoVentasByTenantIdAndPeriodo(tenantId, inicio, fin);
        BigDecimal margen = costoTotal != null ? ingresosTotal.subtract(costoTotal) : null;

        return VentasResumenDTO.builder()
                .ventasCount(ventasCount)
                .ingresosTotal(ingresosTotal)
                .ticketPromedio(ticketPromedio)
                .topProductosVendidos(topProductos)
                .margenEstimado(margen)
                .build();
    }

    /** Convierte un valor de Object[] a BigDecimal de forma segura (null → ZERO). */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return BigDecimal.ZERO;
    }
}

