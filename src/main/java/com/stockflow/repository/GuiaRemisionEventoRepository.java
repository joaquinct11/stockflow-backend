package com.stockflow.repository;

import com.stockflow.entity.gre.GuiaRemisionEvento;
import com.stockflow.entity.gre.TipoEventoGRE;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GuiaRemisionEventoRepository extends JpaRepository<GuiaRemisionEvento, Long> {
    List<GuiaRemisionEvento> findByGuiaRemisionIdOrderByCreatedAtAsc(Long guiaRemisionId);

    /** Verifica si la GRE tiene al menos un evento del tipo dado (ej. EMISION_INICIADA). */
    boolean existsByGuiaRemisionIdAndTipoEvento(Long guiaRemisionId, TipoEventoGRE tipoEvento);

    /** Eventos GRE recientes para actividad reciente (sin filtro de sucursal). JOIN FETCH evita N+1 sobre guiaRemision. */
    @Query("SELECT e FROM GuiaRemisionEvento e JOIN FETCH e.guiaRemision g WHERE e.tenantId = :tenantId " +
           "AND e.tipoEvento IN :tipos ORDER BY e.createdAt DESC")
    List<GuiaRemisionEvento> findTopNRecentesParaActividad(
            @Param("tenantId") String tenantId,
            @Param("tipos") List<TipoEventoGRE> tipos,
            Pageable pageable);

    /** Eventos GRE recientes filtrados por sucursal (origen O destino). JOIN FETCH evita N+1 sobre guiaRemision. */
    @Query("SELECT e FROM GuiaRemisionEvento e JOIN FETCH e.guiaRemision g WHERE e.tenantId = :tenantId " +
           "AND e.tipoEvento IN :tipos " +
           "AND (g.sucursalOrigen.id = :sucursalId OR g.sucursalDestino.id = :sucursalId) " +
           "ORDER BY e.createdAt DESC")
    List<GuiaRemisionEvento> findTopNRecentesParaActividadBySucursal(
            @Param("tenantId") String tenantId,
            @Param("tipos") List<TipoEventoGRE> tipos,
            @Param("sucursalId") Long sucursalId,
            Pageable pageable);
}
