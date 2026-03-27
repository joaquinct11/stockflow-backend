package com.stockflow.repository;

import com.stockflow.entity.Comprobante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    List<Comprobante> findByTenantId(String tenantId);

    Optional<Comprobante> findByVentaIdAndTenantId(Long ventaId, String tenantId);

    boolean existsByVentaIdAndTenantIdAndEstadoNot(Long ventaId, String tenantId, String estado);

    @Query(value = """
      SELECT *
      FROM comprobantes c
      WHERE c.tenant_id = :tenantId
        AND (:tipo IS NULL OR c.tipo = :tipo)
        AND (:estado IS NULL OR c.estado = :estado)
        AND (CAST(:from AS timestamp) IS NULL OR c.fecha_emision >= :from)
        AND (CAST(:to AS timestamp) IS NULL OR c.fecha_emision <= :to)
        AND (:ventaId IS NULL OR c.venta_id = :ventaId)
        AND (
          CAST(:search AS text) = '' OR
          COALESCE(c.numero,'') ILIKE CONCAT('%', CAST(:search AS text), '%') OR
          COALESCE(c.receptor_nombre,'') ILIKE CONCAT('%', CAST(:search AS text), '%') OR
          COALESCE(c.receptor_doc_numero,'') ILIKE CONCAT('%', CAST(:search AS text), '%')
        )
      ORDER BY c.fecha_emision DESC
    """, nativeQuery = true)
    List<Comprobante> findFiltered(
            @Param("tenantId") String tenantId,
            @Param("tipo") String tipo,
            @Param("estado") String estado,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("ventaId") Long ventaId,
            @Param("search") String search
    );
}
