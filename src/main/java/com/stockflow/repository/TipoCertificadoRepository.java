package com.stockflow.repository;

import com.stockflow.entity.TipoCertificado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TipoCertificadoRepository extends JpaRepository<TipoCertificado, Long> {

    List<TipoCertificado> findByActivoTrueOrderByNombreAsc();
}
