package com.stockflow.service.impl;

import com.stockflow.dto.ActualizarMascotaRequestDTO;
import com.stockflow.dto.CrearMascotaRequestDTO;
import com.stockflow.dto.MascotaDTO;
import com.stockflow.dto.MascotaPageResponseDTO;
import com.stockflow.dto.MascotaResumenDTO;
import com.stockflow.dto.MascotaStatsDTO;
import com.stockflow.entity.Cliente;
import com.stockflow.entity.Mascota;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.mapper.MascotaMapper;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.MascotaRepository;
import com.stockflow.service.MascotaService;
import com.stockflow.service.TenantService;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MascotaServiceImpl implements MascotaService {

    private final MascotaRepository mascotaRepository;
    private final ClienteRepository  clienteRepository;
    private final MascotaMapper       mascotaMapper;
    private final TenantService       tenantService;

    @Override
    public MascotaDTO crearMascota(CrearMascotaRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");
        log.info("🐾 Creando mascota '{}' para tenant: {}", dto.getNombre(), tenantId);

        Cliente cliente = clienteRepository.findByIdAndTenantId(dto.getClienteId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente no encontrado en este tenant (id=" + dto.getClienteId() + ")"));

        if (dto.getMicrochip() != null && !dto.getMicrochip().isBlank()
                && mascotaRepository.existsByMicrochipAndTenantId(dto.getMicrochip(), tenantId)) {
            throw new ConflictException(
                    "Ya existe una mascota con microchip " + dto.getMicrochip() + " en este tenant");
        }

        Mascota mascota = Mascota.builder()
                .tenantId(tenantId)
                .cliente(cliente)
                .nombre(dto.getNombre())
                .especie(dto.getEspecie())
                .raza(dto.getRaza())
                .sexo(dto.getSexo())
                .fechaNacimiento(dto.getFechaNacimiento())
                .color(dto.getColor())
                .pesoKg(dto.getPesoKg())
                .microchip(dto.getMicrochip())
                .esterilizado(dto.getEsterilizado())
                .activo(true)
                .createdAt(LocalDateTime.now())
                .build();

        return mascotaMapper.toDTO(mascotaRepository.save(mascota));
    }

    @Override
    public MascotaDTO obtenerPorId(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");
        return mascotaRepository.findByIdAndTenantId(id, tenantId)
                .map(mascotaMapper::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Mascota no encontrada (id=" + id + ")"));
    }

    @Override
    public MascotaPageResponseDTO listar(int page, int size, String estado, String search) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");

        String s = (search == null || search.isBlank()) ? "" : search.trim().toLowerCase();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("nombre").ascending());

        Page<MascotaResumenDTO> pageResult = switch (estado.toUpperCase()) {
            case "INACTIVAS" -> mascotaRepository.findPaginadoInactivas(tenantId, s, pageable);
            case "TODOS"     -> mascotaRepository.findPaginadoTodos(tenantId, s, pageable);
            default          -> mascotaRepository.findPaginadoActivas(tenantId, s, pageable);
        };

        long totalActivas = mascotaRepository.countResumenActivas(tenantId, s);
        long totalTotal   = mascotaRepository.countResumenTotal(tenantId, s);

        return MascotaPageResponseDTO.builder()
                .content(pageResult.getContent())
                .page(page)
                .size(size)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .resumen(MascotaPageResponseDTO.ResumenDTO.builder()
                        .total(totalTotal)
                        .activas(totalActivas)
                        .inactivas(totalTotal - totalActivas)
                        .build())
                .build();
    }

    @Override
    public List<MascotaResumenDTO> listarPorCliente(Long clienteId) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");
        clienteRepository.findByIdAndTenantId(clienteId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cliente no encontrado en este tenant (id=" + clienteId + ")"));
        return mascotaRepository.findResumenByClienteIdAndTenantId(clienteId, tenantId);
    }

    @Override
    public List<MascotaResumenDTO> buscarPorNombre(String nombre) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");
        // Reutiliza findResumenByClienteIdAndTenantId pattern pero filtrando por nombre
        // Delegamos a una query separada para mantener el JOIN sin N+1
        return mascotaRepository.findResumenByTenantId(tenantId).stream()
                .filter(m -> m.getNombre().toLowerCase().contains(nombre.toLowerCase()))
                .toList();
    }

    @Override
    public MascotaDTO actualizar(Long id, ActualizarMascotaRequestDTO dto) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");
        log.info("✏️ Actualizando mascota ID: {} en tenant: {}", id, tenantId);

        Mascota mascota = mascotaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Mascota no encontrada (id=" + id + ")"));

        if (dto.getMicrochip() != null && !dto.getMicrochip().isBlank()
                && !dto.getMicrochip().equals(mascota.getMicrochip())
                && mascotaRepository.existsByMicrochipAndTenantId(dto.getMicrochip(), tenantId)) {
            throw new ConflictException(
                    "Ya existe una mascota con microchip " + dto.getMicrochip() + " en este tenant");
        }

        mascota.setNombre(dto.getNombre());
        mascota.setEspecie(dto.getEspecie());
        mascota.setRaza(dto.getRaza());
        mascota.setSexo(dto.getSexo());
        mascota.setFechaNacimiento(dto.getFechaNacimiento());
        mascota.setColor(dto.getColor());
        mascota.setPesoKg(dto.getPesoKg());
        mascota.setMicrochip(dto.getMicrochip());
        mascota.setEsterilizado(dto.getEsterilizado());
        mascota.setUpdatedAt(LocalDateTime.now());

        return mascotaMapper.toDTO(mascotaRepository.save(mascota));
    }

    @Override
    public MascotaDTO activar(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");
        log.info("✅ Activando mascota ID: {}", id);
        Mascota mascota = mascotaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Mascota no encontrada (id=" + id + ")"));
        mascota.setActivo(true);
        mascota.setUpdatedAt(LocalDateTime.now());
        return mascotaMapper.toDTO(mascotaRepository.save(mascota));
    }

    @Override
    public MascotaDTO desactivar(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");
        log.info("🔒 Desactivando mascota ID: {}", id);
        Mascota mascota = mascotaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Mascota no encontrada (id=" + id + ")"));
        mascota.setActivo(false);
        mascota.setUpdatedAt(LocalDateTime.now());
        return mascotaMapper.toDTO(mascotaRepository.save(mascota));
    }

    @Override
    public MascotaStatsDTO getStats() {
        String tenantId = TenantContext.getCurrentTenant();
        tenantService.requireRubro(tenantId, "VETERINARIA");
        long total   = mascotaRepository.countResumenTotal(tenantId, "");
        long activas = mascotaRepository.countResumenActivas(tenantId, "");
        return new MascotaStatsDTO(total, activas, total - activas);
    }
}
