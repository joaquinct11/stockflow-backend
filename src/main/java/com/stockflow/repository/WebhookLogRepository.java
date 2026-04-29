package com.stockflow.repository;

import com.stockflow.entity.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {

    boolean existsByWebhookIdAndTipo(String webhookId, String tipo);

    Optional<WebhookLog> findByWebhookIdAndTipo(String webhookId, String tipo);
}