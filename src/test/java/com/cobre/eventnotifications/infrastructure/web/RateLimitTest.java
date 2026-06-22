package com.cobre.eventnotifications.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cobre.eventnotifications.application.port.NotificationPage;
import com.cobre.eventnotifications.application.usecase.GetNotification;
import com.cobre.eventnotifications.application.usecase.ListNotifications;
import com.cobre.eventnotifications.application.usecase.ReplayNotification;
import com.cobre.eventnotifications.infrastructure.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
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
@TestPropertySource(properties = {"app.ratelimit.read-per-minute=2"})
class RateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListNotifications listNotifications;

    @MockitoBean
    private GetNotification getNotification;

    @MockitoBean
    private ReplayNotification replayNotification;

    @Test
    void exceedingTheReadTierReturns429() throws Exception {
        when(listNotifications.handle(any())).thenReturn(new NotificationPage(List.of(), null));
        RequestPostProcessor token = jwt().jwt(builder -> builder.claim("client_id", "CLIENT001"))
                .authorities(new SimpleGrantedAuthority("SCOPE_notifications:read"));

        mockMvc.perform(get("/v1/notification_events").with(token)).andExpect(status().isOk());
        mockMvc.perform(get("/v1/notification_events").with(token)).andExpect(status().isOk());

        mockMvc.perform(get("/v1/notification_events").with(token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("RateLimit-Limit", "2"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
