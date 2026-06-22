package com.cobre.eventnotifications.infrastructure.web;

import com.cobre.eventnotifications.application.port.NotificationPage;
import com.cobre.eventnotifications.domain.DeliveryAttempt;
import com.cobre.eventnotifications.domain.Notification;
import java.time.Instant;

/** Maps domain objects to the public API DTOs, applying the internal -> public status mapping. */
final class WebMapper {

    private WebMapper() {}

    static NotificationSummaryResponse toSummary(Notification notification) {
        return new NotificationSummaryResponse(
                notification.id().value(),
                notification.eventType().value(),
                notification.occurredAt().toString(),
                PublicDeliveryStatus.fromInternal(notification.deliveryStatus()).name(),
                notification.attemptCount(),
                notification.lastAttemptAt().map(Instant::toString).orElse(null));
    }

    static NotificationDetailResponse toDetail(Notification notification) {
        return new NotificationDetailResponse(
                notification.id().value(),
                notification.eventType().value(),
                notification.occurredAt().toString(),
                PublicDeliveryStatus.fromInternal(notification.deliveryStatus()).name(),
                notification.attemptCount(),
                notification.lastAttemptAt().map(Instant::toString).orElse(null),
                notification.attempts().stream().map(WebMapper::toAttempt).toList());
    }

    static NotificationPageResponse toPage(NotificationPage page) {
        return new NotificationPageResponse(
                page.items().stream().map(WebMapper::toSummary).toList(),
                page.nextCursor() == null ? null : CursorCodec.encode(page.nextCursor()));
    }

    private static DeliveryAttemptResponse toAttempt(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.attemptNumber(),
                attempt.attemptedAt().toString(),
                attempt.urlUsed().value(),
                attempt.httpStatus(),
                attempt.result().name(),
                attempt.error());
    }
}
