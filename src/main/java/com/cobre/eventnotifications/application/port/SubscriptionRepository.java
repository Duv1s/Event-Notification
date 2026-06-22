package com.cobre.eventnotifications.application.port;

import com.cobre.eventnotifications.domain.Subscription;
import com.cobre.eventnotifications.domain.SubscriptionId;
import java.util.Optional;

/** Outbound port for reading subscriptions (their CRUD is out of scope for this service). */
public interface SubscriptionRepository {

    /**
     * Resolves a subscription by id. Replay and delivery look it up by the {@code subscription_id} the
     * notification already stores, so the current url/secret/state are always used.
     */
    Optional<Subscription> findById(SubscriptionId id);
}
