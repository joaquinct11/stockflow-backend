package com.stockflow.service;

import com.stockflow.entity.Cliente;
import com.stockflow.exception.ConflictException;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.MascotaRepository;
import com.stockflow.service.impl.ClienteServiceImpl;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClienteServiceImpl — eliminación con/sin mascotas")
class ClienteEliminacionMascotaTest {

    private static final String TENANT_VET = "vet-ca35";
    private static final Long   CLIENTE_10 = 10L;

    @Mock private ClienteRepository  clienteRepository;
    @Mock private MascotaRepository  mascotaRepository;

    @InjectMocks private ClienteServiceImpl clienteService;

    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = Cliente.builder().id(CLIENTE_10).nombre("Pedro Pérez")
                .tenantId(TENANT_VET).activo(true).build();
        when(clienteRepository.findByIdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(Optional.of(cliente));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── N: cliente con mascotas → conflicto en delete ─────────────────────────

    @Test
    @DisplayName("N — cliente con mascotas → ConflictException con mensaje descriptivo")
    void eliminarCliente_conMascotas_lanzaConflict() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.countByCliente_IdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(3L);

        assertThatThrownBy(() -> clienteService.eliminarCliente(CLIENTE_10))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("mascota");

        verify(clienteRepository, never()).delete(any(Cliente.class));
    }

    // ── O: cliente sin mascotas → mantiene comportamiento existente ───────────

    @Test
    @DisplayName("O — cliente sin mascotas → delete normal sin excepción")
    void eliminarCliente_sinMascotas_deleteProcede() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(mascotaRepository.countByCliente_IdAndTenantId(CLIENTE_10, TENANT_VET))
                .thenReturn(0L);

        assertThatCode(() -> clienteService.eliminarCliente(CLIENTE_10))
                .doesNotThrowAnyException();

        verify(clienteRepository).delete(cliente);
    }
}
