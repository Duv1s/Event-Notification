package com.cobre.eventnotifications.infrastructure.config;

import com.cobre.eventnotifications.application.port.DeliveryDispatcher;
import com.cobre.eventnotifications.application.usecase.DeliverNotification;
import com.cobre.eventnotifications.domain.EventId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Real dispatcher: hands delivery to a bounded async executor and invokes {@link DeliverNotification}.
 * This makes replay end-to-end: claim (synchronous, returns 202) -&gt; async dispatch -&gt; HTTPS
 * delivery -&gt; attempt recorded. The {@code event_id} is put in the MDC for the delivery thread so
 * every delivery log line is correlatable; it is removed afterwards.
 */
@Component
public class AsyncDeliveryDispatcher implements DeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AsyncDeliveryDispatcher.class);

    private final DeliverNotification deliverNotification;

    public AsyncDeliveryDispatcher(DeliverNotification deliverNotification) {
        this.deliverNotification = deliverNotification;
    }

    @Async("webhookDeliveryExecutor")
    @Override
    public void dispatch(EventId id) {
        MDC.put("event_id", id.value());
        try {
            log.info("dispatching webhook delivery");
            deliverNotification.handle(id);
        } finally {
            MDC.remove("event_id");
        }
    }
}
