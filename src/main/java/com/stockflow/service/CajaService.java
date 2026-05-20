package com.stockflow.service;

import com.stockflow.dto.AbrirCajaRequestDTO;
import com.stockflow.dto.CajaDTO;
import com.stockflow.dto.CerrarCajaRequestDTO;
import com.stockflow.dto.RegistrarRetiroRequestDTO;
import com.stockflow.dto.RetiroCajaDTO;
import java.util.List;
import java.util.Optional;

public interface CajaService {
    CajaDTO abrir(AbrirCajaRequestDTO request, Long usuarioId, String usuarioNombre, String tenantId);
    CajaDTO cerrar(Long cajaId, CerrarCajaRequestDTO request, String tenantId);
    RetiroCajaDTO registrarRetiro(Long cajaId, RegistrarRetiroRequestDTO request, Long usuarioId, String usuarioNombre, String tenantId);
    Optional<CajaDTO> getActiva(String tenantId);
    List<CajaDTO> getAll(String tenantId);
    Optional<CajaDTO> getById(Long id, String tenantId);
}
