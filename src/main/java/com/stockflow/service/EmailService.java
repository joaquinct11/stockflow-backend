package com.stockflow.service;

public interface EmailService {
    void enviarEmailRecuperacionContraseña(String email, String nombre, String token);
    void enviarEmailVerificacion(String email, String nombre, String token);
}