package com.stockflow.service.impl;

import com.stockflow.dto.OnboardingProgresoDTO;
import com.stockflow.dto.PasoOnboardingDTO;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.ResourceNotFoundException;
import com.stockflow.repository.CajaRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.ProveedorRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.VentaRepository;
import com.stockflow.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private final TenantRepository     tenantRepository;
    private final ProductoRepository   productoRepository;
    private final ProveedorRepository  proveedorRepository;
    private final UsuarioRepository    usuarioRepository;
    private final CajaRepository       cajaRepository;
    private final VentaRepository      ventaRepository;

    @Override
    @Transactional(readOnly = true)
    public OnboardingProgresoDTO calcularProgreso(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        List<PasoOnboardingDTO> pasos = List.of(
                paso("cuenta",
                        "Cuenta creada",
                        "Tu cuenta ya está activa",
                        true,
                        null,
                        false),

                paso("empresa",
                        "Configura tu empresa",
                        "Agrega RUC y dirección fiscal — aparecen en tus comprobantes y PDFs",
                        estaConfiguradaEmpresa(tenant),
                        "/dashboard/configuracion",
                        false),

                paso("facturacion",
                        "Activa facturación electrónica",
                        "Conecta tu cuenta OSE (Nubefact, Efact…) para emitir facturas y boletas con validez SUNAT",
                        estaConfiguradoOse(tenant),
                        "/dashboard/configuracion#facturacion",
                        true),   // opcional — no bloquea el onboarding

                paso("proveedor",
                        "Crea tu primer proveedor",
                        "Registra al menos un proveedor para gestionar tus compras",
                        proveedorRepository.countByTenantId(tenantId) > 0,
                        "/dashboard/proveedores",
                        false),

                paso("producto",
                        "Agrega tu primer producto",
                        "Crea los productos o servicios que vas a vender",
                        productoRepository.countByTenantId(tenantId) > 0,
                        "/dashboard/productos",
                        false),

                paso("stock",
                        "Registra tu inventario inicial",
                        "Ingresa las cantidades actuales para poder vender sin quedar en negativo",
                        productoRepository.sumStockActualByTenantId(tenantId) > 0,
                        "/dashboard/inventario",
                        false),

                paso("caja",
                        "Abre tu primera caja",
                        "Abre una caja antes de empezar a registrar ventas",
                        cajaRepository.countByTenantId(tenantId) > 0,
                        "/dashboard/caja",
                        false),

                paso("venta",
                        "Realiza tu primera venta",
                        "¡Ya tienes todo listo! Ve al POS y cobra tu primera venta",
                        ventaRepository.countByTenantId(tenantId) > 0,
                        "/pos",
                        false)
        );

        // El porcentaje y la marca de "completado" solo consideran pasos no opcionales
        List<PasoOnboardingDTO> requeridos = pasos.stream()
                .filter(p -> !p.isOpcional())
                .toList();
        long completados = requeridos.stream().filter(PasoOnboardingDTO::isCompletado).count();
        int porcentaje   = (int) ((completados * 100) / requeridos.size());

        log.debug("📋 Onboarding tenant={} → {}/{}  ({}%)", tenantId, completados, pasos.size(), porcentaje);

        return OnboardingProgresoDTO.builder()
                .pasos(pasos)
                .porcentaje(porcentaje)
                .completado(porcentaje == 100)
                .build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PasoOnboardingDTO paso(String id, String titulo, String descripcion,
                                   boolean completado, String url, boolean opcional) {
        return PasoOnboardingDTO.builder()
                .id(id)
                .titulo(titulo)
                .descripcion(descripcion)
                .completado(completado)
                .url(url)
                .opcional(opcional)
                .build();
    }

    /** Empresa configurada: RUC + dirección fiscal presentes. */
    private boolean estaConfiguradaEmpresa(Tenant tenant) {
        return (tenant.getRuc()       != null && !tenant.getRuc().isBlank()) &&
               (tenant.getDireccion() != null && !tenant.getDireccion().isBlank());
    }

    /** OSE configurado: solo se requiere el token (la URL es config del servidor, no del tenant). */
    private boolean estaConfiguradoOse(Tenant tenant) {
        return tenant.getOseToken() != null && !tenant.getOseToken().isBlank();
    }
}
