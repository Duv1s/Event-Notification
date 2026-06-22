package com.cobre.eventnotifications.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root. Represents one notification to be delivered to a client's webhook, together with
 * its ordered delivery-attempt history.
 *
 * <p>This service does not own the event; the event's data ({@code eventType}, {@code content},
 * {@code occurredAt}) is embedded here. Identity is the {@link EventId} (== {@code event_id}).
 *
 * <p>State changes go exclusively through the behavioural methods below; there are no public status
 * setters and invalid transitions raise a {@link DomainException}. The {@code content} is sensitive
 * and is never exposed through {@link #toString()}.
 */
public final class Notification {

    private final EventId id;
    private final ClientId clientId;
    private final SubscriptionId subscriptionId;
    private final EventType eventType;
    private final String content;
    private final Instant occurredAt;
    private final List<DeliveryAttempt> attempts;
    private DeliveryStatus deliveryStatus;

    public Notification(
            EventId id,
            ClientId clientId,
            SubscriptionId subscriptionId,
            EventType eventType,
            String content,
            Instant occurredAt,
            DeliveryStatus deliveryStatus,
            List<DeliveryAttempt> attempts) {
        this.id = Objects.requireNonNull(id, "id");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        this.content = content;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.deliveryStatus = Objects.requireNonNull(deliveryStatus, "deliveryStatus");
        this.attempts = new ArrayList<>(attempts == null ? List.of() : attempts);
    }

    // --- Behaviour (state machine) ---

    /** PENDING -&gt; DELIVERING when a delivery starts. */
    public void beginDelivery() {
        transitionTo(DeliveryStatus.DELIVERING);
    }

    /** DELIVERING -&gt; COMPLETED, recording the successful attempt. */
    public void recordSuccess(DeliveryAttempt attempt) {
        recordAttemptAndTransition(attempt, DeliveryStatus.COMPLETED);
    }

    /** DELIVERING -&gt; RETRYING, recording the transient failure. */
    public void recordRetryableFailure(DeliveryAttempt attempt) {
        recordAttemptAndTransition(attempt, DeliveryStatus.RETRYING);
    }

    /** DELIVERING -&gt; FAILED, recording a non-retryable failure. */
    public void recordPermanentFailure(DeliveryAttempt attempt) {
        recordAttemptAndTransition(attempt, DeliveryStatus.FAILED);
    }

    /** -&gt; FAILED once the retry budget is exhausted (no new attempt is recorded). */
    public void markExhausted() {
        transitionTo(DeliveryStatus.FAILED);
    }

    /** FAILED -&gt; DELIVERING on replay; throws {@link NotReplayableException} if not FAILED. */
    public void replay() {
        if (!deliveryStatus.canBeReplayed()) {
            throw new NotReplayableException(id, deliveryStatus);
        }
        transitionTo(DeliveryStatus.DELIVERING);
    }

    public boolean canBeReplayed() {
        return deliveryStatus.canBeReplayed();
    }

    private void recordAttemptAndTransition(DeliveryAttempt attempt, DeliveryStatus target) {
        Objects.requireNonNull(attempt, "attempt");
        requireCanTransitionTo(target);
        attempts.add(attempt);
        this.deliveryStatus = target;
    }

    private void transitionTo(DeliveryStatus target) {
        requireCanTransitionTo(target);
        this.deliveryStatus = target;
    }

    private void requireCanTransitionTo(DeliveryStatus target) {
        if (!deliveryStatus.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(id, deliveryStatus, target);
        }
    }

    // --- Derived values (not stored as duplicated truth) ---

    public int attemptCount() {
        return attempts.size();
    }

    public Optional<Instant> lastAttemptAt() {
        return lastAttempt().map(DeliveryAttempt::attemptedAt);
    }

    public Optional<String> lastError() {
        return lastAttempt().map(DeliveryAttempt::error);
    }

    public Optional<WebhookUrl> lastUrl() {
        return lastAttempt().map(DeliveryAttempt::urlUsed);
    }

    private Optional<DeliveryAttempt> lastAttempt() {
        return attempts.isEmpty() ? Optional.empty() : Optional.of(attempts.get(attempts.size() - 1));
    }

    // --- Accessors ---

    public EventId id() {
        return id;
    }

    public ClientId clientId() {
        return clientId;
    }

    public SubscriptionId subscriptionId() {
        return subscriptionId;
    }

    public EventType eventType() {
        return eventType;
    }

    public String content() {
        return content;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public DeliveryStatus deliveryStatus() {
        return deliveryStatus;
    }

    /** An immutable snapshot of the ordered attempt history. */
    public List<DeliveryAttempt> attempts() {
        return List.copyOf(attempts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Notification other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Excludes {@code content} on purpose: it is sensitive and must never reach logs. */
    @Override
    public String toString() {
        return "Notification[id=" + id
                + ", clientId=" + clientId
                + ", subscriptionId=" + subscriptionId
                + ", eventType=" + eventType
                + ", occurredAt=" + occurredAt
                + ", deliveryStatus=" + deliveryStatus
                + ", attemptCount=" + attemptCount()
                + "]";
    }
}
