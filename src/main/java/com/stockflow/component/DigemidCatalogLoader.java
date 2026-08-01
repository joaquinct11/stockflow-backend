package com.stockflow.component;

import com.stockflow.entity.CatalogoDigemid;
import com.stockflow.repository.CatalogoDigemidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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

        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<CatalogoDigemid> batch = new ArrayList<>(500);
        int total = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = new CSVParser(reader, format)) {

            for (CSVRecord rec : parser) {
                // El CSV tiene 12 columnas: 0..11
                if (rec.size() < 12) { skipped++; continue; }

                try {
                    CatalogoDigemid item = CatalogoDigemid.builder()
                            .codProd(blank(rec.get(0)))
                            .nomProd(blank(rec.get(1)))
                            .concent(blank(rec.get(2)))
                            .nomFormFarm(blank(rec.get(3)))
                            .presentac(blank(rec.get(4)))
                            .fraccion(parseFraccion(rec.get(5)))
                            .numRegSan(blank(rec.get(6)))
                            .nomTitular(blank(rec.get(7)))
                            .nomFabricante(blank(rec.get(8)))
                            .nomIfa(blank(rec.get(9)))
                            .nomRubro(blank(rec.get(10)))
                            .situacion(blank(rec.get(11)))
                            .build();

                    batch.add(item);
                    total++;

                    if (batch.size() == 500) {
                        catalogoDigemidRepository.saveAll(batch);
                        batch.clear();
                        if (total % 5000 == 0) log.info("   ... {} productos cargados", total);
                    }
                } catch (Exception e) {
                    skipped++;
                }
            }

            if (!batch.isEmpty()) {
                catalogoDigemidRepository.saveAll(batch);
            }
        }

        log.info("✅ Catálogo DIGEMID cargado: {} productos ({} omitidos)", total, skipped);
    }

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
