package com.stockflow.repository;

import com.stockflow.entity.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {

    boolean existsByWebhookIdAndTipo(String webhookId, String tipo);

    Optional<WebhookLog> findByWebhookIdAndTipo(String webhookId, String tipo);

    boolean existsByWebhookIdAndEstado(String webhookId, String estado);

    /**
     * Eventos pendientes de reconciliación: SIN_CORRELACION con menos de maxIntentos intentos
     * y dentro de la ventana de tiempo indicada, ordenados de más antiguo a más reciente.
     */
    @Query("""
            SELECT w FROM WebhookLog w
            WHERE w.estado = 'SIN_CORRELACION'
              AND w.intentos < :maxIntentos
              AND w.fechaProcesamiento >= :desde
            ORDER BY w.fechaProcesamiento ASC
            """)
    List<WebhookLog> findSinCorrelacionPendientes(
            @Param("maxIntentos") int maxIntentos,
            @Param("desde") LocalDateTime desde
    );

    /**
     * Eventos cuya operación de branch (bloqueo/desbloqueo de sucursales) falló
     * después de que el email ya fue enviado. El reconciliador reintenta solo el branch op.
     */
    @Query("""
            SELECT w FROM WebhookLog w
            WHERE w.estado = 'PROCESADO_PARCIAL'
              AND w.intentos < :maxIntentos
              AND w.fechaProcesamiento >= :desde
            ORDER BY w.fechaProcesamiento ASC
            """)
    List<WebhookLog> findProcesadoParcialPendientes(
            @Param("maxIntentos") int maxIntentos,
            @Param("desde") LocalDateTime desde
    );
}