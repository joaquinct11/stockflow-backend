package com.stockflow.controller;

import com.stockflow.entity.CatalogoDigemid;
import com.stockflow.entity.Producto;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.CatalogoDigemidRepository;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/digemid")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DigemidController {

    private final CatalogoDigemidRepository catalogoDigemidRepository;
    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;

    // ── Búsqueda en catálogo DIGEMID ────────────────────────────────────────

    @GetMapping("/catalogo/buscar")
    public ResponseEntity<List<CatalogoDigemid>> buscar(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            throw new BadRequestException("El término de búsqueda debe tener al menos 2 caracteres.");
        }
        List<CatalogoDigemid> resultados = catalogoDigemidRepository.buscar(q.trim());
        return ResponseEntity.ok(resultados);
    }

    // ── Vincular producto local → cod_digemid ───────────────────────────────

    @PatchMapping("/productos/{productoId}/vincular")
    public ResponseEntity<Map<String, Object>> vincular(
            @PathVariable Long productoId,
            @RequestBody Map<String, String> body) {

        String tenantId = TenantContext.getCurrentTenant();
        String codDigemid = body.get("codDigemid");

        if (codDigemid == null || codDigemid.isBlank()) {
            throw new BadRequestException("Se requiere 'codDigemid'.");
        }

        Producto producto = productoRepository.findById(productoId)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado."));

        CatalogoDigemid catalogo = catalogoDigemidRepository.findByCodProd(codDigemid)
                .orElseThrow(() -> new ResourceNotFoundException("Código DIGEMID no encontrado: " + codDigemid));

        producto.setCodDigemid(codDigemid);
        producto.setRegistroSanitario(catalogo.getNumRegSan());
        productoRepository.save(producto);

        log.info("🔗 [DIGEMID] Producto id={} vinculado a cod_digemid={} (tenant={})",
                productoId, codDigemid, tenantId);

        return ResponseEntity.ok(Map.of(
                "productoId", productoId,
                "codDigemid", codDigemid,
                "nomProd", catalogo.getNomProd(),
                "numRegSan", catalogo.getNumRegSan() != null ? catalogo.getNumRegSan() : ""
        ));
    }

    // ── Desvincular ──────────────────────────────────────────────────────────

    @DeleteMapping("/productos/{productoId}/vincular")
    public ResponseEntity<Void> desvincular(@PathVariable Long productoId) {
        String tenantId = TenantContext.getCurrentTenant();
        Producto producto = productoRepository.findById(productoId)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado."));
        producto.setCodDigemid(null);
        producto.setRegistroSanitario(null);
        productoRepository.save(producto);
        return ResponseEntity.noContent().build();
    }

    // ── Listar productos del tenant con su estado DIGEMID ───────────────────

    @GetMapping("/productos")
    public ResponseEntity<List<Map<String, Object>>> listarProductos() {
        String tenantId = TenantContext.getCurrentTenant();
        List<Producto> productos = productoRepository.findProductosTipoByTenantId(tenantId);

        List<Map<String, Object>> resultado = productos.stream()
                .map(p -> {
                    Optional<CatalogoDigemid> cat = p.getCodDigemid() != null
                            ? catalogoDigemidRepository.findByCodProd(p.getCodDigemid())
                            : Optional.empty();

                    String regSan = p.getRegistroSanitario();
                    if (regSan == null || regSan.isBlank()) {
                        regSan = movimientoInventarioRepository
                                .findLatestRegistroSanitarioByProductoId(p.getId(), tenantId)
                                .orElse("");
                    }
                    final String registroSanitario = regSan;

                    BigDecimal fraccion = cat.map(c -> c.getFraccion() != null && c.getFraccion().compareTo(BigDecimal.ZERO) > 0
                            ? c.getFraccion() : BigDecimal.ONE).orElse(BigDecimal.ONE);

                    String unidadNombre = p.getUnidadMedida() != null ? p.getUnidadMedida().getNombre() : "";
                    BigDecimal[] precios = calcularPreciosOppf(p.getPrecioVenta(), fraccion, unidadNombre);

                    return Map.<String, Object>ofEntries(
                            Map.entry("id", p.getId()),
                            Map.entry("nombre", p.getNombre()),
                            Map.entry("precioVenta", p.getPrecioVenta()),
                            Map.entry("stockActual", p.getStockActual()),
                            Map.entry("registroSanitario", registroSanitario),
                            Map.entry("codDigemid", p.getCodDigemid() != null ? p.getCodDigemid() : ""),
                            Map.entry("nomDigemid", cat.map(CatalogoDigemid::getNomProd).orElse("")),
                            Map.entry("fraccion", fraccion),
                            Map.entry("vinculado", p.getCodDigemid() != null),
                            Map.entry("unidadMedida", unidadNombre),
                            Map.entry("precio1Oppf", precios[0]),
                            Map.entry("precio2Oppf", precios[1])
                    );
                })
                .toList();

        return ResponseEntity.ok(resultado);
    }

    /**
     * Calcula Precio 1 (Empaque) y Precio 2 (Unitario) según la OPPF/SNIPPF.
     * Si precioVenta es por unidad individual → Precio2 = precioVenta, Precio1 = precioVenta × fraccion.
     * Si precioVenta es por empaque (Blíster/Caja/etc.) → Precio1 = precioVenta, Precio2 = precioVenta ÷ fraccion.
     */
    private BigDecimal[] calcularPreciosOppf(BigDecimal precioVenta, BigDecimal fraccion, String unidadNombre) {
        String u = unidadNombre != null ? unidadNombre.trim().toUpperCase() : "";
        boolean esPorUnidad = u.equals("UNIDAD") || u.equals("TABLETA") || u.equals("TABLETAS")
                || u.equals("CÁPSULA") || u.equals("CAPSULA") || u.equals("CÁPSULAS")
                || u.equals("AMPOLLA") || u.equals("AMPOLLAS") || u.equals("VIAL") || u.equals("VIALES")
                || u.equals("COMPRIMIDO") || u.equals("COMPRIMIDOS");

        BigDecimal precio1, precio2;
        if (esPorUnidad) {
            // precioVenta = precio unitario (Precio 2)
            precio2 = precioVenta.setScale(2, RoundingMode.HALF_UP);
            precio1 = precioVenta.multiply(fraccion).setScale(2, RoundingMode.HALF_UP);
        } else {
            // precioVenta = precio empaque (Precio 1)
            precio1 = precioVenta.setScale(2, RoundingMode.HALF_UP);
            precio2 = precio1.divide(fraccion, 2, RoundingMode.HALF_UP);
            BigDecimal minimo = new BigDecimal("0.01");
            if (precio2.compareTo(minimo) < 0) {
                precio2 = minimo;
            }
        }
        return new BigDecimal[]{precio1, precio2};
    }

    // ── Exportar CSV → ZIP para OPPF ────────────────────────────────────────

    @GetMapping("/oppf/exportar")
    public ResponseEntity<byte[]> exportarOppf(
            @RequestParam String codEstablecimiento,
            @RequestParam String ruc,
            @RequestParam(required = false) String mes,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false, defaultValue = "CARGA ARCHIVO") String tipo) {

        if (codEstablecimiento == null || codEstablecimiento.isBlank())
            throw new BadRequestException("Se requiere el código de establecimiento.");
        if (ruc == null || ruc.isBlank())
            throw new BadRequestException("Se requiere el RUC del establecimiento.");

        LocalDate hoy = LocalDate.now();
        String mesStr = (mes != null && !mes.isBlank()) ? mes : String.format("%02d", hoy.getMonthValue());
        String anoStr = (ano != null && !ano.isBlank()) ? ano : String.format("%02d", hoy.getYear() % 100);

        String tenantId = TenantContext.getCurrentTenant();
        List<Producto> todosVinculados = productoRepository.findVinculadosDigemidByTenantId(tenantId);
        List<Producto> vinculados = todosVinculados.stream()
                .filter(p -> p.getStockActual() != null && p.getStockActual() > 0)
                .toList();

        if (todosVinculados.isEmpty())
            throw new BadRequestException("No hay productos vinculados a códigos DIGEMID. Vincula al menos uno antes de exportar.");
        if (vinculados.isEmpty())
            throw new BadRequestException("Todos los productos vinculados tienen stock 0. Ingresa stock antes de exportar.");

        try {
            // ── Preparar filas ────────────────────────────────────────────────
            record FilaOppf(String codEstab, String codProd, BigDecimal precio1, BigDecimal precio2) {}
            List<FilaOppf> filas = new java.util.ArrayList<>();
            for (Producto p : vinculados) {
                CatalogoDigemid cat = catalogoDigemidRepository.findByCodProd(p.getCodDigemid()).orElse(null);
                BigDecimal fraccion = (cat != null && cat.getFraccion() != null && cat.getFraccion().compareTo(BigDecimal.ZERO) > 0)
                        ? cat.getFraccion() : BigDecimal.ONE;
                String unidadNombre = p.getUnidadMedida() != null ? p.getUnidadMedida().getNombre() : "";
                BigDecimal[] precios = calcularPreciosOppf(p.getPrecioVenta(), fraccion, unidadNombre);
                String codProd = p.getCodDigemid().replaceAll("\\.0+$", "");
                filas.add(new FilaOppf(codEstablecimiento, codProd, precios[0], precios[1]));
            }

            // ── Generar CSV — UTF-8 SIN BOM, sin comillas, coma como delimitador ──
            ByteArrayOutputStream csvBytes = new ByteArrayOutputStream();
            // NO escribir BOM — el OPPF rechaza archivos con BOM
            var writer = new java.io.PrintWriter(csvBytes, true, StandardCharsets.UTF_8);
            writer.print("CodEstab,CodProd,Precio 1,Precio 2\r\n");
            for (var f : filas) {
                // Sin comillas alrededor de los campos — preserva ceros iniciales como texto plano
                writer.printf(java.util.Locale.US, "%s,%s,%.2f,%.2f\r\n",
                        f.codEstab(), f.codProd(), f.precio1(), f.precio2());
            }
            writer.flush();

            // ── Validación interna ────────────────────────────────────────────
            byte[] csvData = csvBytes.toByteArray();
            // Confirmar ausencia de BOM
            if (csvData.length >= 3 && (csvData[0] & 0xFF) == 0xEF && (csvData[1] & 0xFF) == 0xBB && (csvData[2] & 0xFF) == 0xBF) {
                throw new BadRequestException("Error interno: el CSV contiene BOM no permitido.");
            }
            log.info("✅ [DIGEMID] CSV validado — sin BOM, {} registros, {} bytes", filas.size(), csvData.length);

            // ── Nombre del archivo ZIP — formato OPPF/SNIPPF ──────────────────
            // Ejemplo: 20131373237_09_21_CARGA ARCHIVO.ZIP
            String nombreCsv  = ruc + "_" + mesStr + "_" + anoStr + "_" + tipo + ".csv";
            String nombreZip  = ruc + "_" + mesStr + "_" + anoStr + "_" + tipo + ".zip";

            // ── Empaquetar CSV en ZIP — solo el CSV en la raíz ───────────────
            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(zipBytes, StandardCharsets.UTF_8)) {
                ZipEntry entry = new ZipEntry(nombreCsv);
                zip.putNextEntry(entry);
                zip.write(csvData);
                zip.closeEntry();
            }

            log.info("📤 [DIGEMID] OPPF ZIP generado: {} → {} productos, tenant={}", nombreZip, filas.size(), tenantId);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreZip + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipBytes.toByteArray());

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ [DIGEMID] Error generando ZIP OPPF: {}", e.getMessage(), e);
            throw new BadRequestException("Error generando el archivo: " + e.getMessage());
        }
    }
}
