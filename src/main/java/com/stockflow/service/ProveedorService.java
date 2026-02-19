package com.stockflow.service;

import com.stockflow.entity.Proveedor;

import java.util.List;
import java.util.Optional;

public interface ProveedorService {

    Proveedor crearProveedor(Proveedor proveedor);

    Optional<Proveedor> obtenerProveedorPorId(Long id);

    Optional<Proveedor> obtenerProveedorPorRuc(String ruc);

    List<Proveedor> buscarProveedoresPorNombre(String nombre);

    List<Proveedor> obtenerProveedoresPorTenant(String tenantId);

    List<Proveedor> obtenerProveedoresActivos();

    List<Proveedor> obtenerTodosProveedores();

    Proveedor actualizarProveedor(Long id, Proveedor proveedorActualizado);

    Proveedor activarProveedor(Long id);

    Proveedor desactivarProveedor(Long id);

    void eliminarProveedor(Long id);
}