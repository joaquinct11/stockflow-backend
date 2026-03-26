package com.stockflow.repository;

import com.stockflow.entity.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface PermisoRepository extends JpaRepository<Permiso, Long> {
    Optional<Permiso> findByNombre(String nombre);
    List<Permiso> findByRolId(Long rolId);
    List<Permiso> findByNombreIn(List<String> nombres);

    @Query("SELECT p.nombre FROM Permiso p WHERE p.rol.nombre = :rolNombre")
    List<String> findNombresByRolNombre(@Param("rolNombre") String rolNombre);
}