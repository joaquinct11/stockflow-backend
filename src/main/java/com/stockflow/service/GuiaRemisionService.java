package com.stockflow.service;

import com.stockflow.dto.gre.*;
import com.stockflow.entity.gre.GuiaRemision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GuiaRemisionService {

    /** Crea un borrador sin enviar a SUNAT. */
    GuiaRemisionResponse crearBorrador(CrearGuiaRemisionRequest request);

    /** Edita campos de un borrador (solo BORRADOR o ERROR). */
    GuiaRemisionResponse editarBorrador(Long id, EditarBorradorRequest request);

    /** Elimina definitivamente un borrador (solo BORRADOR o ERROR). */
    void eliminarBorrador(Long id);

    /** Emite la guía a SUNAT vía Lucode. */
    GuiaRemisionResponse emitirGRE(Long id);

    /** Confirma la recepción física del destinatario. */
    GuiaRemisionResponse confirmarRecepcion(Long id, ConfirmarRecepcionRequest request);

    /** Cancela un borrador (no emitido). */
    GuiaRemisionResponse cancelarBorrador(Long id);

    /** Lista paginada del tenant. */
    Page<GuiaRemisionListResponse> listar(Pageable pageable);

    /** Detalle completo. */
    GuiaRemisionResponse obtener(Long id);

    /** Genera PDF y retorna bytes. */
    byte[] generarPDF(Long id);
}
