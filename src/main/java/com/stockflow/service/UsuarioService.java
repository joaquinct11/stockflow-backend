package com.stockflow.service;

import com.stockflow.dto.CrearUsuarioResult;
import com.stockflow.dto.DeleteAccountValidationDTO;
import com.stockflow.dto.UsuarioUpdateDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import java.util.List;
import java.util.Optional;

public interface UsuarioService {

    /**
     * Crea un usuario nuevo (Case A) o incorpora uno existente al tenant actual (Case B).
     * El tenantId se lee de TenantContext; rol se pasa explícitamente.
     * sucursalId se pasa explícitamente (no se almacena en usuarios, va a usuario_tenant).
     * Nunca sobrescribe la contraseña de un usuario existente.
     */
    CrearUsuarioResult crearUsuario(Usuario usuario, Long sucursalId, Rol rol);

    Optional<Usuario> obtenerUsuarioPorId(Long id);

    Optional<Usuario> obtenerUsuarioPorEmail(String email);

    List<Usuario> obtenerUsuariosPorTenant(String tenantId);

    /**
     * Actualiza campos globales de la identidad del usuario y campos tenant-scoped en usuario_tenant.
     * Campos globales (usuarios): nombre, apellido, activo, tipoDocumento, numeroDocumento, numeroCelular.
     * Campos tenant-scoped (usuario_tenant): rol (explicit param) y sucursalId (from dto.getSucursalId()).
     * El tenantId se lee de TenantContext.
     */
    Usuario actualizarUsuario(Long id, UsuarioUpdateDTO updateDTO, Rol rol);

    void desactivarUsuario(Long id);

    void activarUsuario(Long id);

    DeleteAccountValidationDTO validarEliminacion(Long id);

    void eliminarUsuario(Long id);

    void eliminarCuentaCompleta(Long id);

    /**
     * Guarda directamente el objeto Usuario (usado para actualizar campos como
     * tipo_documento / numero_documento sin pasar por el flujo de actualización completa).
     */
    Usuario guardarUsuario(Usuario usuario);

    /**
     * Regenera el token de activación y reenvía el email de bienvenida.
     * Útil cuando el link original de 48h ya expiró.
     */
    void reenviarActivacion(Long usuarioId, String tenantId);
}
