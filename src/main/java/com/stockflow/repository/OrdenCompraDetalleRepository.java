package com.stockflow.repository;

import com.stockflow.entity.OrdenCompraDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrdenCompraDetalleRepository extends JpaRepository<OrdenCompraDetalle, Long> {

    List<OrdenCompraDetalle> findByOrdenCompraId(Long ordenCompraId);

    /**
     * Returns the total quantity received (from CONFIRMED recepciones) for a
     * specific producto within a given orden_compra.
     */
    @Query("""
            SELECT COALESCE(SUM(rd.cantidadRecibida), 0)
            FROM RecepcionDetalle rd
            WHERE rd.recepcion.ordenCompra.id = :ocId
              AND rd.producto.id = :productoId
              AND rd.recepcion.estado = 'CONFIRMADA'
            """)
    Integer totalRecibidoPorOcYProducto(@Param("ocId") Long ocId, @Param("productoId") Long productoId);
}
