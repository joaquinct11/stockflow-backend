package com.stockflow.component;

import com.stockflow.entity.CatalogoDigemid;
import com.stockflow.repository.CatalogoDigemidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class DigemidCatalogLoader implements CommandLineRunner {

    private final CatalogoDigemidRepository catalogoDigemidRepository;

    private static final long MIN_EXPECTED = 18_000;

    @Override
    public void run(String... args) throws Exception {
        long existentes = catalogoDigemidRepository.count();
        if (existentes >= MIN_EXPECTED) {
            log.info("✅ Catálogo DIGEMID ya cargado ({} productos)", existentes);
            return;
        }
        if (existentes > 0) {
            log.warn("⚠️  Catálogo DIGEMID incompleto ({} registros). Limpiando y recargando...", existentes);
            catalogoDigemidRepository.deleteAll();
        }

        log.info("📦 Cargando catálogo DIGEMID desde recursos...");

        ClassPathResource resource = new ClassPathResource("digemid/catalogo.csv");
        List<CatalogoDigemid> batch = new ArrayList<>(500);
        int total = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            reader.readLine(); // skip header

            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",", -1);
                if (cols.length < 12) continue;

                CatalogoDigemid item = CatalogoDigemid.builder()
                        .codProd(clean(cols[0]))
                        .nomProd(clean(cols[1]))
                        .concent(clean(cols[2]))
                        .nomFormFarm(clean(cols[3]))
                        .presentac(clean(cols[4]))
                        .fraccion(parseFraccion(clean(cols[5])))
                        .numRegSan(clean(cols[6]))
                        .nomTitular(clean(cols[7]))
                        .nomFabricante(clean(cols[8]))
                        .nomIfa(clean(cols[9]))
                        .nomRubro(clean(cols[10]))
                        .situacion(clean(cols[11]))
                        .build();

                batch.add(item);
                total++;

                if (batch.size() == 500) {
                    catalogoDigemidRepository.saveAll(batch);
                    batch.clear();
                    log.info("   ... {} productos cargados", total);
                }
            }

            if (!batch.isEmpty()) {
                catalogoDigemidRepository.saveAll(batch);
            }
        }

        log.info("✅ Catálogo DIGEMID cargado: {} productos", total);
    }

    private String clean(String s) {
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private BigDecimal parseFraccion(String s) {
        if (s == null || s.isEmpty()) return BigDecimal.ONE;
        try {
            // El Excel exporta como "30.0", "1.0", etc.
            return new BigDecimal(s.replace(",", ".")).stripTrailingZeros();
        } catch (NumberFormatException e) {
            return BigDecimal.ONE;
        }
    }
}
