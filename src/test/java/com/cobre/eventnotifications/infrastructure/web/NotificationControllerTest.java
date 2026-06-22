package com.cobre.eventnotifications.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cobre.eventnotifications.application.exception.NotificationNotFoundException;
import com.cobre.eventnotifications.application.exception.ReplayNotAllowedException;
import com.cobre.eventnotifications.application.exception.SubscriptionNotEligibleException;
import com.cobre.eventnotifications.application.port.Cursor;
import com.cobre.eventnotifications.application.port.NotificationPage;
import com.cobre.eventnotifications.application.port.NotificationQuery;
import com.cobre.eventnotifications.application.usecase.GetNotification;
import com.cobre.eventnotifications.application.usecase.ListNotifications;
import com.cobre.eventnotifications.application.usecase.ReplayNotification;
import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.DeliveryAttempt;
import com.cobre.eventnotifications.domain.DeliveryResult;
import com.cobre.eventnotifications.domain.DeliveryStatus;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.EventType;
import com.cobre.eventnotifications.domain.Notification;
import com.cobre.eventnotifications.domain.SubscriptionId;
import com.cobre.eventnotifications.domain.WebhookUrl;
import com.cobre.eventnotifications.infrastructure.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, HeaderClientIdResolver.class})
class NotificationControllerTest {

    private static final String CLIENT = "CLIENT001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListNotifications listNotifications;

    @MockitoBean
    private GetNotification getNotification;

    @MockitoBean
    private ReplayNotification replayNotification;

    private static Notification notification(DeliveryStatus status) {
        return new Notification(
                new EventId("EVT001"),
                new ClientId(CLIENT),
                new SubscriptionId("SUB001"),
                new EventType("credit_transfer"),
                "content",
                Instant.parse("2024-03-15T09:30:22Z"),
                status,
                List.of(new DeliveryAttempt(
                        1,
                        Instant.parse("2024-03-15T09:31:00Z"),
                        new WebhookUrl("https://example.com/hook"),
                        503,
                        DeliveryResult.FAILURE,
                        "err")));
    }

    @Test
    void listReturnsMappedSummariesAndEncodedCursor() throws Exception {
        Notification notification = notification(DeliveryStatus.DELIVERING);
        Cursor cursor = new Cursor(notification.occurredAt(), notification.id());
        when(listNotifications.handle(any())).thenReturn(new NotificationPage(List.of(notification), cursor));

        mockMvc.perform(get("/v1/notification_events").header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].notification_event_id").value("EVT001"))
                .andExpect(jsonPath("$.items[0].delivery_status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[0].attempt_count").value(1))
                .andExpect(jsonPath("$.next_cursor").value(CursorCodec.encode(cursor)));
    }

    @Test
    void mapsPublicStatusFilterToInternalStates() throws Exception {
        when(listNotifications.handle(any())).thenReturn(new NotificationPage(List.of(), null));

        mockMvc.perform(get("/v1/notification_events")
                        .param("delivery_status", "IN_PROGRESS")
                        .header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isOk());

        ArgumentCaptor<NotificationQuery> captor = ArgumentCaptor.forClass(NotificationQuery.class);
        verify(listNotifications).handle(captor.capture());
        assertEquals(
                Set.of(DeliveryStatus.DELIVERING, DeliveryStatus.RETRYING),
                captor.getValue().statuses());
    }

    @Test
    void decodesCursorIntoQuery() throws Exception {
        when(listNotifications.handle(any())).thenReturn(new NotificationPage(List.of(), null));
        Cursor cursor = new Cursor(Instant.parse("2024-03-15T09:30:22Z"), new EventId("EVT005"));

        mockMvc.perform(get("/v1/notification_events")
                        .param("cursor", CursorCodec.encode(cursor))
                        .header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isOk());

        ArgumentCaptor<NotificationQuery> captor = ArgumentCaptor.forClass(NotificationQuery.class);
        verify(listNotifications).handle(captor.capture());
        assertEquals(cursor, captor.getValue().cursor());
    }

    @Test
    void rejectsPageSizeOutOfRange() throws Exception {
        mockMvc.perform(get("/v1/notification_events")
                        .param("page_size", "0")
                        .header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/v1/notification_events")
                        .param("page_size", "101")
                        .header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/v1/notification_events")
                        .param("delivery_status", "BOGUS")
                        .header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsInvalidCursor() throws Exception {
        mockMvc.perform(get("/v1/notification_events")
                        .param("cursor", "@@notbase64@@")
                        .header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidOccurredFrom() throws Exception {
        mockMvc.perform(get("/v1/notification_events")
                        .param("occurred_from", "not-a-date")
                        .header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsDetailWithAttempts() throws Exception {
        when(getNotification.handle(any(), any())).thenReturn(notification(DeliveryStatus.FAILED));

        mockMvc.perform(get("/v1/notification_events/EVT001").header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery_status").value("FAILED"))
                .andExpect(jsonPath("$.attempts[0].attempt_number").value(1))
                .andExpect(jsonPath("$.attempts[0].result").value("FAILURE"));
    }

    @Test
    void getReturns404ProblemDetail() throws Exception {
        when(getNotification.handle(any(), any())).thenThrow(new NotificationNotFoundException(new EventId("EVTX")));

        mockMvc.perform(get("/v1/notification_events/EVTX").header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Notification not found"))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/v1/notification_events/EVTX"));
    }

    @Test
    void replayReturns202WithLocationAndInProgress() throws Exception {
        mockMvc.perform(post("/v1/notification_events/EVT001/replay")
                        .header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/v1/notification_events/EVT001"))
                .andExpect(jsonPath("$.delivery_status").value("IN_PROGRESS"));

        verify(replayNotification).handle(new EventId("EVT001"), new ClientId(CLIENT));
    }

    @Test
    void replayReturns409WhenNotReplayable() throws Exception {
        doThrow(new ReplayNotAllowedException(new EventId("EVT001"), DeliveryStatus.COMPLETED))
                .when(replayNotification)
                .handle(any(), any());

        mockMvc.perform(post("/v1/notification_events/EVT001/replay")
                        .header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLAY_NOT_ALLOWED"));
    }

    @Test
    void replayReturns409WhenSubscriptionNotEligible() throws Exception {
        doThrow(new SubscriptionNotEligibleException(new EventId("EVT001"), new SubscriptionId("SUB001")))
                .when(replayNotification)
                .handle(any(), any());

        mockMvc.perform(post("/v1/notification_events/EVT001/replay")
                        .header(HeaderClientIdResolver.CLIENT_ID_HEADER, CLIENT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_ELIGIBLE"));
    }
}
