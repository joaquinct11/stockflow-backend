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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
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
        List<CatalogoDigemid> resultados = catalogoDigemidRepository.buscar(q.trim(), PageRequest.of(0, 30));
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
        if (producto.getRegistroSanitario() == null) {
            producto.setRegistroSanitario(catalogo.getNumRegSan());
        }
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
        productoRepository.save(producto);
        return ResponseEntity.noContent().build();
    }

    // ── Listar productos del tenant con su estado DIGEMID ───────────────────

    @GetMapping("/productos")
    public ResponseEntity<List<Map<String, Object>>> listarProductos() {
        String tenantId = TenantContext.getCurrentTenant();
        List<Producto> productos = productoRepository.findByTenantId(tenantId);

        List<Map<String, Object>> resultado = productos.stream()
                .filter(p -> "PRODUCTO".equals(p.getTipo()) && Boolean.TRUE.equals(p.getActivo()))
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
        List<Producto> productos = productoRepository.findByTenantId(tenantId);

        List<Producto> vinculados = productos.stream()
                .filter(p -> p.getCodDigemid() != null && Boolean.TRUE.equals(p.getActivo()))
                .toList();

        if (vinculados.isEmpty()) {
            throw new BadRequestException("No hay productos vinculados a códigos DIGEMID. Vincula al menos uno antes de exportar.");
        }

        try {
            ByteArrayOutputStream csvBytes = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(csvBytes, true, StandardCharsets.UTF_8);

            // Sin encabezado — el OPPF espera datos directamente
            for (Producto p : vinculados) {
                CatalogoDigemid cat = catalogoDigemidRepository.findByCodProd(p.getCodDigemid()).orElse(null);
                BigDecimal fraccion = (cat != null && cat.getFraccion() != null && cat.getFraccion().compareTo(BigDecimal.ZERO) > 0)
                        ? cat.getFraccion()
                        : BigDecimal.ONE;

                BigDecimal precio1 = p.getPrecioVenta().setScale(2, RoundingMode.HALF_UP);
                BigDecimal precio2 = precio1.divide(fraccion, 2, RoundingMode.HALF_UP);

                writer.printf("%s,%s,%s,%s%n",
                        codEstablecimiento,
                        p.getCodDigemid(),
                        precio1.toPlainString(),
                        precio2.toPlainString());
            }
            writer.flush();

            // Empaquetar en ZIP
            ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
                String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String nombreCsv = "precios_oppf_" + fecha + ".csv";
                zip.putNextEntry(new ZipEntry(nombreCsv));
                zip.write(csvBytes.toByteArray());
                zip.closeEntry();
            }

            log.info("📤 [DIGEMID] OPPF exportado: {} productos, tenant={}", vinculados.size(), tenantId);

            String zipNombre = "oppf_" + codEstablecimiento + "_" +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".zip";

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
