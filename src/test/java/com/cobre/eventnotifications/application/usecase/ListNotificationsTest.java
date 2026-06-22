package com.cobre.eventnotifications.application.usecase;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cobre.eventnotifications.application.port.NotificationPage;
import com.cobre.eventnotifications.application.port.NotificationQuery;
import com.cobre.eventnotifications.application.port.NotificationRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ListNotificationsTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final ListNotifications useCase = new ListNotifications(notifications);

    @Test
    void delegatesToRepositorySearch() {
        NotificationQuery query = new NotificationQuery(Fixtures.CLIENT_ID, null, null, Set.of(), null, 20);
        NotificationPage page = new NotificationPage(List.of(Fixtures.failed()), null);
        when(notifications.search(query)).thenReturn(page);

        assertSame(page, useCase.handle(query));
        verify(notifications).search(query);
    }
}
