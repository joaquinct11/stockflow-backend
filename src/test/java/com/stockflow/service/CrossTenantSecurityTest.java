package com.stockflow.service;

import com.stockflow.entity.Cliente;
import com.stockflow.entity.Gasto;
import com.stockflow.entity.MovimientoInventario;
import com.stockflow.entity.Proveedor;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.GastoRepository;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.service.impl.ClienteServiceImpl;
import com.stockflow.service.impl.GastoServiceImpl;
import com.stockflow.service.impl.MovimientoInventarioServiceImpl;
import com.stockflow.service.impl.ProveedorServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cross-tenant security — operaciones CRUD aisladas por tenant")
class CrossTenantSecurityTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    // ── Repositorios mock ─────────────────────────────────────────────────────
    @Mock private ClienteRepository clienteRepository;
    @Mock private GastoRepository gastoRepository;
    @Mock private ProveedorRepository proveedorRepository;
    @Mock private MovimientoInventarioRepository movimientoRepository;

    // ── Servicios ─────────────────────────────────────────────────────────────
    @InjectMocks private ClienteServiceImpl clienteService;
    @InjectMocks private GastoServiceImpl gastoService;
    @InjectMocks private ProveedorServiceImpl proveedorService;
    @InjectMocks private MovimientoInventarioServiceImpl movimientoService;

    // ── Fixtures ──────────────────────────────────────────────────────────────
    private Cliente clienteTenantA;
    private Gasto gastoTenantA;
    private Proveedor proveedorTenantA;
    private MovimientoInventario movimientoTenantA;

    @BeforeEach
    void setUp() {
        clienteTenantA = new Cliente();
        clienteTenantA.setId(1L);

        gastoTenantA = new Gasto();
        gastoTenantA.setId(1L);

        proveedorTenantA = new Proveedor();
        proveedorTenantA.setId(1L);

        movimientoTenantA = new MovimientoInventario();
        movimientoTenantA.setId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLIENTES
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cliente: usuario del tenant A puede acceder a su propio cliente")
    void obtenerCliente_sameTenat_returnsCliente() {
        TenantContext.setCurrentTenant(TENANT_A);
        when(clienteRepository.findByIdAndTenantId(1L, TENANT_A))
                .thenReturn(Optional.of(clienteTenantA));

        Optional<Cliente> result = clienteService.obtenerClientePorId(1L);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Cliente: usuario del tenant B NO puede acceder a un cliente del tenant A")
    void obtenerCliente_differentTenant_returnsEmpty() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(clienteRepository.findByIdAndTenantId(1L, TENANT_B))
                .thenReturn(Optional.empty());

        Optional<Cliente> result = clienteService.obtenerClientePorId(1L);

        assertThat(result).isEmpty();
        verify(clienteRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Cliente: modificar cliente de otro tenant lanza ResourceNotFoundException")
    void actualizarCliente_differentTenant_throwsNotFound() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(clienteRepository.findByIdAndTenantId(1L, TENANT_B))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.actualizarCliente(1L, new Cliente()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Cliente: eliminar cliente de otro tenant lanza ResourceNotFoundException")
    void eliminarCliente_differentTenant_throwsNotFound() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(clienteRepository.findByIdAndTenantId(1L, TENANT_B))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.eliminarCliente(1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(clienteRepository, never()).deleteById(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GASTOS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Gasto: usuario puede acceder a su propio gasto")
    void obtenerGasto_sameTenant_returnsGasto() {
        TenantContext.setCurrentTenant(TENANT_A);
        when(gastoRepository.findByIdAndTenantIdAndDeletedAtIsNull(1L, TENANT_A))
                .thenReturn(Optional.of(gastoTenantA));

        Optional<Gasto> result = gastoService.obtenerPorId(1L);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Gasto: usuario NO puede acceder a un gasto de otro tenant")
    void obtenerGasto_differentTenant_returnsEmpty() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(gastoRepository.findByIdAndTenantIdAndDeletedAtIsNull(1L, TENANT_B))
                .thenReturn(Optional.empty());

        Optional<Gasto> result = gastoService.obtenerPorId(1L);

        assertThat(result).isEmpty();
        verify(gastoRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Gasto: modificar gasto de otro tenant lanza ResourceNotFoundException")
    void actualizarGasto_differentTenant_throwsNotFound() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(gastoRepository.findByIdAndTenantIdAndDeletedAtIsNull(1L, TENANT_B))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gastoService.actualizar(1L, new Gasto()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Gasto: eliminar gasto de otro tenant lanza ResourceNotFoundException")
    void eliminarGasto_differentTenant_throwsNotFound() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(gastoRepository.findByIdAndTenantIdAndDeletedAtIsNull(1L, TENANT_B))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gastoService.eliminar(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROVEEDORES
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Proveedor: usuario puede acceder a su propio proveedor")
    void obtenerProveedor_sameTenant_returnsProveedor() {
        TenantContext.setCurrentTenant(TENANT_A);
        when(proveedorRepository.findByIdAndTenantId(1L, TENANT_A))
                .thenReturn(Optional.of(proveedorTenantA));

        Optional<Proveedor> result = proveedorService.obtenerProveedorPorId(1L);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Proveedor: usuario NO puede acceder a un proveedor de otro tenant")
    void obtenerProveedor_differentTenant_returnsEmpty() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(proveedorRepository.findByIdAndTenantId(1L, TENANT_B))
                .thenReturn(Optional.empty());

        Optional<Proveedor> result = proveedorService.obtenerProveedorPorId(1L);

        assertThat(result).isEmpty();
        verify(proveedorRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Proveedor: modificar proveedor de otro tenant lanza ResourceNotFoundException")
    void actualizarProveedor_differentTenant_throwsNotFound() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(proveedorRepository.findByIdAndTenantId(1L, TENANT_B))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> proveedorService.actualizarProveedor(1L, new Proveedor()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Proveedor: eliminar proveedor de otro tenant lanza ResourceNotFoundException")
    void eliminarProveedor_differentTenant_throwsNotFound() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(proveedorRepository.findByIdAndTenantId(1L, TENANT_B))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> proveedorService.eliminarProveedor(1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(proveedorRepository, never()).deleteById(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MOVIMIENTOS DE INVENTARIO
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MovimientoInventario: usuario puede acceder a su propio movimiento")
    void obtenerMovimiento_sameTenant_returnsMovimiento() {
        TenantContext.setCurrentTenant(TENANT_A);
        when(movimientoRepository.findByIdAndTenantId(1L, TENANT_A))
                .thenReturn(Optional.of(movimientoTenantA));

        Optional<MovimientoInventario> result = movimientoService.obtenerMovimientoPorId(1L);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("MovimientoInventario: usuario NO puede acceder a un movimiento de otro tenant")
    void obtenerMovimiento_differentTenant_returnsEmpty() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(movimientoRepository.findByIdAndTenantId(1L, TENANT_B))
                .thenReturn(Optional.empty());

        Optional<MovimientoInventario> result = movimientoService.obtenerMovimientoPorId(1L);

        assertThat(result).isEmpty();
        verify(movimientoRepository, never()).findById(any());
    }

    @Test
    @DisplayName("MovimientoInventario: eliminar movimiento de otro tenant lanza ResourceNotFoundException")
    void eliminarMovimiento_differentTenant_throwsNotFound() {
        TenantContext.setCurrentTenant(TENANT_B);
        when(movimientoRepository.findByIdAndTenantId(1L, TENANT_B))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> movimientoService.eliminarMovimiento(1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(movimientoRepository, never()).deleteById(any());
    }
}
