package com.stockflow.service.impl;

import com.stockflow.dto.CategoriaResponseDTO;
import com.stockflow.entity.Categoria;
import com.stockflow.repository.CategoriaRepository;
import com.stockflow.service.CategoriaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaRepository repository;

    @Override
    public List<CategoriaResponseDTO> listar(String tenantId) {
        return repository.findByTenantIdOrGlobal(tenantId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public CategoriaResponseDTO crear(String nombre, String tenantId) {
        Categoria categoria = Categoria.builder()
                .nombre(nombre)
                .tenantId(tenantId) // nunca null — pertenece al tenant que la crea
                .activo(true)
                .createdAt(LocalDateTime.now())
                .build();

        return toDTO(repository.save(categoria));
    }

    private CategoriaResponseDTO toDTO(Categoria categoria) {
        return CategoriaResponseDTO.builder()
                .id(categoria.getId())
                .nombre(categoria.getNombre())
                .esGlobal(categoria.getTenantId() == null)
                .build();
    }
}
