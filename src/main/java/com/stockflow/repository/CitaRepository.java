package com.stockflow.repository;

import com.stockflow.entity.Cita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CitaRepository extends JpaRepository<Cita, Long>, JpaSpecificationExecutor<Cita> {

    Optional<Cita> findByIdAndTenantId(Long id, String tenantId);

    /**
     * Detecta solapamiento de horario para un profesional.
     * Solo considera citas activas (excluye CANCELADA y NO_ASISTIO).
     * Ignora la cita con id = excludeId (útil en actualización).
     */
    @Query("""
           SELECT c FROM Cita c
           WHERE c.tenantId = :tenantId
             AND c.usuarioTenantId = :profesionalId
             AND c.estado NOT IN ('CANCELADA', 'NO_ASISTIO')
             AND c.id <> :excludeId
             AND c.fechaInicio < :fechaFin
             AND c.fechaFin   > :fechaInicio
           """)
    List<Cita> findSolapamiento(
            @Param("tenantId")       String        tenantId,
            @Param("profesionalId")  Long          profesionalId,
            @Param("fechaInicio")    LocalDateTime fechaInicio,
            @Param("fechaFin")       LocalDateTime fechaFin,
            @Param("excludeId")      Long          excludeId
    );

    // Listado paginado con filtros: usar CitaSpecification.build() + findAll(spec, pageable)
}
