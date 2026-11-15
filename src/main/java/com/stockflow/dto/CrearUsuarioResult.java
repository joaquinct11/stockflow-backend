package com.stockflow.dto;

import com.stockflow.entity.Usuario;

/**
 * Resultado de crearUsuario():
 * - esNuevo=true  → usuario creado por primera vez (enviar link de activación)
 * - esNuevo=false → usuario existente incorporado a este tenant (enviar email informativo)
 */
public record CrearUsuarioResult(Usuario usuario, boolean esNuevo) {}
