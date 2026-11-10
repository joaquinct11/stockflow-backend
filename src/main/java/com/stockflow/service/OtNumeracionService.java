package com.stockflow.service;

import com.stockflow.entity.OtNumeracion;
import com.stockflow.repository.OtNumeracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OtNumeracionService {

    private final OtNumeracionRepository numeracionRepository;

    /**
     * Genera el próximo numero_ot para el tenant y año dados.
     *
     * Protocolo de dos pasos para evitar race condition en el primer registro del año:
     * 1. INSERT ON CONFLICT DO NOTHING → garantiza que el registro exista de forma atómica.
     * 2. SELECT FOR UPDATE → adquiere lock pesimista sobre el registro ya existente.
     *
     * REQUIRES_NEW asegura que el número quede reservado incluso si la TX del caller revierte.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generarNumeroOt(String tenantId, short anio) {
        numeracionRepository.insertarSiNoExiste(tenantId, anio);

        OtNumeracion num = numeracionRepository
                .findByTenantIdAndAnioForUpdate(tenantId, anio)
                .orElseThrow(() -> new IllegalStateException(
                        "Registro de numeración OT no encontrado tras INSERT: " + tenantId + "/" + anio));

        num.setUltimo(num.getUltimo() + 1);
        numeracionRepository.save(num);

        return String.format("OT-%d-%05d", anio, num.getUltimo());
    }
}
