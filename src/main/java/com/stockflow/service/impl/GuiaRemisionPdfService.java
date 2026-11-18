package com.stockflow.service.impl;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.stockflow.entity.Tenant;
import com.stockflow.entity.gre.GuiaRemision;
import com.stockflow.entity.gre.GuiaRemisionItem;
import com.stockflow.entity.gre.GuiaRemisionTransporte;
import com.stockflow.entity.gre.ModalidadTransporteGRE;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.GuiaRemisionRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Genera el PDF local de una Guía de Remisión Electrónica.
 *
 * Lucode no devuelve PDF para GRE, por lo que el documento es generado localmente.
 * NOTA: este PDF es de referencia operativa. Para validez tributaria el usuario
 * debe consultar cpe.sunat.gob.pe con el hash y datos del documento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuiaRemisionPdfService {

    private final GuiaRemisionRepository guiaRepo;
    private final TenantRepository       tenantRepo;

    private static final Color COL_HEADER  = new Color(30,  64, 175);   // azul oscuro
    private static final Color COL_BORDER  = new Color(203, 213, 225);  // slate-300
    private static final Color COL_BG_ROW  = new Color(248, 250, 252);  // slate-50
    private static final Color COL_TEXT    = new Color(15,  23,  42);   // near-black
    private static final Color COL_MUTED   = new Color(100, 116, 139);  // slate-500

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional(readOnly = true)
    public byte[] generar(Long id) {
        String tenantId = TenantContext.getCurrentTenant();

        GuiaRemision guia = guiaRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Guía de remisión no encontrada id=" + id));

        Tenant tenant = tenantRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Configuración de empresa no encontrada"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 50);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            writeEncabezado(doc, guia, tenant);
            spacer(doc, 6);
            writeDatosTraslado(doc, guia);
            spacer(doc, 6);
            writeTransporte(doc, guia);
            spacer(doc, 6);
            writeItems(doc, guia);
            spacer(doc, 8);
            writeDatosTributarios(doc, guia);
            spacer(doc, 6);
            writePieDoc(doc);

            doc.close();
            log.debug("PDF GRE generado: {} ({} bytes)", guia.getNumeroCompleto(), baos.size());
            return baos.toByteArray();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generando PDF GRE {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Error generando PDF de guía de remisión", e);
        }
    }

    // ── Secciones ─────────────────────────────────────────────────────────────

    private void writeEncabezado(Document doc, GuiaRemision g, Tenant tenant) throws DocumentException {
        Font fNombre = new Font(Font.HELVETICA, 11, Font.BOLD, COL_TEXT);
        Font fMuted  = new Font(Font.HELVETICA,  9, Font.NORMAL, COL_MUTED);
        Font fTitulo = new Font(Font.HELVETICA, 11, Font.BOLD, COL_HEADER);
        Font fNum    = new Font(Font.HELVETICA, 13, Font.BOLD, COL_TEXT);

        PdfPTable tbl = new PdfPTable(2);
        tbl.setWidthPercentage(100);
        tbl.setWidths(new float[]{55, 45});

        // Izquierda — datos del emisor
        PdfPCell left = noborde();
        left.setPaddingTop(8);
        left.setPaddingBottom(8);

        if (notBlank(tenant.getLogoBase64())) {
            try {
                String raw = tenant.getLogoBase64().replaceAll("^data:image/[^;]+;base64,", "");
                Image logo = Image.getInstance(Base64.getDecoder().decode(raw));
                logo.scaleToFit(140, 55);
                left.addElement(logo);
                left.addElement(new Paragraph(" ", fMuted));
            } catch (Exception ex) {
                log.warn("No se pudo cargar logo: {}", ex.getMessage());
            }
        }

        left.addElement(new Paragraph(str(tenant.getNombre(), "Empresa"), fNombre));
        if (notBlank(tenant.getRuc()))
            left.addElement(new Paragraph("RUC: " + tenant.getRuc(), fMuted));
        if (notBlank(tenant.getDireccion()))
            left.addElement(new Paragraph(tenant.getDireccion(), fMuted));
        if (notBlank(tenant.getTelefono()))
            left.addElement(new Paragraph("Tel: " + tenant.getTelefono(), fMuted));
        tbl.addCell(left);

        // Derecha — caja con tipo de documento y número
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.BOX);
        right.setBorderColor(new Color(160, 180, 210));
        right.setBorderWidth(1.5f);
        right.setBackgroundColor(Color.WHITE);
        right.setPadding(14);
        right.setHorizontalAlignment(Element.ALIGN_CENTER);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);

        if (notBlank(tenant.getRuc())) {
            Paragraph rucP = new Paragraph("R.U.C. N° " + tenant.getRuc(), fMuted);
            rucP.setAlignment(Element.ALIGN_CENTER);
            right.addElement(rucP);
        }

        Paragraph tipoP = new Paragraph("GUÍA DE REMISIÓN ELECTRÓNICA", fTitulo);
        tipoP.setAlignment(Element.ALIGN_CENTER);
        tipoP.setSpacingBefore(4);
        right.addElement(tipoP);

        Paragraph subtipoP = new Paragraph("REMITENTE", new Font(Font.HELVETICA, 9, Font.NORMAL, COL_MUTED));
        subtipoP.setAlignment(Element.ALIGN_CENTER);
        right.addElement(subtipoP);

        Paragraph numP = new Paragraph(str(g.getNumeroCompleto(), "—"), fNum);
        numP.setAlignment(Element.ALIGN_CENTER);
        numP.setSpacingBefore(6);
        right.addElement(numP);

        tbl.addCell(right);
        doc.add(tbl);
    }

    private void writeDatosTraslado(Document doc, GuiaRemision g) throws DocumentException {
        Font fLabel = new Font(Font.HELVETICA, 8, Font.BOLD, COL_MUTED);
        Font fValue = new Font(Font.HELVETICA, 9, Font.NORMAL, COL_TEXT);

        PdfPTable tbl = new PdfPTable(2);
        tbl.setWidthPercentage(100);
        tbl.setWidths(new float[]{50, 50});
        tbl.setSpacingBefore(2);

        addPair(tbl, "Fecha de emisión:",
                g.getCreatedAt() != null ? g.getCreatedAt().format(DATE_FMT) : "—", fLabel, fValue);
        addPair(tbl, "Fecha de traslado:",
                g.getFechaTraslado() != null ? g.getFechaTraslado().format(DATE_FMT) : "—", fLabel, fValue);

        String motivo = str(g.getMotivoTrasladoDescripcion(), g.getMotivoTrasladoCodigo());
        addPair(tbl, "Motivo de traslado:", motivo, fLabel, fValue);

        String modalidad = g.getModalidadTransporte() == ModalidadTransporteGRE.PRIVADO
                ? "Transporte Privado" : "Transporte Público";
        addPair(tbl, "Modalidad de transporte:", modalidad, fLabel, fValue);

        // Destinatario
        String destNombre = str(g.getDestinatarioRazonSocial(), "—");
        String destDoc    = g.getDestinatarioTipoDoc() + ": " + str(g.getDestinatarioNumDoc(), "—");
        addPair(tbl, "Destinatario:", destNombre + " (" + destDoc + ")", fLabel, fValue);
        addPair(tbl, "Dirección del destinatario:", str(g.getDestinatarioDireccion(), "—"), fLabel, fValue);

        doc.add(tbl);

        // Punto partida / llegada
        GuiaRemisionTransporte t = g.getTransporte();
        if (t != null) {
            PdfPTable tbl2 = new PdfPTable(2);
            tbl2.setWidthPercentage(100);
            tbl2.setWidths(new float[]{50, 50});
            tbl2.setSpacingBefore(4);

            String partida = str(t.getDireccionPartida(), "—")
                    + (notBlank(t.getUbigeoPartida()) ? " (Ubigeo: " + t.getUbigeoPartida() + ")" : "");
            String llegada = str(t.getDireccionLlegada(), "—")
                    + (notBlank(t.getUbigeoLlegada()) ? " (Ubigeo: " + t.getUbigeoLlegada() + ")" : "");

            addPair(tbl2, "Punto de partida:", partida, fLabel, fValue);
            addPair(tbl2, "Punto de llegada:", llegada, fLabel, fValue);
            doc.add(tbl2);
        }
    }

    private void writeTransporte(Document doc, GuiaRemision g) throws DocumentException {
        GuiaRemisionTransporte t = g.getTransporte();
        if (t == null) return;

        Font fLabel = new Font(Font.HELVETICA, 8, Font.BOLD, COL_MUTED);
        Font fValue = new Font(Font.HELVETICA, 9, Font.NORMAL, COL_TEXT);
        Font fSub   = new Font(Font.HELVETICA, 9, Font.BOLD, COL_TEXT);

        doc.add(new Paragraph("Datos del transporte", fSub));
        spacer(doc, 2);

        PdfPTable tbl = new PdfPTable(2);
        tbl.setWidthPercentage(100);
        tbl.setWidths(new float[]{50, 50});

        boolean esPrivado = g.getModalidadTransporte() == ModalidadTransporteGRE.PRIVADO;
        if (esPrivado) {
            addPair(tbl, "Placa del vehículo:", str(t.getVehiculoPlaca(), "—"), fLabel, fValue);
            String conductor = str(t.getConductorNombres(), "") + " " + str(t.getConductorApellidos(), "");
            addPair(tbl, "Conductor:", conductor.isBlank() ? "—" : conductor.trim(), fLabel, fValue);
            if (notBlank(t.getConductorNumDoc()))
                addPair(tbl, "Doc. conductor:", t.getConductorTipoDoc() + ": " + t.getConductorNumDoc(), fLabel, fValue);
            if (notBlank(t.getConductorLicencia()))
                addPair(tbl, "Licencia:", t.getConductorLicencia(), fLabel, fValue);
        } else {
            addPair(tbl, "Transportista:", str(t.getTransportistaDenominacion(), "—"), fLabel, fValue);
            addPair(tbl, "RUC transportista:", str(t.getTransportistaRuc(), "—"), fLabel, fValue);
            if (notBlank(t.getTransportistaNroRegistroMtc()))
                addPair(tbl, "Reg. MTC:", t.getTransportistaNroRegistroMtc(), fLabel, fValue);
            if (notBlank(t.getTransportistaNroAutorizacion()))
                addPair(tbl, "N° Autorización:", t.getTransportistaNroAutorizacion(), fLabel, fValue);
        }

        doc.add(tbl);
    }

    private void writeItems(Document doc, GuiaRemision g) throws DocumentException {
        Font fHead = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        Font fCell = new Font(Font.HELVETICA, 8, Font.NORMAL, COL_TEXT);

        PdfPTable tbl = new PdfPTable(4);
        tbl.setWidthPercentage(100);
        tbl.setWidths(new float[]{45, 20, 15, 20});

        String[] headers = {"Descripción", "Código", "Unidad", "Cantidad"};
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, fHead));
            c.setBackgroundColor(COL_HEADER);
            c.setPadding(5);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            tbl.addCell(c);
        }

        boolean alt = false;
        for (GuiaRemisionItem item : g.getItems()) {
            Color bg = alt ? COL_BG_ROW : Color.WHITE;
            addCell(tbl, str(item.getDescripcion(), "—"), fCell, Element.ALIGN_LEFT, bg);
            addCell(tbl, str(item.getCodigoProducto(), "—"), fCell, Element.ALIGN_CENTER, bg);
            addCell(tbl, str(item.getUnidadMedida(), "NIU"), fCell, Element.ALIGN_CENTER, bg);
            addCell(tbl, item.getCantidad() != null ? item.getCantidad().toPlainString() : "0", fCell, Element.ALIGN_RIGHT, bg);
            alt = !alt;
        }

        // Fila de totales
        Font fTot = new Font(Font.HELVETICA, 8, Font.BOLD, COL_TEXT);
        PdfPCell totLabel = new PdfPCell(new Phrase("Peso bruto total:", fTot));
        totLabel.setColspan(3);
        totLabel.setBorder(Rectangle.TOP);
        totLabel.setPadding(5);
        totLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tbl.addCell(totLabel);

        String pesoStr = g.getPesoBrutoTotal() != null
                ? g.getPesoBrutoTotal().toPlainString() + " " + str(g.getUnidadMedidaPeso(), "KGM")
                : "—";
        PdfPCell totVal = new PdfPCell(new Phrase(pesoStr, fTot));
        totVal.setBorder(Rectangle.TOP);
        totVal.setPadding(5);
        totVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tbl.addCell(totVal);

        doc.add(tbl);

        if (g.getNumeroBultos() != null) {
            Font fInfo = new Font(Font.HELVETICA, 8, Font.NORMAL, COL_MUTED);
            doc.add(new Paragraph("N° de bultos: " + g.getNumeroBultos(), fInfo));
        }
    }

    private void writeDatosTributarios(Document doc, GuiaRemision g) throws DocumentException {
        Font fSub   = new Font(Font.HELVETICA, 9, Font.BOLD, COL_TEXT);
        Font fLabel = new Font(Font.HELVETICA, 8, Font.BOLD, COL_MUTED);
        Font fValue = new Font(Font.HELVETICA, 8, Font.NORMAL, COL_TEXT);

        if (g.getLucodeHash() == null && g.getLucodeId() == null) return;

        doc.add(new Paragraph("Datos tributarios", fSub));
        spacer(doc, 2);

        PdfPTable tbl = new PdfPTable(2);
        tbl.setWidthPercentage(100);
        tbl.setWidths(new float[]{30, 70});

        if (notBlank(g.getLucodeId()))
            addPair(tbl, "ID Lucode:", g.getLucodeId(), fLabel, fValue);
        if (notBlank(g.getLucodeHash()))
            addPair(tbl, "Hash:", g.getLucodeHash(), fLabel, fValue);
        if (notBlank(g.getXmlUrl()))
            addPair(tbl, "XML URL:", g.getXmlUrl(), fLabel, fValue);

        doc.add(tbl);
    }

    private void writePieDoc(Document doc) throws DocumentException {
        Font fPie = new Font(Font.HELVETICA, 7, Font.ITALIC, COL_MUTED);
        Paragraph p = new Paragraph(
                "Documento generado por Fluxus. Para validez tributaria consulte cpe.sunat.gob.pe", fPie);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static PdfPCell noborde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static void addPair(PdfPTable tbl, String label, String value,
                                Font fLabel, Font fValue) {
        PdfPCell l = new PdfPCell(new Phrase(label, fLabel));
        l.setBorder(Rectangle.BOTTOM);
        l.setBorderColor(COL_BORDER);
        l.setPadding(4);
        tbl.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, fValue));
        v.setBorder(Rectangle.BOTTOM);
        v.setBorderColor(COL_BORDER);
        v.setPadding(4);
        tbl.addCell(v);
    }

    private static void addCell(PdfPTable tbl, String text, Font f,
                                int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(bg);
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        c.setBorderColor(COL_BORDER);
        tbl.addCell(c);
    }

    private static void spacer(Document doc, int pt) throws DocumentException {
        doc.add(new Paragraph(" ", new Font(Font.HELVETICA, pt)));
    }

    private static String str(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s : fallback;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
