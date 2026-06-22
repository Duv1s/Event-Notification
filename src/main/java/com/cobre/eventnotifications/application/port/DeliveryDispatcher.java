package com.cobre.eventnotifications.application.port;

import com.cobre.eventnotifications.domain.EventId;

/**
 * Outbound port that triggers asynchronous delivery of a notification. The asynchronous
 * implementation lives in infrastructure; keeping it behind a port lets the application stay pure and
 * enables the "claim (CAS) synchronously, then return 202" semantics of replay.
 */
public interface DeliveryDispatcher {

    void dispatch(EventId id);
}
