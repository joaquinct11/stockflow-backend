package com.stockflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String contraseña;

    @Column(nullable = false)
    private String nombre;

    @ManyToOne
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "token_recuperacion", unique = true)
    private String tokenRecuperacion;

    @Column(name = "token_recuperacion_expira")
    private LocalDateTime tokenRecuperacionExpira;

    /**
     * Tipo de documento de identidad del usuario (DNI, CE, RUC, PASAPORTE).
     * Usado para pre-rellenar el campo de identificación en el checkout de
     * Mercado Pago Suscripciones y evitar que el botón "Confirmar" quede
     * deshabilitado por falta de datos del pagador.
     */
    @Column(name = "tipo_documento", length = 20)
    private String tipoDocumento;

    /**
     * Número de documento del usuario.
     * Requerido junto con tipoDocumento para habilitar el checkout de MP.
     */
    @Column(name = "numero_documento", length = 50)
    private String numeroDocumento;
}