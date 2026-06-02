package com.stockflow.service.impl;

import com.stockflow.entity.Gasto;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.GastoRepository;
import com.stockflow.service.GastoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GastoServiceImpl implements GastoService {

    private final GastoRepository gastoRepository;

    @Override
    public List<Gasto> obtenerTodos(String tenantId) {
        return gastoRepository.findByTenantIdAndDeletedAtIsNullOrderByFechaGastoDesc(tenantId);
    }

    @Override
    public List<Gasto> obtenerActivos(String tenantId) {
        return gastoRepository.findByTenantIdAndActivoTrueAndDeletedAtIsNullOrderByFechaGastoDesc(tenantId);
    }

    @Override
    public List<Gasto> obtenerPorCategoria(String tenantId, String categoria) {
        return gastoRepository.findByTenantIdAndCategoriaAndDeletedAtIsNullOrderByFechaGastoDesc(tenantId, categoria.toUpperCase());
    }

    @Override
    public List<Gasto> obtenerPorRangoFecha(String tenantId, LocalDate inicio, LocalDate fin) {
        return gastoRepository.findByTenantIdAndFechaGastoBetween(tenantId, inicio, fin);
    }

    @Override
    public List<Gasto> buscar(String tenantId, String q) {
        return gastoRepository.searchByConcepto(tenantId, q);
    }

    @Override
    public Optional<Gasto> obtenerPorId(Long id) {
        return gastoRepository.findById(id)
                .filter(g -> g.getDeletedAt() == null);
    }

    @Override
    @Transactional
    public Gasto crear(Gasto gasto) {
        gasto.setCreatedAt(LocalDateTime.now());
        gasto.setActivo(true);
        log.info("💸 Registrando gasto '{}' de S/ {} para tenant {}", gasto.getConcepto(), gasto.getMonto(), gasto.getTenantId());
        return gastoRepository.save(gasto);
    }

    @Override
    @Transactional
    public Gasto actualizar(Long id, Gasto gastoActualizado) {
        Gasto gasto = gastoRepository.findById(id)
                .filter(g -> g.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado con ID: " + id));

        gasto.setConcepto(gastoActualizado.getConcepto());
        gasto.setCategoria(gastoActualizado.getCategoria());
        gasto.setMonto(gastoActualizado.getMonto());
        gasto.setFechaGasto(gastoActualizado.getFechaGasto());
        gasto.setMetodoPago(gastoActualizado.getMetodoPago());
        gasto.setNumeroComprobante(gastoActualizado.getNumeroComprobante());
        gasto.setNotas(gastoActualizado.getNotas());

        log.info("✏️ Actualizando gasto ID: {}", id);
        return gastoRepository.save(gasto);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Gasto gasto = gastoRepository.findById(id)
                .filter(g -> g.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado con ID: " + id));
        gasto.setDeletedAt(LocalDateTime.now());
        gasto.setActivo(false);
        gastoRepository.save(gasto);
        log.info("🗑️ Gasto ID {} eliminado (soft delete)", id);
    }

    @Override
    public BigDecimal totalPorPeriodo(String tenantId, LocalDate inicio, LocalDate fin) {
        return gastoRepository.sumMontoByTenantIdAndFechaBetween(tenantId, inicio, fin);
    }

    @Override
    public long contar(String tenantId) {
        return gastoRepository.countByTenantIdAndDeletedAtIsNull(tenantId);
    }
}
