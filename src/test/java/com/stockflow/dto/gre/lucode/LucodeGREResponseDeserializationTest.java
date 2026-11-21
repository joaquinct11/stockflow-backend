package com.stockflow.dto.gre.lucode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifica que LucodeGREResponse y LucodeGREPayload deserializan correctamente
 * frente a respuestas reales de Lucode, incluido el campo "pdf" que causó el bug T001-2.
 *
 * Tests T-DES-1 a T-DES-7 corresponden al FLUXUS F-08 (corrección crítica GRE).
 */
@DisplayName("LucodeGREResponse — deserialización robusta")
class LucodeGREResponseDeserializationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── T-DES-1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T-DES-1: Lucode responde con pdf.a4 → deserializa sin excepción, success=true, estado=ACEPTADO")
    void respuestaConPdfA4_deserializaCorrectamente() throws Exception {
        String json = """
                {
                  "success": true,
                  "message": "GRE emitida correctamente",
                  "payload": {
                    "hash": "abc123def456",
                    "xml": "https://cdn.lucode.pe/xml/T001-2.xml",
                    "cdr": "https://cdn.lucode.pe/cdr/T001-2.zip",
                    "estado": "ACEPTADO",
                    "pdf": {
                      "a4": "https://cdn.lucode.pe/pdf/T001-2-a4.pdf"
                    }
                  }
                }
                """;

        LucodeGREResponse resp = MAPPER.readValue(json, LucodeGREResponse.class);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getPayload()).isNotNull();
        assertThat(resp.getPayload().getEstado()).isEqualTo("ACEPTADO");
        assertThat(resp.getPayload().getHash()).isEqualTo("abc123def456");
        assertThat(resp.getPayload().getPdf()).isNotNull();
        assertThat(resp.getPayload().getPdf().getA4())
                .isEqualTo("https://cdn.lucode.pe/pdf/T001-2-a4.pdf");
    }

    // ── T-DES-2 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T-DES-2: Lucode responde sin campo pdf → retrocompatible, pdf es null")
    void respuestaSinPdf_deserializaCorrectamente() throws Exception {
        String json = """
                {
                  "success": true,
                  "message": "OK",
                  "payload": {
                    "hash": "hash999",
                    "xml": "https://cdn.lucode.pe/xml/T001-5.xml",
                    "cdr": "https://cdn.lucode.pe/cdr/T001-5.zip",
                    "estado": "ACEPTADO"
                  }
                }
                """;

        LucodeGREResponse resp = MAPPER.readValue(json, LucodeGREResponse.class);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getPayload().getHash()).isEqualTo("hash999");
        assertThat(resp.getPayload().getPdf()).isNull();
    }

    // ── T-DES-3 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T-DES-3: Lucode agrega campos desconocidos en payload → @JsonIgnoreProperties los ignora")
    void camposDesconocidosEnPayload_seIgnoran() {
        String json = """
                {
                  "success": true,
                  "message": "OK",
                  "payload": {
                    "hash": "hashX",
                    "xml": "https://cdn.lucode.pe/xml/T001-6.xml",
                    "cdr": "https://cdn.lucode.pe/cdr/T001-6.zip",
                    "estado": "ACEPTADO",
                    "pdf": { "a4": "https://cdn.lucode.pe/pdf/a4.pdf", "ticket": "https://cdn.lucode.pe/pdf/ticket.pdf" },
                    "sunat_code": "0",
                    "sunat_description": "La operacion de consulta ha sido procesada"
                  }
                }
                """;

        assertThatCode(() -> MAPPER.readValue(json, LucodeGREResponse.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> {
            LucodeGREResponse resp = MAPPER.readValue(json, LucodeGREResponse.class);
            assertThat(resp.getPayload().getHash()).isEqualTo("hashX");
        }).doesNotThrowAnyException();
    }

    // ── T-DES-4 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T-DES-4: Lucode rechaza la GRE (success=false) → isSuccess() retorna false")
    void respuestaDeRechazo_isSuccessEsFalse() throws Exception {
        String json = """
                {
                  "success": false,
                  "message": "El ubigeo de partida es requerido.",
                  "errors": {
                    "puntoDePartida.ubigeo": ["El campo ubigeo de partida es requerido."]
                  }
                }
                """;

        LucodeGREResponse resp = MAPPER.readValue(json, LucodeGREResponse.class);

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getMessage()).contains("ubigeo de partida");
        assertThat(resp.getPayload()).isNull();
    }

    // ── T-DES-5 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T-DES-5: Lucode responde con campos desconocidos en la raíz → LucodeGREResponse los ignora")
    void camposDesconocidosEnRaiz_seIgnoran() {
        String json = """
                {
                  "success": true,
                  "message": "OK",
                  "request_id": "req-99999",
                  "timestamp": "2026-11-20T10:00:00Z",
                  "payload": {
                    "hash": "hashY",
                    "xml": "https://cdn.lucode.pe/xml/T001-7.xml",
                    "cdr": "https://cdn.lucode.pe/cdr/T001-7.zip",
                    "estado": "ACEPTADO"
                  }
                }
                """;

        assertThatCode(() -> {
            LucodeGREResponse resp = MAPPER.readValue(json, LucodeGREResponse.class);
            assertThat(resp.isSuccess()).isTrue();
        }).doesNotThrowAnyException();
    }

    // ── T-DES-6 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T-DES-6: Reproducción exacta del bug T001-2 — antes del fix lanzaba UnrecognizedPropertyException")
    void reproduccionBugT001_2_noLanzaExcepcion() {
        // Exactamente el JSON que Lucode devolvió para T001-2 y causó el bug
        String jsonT001_2 = """
                {
                  "success": true,
                  "message": null,
                  "payload": {
                    "hash": "lucode-hash-t001-2",
                    "xml": "https://cdn.lucode.pe/xml/T001-2.xml",
                    "cdr": "https://cdn.lucode.pe/cdr/T001-2.zip",
                    "estado": "ACEPTADO",
                    "pdf": {
                      "a4": "https://cdn.lucode.pe/pdf/T001-2-a4.pdf"
                    }
                  }
                }
                """;

        // Antes del fix: jackson.databind.exc.UnrecognizedPropertyException: Unrecognized field "pdf"
        // Después del fix: deserializa sin excepción
        assertThatCode(() -> {
            LucodeGREResponse resp = MAPPER.readValue(jsonT001_2, LucodeGREResponse.class);
            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getPayload().getEstado()).isEqualTo("ACEPTADO");
            assertThat(resp.getPayload().getHash()).isEqualTo("lucode-hash-t001-2");
        }).doesNotThrowAnyException();
    }

    // ── T-DES-7 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("T-DES-7: Reconciliación T001-2 — todos los campos necesarios para recuperar el estado EMITIDA")
    void reconciliacioN_camposNecesariosPresentes() throws Exception {
        // Verifica que LucodeGREPayload expone hash, xml, cdr, estado, pdf.a4
        // — exactamente los campos que el SQL de reconciliación necesita para T001-2
        String json = """
                {
                  "success": true,
                  "message": null,
                  "payload": {
                    "hash": "reconcile-hash-001",
                    "xml": "https://cdn.lucode.pe/xml/recon.xml",
                    "cdr": "https://cdn.lucode.pe/cdr/recon.zip",
                    "estado": "ACEPTADO",
                    "pdf": {
                      "a4": "https://cdn.lucode.pe/pdf/recon-a4.pdf"
                    }
                  }
                }
                """;

        LucodeGREResponse resp = MAPPER.readValue(json, LucodeGREResponse.class);
        LucodeGREPayload payload = resp.getPayload();

        // Todos los campos que se escriben en la entidad GuiaRemision al emitir
        assertThat(payload.getHash()).isNotBlank();   // → guia.setLucodeHash()
        assertThat(payload.getXml()).isNotBlank();    // → guia.setXmlUrl()
        assertThat(payload.getCdr()).isNotBlank();    // → guia.setCdrUrl()
        assertThat(payload.getEstado()).isEqualTo("ACEPTADO");
        assertThat(payload.getPdf()).isNotNull();
        assertThat(payload.getPdf().getA4()).isNotBlank();
    }
}
