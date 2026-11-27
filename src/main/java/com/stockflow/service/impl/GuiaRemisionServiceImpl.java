package com.stockflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.gre.*;
import com.stockflow.dto.gre.lucode.LucodeGREResponse;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.entity.ProductoVariante;
import com.stockflow.entity.ProductoVarianteStockSucursal;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Tenant;
import com.stockflow.entity.gre.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.entity.ProductoPresentacion;
import com.stockflow.entity.Venta;
import com.stockflow.repository.*;
import com.stockflow.service.GuiaRemisionService;
import com.stockflow.repository.ProductoStockSucursalRepository;
import com.stockflow.repository.UbigeoRepository;
import com.stockflow.service.MovimientoInventarioService;
import com.stockflow.service.StockLoteService;
import com.stockflow.util.TenantContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuiaRemisionServiceImpl implements GuiaRemisionService {

    private final GuiaRemisionRepository           guiaRepo;
    private final GuiaRemisionEventoRepository     eventoRepo;
    private final SucursalRepository               sucursalRepo;
    private final ProductoRepository               productoRepo;
    private final TenantRepository                 tenantRepo;
    private final VentaRepository                  ventaRepo;
    private final ProductoPresentacionRepository   presentacionRepo;
    private final MovimientoInventarioService      movimientoService;
    private final MovimientoInventarioRepository   movimientoRepo;
    private final LucodeGREService                 lucodeGREService;
    private final GuiaRemisionPdfService           pdfService;
    private final ObjectMapper                     objectMapper;
    private final StockLoteService                 stockLoteService;
    private final UbigeoRepository                 ubigeoRepository;
    private final ProductoStockSucursalRepository              stockSucursalRepo;
    private final ProductoVarianteRepository                   varianteRepo;
    private final ProductoVarianteStockSucursalRepository      varianteSucursalRepo;
    private final GreSerieContadorService                      serieContadorService;

    // ── CREAR BORRADOR ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GuiaRemisionResponse crearBorrador(CrearGuiaRemisionRequest req) {
        String tenantId = TenantContext.getCurrentTenant();
        Long   userId   = TenantContext.getCurrentUserId();

        // Regla 1: idempotencia
        String iKey = req.getIdempotencyKey() != null
                ? req.getIdempotencyKey()
                : UUID.randomUUID().toString();
        if (guiaRepo.existsByIdempotencyKey(iKey)) {
            return toResponse(guiaRepo.findByIdempotencyKey(iKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Guía no encontrada: " + iKey)));
        }

        Tenant tenant = tenantRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado: " + tenantId));

        // Correlativo de solo-avance usando contador atómico (F-22).
        // REQUIRES_NEW asegura que el incremento se persiste incluso si esta
        // transacción hace rollback, evitando reutilización de números ya enviados a Lucode.
        String serie       = tenant.getSerieGuia() != null ? tenant.getSerieGuia() : "T001";
        int    maxActual   = guiaRepo.findMaxCorrelativo(tenantId, serie); // para inicialización
        int    correlativo = serieContadorService.incrementarYObtener(tenantId, serie, maxActual);

        // F-28: validación temprana para SUCURSALES — verificar conteo antes de resolver entidades
        if (req.getTipoTraslado() == TipoTrasladoGRE.SUCURSALES) {
            long totalSucursales = sucursalRepo.countByTenantIdAndActivoTrue(tenantId);
            if (totalSucursales < 2) {
                throw new BadRequestException(
                        "El traslado entre sucursales requiere al menos dos sucursales activas. " +
                        "Este negocio tiene " + totalSucursales + " sucursal(es) activa(s).");
            }
        }

        // Sucursales (pueden ser null para DELIVERY sin destino sucursal)
        Sucursal origen  = resolveOrigenSucursal(req, tenantId);
        Sucursal destino = resolveDestinoSucursal(req, tenantId);

        // Regla 3: SUCURSALES requiere origen y destino distintos
        if (req.getTipoTraslado() == TipoTrasladoGRE.SUCURSALES) {
            if (origen == null || destino == null) {
                throw new BadRequestException(
                        "El traslado entre sucursales requiere sucursalOrigenId y sucursalDestinoId.");
            }
            if (origen.getId().equals(destino.getId())) {
                throw new BadRequestException(
                        "El origen y destino deben ser sucursales distintas.");
            }
        }

        // Venta asociada (solo para DELIVERY — opcional)
        Venta venta = null;
        if (req.getVentaId() != null) {
            venta = ventaRepo.findById(req.getVentaId())
                    .filter(v -> tenantId.equals(v.getTenantId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada: " + req.getVentaId()));
            if ("ANULADA".equals(venta.getEstado())) {
                throw new BadRequestException("No se puede vincular una venta anulada a una guía de remisión.");
            }
        }

        // F-18: solo motivos 01 (Venta) y 04 (Traslado entre establecimientos) están soportados
        validarMotivoSoportado(req.getMotivoTrasladoCodigo());
        String motivoFinal = (req.getMotivoTrasladoCodigo() == null || req.getMotivoTrasladoCodigo().isBlank())
                ? (req.getTipoTraslado() == TipoTrasladoGRE.SUCURSALES ? "04" : "01")
                : req.getMotivoTrasladoCodigo();

        // Regla F-09/F-10: DELIVERY independiente (sin venta) requiere sucursal o punto de despacho
        validarOrigenDelivery(req.getTipoTraslado(), venta, origen, tenantId, tenant);

        // F-17: validar formato del documento del destinatario (DELIVERY)
        if (req.getTipoTraslado() == TipoTrasladoGRE.DELIVERY) {
            validarDocDestinatario(req.getDestinatarioTipoDoc(), req.getDestinatarioNumDoc());
        }

        // Construir entidad
        GuiaRemision guia = GuiaRemision.builder()
                .tenantId(tenantId)
                .serie(serie)
                .correlativo(correlativo)
                .tipoTraslado(req.getTipoTraslado())
                .motivoTrasladoCodigo(motivoFinal)
                .motivoTrasladoDescripcion(req.getMotivoTrasladoDescripcion())
                .modalidadTransporte(req.getModalidadTransporte())
                .fechaTraslado(req.getFechaTraslado())
                .pesoBrutoTotal(req.getPesoBrutoTotal())
                .unidadMedidaPeso(req.getUnidadMedidaPeso() != null ? req.getUnidadMedidaPeso() : "KGM")
                .numeroBultos(req.getNumeroBultos())
                .observaciones(req.getObservaciones())
                .sucursalOrigen(origen)
                .sucursalDestino(destino)
                .destinatarioTipoDoc(req.getDestinatarioTipoDoc())
                .destinatarioNumDoc(req.getDestinatarioNumDoc())
                .destinatarioRazonSocial(req.getDestinatarioRazonSocial())
                .destinatarioDireccion(req.getDestinatarioDireccion())
                .venta(venta)
                .idempotencyKey(iKey)
                .createdBy(userId != null ? userId.toString() : null)
                .estadoTributario(EstadoTributarioGRE.BORRADOR)
                .estadoLogistico(EstadoLogisticoGRE.PENDIENTE)
                .build();

        guia = guiaRepo.save(guia);

        // Items
        for (GuiaRemisionItemDTO itemDTO : req.getItems()) {
            Producto producto = productoRepo.findById(itemDTO.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado id=" + itemDTO.getProductoId()));
            if (!tenantId.equals(producto.getTenantId())) {
                throw new BadRequestException("El producto id=" + itemDTO.getProductoId() + " no pertenece a este tenant.");
            }
            String unidadMedidaSunat = itemDTO.getUnidadMedida() != null ? itemDTO.getUnidadMedida() : "NIU";
            if (itemDTO.getPresentacionId() != null && "NIU".equals(unidadMedidaSunat)) {
                unidadMedidaSunat = presentacionRepo
                        .findByIdInWithUnidadMedida(java.util.List.of(itemDTO.getPresentacionId()))
                        .stream().findFirst()
                        .filter(p -> p.getUnidadMedida() != null)
                        .map(p -> abreviaturaToCodigoSunat(p.getUnidadMedida().getAbreviatura()))
                        .orElse("NIU");
            }
            GuiaRemisionItem item = GuiaRemisionItem.builder()
                    .guiaRemision(guia)
                    .producto(producto)
                    .descripcion(itemDTO.getDescripcion() != null
                            ? itemDTO.getDescripcion() : producto.getNombre())
                    .cantidad(itemDTO.getCantidad())
                    .unidadMedida(unidadMedidaSunat)
                    .codigoProducto(producto.getCodigoBarras())
                    .presentacionId(itemDTO.getPresentacionId())
                    .varianteId(itemDTO.getVarianteId())
                    .tenantId(tenantId)
                    .build();
            guia.getItems().add(item);
        }

        // Transporte
        if (req.getTransporte() != null) {
            TransporteDTO td = req.getTransporte();
            if (td.getUbigeoPartida() != null)
                validarUbigeo(td.getUbigeoPartida(), "ubigeo de partida");
            if (td.getUbigeoLlegada() != null)
                validarUbigeo(td.getUbigeoLlegada(), "ubigeo de llegada");
            GuiaRemisionTransporte transporte = GuiaRemisionTransporte.builder()
                    .guiaRemision(guia)
                    .tenantId(tenantId)
                    .direccionPartida(td.getDireccionPartida())
                    .ubigeoPartida(td.getUbigeoPartida())
                    .direccionLlegada(td.getDireccionLlegada())
                    .ubigeoLlegada(td.getUbigeoLlegada())
                    .vehiculoPlaca(td.getVehiculoPlaca())
                    .conductorTipoDoc(td.getConductorTipoDoc())
                    .conductorNumDoc(td.getConductorNumDoc())
                    .conductorNombres(td.getConductorNombres())
                    .conductorApellidos(td.getConductorApellidos())
                    .conductorLicencia(td.getConductorLicencia())
                    .fechaInicioTraslado(td.getFechaInicioTraslado())
                    .transportistaRuc(td.getTransportistaRuc())
                    .transportistaDenominacion(td.getTransportistaDenominacion())
                    .transportistaNroRegistroMtc(td.getTransportistaNroRegistroMtc())
                    .transportistaNroAutorizacion(td.getTransportistaNroAutorizacion())
                    .transportistaCodigoEntidadAutorizadora(td.getTransportistaCodigoEntidadAutorizadora())
                    .fechaEntregaTransportista(td.getFechaEntregaTransportista())
                    .build();
            guia.setTransporte(transporte);
        }

        guia = guiaRepo.save(guia);
        registrarEvento(guia, TipoEventoGRE.CREADA, "Borrador creado", userId);
        log.info("📋 GRE borrador creado: {} para tenant {}", guia.getNumeroCompleto(), tenantId);
        return toResponse(guia);
    }

    // ── EDITAR BORRADOR ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public GuiaRemisionResponse editarBorrador(Long id, EditarBorradorRequest req) {
        String tenantId = TenantContext.getCurrentTenant();
        Long   userId   = TenantContext.getCurrentUserId();

        GuiaRemision guia = loadGuia(id, tenantId);

        if (guia.getEstadoTributario() != EstadoTributarioGRE.BORRADOR
                && guia.getEstadoTributario() != EstadoTributarioGRE.ERROR) {
            throw new BadRequestException(
                    "Solo se puede editar una guía en estado BORRADOR o ERROR. Estado actual: "
                    + guia.getEstadoTributario());
        }

        // Actualizar campos escalares
        if (req.getMotivoTrasladoCodigo() != null)
            guia.setMotivoTrasladoCodigo(req.getMotivoTrasladoCodigo());
        guia.setMotivoTrasladoDescripcion(req.getMotivoTrasladoDescripcion());
        guia.setModalidadTransporte(req.getModalidadTransporte());
        guia.setFechaTraslado(req.getFechaTraslado());
        guia.setPesoBrutoTotal(req.getPesoBrutoTotal());
        if (req.getUnidadMedidaPeso() != null)
            guia.setUnidadMedidaPeso(req.getUnidadMedidaPeso());
        guia.setNumeroBultos(req.getNumeroBultos());
        guia.setObservaciones(req.getObservaciones());
        guia.setDestinatarioTipoDoc(req.getDestinatarioTipoDoc());
        guia.setDestinatarioNumDoc(req.getDestinatarioNumDoc());
        guia.setDestinatarioRazonSocial(req.getDestinatarioRazonSocial());
        guia.setDestinatarioDireccion(req.getDestinatarioDireccion());

        // Actualizar sucursal origen si se provee (permite a DELIVERY independiente asignar origen)
        if (req.getSucursalOrigenId() != null) {
            Sucursal nuevoOrigen = sucursalRepo.findByIdAndTenantId(req.getSucursalOrigenId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Sucursal no encontrada id=" + req.getSucursalOrigenId()));
            guia.setSucursalOrigen(nuevoOrigen);
        }

        // Regla F-09/F-10: DELIVERY independiente (sin venta) requiere sucursal o punto de despacho
        Tenant tenant = tenantRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado: " + tenantId));
        validarOrigenDelivery(guia.getTipoTraslado(), guia.getVenta(), guia.getSucursalOrigen(), tenantId, tenant);

        // Reemplazar items (orphanRemoval=true elimina los anteriores)
        guia.getItems().clear();
        for (GuiaRemisionItemDTO itemDTO : req.getItems()) {
            Producto producto = productoRepo.findById(itemDTO.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado id=" + itemDTO.getProductoId()));
            if (!tenantId.equals(producto.getTenantId())) {
                throw new BadRequestException(
                        "El producto id=" + itemDTO.getProductoId() + " no pertenece a este tenant.");
            }
            String unidadMedidaSunat = itemDTO.getUnidadMedida() != null ? itemDTO.getUnidadMedida() : "NIU";
            if (itemDTO.getPresentacionId() != null && "NIU".equals(unidadMedidaSunat)) {
                unidadMedidaSunat = presentacionRepo
                        .findByIdInWithUnidadMedida(java.util.List.of(itemDTO.getPresentacionId()))
                        .stream().findFirst()
                        .filter(p -> p.getUnidadMedida() != null)
                        .map(p -> abreviaturaToCodigoSunat(p.getUnidadMedida().getAbreviatura()))
                        .orElse("NIU");
            }
            GuiaRemisionItem item = GuiaRemisionItem.builder()
                    .guiaRemision(guia)
                    .producto(producto)
                    .descripcion(itemDTO.getDescripcion() != null
                            ? itemDTO.getDescripcion() : producto.getNombre())
                    .cantidad(itemDTO.getCantidad())
                    .unidadMedida(unidadMedidaSunat)
                    .codigoProducto(producto.getCodigoBarras())
                    .presentacionId(itemDTO.getPresentacionId())
                    .varianteId(itemDTO.getVarianteId())
                    .tenantId(tenantId)
                    .build();
            guia.getItems().add(item);
        }

        // Actualizar transporte in-place para evitar 409 por UNIQUE(guia_remision_id).
        // Crear instancia nueva + null previo causa INSERT antes de DELETE en el mismo flush.
        if (req.getTransporte() != null) {
            TransporteDTO td = req.getTransporte();
            if (td.getUbigeoPartida() != null)
                validarUbigeo(td.getUbigeoPartida(), "ubigeo de partida");
            if (td.getUbigeoLlegada() != null)
                validarUbigeo(td.getUbigeoLlegada(), "ubigeo de llegada");
            GuiaRemisionTransporte transporte = guia.getTransporte();
            if (transporte == null) {
                transporte = new GuiaRemisionTransporte();
                transporte.setGuiaRemision(guia);
                transporte.setTenantId(tenantId);
                guia.setTransporte(transporte);
            }
            transporte.setDireccionPartida(td.getDireccionPartida());
            transporte.setUbigeoPartida(td.getUbigeoPartida());
            transporte.setDireccionLlegada(td.getDireccionLlegada());
            transporte.setUbigeoLlegada(td.getUbigeoLlegada());
            transporte.setVehiculoPlaca(td.getVehiculoPlaca());
            transporte.setConductorTipoDoc(td.getConductorTipoDoc());
            transporte.setConductorNumDoc(td.getConductorNumDoc());
            transporte.setConductorNombres(td.getConductorNombres());
            transporte.setConductorApellidos(td.getConductorApellidos());
            transporte.setConductorLicencia(td.getConductorLicencia());
            transporte.setFechaInicioTraslado(td.getFechaInicioTraslado());
            transporte.setTransportistaRuc(td.getTransportistaRuc());
            transporte.setTransportistaDenominacion(td.getTransportistaDenominacion());
            transporte.setTransportistaNroRegistroMtc(td.getTransportistaNroRegistroMtc());
            transporte.setTransportistaNroAutorizacion(td.getTransportistaNroAutorizacion());
            transporte.setTransportistaCodigoEntidadAutorizadora(td.getTransportistaCodigoEntidadAutorizadora());
            transporte.setFechaEntregaTransportista(td.getFechaEntregaTransportista());
        } else if (guia.getTransporte() != null) {
            guia.setTransporte(null);
        }

        guia = guiaRepo.save(guia);
        registrarEvento(guia, TipoEventoGRE.CREADA, "Borrador editado", userId);
        log.info("✏️ GRE borrador editado: {} tenant={}", guia.getNumeroCompleto(), tenantId);
        return toResponse(guia);
    }

    // ── VALIDACIÓN DOCUMENTO DESTINATARIO ────────────────────────────────────

    /**
     * Valida tipo y número de documento del destinatario (catálogo 06 SUNAT).
     * Solo se ejecuta cuando tipoDoc es distinto de null/blank/'-'.
     * numDoc vacío se acepta (el campo es opcional en la GRE).
     */
    private static final java.util.Set<String> MOTIVOS_SOPORTADOS = java.util.Set.of("01", "04");

    private void validarMotivoSoportado(String motivo) {
        if (motivo == null || motivo.isBlank()) return; // se asigna por defecto
        if (!MOTIVOS_SOPORTADOS.contains(motivo)) {
            throw new BadRequestException(
                "El motivo de traslado '" + motivo + "' no está soportado en esta versión. " +
                "Use '01' (Venta) para DELIVERY o '04' (Traslado entre establecimientos) para SUCURSALES.");
        }
    }

    private void validarDocDestinatario(String tipoDoc, String numDoc) {
        if (tipoDoc == null || tipoDoc.isBlank() || "-".equals(tipoDoc)) return;
        if (numDoc == null || numDoc.isBlank()) return;
        String v = numDoc.trim();
        switch (tipoDoc) {
            case "1" -> { // DNI
                if (!v.matches("\\d{8}"))
                    throw new BadRequestException(
                            "El DNI del destinatario debe tener exactamente 8 dígitos numéricos.");
            }
            case "6" -> { // RUC — F-18.1: solo valida formato, no dígito verificador
                if (!v.matches("\\d{11}"))
                    throw new BadRequestException(
                            "El RUC del destinatario debe tener exactamente 11 dígitos numéricos.");
            }
            default -> { // Carnet ext. '4', Pasaporte '7', u otros
                if (v.length() < 1 || v.length() > 20)
                    throw new BadRequestException(
                            "El número de documento del destinatario debe tener entre 1 y 20 caracteres.");
            }
        }
    }


    // ── VALIDACIÓN UBIGEO ─────────────────────────────────────────────────────

    private void validarUbigeo(String codigo, String label) {
        if (codigo == null || codigo.length() != 6) {
            throw new BadRequestException(
                    "El " + label + " debe tener exactamente 6 dígitos.");
        }
        if (!ubigeoRepository.existsById(codigo)) {
            throw new BadRequestException(
                    "El " + label + " '" + codigo + "' no existe en el catálogo oficial SUNAT/INEI.");
        }
    }

    // ── ELIMINAR BORRADOR ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public void eliminarBorrador(Long id) {
        String tenantId = TenantContext.getCurrentTenant();

        GuiaRemision guia = loadGuia(id, tenantId);

        if (guia.getEstadoTributario() != EstadoTributarioGRE.BORRADOR
                && guia.getEstadoTributario() != EstadoTributarioGRE.ERROR) {
            throw new BadRequestException(
                    "Solo se puede eliminar una guía en estado BORRADOR o ERROR. Estado actual: "
                    + guia.getEstadoTributario());
        }

        // F-22: protección de documentos enviados a Lucode.
        // Si alguna vez se inició la emisión (evento EMISION_INICIADA), el correlativo
        // ya fue reservado en el OSE y no debe eliminarse físicamente para preservar
        // trazabilidad y evitar que el correlativo sea reutilizado en el contador.
        boolean fueEnviada = eventoRepo.existsByGuiaRemisionIdAndTipoEvento(
                id, TipoEventoGRE.EMISION_INICIADA);
        if (fueEnviada) {
            throw new BadRequestException(
                    "La guía " + guia.getNumeroCompleto() + " fue enviada al servicio tributario " +
                    "(correlativo " + guia.getNumeroCompleto() + " ya reservado). " +
                    "No puede eliminarse para preservar la trazabilidad del documento. " +
                    "Si la emisión fue rechazada, el documento permanece en estado ERROR como registro auditado.");
        }

        guiaRepo.delete(guia);
        log.info("🗑️ GRE borrador eliminado id={} tenant={}", id, tenantId);
    }

    // ── EMITIR GRE ────────────────────────────────────────────────────────────

    @Override
    @Transactional(dontRollbackOn = RuntimeException.class)
    public GuiaRemisionResponse emitirGRE(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        Long   userId   = TenantContext.getCurrentUserId();

        // Lock pesimista: bloquea la fila en DB durante toda la transacción
        // para evitar que dos requests concurrentes emitan la misma guía
        GuiaRemision guia = guiaRepo.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Guía de remisión no encontrada id=" + id));

        // Regla 4: solo BORRADOR puede emitirse
        if (guia.getEstadoTributario() == EstadoTributarioGRE.EMITIDA) {
            return toResponse(guia);  // idempotente
        }
        if (guia.getEstadoTributario() == EstadoTributarioGRE.EMITIENDO) {
            throw new BadRequestException("La guía ya está en proceso de emisión. Espere un momento.");
        }
        if (guia.getEstadoTributario() != EstadoTributarioGRE.BORRADOR
                && guia.getEstadoTributario() != EstadoTributarioGRE.ERROR) {
            throw new BadRequestException("Solo se puede emitir una guía en estado BORRADOR o ERROR.");
        }

        Tenant tenant = tenantRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado: " + tenantId));

        // Regla F-09/F-10: DELIVERY independiente debe tener sucursal o punto de despacho antes de emitir.
        // Aplica también a borradores creados antes de estas validaciones.
        validarOrigenDelivery(guia.getTipoTraslado(), guia.getVenta(), guia.getSucursalOrigen(), tenantId, tenant);

        // F-21.1: verificar OSE habilitado ANTES de cambiar el estado a EMITIENDO.
        // Si el token no existe el GRE conserva BORRADOR; no se genera evento de error ni
        // se clasifica la condición como fallo de Lucode.
        if (tenant.getOseToken() == null || tenant.getOseToken().isBlank()) {
            throw new BadRequestException(
                    "La facturación electrónica no está habilitada para este negocio. " +
                    "Actívala desde el panel de configuración antes de emitir guías de remisión.");
        }

        // Regla 5a: validar stock ANTES de marcar EMITIENDO para no contaminar el estado
        // si el inventario es insuficiente.  Lanza BadRequestException sin efectos secundarios.
        validarStockParaEmision(guia);

        // Regla 5b: marcar EMITIENDO antes de llamar a Lucode (previene doble emisión concurrente)
        guia.setEstadoTributario(EstadoTributarioGRE.EMITIENDO);
        guiaRepo.saveAndFlush(guia);  // flush inmediato para bloquear concurrentes en DB
        registrarEvento(guia, TipoEventoGRE.EMISION_INICIADA, "Emisión iniciada", userId);

        // NOTA: NO se registra movimiento de inventario aquí.
        // La SALIDA de inventario ocurre SOLO después de confirmación exitosa de Lucode.
        // Esto garantiza que un rechazo o timeout de Lucode no deja stock inconsistente.

        try {
            var lucodeResp = lucodeGREService.emitirGRE(guia, tenant.getOseToken());

            if (!lucodeResp.isSuccess()) {
                // Lucode rechazó explícitamente — no hubo emisión, no se descuenta stock
                guia.setEstadoTributario(EstadoTributarioGRE.ERROR);
                guia.setLucodeErrorRaw(lucodeResp.getMessage());
                guiaRepo.save(guia);
                String rawMsg = lucodeResp.getMessage();
                log.error("Emisión GRE {} rechazada por servicio tributario: {}", guia.getNumeroCompleto(), rawMsg);
                registrarEvento(guia, TipoEventoGRE.ERROR_LUCODE,
                        "Servicio tributario rechazó: " + rawMsg, userId);
                throw new BadRequestException(LucodeGREService.translateEmissionError(rawMsg));
            }

            // Lucode confirmó éxito — ahora sí registrar SALIDA de inventario
            // Caso A (con sucursal) y Caso B (legacy sin sucursal): ambos descontan stock
            // Caso D (con venta): registrarSalidaInventario lo omite internamente
            if (guia.getVenta() == null) {
                registrarSalidaInventario(guia, tenantId, userId);
            }

            // Actualizar con datos de Lucode
            var payload = lucodeResp.getPayload();
            guia.setEstadoTributario(EstadoTributarioGRE.EMITIDA);
            guia.setEstadoLogistico(EstadoLogisticoGRE.EN_TRANSITO);
            if (payload != null) {
                guia.setLucodeHash(payload.getHash());
                guia.setXmlUrl(payload.getXml());
                guia.setCdrUrl(payload.getCdr());
                if (payload.getPdf() != null && payload.getPdf().getA4() != null) {
                    guia.setPdfUrl(payload.getPdf().getA4());
                }
                try {
                    guia.setLucodeRespuestaRaw(objectMapper.writeValueAsString(payload));
                } catch (JsonProcessingException ignored) {}
            }
            guiaRepo.save(guia);
            registrarEvento(guia, TipoEventoGRE.EMITIDA,
                    "Emitida exitosamente. Hash: " + (payload != null ? payload.getHash() : "-"),
                    userId);

            log.info("✅ GRE emitida: {} tenant={}", guia.getNumeroCompleto(), tenantId);
            return toResponse(guia);

        } catch (BadRequestException e) {
            // Si la guía quedó en EMITIENDO (error de validación pre-HTTP), marcarla ERROR
            // para que el usuario pueda corregir y reintentar.
            if (guia.getEstadoTributario() == EstadoTributarioGRE.EMITIENDO) {
                guia.setEstadoTributario(EstadoTributarioGRE.ERROR);
                guia.setLucodeErrorRaw(e.getMessage());
                guiaRepo.save(guia);
                registrarEvento(guia, TipoEventoGRE.ERROR_LUCODE,
                        "Error de validación: " + e.getMessage(), userId);
            }
            throw e;
        } catch (Exception e) {
            // Timeout, error de red u otro error inesperado.
            // El estado queda EMITIENDO (o se marca ERROR aquí).
            // NO se descuenta stock — resultado de Lucode es desconocido, requiere revisión manual.
            guia.setEstadoTributario(EstadoTributarioGRE.ERROR);
            guia.setLucodeErrorRaw("TIMEOUT/ERROR - resultado desconocido: " + e.getMessage());
            guiaRepo.save(guia);
            registrarEvento(guia, TipoEventoGRE.ERROR_LUCODE,
                    "Timeout o error de red — requiere verificación manual: " + e.getMessage(), userId);
            throw new RuntimeException("Error emitiendo GRE: " + e.getMessage(), e);
        }
    }

    // ── CONFIRMAR RECEPCIÓN ───────────────────────────────────────────────────

    @Override
    @Transactional
    public GuiaRemisionResponse confirmarRecepcion(Long id, ConfirmarRecepcionRequest req) {
        String tenantId = TenantContext.getCurrentTenant();
        Long   userId   = TenantContext.getCurrentUserId();

        GuiaRemision guia = loadGuia(id, tenantId);

        if (guia.getEstadoLogistico() == EstadoLogisticoGRE.RECIBIDA) {
            return toResponse(guia);  // idempotente
        }
        if (guia.getEstadoTributario() != EstadoTributarioGRE.EMITIDA) {
            throw new BadRequestException("Solo se puede confirmar recepción de guías emitidas.");
        }
        if (guia.getEstadoLogistico() != EstadoLogisticoGRE.EN_TRANSITO) {
            throw new BadRequestException("La guía no está en estado EN_TRANSITO.");
        }

        // Regla 7: registrar ENTRADA en sucursal destino solo al confirmar
        if (guia.getSucursalDestino() != null) {
            registrarEntradaInventario(guia, tenantId, userId);
        }

        guia.setEstadoLogistico(EstadoLogisticoGRE.RECIBIDA);
        if (req.getObservaciones() != null && !req.getObservaciones().isBlank()) {
            guia.setObservaciones((guia.getObservaciones() != null
                    ? guia.getObservaciones() + " | Recepción: "
                    : "Recepción: ") + req.getObservaciones());
        }
        guiaRepo.save(guia);
        registrarEvento(guia, TipoEventoGRE.RECEPCION_CONFIRMADA,
                "Recepción confirmada" + (req.getObservaciones() != null
                        ? ": " + req.getObservaciones() : ""),
                userId);

        log.info("📦 GRE recepción confirmada: {} tenant={}", guia.getNumeroCompleto(), tenantId);
        return toResponse(guia);
    }

    // ── CANCELAR BORRADOR ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public GuiaRemisionResponse cancelarBorrador(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        Long   userId   = TenantContext.getCurrentUserId();

        GuiaRemision guia = loadGuia(id, tenantId);

        // Solo se puede cancelar BORRADOR o ERROR — no EMITIDA (requeriría anulación SUNAT)
        if (guia.getEstadoTributario() != EstadoTributarioGRE.BORRADOR
                && guia.getEstadoTributario() != EstadoTributarioGRE.ERROR) {
            throw new BadRequestException(
                    "Solo se puede cancelar una guía en estado BORRADOR o ERROR. " +
                    "Para anular una guía emitida, contacta soporte.");
        }

        guia.setEstadoTributario(EstadoTributarioGRE.CANCELADA);
        guia.setEstadoLogistico(EstadoLogisticoGRE.CANCELADA);
        guiaRepo.save(guia);
        registrarEvento(guia, TipoEventoGRE.CANCELADA, "Borrador cancelado", userId);

        log.info("🗑️ GRE borrador cancelado: {} tenant={}", guia.getNumeroCompleto(), tenantId);
        return toResponse(guia);
    }

    // ── LISTAR ────────────────────────────────────────────────────────────────

    @Override
    public Page<GuiaRemisionListResponse> listar(String q, EstadoTributarioGRE estado, Long sucursalId, Pageable pageable) {
        String tenantId = TenantContext.getCurrentTenant();
        String qTrimmed = (q != null && !q.isBlank()) ? q.trim() : null;
        return guiaRepo.findByTenantIdWithSearchAndSucursal(tenantId, sucursalId, qTrimmed, estado, pageable)
                .map(this::toListResponse);
    }

    @Override
    public GuiaRemisionStatsResponse stats(Long sucursalId) {
        String tenantId = TenantContext.getCurrentTenant();
        java.util.List<Object[]> rows = guiaRepo.countByEstadoTributarioForTenantAndSucursal(tenantId, sucursalId);
        long total = 0, emitidas = 0, pendientes = 0, conError = 0;
        for (Object[] row : rows) {
            EstadoTributarioGRE est = (EstadoTributarioGRE) row[0];
            long cnt = (long) row[1];
            total += cnt;
            switch (est) {
                case EMITIDA   -> emitidas   += cnt;
                case BORRADOR, EMITIENDO -> pendientes += cnt;
                case ERROR     -> conError   += cnt;
                default -> {}
            }
        }
        return GuiaRemisionStatsResponse.builder()
                .total(total).emitidas(emitidas).pendientes(pendientes).conError(conError)
                .build();
    }

    // ── OBTENER ───────────────────────────────────────────────────────────────

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public GuiaRemisionResponse obtener(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        return toResponse(loadGuia(id, tenantId));
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    @Override
    public byte[] generarPDF(Long id) {
        return pdfService.generar(id);
    }

    // ── RECUPERAR EMITIENDO ───────────────────────────────────────────────────

    @Override
    @Transactional
    public GuiaRemisionResponse recuperarEmitiendo(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        Long   userId   = TenantContext.getCurrentUserId();

        GuiaRemision guia = loadGuia(id, tenantId);

        if (guia.getEstadoTributario() != EstadoTributarioGRE.EMITIENDO) {
            throw new BadRequestException(
                    "Solo se puede recuperar una guía atascada en estado EMITIENDO. " +
                    "Estado actual: " + guia.getEstadoTributario());
        }

        // Verificar que Lucode nunca confirmó la emisión (triple garantía)
        if (guia.getLucodeHash() != null || guia.getXmlUrl() != null || guia.getLucodeId() != null) {
            throw new BadRequestException(
                    "La guía tiene datos de emisión registrados (hash/xml/id). " +
                    "No se puede recuperar automáticamente — contacta soporte.");
        }

        guia.setEstadoTributario(EstadoTributarioGRE.ERROR);
        guia.setLucodeErrorRaw("Recuperación manual: proceso interrumpido antes de respuesta de servicio tributario.");
        guiaRepo.save(guia);

        registrarEvento(guia, TipoEventoGRE.RECUPERACION_INTERRUPCION,
                "Guía recuperada de estado EMITIENDO: proceso interrumpido sin confirmación del servicio tributario.",
                userId);

        log.info("🔁 GRE recuperada de EMITIENDO a ERROR: {} tenant={}", guia.getNumeroCompleto(), tenantId);
        return toResponse(guia);
    }

    // ── RECONCILIACIÓN F-08 ───────────────────────────────────────────────────

    /**
     * Reconcilia una GRE que quedó en estado ERROR local pero fue aceptada por SUNAT/Lucode.
     * Escenario: bug F-08 (UnrecognizedPropertyException en LucodeGREPayload.pdf).
     *
     * Precondiciones verificadas en orden:
     *  1. La guía existe y pertenece al tenant del token.
     *  2. estadoTributario == ERROR (no volver a aplicar si ya es EMITIDA).
     *  3. No existen movimientos de SALIDA previos con referencia="GRE-{id}" (anti-duplicado).
     *
     * Efectos (en una sola transacción):
     *  - Registra los MovimientoInventario de SALIDA (igual que registrarSalidaInventario).
     *  - Actualiza estadoTributario=EMITIDA, estadoLogistico=EN_TRANSITO.
     *  - Escribe los datos tributarios (hash, xmlUrl, cdrUrl, pdfUrl) y limpia lucodeErrorRaw.
     *  - Registra evento RECONCILIACION_F08 con trazabilidad completa.
     *
     * No llama a Lucode — el caller es responsable de verificar la aceptación previamente.
     */
    @Override
    @Transactional
    public GuiaRemisionResponse reconciliarGREAceptada(Long id, ReconciliarGRERequest req) {
        String tenantId = TenantContext.getCurrentTenant();
        Long   userId   = TenantContext.getCurrentUserId();

        // Bloqueo pesimista para evitar doble-reconciliación concurrente
        GuiaRemision guia = guiaRepo.findByIdAndTenantIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Guía de remisión no encontrada id=" + id));

        // Guard: solo reconciliar si está en ERROR
        if (guia.getEstadoTributario() == EstadoTributarioGRE.EMITIDA) {
            log.info("Reconciliación F-08 omitida: GRE {} ya está EMITIDA", guia.getNumeroCompleto());
            return toResponse(guia);
        }
        if (guia.getEstadoTributario() != EstadoTributarioGRE.ERROR) {
            throw new BadRequestException(
                    "Solo se puede reconciliar una guía en estado ERROR. " +
                    "Estado actual: " + guia.getEstadoTributario());
        }

        // Guard: anti-duplicado — verificar que no existan SALIDA previas para esta GRE
        String refGRE = "GRE-" + guia.getId();
        if (movimientoRepo.existsByReferenciaAndTipoAndTenantId(refGRE, "SALIDA", tenantId)) {
            throw new BadRequestException(
                    "Ya existen movimientos de SALIDA para la guía " + guia.getNumeroCompleto() +
                    ". La reconciliación no puede ejecutarse dos veces.");
        }

        // F-22 Parte 3: verificar que el documento T001-N realmente existe en Lucode
        // y que el hash/serie/correlativo coincide con la GRE local.
        // Protege contra reconciliar un documento de otro tenant o con datos incorrectos.
        Tenant tenantParaReconciliar = tenantRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado: " + tenantId));
        if (tenantParaReconciliar.getOseToken() != null && !tenantParaReconciliar.getOseToken().isBlank()) {
            try {
                LucodeGREResponse estadoLucode = lucodeGREService.consultarEstadoGRE(
                        guia.getSerie(), guia.getCorrelativo(), tenantParaReconciliar.getOseToken());
                if (estadoLucode.getPayload() != null) {
                    String hashLucode = estadoLucode.getPayload().getHash();
                    if (hashLucode != null && !hashLucode.isBlank()
                            && !hashLucode.equalsIgnoreCase(req.getLucodeHash())) {
                        throw new BadRequestException(
                                "El hash proporcionado (" + req.getLucodeHash() + ") no coincide " +
                                "con el registrado en Lucode (" + hashLucode + ") para " +
                                guia.getNumeroCompleto() + ". Verifica los datos antes de reconciliar.");
                    }
                }
                log.info("✅ Verificación Lucode OK para reconciliación de {}", guia.getNumeroCompleto());
            } catch (BadRequestException e) {
                // Re-lanzar si es el error de hash mismatch; para errores de red loguear y continuar
                if (e.getMessage() != null && e.getMessage().contains("no coincide")) throw e;
                log.warn("Verificación Lucode no disponible para {}; procediendo con reconciliación manual: {}",
                        guia.getNumeroCompleto(), e.getMessage());
            }
        }

        // Registrar SALIDA de inventario (misma lógica que registrarSalidaInventario)
        // Caso A y B: descontar stock. Caso D (con venta): el POS ya descontó.
        if (guia.getVenta() == null) {
            registrarSalidaInventario(guia, tenantId, userId);
        }

        // Actualizar datos tributarios y estado
        guia.setEstadoTributario(EstadoTributarioGRE.EMITIDA);
        guia.setEstadoLogistico(EstadoLogisticoGRE.EN_TRANSITO);
        guia.setLucodeHash(req.getLucodeHash());
        guia.setXmlUrl(req.getXmlUrl());
        guia.setCdrUrl(req.getCdrUrl());
        if (req.getPdfA4Url() != null) {
            guia.setPdfUrl(req.getPdfA4Url());
        }
        guia.setLucodeErrorRaw(null);

        try {
            guia.setLucodeRespuestaRaw(objectMapper.writeValueAsString(req));
        } catch (JsonProcessingException ignored) {}

        guia = guiaRepo.save(guia);

        String nota = String.format(
                "Reconciliación F-08: GRE aceptada por Lucode (hash=%s). " +
                "Movimientos de inventario registrados correctamente. " +
                "Causa original: deserialización del campo pdf.a4 en LucodeGREPayload (bug corregido).",
                req.getLucodeHash());
        registrarEvento(guia, TipoEventoGRE.RECONCILIACION_F08, nota, userId);

        log.info("✅ GRE {} reconciliada F-08: {} → EMITIDA", guia.getNumeroCompleto(), tenantId);
        return toResponse(guia);
    }

    // ── CONSULTAR ESTADO EN LUCODE (F-22 Parte 4 — solo lectura) ─────────────

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public LucodeGREResponse consultarEstadoEnLucode(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        GuiaRemision guia = loadGuia(id, tenantId);
        Tenant tenant = tenantRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado: " + tenantId));

        log.info("🔍 Consultando estado en Lucode para GRE {} ({}) tenant={}",
                guia.getNumeroCompleto(), id, tenantId);

        return lucodeGREService.consultarEstadoGRE(
                guia.getSerie(), guia.getCorrelativo(), tenant.getOseToken());
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    static String abreviaturaToCodigoSunat(String abreviatura) {
        if (abreviatura == null) return "NIU";
        return switch (abreviatura.toUpperCase()) {
            case "CAJA", "CJA", "CX", "BOX" -> "BX";
            case "KG", "KGM", "KILO", "KILOGRAMO" -> "KGM";
            case "GR", "GRM", "GRAMO" -> "GRM";
            case "LT", "LTR", "LITRO", "LITER" -> "LTR";
            case "ML", "MLT", "MILILITRO" -> "MLT";
            case "MT", "MTR", "METRO" -> "MTR";
            default -> "NIU";
        };
    }

    private GuiaRemision loadGuia(Long id, String tenantId) {
        return guiaRepo.findByIdAndTenantIdWithSucursales(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Guía de remisión no encontrada id=" + id));
    }

    private Sucursal resolveOrigenSucursal(CrearGuiaRemisionRequest req, String tenantId) {
        if (req.getSucursalOrigenId() == null) return null;
        return sucursalRepo.findByIdAndTenantId(req.getSucursalOrigenId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sucursal no encontrada id=" + req.getSucursalOrigenId()));
    }

    private Sucursal resolveDestinoSucursal(CrearGuiaRemisionRequest req, String tenantId) {
        if (req.getSucursalDestinoId() == null) return null;
        return sucursalRepo.findByIdAndTenantId(req.getSucursalDestinoId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sucursal no encontrada id=" + req.getSucursalDestinoId()));
    }

    // ── VALIDACIÓN F-09/F-10: DELIVERY INDEPENDIENTE ─────────────────────────

    /**
     * Valida el origen de una GRE DELIVERY independiente (sin venta).
     *
     * Caso A — Tenant con sucursales, origen seleccionado: OK.
     * Caso A-fallido — Tiene sucursales pero no se seleccionó origen: rechaza.
     * Caso B — Sin sucursales, con ubigeo_despacho y dirección_despacho: OK (tenant legacy).
     * Caso C — Sin sucursales ni punto de despacho: rechaza con guía hacia Configuración.
     * Caso D — Con venta: OK sin verificación (POS manejó el inventario).
     */
    private void validarOrigenDelivery(TipoTrasladoGRE tipo, Venta venta, Sucursal origen,
                                       String tenantId, Tenant tenant) {
        if (tipo != TipoTrasladoGRE.DELIVERY) return;
        if (venta != null) return;    // Caso D
        if (origen != null) return;   // Caso A con sucursal seleccionada

        long totalSucursales = sucursalRepo.countByTenantIdAndActivoTrue(tenantId);
        if (totalSucursales > 0) {
            // Caso A-fallido: el tenant usa sucursales pero no indicó una para este despacho
            throw new BadRequestException(
                    "Una guía DELIVERY sin venta asociada requiere una sucursal de origen. " +
                    "Indica desde qué sucursal se realizará el despacho.");
        }

        // Sin sucursales: verificar punto de despacho del tenant (Caso B vs Caso C)
        if (tenant.getUbigeoDespacho() != null && !tenant.getUbigeoDespacho().isBlank()
                && tenant.getDireccionDespacho() != null && !tenant.getDireccionDespacho().isBlank()) {
            return; // Caso B: punto de despacho legacy configurado
        }

        // Caso C: sin sucursales ni punto de despacho
        throw new BadRequestException(
                "Para emitir guías DELIVERY sin sucursales, debes configurar el punto de despacho " +
                "en Configuración → Guías de Remisión (dirección y ubigeo de despacho).");
    }

    // ── VALIDACIÓN DE STOCK ───────────────────────────────────────────────────

    /**
     * Verifica que haya stock suficiente para todos los ítems antes de llamar a Lucode.
     * - Caso D (con venta): omite — el POS ya validó stock al crear la venta.
     * - Caso A (con sucursal): valida stock de esa sucursal.
     * - Caso B (sin sucursal, legacy): valida stock general del tenant (sucursalId=null).
     */
    private void validarStockParaEmision(GuiaRemision guia) {
        if (guia.getVenta() != null) return; // Caso D: POS ya validó y descontó

        String tenantId   = guia.getTenantId();
        Long   sucursalId = guia.getSucursalOrigen() != null ? guia.getSucursalOrigen().getId() : null;

        for (GuiaRemisionItem item : guia.getItems()) {
            Producto producto = item.getProducto();

            if (producto == null || !tenantId.equals(producto.getTenantId())) {
                throw new BadRequestException(
                        "El producto del ítem no pertenece a este tenant.");
            }

            BigDecimal cantidad = item.getCantidad();
            if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException(
                        "La cantidad de cada ítem debe ser mayor a cero.");
            }

            int factor = getFactorPresentacion(item, tenantId);
            int cantidadBase = cantidad.intValue() * factor;

            // F-23: si el ítem tiene variante, validar contra PVSS (más específico que PSS del producto)
            if (item.getVarianteId() != null) {
                int stockVariante;
                if (sucursalId != null) {
                    stockVariante = varianteSucursalRepo
                            .findByVarianteIdAndSucursalId(item.getVarianteId(), sucursalId)
                            .map(pvss -> pvss.getStockActual() != null ? pvss.getStockActual() : 0)
                            .orElse(0);
                } else {
                    stockVariante = varianteRepo.findByIdAndTenantId(item.getVarianteId(), tenantId)
                            .map(v -> v.getStockActual() != null ? v.getStockActual() : 0)
                            .orElse(0);
                }
                if (cantidadBase > stockVariante) {
                    throw new BadRequestException(String.format(
                            "Stock insuficiente para variante ID %d de '%s': disponible=%d, requerido=%d.",
                            item.getVarianteId(), producto.getNombre(), stockVariante, cantidadBase));
                }
                continue; // validación de variante satisfactoria; omitir validación PSS
            }

            Integer stockLote = stockLoteService.getStockVigente(tenantId, producto.getId(), sucursalId);
            int stockDisponible;
            if (stockLote != null) {
                stockDisponible = stockLote;
            } else if (sucursalId != null && guia.getTipoTraslado() == TipoTrasladoGRE.SUCURSALES) {
                // Suma de PSS en todas las sucursales: si > 0, el stock está distribuido por rama.
                // Si = 0, los PSS fueron inicializados en cero → usar stock global.
                int totalPssStock = stockSucursalRepo.sumStockByProductoIdAndTenantId(
                        producto.getId(), tenantId);
                if (totalPssStock > 0) {
                    // Stock real distribuido entre sucursales → usar PSS de la sucursal origen (con lock)
                    var pss = stockSucursalRepo.findByProductoIdAndSucursalIdForUpdate(
                            producto.getId(), sucursalId);
                    stockDisponible = pss.map(p -> p.getStockActual() != null ? p.getStockActual() : 0)
                                         .orElse(0);
                    if (pss.isEmpty()) {
                        log.warn("⚠️ Sin PSS para producto={} sucursal={} (stock asignado a otra sucursal) — stock=0",
                                producto.getId(), sucursalId);
                    }
                } else {
                    // PSS sum = 0 → stock global/legacy, no distribuido por sucursal → fallback
                    log.warn("⚠️ PSS sin stock distribuido para producto={} — fallback a stock global",
                            producto.getId());
                    stockDisponible = producto.getStockActual() != null ? producto.getStockActual() : 0;
                }
            } else {
                stockDisponible = producto.getStockActual() != null ? producto.getStockActual() : 0;
            }

            if (cantidadBase > stockDisponible) {
                throw new BadRequestException(String.format(
                        "Stock insuficiente para '%s': disponible=%d unidades base, requerido=%d%s.",
                        producto.getNombre(), stockDisponible, cantidadBase,
                        factor > 1 ? " (" + cantidad.intValue() + " × " + factor + ")" : ""));
            }
        }
    }

    /** Devuelve el factor de conversión a unidad base de la presentación del ítem, o 1 si no aplica. */
    private int getFactorPresentacion(GuiaRemisionItem item, String tenantId) {
        if (item.getPresentacionId() == null) return 1;
        return presentacionRepo.findById(item.getPresentacionId())
                .filter(p -> tenantId.equals(p.getTenantId())
                        && item.getProducto() != null
                        && item.getProducto().getId().equals(p.getProducto().getId()))
                .map(p -> p.getFactor() != null ? p.getFactor() : 1)
                .orElseThrow(() -> new BadRequestException(
                        "Presentación inválida o no pertenece a este producto: " + item.getPresentacionId()));
    }

    private void registrarSalidaInventario(GuiaRemision guia, String tenantId, Long userId) {
        if (guia.getVenta() != null) return; // Caso D: POS ya descontó inventario
        Long sucursalId = guia.getSucursalOrigen() != null ? guia.getSucursalOrigen().getId() : null;
        for (GuiaRemisionItem item : guia.getItems()) {
            int cantidadBase = item.getCantidad().intValue() * getFactorPresentacion(item, tenantId);
            MovimientoInventario mov = MovimientoInventario.builder()
                    .producto(item.getProducto())
                    .cantidad(cantidadBase)
                    .tipo("SALIDA")
                    .tenantId(tenantId)
                    .sucursalId(sucursalId)
                    .varianteId(item.getVarianteId())
                    .descripcion("GRE " + guia.getNumeroCompleto() + " - Traslado salida")
                    .referencia("GRE-" + guia.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
            movimientoService.crearMovimiento(mov);
            // Descontar lotes FEFO (sucursalId=null para Caso B legacy, id concreto para Caso A)
            stockLoteService.descontarFefo(tenantId, item.getProducto().getId(), sucursalId, cantidadBase);
            // F-21.2: actualizar PSS de la sucursal origen y el stock global del producto.
            final int delta = cantidadBase;
            final Producto producto = item.getProducto();
            // Descontar PSS solo si ya existe la fila (stock distribuido por sucursal).
            // Si no existe (modo legacy/global), no crear PSS con valor ficticio.
            if (sucursalId != null) {
                stockSucursalRepo.findByProductoIdAndSucursalId(producto.getId(), sucursalId)
                        .ifPresent(pss -> {
                            int cur = pss.getStockActual() != null ? pss.getStockActual() : 0;
                            pss.setStockActual(Math.max(0, cur - delta));
                            stockSucursalRepo.save(pss);
                        });
            }
            // F-23: si el item tiene variante, actualizar stock de esa variante en la sucursal origen.
            if (item.getVarianteId() != null) {
                actualizarStockVariante(item.getVarianteId(), sucursalId, tenantId, -delta, guia.getNumeroCompleto());
            }
            // Usar decrementarStockAtomico (con guarda anti-negativos WHERE stockActual >= delta)
            int updated = productoRepo.decrementarStockAtomico(producto.getId(), delta, tenantId);
            if (updated == 0) {
                log.warn("⚠️ GRE {}: decrementarStockAtomico no actualizó stock global para producto={} delta={} " +
                         "(stock insuficiente o producto no encontrado)", guia.getNumeroCompleto(), producto.getId(), delta);
            }
        }
    }

    private void registrarEntradaInventario(GuiaRemision guia, String tenantId, Long userId) {
        Long sucursalDestinoId = guia.getSucursalDestino() != null ? guia.getSucursalDestino().getId() : null;
        for (GuiaRemisionItem item : guia.getItems()) {
            int cantidadBase = item.getCantidad().intValue() * getFactorPresentacion(item, tenantId);
            MovimientoInventario mov = MovimientoInventario.builder()
                    .producto(item.getProducto())
                    .cantidad(cantidadBase)
                    .tipo("ENTRADA")
                    .tenantId(tenantId)
                    .sucursalId(sucursalDestinoId)
                    .varianteId(item.getVarianteId())
                    .descripcion("GRE " + guia.getNumeroCompleto() + " - Recepción confirmada")
                    .referencia("GRE-" + guia.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
            movimientoService.crearMovimiento(mov);
            // F-21.2: actualizar PSS de la sucursal destino y el stock global del producto.
            final int delta = cantidadBase;
            final Producto producto = item.getProducto();
            if (sucursalDestinoId != null) {
                final Long destId = sucursalDestinoId;
                sucursalRepo.findById(destId).ifPresent(sucursal -> {
                    com.stockflow.entity.ProductoStockSucursal pss = stockSucursalRepo
                            .findByProductoIdAndSucursalId(producto.getId(), destId)
                            .orElseGet(() -> com.stockflow.entity.ProductoStockSucursal.builder()
                                    .producto(producto)
                                    .sucursal(sucursal)
                                    .tenantId(tenantId)
                                    .stockActual(0)
                                    .build());
                    int cur = pss.getStockActual() != null ? pss.getStockActual() : 0;
                    pss.setStockActual(cur + delta);
                    stockSucursalRepo.save(pss);
                });
            }
            // F-23: si el item tiene variante, actualizar stock de esa variante en la sucursal destino.
            if (item.getVarianteId() != null) {
                actualizarStockVariante(item.getVarianteId(), sucursalDestinoId, tenantId, +delta, guia.getNumeroCompleto());
            }
            productoRepo.incrementarStockAtomico(producto.getId(), delta, tenantId);
        }
    }

    /**
     * F-23: Actualiza stock de una variante en una sucursal específica (delta positivo = entrada, negativo = salida).
     * Si el registro en producto_variante_stock_sucursal no existe, lo crea con stock 0 antes de aplicar el delta.
     * Después recalcula ProductoVariante.stockActual como suma de todos los locales del tenant.
     */
    private void actualizarStockVariante(Long varianteId, Long sucursalId, String tenantId, int delta, String greNumero) {
        varianteRepo.findByIdAndTenantId(varianteId, tenantId).ifPresentOrElse(variante -> {
            if (sucursalId != null) {
                com.stockflow.entity.ProductoVarianteStockSucursal pvss = varianteSucursalRepo
                        .findByVarianteIdAndSucursalId(varianteId, sucursalId)
                        .orElseGet(() -> com.stockflow.entity.ProductoVarianteStockSucursal.builder()
                                .varianteId(varianteId)
                                .sucursalId(sucursalId)
                                .tenantId(tenantId)
                                .stockActual(0)
                                .stockMinimo(variante.getStockMinimo() != null ? variante.getStockMinimo() : 0)
                                .build());
                int cur = pvss.getStockActual() != null ? pvss.getStockActual() : 0;
                pvss.setStockActual(Math.max(0, cur + delta));
                varianteSucursalRepo.save(pvss);
                log.info("📍 GRE {}: variante {} sucursal {} stock {} → {}", greNumero, varianteId, sucursalId, cur, pvss.getStockActual());
            } else {
                // Modo legacy sin sucursal: ajustar directamente el stock global de la variante
                int cur = variante.getStockActual() != null ? variante.getStockActual() : 0;
                variante.setStockActual(Math.max(0, cur + delta));
            }
            // Recalcular stock global de la variante como suma de sus locales (si existen locales)
            int totalLocal = varianteSucursalRepo.sumStockByVarianteIdAndTenantId(varianteId, tenantId);
            if (totalLocal > 0 || sucursalId != null) {
                variante.setStockActual(totalLocal);
            }
            varianteRepo.save(variante);
        }, () -> log.warn("⚠️ GRE {}: variante {} no encontrada en tenant {}", greNumero, varianteId, tenantId));
    }

    private void registrarEvento(GuiaRemision guia, TipoEventoGRE tipo, String descripcion, Long userId) {
        GuiaRemisionEvento evento = GuiaRemisionEvento.builder()
                .guiaRemision(guia)
                .tenantId(guia.getTenantId())
                .tipoEvento(tipo)
                .descripcion(descripcion)
                .usuario(userId != null ? userId.toString() : null)
                .createdAt(OffsetDateTime.now())
                .build();
        eventoRepo.save(evento);
    }

    // ── MAPPERS ───────────────────────────────────────────────────────────────

    private GuiaRemisionResponse toResponse(GuiaRemision g) {
        GuiaRemisionResponse.GuiaRemisionResponseBuilder b = GuiaRemisionResponse.builder()
                .id(g.getId())
                .tenantId(g.getTenantId())
                .serie(g.getSerie())
                .correlativo(g.getCorrelativo())
                .numeroCompleto(g.getNumeroCompleto())
                .tipoTraslado(g.getTipoTraslado())
                .motivoTrasladoCodigo(g.getMotivoTrasladoCodigo())
                .motivoTrasladoDescripcion(g.getMotivoTrasladoDescripcion())
                .modalidadTransporte(g.getModalidadTransporte())
                .fechaTraslado(g.getFechaTraslado())
                .pesoBrutoTotal(g.getPesoBrutoTotal())
                .unidadMedidaPeso(g.getUnidadMedidaPeso())
                .numeroBultos(g.getNumeroBultos())
                .observaciones(g.getObservaciones())
                .destinatarioTipoDoc(g.getDestinatarioTipoDoc())
                .destinatarioNumDoc(g.getDestinatarioNumDoc())
                .destinatarioRazonSocial(g.getDestinatarioRazonSocial())
                .destinatarioDireccion(g.getDestinatarioDireccion())
                .ventaId(g.getVenta() != null ? g.getVenta().getId() : null)
                .estadoTributario(g.getEstadoTributario())
                .estadoLogistico(g.getEstadoLogistico())
                .lucodeId(g.getLucodeId())
                .lucodeHash(g.getLucodeHash())
                .xmlUrl(g.getXmlUrl())
                .cdrUrl(g.getCdrUrl())
                .pdfUrl(g.getPdfUrl())
                .createdBy(g.getCreatedBy())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt());

        if (g.getSucursalOrigen() != null) {
            b.sucursalOrigenId(g.getSucursalOrigen().getId())
             .sucursalOrigenNombre(g.getSucursalOrigen().getNombre());
        }
        if (g.getSucursalDestino() != null) {
            b.sucursalDestinoId(g.getSucursalDestino().getId())
             .sucursalDestinoNombre(g.getSucursalDestino().getNombre());
        }

        // Items
        if (g.getItems() != null && !g.getItems().isEmpty()) {
            b.items(g.getItems().stream().map(item -> {
                GuiaRemisionItemDTO dto = new GuiaRemisionItemDTO();
                dto.setProductoId(item.getProducto() != null ? item.getProducto().getId() : null);
                dto.setDescripcion(item.getDescripcion());
                dto.setCantidad(item.getCantidad());
                dto.setUnidadMedida(item.getUnidadMedida());
                dto.setPresentacionId(item.getPresentacionId());
                dto.setVarianteId(item.getVarianteId());
                return dto;
            }).collect(Collectors.toList()));
        }

        // Transporte
        if (g.getTransporte() != null) {
            GuiaRemisionTransporte t = g.getTransporte();
            TransporteDTO td = new TransporteDTO();
            td.setDireccionPartida(t.getDireccionPartida());
            td.setUbigeoPartida(t.getUbigeoPartida());
            td.setDireccionLlegada(t.getDireccionLlegada());
            td.setUbigeoLlegada(t.getUbigeoLlegada());
            td.setVehiculoPlaca(t.getVehiculoPlaca());
            td.setConductorTipoDoc(t.getConductorTipoDoc());
            td.setConductorNumDoc(t.getConductorNumDoc());
            td.setConductorNombres(t.getConductorNombres());
            td.setConductorApellidos(t.getConductorApellidos());
            td.setConductorLicencia(t.getConductorLicencia());
            td.setFechaInicioTraslado(t.getFechaInicioTraslado());
            td.setTransportistaRuc(t.getTransportistaRuc());
            td.setTransportistaDenominacion(t.getTransportistaDenominacion());
            td.setTransportistaNroRegistroMtc(t.getTransportistaNroRegistroMtc());
            td.setTransportistaNroAutorizacion(t.getTransportistaNroAutorizacion());
            td.setTransportistaCodigoEntidadAutorizadora(t.getTransportistaCodigoEntidadAutorizadora());
            td.setFechaEntregaTransportista(t.getFechaEntregaTransportista());
            b.transporte(td);
        }

        return b.build();
    }

    private GuiaRemisionListResponse toListResponse(GuiaRemision g) {
        return GuiaRemisionListResponse.builder()
                .id(g.getId())
                .numeroCompleto(g.getNumeroCompleto())
                .tipoTraslado(g.getTipoTraslado())
                .modalidadTransporte(g.getModalidadTransporte())
                .fechaTraslado(g.getFechaTraslado())
                .sucursalOrigenNombre(g.getSucursalOrigen() != null ? g.getSucursalOrigen().getNombre() : null)
                .sucursalDestinoNombre(g.getSucursalDestino() != null ? g.getSucursalDestino().getNombre() : null)
                .destinatarioRazonSocial(g.getDestinatarioRazonSocial())
                .estadoTributario(g.getEstadoTributario())
                .estadoLogistico(g.getEstadoLogistico())
                .ventaId(g.getVenta() != null ? g.getVenta().getId() : null)
                .pdfUrl(g.getPdfUrl())
                .createdAt(g.getCreatedAt())
                .build();
    }
}
