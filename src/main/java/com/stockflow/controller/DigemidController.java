package com.stockflow.controller;

import com.stockflow.entity.CatalogoDigemid;
import com.stockflow.entity.Producto;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.CatalogoDigemidRepository;
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

                    return Map.<String, Object>of(
                            "id", p.getId(),
                            "nombre", p.getNombre(),
                            "precioVenta", p.getPrecioVenta(),
                            "stockActual", p.getStockActual(),
                            "registroSanitario", p.getRegistroSanitario() != null ? p.getRegistroSanitario() : "",
                            "codDigemid", p.getCodDigemid() != null ? p.getCodDigemid() : "",
                            "nomDigemid", cat.map(CatalogoDigemid::getNomProd).orElse(""),
                            "fraccion", cat.map(c -> c.getFraccion() != null ? c.getFraccion() : BigDecimal.ONE).orElse(BigDecimal.ONE),
                            "vinculado", p.getCodDigemid() != null
                    );
                })
                .toList();

        return ResponseEntity.ok(resultado);
    }

    // ── Exportar CSV → ZIP para OPPF ────────────────────────────────────────

    @GetMapping("/oppf/exportar")
    public ResponseEntity<byte[]> exportarOppf(@RequestParam String codEstablecimiento) {
        if (codEstablecimiento == null || codEstablecimiento.isBlank()) {
            throw new BadRequestException("Se requiere el código de establecimiento.");
        }

        String tenantId = TenantContext.getCurrentTenant();
        List<Producto> vinculados = productoRepository.findVinculadosDigemidByTenantId(tenantId);

        if (vinculados.isEmpty()) {
            throw new BadRequestException("No hay productos vinculados a códigos DIGEMID. Vincula al menos uno antes de exportar.");
        }

        try {
            String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // ── Preparar datos ────────────────────────────────────────────────
            record FilaOppf(String codEstab, String codProd, BigDecimal precio1, BigDecimal precio2) {}
            List<FilaOppf> filas = new java.util.ArrayList<>();
            for (Producto p : vinculados) {
                CatalogoDigemid cat = catalogoDigemidRepository.findByCodProd(p.getCodDigemid()).orElse(null);
                BigDecimal fraccion = (cat != null && cat.getFraccion() != null && cat.getFraccion().compareTo(BigDecimal.ZERO) > 0)
                        ? cat.getFraccion()
                        : BigDecimal.ONE;
                BigDecimal precio1 = p.getPrecioVenta().setScale(2, RoundingMode.HALF_UP);
                BigDecimal precio2 = precio1.divide(fraccion, 2, RoundingMode.HALF_UP);
                // Eliminar el ".0" que puede traer el cod_digemid si viene de un campo numérico
                String codProd = p.getCodDigemid().replaceAll("\\.0$", "");
                filas.add(new FilaOppf(codEstablecimiento, codProd, precio1, precio2));
            }

            // ── Generar CSV (para subir al OPPF) ─────────────────────────────
            ByteArrayOutputStream csvBytes = new ByteArrayOutputStream();
            csvBytes.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // BOM UTF-8
            var writer = new java.io.PrintWriter(csvBytes, true, StandardCharsets.UTF_8);
            writer.println("CodEstab,CodProd,Precio 1,Precio 2");
            for (var f : filas) {
                writer.printf(java.util.Locale.US, "\"%s\",%s,%.2f,%.2f%n",
                        f.codEstab(), f.codProd(), f.precio1(), f.precio2());
            }
            writer.flush();

            // ── Generar XLSX (para verificar en Excel) ────────────────────────
            ByteArrayOutputStream xlsxBytes = new ByteArrayOutputStream();
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("OPPF");

                // Estilos
                CellStyle estiloTexto = wb.createCellStyle();
                DataFormat fmt = wb.createDataFormat();
                estiloTexto.setDataFormat(fmt.getFormat("@")); // formato texto

                CellStyle estiloDecimal = wb.createCellStyle();
                estiloDecimal.setDataFormat(fmt.getFormat("0.00")); // 2 decimales siempre

                // Encabezado
                Row header = sheet.createRow(0);
                for (int i = 0; i < 4; i++) {
                    Cell c = header.createCell(i);
                    c.setCellStyle(estiloTexto);
                }
                header.getCell(0).setCellValue("CodEstab");
                header.getCell(1).setCellValue("CodProd");
                header.getCell(2).setCellValue("Precio 1");
                header.getCell(3).setCellValue("Precio 2");

                // Datos
                int fila = 1;
                for (var f : filas) {
                    Row row = sheet.createRow(fila++);

                    Cell cEstab = row.createCell(0);
                    cEstab.setCellValue(f.codEstab());
                    cEstab.setCellStyle(estiloTexto);

                    Cell cProd = row.createCell(1);
                    cProd.setCellValue(f.codProd());
                    cProd.setCellStyle(estiloTexto);

                    Cell cP1 = row.createCell(2);
                    cP1.setCellValue(f.precio1().doubleValue());
                    cP1.setCellStyle(estiloDecimal);

                    Cell cP2 = row.createCell(3);
                    cP2.setCellValue(f.precio2().doubleValue());
                    cP2.setCellStyle(estiloDecimal);
                }

                sheet.autoSizeColumn(0);
                sheet.autoSizeColumn(1);
                sheet.autoSizeColumn(2);
                sheet.autoSizeColumn(3);

                wb.write(xlsxBytes);
            }

            // ── Empaquetar solo el CSV en ZIP (formato requerido por OPPF) ───
            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
                zip.putNextEntry(new ZipEntry("precios_oppf_" + fecha + ".csv"));
                zip.write(csvBytes.toByteArray());
                zip.closeEntry();
            }

            log.info("📤 [DIGEMID] OPPF exportado: {} productos, tenant={}", vinculados.size(), tenantId);

            String zipNombre = "oppf_" + codEstablecimiento + "_" + fecha + ".zip";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipNombre + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipBytes.toByteArray());

        } catch (Exception e) {
            log.error("❌ [DIGEMID] Error generando ZIP OPPF: {}", e.getMessage(), e);
            throw new BadRequestException("Error generando el archivo: " + e.getMessage());
        }
    }
}
