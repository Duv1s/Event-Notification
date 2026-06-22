/**
 * Ports (interfaces) the application core uses to reach the outside world: repositories, the webhook
 * client, the notification publisher, and the clock.
 *
 * <p>Implemented by adapters under {@code infrastructure}; the core depends only on these
 * abstractions, never on their implementations.
 */
package com.cobre.eventnotifications.application.port;
