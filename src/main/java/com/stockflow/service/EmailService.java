package com.stockflow.service;

public interface EmailService {

    /** Recuperación de contraseña — link expira en 1 hora */
    void enviarEmailRecuperacionContraseña(String email, String nombre, String token);

    /** Verificación de cuenta al registrarse — link expira en 24 horas */
    void enviarEmailVerificacion(String email, String nombre, String token);

    /** Bienvenida al nuevo tenant/empresa */
    void enviarBienvenida(String email, String nombreEmpresa, String nombreUsuario);
}
