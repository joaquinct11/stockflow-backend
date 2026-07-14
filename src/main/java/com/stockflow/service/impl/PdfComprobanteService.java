package com.stockflow.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.*;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Genera el PDF de boleta/factura con diseño inspirado en el formato alucode/lucode.
 * Usa OpenPDF (LGPL) + ZXing para QR.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfComprobanteService {

    private final ComprobanteRepository comprobanteRepository;
    private final TenantRepository      tenantRepository;

    // ── Paleta ────────────────────────────────────────────────────────────────
    private static final Color COL_BLUE_HEADER = new Color(68,  114, 196);   // cabecera tabla ítems
    private static final Color COL_BORDER      = new Color(203, 213, 225);   // slate-300
    private static final Color COL_LIGHT_BG    = new Color(248, 250, 252);   // slate-50
    private static final Color COL_TEXT        = new Color(15,  23,  42);    // near-black
    private static final Color COL_MUTED       = new Color(100, 116, 139);   // slate-500
    private static final Color COL_HEADER_BOX  = new Color(160, 180, 210);   // borde caja derecha

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
            spacer(doc, 4);
            writeReceptor(doc, c);
            spacer(doc, 4);
            writeObservacion(doc);
            spacer(doc, 4);
            writeInfoRow(doc, c);
            spacer(doc, 4);
            writeItems(doc, c);
            spacer(doc, 6);
            writeFooterSection(doc, c, tenant);

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

    // ── Secciones ─────────────────────────────────────────────────────────────

    /** Cabecera: nombre empresa a la izquierda | caja con RUC/tipo/serie a la derecha */
    private void writeHeader(Document doc, Comprobante c, Tenant tenant) throws DocumentException {
        Font fName   = new Font(Font.HELVETICA, 11, Font.BOLD,   COL_TEXT);
        Font fDash   = new Font(Font.HELVETICA,  9, Font.NORMAL, COL_MUTED);
        Font fRucBox = new Font(Font.HELVETICA,  9, Font.NORMAL, COL_TEXT);
        Font fTipo   = new Font(Font.HELVETICA, 13, Font.BOLD,   COL_TEXT);
        Font fNum    = new Font(Font.HELVETICA, 13, Font.BOLD,   COL_TEXT);

        PdfPTable tbl = new PdfPTable(2);
        tbl.setWidthPercentage(100);
        tbl.setWidths(new float[]{55, 45});

        // ── Izquierda: logo + nombre ──────────────────────────────────────────
        PdfPCell left = nobordeCell();
        left.setPaddingTop(8);
        left.setPaddingBottom(8);

        if (notBlank(tenant.getLogoBase64())) {
            try {
                String raw = tenant.getLogoBase64().replaceAll("^data:image/[^;]+;base64,", "");
                Image logo = Image.getInstance(Base64.getDecoder().decode(raw));
                logo.scaleToFit(140, 55);
                left.addElement(logo);
                left.addElement(new Paragraph(" ", fDash));
            } catch (Exception ex) {
                log.warn("No se pudo cargar logo: {}", ex.getMessage());
            }
        }

        left.addElement(new Paragraph(str(tenant.getNombre(), "Empresa"), fName));
        if (notBlank(tenant.getRuc()))
            left.addElement(new Paragraph("RUC: " + tenant.getRuc(), fDash));
        if (notBlank(tenant.getDireccion()))
            left.addElement(new Paragraph(tenant.getDireccion(), fDash));
        if (notBlank(tenant.getTelefono()))
            left.addElement(new Paragraph("Tel: " + tenant.getTelefono(), fDash));
        tbl.addCell(left);

        // ── Derecha: caja con borde (fondo blanco) ───────────────────────────
        boolean esBoleta = "BOLETA".equals(c.getTipo());
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.BOX);
        right.setBorderColor(COL_HEADER_BOX);
        right.setBorderWidth(1.5f);
        right.setBackgroundColor(Color.WHITE);
        right.setPadding(14);
        right.setHorizontalAlignment(Element.ALIGN_CENTER);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph rucP = new Paragraph("R.U.C. N° " + str(tenant.getRuc(), "—"), fRucBox);
        rucP.setAlignment(Element.ALIGN_CENTER);
        right.addElement(rucP);

        Paragraph tipoP = new Paragraph(esBoleta ? "BOLETA DE VENTA" : "FACTURA ELECTRÓNICA", fTipo);
        tipoP.setAlignment(Element.ALIGN_CENTER);
        tipoP.setSpacingBefore(4);
        right.addElement(tipoP);

        Paragraph numP = new Paragraph(str(c.getNumero(), "—"), fNum);
        numP.setAlignment(Element.ALIGN_CENTER);
        right.addElement(numP);

        tbl.addCell(right);
        doc.add(tbl);
    }

    /** Datos del receptor (cliente / empresa emisora del RUC) */
    private void writeReceptor(Document doc, Comprobante c) throws DocumentException {
        Font fLabel = new Font(Font.HELVETICA, 9, Font.BOLD,   COL_TEXT);
        Font fValue = new Font(Font.HELVETICA, 9, Font.NORMAL, COL_TEXT);

        PdfPTable tbl = new PdfPTable(1);
        tbl.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(COL_BORDER);
        cell.setBackgroundColor(COL_LIGHT_BG);
        cell.setPadding(8);

        boolean esFactura = "FACTURA".equals(c.getTipo());
        String docLabel  = esFactura ? "RUC"        : str(c.getReceptorDocTipo(), "DNI");
        String nameLabel = esFactura ? "Razón Social" : "Nombres";

        if (notBlank(c.getReceptorDocNumero()))
            cell.addElement(labeledLine(docLabel + ":   ", c.getReceptorDocNumero(), fLabel, fValue));
        if (notBlank(c.getReceptorNombre()))
            cell.addElement(labeledLine(nameLabel + ":   ", c.getReceptorNombre(), fLabel, fValue));

        String dir = notBlank(c.getReceptorDireccion()) ? c.getReceptorDireccion() : "SIN DIRECCIÓN ESPECIFICADA";
        cell.addElement(labeledLine("Dirección:   ", dir, fLabel, fValue));

        tbl.addCell(cell);
        doc.add(tbl);
    }

    /** Fila "Observación:" */
    private void writeObservacion(Document doc) throws DocumentException {
        Font fLabel = new Font(Font.HELVETICA, 9, Font.BOLD, COL_TEXT);

        PdfPTable tbl = new PdfPTable(1);
        tbl.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(COL_BORDER);
        cell.setBackgroundColor(COL_LIGHT_BG);
        cell.setPadding(7);
        cell.addElement(new Paragraph("Observación:", fLabel));

        tbl.addCell(cell);
        doc.add(tbl);
    }

    /** Tabla informativa: Moneda | Forma de pago | Fecha emisión | Fecha vencimiento | Orden compra */
    private void writeInfoRow(Document doc, Comprobante c) throws DocumentException {
        Font fHead = new Font(Font.HELVETICA, 8, Font.NORMAL, COL_MUTED);
        Font fVal  = new Font(Font.HELVETICA, 9, Font.BOLD,   COL_TEXT);

        PdfPTable tbl = new PdfPTable(5);
        tbl.setWidthPercentage(100);
        tbl.setWidths(new float[]{20, 20, 20, 20, 20});

        // Fila 1: etiquetas
        for (String label : new String[]{"Moneda", "Forma de pago", "Fecha de emisión", "Fecha de vencimiento", "Orden de compra"}) {
            PdfPCell hc = centeredCell(label, fHead);
            hc.setBackgroundColor(COL_LIGHT_BG);
            hc.setBorderColor(COL_BORDER);
            hc.setBorder(Rectangle.BOX);
            hc.setPadding(5);
            tbl.addCell(hc);
        }

        // Fila 2: valores
        String fechaEmision = c.getFechaEmision() != null ? c.getFechaEmision().format(DATE_FMT) : "—";
        String metodoPago   = "CONTADO";
        if (c.getVenta() != null && notBlank(c.getVenta().getMetodoPago()))
            metodoPago = c.getVenta().getMetodoPago();

        for (String val : new String[]{"SOLES", metodoPago, fechaEmision, "-", "S/N"}) {
            PdfPCell vc = centeredCell(val, fVal);
            vc.setBorderColor(COL_BORDER);
            vc.setBorder(Rectangle.BOX);
            vc.setPadding(5);
            tbl.addCell(vc);
        }

        doc.add(tbl);
    }

    /** Tabla de ítems con cabecera azul */
    private void writeItems(Document doc, Comprobante c) throws DocumentException {
        Font fHead = new Font(Font.HELVETICA, 9, Font.BOLD,   Color.WHITE);
        Font fBody = new Font(Font.HELVETICA, 8, Font.NORMAL, COL_TEXT);

        PdfPTable tbl = new PdfPTable(6);
        tbl.setWidthPercentage(100);
        tbl.setWidths(new float[]{9, 9, 14, 38, 15, 15});

        // Cabecera
        for (String h : new String[]{"Cant.", "UM", "Código", "Descripción", "Precio Unit.", "Subtotal"}) {
            PdfPCell hc = new PdfPCell(new Phrase(h, fHead));
            hc.setBackgroundColor(COL_BLUE_HEADER);
            hc.setPadding(6);
            hc.setBorder(Rectangle.NO_BORDER);
            hc.setHorizontalAlignment("Descripción".equals(h) ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
            tbl.addCell(hc);
        }

        List<DetalleVenta> detalles = c.getVenta() != null && c.getVenta().getDetalles() != null
                ? c.getVenta().getDetalles() : List.of();

        for (DetalleVenta d : detalles) {
            String nombre = d.getProducto() != null ? d.getProducto().getNombre() : "Producto";
            String codigo = d.getProducto() != null ? str(d.getProducto().getCodigoBarras(), "") : "";

            addBodyCell(tbl, String.valueOf(d.getCantidad()),   fBody, Element.ALIGN_CENTER);
            addBodyCell(tbl, "NIU",                            fBody, Element.ALIGN_CENTER);
            addBodyCell(tbl, codigo,                           fBody, Element.ALIGN_CENTER);
            addBodyCell(tbl, nombre,                           fBody, Element.ALIGN_LEFT);
            addBodyCell(tbl, fmt(d.getPrecioUnitario()),       fBody, Element.ALIGN_RIGHT);
            addBodyCell(tbl, fmt(d.getSubtotal()),             fBody, Element.ALIGN_RIGHT);
        }

        if (detalles.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Sin detalle de productos", fBody));
            empty.setColspan(6);
            empty.setPadding(8);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBorderColor(COL_BORDER);
            tbl.addCell(empty);
        }

        doc.add(tbl);
    }

    /** Sección inferior: [QR + texto] izquierda | [caja totales] derecha, luego fila SON */
    private void writeFooterSection(Document doc, Comprobante c, Tenant tenant) throws DocumentException {
        Font fSmall  = new Font(Font.HELVETICA, 8, Font.NORMAL, COL_TEXT);
        Font fSmallB = new Font(Font.HELVETICA, 8, Font.BOLD,   COL_TEXT);
        Font fHash   = new Font(Font.HELVETICA, 7, Font.NORMAL, COL_MUTED);
        Font fLabel  = new Font(Font.HELVETICA, 9, Font.NORMAL, COL_TEXT);
        Font fTLabel = new Font(Font.HELVETICA, 9, Font.BOLD,   COL_TEXT);

        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{55, 45});

        // ── Izquierda: QR + texto ─────────────────────────────────────────────
        PdfPCell leftCell = nobordeCell();

        PdfPTable leftInner = new PdfPTable(2);
        leftInner.setWidthPercentage(100);
        leftInner.setWidths(new float[]{38, 62});

        // QR
        PdfPCell qrCell = nobordeCell();
        qrCell.setPaddingRight(6);
        if (notBlank(c.getQr())) {
            Image qrImg = generarImagenQr(c.getQr(), 90);
            if (qrImg != null) {
                qrCell.addElement(qrImg);
            }
        }
        leftInner.addCell(qrCell);

        // Texto junto al QR
        PdfPCell qrText = nobordeCell();
        qrText.setPaddingTop(2);

        boolean esBoleta = "BOLETA".equals(c.getTipo());
        Phrase reprFrase = new Phrase();
        reprFrase.add(new Chunk("Representación impresa de la ", fSmall));
        reprFrase.add(new Chunk(esBoleta ? "Boleta De Venta" : "Factura Electrónica", fSmallB));
        Paragraph reprP = new Paragraph();
        reprP.add(reprFrase);
        qrText.addElement(reprP);

        if (notBlank(c.getHash()))
            qrText.addElement(new Paragraph("HASH: " + c.getHash(), fHash));

        leftInner.addCell(qrText);
        leftCell.addElement(leftInner);
        outer.addCell(leftCell);

        // ── Derecha: caja de totales ──────────────────────────────────────────
        PdfPCell rightCell = nobordeCell();
        rightCell.setPaddingLeft(10);

        PdfPTable totals = new PdfPTable(3);
        totals.setWidthPercentage(100);
        totals.setWidths(new float[]{55, 12, 33});

        double igvPct = tenant.getIgvPorcentaje() != null ? tenant.getIgvPorcentaje() : 18.0;
        String igvLabel = "IGV " + (igvPct == Math.floor(igvPct)
                ? (int) igvPct + ".00" : igvPct) + "%";

        addTotalsRow(totals, "Op. Gravadas", "S/", fmt(c.getSubtotal()), fLabel);
        addTotalsRow(totals, igvLabel,        "S/", fmt(c.getIgv()),     fLabel);
        addTotalsRow(totals, "Importe Total", "S/", fmt(c.getTotal()),   fTLabel);

        rightCell.addElement(totals);
        outer.addCell(rightCell);

        doc.add(outer);
        spacer(doc, 6);

        // ── Fila SON ─────────────────────────────────────────────────────────
        Font fSon  = new Font(Font.HELVETICA, 9, Font.NORMAL, COL_TEXT);
        Font fSonB = new Font(Font.HELVETICA, 9, Font.BOLD,   COL_TEXT);

        PdfPTable sonTbl = new PdfPTable(1);
        sonTbl.setWidthPercentage(100);
        PdfPCell sonCell = new PdfPCell();
        sonCell.setBorder(Rectangle.BOX);
        sonCell.setBorderColor(COL_BORDER);
        sonCell.setBackgroundColor(COL_LIGHT_BG);
        sonCell.setPadding(7);

        Paragraph sonP = new Paragraph();
        sonP.add(new Chunk("SON: ", fSonB));
        sonP.add(new Chunk(numeroALetras(c.getTotal()), fSon));
        sonCell.addElement(sonP);
        sonTbl.addCell(sonCell);
        doc.add(sonTbl);

        spacer(doc, 10);

        // ── Pie ───────────────────────────────────────────────────────────────
        Font fGen = new Font(Font.HELVETICA, 7, Font.NORMAL, COL_MUTED);
        centeredParagraph(doc, "Generated by Fluxus", fGen);
    }

    // ── Helpers de layout ─────────────────────────────────────────────────────

    private void addBodyCell(PdfPTable tbl, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        cell.setBorderColor(COL_BORDER);
        cell.setBorder(Rectangle.BOTTOM);
        tbl.addCell(cell);
    }

    private void addTotalsRow(PdfPTable tbl, String label, String currency, String amount, Font font) {
        PdfPCell lc = new PdfPCell(new Phrase(label, font));
        lc.setBorder(Rectangle.BOX);
        lc.setBorderColor(COL_BORDER);
        lc.setPadding(5);
        lc.setPaddingLeft(7);
        tbl.addCell(lc);

        PdfPCell cc = new PdfPCell(new Phrase(currency, font));
        cc.setBorder(Rectangle.BOX);
        cc.setBorderColor(COL_BORDER);
        cc.setPadding(5);
        cc.setHorizontalAlignment(Element.ALIGN_CENTER);
        tbl.addCell(cc);

        PdfPCell ac = new PdfPCell(new Phrase(amount, font));
        ac.setBorder(Rectangle.BOX);
        ac.setBorderColor(COL_BORDER);
        ac.setPadding(5);
        ac.setHorizontalAlignment(Element.ALIGN_RIGHT);
        ac.setPaddingRight(7);
        tbl.addCell(ac);
    }

    private PdfPCell centeredCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderColor(COL_BORDER);
        return cell;
    }

    private PdfPCell nobordeCell() {
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
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

    private void spacer(Document doc, float height) throws DocumentException {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        doc.add(p);
    }

    // ── Generación de QR ─────────────────────────────────────────────────────

    /** Genera la imagen QR a partir del texto del campo qr. Retorna null si falla. */
    private Image generarImagenQr(String contenido, int tamano) {
        try {
            // Primero intentar como base64 (algunos proveedores devuelven la imagen directamente)
            String stripped = contenido.replaceAll("^data:image/[^;]+;base64,", "");
            if (stripped.length() > 50 && !stripped.contains(" ") && !stripped.contains("\n")) {
                try {
                    byte[] decoded = Base64.getDecoder().decode(stripped);
                    return Image.getInstance(decoded);
                } catch (Exception ignored) {
                    // No era base64; generar con ZXing
                }
            }

            // Generar QR con ZXing
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = writer.encode(contenido, BarcodeFormat.QR_CODE, tamano, tamano, hints);
            BufferedImage buffered = MatrixToImageWriter.toBufferedImage(matrix);

            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            ImageIO.write(buffered, "PNG", pngOut);
            Image img = Image.getInstance(pngOut.toByteArray());
            img.scaleToFit(tamano, tamano);
            return img;

        } catch (Exception e) {
            log.warn("No se pudo generar QR: {}", e.getMessage());
            return null;
        }
    }

    // ── Número a letras (estilo Perú) ────────────────────────────────────────

    private String numeroALetras(BigDecimal monto) {
        if (monto == null) return "CERO CON 00/100 SOLES";
        long entero    = monto.longValue();
        int centavos   = monto.remainder(BigDecimal.ONE)
                              .multiply(BigDecimal.valueOf(100))
                              .abs().intValue();
        return enteroALetras(entero).toUpperCase() + " CON "
                + String.format("%02d", centavos) + "/100 SOLES";
    }

    private static final String[] UNIDADES = {
        "", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
        "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE",
        "DIECISEIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE"
    };
    private static final String[] DECENAS = {
        "", "", "VEINTE", "TREINTA", "CUARENTA", "CINCUENTA",
        "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"
    };
    private static final String[] CENTENAS = {
        "", "CIEN", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
        "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"
    };

    private String enteroALetras(long n) {
        if (n == 0)  return "CERO";
        if (n < 0)   return "MENOS " + enteroALetras(-n);
        if (n < 20)  return UNIDADES[(int) n];
        if (n < 100) {
            int dec = (int)(n / 10);
            int rem = (int)(n % 10);
            if (n == 20) return "VEINTE";
            if (n < 30)  return "VEINTI" + UNIDADES[rem].toLowerCase();
            return rem == 0 ? DECENAS[dec] : DECENAS[dec] + " Y " + UNIDADES[rem];
        }
        if (n < 1000) {
            int cent = (int)(n / 100);
            int rem  = (int)(n % 100);
            String centStr = (cent == 1 && rem > 0) ? "CIENTO" : CENTENAS[cent];
            return rem == 0 ? centStr : centStr + " " + enteroALetras(rem);
        }
        if (n < 1_000_000) {
            long miles = n / 1000;
            long rem   = n % 1000;
            String milesStr = miles == 1 ? "MIL" : enteroALetras(miles) + " MIL";
            return rem == 0 ? milesStr : milesStr + " " + enteroALetras(rem);
        }
        long millones = n / 1_000_000;
        long rem      = n % 1_000_000;
        String millStr = millones == 1 ? "UN MILLÓN" : enteroALetras(millones) + " MILLONES";
        return rem == 0 ? millStr : millStr + " " + enteroALetras(rem);
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
