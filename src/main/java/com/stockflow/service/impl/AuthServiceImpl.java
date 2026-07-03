package com.stockflow.service.impl;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.RegistrationRequestDTO;
import com.stockflow.dto.SuscripcionDTO;
import com.stockflow.entity.RefreshToken;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Tenant;
import com.stockflow.entity.Usuario;
import com.stockflow.dto.CambiarPasswordDTO;
import com.stockflow.dto.ForgotPasswordDTO;
import com.stockflow.dto.ResetPasswordDTO;
import com.stockflow.dto.UsuarioProfileDTO;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.UnauthorizedException;
import com.stockflow.repository.RolRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.config.properties.JwtProperties;
import com.stockflow.service.*;
import com.stockflow.util.JwtUtil;
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

        // Buscar usuario por email
        Usuario usuario = usuarioRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Email o contraseña incorrectos"));

        // Validar que el usuario esté activo
        if (!usuario.getActivo()) {
            throw new UnauthorizedException("Usuario inactivo - Suscripción vencida o cancelada");
        }

        // Validar contraseña
        if (!passwordEncoder.matches(loginDTO.getContraseña(), usuario.getContraseña())) {
            throw new UnauthorizedException("Email o contraseña incorrectos");
        }

        // Actualizar último login
        usuario.setUltimoLogin(LocalDateTime.now());
        usuarioRepository.save(usuario);

        // Generar Access Token (15 min)
        String accessToken = jwtUtil.generateToken(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol().getNombre(),
                usuario.getTenantId()
        );

        // Generar y guardar Refresh Token (7 días)
        RefreshToken refreshToken = refreshTokenService.crearRefreshToken(usuario);

        // Obtener suscripción del usuario
        Suscripcion suscripcion = suscripcionService.obtenerSuscripcionPorUsuario(usuario.getId())
                .orElse(null);

        // Si el trial venció, pasar a PENDIENTE para que el usuario pague
        if (suscripcion != null) {
            log.info("🔍 Suscripción encontrada — estado={} trialEnd={} ahora={}",
                    suscripcion.getEstado(), suscripcion.getTrialEndDate(), LocalDateTime.now());
        }
        if (suscripcion != null
                && "TRIAL".equals(suscripcion.getEstado())
                && suscripcion.getTrialEndDate() != null
                && LocalDateTime.now().isAfter(suscripcion.getTrialEndDate())) {
            suscripcion = suscripcionService.expirarTrial(suscripcion.getId());
            log.info("⏰ Trial vencido para usuario {} — estado actualizado a PENDIENTE", usuario.getEmail());
        }

        SuscripcionDTO suscripcionDTO = suscripcion != null ? mapToSuscripcionDTO(suscripcion) : null;

        return JwtResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tipo("Bearer")
                .usuarioId(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .rol(usuario.getRol().getNombre())
                .tenantId(usuario.getTenantId())
                .expiresIn((int) (jwtProperties.getExpiration() / 1000))
                .suscripcion(suscripcionDTO)
                .sucursalId(usuario.getSucursalId())
                .build();
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
        Tenant tenant = tenantService.crearTenant(request.getNombreFarmacia(), request.getRubro());
        log.info("✅ Tenant creado: {}", tenant.getTenantId());

        // 3. Crear USUARIO (rol ADMIN)
        Rol rolAdmin = rolRepository.findByNombre("ADMIN")
                .orElseThrow(() -> new BadRequestException("Rol ADMIN no encontrado"));

        Usuario usuario = Usuario.builder()
                .email(request.getEmail())
                .contraseña(passwordEncoder.encode(request.getContraseña()))
                .nombre(request.getNombre())
                .rol(rolAdmin)
                .activo(true)
                .tenantId(tenant.getTenantId())
                .ultimoLogin(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .apellido(request.getApellido())
                .tipoDocumento(request.getTipoDocumento())
                .numeroDocumento(request.getNumeroDocumento())
                .numeroCelular(request.getNumeroCelular())
                .build();

        Usuario usuarioCreado = usuarioRepository.save(usuario);
        log.info("✅ Usuario creado: {} con tenant: {}", usuarioCreado.getEmail(), tenant.getTenantId());

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

        // 7. Generar tokens JWT
        String accessToken = jwtUtil.generateToken(
                usuarioCreado.getId(),
                usuarioCreado.getEmail(),
                usuarioCreado.getNombre(),
                usuarioCreado.getRol().getNombre(),
                tenant.getTenantId()
        );

        RefreshToken refreshToken = refreshTokenService.crearRefreshToken(usuarioCreado);

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
                .rol(usuarioCreado.getRol().getNombre())
                .tenantId(tenant.getTenantId())
                .expiresIn((int) (jwtProperties.getExpiration() / 1000))
                .suscripcion(mapToSuscripcionDTO(suscripcionCreada))
                .build();
    }

    @Override
    @Transactional
    public JwtResponseDTO refresh(String refreshTokenString) {
        // Validar refresh token
        RefreshToken refreshToken = refreshTokenService.validarRefreshToken(refreshTokenString);
        Usuario usuario = refreshToken.getUsuario();

        // Generar nuevo Access Token
        String newAccessToken = jwtUtil.generateToken(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol().getNombre(),
                usuario.getTenantId()
        );

        // Rotación: revocar el refresh token usado y crear uno nuevo
        refreshTokenService.revocarRefreshToken(refreshTokenString);
        RefreshToken newRefreshToken = refreshTokenService.crearRefreshToken(usuario);

        log.info("✅ Tokens renovados para usuario: {}", usuario.getEmail());

        // Obtener suscripción del usuario
        Suscripcion suscripcion = suscripcionService.obtenerSuscripcionPorUsuario(usuario.getId())
                .orElse(null);

        // Expirar trial si venció
        if (suscripcion != null
                && "TRIAL".equals(suscripcion.getEstado())
                && suscripcion.getTrialEndDate() != null
                && LocalDateTime.now().isAfter(suscripcion.getTrialEndDate())) {
            suscripcion = suscripcionService.expirarTrial(suscripcion.getId());
            log.info("⏰ Trial vencido detectado en refresh para usuario {}", usuario.getEmail());
        }

        SuscripcionDTO suscripcionDTO = suscripcion != null ? mapToSuscripcionDTO(suscripcion) : null;

        return JwtResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .tipo("Bearer")
                .expiresIn((int) (jwtProperties.getExpiration() / 1000))
                .usuarioId(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .rol(usuario.getRol().getNombre())
                .tenantId(usuario.getTenantId())
                .suscripcion(suscripcionDTO)
                .sucursalId(usuario.getSucursalId())
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshTokenString) {
        refreshTokenService.revocarRefreshToken(refreshTokenString);
        log.info("✅ Refresh token revocado (logout)");
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

        // Obtener nombre de la farmacia desde el tenant
        Tenant tenant = tenantService.obtenerTenant(usuario.getTenantId())
                .orElse(null);

        Set<String> permisos = new TreeSet<>(rolePermissionDefaults.getBasePermissions(usuario.getRol().getNombre()));
        permisos.addAll(usuarioPermisoService.obtenerPermisosCodigos(usuario.getId(), usuario.getTenantId()));

        return UsuarioProfileDTO.builder()
                .usuarioId(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .rol(usuario.getRol().getNombre())
                .tenantId(usuario.getTenantId())
                .ultimoLogin(usuario.getUltimoLogin())
                .createdAt(usuario.getCreatedAt())
                .activo(usuario.getActivo())
                .nombreFarmacia(tenant != null ? tenant.getNombre() : "N/A")
                .permisos(new ArrayList<>(permisos))
                .tipoDocumento(usuario.getTipoDocumento())
                .numeroDocumento(usuario.getNumeroDocumento())
                .numeroCelular(usuario.getNumeroCelular())
                .sucursalId(usuario.getSucursalId())
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

