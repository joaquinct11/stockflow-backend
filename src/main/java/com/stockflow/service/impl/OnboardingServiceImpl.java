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
                        null),

                paso("negocio",
                        "Configura tu negocio",
                        "Agrega RUC, dirección y datos de contacto de tu negocio",
                        estaConfiguradoNegocio(tenant),
                        "/dashboard/configuracion"),

                paso("proveedor",
                        "Crea tu primer proveedor",
                        "Registra al menos un proveedor para tus compras",
                        proveedorRepository.countByTenantId(tenantId) > 0,
                        "/dashboard/proveedores"),

                paso("producto",
                        "Agrega tu primer producto",
                        "Crea los productos que vas a vender en tu negocio",
                        productoRepository.countByTenantId(tenantId) > 0,
                        "/dashboard/productos"),

                paso("stock",
                        "Registra tu inventario inicial",
                        "Ingresa las cantidades actuales de tus productos para poder vender",
                        productoRepository.sumStockActualByTenantId(tenantId) > 0,
                        "/dashboard/inventario"),

                paso("caja",
                        "Abre tu primera caja",
                        "Abre una caja antes de empezar a vender",
                        cajaRepository.countByTenantId(tenantId) > 0,
                        "/dashboard/caja"),

                paso("venta",
                        "Realiza tu primera venta",
                        "¡Ya tienes todo listo! Ve al POS y haz tu primera venta",
                        ventaRepository.countByTenantId(tenantId) > 0,
                        "/pos")
        );

        long completados = pasos.stream().filter(PasoOnboardingDTO::isCompletado).count();
        int porcentaje   = (int) ((completados * 100) / pasos.size());

        log.debug("📋 Onboarding tenant={} → {}/{}  ({}%)", tenantId, completados, pasos.size(), porcentaje);

        return OnboardingProgresoDTO.builder()
                .pasos(pasos)
                .porcentaje(porcentaje)
                .completado(porcentaje == 100)
                .build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PasoOnboardingDTO paso(String id, String titulo, String descripcion,
                                   boolean completado, String url) {
        return PasoOnboardingDTO.builder()
                .id(id)
                .titulo(titulo)
                .descripcion(descripcion)
                .completado(completado)
                .url(url)
                .build();
    }

    /**
     * El negocio está configurado si el tenant tiene al menos RUC o dirección guardada.
     */
    private boolean estaConfiguradoNegocio(Tenant tenant) {
        return (tenant.getRuc()       != null && !tenant.getRuc().isBlank()) ||
               (tenant.getDireccion() != null && !tenant.getDireccion().isBlank());
    }
}
