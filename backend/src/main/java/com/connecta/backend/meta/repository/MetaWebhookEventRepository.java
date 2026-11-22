package com.connecta.backend.meta.repository;

import com.connecta.backend.meta.domain.MetaWebhookEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaWebhookEventRepository extends JpaRepository<MetaWebhookEvent, UUID> {
}
