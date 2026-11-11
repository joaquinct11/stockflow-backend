package com.stockflow.repository;

import com.stockflow.entity.Cita;
import com.stockflow.entity.EstadoCita;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CitaSpecification {

    private CitaSpecification() {}

    public static Specification<Cita> build(
            String        tenantId,
            EstadoCita    estado,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            Long          clienteId,
            Long          mascotaId,
            Long          usuarioTenantId,
            Long          sucursalId,
            String        q
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (estado != null) {
                predicates.add(cb.equal(root.get("estado"), estado));
            }
            if (fechaDesde != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fechaInicio"), fechaDesde));
            }
            if (fechaHasta != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fechaInicio"), fechaHasta));
            }
            if (clienteId != null) {
                predicates.add(cb.equal(root.get("clienteId"), clienteId));
            }
            if (mascotaId != null) {
                predicates.add(cb.equal(root.get("mascotaId"), mascotaId));
            }
            if (usuarioTenantId != null) {
                predicates.add(cb.equal(root.get("usuarioTenantId"), usuarioTenantId));
            }
            if (sucursalId != null) {
                predicates.add(cb.equal(root.get("sucursalId"), sucursalId));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("id").as(String.class), "%" + q + "%"),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("motivo"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("observaciones"), "")), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
