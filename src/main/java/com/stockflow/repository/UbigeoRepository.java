package com.stockflow.repository;

import com.stockflow.entity.Ubigeo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UbigeoRepository extends JpaRepository<Ubigeo, String> {

    @Query("SELECT DISTINCT u.departamento FROM Ubigeo u ORDER BY u.departamento")
    List<String> findAllDepartamentos();

    @Query("SELECT DISTINCT u.provincia FROM Ubigeo u WHERE u.departamento = :departamento ORDER BY u.provincia")
    List<String> findProvinciasByDepartamento(String departamento);

    @Query("SELECT u FROM Ubigeo u WHERE u.departamento = :departamento AND u.provincia = :provincia ORDER BY u.distrito")
    List<Ubigeo> findByDepartamentoAndProvincia(String departamento, String provincia);

    Optional<Ubigeo> findByCodigo(String codigo);
}
