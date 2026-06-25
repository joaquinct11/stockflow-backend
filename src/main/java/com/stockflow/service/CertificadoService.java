package com.stockflow.service;

import com.stockflow.dto.CertificadoDTO;

import java.util.List;

public interface CertificadoService {
    List<CertificadoDTO> listar(String tenantId);
    CertificadoDTO crear(CertificadoDTO dto, String tenantId);
    CertificadoDTO actualizar(Long id, CertificadoDTO dto, String tenantId);
    void eliminar(Long id, String tenantId);
    long contarAlertas(String tenantId);
}
