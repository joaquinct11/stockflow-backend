package com.stockflow.service.impl;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.dto.*;
import com.stockflow.entity.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.UnauthorizedException;
import com.stockflow.repository.RolRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.config.properties.JwtProperties;
import com.stockflow.service.*;
import com.stockflow.util.JwtUtil;
import com.stockflow.util.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioTenantRepository usuarioTenantRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final TenantService tenantService;
    private final SuscripcionService suscripcionService;
    private final EmailService emailService;
    private final com.stockflow.service.UsuarioPermisoService usuarioPermisoService;
    private final RolePermissionDefaults rolePermissionDefaults;
    private final com.stockflow.config.properties.CulqiProperties culqiProperties;
    private final SucursalService sucursalService;

    @Override
    @Transactional
    public JwtResponseDTO login(LoginDTO loginDTO) {
        log.info("🔐 Login: {}", loginDTO.getEmail());

        Usuario usuario = usuarioRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Email o contraseña incorrectos"));

        if (!usuario.getActivo()) {
            throw new UnauthorizedException("Usuario inactivo - Suscripción vencida o cancelada");
        }

        if (!passwordEncoder.matches(loginDTO.getContraseña(), usuario.getContraseña())) {
            throw new UnauthorizedException("Email o contraseña incorrectos");
        }

        usuario.setUltimoLogin(LocalDateTime.now());
        usuarioRepository.save(usuario);

        // Determinar tenants activos del usuario (fuente de verdad: usuario_tenant)
        List<UsuarioTenant> tenantsActivos = usuarioTenantRepository.findActivosConRolByUsuarioId(usuario.getId());

        if (tenantsActivos.isEmpty()) {
            throw new UnauthorizedException("El usuario no está activo en ningún negocio");
        }

        if (tenantsActivos.size() > 1) {
            // Múltiples tenants → devolver selectionToken + lista para el selector
            log.info("🏢 Usuario {} tiene {} tenants — requiere selector", usuario.getEmail(), tenantsActivos.size());
            String selectionToken = jwtUtil.generateSelectionToken(
                    usuario.getId(), usuario.getEmail(), usuario.getNombre());

            List<TenantInfoDTO> tenantInfoList = tenantsActivos.stream()
                    .map(ut -> buildTenantInfo(ut))
                    .toList();

            return JwtResponseDTO.builder()
                    .selectionToken(selectionToken)
                    .tenants(tenantInfoList)
                    .usuarioId(usuario.getId())
                    .email(usuario.getEmail())
                    .nombre(usuario.getNombre())
                    .build();
        }

        // Tenant único
        UsuarioTenant ut = tenantsActivos.get(0);
        return buildFullJwtResponse(usuario, ut.getTenantId(), ut.getRol().getNombre());
    }

    @Override
    @Transactional
    public JwtResponseDTO registrar(RegistrationRequestDTO request) {
        log.info("📝 Iniciando registro de nuevo usuario: {}", request.getEmail());

        // 1. Validar que el email no exista
        if (usuarioRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("⚠️ Email ya registrado: {}", request.getEmail());
            throw new ConflictException("El email ya está registrado");
        }

        // 2. Crear TENANT
        Tenant tenant = tenantService.crearTenant(
                request.getNombreFarmacia(),
                request.getRubro(),
                request.getRucEmpresa(),
                request.getEmail(),
                request.getNumeroCelular()
        );
        log.info("✅ Tenant creado: {}", tenant.getTenantId());

        // 3. Crear USUARIO (identidad global) + ADMIN en UsuarioTenant
        Rol rolAdmin = rolRepository.findByNombre("ADMIN")
                .orElseThrow(() -> new BadRequestException("Rol ADMIN no encontrado"));

        Usuario usuario = Usuario.builder()
                .email(request.getEmail())
                .contraseña(passwordEncoder.encode(request.getContraseña()))
                .nombre(request.getNombre())
                .activo(true)
                .ultimoLogin(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .apellido(request.getApellido())
                .tipoDocumento(request.getTipoDocumento())
                .numeroDocumento(request.getNumeroDocumento())
                .numeroCelular(request.getNumeroCelular())
                .build();

        Usuario usuarioCreado = usuarioRepository.save(usuario);
        log.info("✅ Usuario creado: {}", usuarioCreado.getEmail());

        // Relación propietario ↔ tenant (fuente de verdad para rol y tenant)
        UsuarioTenant usuarioTenant = UsuarioTenant.builder()
                .usuario(usuarioCreado)
                .tenantId(tenant.getTenantId())
                .rol(rolAdmin)
                .activo(true)
                .build();
        usuarioTenantRepository.save(usuarioTenant);
        log.info("✅ UsuarioTenant creado: {} → tenant:{}", usuarioCreado.getEmail(), tenant.getTenantId());

        // 4. Crear SUSCRIPCIÓN en período de prueba de 14 días
        BigDecimal precioMensual = obtenerPrecioPlan(request.getPlanId());
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime trialEnd = ahora.plusDays(14);

        Suscripcion suscripcion = Suscripcion.builder()
                .usuarioPrincipal(usuarioCreado)
                .planId(request.getPlanId())
                .precioMensual(precioMensual)
                .estado("TRIAL")
                .trialEndDate(trialEnd)
                .tenantId(tenant.getTenantId())
                .fechaInicio(ahora)
                .fechaProximoCobro(trialEnd)
                .build();

        Suscripcion suscripcionCreada = suscripcionService.crearSuscripcion(suscripcion);
        log.info("✅ Suscripción creada: Plan {} para usuario {}",
                suscripcionCreada.getPlanId(), usuarioCreado.getEmail());

        // 5. Si se registra directamente con Plan PRO, crear sucursal principal
        if ("PRO".equals(request.getPlanId())) {
            sucursalService.inicializarPrincipal(tenant.getTenantId());
            log.info("✅ Sucursal principal inicializada para registro PRO: {}", tenant.getTenantId());
        }

        // 7. Generar tokens JWT (rol desde usuario_tenant)
        String accessToken = jwtUtil.generateToken(
                usuarioCreado.getId(),
                usuarioCreado.getEmail(),
                usuarioCreado.getNombre(),
                rolAdmin.getNombre(),
                tenant.getTenantId()
        );

        RefreshToken refreshToken = refreshTokenService.crearRefreshToken(usuarioCreado, tenant.getTenantId());

        log.info("✅ Registro completado exitosamente para: {}", request.getEmail());

        // 8. Enviar email de bienvenida (async — no bloquea)
        try {
            emailService.enviarBienvenida(
                    usuarioCreado.getEmail(),
                    request.getNombreFarmacia(),
                    usuarioCreado.getNombre()
            );
        } catch (Exception e) {
            log.error("❌ Error enviando email de bienvenida: {}", e.getMessage(), e);
        }

        // 7. Retornar respuesta
        return JwtResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tipo("Bearer")
                .usuarioId(usuarioCreado.getId())
                .email(usuarioCreado.getEmail())
                .nombre(usuarioCreado.getNombre())
                .rol(rolAdmin.getNombre())
                .tenantId(tenant.getTenantId())
                .expiresIn((int) (jwtProperties.getExpiration() / 1000))
                .suscripcion(mapToSuscripcionDTO(suscripcionCreada))
                .build();
    }

    @Override
    @Transactional
    public JwtResponseDTO refresh(String refreshTokenString) {
        RefreshToken refreshToken = refreshTokenService.validarRefreshToken(refreshTokenString);
        Usuario usuario = refreshToken.getUsuario();

        // Obtener tenantId del claim del refresh token (multi-tenant)
        String tenantIdFromToken = jwtUtil.getTenantIdFromToken(refreshTokenString);
        String activeTenantId;
        String activeRolNombre;
        Long   activeSucursalId;

        if (tenantIdFromToken == null || tenantIdFromToken.isBlank()) {
            refreshTokenService.revocarRefreshToken(refreshTokenString);
            throw new UnauthorizedException("Refresh token inválido: sin tenantId");
        }

        // Validar que la relación usuario_tenant sigue activa
        UsuarioTenant ut = usuarioTenantRepository
                .findByUsuarioIdAndTenantIdAndActivoTrue(usuario.getId(), tenantIdFromToken)
                .orElseThrow(() -> {
                    refreshTokenService.revocarRefreshToken(refreshTokenString);
                    return new UnauthorizedException("El usuario ya no pertenece al tenant del refresh token");
                });

        activeTenantId   = tenantIdFromToken;
        activeRolNombre  = ut.getRol().getNombre();
        activeSucursalId = ut.getSucursalId();

        // Rotación: revocar el refresh token usado y crear uno nuevo
        refreshTokenService.revocarRefreshToken(refreshTokenString);
        RefreshToken newRefreshToken = refreshTokenService.crearRefreshToken(usuario, activeTenantId);

        log.info("✅ Tokens renovados para usuario: {} tenant: {}", usuario.getEmail(), activeTenantId);

        Suscripcion suscripcion = (activeTenantId != null)
                ? suscripcionService.obtenerSuscripcionPorTenant(activeTenantId).orElse(null)
                : null;

        if (suscripcion != null
                && "TRIAL".equals(suscripcion.getEstado())
                && suscripcion.getTrialEndDate() != null
                && LocalDateTime.now().isAfter(suscripcion.getTrialEndDate())) {
            suscripcion = suscripcionService.expirarTrial(suscripcion.getId());
            log.info("⏰ Trial vencido detectado en refresh para usuario {}", usuario.getEmail());
        }

        String newAccessToken = jwtUtil.generateToken(
                usuario.getId(), usuario.getEmail(), usuario.getNombre(),
                activeRolNombre, activeTenantId);

        SuscripcionDTO suscripcionDTO = suscripcion != null ? mapToSuscripcionDTO(suscripcion) : null;

        return JwtResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .tipo("Bearer")
                .expiresIn((int) (jwtProperties.getExpiration() / 1000))
                .usuarioId(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .rol(activeRolNombre)
                .tenantId(activeTenantId)
                .suscripcion(suscripcionDTO)
                .sucursalId(activeSucursalId)
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshTokenString) {
        refreshTokenService.revocarRefreshToken(refreshTokenString);
        log.info("✅ Refresh token revocado (logout)");
    }


    @Override
    @Transactional(readOnly = true)
    public List<TenantInfoDTO> getTenants(Long usuarioId) {
        return usuarioTenantRepository.findActivosConRolByUsuarioId(usuarioId).stream()
                .map(this::buildTenantInfo)
                .toList();
    }

    @Override
    @Transactional
    public JwtResponseDTO selectTenant(Long usuarioId, SelectTenantRequestDTO dto) {
        String requestedTenantId = dto.getTenantId();

        // Validar que el usuario pertenece al tenant solicitado y la relación está activa
        UsuarioTenant ut = usuarioTenantRepository
                .findByUsuarioIdAndTenantIdAndActivoTrue(usuarioId, requestedTenantId)
                .orElseThrow(() -> new UnauthorizedException(
                        "No tienes acceso al tenant: " + requestedTenantId));

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        log.info("🏢 selectTenant: usuario {} → tenant {}", usuario.getEmail(), requestedTenantId);

        return buildFullJwtResponse(usuario, requestedTenantId, ut.getRol().getNombre());
    }

    // ── Helpers privados ────────────────────────────────────────────────────────

    private JwtResponseDTO buildFullJwtResponse(Usuario usuario, String tenantId, String rolNombre) {
        String accessToken = jwtUtil.generateToken(
                usuario.getId(), usuario.getEmail(), usuario.getNombre(), rolNombre, tenantId);

        RefreshToken refreshToken = refreshTokenService.crearRefreshToken(usuario, tenantId);

        // sucursalId desde usuario_tenant (fuente de verdad para tenant activo)
        Long sucursalId = null;
        if (tenantId != null) {
            sucursalId = usuarioTenantRepository
                    .findByUsuarioIdAndTenantIdAndActivoTrue(usuario.getId(), tenantId)
                    .map(UsuarioTenant::getSucursalId)
                    .orElse(null);
        }

        Suscripcion suscripcion = (tenantId != null)
                ? suscripcionService.obtenerSuscripcionPorTenant(tenantId).orElse(null)
                : null;

        if (suscripcion != null
                && "TRIAL".equals(suscripcion.getEstado())
                && suscripcion.getTrialEndDate() != null
                && LocalDateTime.now().isAfter(suscripcion.getTrialEndDate())) {
            suscripcion = suscripcionService.expirarTrial(suscripcion.getId());
            log.info("⏰ Trial vencido para usuario {} en buildFullJwtResponse", usuario.getEmail());
        }

        SuscripcionDTO suscripcionDTO = suscripcion != null ? mapToSuscripcionDTO(suscripcion) : null;

        return JwtResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tipo("Bearer")
                .usuarioId(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .rol(rolNombre)
                .tenantId(tenantId)
                .expiresIn((int) (jwtProperties.getExpiration() / 1000))
                .suscripcion(suscripcionDTO)
                .sucursalId(sucursalId)
                .build();
    }

    @Override
    @Transactional
    public TenantInfoDTO crearNegocio(Long usuarioId, CrearNegocioRequestDTO dto) {
        log.info("🏢 Creando nuevo negocio para usuario {}: {}", usuarioId, dto.getNombreNegocio());

        BigDecimal precioMensual = obtenerPrecioPlan(dto.getPlanId());

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        Tenant tenant = tenantService.crearTenant(
                dto.getNombreNegocio(),
                dto.getRubro(),
                dto.getRuc(),
                dto.getEmailContacto(),
                dto.getTelefono()
        );

        Rol rolAdmin = rolRepository.findByNombre("ADMIN")
                .orElseThrow(() -> new BadRequestException("Rol ADMIN no encontrado"));

        UsuarioTenant usuarioTenant = UsuarioTenant.builder()
                .usuario(usuario)
                .tenantId(tenant.getTenantId())
                .rol(rolAdmin)
                .activo(true)
                .build();
        usuarioTenantRepository.save(usuarioTenant);
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime trialEnd = ahora.plusDays(14);

        Suscripcion suscripcion = Suscripcion.builder()
                .usuarioPrincipal(usuario)
                .planId(dto.getPlanId())
                .precioMensual(precioMensual)
                .estado("TRIAL")
                .trialEndDate(trialEnd)
                .tenantId(tenant.getTenantId())
                .fechaInicio(ahora)
                .fechaProximoCobro(trialEnd)
                .build();
        suscripcionService.crearSuscripcion(suscripcion);

        if ("PRO".equals(dto.getPlanId())) {
            sucursalService.inicializarPrincipal(tenant.getTenantId());
            log.info("✅ Sucursal principal inicializada para nuevo negocio PRO: {}", tenant.getTenantId());
        }

        log.info("✅ Nuevo negocio creado: {} para usuario {}", tenant.getTenantId(), usuario.getEmail());

        return buildTenantInfo(usuarioTenant);
    }

    private TenantInfoDTO buildTenantInfo(UsuarioTenant ut) {
        Tenant tenant = tenantService.obtenerTenant(ut.getTenantId()).orElse(null);
        return TenantInfoDTO.builder()
                .tenantId(ut.getTenantId())
                .nombre(tenant != null ? tenant.getNombre() : ut.getTenantId())
                .rubro(tenant != null ? tenant.getRubro() : null)
                .logoUrl(null)
                .rol(ut.getRol().getNombre())
                .build();
    }

    private BigDecimal obtenerPrecioPlan(String planId) {
        if ("PRO".equals(planId)) {
            return culqiProperties.getPrecioPro();
        }
        if ("BASICO".equals(planId)) {
            return culqiProperties.getPrecioBasico();
        }
        throw new BadRequestException("Plan inválido: " + planId + ". Planes válidos: BASICO, PRO");
    }

    private SuscripcionDTO mapToSuscripcionDTO(Suscripcion suscripcion) {
        return SuscripcionDTO.builder()
                .id(suscripcion.getId())
                .usuarioPrincipalId(suscripcion.getUsuarioPrincipal().getId())
                .planId(suscripcion.getPlanId())
                .precioMensual(suscripcion.getPrecioMensual())
                .estado(suscripcion.getEstado())
                .tenantId(suscripcion.getTenantId())
                .trialEndDate(suscripcion.getTrialEndDate())
                .build();
    }

    @Override
    public UsuarioProfileDTO obtenerPerfil(Long usuarioId) {
        log.info("📋 Obteniendo perfil del usuario: {}", usuarioId);

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        // Tenant activo desde el JWT (claim seteado por JwtAuthenticationFilter)
        String effectiveTenantId = TenantContext.getCurrentTenant();
        if (effectiveTenantId == null || effectiveTenantId.isBlank()) {
            throw new UnauthorizedException("No hay tenant activo en la sesión");
        }

        // Rol y sucursal exclusivamente desde usuario_tenant
        UsuarioTenant ut = usuarioTenantRepository
                .findByUsuarioIdAndTenantIdAndActivoTrue(usuario.getId(), effectiveTenantId)
                .orElseThrow(() -> new UnauthorizedException(
                        "El usuario no está activo en el tenant: " + effectiveTenantId));

        String effectiveRol = ut.getRol().getNombre();
        Long sucursalId = ut.getSucursalId();

        log.info("👤 Perfil — userId:{} tenant:{} rol:{}", usuarioId, effectiveTenantId, effectiveRol);

        Tenant tenant = tenantService.obtenerTenant(effectiveTenantId).orElse(null);

        Set<String> permisos = new TreeSet<>(rolePermissionDefaults.getBasePermissions(effectiveRol));
        permisos.addAll(usuarioPermisoService.obtenerPermisosCodigos(usuario.getId(), effectiveTenantId));

        return UsuarioProfileDTO.builder()
                .usuarioId(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .rol(effectiveRol)
                .tenantId(effectiveTenantId)
                .ultimoLogin(usuario.getUltimoLogin())
                .createdAt(usuario.getCreatedAt())
                .activo(usuario.getActivo())
                .nombreFarmacia(tenant != null ? tenant.getNombre() : "N/A")
                .permisos(new ArrayList<>(permisos))
                .tipoDocumento(usuario.getTipoDocumento())
                .numeroDocumento(usuario.getNumeroDocumento())
                .numeroCelular(usuario.getNumeroCelular())
                .sucursalId(sucursalId)
                .build();
    }

    @Override
    @Transactional
    public void cambiarContraseña(Long usuarioId, CambiarPasswordDTO dto) {
        log.info("🔐 Cambiando contraseña del usuario: {}", usuarioId);

        // Validar que las contraseñas nuevas coincidan
        if (!dto.getNuevaContraseña().equals(dto.getConfirmarContraseña())) {
            throw new BadRequestException("Las contraseñas no coinciden");
        }

        // Validar que no sea la misma contraseña
        if (dto.getContraseñaActual().equals(dto.getNuevaContraseña())) {
            throw new BadRequestException("La nueva contraseña debe ser diferente a la actual");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        // Validar contraseña actual
        if (!passwordEncoder.matches(dto.getContraseñaActual(), usuario.getContraseña())) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }

        // Cambiar contraseña
        usuario.setContraseña(passwordEncoder.encode(dto.getNuevaContraseña()));
        usuarioRepository.save(usuario);

        // Revocar todos los refresh tokens
        refreshTokenService.revocarTodosLosTokensDelUsuario(usuarioId);

        // Email de confirmación de seguridad (asíncrono — no bloquea la respuesta)
        try {
            emailService.enviarConfirmacionCambioContraseña(usuario.getEmail(), usuario.getNombre());
        } catch (Exception e) {
            log.warn("No se pudo enviar email de confirmación de cambio de contraseña: {}", e.getMessage());
        }

        log.info("✅ Contraseña cambiada exitosamente");
    }

    @Override
    public void solicitarRecuperacionContraseña(ForgotPasswordDTO dto) {
        log.info("📧 Solicitud de recuperación de contraseña: {}", dto.getEmail());

        // Respuesta genérica aunque el email no exista — evita enumeración de emails
        Usuario usuario = usuarioRepository.findByEmail(dto.getEmail()).orElse(null);
        if (usuario == null) {
            log.info("📧 Email no registrado (respuesta silenciosa): {}", dto.getEmail());
            return;  // No lanzar error — el frontend mostrará el mismo mensaje de éxito
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiracion = LocalDateTime.now().plusHours(1);

        usuario.setTokenRecuperacion(token);
        usuario.setTokenRecuperacionExpira(expiracion);
        usuarioRepository.save(usuario);

        log.info("🔑 Token generado: {}", token);
        log.info("📧 Enviando email a: {}", usuario.getEmail());

        // ✅ ENVIAR EMAIL (async — no bloquea la respuesta)
        try {
            emailService.enviarEmailRecuperacionContraseña(
                    usuario.getEmail(),
                    usuario.getNombre(),
                    token
            );
            log.info("✅ Email de recuperación enviado a: {}", usuario.getEmail());
        } catch (Exception e) {
            log.error("❌ Error enviando email de recuperación: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void resetearContraseña(ResetPasswordDTO dto) {
        log.info("🔐 Reseteando contraseña con token");

        // Validar que las contraseñas coincidan
        if (!dto.getNuevaContraseña().equals(dto.getConfirmarContraseña())) {
            throw new BadRequestException("Las contraseñas no coinciden");
        }

        // Buscar usuario con token válido
        Usuario usuario = usuarioRepository.findByTokenRecuperacion(dto.getToken())
                .orElseThrow(() -> new BadRequestException("Token inválido"));

        // Validar que el token no haya expirado
        if (usuario.getTokenRecuperacionExpira() == null ||
                usuario.getTokenRecuperacionExpira().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token expirado");
        }

        // Cambiar contraseña
        usuario.setContraseña(passwordEncoder.encode(dto.getNuevaContraseña()));
        usuario.setTokenRecuperacion(null);
        usuario.setTokenRecuperacionExpira(null);
        usuarioRepository.save(usuario);

        // Revocar todos los refresh tokens
        refreshTokenService.revocarTodosLosTokensDelUsuario(usuario.getId());

        // Email de confirmación de seguridad
        try {
            emailService.enviarConfirmacionCambioContraseña(usuario.getEmail(), usuario.getNombre());
        } catch (Exception e) {
            log.warn("No se pudo enviar email de confirmación de reset de contraseña: {}", e.getMessage());
        }

        log.info("✅ Contraseña reseteada exitosamente");
    }

    @Override
    @Transactional
    public void activarCuenta(ResetPasswordDTO dto) {
        log.info("🔐 Activando cuenta con token de activación");

        if (!dto.getNuevaContraseña().equals(dto.getConfirmarContraseña())) {
            throw new BadRequestException("Las contraseñas no coinciden");
        }
        if (dto.getNuevaContraseña().length() < 6) {
            throw new BadRequestException("La contraseña debe tener al menos 6 caracteres");
        }

        Usuario usuario = usuarioRepository.findByTokenActivacion(dto.getToken())
                .orElseThrow(() -> new BadRequestException("Token de activación inválido o ya utilizado"));

        if (usuario.getTokenActivacionExpira() == null ||
                usuario.getTokenActivacionExpira().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("El token de activación ha expirado. Solicita al administrador que te reenvíe el link.");
        }

        usuario.setContraseña(passwordEncoder.encode(dto.getNuevaContraseña()));
        usuario.setTokenActivacion(null);
        usuario.setTokenActivacionExpira(null);
        usuarioRepository.save(usuario);

        log.info("✅ Cuenta activada exitosamente para: {}", usuario.getEmail());
    }

}

