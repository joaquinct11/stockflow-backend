package com.stockflow.service.impl;

import com.stockflow.dto.OtConsumoClasificacionDTO;
import com.stockflow.dto.RegistrarClasificacionOtRequestDTO;
import com.stockflow.entity.OtConsumoClasificacion;
import com.stockflow.entity.OtConsumoMaterial;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.Producto;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.OrdenTrabajoRepository;
import com.stockflow.repository.OtConsumoClasificacionRepository;
import com.stockflow.repository.OtConsumoDevolucionRepository;
import com.stockflow.repository.OtConsumoMaterialRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.OtClasificacionService;
import com.stockflow.service.TenantService;
import com.stockflow.util.TenantContext;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OtClasificacionServiceImpl implements OtClasificacionService {

    private static final String RUBRO = "EMPRESA_SERVICIOS";
    private static final Set<String> ESTADOS_CON_CLASIFICACION = Set.of(
            "ABIERTA", "EN_PROCESO", "DIAGNOSTICADA", "PENDIENTE_REPUESTO");
    private static final Set<String> CLASIFICACIONES_VALIDAS = Set.of(
            "INSTALADO", "RECUPERABLE", "DAÑADO", "PERDIDO");

    private final OrdenTrabajoRepository otRepository;
    private final OtConsumoMaterialRepository consumoRepository;
    private final OtConsumoDevolucionRepository devolucionRepository;
    private final OtConsumoClasificacionRepository clasificacionRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TenantService tenantService;

    @Override
    @Transactional
    public OtConsumoClasificacionDTO registrarClasificacion(Long otId, Long consumoId,
            RegistrarClasificacionOtRequestDTO req) {
        String tenantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        tenantService.requireRubro(tenantId, RUBRO);

        // ── Paso 1: Lock OT ─────────────────────────────────────────────────────
        OrdenTrabajo ot = otRepository.findByIdAndTenantIdForUpdate(otId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));
        assertTecnicoAccess(ot, userId);

        if (!ESTADOS_CON_CLASIFICACION.contains(ot.getEstado())) {
            throw new BadRequestException(
                    "No se pueden registrar clasificaciones en una OT en estado: " + ot.getEstado());
        }

        // ── Paso 2: Lock Consumo ─────────────────────────────────────────────────
        OtConsumoMaterial consumo = consumoRepository.findByIdAndTenantIdForUpdate(consumoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumo no encontrado"));

        if (!consumo.getOrdenTrabajo().getId().equals(otId)) {
            throw new ResourceNotFoundException("Consumo no encontrado");
        }

        // ── Paso 3: Verificar que es material físico (no servicio) ───────────────
        Producto producto = productoRepository.findById(consumo.getProductoId())
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        if ("SERVICIO".equals(producto.getTipo())) {
            throw new BadRequestException("No se pueden clasificar servicios, solo materiales físicos");
        }

        // ── Paso 4: Validar clasificación ────────────────────────────────────────
        if (!CLASIFICACIONES_VALIDAS.contains(req.getClasificacion())) {
            throw new BadRequestException(
                    "Clasificación inválida. Valores aceptados: INSTALADO, RECUPERABLE, DAÑADO, PERDIDO");
        }

        // ── Paso 5: Verificar que no supera la cantidad disponible para clasificar
        // cantidad pendiente = cantidadBase - cantidadDevuelta - cantidadYaClasificada
        int cantidadDevuelta = devolucionRepository
                .sumCantidadDevueltaByConsumoId(consumoId, tenantId);
        int cantidadYaClasificada = clasificacionRepository
                .sumCantidadClasificadaByConsumoId(consumoId, tenantId);
        int disponibleParaClasificar = consumo.getCantidadBase() - cantidadDevuelta - cantidadYaClasificada;

        if (disponibleParaClasificar <= 0) {
            throw new BadRequestException(
                    "Todo el material de este consumo ya fue clasificado o devuelto");
        }
        if (req.getCantidad() > disponibleParaClasificar) {
            throw new BadRequestException(
                    "Cantidad a clasificar (" + req.getCantidad()
                    + ") supera la disponible (" + disponibleParaClasificar + ")");
        }

        // ── Paso 6: Guardar clasificación ────────────────────────────────────────
        OtConsumoClasificacion clasificacion = clasificacionRepository.save(
                OtConsumoClasificacion.builder()
                        .tenantId(tenantId)
                        .consumoId(consumoId)
                        .ordenTrabajoId(otId)
                        .clasificacion(req.getClasificacion())
                        .cantidad(req.getCantidad())
                        .notas(req.getNotas())
                        .registradoPor(userId)
                        .build());

        String registradoPorNombre = usuarioRepository.findById(userId)
                .map(this::usuarioNombre)
                .orElse(null);

        return toDTO(clasificacion, registradoPorNombre);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OtConsumoClasificacionDTO> getClasificaciones(Long otId, Long consumoId) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);

        otRepository.findByIdAndTenantId(otId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de trabajo no encontrada"));

        OtConsumoMaterial consumo = consumoRepository.findById(consumoId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Consumo no encontrado"));
        if (!consumo.getOrdenTrabajo().getId().equals(otId)) {
            throw new ResourceNotFoundException("Consumo no encontrado");
        }

        List<OtConsumoClasificacion> clasificaciones = clasificacionRepository
                .findByConsumoIdOrderByCreatedAtAsc(consumoId);

        if (clasificaciones.isEmpty()) return List.of();

        Set<Long> userIds = clasificaciones.stream()
                .map(OtConsumoClasificacion::getRegistradoPor)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> usuarioNombres = usuarioRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Usuario::getId, this::usuarioNombre));

        return clasificaciones.stream().map(cl -> toDTO(
                cl,
                cl.getRegistradoPor() != null ? usuarioNombres.get(cl.getRegistradoPor()) : null
        )).collect(Collectors.toList());
    }

    private OtConsumoClasificacionDTO toDTO(OtConsumoClasificacion cl, String registradoPorNombre) {
        return OtConsumoClasificacionDTO.builder()
                .id(cl.getId())
                .consumoId(cl.getConsumoId())
                .ordenTrabajoId(cl.getOrdenTrabajoId())
                .clasificacion(cl.getClasificacion())
                .cantidad(cl.getCantidad())
                .notas(cl.getNotas())
                .registradoPor(cl.getRegistradoPor())
                .registradoPorNombre(registradoPorNombre)
                .createdAt(cl.getCreatedAt())
                .build();
    }

    private String usuarioNombre(Usuario u) {
        return u.getNombre() != null ? u.getNombre() : u.getEmail();
    }

    private boolean callerHasAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    private void assertTecnicoAccess(OrdenTrabajo ot, Long userId) {
        if (callerHasAuthority("PERM_VER_TODAS_OT")) return;
        if (ot.getTecnico() == null || !ot.getTecnico().getId().equals(userId)) {
            throw new ResourceNotFoundException("Orden de trabajo no encontrada");
        }
    }
}
