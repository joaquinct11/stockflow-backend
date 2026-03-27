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

    @Query("SELECT c FROM Comprobante c WHERE c.tenantId = :tenantId " +
           "AND (:tipo IS NULL OR c.tipo = :tipo) " +
           "AND (:estado IS NULL OR c.estado = :estado) " +
           "AND (:from IS NULL OR c.fechaEmision >= :from) " +
           "AND (:to IS NULL OR c.fechaEmision <= :to) " +
           "AND (:ventaId IS NULL OR c.venta.id = :ventaId) " +
           "AND (:search IS NULL OR LOWER(c.numero) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(c.receptorNombre) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(c.receptorDocNumero) LIKE LOWER(CONCAT('%', :search, '%')))")
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
