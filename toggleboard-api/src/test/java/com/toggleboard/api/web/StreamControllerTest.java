package com.toggleboard.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.streaming.FlagChangePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FlagChangePublisher flagChangePublisher;

    private String environmentKey;
    private String apiKey;

    @BeforeEach
    void setUp() throws Exception {
        environmentKey = "env-" + UUID.randomUUID().toString().substring(0, 8);
        String envBody = objectMapper.writeValueAsString(Map.of("key", environmentKey, "name", "Test Env"));
        String response = mockMvc.perform(post("/api/v1/environments").contentType("application/json").content(envBody)
                        .header("X-Role", "admin"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        apiKey = objectMapper.readTree(response).get("apiKey").asText();
    }

    @Test
    void subscribingWithAValidApiKeyStartsAnEventStream() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/stream").header("X-API-Key", apiKey))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());
    }

    @Test
    void rejectsSubscriptionWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsSubscriptionWithWrongApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/stream").header("X-API-Key", "tgb_wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsSubscriptionToAnUnknownEnvironmentAtTheApiKeyFilter() throws Exception {
        mockMvc.perform(get("/api/v1/environments/no-such-env/stream").header("X-API-Key", "tgb_whatever"))
                .andExpect(status().isForbidden());
    }

    @Test
    void streamDoesNotRequireARoleHeader() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/stream").header("X-API-Key", apiKey))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());
    }

    @Test
    void disconnectingRemovesTheSubscriberFromThePublisher() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/stream").header("X-API-Key", apiKey))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(flagChangePublisher.subscriberCount(environmentKey)).isEqualTo(1);

        result.getRequest().getAsyncContext().complete();

        assertThat(flagChangePublisher.subscriberCount(environmentKey)).isEqualTo(0);
    }
}
