package com.stockflow.util;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.stockflow.entity.OrdenCompra;
import com.stockflow.entity.OrdenCompraDetalle;
import com.stockflow.entity.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

/**
 * Genera el PDF de una Orden de Compra para adjuntar en emails o descarga directa.
 */
@Slf4j
@Component
public class OcPdfGenerator {

    // ── Colores corporativos ─────────────────────────────────────────────────
    private static final Color COLOR_PRIMARY   = new Color(79, 70, 229);   // indigo-600
    private static final Color COLOR_HEADER_BG = new Color(238, 242, 255); // indigo-50
    private static final Color COLOR_ROW_ALT   = new Color(249, 250, 251); // gray-50
    private static final Color COLOR_BORDER    = new Color(209, 213, 219); // gray-300
    private static final Color COLOR_TEXT_DARK = new Color(17,  24,  39);  // gray-900
    private static final Color COLOR_TEXT_MID  = new Color(75,  85,  99);  // gray-600
    private static final Color COLOR_TEXT_SOFT = new Color(156, 163, 175); // gray-400

    // ── Fuentes ──────────────────────────────────────────────────────────────
    private static final Font F_TITLE    = new Font(Font.HELVETICA, 18, Font.BOLD,   COLOR_PRIMARY);
    private static final Font F_SUBTITLE = new Font(Font.HELVETICA,  9, Font.NORMAL, COLOR_TEXT_MID);
    private static final Font F_LABEL    = new Font(Font.HELVETICA,  8, Font.BOLD,   COLOR_TEXT_MID);
    private static final Font F_VALUE    = new Font(Font.HELVETICA,  9, Font.NORMAL, COLOR_TEXT_DARK);
    private static final Font F_VALUE_B  = new Font(Font.HELVETICA,  9, Font.BOLD,   COLOR_TEXT_DARK);
    private static final Font F_TH       = new Font(Font.HELVETICA,  8, Font.BOLD,   Color.WHITE);
    private static final Font F_TD       = new Font(Font.HELVETICA,  8, Font.NORMAL, COLOR_TEXT_DARK);
    private static final Font F_TOTAL_L  = new Font(Font.HELVETICA,  9, Font.BOLD,   COLOR_TEXT_DARK);
    private static final Font F_TOTAL_V  = new Font(Font.HELVETICA, 10, Font.BOLD,   COLOR_PRIMARY);
    private static final Font F_FOOTER   = new Font(Font.HELVETICA,  7, Font.NORMAL, COLOR_TEXT_SOFT);

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ────────────────────────────────────────────────────────────────────────

    public byte[] generar(OrdenCompra oc, Tenant tenant) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            // Número de página en el pie
            writer.setPageEvent(new PiePageNumero(tenant));

            doc.open();

            // ── 1. Encabezado del negocio ───────────────────────────────────
            agregarEncabezado(doc, oc, tenant);

            // ── 2. Caja info proveedor + OC ────────────────────────────────
            agregarInfoBox(doc, oc, tenant);

            // ── 3. Tabla de ítems ───────────────────────────────────────────
            agregarTablaItems(doc, oc, tenant);

            // ── 4. Observaciones ───────────────────────────────────────────
            agregarObservaciones(doc, oc);

            // ── 5. Firmas ──────────────────────────────────────────────────
            agregarFirmas(doc);

            doc.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("❌ Error generando PDF de OC #{}: {}", oc.getId(), e.getMessage(), e);
            throw new RuntimeException("Error generando PDF de OC", e);
        }
    }

    // ── ENCABEZADO ───────────────────────────────────────────────────────────

    private void agregarEncabezado(Document doc, OrdenCompra oc, Tenant tenant) throws Exception {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{55f, 45f});
        header.setSpacingAfter(14);

        // Columna izquierda: logo + datos del negocio
        PdfPCell celdaLogo = new PdfPCell();
        celdaLogo.setBorder(Rectangle.NO_BORDER);
        celdaLogo.setPadding(0);

        if (tenant.getLogoBase64() != null && !tenant.getLogoBase64().isBlank()) {
            try {
                String b64 = tenant.getLogoBase64();
                if (b64.contains(",")) b64 = b64.substring(b64.indexOf(',') + 1);
                byte[] imgBytes = Base64.getDecoder().decode(b64);
                Image logo = Image.getInstance(imgBytes);
                logo.scaleToFit(120, 48);
                logo.setAlignment(Image.LEFT);
                celdaLogo.addElement(logo);
                celdaLogo.addElement(new Phrase(" ", F_SUBTITLE)); // espaciado
            } catch (Exception ex) {
                log.warn("⚠️ No se pudo cargar el logo en el PDF: {}", ex.getMessage());
            }
        }

        Paragraph nombreNegocio = new Paragraph(
                tenant.getNombre() != null ? tenant.getNombre() : "Mi Negocio", F_TITLE);
        nombreNegocio.setSpacingBefore(2);
        celdaLogo.addElement(nombreNegocio);

        if (tenant.getRuc() != null && !tenant.getRuc().isBlank()) {
            celdaLogo.addElement(new Paragraph("RUC: " + tenant.getRuc(), F_SUBTITLE));
        }
        if (tenant.getDireccion() != null && !tenant.getDireccion().isBlank()) {
            celdaLogo.addElement(new Paragraph(tenant.getDireccion(), F_SUBTITLE));
        }
        if (tenant.getCiudad() != null && !tenant.getCiudad().isBlank()) {
            celdaLogo.addElement(new Paragraph(tenant.getCiudad(), F_SUBTITLE));
        }
        if (tenant.getTelefono() != null && !tenant.getTelefono().isBlank()) {
            celdaLogo.addElement(new Paragraph("Tel: " + tenant.getTelefono(), F_SUBTITLE));
        }
        header.addCell(celdaLogo);

        // Columna derecha: caja "ORDEN DE COMPRA"
        PdfPCell celdaOC = new PdfPCell();
        celdaOC.setBorder(Rectangle.BOX);
        celdaOC.setBorderColor(COLOR_PRIMARY);
        celdaOC.setBorderWidth(1.5f);
        celdaOC.setBackgroundColor(COLOR_HEADER_BG);
        celdaOC.setPadding(12);
        celdaOC.setHorizontalAlignment(Element.ALIGN_CENTER);
        celdaOC.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Font fDocTitulo = new Font(Font.HELVETICA, 14, Font.BOLD, COLOR_PRIMARY);
        Font fDocNum    = new Font(Font.HELVETICA, 22, Font.BOLD, COLOR_TEXT_DARK);
        Font fDocFecha  = new Font(Font.HELVETICA,  9, Font.NORMAL, COLOR_TEXT_MID);

        Paragraph pDocTipo = new Paragraph("ORDEN DE COMPRA", fDocTitulo);
        pDocTipo.setAlignment(Element.ALIGN_CENTER);
        celdaOC.addElement(pDocTipo);

        Paragraph pDocNum = new Paragraph("# " + String.format("%05d", oc.getId()), fDocNum);
        pDocNum.setAlignment(Element.ALIGN_CENTER);
        pDocNum.setSpacingBefore(4);
        celdaOC.addElement(pDocNum);

        String fecha = oc.getCreatedAt() != null ? oc.getCreatedAt().format(FMT) : "—";
        Paragraph pFecha = new Paragraph("Fecha: " + fecha, fDocFecha);
        pFecha.setAlignment(Element.ALIGN_CENTER);
        pFecha.setSpacingBefore(6);
        celdaOC.addElement(pFecha);

        // Badge de estado
        Font fEstado = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        Paragraph pEstado = new Paragraph(oc.getEstado(), fEstado);
        pEstado.setAlignment(Element.ALIGN_CENTER);
        PdfPTable badgeTable = new PdfPTable(1);
        badgeTable.setWidthPercentage(60);
        badgeTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell badgeCell = new PdfPCell(pEstado);
        badgeCell.setBackgroundColor(COLOR_PRIMARY);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setPadding(4);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeTable.addCell(badgeCell);
        celdaOC.addElement(new Phrase("\n"));
        celdaOC.addElement(badgeTable);

        header.addCell(celdaOC);
        doc.add(header);

        // Línea separadora
        PdfPTable linea = new PdfPTable(1);
        linea.setWidthPercentage(100);
        linea.setSpacingAfter(14);
        PdfPCell lineaCell = new PdfPCell();
        lineaCell.setBorderWidthBottom(2f);
        lineaCell.setBorderColorBottom(COLOR_PRIMARY);
        lineaCell.setBorderWidthTop(0);
        lineaCell.setBorderWidthLeft(0);
        lineaCell.setBorderWidthRight(0);
        lineaCell.setFixedHeight(1f);
        linea.addCell(lineaCell);
        doc.add(linea);
    }

    // ── INFO BOX ─────────────────────────────────────────────────────────────

    private void agregarInfoBox(Document doc, OrdenCompra oc, Tenant tenant) throws Exception {
        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setWidths(new float[]{50f, 50f});
        info.setSpacingAfter(14);

        // Proveedor
        PdfPCell cProveedor = new PdfPCell();
        cProveedor.setBorder(Rectangle.BOX);
        cProveedor.setBorderColor(COLOR_BORDER);
        cProveedor.setBackgroundColor(COLOR_ROW_ALT);
        cProveedor.setPadding(10);

        Paragraph tProveedor = new Paragraph("PROVEEDOR", F_LABEL);
        cProveedor.addElement(tProveedor);
        cProveedor.addElement(new Paragraph(oc.getProveedor().getNombre(), F_VALUE_B));
        if (oc.getProveedor().getRuc() != null && !oc.getProveedor().getRuc().isBlank()) {
            cProveedor.addElement(new Paragraph("RUC: " + oc.getProveedor().getRuc(), F_VALUE));
        }
        if (oc.getProveedor().getDireccion() != null && !oc.getProveedor().getDireccion().isBlank()) {
            cProveedor.addElement(new Paragraph(oc.getProveedor().getDireccion(), F_VALUE));
        }
        if (oc.getProveedor().getTelefono() != null && !oc.getProveedor().getTelefono().isBlank()) {
            cProveedor.addElement(new Paragraph("Tel: " + oc.getProveedor().getTelefono(), F_VALUE));
        }
        if (oc.getProveedor().getEmail() != null && !oc.getProveedor().getEmail().isBlank()) {
            cProveedor.addElement(new Paragraph(oc.getProveedor().getEmail(), F_VALUE));
        }
        info.addCell(cProveedor);

        // Datos de la OC
        PdfPCell cDatos = new PdfPCell();
        cDatos.setBorder(Rectangle.BOX);
        cDatos.setBorderColor(COLOR_BORDER);
        cDatos.setPadding(10);

        cDatos.addElement(new Paragraph("DATOS DE LA ORDEN", F_LABEL));
        agregarFilaDatos(cDatos, "Número:", String.format("OC-%05d", oc.getId()));
        agregarFilaDatos(cDatos, "Fecha emisión:", oc.getCreatedAt() != null ? oc.getCreatedAt().format(FMT) : "—");
        agregarFilaDatos(cDatos, "Elaborado por:", oc.getUsuarioCreador() != null ? oc.getUsuarioCreador().getNombre() : "—");
        agregarFilaDatos(cDatos, "Moneda:", tenant.getMoneda() != null ? tenant.getMoneda() : "S/.");
        info.addCell(cDatos);

        doc.add(info);
    }

    private void agregarFilaDatos(PdfPCell celda, String label, String valor) {
        PdfPTable fila = new PdfPTable(2);
        fila.setWidthPercentage(100);
        try { fila.setWidths(new float[]{40f, 60f}); } catch (Exception ignored) {}

        PdfPCell cLabel = new PdfPCell(new Phrase(label, F_LABEL));
        cLabel.setBorder(Rectangle.NO_BORDER);
        cLabel.setPaddingTop(3);
        fila.addCell(cLabel);

        PdfPCell cValor = new PdfPCell(new Phrase(valor, F_VALUE));
        cValor.setBorder(Rectangle.NO_BORDER);
        cValor.setPaddingTop(3);
        fila.addCell(cValor);

        celda.addElement(fila);
    }

    // ── TABLA DE ÍTEMS ───────────────────────────────────────────────────────

    private void agregarTablaItems(Document doc, OrdenCompra oc, Tenant tenant) throws Exception {
        String moneda = tenant.getMoneda() != null ? tenant.getMoneda() : "S/.";

        // Cabecera de sección
        Paragraph titulo = new Paragraph("DETALLE DE PRODUCTOS", F_LABEL);
        titulo.setSpacingAfter(6);
        doc.add(titulo);

        PdfPTable tabla = new PdfPTable(5);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{38f, 20f, 10f, 16f, 16f});
        tabla.setSpacingAfter(10);

        // Cabeceras
        agregarTH(tabla, "PRODUCTO");
        agregarTH(tabla, "CÓDIGO");
        agregarTH(tabla, "CANT.");
        agregarTH(tabla, "P. UNIT. (" + moneda + ")");
        agregarTH(tabla, "SUBTOTAL (" + moneda + ")");

        List<OrdenCompraDetalle> detalles = oc.getDetalles() != null ? oc.getDetalles() : List.of();
        BigDecimal total = BigDecimal.ZERO;
        boolean altRow = false;

        for (OrdenCompraDetalle d : detalles) {
            Color bg = altRow ? COLOR_ROW_ALT : Color.WHITE;
            altRow = !altRow;

            String nombre = d.getProducto() != null ? d.getProducto().getNombre() : "—";
            String codigo = d.getProducto() != null && d.getProducto().getCodigoBarras() != null
                    ? d.getProducto().getCodigoBarras() : "—";
            int cant = d.getCantidadSolicitada();
            BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal sub = precio.multiply(BigDecimal.valueOf(cant));
            total = total.add(sub);

            agregarTD(tabla, nombre,                  Element.ALIGN_LEFT,   bg);
            agregarTD(tabla, codigo,                  Element.ALIGN_CENTER, bg);
            agregarTD(tabla, String.valueOf(cant),     Element.ALIGN_CENTER, bg);
            agregarTD(tabla, String.format("%.2f", precio), Element.ALIGN_RIGHT, bg);
            agregarTD(tabla, String.format("%.2f", sub),    Element.ALIGN_RIGHT, bg);
        }

        // Fila de total
        PdfPCell cTotalLabel = new PdfPCell(new Phrase("TOTAL ESTIMADO", F_TOTAL_L));
        cTotalLabel.setColspan(4);
        cTotalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cTotalLabel.setPadding(8);
        cTotalLabel.setBackgroundColor(COLOR_HEADER_BG);
        cTotalLabel.setBorderColor(COLOR_PRIMARY);
        tabla.addCell(cTotalLabel);

        PdfPCell cTotalVal = new PdfPCell(
                new Phrase(moneda + " " + String.format("%.2f", total), F_TOTAL_V));
        cTotalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cTotalVal.setPadding(8);
        cTotalVal.setBackgroundColor(COLOR_HEADER_BG);
        cTotalVal.setBorderColor(COLOR_PRIMARY);
        tabla.addCell(cTotalVal);

        doc.add(tabla);
    }

    private void agregarTH(PdfPTable tabla, String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, F_TH));
        cell.setBackgroundColor(COLOR_PRIMARY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(7);
        cell.setBorderColor(COLOR_PRIMARY);
        tabla.addCell(cell);
    }

    private void agregarTD(PdfPTable tabla, String texto, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, F_TD));
        cell.setHorizontalAlignment(align);
        cell.setPadding(6);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(COLOR_BORDER);
        tabla.addCell(cell);
    }

    // ── OBSERVACIONES ────────────────────────────────────────────────────────

    private void agregarObservaciones(Document doc, OrdenCompra oc) throws Exception {
        if (oc.getObservaciones() == null || oc.getObservaciones().isBlank()) return;

        PdfPTable obs = new PdfPTable(1);
        obs.setWidthPercentage(100);
        obs.setSpacingAfter(16);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderWidthLeft(3f);
        cell.setBorderColorLeft(COLOR_PRIMARY);
        cell.setBackgroundColor(COLOR_HEADER_BG);
        cell.setPadding(10);

        cell.addElement(new Paragraph("OBSERVACIONES", F_LABEL));
        Font fObs = new Font(Font.HELVETICA, 9, Font.ITALIC, COLOR_TEXT_MID);
        cell.addElement(new Paragraph(oc.getObservaciones(), fObs));
        obs.addCell(cell);
        doc.add(obs);
    }

    // ── FIRMAS ───────────────────────────────────────────────────────────────

    private void agregarFirmas(Document doc) throws Exception {
        doc.add(new Paragraph(" "));
        PdfPTable firmas = new PdfPTable(3);
        firmas.setWidthPercentage(90);
        firmas.setHorizontalAlignment(Element.ALIGN_CENTER);
        firmas.setSpacingBefore(20);

        agregarLinea(firmas, "Elaborado por");
        agregarLinea(firmas, "Aprobado por");
        agregarLinea(firmas, "Recibido por");

        doc.add(firmas);
    }

    private void agregarLinea(PdfPTable tabla, String texto) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        // Línea
        PdfPTable lineaT = new PdfPTable(1);
        lineaT.setWidthPercentage(80);
        lineaT.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell lineaC = new PdfPCell();
        lineaC.setBorderWidthBottom(1f);
        lineaC.setBorderColorBottom(COLOR_TEXT_MID);
        lineaC.setBorderWidthTop(0);
        lineaC.setBorderWidthLeft(0);
        lineaC.setBorderWidthRight(0);
        lineaC.setFixedHeight(20f);
        lineaT.addCell(lineaC);
        cell.addElement(lineaT);

        Paragraph p = new Paragraph(texto, F_FOOTER);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(4);
        cell.addElement(p);
        tabla.addCell(cell);
    }

    // ── PIE DE PÁGINA CON NÚMERO ─────────────────────────────────────────────

    private static class PiePageNumero extends PdfPageEventHelper {
        private final Tenant tenant;
        PiePageNumero(Tenant tenant) { this.tenant = tenant; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            String piePagina = tenant.getPiePaginaPdf() != null && !tenant.getPiePaginaPdf().isBlank()
                    ? tenant.getPiePaginaPdf()
                    : (tenant.getNombre() != null ? tenant.getNombre() : "Fluxus") + " · Documento generado automáticamente";

            String texto = piePagina + "    |    Página " + writer.getPageNumber();
            Font f = new Font(Font.HELVETICA, 7, Font.NORMAL, COLOR_TEXT_SOFT);
            Phrase pie = new Phrase(texto, f);

            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, pie,
                    (document.left() + document.right()) / 2,
                    document.bottom() - 20, 0);
        }
    }
}
