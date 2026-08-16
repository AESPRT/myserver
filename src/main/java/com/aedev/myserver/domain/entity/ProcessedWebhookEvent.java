package com.aedev.myserver.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Records every PayMongo webhook event ID we have successfully processed.
 * <p>
 * PayMongo may redeliver the same event (retry on timeout, network error,
 * non-2xx response, etc). The unique constraint on {@code eventId} is the
 * actual guard against double-processing -- it is enforced by the database,
 * not just checked in application code, so two concurrent deliveries of the
 * same event cannot both "win" a check-then-act race.
 * <p>
 * The webhook handler attempts to insert this row BEFORE applying business
 * effects (transaction + subscription updates) inside the same DB
 * transaction. If the insert violates the unique constraint, the event is
 * a duplicate and processing is skipped -- see WebhookProcessingService.
 */
@Entity
@Table(name = "processed_webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PayMongo's event id, e.g. "evt_xxxxxxxx". Globally unique per PayMongo. */
    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    /** e.g. "checkout_session.payment.paid", "payment.failed" */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;
}