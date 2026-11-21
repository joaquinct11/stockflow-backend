package com.stockflow.component;

import com.stockflow.entity.Ubigeo;
import com.stockflow.repository.UbigeoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class UbigeoCatalogLoader implements CommandLineRunner {

    private static final int MIN_EXPECTED = 1800;
    private static final int BATCH_SIZE   = 500;
    private static final String CSV_PATH  = "ubigeos/ubigeos.csv";
    private static final long ADVISORY_LOCK_KEY = 7_654_321L;

    private final UbigeoRepository ubigeoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Boolean lockAcquired = (Boolean) entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(:key)")
                .setParameter("key", ADVISORY_LOCK_KEY)
                .getSingleResult();

        if (Boolean.FALSE.equals(lockAcquired)) {
            log.info("UbigeoCatalogLoader: otra instancia ya está cargando el catálogo, omitiendo.");
            return;
        }

        long count = ubigeoRepository.count();
        if (count >= MIN_EXPECTED) {
            log.info("UbigeoCatalogLoader: catálogo ya tiene {} registros, omitiendo carga.", count);
            return;
        }

        log.info("UbigeoCatalogLoader: cargando catálogo de ubigeos desde {}", CSV_PATH);
        ClassPathResource resource = new ClassPathResource(CSV_PATH);

        List<Ubigeo> batch = new ArrayList<>(BATCH_SIZE);
        int total = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line = reader.readLine(); // saltar encabezado
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",", 4);
                if (parts.length < 4) continue;

                Ubigeo ubigeo = Ubigeo.builder()
                        .codigo(parts[0].trim())
                        .departamento(parts[1].trim())
                        .provincia(parts[2].trim())
                        .distrito(parts[3].trim())
                        .build();
                batch.add(ubigeo);

                if (batch.size() == BATCH_SIZE) {
                    ubigeoRepository.saveAll(batch);
                    ubigeoRepository.flush();
                    total += batch.size();
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            ubigeoRepository.saveAll(batch);
            total += batch.size();
        }

        log.info("UbigeoCatalogLoader: {} ubigeos cargados correctamente.", total);
    }
}
