package com.stockflow.controller;

import com.stockflow.dto.UnidadMedidaRequestDTO;
import com.stockflow.dto.UnidadMedidaResponseDTO;
import com.stockflow.service.UnidadMedidaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/unidad-medida")
@RequiredArgsConstructor
public class UnidadMedidaController {

    private final UnidadMedidaService service;

    @GetMapping
    public List<UnidadMedidaResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public UnidadMedidaResponseDTO obtener(@PathVariable Long id) {
        return service.obtenerPorId(id);
    }

    @PostMapping
    public UnidadMedidaResponseDTO crear(@RequestBody UnidadMedidaRequestDTO request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public UnidadMedidaResponseDTO actualizar(@PathVariable Long id,
                                              @RequestBody UnidadMedidaRequestDTO request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }
}