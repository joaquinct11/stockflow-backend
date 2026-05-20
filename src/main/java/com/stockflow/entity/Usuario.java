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

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "token_recuperacion", unique = true)
    private String tokenRecuperacion;

    @Column(name = "token_recuperacion_expira")
    private LocalDateTime tokenRecuperacionExpira;

    /** Token de activación de cuenta (enviado por email al crear usuario desde el admin) */
    @Column(name = "token_activacion", unique = true)
    private String tokenActivacion;

    @Column(name = "token_activacion_expira")
    private LocalDateTime tokenActivacionExpira;

    /**
     * Tipo de documento del usuario (DNI, CE, RUC, PASAPORTE).
     * Requerido para habilitar el checkout de Mercado Pago Suscripciones.
     */
    @Column(name = "tipo_documento", length = 20)
    private String tipoDocumento;

    @Column(name = "numero_documento", length = 50)
    private String numeroDocumento;

    @Column(name = "numero_celular", length = 20)
    private String numeroCelular;
}
