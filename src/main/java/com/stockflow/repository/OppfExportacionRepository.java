package com.stockflow.repository;

import com.stockflow.entity.OppfExportacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OppfExportacionRepository extends JpaRepository<OppfExportacion, Long> {

    List<OppfExportacion> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
