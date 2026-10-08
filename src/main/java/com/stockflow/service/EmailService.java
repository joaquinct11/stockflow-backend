package com.stockflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

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
     * Notificación para un usuario que ya tenía cuenta y fue incorporado a un negocio adicional.
     * No incluye link de activación porque el usuario ya tiene contraseña activa.
     */
    void enviarIncorporacionNuevoNegocio(String email, String nombre, String tenantId);

    /**
     * Resumen de cierre de caja enviado a ADMIN y GERENTE del tenant.
     */
    void enviarResumenCierreCaja(String email, String empresaNombre, String usuarioApertura,
                                  String usuarioCierre,
                                  BigDecimal montoApertura, BigDecimal totalEfectivo,
                                  BigDecimal totalTarjeta, BigDecimal totalYapePlin,
                                  BigDecimal totalIngresos, BigDecimal montoContado,
                                  BigDecimal diferencia, Integer cantidadVentas,
                                  BigDecimal totalRetiros, Integer cantidadRetiros);

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
     * Aviso de cobro automático programado para mañana (suscripciones Culqi activas).
     * Se dispara desde el scheduler 1 día antes de fechaProximoCobro.
     */
    void enviarAvisoCobro(String email, String nombre, String planId,
                          java.math.BigDecimal monto, LocalDate fechaCobro);

    /**
     * Confirmación de seguridad tras un cambio de contraseña exitoso.
     * Si el usuario no lo solicitó, puede contactar soporte.
     */
    void enviarConfirmacionCambioContraseña(String email, String nombre);

    /**
     * Alerta de certificado vencido o próximo a vencer.
     * Se dispara desde el scheduler de certificados.
     */
    void enviarAlertaCertificado(String email, String nombreUsuario, String descripcionCert,
                                  java.time.LocalDate fechaVencimiento, long diasRestantes, boolean vencido);

    /**
     * Envía la reclamación al correo de contacto de Fluxus y
     * un acuse de recibo al consumidor.
     */
    /**
     * Confirmación de activación de suscripción Culqi.
     * Incluye nombre del negocio, plan, precio, fechas y últimos 4 dígitos de tarjeta.
     * Si el envío falla, la suscripción NO se revierte.
     * Destinatario = email real del usuario (NUNCA el tenant-scoped de Culqi).
     */
    void enviarConfirmacionSuscripcionCulqi(
            String emailReal,
            String nombreUsuario,
            String tenantId,
            String planId,
            BigDecimal precioMensual,
            LocalDateTime fechaActivacion,
            LocalDateTime fechaProximoCobro,
            String ultimos4Digitos,
            String metodoPago,
            String culqiSubscriptionId);

    void enviarReclamacion(String tipo, String nombre, String apellido,
                           String dni, String correoConsumidor, String telefono,
                           String pedido, String monto, String descripcion,
                           List<MultipartFile> archivos);
}
