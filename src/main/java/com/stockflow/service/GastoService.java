package com.stockflow.service;

import com.stockflow.entity.Gasto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GastoService {

    List<Gasto> obtenerTodos(String tenantId);

    List<Gasto> obtenerActivos(String tenantId);

    List<Gasto> obtenerPorCategoria(String tenantId, String categoria);

    List<Gasto> obtenerPorRangoFecha(String tenantId, LocalDate inicio, LocalDate fin);

    List<Gasto> buscar(String tenantId, String q);

    Optional<Gasto> obtenerPorId(Long id);

    Gasto crear(Gasto gasto);

    Gasto actualizar(Long id, Gasto gasto);

    void eliminar(Long id);

    BigDecimal totalPorPeriodo(String tenantId, LocalDate inicio, LocalDate fin);

    long contar(String tenantId);
}
