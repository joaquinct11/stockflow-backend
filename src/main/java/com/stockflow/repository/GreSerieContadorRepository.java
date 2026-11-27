package com.stockflow.repository;

import com.stockflow.entity.gre.GreSerieContador;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GreSerieContadorRepository
        extends JpaRepository<GreSerieContador, GreSerieContador.GreSerieContadorId> {

    /**
     * Crea la fila del contador si no existe (ON CONFLICT DO NOTHING).
     * Es atómica: varios hilos concurrentes pueden llamarla; solo el primero
     * insertará, los demás harán DO NOTHING sin error.
     */
    @Modifying
    @Query(value = "INSERT INTO gre_serie_contador (tenant_id, serie, ultimo) " +
                   "VALUES (:tenantId, :serie, :baseline) " +
                   "ON CONFLICT (tenant_id, serie) DO NOTHING",
           nativeQuery = true)
    void insertIfAbsent(@Param("tenantId") String tenantId,
                        @Param("serie")    String serie,
                        @Param("baseline") int    baseline);

    /**
     * Carga el contador con bloqueo pesimista (SELECT FOR UPDATE).
     * Usar después de {@link #insertIfAbsent} para garantizar que la fila existe.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM GreSerieContador c WHERE c.tenantId = :tenantId AND c.serie = :serie")
    Optional<GreSerieContador> findByTenantIdAndSerieForUpdate(
            @Param("tenantId") String tenantId,
            @Param("serie")    String serie);
}
