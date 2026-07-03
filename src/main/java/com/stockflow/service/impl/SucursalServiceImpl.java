package com.stockflow.service.impl;

import com.stockflow.dto.SucursalDTO;
import com.stockflow.entity.Sucursal;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.*;
import com.stockflow.service.PlanLimitService;
import com.stockflow.service.SucursalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SucursalServiceImpl implements SucursalService {

    private static final int MAX_SUCURSALES_PRO = 5;

    private final SucursalRepository                        sucursalRepository;
    private final ProductoStockSucursalRepository           stockSucursalRepository;
    private final ProductoVarianteStockSucursalRepository   varianteStockSucursalRepository;
    private final PlanLimitService                          planLimitService;

    // Repositorios para el backfill masivo al inicializar la sucursal principal
    private final VentaRepository         ventaRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final GastoRepository         gastoRepository;
    private final CajaRepository          cajaRepository;
    private final DevolucionRepository    devolucionRepository;
    private final NotaCreditoRepository   notaCreditoRepository;
    private final OrdenCompraRepository   ordenCompraRepository;
    private final RecepcionRepository     recepcionRepository;
    private final ComprobanteRepository   comprobanteRepository;
    private final UsuarioRepository       usuarioRepository;
    private final CertificadoRepository   certificadoRepository;

    // ── Listado ──────────────────────────────────────────────────────────────

    @Override
    public List<SucursalDTO> listar(String tenantId) {
        return sucursalRepository
                .findByTenantIdAndActivoTrueOrderByEsPrincipalDescNombreAsc(tenantId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public SucursalDTO obtenerPorId(Long id, String tenantId) {
        return toDTO(findOrThrow(id, tenantId));
    }

    // ── Creación ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SucursalDTO crear(SucursalDTO dto, String tenantId) {
        validarPlanPro(tenantId);
        validarLimiteSucursales(tenantId);

        Sucursal sucursal = Sucursal.builder()
                .tenantId(tenantId)
                .nombre(dto.getNombre().trim())
                .direccion(dto.getDireccion())
                .telefono(dto.getTelefono())
                .email(dto.getEmail())
                .esPrincipal(false)
                .activo(true)
                .build();

        Sucursal guardada = sucursalRepository.save(sucursal);

        // Nueva sucursal siempre arranca con stock 0 en todos los productos y variantes
        stockSucursalRepository.inicializarStockVacioParaSucursal(guardada.getId(), tenantId);
        varianteStockSucursalRepository.inicializarStockVacioParaSucursal(guardada.getId(), tenantId);

        log.info("Sucursal '{}' creada para tenant {} (id={})", guardada.getNombre(), tenantId, guardada.getId());
        return toDTO(guardada);
    }

    // ── Actualización ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SucursalDTO actualizar(Long id, SucursalDTO dto, String tenantId) {
        Sucursal sucursal = findOrThrow(id, tenantId);

        sucursal.setNombre(dto.getNombre().trim());
        sucursal.setDireccion(dto.getDireccion());
        sucursal.setTelefono(dto.getTelefono());
        sucursal.setEmail(dto.getEmail());
        sucursal.setUpdatedAt(LocalDateTime.now());

        return toDTO(sucursalRepository.save(sucursal));
    }

    // ── Desactivar ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void desactivar(Long id, String tenantId) {
        Sucursal sucursal = findOrThrow(id, tenantId);

        if (Boolean.TRUE.equals(sucursal.getEsPrincipal())) {
            throw new BadRequestException("No se puede desactivar la sucursal principal.");
        }

        sucursal.setActivo(false);
        sucursal.setDeletedAt(LocalDateTime.now());
        sucursalRepository.save(sucursal);
        log.info("Sucursal {} desactivada para tenant {}", id, tenantId);
    }

    // ── Inicialización (upgrade Básico → PRO) ────────────────────────────────

    @Override
    @Transactional
    public SucursalDTO inicializarPrincipal(String tenantId) {
        // Si ya existe una sucursal principal, retornarla sin duplicar
        return sucursalRepository.findByTenantIdAndEsPrincipalTrue(tenantId)
                .map(this::toDTO)
                .orElseGet(() -> crearSucursalPrincipalYMigrar(tenantId));
    }

    private SucursalDTO crearSucursalPrincipalYMigrar(String tenantId) {
        Sucursal principal = Sucursal.builder()
                .tenantId(tenantId)
                .nombre("Sucursal Principal")
                .esPrincipal(true)
                .activo(true)
                .build();

        Sucursal guardada = sucursalRepository.save(principal);
        Long sucursalId = guardada.getId();

        log.info("Inicializando sucursal principal (id={}) para tenant {}. Migrando datos...", sucursalId, tenantId);

        // Migrar todos los registros existentes del tenant a esta sucursal
        ventaRepository.asignarSucursalDondeEsNulo(sucursalId, tenantId);
        movimientoRepository.asignarSucursalDondeEsNulo(sucursalId, tenantId);
        gastoRepository.asignarSucursalDondeEsNulo(sucursalId, tenantId);
        cajaRepository.asignarSucursalDondeEsNulo(sucursalId, tenantId);
        devolucionRepository.asignarSucursalDondeEsNulo(sucursalId, tenantId);
        notaCreditoRepository.asignarSucursalDondeEsNulo(sucursalId, tenantId);
        ordenCompraRepository.asignarSucursalDondeEsNulo(sucursalId, tenantId);
        recepcionRepository.asignarSucursalDondeEsNulo(sucursalId, tenantId);
        comprobanteRepository.asignarSucursalDondeEsNulo(sucursalId, tenantId);
        certificadoRepository.asignarSucursalDondeEsNulo(sucursalId, tenantId);
        usuarioRepository.asignarSucursalAdminNulo(sucursalId, tenantId);

        // Inicializar stock de todos los productos y variantes para esta sucursal principal
        stockSucursalRepository.inicializarStockParaSucursal(sucursalId, tenantId);
        varianteStockSucursalRepository.inicializarStockPrincipalParaSucursal(sucursalId, tenantId);

        log.info("Migración completada para tenant {}", tenantId);
        return toDTO(guardada);
    }

    @Override
    @Transactional
    public void resyncStockPrincipal(String tenantId) {
        Sucursal principal = sucursalRepository.findByTenantIdAndEsPrincipalTrue(tenantId)
                .orElseThrow(() -> new BadRequestException("No existe sucursal principal para este tenant"));
        stockSucursalRepository.resyncStockPrincipal(principal.getId(), tenantId);
        log.info("✅ Stock de sucursal principal (id={}) resincronizado para tenant {}", principal.getId(), tenantId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Sucursal findOrThrow(Long id, String tenantId) {
        return sucursalRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada"));
    }

    private void validarPlanPro(String tenantId) {
        if (planLimitService.isBasico(tenantId)) {
            throw new BadRequestException(
                    "Las sucursales múltiples están disponibles en el plan Pro. Actualiza tu suscripción."
            );
        }
    }

    private void validarLimiteSucursales(String tenantId) {
        long total = sucursalRepository.countByTenantIdAndActivoTrue(tenantId);
        if (total >= MAX_SUCURSALES_PRO) {
            throw new BadRequestException(
                    "El plan Pro permite máximo " + MAX_SUCURSALES_PRO + " sucursales activas."
            );
        }
    }

    private SucursalDTO toDTO(Sucursal s) {
        return SucursalDTO.builder()
                .id(s.getId())
                .nombre(s.getNombre())
                .direccion(s.getDireccion())
                .telefono(s.getTelefono())
                .email(s.getEmail())
                .esPrincipal(s.getEsPrincipal())
                .activo(s.getActivo())
                .tenantId(s.getTenantId())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
