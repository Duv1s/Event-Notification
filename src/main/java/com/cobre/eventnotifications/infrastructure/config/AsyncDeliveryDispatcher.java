package com.cobre.eventnotifications.infrastructure.config;

import com.cobre.eventnotifications.application.port.DeliveryDispatcher;
import com.cobre.eventnotifications.application.usecase.DeliverNotification;
import com.cobre.eventnotifications.domain.EventId;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Real dispatcher: hands delivery to a bounded async executor and invokes {@link DeliverNotification}.
 * This makes replay end-to-end: claim (synchronous, returns 202) -&gt; async dispatch -&gt; HTTPS
 * delivery -&gt; attempt recorded.
 */
@Component
public class AsyncDeliveryDispatcher implements DeliveryDispatcher {

    private final DeliverNotification deliverNotification;

    public AsyncDeliveryDispatcher(DeliverNotification deliverNotification) {
        this.deliverNotification = deliverNotification;
    }

    @Async("webhookDeliveryExecutor")
    @Override
    public void dispatch(EventId id) {
        deliverNotification.handle(id);
    }
}
