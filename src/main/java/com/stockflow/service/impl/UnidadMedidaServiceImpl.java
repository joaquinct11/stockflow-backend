package com.stockflow.service.impl;

import com.stockflow.dto.UnidadMedidaRequestDTO;
import com.stockflow.dto.UnidadMedidaResponseDTO;
import com.stockflow.entity.UnidadMedida;
import com.stockflow.repository.UnidadMedidaRepository;
import com.stockflow.service.UnidadMedidaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnidadMedidaServiceImpl implements UnidadMedidaService {

    private final UnidadMedidaRepository repository;

    @Override
    public List<UnidadMedidaResponseDTO> listar() {
        return repository.findAll()
                .stream()
                .map(u -> new UnidadMedidaResponseDTO(u.getId(), u.getNombre()))
                .toList();
    }

    @Override
    public UnidadMedidaResponseDTO obtenerPorId(Long id) {
        UnidadMedida unidad = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada"));

        return new UnidadMedidaResponseDTO(unidad.getId(), unidad.getNombre());
    }

    @Override
    public UnidadMedidaResponseDTO crear(UnidadMedidaRequestDTO request) {
        UnidadMedida unidad = new UnidadMedida();
        unidad.setNombre(request.getNombre());

        return map(repository.save(unidad));
    }

    @Override
    public UnidadMedidaResponseDTO actualizar(Long id, UnidadMedidaRequestDTO request) {
        UnidadMedida unidad = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Unidad no encontrada"));

        unidad.setNombre(request.getNombre());

        return map(repository.save(unidad));
    }

    @Override
    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Unidad no encontrada");
        }
        repository.deleteById(id);
    }

    private UnidadMedidaResponseDTO map(UnidadMedida unidad) {
        return new UnidadMedidaResponseDTO(unidad.getId(), unidad.getNombre());
    }
}
