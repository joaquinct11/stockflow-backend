package com.stockflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface EmailService {

    /** Recuperación de contraseña — link expira en 1 hora */
    void enviarEmailRecuperacionContraseña(String email, String nombre, String token);

    /** Verificación de cuenta al registrarse — link expira en 24 horas */
    void enviarEmailVerificacion(String email, String nombre, String token);

    /** Bienvenida al nuevo tenant/empresa */
    void enviarBienvenida(String email, String nombreEmpresa, String nombreUsuario);

    /**
     * Envía la OC al proveedor por email con detalle HTML (asíncrono).
     * Recibe solo IDs para evitar problemas de sesión Hibernate entre hilos.
     * Si el proveedor no tiene email configurado, se omite silenciosamente.
     */
    void enviarOCAlProveedor(Long ocId, String tenantId);

    /**
     * Bienvenida a usuario nuevo creado por el admin.
     * Contiene link de activación para que el usuario establezca su propia contraseña.
     * El link expira en 48 horas.
     */
    void enviarBienvenidaUsuarioNuevo(String email, String nombre, String tenantId, String token);

    /**
     * Resumen de cierre de caja enviado a ADMIN y GERENTE del tenant.
     */
    void enviarResumenCierreCaja(String email, String empresaNombre, String usuarioCierre,
                                  BigDecimal montoApertura, BigDecimal totalEfectivo,
                                  BigDecimal totalTarjeta, BigDecimal totalYapePlin,
                                  BigDecimal totalIngresos, BigDecimal montoContado,
                                  BigDecimal diferencia, Integer cantidadVentas);

    /**
     * Notificación de cambio de estado de suscripción.
     * estados relevantes: ACTIVA (approved), SUSPENDIDA (rejected), CANCELADA (cancelled).
     */
    void enviarEmailSuscripcion(String email, String nombre, String estado, String planId);

    /**
     * Aviso proactivo de trial por vencer (7, 3 y 1 día antes).
     * Se dispara desde el scheduler, no desde un evento de MP.
     */
    void enviarEmailTrialPorVencer(String email, String nombre, int diasRestantes, LocalDate fechaVencimiento);

    /**
     * Confirmación de seguridad tras un cambio de contraseña exitoso.
     * Si el usuario no lo solicitó, puede contactar soporte.
     */
    void enviarConfirmacionCambioContraseña(String email, String nombre);
}
