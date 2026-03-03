package com.stockflow.service.impl;

import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.RegistrationRequestDTO;
import com.stockflow.dto.SuscripcionDTO;
import com.stockflow.entity.RefreshToken;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Tenant;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.UnauthorizedException;
import com.stockflow.repository.RolRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.AuthService;
import com.stockflow.service.RefreshTokenService;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.TenantService;
import com.stockflow.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final TenantService tenantService;
    private final SuscripcionService suscripcionService;

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
                .expiresIn(900)
                .suscripcion(suscripcionDTO)
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
        Tenant tenant = tenantService.crearTenant(request.getNombreFarmacia());
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
                .fechaCreacion(LocalDateTime.now())
                .ultimoLogin(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        Usuario usuarioCreado = usuarioRepository.save(usuario);
        log.info("✅ Usuario creado: {} con tenant: {}", usuarioCreado.getEmail(), tenant.getTenantId());

        // 4. Crear SUSCRIPCIÓN
        BigDecimal precioMensual = obtenerPrecioPlan(request.getPlanId());

        // Generar preapprovalId automáticamente
        String preapprovalId = generarPreapprovalId();

        Suscripcion suscripcion = Suscripcion.builder()
                .usuarioPrincipal(usuarioCreado)
                .preapprovalId(preapprovalId)
                .planId(request.getPlanId())
                .precioMensual(precioMensual)
                .estado("ACTIVA")
                .metodoPago("PENDIENTE")
                .tenantId(tenant.getTenantId())
                .fechaInicio(LocalDateTime.now())
                .fechaProximoCobro(LocalDateTime.now().plusMonths(1))
                .build();

        Suscripcion suscripcionCreada = suscripcionService.crearSuscripcion(suscripcion);
        log.info("✅ Suscripción creada: Plan {} para usuario {}",
                suscripcionCreada.getPlanId(), usuarioCreado.getEmail());

        // 5. Generar tokens JWT
        String accessToken = jwtUtil.generateToken(
                usuarioCreado.getId(),
                usuarioCreado.getEmail(),
                usuarioCreado.getNombre(),
                usuarioCreado.getRol().getNombre(),
                usuarioCreado.getTenantId()
        );

        RefreshToken refreshToken = refreshTokenService.crearRefreshToken(usuarioCreado);

        log.info("✅ Registro completado exitosamente para: {}", request.getEmail());

        // 6. Retornar respuesta
        return JwtResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tipo("Bearer")
                .usuarioId(usuarioCreado.getId())
                .email(usuarioCreado.getEmail())
                .nombre(usuarioCreado.getNombre())
                .rol(usuarioCreado.getRol().getNombre())
                .tenantId(tenant.getTenantId())
                .expiresIn(900)
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

        return JwtResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .tipo("Bearer")
                .expiresIn(900)
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshTokenString) {
        refreshTokenService.revocarRefreshToken(refreshTokenString);
        log.info("✅ Refresh token revocado (logout)");
    }

    private String generarPreapprovalId() {
        LocalDateTime ahora = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String fecha = ahora.format(formatter);

        Random random = new Random();
        int numero = random.nextInt(999999);

        return String.format("PRE-%s-%06d", fecha, numero);
    }

    private BigDecimal obtenerPrecioPlan(String planId) {
        return switch (planId) {
            case "FREE" -> BigDecimal.ZERO;
            case "BASICO" -> new BigDecimal("49.99");
            case "PRO" -> new BigDecimal("99.99");
            default -> throw new BadRequestException("Plan inválido: " + planId);
        };
    }

    private SuscripcionDTO mapToSuscripcionDTO(Suscripcion suscripcion) {
        return SuscripcionDTO.builder()
                .id(suscripcion.getId())
                .usuarioPrincipalId(suscripcion.getUsuarioPrincipal().getId())
                .planId(suscripcion.getPlanId())
                .precioMensual(suscripcion.getPrecioMensual())
                .estado(suscripcion.getEstado())
                .tenantId(suscripcion.getTenantId())
                .build();
    }
}
