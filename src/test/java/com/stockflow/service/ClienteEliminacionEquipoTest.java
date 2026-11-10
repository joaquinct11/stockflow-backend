package com.stockflow.service;

import com.stockflow.util.TenantContext;
import com.stockflow.entity.Cliente;
import com.stockflow.exception.ConflictException;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.EquipoClienteRepository;
import com.stockflow.repository.MascotaRepository;
import com.stockflow.service.impl.ClienteServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClienteEliminacionEquipoTest {

    private static final String TENANT = "serv-ab12";
    private static final Long   CLI_1  = 1L;

    @Mock private ClienteRepository clienteRepository;
    @Mock private MascotaRepository mascotaRepository;
    @Mock private EquipoClienteRepository equipoClienteRepository;

    @InjectMocks private ClienteServiceImpl service;

    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = Cliente.builder()
                .id(CLI_1)
                .tenantId(TENANT)
                .nombre("Empresa ABC")
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void eliminarCliente_conEquipos_lanzaConflict() {
        TenantContext.setCurrentTenant(TENANT);
        when(clienteRepository.findByIdAndTenantId(CLI_1, TENANT)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.countByCliente_IdAndTenantId(CLI_1, TENANT)).thenReturn(0L);
        when(equipoClienteRepository.countByCliente_IdAndTenantId(CLI_1, TENANT)).thenReturn(3L);

        assertThatThrownBy(() -> service.eliminarCliente(CLI_1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("equipo");

        verify(clienteRepository, never()).delete(cliente);
    }

    @Test
    void eliminarCliente_sinMascotasNiEquipos_eliminaOk() {
        TenantContext.setCurrentTenant(TENANT);
        when(clienteRepository.findByIdAndTenantId(CLI_1, TENANT)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.countByCliente_IdAndTenantId(CLI_1, TENANT)).thenReturn(0L);
        when(equipoClienteRepository.countByCliente_IdAndTenantId(CLI_1, TENANT)).thenReturn(0L);

        service.eliminarCliente(CLI_1);

        verify(clienteRepository).delete(cliente);
    }

    @Test
    void eliminarCliente_conMascotas_lanzaConflictAntesDeEquipos() {
        TenantContext.setCurrentTenant(TENANT);
        when(clienteRepository.findByIdAndTenantId(CLI_1, TENANT)).thenReturn(Optional.of(cliente));
        when(mascotaRepository.countByCliente_IdAndTenantId(CLI_1, TENANT)).thenReturn(2L);

        assertThatThrownBy(() -> service.eliminarCliente(CLI_1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("mascota");

        verify(clienteRepository, never()).delete(cliente);
        verify(equipoClienteRepository, never()).countByCliente_IdAndTenantId(CLI_1, TENANT);
    }
}
