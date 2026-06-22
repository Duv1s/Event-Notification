package com.cobre.eventnotifications.application.usecase;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cobre.eventnotifications.application.exception.NotificationNotFoundException;
import com.cobre.eventnotifications.application.exception.ReplayNotAllowedException;
import com.cobre.eventnotifications.application.exception.SubscriptionNotEligibleException;
import com.cobre.eventnotifications.application.port.DeliveryDispatcher;
import com.cobre.eventnotifications.application.port.NotificationRepository;
import com.cobre.eventnotifications.application.port.SubscriptionRepository;
import com.cobre.eventnotifications.domain.EventTypeFilter;
import com.cobre.eventnotifications.domain.SubscriptionState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReplayNotificationTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final DeliveryDispatcher dispatcher = mock(DeliveryDispatcher.class);
    private final ReplayNotification useCase = new ReplayNotification(notifications, subscriptions, dispatcher);

    @Test
    void happyPathClaimsAndDispatches() {
        when(notifications.findByIdAndClientId(Fixtures.EVENT_ID, Fixtures.CLIENT_ID))
                .thenReturn(Optional.of(Fixtures.failed()));
        when(subscriptions.findById(Fixtures.SUBSCRIPTION_ID)).thenReturn(Optional.of(Fixtures.activeSubscription()));
        when(notifications.claimForReplay(Fixtures.EVENT_ID, Fixtures.CLIENT_ID))
                .thenReturn(true);

        useCase.handle(Fixtures.EVENT_ID, Fixtures.CLIENT_ID);

        verify(notifications).claimForReplay(Fixtures.EVENT_ID, Fixtures.CLIENT_ID);
        verify(dispatcher).dispatch(Fixtures.EVENT_ID);
    }

    @Test
    void throwsNotFoundAndDoesNotClaim() {
        when(notifications.findByIdAndClientId(any(), any())).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class, () -> useCase.handle(Fixtures.EVENT_ID, Fixtures.CLIENT_ID));
        verify(notifications, never()).claimForReplay(any(), any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void throwsConflictWhenNotFailed() {
        when(notifications.findByIdAndClientId(any(), any())).thenReturn(Optional.of(Fixtures.completed()));

        assertThrows(ReplayNotAllowedException.class, () -> useCase.handle(Fixtures.EVENT_ID, Fixtures.CLIENT_ID));
        verify(notifications, never()).claimForReplay(any(), any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void throwsConflictWhenSubscriptionInactive() {
        when(notifications.findByIdAndClientId(any(), any())).thenReturn(Optional.of(Fixtures.failed()));
        when(subscriptions.findById(any()))
                .thenReturn(Optional.of(Fixtures.subscription(EventTypeFilter.all(), SubscriptionState.PAUSED)));

        assertThrows(
                SubscriptionNotEligibleException.class, () -> useCase.handle(Fixtures.EVENT_ID, Fixtures.CLIENT_ID));
        verify(notifications, never()).claimForReplay(any(), any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void throwsConflictWhenSubscriptionNoLongerCoversEventType() {
        when(notifications.findByIdAndClientId(any(), any())).thenReturn(Optional.of(Fixtures.failed()));
        when(subscriptions.findById(any()))
                .thenReturn(Optional.of(
                        Fixtures.subscription(new EventTypeFilter(List.of("debit_*")), SubscriptionState.ACTIVE)));

        assertThrows(
                SubscriptionNotEligibleException.class, () -> useCase.handle(Fixtures.EVENT_ID, Fixtures.CLIENT_ID));
        verify(notifications, never()).claimForReplay(any(), any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void throwsConflictWhenClaimLosesTheRace() {
        when(notifications.findByIdAndClientId(any(), any())).thenReturn(Optional.of(Fixtures.failed()));
        when(subscriptions.findById(any())).thenReturn(Optional.of(Fixtures.activeSubscription()));
        when(notifications.claimForReplay(any(), any())).thenReturn(false);

        assertThrows(ReplayNotAllowedException.class, () -> useCase.handle(Fixtures.EVENT_ID, Fixtures.CLIENT_ID));
        verify(dispatcher, never()).dispatch(any());
    }
}
