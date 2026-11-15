package com.stockflow.repository;

import com.stockflow.entity.OtNumeracion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OtNumeracionRepository extends JpaRepository<OtNumeracion, Long> {

    /**
     * Garantiza que exista un registro para (tenant_id, anio) antes de adquirir el lock.
     * ON CONFLICT DO NOTHING es atómico en PostgreSQL: si dos threads llegan simultáneamente
     * al primer registro del año, solo uno inserta y el otro hace DO NOTHING sin error.
     */
    @Modifying
    @Query(value = """
            INSERT INTO ot_numeracion (tenant_id, anio, ultimo)
            VALUES (:tenantId, :anio, 0)
            ON CONFLICT (tenant_id, anio) DO NOTHING
            """, nativeQuery = true)
    void insertarSiNoExiste(@Param("tenantId") String tenantId, @Param("anio") short anio);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM OtNumeracion n WHERE n.tenantId = :tenantId AND n.anio = :anio")
    Optional<OtNumeracion> findByTenantIdAndAnioForUpdate(
            @Param("tenantId") String tenantId,
            @Param("anio") Short anio);
}
