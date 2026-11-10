package com.stockflow.service;

import com.stockflow.util.TenantContext;
import com.stockflow.dto.CrearEquipoRequestDTO;
import com.stockflow.exception.ForbiddenException;
import com.stockflow.mapper.EquipoClienteMapper;
import com.stockflow.repository.ClienteRepository;
import com.stockflow.repository.EquipoClienteRepository;
import com.stockflow.service.impl.EquipoClienteServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EquipoRubroTest {

    private static final String TENANT_VET    = "vet-ca35";
    private static final String RUBRO_EQUIPO  = "EMPRESA_SERVICIOS";

    @Mock private EquipoClienteRepository equipoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private EquipoClienteMapper mapper;
    @Mock private TenantService tenantService;

    @InjectMocks private EquipoClienteServiceImpl service;

    @BeforeEach
    void setUp() {
        doThrow(new ForbiddenException("Módulo no habilitado para este rubro"))
                .when(tenantService).requireRubro(anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getStats_rubroIncorrecto_lanzaForbidden() {
        TenantContext.setCurrentTenant(TENANT_VET);
        assertThatThrownBy(() -> service.getStats())
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listar_rubroIncorrecto_lanzaForbidden() {
        TenantContext.setCurrentTenant(TENANT_VET);
        assertThatThrownBy(() -> service.listar(0, 20, null, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void obtenerPorId_rubroIncorrecto_lanzaForbidden() {
        TenantContext.setCurrentTenant(TENANT_VET);
        assertThatThrownBy(() -> service.obtenerPorId(1L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void crear_rubroIncorrecto_lanzaForbidden() {
        TenantContext.setCurrentTenant(TENANT_VET);
        CrearEquipoRequestDTO req = CrearEquipoRequestDTO.builder()
                .clienteId(1L)
                .nombre("Equipo")
                .build();
        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void activar_rubroIncorrecto_lanzaForbidden() {
        TenantContext.setCurrentTenant(TENANT_VET);
        assertThatThrownBy(() -> service.activar(1L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void desactivar_rubroIncorrecto_lanzaForbidden() {
        TenantContext.setCurrentTenant(TENANT_VET);
        assertThatThrownBy(() -> service.desactivar(1L))
                .isInstanceOf(ForbiddenException.class);
    }
}
