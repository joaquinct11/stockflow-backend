package com.stockflow;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class StockFlowBackendApplication {

	@PostConstruct
	public void init() {
		// Forzar zona horaria de Perú (UTC-5) en toda la JVM.
		// Necesario cuando el servidor (Render) corre en UTC:
		// sin esto, LocalDateTime.now() retorna hora UTC y
		// una venta creada a las 10 PM Lima aparece como
		// 3 AM del día siguiente.
		TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
	}

	public static void main(String[] args) {
		SpringApplication.run(StockFlowBackendApplication.class, args);
	}

}
