package com.stockflow.service.impl;

import com.stockflow.entity.gre.GreSerieContador;
import com.stockflow.repository.GreSerieContadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera correlativos GRE de solo-avance usando un contador por (tenant, serie).
 *
 * La transacción usa REQUIRES_NEW para que el incremento sea visible y permanente
 * incluso si la transacción outer hace rollback: esto garantiza que un correlativo
 * asignado nunca sea reciclado (puede quedar como gap, pero no puede colisionar
 * con un número ya enviado a Lucode).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GreSerieContadorService {

    private final GreSerieContadorRepository contadorRepo;

    /**
     * Incrementa el contador de forma atómica y devuelve el nuevo correlativo.
     *
     * @param tenantId   tenant actual
     * @param serie      serie GRE (ej. "T001")
     * @param maxActual  MAX(correlativo) de las GREs existentes — se usa SOLO para
     *                   inicializar el contador si aún no existe, garantizando que
     *                   el primer correlativo generado sea mayor que cualquier GRE
     *                   ya existente.
     * @return correlativo reservado (el contador pasa de N a N+1 y retorna N+1)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int incrementarYObtener(String tenantId, String serie, int maxActual) {
        // Garantiza que la fila existe antes del SELECT FOR UPDATE.
        // ON CONFLICT DO NOTHING es atómico: varios hilos concurrentes solo insertan una vez.
        contadorRepo.insertIfAbsent(tenantId, serie, maxActual);

        GreSerieContador contador = contadorRepo
                .findByTenantIdAndSerieForUpdate(tenantId, serie)
                .orElseThrow(() -> new IllegalStateException(
                        "Contador GRE no encontrado tras insertIfAbsent: " + tenantId + "/" + serie));

        // Si el contador está por debajo del MAX real (ej. datos previos a esta migración),
        // nivelarlo antes de incrementar para evitar colisiones con la unique constraint.
        if (contador.getUltimo() < maxActual) {
            log.warn("Contador GRE {}/{} nivelado: {} → {}",
                    tenantId, serie, contador.getUltimo(), maxActual);
            contador.setUltimo(maxActual);
        }

        contador.setUltimo(contador.getUltimo() + 1);
        contadorRepo.save(contador);

        log.debug("Correlativo reservado: {}/{}-{}", tenantId, serie, contador.getUltimo());
        return contador.getUltimo();
    }
}
