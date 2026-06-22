package com.cobre.eventnotifications.application.usecase;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cobre.eventnotifications.application.exception.NotificationNotFoundException;
import com.cobre.eventnotifications.application.port.NotificationRepository;
import com.cobre.eventnotifications.domain.Notification;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetNotificationTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final GetNotification useCase = new GetNotification(notifications);

    @Test
    void returnsNotificationWhenFound() {
        Notification notification = Fixtures.failed();
        when(notifications.findByIdAndClientId(Fixtures.EVENT_ID, Fixtures.CLIENT_ID))
                .thenReturn(Optional.of(notification));

        assertSame(notification, useCase.handle(Fixtures.EVENT_ID, Fixtures.CLIENT_ID));
    }

    @Test
    void throwsNotFoundWhenMissing() {
        when(notifications.findByIdAndClientId(Fixtures.EVENT_ID, Fixtures.CLIENT_ID))
                .thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class, () -> useCase.handle(Fixtures.EVENT_ID, Fixtures.CLIENT_ID));
    }
}
