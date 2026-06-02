package com.stockflow.util;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.stockflow.entity.DevolucionDetalle;
import com.stockflow.entity.NotaCredito;
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
 * Genera el PDF de una Nota de Credito usando OpenPDF (com.lowagie).
 * Sigue el mismo estilo visual que OcPdfGenerator.
 */
@Slf4j
@Component
public class NotaCreditoPdfGenerator {

    // ── Colores corporativos ─────────────────────────────────────────────────
    private static final Color COLOR_PRIMARY   = new Color(79, 70, 229);   // indigo-600
    private static final Color COLOR_HEADER_BG = new Color(238, 242, 255); // indigo-50
    private static final Color COLOR_ROW_ALT   = new Color(249, 250, 251); // gray-50
    private static final Color COLOR_BORDER    = new Color(209, 213, 219); // gray-300
    private static final Color COLOR_TEXT_DARK = new Color(17,  24,  39);  // gray-900
    private static final Color COLOR_TEXT_MID  = new Color(75,  85,  99);  // gray-600
    private static final Color COLOR_TEXT_SOFT = new Color(156, 163, 175); // gray-400
    private static final Color COLOR_GREEN     = new Color(22, 163, 74);   // green-600

    // ── Fuentes ──────────────────────────────────────────────────────────────
    private static final Font F_TITLE    = new Font(Font.HELVETICA, 18, Font.BOLD,   COLOR_PRIMARY);
    private static final Font F_SUBTITLE = new Font(Font.HELVETICA,  9, Font.NORMAL, COLOR_TEXT_MID);
    private static final Font F_LABEL    = new Font(Font.HELVETICA,  8, Font.BOLD,   COLOR_TEXT_MID);
    private static final Font F_VALUE    = new Font(Font.HELVETICA,  9, Font.NORMAL, COLOR_TEXT_DARK);
    private static final Font F_VALUE_B  = new Font(Font.HELVETICA,  9, Font.BOLD,   COLOR_TEXT_DARK);
    private static final Font F_TH       = new Font(Font.HELVETICA,  8, Font.BOLD,   Color.WHITE);
    private static final Font F_TD       = new Font(Font.HELVETICA,  8, Font.NORMAL, COLOR_TEXT_DARK);
    private static final Font F_TOTAL_L  = new Font(Font.HELVETICA,  9, Font.BOLD,   COLOR_TEXT_DARK);
    private static final Font F_TOTAL_V  = new Font(Font.HELVETICA, 12, Font.BOLD,   COLOR_GREEN);
    private static final Font F_FOOTER   = new Font(Font.HELVETICA,  7, Font.NORMAL, COLOR_TEXT_SOFT);
    private static final Font F_PROMO    = new Font(Font.HELVETICA,  9, Font.ITALIC, COLOR_TEXT_MID);

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ────────────────────────────────────────────────────────────────────────

    public byte[] generar(NotaCredito nc, Tenant tenant) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            writer.setPageEvent(new PiePagina(tenant));

            doc.open();

            // 1. Encabezado con logo y datos del negocio + caja NC
            agregarEncabezado(doc, nc, tenant);

            // 2. Info box: datos de la devolucion
            agregarInfoBox(doc, nc, tenant);

            // 3. Tabla de productos devueltos
            agregarTablaProductos(doc, nc, tenant);

            // 4. Motivo
            agregarMotivo(doc, nc);

            // 5. Total a favor (caja destacada)
            agregarTotalFavor(doc, nc, tenant);

            // 6. Sello / leyenda
            agregarLeyenda(doc, nc);

            doc.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Error generando PDF de nota de credito #{}: {}", nc.getId(), e.getMessage(), e);
            throw new RuntimeException("Error generando PDF de Nota de Credito", e);
        }
    }

    // ── ENCABEZADO ───────────────────────────────────────────────────────────

    private void agregarEncabezado(Document doc, NotaCredito nc, Tenant tenant) throws Exception {
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
                celdaLogo.addElement(new Phrase(" ", F_SUBTITLE));
            } catch (Exception ex) {
                log.warn("No se pudo cargar el logo en el PDF: {}", ex.getMessage());
            }
        }

        String nombreNegocio = tenant.getNombre() != null ? tenant.getNombre() : "Mi Negocio";
        Paragraph pNombre = new Paragraph(nombreNegocio, F_TITLE);
        pNombre.setSpacingBefore(2);
        celdaLogo.addElement(pNombre);

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

        // Columna derecha: caja "NOTA DE CREDITO"
        PdfPCell celdaNC = new PdfPCell();
        celdaNC.setBorder(Rectangle.BOX);
        celdaNC.setBorderColor(COLOR_PRIMARY);
        celdaNC.setBorderWidth(1.5f);
        celdaNC.setBackgroundColor(COLOR_HEADER_BG);
        celdaNC.setPadding(12);
        celdaNC.setHorizontalAlignment(Element.ALIGN_CENTER);
        celdaNC.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Font fDocTitulo = new Font(Font.HELVETICA, 13, Font.BOLD, COLOR_PRIMARY);
        Font fDocCod    = new Font(Font.HELVETICA, 11, Font.BOLD, COLOR_TEXT_DARK);
        Font fDocFecha  = new Font(Font.HELVETICA,  9, Font.NORMAL, COLOR_TEXT_MID);

        Paragraph pDocTipo = new Paragraph("NOTA DE CREDITO", fDocTitulo);
        pDocTipo.setAlignment(Element.ALIGN_CENTER);
        celdaNC.addElement(pDocTipo);

        Paragraph pCodigo = new Paragraph(nc.getCodigo(), fDocCod);
        pCodigo.setAlignment(Element.ALIGN_CENTER);
        pCodigo.setSpacingBefore(6);
        celdaNC.addElement(pCodigo);

        String fechaEmision = nc.getFechaEmision() != null ? nc.getFechaEmision().format(FMT) : "—";
        String fechaVence   = nc.getFechaVencimiento() != null ? nc.getFechaVencimiento().format(FMT) : "—";

        Paragraph pFecha = new Paragraph("Fecha: " + fechaEmision, fDocFecha);
        pFecha.setAlignment(Element.ALIGN_CENTER);
        pFecha.setSpacingBefore(4);
        celdaNC.addElement(pFecha);

        Paragraph pVence = new Paragraph("Vence: " + fechaVence, fDocFecha);
        pVence.setAlignment(Element.ALIGN_CENTER);
        pVence.setSpacingBefore(2);
        celdaNC.addElement(pVence);

        // Badge de estado
        Font fEstado = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        Paragraph pEstado = new Paragraph(nc.getEstado(), fEstado);
        pEstado.setAlignment(Element.ALIGN_CENTER);
        PdfPTable badgeTable = new PdfPTable(1);
        badgeTable.setWidthPercentage(60);
        badgeTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell badgeCell = new PdfPCell(pEstado);
        Color badgeColor = "PENDIENTE".equals(nc.getEstado()) ? new Color(22, 163, 74) : COLOR_PRIMARY;
        badgeCell.setBackgroundColor(badgeColor);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeCell.setPadding(4);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeTable.addCell(badgeCell);
        celdaNC.addElement(new Phrase("\n"));
        celdaNC.addElement(badgeTable);

        header.addCell(celdaNC);
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

    private void agregarInfoBox(Document doc, NotaCredito nc, Tenant tenant) throws Exception {
        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setWidths(new float[]{50f, 50f});
        info.setSpacingAfter(14);

        // Celda izquierda: datos de la devolucion
        PdfPCell cDev = new PdfPCell();
        cDev.setBorder(Rectangle.BOX);
        cDev.setBorderColor(COLOR_BORDER);
        cDev.setBackgroundColor(COLOR_ROW_ALT);
        cDev.setPadding(10);

        cDev.addElement(new Paragraph("DATOS DE LA DEVOLUCION", F_LABEL));
        if (nc.getDevolucion() != null) {
            agregarFilaDatos(cDev, "Devolucion #:", String.valueOf(nc.getDevolucion().getId()));
            if (nc.getDevolucion().getVenta() != null) {
                agregarFilaDatos(cDev, "Venta origen #:", String.valueOf(nc.getDevolucion().getVenta().getId()));
            }
            if (nc.getDevolucion().getFechaDevolucion() != null) {
                agregarFilaDatos(cDev, "Fecha devolucion:", nc.getDevolucion().getFechaDevolucion().format(FMT));
            }
            if (nc.getDevolucion().getUsuario() != null) {
                agregarFilaDatos(cDev, "Atendido por:", nc.getDevolucion().getUsuario().getNombre());
            }
        }
        info.addCell(cDev);

        // Celda derecha: datos de la nota de credito
        PdfPCell cNC = new PdfPCell();
        cNC.setBorder(Rectangle.BOX);
        cNC.setBorderColor(COLOR_BORDER);
        cNC.setPadding(10);

        cNC.addElement(new Paragraph("DATOS DE LA NOTA DE CREDITO", F_LABEL));
        agregarFilaDatos(cNC, "Codigo:", nc.getCodigo());
        agregarFilaDatos(cNC, "Moneda:", tenant.getMoneda() != null ? tenant.getMoneda() : "S/.");
        if (nc.getFechaEmision() != null) {
            agregarFilaDatos(cNC, "Fecha emision:", nc.getFechaEmision().format(FMT));
        }
        if (nc.getFechaVencimiento() != null) {
            agregarFilaDatos(cNC, "Fecha vence:", nc.getFechaVencimiento().format(FMT));
        }
        agregarFilaDatos(cNC, "Estado:", nc.getEstado());
        info.addCell(cNC);

        doc.add(info);
    }

    private void agregarFilaDatos(PdfPCell celda, String label, String valor) {
        PdfPTable fila = new PdfPTable(2);
        fila.setWidthPercentage(100);
        try { fila.setWidths(new float[]{42f, 58f}); } catch (Exception ignored) {}

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

    // ── TABLA DE PRODUCTOS ────────────────────────────────────────────────────

    private void agregarTablaProductos(Document doc, NotaCredito nc, Tenant tenant) throws Exception {
        String moneda = tenant.getMoneda() != null ? tenant.getMoneda() : "S/.";

        Paragraph titulo = new Paragraph("PRODUCTOS DEVUELTOS", F_LABEL);
        titulo.setSpacingAfter(6);
        doc.add(titulo);

        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{44f, 14f, 21f, 21f});
        tabla.setSpacingAfter(10);

        agregarTH(tabla, "PRODUCTO");
        agregarTH(tabla, "CANT.");
        agregarTH(tabla, "P. UNIT. (" + moneda + ")");
        agregarTH(tabla, "SUBTOTAL (" + moneda + ")");

        List<DevolucionDetalle> detalles = (nc.getDevolucion() != null && nc.getDevolucion().getDetalles() != null)
                ? nc.getDevolucion().getDetalles()
                : List.of();

        BigDecimal total = BigDecimal.ZERO;
        boolean altRow = false;

        for (DevolucionDetalle d : detalles) {
            Color bg = altRow ? COLOR_ROW_ALT : Color.WHITE;
            altRow = !altRow;

            String nombre = d.getProducto() != null ? d.getProducto().getNombre() : "—";
            int cant = d.getCantidadDevuelta() != null ? d.getCantidadDevuelta() : 0;
            BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
            BigDecimal sub = d.getSubtotal() != null ? d.getSubtotal() : BigDecimal.ZERO;
            total = total.add(sub);

            agregarTD(tabla, nombre,                           Element.ALIGN_LEFT,   bg);
            agregarTD(tabla, String.valueOf(cant),             Element.ALIGN_CENTER, bg);
            agregarTD(tabla, String.format("%.2f", precio),   Element.ALIGN_RIGHT,  bg);
            agregarTD(tabla, String.format("%.2f", sub),      Element.ALIGN_RIGHT,  bg);
        }

        // Fila total
        PdfPCell cTotalLabel = new PdfPCell(new Phrase("SUBTOTAL", F_TOTAL_L));
        cTotalLabel.setColspan(3);
        cTotalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cTotalLabel.setPadding(8);
        cTotalLabel.setBackgroundColor(COLOR_HEADER_BG);
        cTotalLabel.setBorderColor(COLOR_PRIMARY);
        tabla.addCell(cTotalLabel);

        BigDecimal montoFinal = nc.getMontoTotal() != null ? nc.getMontoTotal() : total;
        PdfPCell cTotalVal = new PdfPCell(
                new Phrase(moneda + " " + String.format("%.2f", montoFinal), F_TOTAL_V));
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

    // ── MOTIVO ────────────────────────────────────────────────────────────────

    private void agregarMotivo(Document doc, NotaCredito nc) throws Exception {
        if (nc.getDevolucion() == null) return;
        String motivo = nc.getDevolucion().getMotivo();
        if (motivo == null || motivo.isBlank()) return;

        PdfPTable obs = new PdfPTable(1);
        obs.setWidthPercentage(100);
        obs.setSpacingAfter(12);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderWidthLeft(3f);
        cell.setBorderColorLeft(COLOR_PRIMARY);
        cell.setBackgroundColor(COLOR_HEADER_BG);
        cell.setPadding(10);

        cell.addElement(new Paragraph("MOTIVO DE LA DEVOLUCION", F_LABEL));
        cell.addElement(new Paragraph(motivo, F_PROMO));

        String observaciones = nc.getDevolucion().getObservaciones();
        if (observaciones != null && !observaciones.isBlank()) {
            cell.addElement(new Paragraph(observaciones, F_PROMO));
        }
        obs.addCell(cell);
        doc.add(obs);
    }

    // ── TOTAL A FAVOR ─────────────────────────────────────────────────────────

    private void agregarTotalFavor(Document doc, NotaCredito nc, Tenant tenant) throws Exception {
        String moneda = tenant.getMoneda() != null ? tenant.getMoneda() : "S/.";

        PdfPTable totalBox = new PdfPTable(1);
        totalBox.setWidthPercentage(60);
        totalBox.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalBox.setSpacingAfter(16);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(COLOR_GREEN);
        cell.setBorderWidth(2f);
        cell.setBackgroundColor(new Color(240, 253, 244)); // green-50
        cell.setPadding(14);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Font fLabel = new Font(Font.HELVETICA, 10, Font.BOLD, COLOR_GREEN);
        Font fMonto = new Font(Font.HELVETICA, 22, Font.BOLD, COLOR_GREEN);

        Paragraph pLabel = new Paragraph("TOTAL A FAVOR", fLabel);
        pLabel.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(pLabel);

        BigDecimal monto = nc.getMontoTotal() != null ? nc.getMontoTotal() : BigDecimal.ZERO;
        Paragraph pMonto = new Paragraph(moneda + " " + String.format("%.2f", monto), fMonto);
        pMonto.setAlignment(Element.ALIGN_CENTER);
        pMonto.setSpacingBefore(4);
        cell.addElement(pMonto);

        totalBox.addCell(cell);
        doc.add(totalBox);
    }

    // ── LEYENDA ───────────────────────────────────────────────────────────────

    private void agregarLeyenda(Document doc, NotaCredito nc) throws Exception {
        doc.add(new Paragraph(" "));

        // Línea separadora
        PdfPTable linea1 = new PdfPTable(1);
        linea1.setWidthPercentage(80);
        linea1.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell lc1 = new PdfPCell();
        lc1.setBorderWidthBottom(1f);
        lc1.setBorderColorBottom(COLOR_BORDER);
        lc1.setBorderWidthTop(0);
        lc1.setBorderWidthLeft(0);
        lc1.setBorderWidthRight(0);
        lc1.setFixedHeight(1f);
        linea1.addCell(lc1);
        doc.add(linea1);

        Font fLeyenda = new Font(Font.HELVETICA, 9, Font.ITALIC, COLOR_TEXT_MID);
        Font fCodigo  = new Font(Font.HELVETICA, 10, Font.BOLD, COLOR_PRIMARY);

        Paragraph p1 = new Paragraph("Este documento es valido para canjear en tu proxima compra.", fLeyenda);
        p1.setAlignment(Element.ALIGN_CENTER);
        p1.setSpacingBefore(8);
        doc.add(p1);

        Paragraph p2 = new Paragraph("Codigo: " + nc.getCodigo(), fCodigo);
        p2.setAlignment(Element.ALIGN_CENTER);
        p2.setSpacingBefore(4);
        doc.add(p2);

        // Línea separadora final
        PdfPTable linea2 = new PdfPTable(1);
        linea2.setWidthPercentage(80);
        linea2.setHorizontalAlignment(Element.ALIGN_CENTER);
        linea2.setSpacingBefore(8);
        PdfPCell lc2 = new PdfPCell();
        lc2.setBorderWidthBottom(1f);
        lc2.setBorderColorBottom(COLOR_BORDER);
        lc2.setBorderWidthTop(0);
        lc2.setBorderWidthLeft(0);
        lc2.setBorderWidthRight(0);
        lc2.setFixedHeight(1f);
        linea2.addCell(lc2);
        doc.add(linea2);

        if (nc.getFechaVencimiento() != null) {
            Font fVence = new Font(Font.HELVETICA, 8, Font.NORMAL, COLOR_TEXT_SOFT);
            Paragraph pVence = new Paragraph("Valido hasta: " + nc.getFechaVencimiento().format(FMT), fVence);
            pVence.setAlignment(Element.ALIGN_CENTER);
            pVence.setSpacingBefore(4);
            doc.add(pVence);
        }
    }

    // ── PIE DE PÁGINA ─────────────────────────────────────────────────────────

    private static class PiePagina extends PdfPageEventHelper {
        private final Tenant tenant;
        PiePagina(Tenant tenant) { this.tenant = tenant; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            String piePagina = tenant.getPiePaginaPdf() != null && !tenant.getPiePaginaPdf().isBlank()
                    ? tenant.getPiePaginaPdf()
                    : (tenant.getNombre() != null ? tenant.getNombre() : "Stockflow") + " · Documento generado automaticamente";

            String texto = piePagina + "    |    Pagina " + writer.getPageNumber();
            Font f = new Font(Font.HELVETICA, 7, Font.NORMAL, COLOR_TEXT_SOFT);
            Phrase pie = new Phrase(texto, f);

            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, pie,
                    (document.left() + document.right()) / 2,
                    document.bottom() - 20, 0);
        }
    }
}
