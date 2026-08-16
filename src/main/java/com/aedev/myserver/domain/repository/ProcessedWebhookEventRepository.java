package com.aedev.myserver.domain.repository;

import com.aedev.myserver.domain.entity.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, Long> {

    boolean existsByEventId(String eventId);
}