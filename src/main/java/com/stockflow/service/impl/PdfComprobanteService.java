package com.stockflow.service.impl;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.stockflow.entity.Comprobante;
import com.stockflow.entity.DetalleVenta;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.ComprobanteRepository;
import com.stockflow.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * Genera el PDF de un comprobante (boleta/factura) en formato A4.
 * Usa OpenPDF (LGPL) — sin dependencias de licencia comercial.
 *
 * Layout:
 *  ┌──────────────────────────┬────────────────────┐
 *  │  Logo  Empresa  RUC Dir  │  BOLETA DE VENTA   │
 *  │                          │  B001-00000001     │
 *  │                          │  21/05/2026 10:30  │
 *  ├──────────────────────────┴────────────────────┤
 *  │  DATOS DEL CLIENTE                            │
 *  │  DNI: 12345678  Nombre: Juan Pérez            │
 *  ├───────────────────────────────────────────────┤
 *  │  DESCRIPCIÓN          CANT  P.UNIT    TOTAL   │
 *  │  Producto A              2   50.00   100.00   │
 *  ├───────────────────────────────────────────────┤
 *  │                      OP. GRAVADA    S/. ...   │
 *  │                      IGV (18%)      S/. ...   │
 *  │                      IMPORTE TOTAL  S/. ...   │
 *  └───────────────────────────────────────────────┘
 *  Representación impresa generada con Fluxus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfComprobanteService {

    private final ComprobanteRepository comprobanteRepository;
    private final TenantRepository      tenantRepository;

    // ── Paleta ────────────────────────────────────────────────────────────────
    private static final Color COL_PRIMARY    = new Color(30,  58,  95);   // navy
    private static final Color COL_LIGHT_BG   = new Color(248, 250, 252);  // slate-50
    private static final Color COL_BORDER     = new Color(203, 213, 225);  // slate-300
    private static final Color COL_TEXT       = new Color(15,  23,  42);   // near-black
    private static final Color COL_MUTED      = new Color(100, 116, 139);  // slate-500
    private static final Color COL_TOTAL_BG   = new Color(239, 246, 255);  // blue-50
    private static final Color COL_LIGHT_NAVY = new Color(186, 230, 253);  // blue-200 (for header subtext)

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── API pública ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generate(Long comprobanteId, String tenantId) {

        Comprobante c = comprobanteRepository.findById(comprobanteId)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comprobante no encontrado: " + comprobanteId));

        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Configuración de empresa no encontrada"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            writeHeader(doc, c, tenant);
            spacer(doc);
            writeReceptor(doc, c);
            spacer(doc);
            writeItems(doc, c);
            spacer(doc);
            writeTotals(doc, c, tenant);
            spacer(doc);
            writeFooter(doc, c, tenant);

            doc.close();
            log.debug("PDF generado para comprobante {} ({} bytes)", c.getNumero(), baos.size());
            return baos.toByteArray();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generando PDF para comprobante {}: {}", comprobanteId, e.getMessage(), e);
            throw new RuntimeException("Error generando PDF del comprobante", e);
        }
    }

    // ── Secciones del PDF ─────────────────────────────────────────────────────

    /** Cabecera: info empresa a la izquierda, tipo+número del comprobante a la derecha. */
    private void writeHeader(Document doc, Comprobante c, Tenant tenant) throws DocumentException {
        Font fCompanyName   = new Font(Font.HELVETICA, 12, Font.BOLD,   COL_TEXT);
        Font fCompanyDetail = new Font(Font.HELVETICA,  8, Font.NORMAL, COL_MUTED);
        Font fType          = new Font(Font.HELVETICA, 11, Font.BOLD,   Color.WHITE);
        Font fNumero        = new Font(Font.HELVETICA, 14, Font.BOLD,   Color.WHITE);
        Font fDate          = new Font(Font.HELVETICA,  8, Font.NORMAL, COL_LIGHT_NAVY);

        PdfPTable tbl = new PdfPTable(2);
        tbl.setWidthPercentage(100);
        tbl.setWidths(new float[]{60, 40});

        // ── Celda izquierda: empresa ──────────────────────────────────────────
        PdfPCell left = nobordeCell();
        left.setPadding(12);
        left.setBackgroundColor(COL_LIGHT_BG);

        // Logo (base64 opcional)
        if (tenant.getLogoBase64() != null && !tenant.getLogoBase64().isBlank()) {
            try {
                String raw = tenant.getLogoBase64()
                        .replaceAll("^data:image/[^;]+;base64,", "");
                Image logo = Image.getInstance(Base64.getDecoder().decode(raw));
                logo.scaleToFit(130, 55);
                left.addElement(logo);
                left.addElement(new Paragraph(" "));
            } catch (Exception ex) {
                log.warn("No se pudo cargar el logo del tenant: {}", ex.getMessage());
            }
        }

        left.addElement(new Paragraph(str(tenant.getNombre(), "Empresa"), fCompanyName));
        if (notBlank(tenant.getRuc()))
            left.addElement(new Paragraph("RUC: " + tenant.getRuc(), fCompanyDetail));
        if (notBlank(tenant.getDireccion()))
            left.addElement(new Paragraph(tenant.getDireccion(), fCompanyDetail));
        if (notBlank(tenant.getTelefono()))
            left.addElement(new Paragraph("Tel: " + tenant.getTelefono(), fCompanyDetail));
        if (notBlank(tenant.getEmailContacto()))
            left.addElement(new Paragraph(tenant.getEmailContacto(), fCompanyDetail));
        tbl.addCell(left);

        // ── Celda derecha: tipo y número del comprobante ──────────────────────
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.BOX);
        right.setBorderColor(COL_PRIMARY);
        right.setBackgroundColor(COL_PRIMARY);
        right.setPadding(10);
        right.setHorizontalAlignment(Element.ALIGN_CENTER);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);

        boolean esBoleta = "BOLETA".equals(c.getTipo());
        centeredParagraph(right, esBoleta ? "BOLETA DE VENTA" : "FACTURA ELECTRÓNICA", fType);
        centeredParagraph(right, str(c.getNumero(), "—"), fNumero);
        if (c.getFechaEmision() != null)
            centeredParagraph(right, c.getFechaEmision().format(DATE_FMT), fDate);
        tbl.addCell(right);

        doc.add(tbl);
    }

    /** Bloque de datos del receptor (cliente/empresa). */
    private void writeReceptor(Document doc, Comprobante c) throws DocumentException {
        Font fTitle = new Font(Font.HELVETICA, 7,  Font.BOLD,   Color.WHITE);
        Font fLabel = new Font(Font.HELVETICA, 7,  Font.BOLD,   COL_MUTED);
        Font fValue = new Font(Font.HELVETICA, 9,  Font.NORMAL, COL_TEXT);

        PdfPTable tbl = new PdfPTable(1);
        tbl.setWidthPercentage(100);

        // Título de sección
        String sectionTitle = "FACTURA".equals(c.getTipo())
                ? "DATOS DEL CLIENTE — EMPRESA" : "DATOS DEL CLIENTE";
        PdfPCell titleCell = new PdfPCell(new Phrase(sectionTitle, fTitle));
        titleCell.setBackgroundColor(COL_PRIMARY);
        titleCell.setPadding(5);
        titleCell.setBorder(Rectangle.NO_BORDER);
        tbl.addCell(titleCell);

        // Contenido
        PdfPCell body = new PdfPCell();
        body.setBorder(Rectangle.BOX);
        body.setBorderColor(COL_BORDER);
        body.setBackgroundColor(COL_LIGHT_BG);
        body.setPadding(6);

        boolean hasData = false;
        if (notBlank(c.getReceptorDocNumero())) {
            body.addElement(labeledLine(
                    str(c.getReceptorDocTipo(), "DOC") + ": ", c.getReceptorDocNumero(), fLabel, fValue));
            hasData = true;
        }
        if (notBlank(c.getReceptorNombre())) {
            body.addElement(labeledLine("NOMBRE: ", c.getReceptorNombre(), fLabel, fValue));
            hasData = true;
        }
        if (notBlank(c.getReceptorDireccion())) {
            body.addElement(labeledLine("DIRECCIÓN: ", c.getReceptorDireccion(), fLabel, fValue));
            hasData = true;
        }
        if (!hasData) {
            body.addElement(new Paragraph("— CLIENTES VARIOS —", fValue));
        }

        tbl.addCell(body);
        doc.add(tbl);
    }

    /** Tabla de ítems (productos de la venta). */
    private void writeItems(Document doc, Comprobante c) throws DocumentException {
        Font fHead  = new Font(Font.HELVETICA, 8, Font.BOLD,   Color.WHITE);
        Font fBody  = new Font(Font.HELVETICA, 8, Font.NORMAL, COL_TEXT);
        Font fMono  = new Font(Font.COURIER,   8, Font.NORMAL, COL_TEXT);
        Font fSmall = new Font(Font.HELVETICA, 6, Font.NORMAL, COL_MUTED);

        PdfPTable tbl = new PdfPTable(4);
        tbl.setWidthPercentage(100);
        tbl.setWidths(new float[]{46, 14, 20, 20});

        // Encabezados
        addHeaderCell(tbl, "DESCRIPCIÓN",  fHead, Element.ALIGN_LEFT);
        addHeaderCell(tbl, "CANT.",        fHead, Element.ALIGN_CENTER);
        addHeaderCell(tbl, "P. UNIT.",     fHead, Element.ALIGN_RIGHT);
        addHeaderCell(tbl, "TOTAL",        fHead, Element.ALIGN_RIGHT);

        List<DetalleVenta> detalles = c.getVenta() != null && c.getVenta().getDetalles() != null
                ? c.getVenta().getDetalles()
                : List.of();

        boolean alt = false;
        for (DetalleVenta d : detalles) {
            Color rowBg = alt ? COL_LIGHT_BG : Color.WHITE;
            alt = !alt;

            String nombre = d.getProducto() != null ? d.getProducto().getNombre() : "Producto";
            String cb     = d.getProducto() != null ? d.getProducto().getCodigoBarras() : null;

            // Celda descripción (con código de barras como sub-texto)
            PdfPCell descCell = itemCell(rowBg);
            Paragraph descP = new Paragraph(nombre, fBody);
            if (notBlank(cb)) descP.add(new Chunk("\n" + cb, fSmall));
            descCell.addElement(descP);
            tbl.addCell(descCell);

            addItemCell(tbl, String.valueOf(d.getCantidad()), fMono, Element.ALIGN_CENTER, rowBg);
            addItemCell(tbl, fmt(d.getPrecioUnitario()),      fMono, Element.ALIGN_RIGHT,  rowBg);
            addItemCell(tbl, fmt(d.getSubtotal()),            fMono, Element.ALIGN_RIGHT,  rowBg);
        }

        if (detalles.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Sin detalle de productos", fBody));
            empty.setColspan(4);
            empty.setPadding(8);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBorderColor(COL_BORDER);
            empty.setBorder(Rectangle.BOX);
            tbl.addCell(empty);
        }

        doc.add(tbl);
    }

    /** Bloque de totales (alineado a la derecha, columna vacía a la izquierda). */
    private void writeTotals(Document doc, Comprobante c, Tenant tenant) throws DocumentException {
        Font fLabel  = new Font(Font.HELVETICA,  8, Font.NORMAL, COL_MUTED);
        Font fValue  = new Font(Font.HELVETICA,  8, Font.NORMAL, COL_TEXT);
        Font fTLabel = new Font(Font.HELVETICA, 10, Font.BOLD,   COL_TEXT);
        Font fTValue = new Font(Font.HELVETICA, 10, Font.BOLD,   COL_PRIMARY);

        // Contenedor 50/50 para empujar los totales a la derecha
        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{50, 50});
        outer.addCell(nobordeCell());  // celda izquierda vacía

        // Sub-tabla de totales
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);
        addTotalRow(totals, "OP. GRAVADA", "S/. " + fmt(c.getSubtotal()), fLabel, fValue, Color.WHITE);
        double igvPct = tenant.getIgvPorcentaje() != null ? tenant.getIgvPorcentaje() : 18.0;
        String igvLabel = "IGV (" + (igvPct == Math.floor(igvPct) ? String.valueOf((int)igvPct) : String.valueOf(igvPct)) + "%)";
        addTotalRow(totals, igvLabel, "S/. " + fmt(c.getIgv()), fLabel, fValue, Color.WHITE);

        // Separador
        PdfPCell sep = new PdfPCell(new Phrase(""));
        sep.setColspan(2);
        sep.setFixedHeight(1f);
        sep.setBackgroundColor(COL_BORDER);
        sep.setBorder(Rectangle.NO_BORDER);
        totals.addCell(sep);

        // Fila TOTAL
        PdfPCell tLabel = totalsCell("IMPORTE TOTAL", fTLabel, Element.ALIGN_LEFT);
        tLabel.setBackgroundColor(COL_TOTAL_BG);
        tLabel.setBorder(Rectangle.BOX);
        tLabel.setBorderColor(COL_BORDER);
        totals.addCell(tLabel);

        PdfPCell tValue = totalsCell("S/. " + fmt(c.getTotal()), fTValue, Element.ALIGN_RIGHT);
        tValue.setBackgroundColor(COL_TOTAL_BG);
        tValue.setBorder(Rectangle.BOX);
        tValue.setBorderColor(COL_BORDER);
        totals.addCell(tValue);

        PdfPCell rightCell = nobordeCell();
        rightCell.addElement(totals);
        outer.addCell(rightCell);

        doc.add(outer);
    }

    /** Pie de página. Usa piePaginaPdf del tenant si está configurado. */
    private void writeFooter(Document doc, Comprobante c, Tenant tenant) throws DocumentException {
        Font fFooter = new Font(Font.HELVETICA, 7, Font.ITALIC, COL_MUTED);
        Font fQr     = new Font(Font.COURIER,   6, Font.NORMAL, COL_MUTED);

        LineSeparator sep = new LineSeparator();
        sep.setLineColor(COL_BORDER);
        doc.add(new Chunk(sep));
        spacer(doc);

        // Estado SUNAT
        if (notBlank(c.getSunatEstado())) {
            String sunatText = switch (c.getSunatEstado()) {
                case "ACEPTADO"  -> "✓ ACEPTADO POR SUNAT";
                case "PENDIENTE" -> "⏳ PENDIENTE DE CONFIRMACIÓN SUNAT";
                case "RECHAZADO" -> "✗ RECHAZADO POR SUNAT";
                default           -> "SUNAT: " + c.getSunatEstado();
            };
            Color sunatColor = "ACEPTADO".equals(c.getSunatEstado())
                    ? new Color(21, 128, 61)   // green-700
                    : "RECHAZADO".equals(c.getSunatEstado())
                    ? new Color(185, 28, 28)   // red-700
                    : COL_MUTED;
            Font fSunatColored = new Font(Font.HELVETICA, 8, Font.BOLD, sunatColor);
            centeredParagraph(doc, sunatText, fSunatColored);
            spacer(doc);
        }

        // QR data (si Nubefact lo devolvió)
        if (notBlank(c.getQr())) {
            Paragraph qrLabel = new Paragraph("Código QR:", fFooter);
            qrLabel.setAlignment(Element.ALIGN_CENTER);
            doc.add(qrLabel);
            Paragraph qrData = new Paragraph(c.getQr(), fQr);
            qrData.setAlignment(Element.ALIGN_CENTER);
            doc.add(qrData);
            spacer(doc);
        }

        // Pie personalizado del negocio (configurado en Ajustes → Facturación)
        if (notBlank(tenant.getPiePaginaPdf())) {
            centeredParagraph(doc, tenant.getPiePaginaPdf(), fFooter);
        }

        centeredParagraph(doc,
                "Representación impresa generada con Fluxus · Venta #"
                + (c.getVenta() != null ? c.getVenta().getId() : "—")
                + ("ANULADO".equals(c.getEstado()) ? " · ANULADO" : ""),
                fFooter);
    }

    // ── Helpers de layout ─────────────────────────────────────────────────────

    private PdfPCell nobordeCell() {
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfPCell itemCell(Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setBorderColor(COL_BORDER);
        cell.setBorder(Rectangle.BOTTOM);
        return cell;
    }

    private void addHeaderCell(PdfPTable tbl, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(COL_PRIMARY);
        cell.setPadding(5);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(align);
        tbl.addCell(cell);
    }

    private void addItemCell(PdfPTable tbl, String text, Font font, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setBorderColor(COL_BORDER);
        cell.setBorder(Rectangle.BOTTOM);
        tbl.addCell(cell);
    }

    private void addTotalRow(PdfPTable tbl, String label, String value,
                              Font fLabel, Font fValue, Color bg) {
        PdfPCell lc = new PdfPCell(new Phrase(label, fLabel));
        lc.setBackgroundColor(bg);
        lc.setPadding(4);
        lc.setPaddingLeft(8);
        lc.setBorder(Rectangle.NO_BORDER);
        tbl.addCell(lc);

        PdfPCell vc = new PdfPCell(new Phrase(value, fValue));
        vc.setBackgroundColor(bg);
        vc.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vc.setPadding(4);
        vc.setPaddingRight(8);
        vc.setBorder(Rectangle.NO_BORDER);
        tbl.addCell(vc);
    }

    private PdfPCell totalsCell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setPaddingTop(6);
        cell.setPaddingBottom(6);
        cell.setPaddingLeft(8);
        cell.setPaddingRight(8);
        return cell;
    }

    private void centeredParagraph(PdfPCell cell, String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p);
    }

    private void centeredParagraph(Document doc, String text, Font font) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    private Paragraph labeledLine(String label, String value, Font fLabel, Font fValue) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label, fLabel));
        p.add(new Chunk(value, fValue));
        return p;
    }

    private void spacer(Document doc) throws DocumentException {
        doc.add(new Paragraph(" "));
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private String fmt(BigDecimal val) {
        return val != null ? String.format("%.2f", val) : "0.00";
    }

    private String str(String val, String defaultVal) {
        return (val != null && !val.isBlank()) ? val : defaultVal;
    }

    private boolean notBlank(String val) {
        return val != null && !val.isBlank();
    }
}
