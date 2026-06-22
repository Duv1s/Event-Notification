package com.cobre.eventnotifications.infrastructure.persistence;

import com.cobre.eventnotifications.application.port.SubscriptionRepository;
import com.cobre.eventnotifications.domain.Subscription;
import com.cobre.eventnotifications.domain.SubscriptionId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Thread-safe in-memory reference implementation of {@link SubscriptionRepository}, populated from the
 * subscriptions seed at startup. The production target is a separate subscriptions bounded context.
 */
@Repository
public class InMemorySubscriptionRepository implements SubscriptionRepository {

    private final Map<SubscriptionId, Subscription> store = new ConcurrentHashMap<>();

    /** Seeding hook (not part of the port): registers a subscription. */
    public void add(Subscription subscription) {
        store.put(subscription.subscriptionId(), subscription);
    }

    @Override
    public Optional<Subscription> findById(SubscriptionId id) {
        return Optional.ofNullable(store.get(id));
    }
}
