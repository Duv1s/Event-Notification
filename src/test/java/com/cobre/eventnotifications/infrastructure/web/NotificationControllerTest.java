package com.cobre.eventnotifications.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, JwtClientIdResolver.class})
@TestPropertySource(properties = {"app.ratelimit.read-per-minute=100000", "app.ratelimit.replay-per-minute=100000"})
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

    private static RequestPostProcessor read(String clientId) {
        return jwt().jwt(builder -> builder.claim("client_id", clientId))
                .authorities(new SimpleGrantedAuthority("SCOPE_notifications:read"));
    }

    private static RequestPostProcessor replay(String clientId) {
        return jwt().jwt(builder -> builder.claim("client_id", clientId))
                .authorities(new SimpleGrantedAuthority("SCOPE_notifications:replay"));
    }

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

    // --- functional ---

    @Test
    void listReturnsMappedSummariesAndEncodedCursor() throws Exception {
        Notification notification = notification(DeliveryStatus.DELIVERING);
        Cursor cursor = new Cursor(notification.occurredAt(), notification.id());
        when(listNotifications.handle(any())).thenReturn(new NotificationPage(List.of(notification), cursor));

        mockMvc.perform(get("/v1/notification_events").with(read(CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].notification_event_id").value("EVT001"))
                .andExpect(jsonPath("$.items[0].delivery_status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.next_cursor").value(CursorCodec.encode(cursor)));
    }

    @Test
    void listAcceptsRealSignedToken() throws Exception {
        when(listNotifications.handle(any())).thenReturn(new NotificationPage(List.of(), null));

        mockMvc.perform(get("/v1/notification_events")
                        .header("Authorization", "Bearer " + JwtTestTokens.valid(CLIENT, "notifications:read")))
                .andExpect(status().isOk());
    }

    @Test
    void mapsPublicStatusFilterToInternalStates() throws Exception {
        when(listNotifications.handle(any())).thenReturn(new NotificationPage(List.of(), null));

        mockMvc.perform(get("/v1/notification_events")
                        .param("delivery_status", "IN_PROGRESS")
                        .with(read(CLIENT)))
                .andExpect(status().isOk());

        ArgumentCaptor<NotificationQuery> captor = ArgumentCaptor.forClass(NotificationQuery.class);
        verify(listNotifications).handle(captor.capture());
        assertEquals(
                Set.of(DeliveryStatus.DELIVERING, DeliveryStatus.RETRYING),
                captor.getValue().statuses());
    }

    @Test
    void scopesQueryToTheTokenClientId() throws Exception {
        when(listNotifications.handle(any())).thenReturn(new NotificationPage(List.of(), null));

        mockMvc.perform(get("/v1/notification_events").with(read("CLIENT002"))).andExpect(status().isOk());

        ArgumentCaptor<NotificationQuery> captor = ArgumentCaptor.forClass(NotificationQuery.class);
        verify(listNotifications).handle(captor.capture());
        assertEquals(new ClientId("CLIENT002"), captor.getValue().clientId());
    }

    @Test
    void decodesCursorIntoQuery() throws Exception {
        when(listNotifications.handle(any())).thenReturn(new NotificationPage(List.of(), null));
        Cursor cursor = new Cursor(Instant.parse("2024-03-15T09:30:22Z"), new EventId("EVT005"));

        mockMvc.perform(get("/v1/notification_events")
                        .param("cursor", CursorCodec.encode(cursor))
                        .with(read(CLIENT)))
                .andExpect(status().isOk());

        ArgumentCaptor<NotificationQuery> captor = ArgumentCaptor.forClass(NotificationQuery.class);
        verify(listNotifications).handle(captor.capture());
        assertEquals(cursor, captor.getValue().cursor());
    }

    @Test
    void rejectsPageSizeOutOfRange() throws Exception {
        mockMvc.perform(get("/v1/notification_events").param("page_size", "0").with(read(CLIENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/v1/notification_events")
                        .param("delivery_status", "BOGUS")
                        .with(read(CLIENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsInvalidCursor() throws Exception {
        mockMvc.perform(get("/v1/notification_events")
                        .param("cursor", "@@notbase64@@")
                        .with(read(CLIENT)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOccurredFromNotBeforeOccurredTo() throws Exception {
        mockMvc.perform(get("/v1/notification_events")
                        .param("occurred_from", "2024-03-15T10:00:00Z")
                        .param("occurred_to", "2024-03-15T09:00:00Z")
                        .with(read(CLIENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void getReturnsDetailWithAttempts() throws Exception {
        when(getNotification.handle(any(), any())).thenReturn(notification(DeliveryStatus.FAILED));

        mockMvc.perform(get("/v1/notification_events/EVT001").with(read(CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery_status").value("FAILED"))
                .andExpect(jsonPath("$.attempts[0].attempt_number").value(1));
    }

    @Test
    void getReturns404ProblemDetail() throws Exception {
        when(getNotification.handle(any(), any())).thenThrow(new NotificationNotFoundException(new EventId("EVTX")));

        mockMvc.perform(get("/v1/notification_events/EVTX").with(read(CLIENT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void replayReturns202WithLocationAndInProgress() throws Exception {
        mockMvc.perform(post("/v1/notification_events/EVT001/replay").with(replay(CLIENT)))
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

        mockMvc.perform(post("/v1/notification_events/EVT001/replay").with(replay(CLIENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLAY_NOT_ALLOWED"));
    }

    @Test
    void replayReturns409WhenSubscriptionNotEligible() throws Exception {
        doThrow(new SubscriptionNotEligibleException(new EventId("EVT001"), new SubscriptionId("SUB001")))
                .when(replayNotification)
                .handle(any(), any());

        mockMvc.perform(post("/v1/notification_events/EVT001/replay").with(replay(CLIENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_ELIGIBLE"));
    }

    // --- authentication (decoder) ---

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/v1/notification_events")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        mockMvc.perform(get("/v1/notification_events")
                        .header("Authorization", "Bearer " + JwtTestTokens.expired(CLIENT, "notifications:read")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        mockMvc.perform(get("/v1/notification_events")
                        .header("Authorization", "Bearer " + JwtTestTokens.wrongAudience(CLIENT, "notifications:read")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithoutClientIdClaim() throws Exception {
        mockMvc.perform(get("/v1/notification_events")
                        .header("Authorization", "Bearer " + JwtTestTokens.withoutClientId("notifications:read")))
                .andExpect(status().isUnauthorized());
    }

    // --- authorization (scopes) ---

    @Test
    void listForbiddenWithoutReadScope() throws Exception {
        RequestPostProcessor noScope = jwt().jwt(builder -> builder.claim("client_id", CLIENT));
        mockMvc.perform(get("/v1/notification_events").with(noScope)).andExpect(status().isForbidden());
    }

    @Test
    void replayForbiddenWithOnlyReadScope() throws Exception {
        mockMvc.perform(post("/v1/notification_events/EVT001/replay").with(read(CLIENT)))
                .andExpect(status().isForbidden());
    }
}
