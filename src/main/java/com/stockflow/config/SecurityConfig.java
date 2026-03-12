package com.stockflow.config;

import com.stockflow.security.JwtAccessDeniedHandler;
import com.stockflow.security.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/auth/**",
                                "/actuator/**",
                                "/info/**"
                        ).permitAll()

                        // Solo ADMIN: gestión de roles y permisos
                        .requestMatchers("/roles/**").hasRole("ADMIN")
                        .requestMatchers("/permisos/**").hasRole("ADMIN")

                        // ADMIN y GERENTE: gestión de usuarios y suscripciones
                        .requestMatchers("/usuarios/**").hasAnyRole("ADMIN", "GERENTE")
                        .requestMatchers("/suscripciones/**").hasAnyRole("ADMIN", "GERENTE")

                        // Productos: lectura para todos los roles, escritura solo ADMIN y GESTOR_INVENTARIO
                        .requestMatchers(HttpMethod.GET, "/productos/**").hasAnyRole("ADMIN", "GERENTE", "GESTOR_INVENTARIO", "VENDEDOR")
                        .requestMatchers(HttpMethod.POST, "/productos/**").hasAnyRole("ADMIN", "GESTOR_INVENTARIO")
                        .requestMatchers(HttpMethod.PUT, "/productos/**").hasAnyRole("ADMIN", "GESTOR_INVENTARIO")
                        .requestMatchers(HttpMethod.DELETE, "/productos/**").hasAnyRole("ADMIN", "GESTOR_INVENTARIO")

                        // Proveedores: lectura para todos los roles, escritura solo ADMIN y GESTOR_INVENTARIO
                        .requestMatchers(HttpMethod.GET, "/proveedores/**").hasAnyRole("ADMIN", "GERENTE", "GESTOR_INVENTARIO", "VENDEDOR")
                        .requestMatchers(HttpMethod.POST, "/proveedores/**").hasAnyRole("ADMIN", "GESTOR_INVENTARIO")
                        .requestMatchers(HttpMethod.PUT, "/proveedores/**").hasAnyRole("ADMIN", "GESTOR_INVENTARIO")
                        .requestMatchers(HttpMethod.PATCH, "/proveedores/**").hasAnyRole("ADMIN", "GESTOR_INVENTARIO")
                        .requestMatchers(HttpMethod.DELETE, "/proveedores/**").hasAnyRole("ADMIN", "GESTOR_INVENTARIO")

                        // Movimientos de inventario: lectura para ADMIN, GERENTE y GESTOR_INVENTARIO; escritura solo ADMIN y GESTOR_INVENTARIO
                        .requestMatchers(HttpMethod.GET, "/movimientos-inventario/**").hasAnyRole("ADMIN", "GERENTE", "GESTOR_INVENTARIO")
                        .requestMatchers(HttpMethod.POST, "/movimientos-inventario/**").hasAnyRole("ADMIN", "GESTOR_INVENTARIO")
                        .requestMatchers(HttpMethod.DELETE, "/movimientos-inventario/**").hasAnyRole("ADMIN", "GESTOR_INVENTARIO")

                        // Ventas: lectura para ADMIN, GERENTE y VENDEDOR; creación para ADMIN y VENDEDOR; eliminación solo ADMIN
                        .requestMatchers(HttpMethod.GET, "/ventas/**").hasAnyRole("ADMIN", "GERENTE", "VENDEDOR")
                        .requestMatchers(HttpMethod.POST, "/ventas/**").hasAnyRole("ADMIN", "VENDEDOR")
                        .requestMatchers(HttpMethod.DELETE, "/ventas/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }
}