package com.stockflow.service;

import com.stockflow.entity.Proveedor;
import java.util.List;
import java.util.Optional;

public interface ProveedorService {

    Proveedor crearProveedor(Proveedor proveedor);

    Optional<Proveedor> obtenerProveedorPorId(Long id);

    Optional<Proveedor> obtenerProveedorPorRuc(String ruc);

    List<Proveedor> buscarProveedoresPorNombre(String nombre);

    Proveedor actualizarProveedor(Long id, Proveedor proveedor);

    Proveedor activarProveedor(Long id);

    Proveedor desactivarProveedor(Long id);

    void eliminarProveedor(Long id);

    List<Proveedor> obtenerProveedoresPorTenant(String tenantId);

    List<Proveedor> obtenerProveedoresActivosPorTenant(String tenantId);
}
