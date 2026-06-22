package com.cobre.eventnotifications.infrastructure.config;

import com.cobre.eventnotifications.application.port.DeliveryDispatcher;
import com.cobre.eventnotifications.domain.EventId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TEMPORARY {@link DeliveryDispatcher} that only logs. Replay still performs its atomic claim and
 * returns 202; the real asynchronous delivery (via {@code WebhookClient}) is wired in Phase 6.
 */
@Component
public class TemporaryDeliveryDispatcher implements DeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TemporaryDeliveryDispatcher.class);

    @Override
    public void dispatch(EventId id) {
        log.info("would dispatch delivery for notification {} (temporary no-op; real delivery in Phase 6)", id);
    }
}
