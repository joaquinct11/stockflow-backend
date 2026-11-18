package com.stockflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.gre.lucode.LucodeGRERequest;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Tenant;
import com.stockflow.entity.gre.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("LucodeGREService — validaciones antes de enviar a Lucode")
class LucodeGREServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    // ── Helpers ──────────────────────────────────────────────────────────────

    private LucodeGREService service() {
        return serviceWithTenant(tenantSinUbigeo("t1"));
    }

    private LucodeGREService serviceWithTenant(Tenant tenant) {
        TenantRepository repo = mock(TenantRepository.class);
        when(repo.findByTenantId(tenant.getTenantId())).thenReturn(Optional.of(tenant));
        return new LucodeGREService(repo);
    }

    private Tenant tenantSinUbigeo(String tenantId) {
        return Tenant.builder().tenantId(tenantId).nombre("Negocio Test").build();
    }

    private Tenant tenantConUbigeo(String tenantId, String ubigeo, String dir) {
        return Tenant.builder()
                .tenantId(tenantId)
                .nombre("Negocio Test")
                .ubigeoDespacho(ubigeo)
                .direccionDespacho(dir)
                .build();
    }

    private Sucursal sucursalConUbigeo(String ubigeo) {
        return Sucursal.builder()
                .id(1L)
                .nombre("Tienda Principal")
                .ubigeo(ubigeo)
                .direccion("Av. Test 123")
                .build();
    }

    private GuiaRemision guiaBase(OffsetDateTime createdAt) {
        GuiaRemisionItem item = GuiaRemisionItem.builder()
                .descripcion("Paracetamol 500mg")
                .cantidad(BigDecimal.valueOf(3))
                .unidadMedida("NIU")
                .tenantId("t1")
                .build();

        GuiaRemisionTransporte transporte = GuiaRemisionTransporte.builder()
                .ubigeoPartida("150101")
                .direccionPartida("Av. Lima 100")
                .ubigeoLlegada("150110")
                .direccionLlegada("Av. Destino 200")
                .build();

        return GuiaRemision.builder()
                .id(1L)
                .tenantId("t1")
                .serie("T001")
                .correlativo(1)
                .tipoTraslado(TipoTrasladoGRE.SUCURSALES)
                .motivoTrasladoCodigo("01")
                .modalidadTransporte(ModalidadTransporteGRE.PRIVADO)
                .fechaTraslado(LocalDate.of(2026, 11, 17))
                .estadoTributario(EstadoTributarioGRE.BORRADOR)
                .sucursalOrigen(sucursalConUbigeo("150101"))
                .sucursalDestino(sucursalConUbigeo("150110"))
                .destinatarioRazonSocial("Sucursal Destino")
                .destinatarioDireccion("Av. Real 456")
                .createdAt(createdAt)
                .items(new ArrayList<>(List.of(item)))
                .transporte(transporte)
                .build();
    }

    // ── Tests originales (1–7) ───────────────────────────────────────────────

    @Test
    @DisplayName("GRE-LUCODE-1: horaDeEmision tiene formato HH:mm:ss")
    void horaDeEmision_tieneFormatoHHmmss() {
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 11, 17, 21, 15, 30, 0, ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);

        LucodeGRERequest req = service().buildRequest(guia);

        assertThat(req.getHoraDeEmision())
                .matches("\\d{2}:\\d{2}:\\d{2}")
                .describedAs("Debe tener formato HH:mm:ss");
    }

    @Test
    @DisplayName("GRE-LUCODE-2: horaDeEmision usa zona America/Lima (UTC-5), no UTC")
    void horaDeEmision_usaZonaHorariaLima() {
        // 21:30 UTC = 16:30 Lima (UTC-5)
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 11, 17, 21, 30, 45, 0, ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);

        LucodeGRERequest req = service().buildRequest(guia);

        assertThat(req.getHoraDeEmision()).startsWith("16:30:45");
    }

    @Test
    @DisplayName("GRE-LUCODE-3: ubigeo de partida null con sucursal sin ubigeo → BadRequestException")
    void ubigeoPartida_nulo_lanzaBadRequestException() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.getTransporte().setUbigeoPartida(null);
        guia.setSucursalOrigen(sucursalConUbigeo(null));

        assertThatThrownBy(() -> service().buildRequest(guia))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("partida")
                .hasMessageContaining("ubigeo");
    }

    @Test
    @DisplayName("GRE-LUCODE-4: dirección destinatario '-' → BadRequestException antes de Lucode")
    void direccionPlaceholder_lanzaBadRequestException() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setDestinatarioDireccion("-");
        guia.setSucursalDestino(null);

        assertThatThrownBy(() -> service().buildRequest(guia))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dirección del destinatario");
    }

    @Test
    @DisplayName("GRE-LUCODE-5: item con unidadMedida='BX' (Caja) se envía como 'BX' a Lucode")
    void unidadMedidaBX_sePasaCorrectamente() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.getItems().get(0).setUnidadMedida("BX");

        LucodeGRERequest req = service().buildRequest(guia);

        assertThat(req.getItems()).hasSize(1);
        assertThat(req.getItems().get(0).getUnidadDeMedida()).isEqualTo("BX");
    }

    @Test
    @DisplayName("GRE-LUCODE-6: ubigeo del transporte tiene prioridad sobre ubigeo de sucursal")
    void ubigeoTransporte_tienePrioridadSobreSucursal() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.getTransporte().setUbigeoPartida("999999");
        guia.setSucursalOrigen(sucursalConUbigeo("150101"));

        LucodeGRERequest req = service().buildRequest(guia);

        assertThat(req.getPuntoDePartida().getUbigeo()).isEqualTo("999999");
    }

    @Test
    @DisplayName("GRE-LUCODE-7: extractLucodeErrors extrae todos los campos de error del JSON 422")
    void extractLucodeErrors_extrae4Errores() throws Exception {
        String json = """
                {
                  "success": false,
                  "message": "El campo hora de emision no corresponde con el formato H:i:s. (y 3 errores más)",
                  "errors": {
                    "horaDeEmision": ["El campo hora de emision no corresponde con el formato H:i:s."],
                    "puntoDePartida.ubigeo": ["El campo punto de partida ubigeo es requerido."],
                    "puntoDeLlegada.ubigeo": ["El campo punto de llegada ubigeo es requerido."],
                    "destinatarioDireccion": ["El campo destinatario direccion es requerido."]
                  }
                }
                """;
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(json);

        String result = LucodeGREService.extractLucodeErrors(node);

        assertThat(result)
                .contains("horaDeEmision")
                .contains("puntoDePartida.ubigeo")
                .contains("puntoDeLlegada.ubigeo")
                .contains("destinatarioDireccion");
    }

    // ── Tests nuevos (8–17) — compatibilidad tenants sin sucursales ──────────

    @Test
    @DisplayName("GRE-COMPAT-1: tenant sin sucursales usa ubigeo_despacho del tenant como partida")
    void gre_tenantSinSucursales_usaUbigeoDespachoTenant() {
        Tenant tenant = tenantConUbigeo("t1", "150101", "Av. Despacho 999");
        LucodeGREService svc = serviceWithTenant(tenant);

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        // Sin sucursal de origen y sin ubigeo en transporte
        guia.setSucursalOrigen(null);
        guia.getTransporte().setUbigeoPartida(null);
        guia.getTransporte().setDireccionPartida(null);

        LucodeGRERequest req = svc.buildRequest(guia);

        assertThat(req.getPuntoDePartida().getUbigeo()).isEqualTo("150101");
        assertThat(req.getPuntoDePartida().getDireccion()).isEqualTo("Av. Despacho 999");
    }

    @Test
    @DisplayName("GRE-COMPAT-2: sucursal con ubigeo tiene prioridad sobre tenant.ubigeoDespacho")
    void gre_sucursalConUbigeo_usaSucursal_ignoraTenant() {
        Tenant tenant = tenantConUbigeo("t1", "150101", "Av. Tenant 1");
        LucodeGREService svc = serviceWithTenant(tenant);

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setSucursalOrigen(sucursalConUbigeo("040101")); // Arequipa
        guia.getTransporte().setUbigeoPartida(null);         // sin override de transporte

        LucodeGRERequest req = svc.buildRequest(guia);

        assertThat(req.getPuntoDePartida().getUbigeo()).isEqualTo("040101");
    }

    @Test
    @DisplayName("GRE-COMPAT-3: transporte.ubigeoPartida explícito respeta siempre su valor")
    void gre_transporteExplicito_respetaTransporte() {
        Tenant tenant = tenantConUbigeo("t1", "150101", "Av. Tenant 1");
        LucodeGREService svc = serviceWithTenant(tenant);

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setSucursalOrigen(sucursalConUbigeo("040101"));
        guia.getTransporte().setUbigeoPartida("130101"); // Cusco explícito

        LucodeGRERequest req = svc.buildRequest(guia);

        assertThat(req.getPuntoDePartida().getUbigeo()).isEqualTo("130101");
    }

    @Test
    @DisplayName("GRE-COMPAT-4: sin ningún origen (sucursal=null, tenant sin ubigeo, transporte=null) → BadRequestException")
    void gre_sinNingunOrigen_bloqueaEmision() {
        // Tenant sin ubigeo_despacho
        LucodeGREService svc = service(); // tenant "t1" sin ubigeoDespacho

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setSucursalOrigen(null);
        guia.getTransporte().setUbigeoPartida(null);

        assertThatThrownBy(() -> svc.buildRequest(guia))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("punto de despacho predeterminado");
    }

    @Test
    @DisplayName("GRE-COMPAT-5: traslado entre sucursales usa ubigeo de origen y destino")
    void gre_trasladoEntreSucursales_usaUbigeoOrigenYDestino() {
        LucodeGREService svc = service();

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setSucursalOrigen(sucursalConUbigeo("150101"));
        guia.setSucursalDestino(sucursalConUbigeo("040101"));
        guia.getTransporte().setUbigeoPartida(null);
        guia.getTransporte().setUbigeoLlegada(null);

        LucodeGRERequest req = svc.buildRequest(guia);

        assertThat(req.getPuntoDePartida().getUbigeo()).isEqualTo("150101");
        assertThat(req.getPuntoDeLlegada().getUbigeo()).isEqualTo("040101");
    }

    @Test
    @DisplayName("GRE-COMPAT-6: destino sin ubigeo → BadRequestException (tenant NO aplica como fallback)")
    void gre_destinoSinUbigeo_bloqueaEmision() {
        Tenant tenant = tenantConUbigeo("t1", "150101", "Av. Despacho");
        LucodeGREService svc = serviceWithTenant(tenant);

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setSucursalDestino(sucursalConUbigeo(null)); // destino sin ubigeo
        guia.getTransporte().setUbigeoLlegada(null);

        assertThatThrownBy(() -> svc.buildRequest(guia))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("llegada");
    }

    @Test
    @DisplayName("GRE-COMPAT-7: tenant A no usa configuración de tenant B — aislamiento multi-tenant")
    void gre_tenantANoUsaConfigTenantB() {
        TenantRepository repo = mock(TenantRepository.class);
        Tenant tenantA = tenantConUbigeo("tenantA", "150101", "Dir A");
        Tenant tenantB = tenantConUbigeo("tenantB", "999999", "Dir B");
        when(repo.findByTenantId("tenantA")).thenReturn(Optional.of(tenantA));
        when(repo.findByTenantId("tenantB")).thenReturn(Optional.of(tenantB));
        LucodeGREService svc = new LucodeGREService(repo);

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guiaA = guiaBase(createdAt);
        guiaA.setTenantId("tenantA");
        guiaA.setSucursalOrigen(null);
        guiaA.getTransporte().setUbigeoPartida(null);

        LucodeGRERequest req = svc.buildRequest(guiaA);

        assertThat(req.getPuntoDePartida().getUbigeo()).isEqualTo("150101"); // ubigeo de A, no de B
        verify(repo, never()).findByTenantId("tenantB");
    }

    @Test
    @DisplayName("GRE-COMPAT-8: cuando ubigeo viene del tenant, dirección también viene del tenant")
    void gre_direccionCoherenteConUbigeo_desdeTenant() {
        Tenant tenant = tenantConUbigeo("t1", "150101", "Av. Despacho Tenant 777");
        LucodeGREService svc = serviceWithTenant(tenant);

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setSucursalOrigen(null);
        guia.getTransporte().setUbigeoPartida(null);
        guia.getTransporte().setDireccionPartida(null);

        LucodeGRERequest req = svc.buildRequest(guia);

        assertThat(req.getPuntoDePartida().getUbigeo()).isEqualTo("150101");
        assertThat(req.getPuntoDePartida().getDireccion()).isEqualTo("Av. Despacho Tenant 777");
    }

    @Test
    @DisplayName("GRE-COMPAT-9: validación fallida no llama a Lucode (buildRequest lanza antes de HTTP)")
    void gre_validacionFallida_noLlamaLucode_noModificaInventario() {
        // Si buildRequest() lanza BadRequestException, el llamado HTTP nunca ocurre.
        // Verificamos que la excepción se propaga antes de cualquier operación remota.
        LucodeGREService svc = service(); // tenant sin ubigeoDespacho

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setSucursalOrigen(null);
        guia.getTransporte().setUbigeoPartida(null);

        // buildRequest lanza → emitirGRE nunca llegaría a enviar HTTP
        assertThatThrownBy(() -> svc.buildRequest(guia))
                .isInstanceOf(BadRequestException.class);
        // No hay cliente HTTP que verificar aquí; la cobertura de inventario
        // queda en GuiaRemisionStockValidationTest (emitirGRE no llega al catch-to-inventario)
    }

    @Test
    @DisplayName("GRE-VAL-1: ubigeo de transporte con espacios en blanco se trata como vacío → fallback a sucursal")
    void gre_ubigeoTransporteEspacios_fallbackASucursal() {
        LucodeGREService svc = service();

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.getTransporte().setUbigeoPartida("   ");         // solo espacios = vacío lógico
        guia.setSucursalOrigen(sucursalConUbigeo("040101")); // Arequipa

        LucodeGRERequest req = svc.buildRequest(guia);

        assertThat(req.getPuntoDePartida().getUbigeo()).isEqualTo("040101");
    }

    @Test
    @DisplayName("GRE-VAL-2: resolveDestinatarioDireccion retorna null sin dirección → BadRequestException antes de Lucode")
    void gre_destinatarioDireccionNull_lanzaBadRequest() {
        LucodeGREService svc = service();

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setDestinatarioDireccion(null);
        guia.setSucursalDestino(null); // sin destino que provea dirección automática

        assertThatThrownBy(() -> svc.buildRequest(guia))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dirección del destinatario");
    }

    @Test
    @DisplayName("GRE-COMPAT-10: buildRequest exitoso con fallback al tenant produce request coherente")
    void gre_buildRequestExitoso_conFallbackTenant() {
        Tenant tenant = tenantConUbigeo("t1", "150101", "Av. Despacho Central");
        LucodeGREService svc = serviceWithTenant(tenant);

        OffsetDateTime createdAt = OffsetDateTime.of(2026, 11, 17, 21, 0, 0, 0, ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setSucursalOrigen(null);
        guia.getTransporte().setUbigeoPartida(null);
        guia.getTransporte().setDireccionPartida(null);

        LucodeGRERequest req = svc.buildRequest(guia);

        // La request es coherente: tiene ubigeo, número, serie y hora bien formada
        assertThat(req.getSerie()).isEqualTo("T001");
        assertThat(req.getPuntoDePartida().getUbigeo()).isEqualTo("150101");
        assertThat(req.getPuntoDePartida().getDireccion()).isEqualTo("Av. Despacho Central");
        assertThat(req.getHoraDeEmision()).matches("\\d{2}:\\d{2}:\\d{2}");
        assertThat(req.getPuntoDeLlegada().getUbigeo()).isEqualTo("150110"); // desde transporte
    }

    // ── Tests fecha de traslado (GRE-FECHA-1 a GRE-FECHA-4) ─────────────────

    @Test
    @DisplayName("GRE-FECHA-1: transporte.fechaInicioTraslado null → usa guia.fechaTraslado como fallback")
    void fechaInicioDeTraslado_transporteNull_usaFechaTraslado() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setFechaTraslado(LocalDate.of(2026, 11, 17));
        guia.getTransporte().setFechaInicioTraslado(null);

        LucodeGRERequest req = service().buildRequest(guia);

        assertThat(req.getFechaInicioDeTraslado())
                .isEqualTo("2026-11-17")
                .describedAs("Debe usar fechaTraslado de la GRE como fallback");
    }

    @Test
    @DisplayName("GRE-FECHA-2: transporte.fechaInicioTraslado explícita tiene prioridad sobre guia.fechaTraslado")
    void fechaInicioDeTraslado_transporteExplicito_tienePrioridad() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setFechaTraslado(LocalDate.of(2026, 11, 17));
        guia.getTransporte().setFechaInicioTraslado(LocalDate.of(2026, 11, 20));

        LucodeGRERequest req = service().buildRequest(guia);

        assertThat(req.getFechaInicioDeTraslado())
                .isEqualTo("2026-11-20")
                .describedAs("Debe usar la fecha explícita del transporte");
    }

    @Test
    @DisplayName("GRE-FECHA-3: fecha conserva día sin desplazamiento de zona horaria")
    void fechaInicioDeTraslado_sinDesplazamientoZonaHoraria() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setFechaTraslado(LocalDate.of(2026, 11, 17));
        guia.getTransporte().setFechaInicioTraslado(null);

        LucodeGRERequest req = service().buildRequest(guia);

        assertThat(req.getFechaInicioDeTraslado()).isEqualTo("2026-11-17");
        assertThat(req.getFechaDeEmision()).isEqualTo("2026-11-17");
    }

    @Test
    @DisplayName("GRE-FECHA-4: GRE PRIVADO sin ninguna fecha → BadRequestException antes de llamar a Lucode")
    void fechaInicioDeTraslado_ambosNulos_lanzaBadRequest() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        GuiaRemision guia = guiaBase(createdAt);
        guia.setFechaTraslado(null);
        guia.getTransporte().setFechaInicioTraslado(null);

        assertThatThrownBy(() -> service().buildRequest(guia))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("fecha de inicio del traslado");
    }

    // ── Traducción de errores ─────────────────────────────────────────────────

    @Test
    @DisplayName("GRE-ERR-1: mensaje 'fecha inicio de traslado' se traduce sin exponer proveedor")
    void translateEmissionError_fechaInicio() {
        String raw = "El campo fecha inicio de traslado es requerido.";
        String friendly = LucodeGREService.translateEmissionError(raw);
        assertThat(friendly).doesNotContainIgnoringCase("lucode");
        assertThat(friendly).doesNotContainIgnoringCase("apisunat");
        assertThat(friendly).containsIgnoringCase("fecha de inicio del traslado");
    }

    @Test
    @DisplayName("GRE-ERR-2: mensaje de ubigeo partida se traduce correctamente")
    void translateEmissionError_ubigeoPartida() {
        String friendly = LucodeGREService.translateEmissionError("ubigeo de partida inválido");
        assertThat(friendly).doesNotContainIgnoringCase("lucode");
        assertThat(friendly).containsIgnoringCase("ubigeo de partida");
        assertThat(friendly).containsIgnoringCase("6 dígitos");
    }

    @Test
    @DisplayName("GRE-ERR-3: mensaje de ubigeo llegada se traduce correctamente")
    void translateEmissionError_ubigeoLlegada() {
        String friendly = LucodeGREService.translateEmissionError("El campo ubigeo llegada es requerido");
        assertThat(friendly).doesNotContainIgnoringCase("lucode");
        assertThat(friendly).containsIgnoringCase("ubigeo de llegada");
        assertThat(friendly).containsIgnoringCase("6 dígitos");
    }

    @Test
    @DisplayName("GRE-ERR-4: mensaje desconocido devuelve mensaje genérico sin exponer proveedor")
    void translateEmissionError_desconocido() {
        String friendly = LucodeGREService.translateEmissionError("algún error interno del sistema tributario");
        assertThat(friendly).doesNotContainIgnoringCase("lucode");
        assertThat(friendly).doesNotContainIgnoringCase("apisunat");
        assertThat(friendly).containsIgnoringCase("revisa los datos");
    }

    @Test
    @DisplayName("GRE-ERR-5: mensaje null devuelve mensaje genérico")
    void translateEmissionError_null() {
        String friendly = LucodeGREService.translateEmissionError(null);
        assertThat(friendly).doesNotContainIgnoringCase("lucode");
        assertThat(friendly).isNotBlank();
    }

    @Test
    @DisplayName("GRE-ERR-6: mensaje de placa/vehículo se traduce correctamente")
    void translateEmissionError_placa() {
        String friendly = LucodeGREService.translateEmissionError("El campo vehiculo placa es requerido");
        assertThat(friendly).doesNotContainIgnoringCase("lucode");
        assertThat(friendly).containsIgnoringCase("placa");
    }
}
