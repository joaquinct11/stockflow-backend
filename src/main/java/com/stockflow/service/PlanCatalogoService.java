package com.stockflow.service;

import com.stockflow.dto.PlanCatalogoDTO;
import com.stockflow.dto.PlanPrecioDTO;
import com.stockflow.entity.PlanCatalogo;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.PlanCatalogoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanCatalogoService {

    private final PlanCatalogoRepository planCatalogoRepository;

    public List<PlanCatalogoDTO> listarPorSegmento(String segmento) {
        return planCatalogoRepository
                .findBySegmentoAndActivoTrueOrderByNivelAsc(segmento.toUpperCase())
                .stream()
                .map(PlanCatalogoDTO::from)
                .toList();
    }

    public PlanCatalogoDTO obtenerPorCodigo(String codigo) {
        return planCatalogoRepository.findByCodigoAndActivoTrue(codigo.toUpperCase())
                .map(PlanCatalogoDTO::from)
                .orElseThrow(() -> new BadRequestException("Plan no encontrado o inactivo: " + codigo));
    }

    /**
     * Devuelve los precios Básico y Pro para un rubro operativo consultando el catálogo.
     * Si el rubro no tiene planes activos cae al rubro OTRO como fallback.
     * Unifica el endpoint /precios con la fuente de verdad de plan_catalogo.
     */
    public PlanPrecioDTO getPreciosPorRubroOperativo(String rubroOperativo) {
        List<PlanCatalogo> planes = planCatalogoRepository
                .findByRubroOperativoAndActivoTrueOrderByNivelAsc(rubroOperativo.toUpperCase());
        if (planes.isEmpty()) {
            planes = planCatalogoRepository.findByRubroOperativoAndActivoTrueOrderByNivelAsc("OTRO");
        }
        int basico = planes.stream()
                .filter(p -> "BASICO".equals(p.getNivel()))
                .mapToInt(p -> p.getPrecioMensual().intValue())
                .findFirst().orElse(89);
        int pro = planes.stream()
                .filter(p -> "PRO".equals(p.getNivel()))
                .mapToInt(p -> p.getPrecioMensual().intValue())
                .findFirst().orElse(169);
        return new PlanPrecioDTO(basico, pro);
    }

    /**
     * Valida que el código exista, esté activo y sea compatible con el rubro operativo del tenant.
     * Lanza BadRequestException si no es válido.
     */
    public PlanCatalogo validarYObtener(String planCodigo, String rubroOperativo) {
        PlanCatalogo plan = planCatalogoRepository.findByCodigoAndActivoTrue(planCodigo.toUpperCase())
                .orElseThrow(() -> new BadRequestException(
                        "El código de plan '" + planCodigo + "' no existe o está inactivo."));

        if (rubroOperativo != null && !rubroOperativo.isBlank()
                && !plan.getRubroOperativo().equalsIgnoreCase(rubroOperativo)) {
            throw new BadRequestException(
                    "El plan '" + planCodigo + "' no es compatible con el rubro '" + rubroOperativo + "'. "
                    + "Rubro esperado: " + plan.getRubroOperativo());
        }
        return plan;
    }
}
