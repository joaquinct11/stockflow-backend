package com.stockflow.repository;

import com.stockflow.entity.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    List<MovimientoInventario> findByProductoId(Long productoId);

    List<MovimientoInventario> findByUsuarioId(Long usuarioId);

    List<MovimientoInventario> findByTenantId(String tenantId);

    List<MovimientoInventario> findByTipo(String tipo);

    @Query("SELECT m FROM MovimientoInventario m WHERE m.producto.id = :productoId AND m.createdAt BETWEEN :inicio AND :fin")
    List<MovimientoInventario> findMovimientosPorProductoYPeriodo(
            @Param("productoId") Long productoId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin
    );
}