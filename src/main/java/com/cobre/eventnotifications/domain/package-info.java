/**
 * Core domain model of the Event Notifications service.
 *
 * <p>Holds the {@code Notification} aggregate together with {@code DeliveryAttempt} and {@code
 * Subscription}. This package must not depend on Spring, Jackson, or any infrastructure adapter; the
 * rule is enforced by ArchUnit.
 */
package com.cobre.eventnotifications.domain;
