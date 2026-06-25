package com.stockflow.service.impl;

import com.stockflow.dto.CertificadoDTO;
import com.stockflow.entity.CertificadoEstablecimiento;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.CertificadoRepository;
import com.stockflow.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.stockflow.service.CertificadoService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CertificadoServiceImpl implements CertificadoService {

    private final CertificadoRepository certificadoRepository;
    private final UsuarioRepository     usuarioRepository;

    @Override
    public List<CertificadoDTO> listar(String tenantId) {
        return certificadoRepository
                .findByTenantIdAndActivoTrueOrderByFechaVencimientoAsc(tenantId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public CertificadoDTO crear(CertificadoDTO dto, String tenantId) {
        validarDTO(dto);
        CertificadoEstablecimiento cert = CertificadoEstablecimiento.builder()
                .tenantId(tenantId)
                .tipo(dto.getTipo())
                .descripcion(dto.getDescripcion().trim())
                .usuarioId(dto.getUsuarioId())
                .fechaVencimiento(dto.getFechaVencimiento())
                .diasAlerta(dto.getDiasAlerta() != null ? dto.getDiasAlerta() : 30)
                .observaciones(dto.getObservaciones())
                .activo(true)
                .build();
        return toDTO(certificadoRepository.save(cert));
    }

    @Override
    public CertificadoDTO actualizar(Long id, CertificadoDTO dto, String tenantId) {
        validarDTO(dto);
        CertificadoEstablecimiento cert = certificadoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificado no encontrado: " + id));
        cert.setTipo(dto.getTipo());
        cert.setDescripcion(dto.getDescripcion().trim());
        cert.setUsuarioId(dto.getUsuarioId());
        cert.setFechaVencimiento(dto.getFechaVencimiento());
        cert.setDiasAlerta(dto.getDiasAlerta() != null ? dto.getDiasAlerta() : 30);
        cert.setObservaciones(dto.getObservaciones());
        return toDTO(certificadoRepository.save(cert));
    }

    @Override
    public void eliminar(Long id, String tenantId) {
        CertificadoEstablecimiento cert = certificadoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificado no encontrado: " + id));
        cert.setActivo(false);
        certificadoRepository.save(cert);
    }

    @Override
    public long contarAlertas(String tenantId) {
        // Máximo de días de alerta que usa cualquier certificado (usamos 90 como techo para la query)
        LocalDate horizonte = LocalDate.now().plusDays(90);
        return certificadoRepository.countAlertasActivas(tenantId, horizonte);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private CertificadoDTO toDTO(CertificadoEstablecimiento c) {
        LocalDate hoy = LocalDate.now();
        long diasRestantes = ChronoUnit.DAYS.between(hoy, c.getFechaVencimiento());
        int alerta = c.getDiasAlerta() != null ? c.getDiasAlerta() : 30;

        String estado;
        if (diasRestantes < 0) {
            estado = "VENCIDO";
        } else if (diasRestantes <= alerta) {
            estado = "POR_VENCER";
        } else {
            estado = "VIGENTE";
        }

        String usuarioNombre = null;
        if (c.getUsuarioId() != null) {
            usuarioNombre = usuarioRepository.findById(c.getUsuarioId())
                    .map(u -> u.getNombre() + (u.getApellido() != null ? " " + u.getApellido() : ""))
                    .orElse(null);
        }

        return CertificadoDTO.builder()
                .id(c.getId())
                .tipo(c.getTipo())
                .descripcion(c.getDescripcion())
                .usuarioId(c.getUsuarioId())
                .usuarioNombre(usuarioNombre)
                .fechaVencimiento(c.getFechaVencimiento())
                .diasAlerta(c.getDiasAlerta())
                .observaciones(c.getObservaciones())
                .estado(estado)
                .diasRestantes(diasRestantes)
                .build();
    }

    private void validarDTO(CertificadoDTO dto) {
        if (dto.getTipo() == null || dto.getTipo().isBlank())
            throw new BadRequestException("El tipo de certificado es requerido.");
        if (dto.getFechaVencimiento() == null)
            throw new BadRequestException("La fecha de vencimiento es requerida.");
        String tipoLower = dto.getTipo().toLowerCase();
        boolean esCarnet = tipoLower.contains("carnet") || tipoLower.contains("sanidad");
        if (esCarnet && dto.getUsuarioId() == null)
            throw new BadRequestException("Debe seleccionar el usuario para el carnet de sanidad.");
    }
}
