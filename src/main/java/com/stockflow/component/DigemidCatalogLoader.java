package com.stockflow.component;

import com.stockflow.entity.CatalogoDigemid;
import com.stockflow.repository.CatalogoDigemidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class DigemidCatalogLoader implements CommandLineRunner {

    private final CatalogoDigemidRepository catalogoDigemidRepository;
    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;

    // ── Configuración inyectable (override en tests mediante properties) ─────
    @Value("${digemid.min-expected:18000}")
    private long minExpected;

    // Cuando es false el arranque se hace de forma síncrona (útil en tests)
    @Value("${digemid.async:true}")
    private boolean async;

    private static final long LOCK_KEY = 8_675_309L;

    public enum EstadoCarga { PENDIENTE, EN_PROCESO, COMPLETADA, ERROR }

    private static final AtomicReference<EstadoCarga> estado =
            new AtomicReference<>(EstadoCarga.PENDIENTE);

    public static EstadoCarga getEstado() { return estado.get(); }

    // ── CommandLineRunner ─────────────────────────────────────────────────────

    @Override
    public void run(String... args) {
        if (async) {
            Thread t = new Thread(this::ejecutarCarga, "digemid-catalog-loader");
            t.setDaemon(true);
            t.start();
            log.info("📦 [DIGEMID] Carga del catálogo iniciada en segundo plano ({}). " +
                     "Fluxus continúa el arranque.", t.getName());
        } else {
            // Modo síncrono: usado en tests para evitar carreras de hilos
            ejecutarCarga();
        }
    }

    // ── Lógica principal de carga (paquete-visible para tests) ───────────────

    public void ejecutarCarga() {
        Connection lockConn = null;
        try {
            lockConn = dataSource.getConnection();
            lockConn.setAutoCommit(true);

            // ── 1. Advisory lock no-bloqueante ──────────────────────────────
            if (!adquirirLock(lockConn)) {
                log.info("📦 [DIGEMID] Otra instancia ya está cargando — esta instancia lo omite.");
                return;
            }

            estado.set(EstadoCarga.EN_PROCESO);
            log.info("📦 [DIGEMID] Estado: EN_PROCESO");

            // ── 2. Idempotencia: verificar si ya está cargado ───────────────
            long existentes = catalogoDigemidRepository.count();
            if (existentes >= minExpected) {
                log.info("✅ [DIGEMID] Catálogo ya cargado ({} productos). Estado: COMPLETADA", existentes);
                estado.set(EstadoCarga.COMPLETADA);
                return;
            }

            if (existentes > 0) {
                log.warn("⚠️ [DIGEMID] Catálogo incompleto ({} registros). Se validará el CSV antes de reemplazar.",
                        existentes);
            }

            // ── 3. Cargar y validar CSV en memoria (sin tocar el catálogo activo) ──
            List<CatalogoDigemid> registros = cargarYValidarCSV();
            // Si la validación falla, lanza excepción → catálogo activo queda intacto

            // ── 4. Reemplazo atómico dentro de una sola transacción ──────────
            // Si esta transacción falla → rollback → datos anteriores restaurados
            reemplazarCatalogoAtomicamente(registros);

            estado.set(EstadoCarga.COMPLETADA);
            log.info("✅ [DIGEMID] Catálogo reemplazado. {} productos. Estado: COMPLETADA", registros.size());

        } catch (Exception e) {
            estado.set(EstadoCarga.ERROR);
            log.error("❌ [DIGEMID] Error en la carga. Estado: ERROR. Catálogo anterior intacto. Causa: {}",
                    e.getMessage(), e);
        } finally {
            // ── 5. Liberar el lock ANTES de devolver la conexión al pool ────
            // HikariCP no cierra la sesión PostgreSQL al hacer conn.close().
            // Sin unlock explícito, el advisory lock permanece activo en la sesión
            // del pool y bloquea arranques posteriores dentro del mismo proceso.
            if (lockConn != null) {
                liberarLock(lockConn);
                try { lockConn.close(); } catch (Exception ex) {
                    log.warn("[DIGEMID] No se pudo cerrar la conexión del advisory lock: {}", ex.getMessage());
                }
            }
        }
    }

    // ── Helpers de advisory lock ─────────────────────────────────────────────

    private boolean adquirirLock(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, LOCK_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private void liberarLock(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, LOCK_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                boolean released = rs.getBoolean(1);
                if (!released) {
                    // Puede suceder si la conexión fue reciclada por el pool antes del unlock
                    log.warn("⚠️ [DIGEMID] pg_advisory_unlock() devolvió false — el lock ya no estaba activo en esta sesión.");
                }
            }
        } catch (Exception ex) {
            log.warn("⚠️ [DIGEMID] No se pudo liberar el advisory lock: {}", ex.getMessage());
        }
    }

    // ── Carga y validación del CSV en memoria ────────────────────────────────

    public List<CatalogoDigemid> cargarYValidarCSV() throws Exception {
        ClassPathResource resource = new ClassPathResource("digemid/catalogo.csv");

        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).setTrim(true).build();

        // LinkedHashMap para deduplicar por cod_prod preservando orden de aparición
        Map<String, CatalogoDigemid> deduped = new LinkedHashMap<>(20_000);
        Set<String> duplicados = new HashSet<>();
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, format)) {

            for (CSVRecord rec : parser) {
                if (rec.size() < 12) { skipped++; continue; }
                try {
                    String codProd = blank(rec.get(0));
                    String nomProd = blank(rec.get(1));

                    // Validación de campos obligatorios por registro
                    if (codProd == null || nomProd == null) { skipped++; continue; }

                    if (deduped.containsKey(codProd)) {
                        duplicados.add(codProd);
                        // Se conserva la primera aparición
                        continue;
                    }

                    deduped.put(codProd, CatalogoDigemid.builder()
                            .codProd(codProd).nomProd(nomProd)
                            .concent(blank(rec.get(2))).nomFormFarm(blank(rec.get(3)))
                            .presentac(blank(rec.get(4))).fraccion(parseFraccion(rec.get(5)))
                            .numRegSan(blank(rec.get(6))).nomTitular(blank(rec.get(7)))
                            .nomFabricante(blank(rec.get(8))).nomIfa(blank(rec.get(9)))
                            .nomRubro(blank(rec.get(10))).situacion(blank(rec.get(11)))
                            .build());
                } catch (Exception e) {
                    skipped++;
                }
            }
        }

        if (!duplicados.isEmpty()) {
            log.warn("⚠️ [DIGEMID] CSV contiene {} cod_prod duplicados — se conservó la primera aparición.", duplicados.size());
        }

        List<CatalogoDigemid> registros = new ArrayList<>(deduped.values());
        log.info("📋 [DIGEMID] CSV leído: {} registros únicos, {} omitidos.", registros.size(), skipped);

        // ── Validación de completitud ────────────────────────────────────────
        if (registros.size() < minExpected) {
            throw new IllegalStateException(String.format(
                    "CSV incompleto: %d registros únicos (mínimo esperado: %d). " +
                    "No se reemplaza el catálogo activo.", registros.size(), minExpected));
        }

        return registros;
    }

    // ── Reemplazo atómico del catálogo ───────────────────────────────────────

    public void reemplazarCatalogoAtomicamente(List<CatalogoDigemid> registros) {
        // Toda la operación (delete + insert) en una única transacción.
        // Si falla en cualquier punto → rollback → catálogo anterior restaurado.
        transactionTemplate.executeWithoutResult(status -> {
            catalogoDigemidRepository.deleteAllInBatch();

            int batchSize = 500;
            for (int i = 0; i < registros.size(); i += batchSize) {
                List<CatalogoDigemid> batch = registros.subList(i,
                        Math.min(i + batchSize, registros.size()));
                catalogoDigemidRepository.saveAll(batch);
                if (i > 0 && i % 5000 == 0) {
                    log.info("   ... {} registros insertados", i);
                }
            }
        });
    }

    // Acceso al TransactionTemplate para construcción de instancias en tests
    public TransactionTemplate getTransactionTemplateForTest() { return transactionTemplate; }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private String blank(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private BigDecimal parseFraccion(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ONE;
        try {
            return new BigDecimal(s.trim().replace(",", ".")).stripTrailingZeros();
        } catch (NumberFormatException e) {
            return BigDecimal.ONE;
        }
    }
}
