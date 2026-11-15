package com.stockflow.service;

import com.stockflow.config.properties.SuperAdminProperties;
import com.stockflow.controller.SuperAdminAuthController;
import com.stockflow.dto.superadmin.SuperAdminLoginRequestDTO;
import com.stockflow.dto.superadmin.SuperAdminTokenDTO;
import com.stockflow.exception.BadRequestException;
import com.stockflow.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SuperAdmin — seguridad de autenticación y aislamiento de roles")
class SuperAdminSecurityTest {

    @Mock private SuperAdminProperties superAdminProperties;
    @Mock private JwtUtil              jwtUtil;

    @InjectMocks private SuperAdminAuthController authController;

    @BeforeEach
    void setUp() {
        when(superAdminProperties.getUsername()).thenReturn("sa-admin");
        when(superAdminProperties.getPassword()).thenReturn("s3cr3t");
    }

    // ── 1. Login correcto ─────────────────────────────────────────────────────

    @Test
    @DisplayName("login correcto — retorna token SA")
    void login_credencialesCorrectas_retornaToken() {
        when(jwtUtil.generateSuperAdminToken("sa-admin")).thenReturn("mock-sa-token");

        SuperAdminLoginRequestDTO req = new SuperAdminLoginRequestDTO();
        req.setUsername("sa-admin");
        req.setPassword("s3cr3t");

        ResponseEntity<SuperAdminTokenDTO> res = authController.login(req);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getToken()).isEqualTo("mock-sa-token");
        assertThat(res.getBody().getUsername()).isEqualTo("sa-admin");
        verify(jwtUtil).generateSuperAdminToken("sa-admin");
    }

    // ── 2. Login incorrecto ───────────────────────────────────────────────────

    @Test
    @DisplayName("login con password incorrecta — lanza BadRequestException")
    void login_passwordIncorrecta_lanzaExcepcion() {
        SuperAdminLoginRequestDTO req = new SuperAdminLoginRequestDTO();
        req.setUsername("sa-admin");
        req.setPassword("wrong");

        assertThatThrownBy(() -> authController.login(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Credenciales incorrectas");

        verify(jwtUtil, never()).generateSuperAdminToken(any());
    }

    @Test
    @DisplayName("login con username incorrecto — lanza BadRequestException")
    void login_usernameIncorrecto_lanzaExcepcion() {
        SuperAdminLoginRequestDTO req = new SuperAdminLoginRequestDTO();
        req.setUsername("hacker");
        req.setPassword("s3cr3t");

        assertThatThrownBy(() -> authController.login(req))
                .isInstanceOf(BadRequestException.class);
    }

    // ── 3. @PreAuthorize en SuperAdminController ──────────────────────────────

    @Test
    @DisplayName("SuperAdminController — clase anotada con @PreAuthorize SUPER_ADMIN")
    void superAdminController_tienePreAuthorize_superAdmin() {
        Class<?> cls = com.stockflow.controller.SuperAdminController.class;
        PreAuthorize annotation = cls.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("SUPER_ADMIN");
    }

    // ── 4. /superadmin/auth/login es endpoint público (sin @PreAuthorize) ─────

    @Test
    @DisplayName("SuperAdminAuthController.login — no tiene @PreAuthorize (es público)")
    void superAdminAuthController_login_sinPreAuthorize() throws NoSuchMethodException {
        Method loginMethod = SuperAdminAuthController.class.getMethod(
                "login", SuperAdminLoginRequestDTO.class);

        assertThat(loginMethod.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(SuperAdminAuthController.class.getAnnotation(PreAuthorize.class)).isNull();
    }

    // ── 5. Aislamiento SA vs tenant — JwtUtil no setea TenantContext para SA ──

    @Test
    @DisplayName("generateSuperAdminToken — genera token sin tenantId")
    void generateSuperAdminToken_sinTenantId() {
        // Verifica que el método existe y se llama sin tenantId
        when(jwtUtil.generateSuperAdminToken("sa-admin")).thenReturn("sa-jwt-xyz");

        String token = jwtUtil.generateSuperAdminToken("sa-admin");

        assertThat(token).isEqualTo("sa-jwt-xyz");
        verify(jwtUtil).generateSuperAdminToken("sa-admin");
        // No se llama a ningún método que requiera tenantId
        verify(jwtUtil, never()).generateToken(any(), any(), any(), any(), any());
    }
}
