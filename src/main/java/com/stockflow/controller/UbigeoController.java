package com.stockflow.controller;

import com.stockflow.entity.Ubigeo;
import com.stockflow.repository.UbigeoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ubigeos")
@RequiredArgsConstructor
public class UbigeoController {

    private final UbigeoRepository ubigeoRepository;

    @GetMapping("/departamentos")
    public List<String> getDepartamentos() {
        return ubigeoRepository.findAllDepartamentos();
    }

    @GetMapping("/provincias")
    public List<String> getProvincias(@RequestParam String departamento) {
        return ubigeoRepository.findProvinciasByDepartamento(departamento);
    }

    @GetMapping("/distritos")
    public List<Ubigeo> getDistritos(@RequestParam String departamento,
                                     @RequestParam String provincia) {
        return ubigeoRepository.findByDepartamentoAndProvincia(departamento, provincia);
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<Ubigeo> getByCodigo(@PathVariable String codigo) {
        return ubigeoRepository.findByCodigo(codigo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
