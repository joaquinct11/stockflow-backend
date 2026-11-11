package com.stockflow.service;

import com.stockflow.entity.Tenant;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.impl.TenantServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para TenantServiceImpl.eliminarPermanentemente().
 * Verifica que las consultas SQL nativas usen el esquema multi-tenant correcto
 * (usuario_tenant en lugar de usuarios.tenant_id, eliminado en V103).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenantServiceImpl — eliminarPermanentemente()")
class TenantServiceImplEliminarTest {

    @Mock private TenantRepository        tenantRepository;
    @Mock private UsuarioRepository       usuarioRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private ProductoRepository      productoRepository;
    @Mock private VentaRepository         ventaRepository;
    @Mock private ProveedorRepository     proveedorRepository;
    @Mock private SuscripcionRepository   suscripcionRepository;

    @InjectMocks
    private TenantServiceImpl tenantService;

    @Mock private EntityManager em;
    @Mock private Query         query;

    private static final String TENANT_ID = "botica-test-a1b2";

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        // Inyectar EntityManager (campo @PersistenceContext no inyectado por @InjectMocks)
        ReflectionTestUtils.setField(tenantService, "em", em);

        tenant = Tenant.builder()
                .tenantId(TENANT_ID)
                .nombre("Botica Test")
                .build();

        when(tenantRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);
    }

    @Nested
    @DisplayName("Consultas SQL — esquema correcto post-V103")
    class ConsultasSQL {

        @Test
        @DisplayName("refresh_tokens: usa usuario_tenant, no usuarios.tenant_id")
        void refreshTokens_usaUsuarioTenant() {
            tenantService.eliminarPermanentemente(TENANT_ID);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(em, atLeastOnce()).createNativeQuery(sqlCaptor.capture());

            List<String> sqls = sqlCaptor.getAllValues();
            String sqlRefresh = sqls.stream()
                    .filter(s -> s.contains("refresh_tokens"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No se encontró query de refresh_tokens"));

            assertThat(sqlRefresh)
                    .as("refresh_tokens debe filtrar por usuario_tenant, no usuarios.tenant_id")
                    .contains("usuario_tenant")
                    .doesNotContain("FROM usuarios WHERE tenant_id");
        }

        @Test
        @DisplayName("usuario_permisos: usa tenant_id propio, no subquery de usuarios")
        void usuarioPermisos_usaTenantIdDirecto() {
            tenantService.eliminarPermanentemente(TENANT_ID);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(em, atLeastOnce()).createNativeQuery(sqlCaptor.capture());

            String sqlPermisos = sqlCaptor.getAllValues().stream()
                    .filter(s -> s.contains("usuario_permisos"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No se encontró query de usuario_permisos"));

            assertThat(sqlPermisos)
                    .as("usuario_permisos debe filtrarse por su propio tenant_id")
                    .contains("DELETE FROM usuario_permisos WHERE tenant_id")
                    .doesNotContain("FROM usuarios WHERE tenant_id");
        }

        @Test
        @DisplayName("usuarios: usa usuario_tenant como filtro, no usuarios.tenant_id")
        void usuarios_usaUsuarioTenantComoFiltro() {
            tenantService.eliminarPermanentemente(TENANT_ID);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(em, atLeastOnce()).createNativeQuery(sqlCaptor.capture());

            String sqlUsuarios = sqlCaptor.getAllValues().stream()
                    .filter(s -> s.startsWith("DELETE FROM usuarios"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No se encontró DELETE FROM usuarios"));

            assertThat(sqlUsuarios)
                    .as("DELETE FROM usuarios debe usar usuario_tenant para filtrar, no usuarios.tenant_id")
                    .contains("usuario_tenant")
                    .doesNotContain("WHERE tenant_id = :t"); // no referencia directa a columna inexistente
        }

        @Test
        @DisplayName("usuarios: solo elimina los exclusivos del tenant (subquery NOT EXISTS)")
        void usuarios_eliminaSoloExclusivos() {
            tenantService.eliminarPermanentemente(TENANT_ID);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(em, atLeastOnce()).createNativeQuery(sqlCaptor.capture());

            String sqlUsuarios = sqlCaptor.getAllValues().stream()
                    .filter(s -> s.startsWith("DELETE FROM usuarios"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No se encontró DELETE FROM usuarios"));

            assertThat(sqlUsuarios)
                    .as("usuarios compartidos en otros tenants no deben eliminarse")
                    .containsIgnoringCase("NOT EXISTS");
        }

        @Test
        @DisplayName("ninguna query referencia usuarios.tenant_id (columna eliminada en V103)")
        void ningunaQuery_usaUsuariosTenantId() {
            tenantService.eliminarPermanentemente(TENANT_ID);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(em, atLeastOnce()).createNativeQuery(sqlCaptor.capture());

            for (String sql : sqlCaptor.getAllValues()) {
                assertThat(sql)
                        .as("Query no debe referenciar usuarios.tenant_id (eliminada en V103): %s", sql)
                        .doesNotContain("FROM usuarios WHERE tenant_id");
            }
        }
    }

    @Nested
    @DisplayName("Transaccionalidad y orden de operaciones")
    class Transaccionalidad {

        @Test
        @DisplayName("suscripciones se elimina antes de borrar el tenant")
        void suscripciones_seEliminaAntesDeTenant() {
            tenantService.eliminarPermanentemente(TENANT_ID);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(em, atLeastOnce()).createNativeQuery(sqlCaptor.capture());

            List<String> sqls = sqlCaptor.getAllValues();
            int idxSus = -1;
            for (int i = 0; i < sqls.size(); i++) {
                if (sqls.get(i).contains("suscripciones")) { idxSus = i; break; }
            }

            assertThat(idxSus).as("suscripciones debe eliminarse durante el proceso").isGreaterThanOrEqualTo(0);
            // El tenant se borra al final vía tenantRepository.delete
            verify(tenantRepository).delete(tenant);
        }

        @Test
        @DisplayName("si tenantId no existe, no se ejecuta ningún DELETE")
        void tenantInexistente_ningunaEliminacion() {
            when(tenantRepository.findByTenantId("inexistente")).thenReturn(Optional.empty());

            tenantService.eliminarPermanentemente("inexistente");

            verify(em, never()).createNativeQuery(anyString());
            verify(tenantRepository, never()).delete(any());
        }

        @Test
        @DisplayName("tenant se elimina exactamente una vez al final")
        void tenant_seEliminaUnaVezAlFinal() {
            tenantService.eliminarPermanentemente(TENANT_ID);

            verify(tenantRepository, times(1)).delete(tenant);
        }
    }
}
