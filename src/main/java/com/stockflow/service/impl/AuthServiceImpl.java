package com.stockflow.service.impl;

import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.UsuarioDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.UnauthorizedException;
import com.stockflow.repository.RolRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.service.AuthService;
import com.stockflow.util.JwtUtil;
import com.stockflow.util.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklist tokenBlacklist;

    @Override
    public JwtResponseDTO login(LoginDTO loginDTO) {
        // Buscar usuario por email
        Usuario usuario = usuarioRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Email o contraseña incorrectos"));

        // Validar que el usuario esté activo
        if (!usuario.getActivo()) {
            throw new UnauthorizedException("Usuario inactivo");
        }

        // Validar contraseña
        if (!passwordEncoder.matches(loginDTO.getContraseña(), usuario.getContraseña())) {
            throw new UnauthorizedException("Email o contraseña incorrectos");
        }

        // Actualizar último login
        usuario.setUltimoLogin(LocalDateTime.now());
        usuarioRepository.save(usuario);

        // Generar JWT
        String token = jwtUtil.generateToken(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol().getNombre()
        );

        return JwtResponseDTO.builder()
                .token(token)
                .tipo("Bearer")
                .usuarioId(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .rol(usuario.getRol().getNombre())
                .build();
    }

    @Override
    public JwtResponseDTO registroUsuario(UsuarioDTO usuarioDTO) {
        // Validar que el email no exista
        if (usuarioRepository.findByEmail(usuarioDTO.getEmail()).isPresent()) {
            throw new ConflictException("El email ya está registrado");
        }

        // Buscar el rol
        Rol rol = rolRepository.findByNombre(usuarioDTO.getRolNombre())
                .orElseThrow(() -> new BadRequestException("El rol especificado no existe"));

        // Crear nuevo usuario
        Usuario usuario = Usuario.builder()
                .email(usuarioDTO.getEmail())
                .contraseña(passwordEncoder.encode(usuarioDTO.getContraseña()))
                .nombre(usuarioDTO.getNombre())
                .rol(rol)
                .activo(true)
                .tenantId(usuarioDTO.getTenantId())
                .fechaCreacion(LocalDateTime.now())
                .ultimoLogin(LocalDateTime.now())
                .build();

        Usuario usuarioGuardado = usuarioRepository.save(usuario);

        // Generar JWT
        String token = jwtUtil.generateToken(
                usuarioGuardado.getId(),
                usuarioGuardado.getEmail(),
                usuarioGuardado.getNombre(),
                usuarioGuardado.getRol().getNombre()
        );

        return JwtResponseDTO.builder()
                .token(token)
                .tipo("Bearer")
                .usuarioId(usuarioGuardado.getId())
                .email(usuarioGuardado.getEmail())
                .nombre(usuarioGuardado.getNombre())
                .rol(usuarioGuardado.getRol().getNombre())
                .build();
    }

    @Override
    public void logout(String token) {
        tokenBlacklist.addTokenToBlacklist(token);
//        logger.info("Token añadido a la blacklist: {}", token);
    }
}