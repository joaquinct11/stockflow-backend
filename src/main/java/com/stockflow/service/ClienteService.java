package com.stockflow.service;

import com.stockflow.entity.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClienteService {

    Cliente crearCliente(Cliente cliente);

    Optional<Cliente> obtenerClientePorId(Long id);

    List<Cliente> buscarClientesPorNombre(String nombre, String tenantId);

    List<Cliente> buscarClientesPorDocumento(String numeroDocumento, String tenantId);

    Cliente actualizarCliente(Long id, Cliente cliente);

    Cliente activarCliente(Long id);

    Cliente desactivarCliente(Long id);

    void eliminarCliente(Long id);

    List<Cliente> obtenerClientesPorTenant(String tenantId);

    List<Cliente> obtenerClientesActivosPorTenant(String tenantId);
}
