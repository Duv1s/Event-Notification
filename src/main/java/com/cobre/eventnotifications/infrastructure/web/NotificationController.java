package com.cobre.eventnotifications.infrastructure.web;

import com.cobre.eventnotifications.application.port.NotificationQuery;
import com.cobre.eventnotifications.application.usecase.GetNotification;
import com.cobre.eventnotifications.application.usecase.ListNotifications;
import com.cobre.eventnotifications.application.usecase.ReplayNotification;
import com.cobre.eventnotifications.domain.DeliveryStatus;
import com.cobre.eventnotifications.domain.EventId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Self-service REST API. Thin: parses/validates, maps DTO <-> domain, and delegates to use cases. */
@RestController
@RequestMapping("/v1/notification_events")
@Validated
@Tag(name = "Notification events", description = "Query and replay notification deliveries")
public class NotificationController {

    private final ListNotifications listNotifications;
    private final GetNotification getNotification;
    private final ReplayNotification replayNotification;
    private final ClientIdResolver clientIdResolver;

    public NotificationController(
            ListNotifications listNotifications,
            GetNotification getNotification,
            ReplayNotification replayNotification,
            ClientIdResolver clientIdResolver) {
        this.listNotifications = listNotifications;
        this.getNotification = getNotification;
        this.replayNotification = replayNotification;
        this.clientIdResolver = clientIdResolver;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_notifications:read')")
    @Operation(summary = "List the caller's notifications (keyset paginated)")
    public NotificationPageResponse list(
            @RequestParam(name = "occurred_from", required = false) String occurredFrom,
            @RequestParam(name = "occurred_to", required = false) String occurredTo,
            @RequestParam(name = "delivery_status", required = false) List<String> deliveryStatus,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "page_size", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        NotificationQuery query = new NotificationQuery(
                clientIdResolver.resolve(),
                parseInstant(occurredFrom, "occurred_from"),
                parseInstant(occurredTo, "occurred_to"),
                parseStatuses(deliveryStatus),
                cursor == null ? null : CursorCodec.decode(cursor),
                pageSize);
        return WebMapper.toPage(listNotifications.handle(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_notifications:read')")
    @Operation(summary = "Get a single notification with its delivery-attempt history")
    public NotificationDetailResponse get(@PathVariable String id) {
        return WebMapper.toDetail(getNotification.handle(new EventId(id), clientIdResolver.resolve()));
    }

    @PostMapping("/{id}/replay")
    @PreAuthorize("hasAuthority('SCOPE_notifications:replay')")
    @Operation(summary = "Replay a definitively failed notification (asynchronous)")
    public ResponseEntity<ReplayResponse> replay(@PathVariable String id) {
        replayNotification.handle(new EventId(id), clientIdResolver.resolve());
        return ResponseEntity.accepted()
                .location(URI.create("/v1/notification_events/" + id))
                .body(new ReplayResponse(id, PublicDeliveryStatus.IN_PROGRESS.name()));
    }

    private static Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(field + " must be an ISO-8601 instant");
        }
    }

    private static Set<DeliveryStatus> parseStatuses(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<DeliveryStatus> internal = new LinkedHashSet<>();
        for (String value : values) {
            for (String token : value.split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                internal.addAll(toPublicStatus(trimmed).toInternal());
            }
        }
        return internal;
    }

    private static PublicDeliveryStatus toPublicStatus(String value) {
        try {
            return PublicDeliveryStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("unknown delivery_status: " + value);
        }
    }
}
