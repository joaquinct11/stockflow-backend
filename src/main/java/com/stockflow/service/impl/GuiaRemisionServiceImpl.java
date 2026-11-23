package com.stockflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.gre.*;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Sucursal;
import com.stockflow.entity.Tenant;
import com.stockflow.entity.gre.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.entity.ProductoPresentacion;
import com.stockflow.entity.Venta;
import com.stockflow.repository.*;
import com.stockflow.service.GuiaRemisionService;
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

        // Correlativo auto-incremental por serie
        String serie       = tenant.getSerieGuia() != null ? tenant.getSerieGuia() : "T001";
        int    correlativo = guiaRepo.findMaxCorrelativo(tenantId, serie) + 1;

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
        }

        // Regla F-09/F-10: DELIVERY independiente (sin venta) requiere sucursal o punto de despacho
        validarOrigenDelivery(req.getTipoTraslado(), venta, origen, tenantId, tenant);

        // Construir entidad
        GuiaRemision guia = GuiaRemision.builder()
                .tenantId(tenantId)
                .serie(serie)
                .correlativo(correlativo)
                .tipoTraslado(req.getTipoTraslado())
                .motivoTrasladoCodigo(req.getMotivoTrasladoCodigo())
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
    public Page<GuiaRemisionListResponse> listar(Pageable pageable) {
        String tenantId = TenantContext.getCurrentTenant();
        return guiaRepo.findByTenantId(tenantId, pageable)
                .map(this::toListResponse);
    }

    // ── OBTENER ───────────────────────────────────────────────────────────────

    @Override
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
        return guiaRepo.findByIdAndTenantId(id, tenantId)
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

            Integer stockLote = stockLoteService.getStockVigente(tenantId, producto.getId(), sucursalId);
            int stockDisponible = (stockLote != null)
                    ? stockLote
                    : (producto.getStockActual() != null ? producto.getStockActual() : 0);

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
                    .descripcion("GRE " + guia.getNumeroCompleto() + " - Traslado salida")
                    .referencia("GRE-" + guia.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
            movimientoService.crearMovimiento(mov);
            // Descontar lotes FEFO (sucursalId=null para Caso B legacy, id concreto para Caso A)
            stockLoteService.descontarFefo(tenantId, item.getProducto().getId(), sucursalId, cantidadBase);
        }
    }

    private void registrarEntradaInventario(GuiaRemision guia, String tenantId, Long userId) {
        for (GuiaRemisionItem item : guia.getItems()) {
            int cantidadBase = item.getCantidad().intValue() * getFactorPresentacion(item, tenantId);
            MovimientoInventario mov = MovimientoInventario.builder()
                    .producto(item.getProducto())
                    .cantidad(cantidadBase)
                    .tipo("ENTRADA")
                    .tenantId(tenantId)
                    .descripcion("GRE " + guia.getNumeroCompleto() + " - Recepción confirmada")
                    .referencia("GRE-" + guia.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
            movimientoService.crearMovimiento(mov);
        }
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
                .createdAt(g.getCreatedAt())
                .build();
    }
}
