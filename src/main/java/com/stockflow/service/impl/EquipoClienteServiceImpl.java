package com.stockflow.service.impl;

import com.stockflow.util.TenantContext;
import com.stockflow.dto.ActualizarEquipoRequestDTO;
import com.stockflow.dto.CrearEquipoRequestDTO;
import com.stockflow.dto.EquipoClienteDTO;
import com.stockflow.dto.EquipoPageResponseDTO;
import com.stockflow.dto.EquipoResumenDTO;
import com.stockflow.dto.EquipoStatsDTO;
import com.stockflow.entity.Cliente;
import com.stockflow.entity.EquipoCliente;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.EquipoClienteMapper;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.EquipoClienteRepository;
import com.stockflow.service.EquipoClienteService;
import com.stockflow.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EquipoClienteServiceImpl implements EquipoClienteService {

    private static final String RUBRO = "EMPRESA_SERVICIOS";

    private final EquipoClienteRepository equipoRepository;
    private final ClienteRepository clienteRepository;
    private final EquipoClienteMapper mapper;
    private final TenantService tenantService;

    @Override
    public EquipoStatsDTO getStats() {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);
        return EquipoStatsDTO.builder()
                .total(equipoRepository.countTotal(tenantId))
                .activos(equipoRepository.countActivos(tenantId))
                .inactivos(equipoRepository.countInactivos(tenantId))
                .clientesConEquipos(equipoRepository.countClientesConEquipos(tenantId))
                .build();
    }

    @Override
    public EquipoPageResponseDTO listar(int page, int size, String estado, String search, Long clienteId) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);

        String s = (search == null) ? "" : search.trim().toLowerCase();
        PageRequest pageable = PageRequest.of(page, size);

        Page<EquipoResumenDTO> pageResult = switch (estado == null ? "TODOS" : estado.toUpperCase()) {
            case "ACTIVOS"   -> equipoRepository.findPaginadoActivos(tenantId, clienteId, s, pageable);
            case "INACTIVOS" -> equipoRepository.findPaginadoInactivos(tenantId, clienteId, s, pageable);
            default          -> equipoRepository.findPaginadoTodos(tenantId, clienteId, s, pageable);
        };

        long total    = equipoRepository.countTotal(tenantId);
        long activos  = equipoRepository.countActivos(tenantId);
        long inactivos = total - activos;

        return EquipoPageResponseDTO.builder()
                .content(pageResult.getContent())
                .page(page)
                .size(size)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .clientesConEquipos(equipoRepository.countClientesConEquipos(tenantId))
                .resumen(EquipoPageResponseDTO.ResumenDTO.builder()
                        .total(total)
                        .activos(activos)
                        .inactivos(inactivos)
                        .build())
                .build();
    }

    @Override
    public EquipoClienteDTO obtenerPorId(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);
        EquipoCliente equipo = findOrThrow(id, tenantId);
        return mapper.toDTO(equipo);
    }

    @Override
    @Transactional
    public EquipoClienteDTO crear(CrearEquipoRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);

        Cliente cliente = clienteRepository.findByIdAndTenantId(dto.getClienteId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));

        String serie = normalizeNumeroSerie(dto.getNumeroSerie());
        if (serie != null && equipoRepository.existsByNumeroSerieAndTenantId(serie, tenantId)) {
            throw new ConflictException("Ya existe un equipo con el número de serie '" + serie + "' en este tenant.");
        }

        EquipoCliente equipo = EquipoCliente.builder()
                .tenantId(tenantId)
                .cliente(cliente)
                .nombre(dto.getNombre().trim())
                .tipo(dto.getTipo())
                .marca(dto.getMarca())
                .modelo(dto.getModelo())
                .numeroSerie(serie)
                .ubicacion(dto.getUbicacion())
                .observaciones(dto.getObservaciones())
                .activo(true)
                .createdAt(LocalDateTime.now())
                .build();

        return mapper.toDTO(equipoRepository.save(equipo));
    }

    @Override
    @Transactional
    public EquipoClienteDTO actualizar(Long id, ActualizarEquipoRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);

        EquipoCliente equipo = findOrThrow(id, tenantId);

        String serie = normalizeNumeroSerie(dto.getNumeroSerie());
        if (serie != null && equipoRepository.existsByNumeroSerieAndTenantIdAndIdNot(serie, tenantId, id)) {
            throw new ConflictException("Ya existe un equipo con el número de serie '" + serie + "' en este tenant.");
        }

        equipo.setNombre(dto.getNombre().trim());
        equipo.setTipo(dto.getTipo());
        equipo.setMarca(dto.getMarca());
        equipo.setModelo(dto.getModelo());
        equipo.setNumeroSerie(serie);
        equipo.setUbicacion(dto.getUbicacion());
        equipo.setObservaciones(dto.getObservaciones());
        equipo.setUpdatedAt(LocalDateTime.now());

        return mapper.toDTO(equipoRepository.save(equipo));
    }

    @Override
    @Transactional
    public void activar(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);
        EquipoCliente equipo = findOrThrow(id, tenantId);
        equipo.setActivo(true);
        equipo.setUpdatedAt(LocalDateTime.now());
        equipoRepository.save(equipo);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, RUBRO);
        EquipoCliente equipo = findOrThrow(id, tenantId);
        equipo.setActivo(false);
        equipo.setUpdatedAt(LocalDateTime.now());
        equipoRepository.save(equipo);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EquipoCliente findOrThrow(Long id, String tenantId) {
        return equipoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo no encontrado"));
    }

    private String normalizeNumeroSerie(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw.trim();
    }
}
