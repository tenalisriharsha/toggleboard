package com.toggleboard.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FlagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String environmentKey;

    @BeforeEach
    void setUp() throws Exception {
        environmentKey = "env-" + UUID.randomUUID().toString().substring(0, 8);
        String body = objectMapper.writeValueAsString(Map.of("key", environmentKey, "name", "Test Env"));
        mockMvc.perform(post("/api/v1/environments").contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createsAndFetchesABooleanFlag() throws Exception {
        String flagBody = objectMapper.writeValueAsString(Map.of(
                "key", "new-nav", "name", "New Nav", "description", "desc",
                "type", "BOOLEAN", "enabled", true, "rolloutPercentage", 25));

        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/flags")
                        .contentType("application/json").content(flagBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("new-nav"))
                .andExpect(jsonPath("$.rolloutPercentage").value(25));

        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/flags/new-nav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Nav"));
    }

    @Test
    void createsAMultivariateFlagWithVariants() throws Exception {
        String flagBody = objectMapper.writeValueAsString(Map.of(
                "key", "checkout-copy", "name", "Checkout Copy", "description", "desc",
                "type", "MULTIVARIATE", "enabled", true, "rolloutPercentage", 0,
                "variants", List.of(
                        Map.of("key", "control", "value", "Buy now", "weight", 50),
                        Map.of("key", "treatment", "value", "Purchase", "weight", 50)),
                "defaultVariantKey", "control"));

        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/flags")
                        .contentType("application/json").content(flagBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variants", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void rejectsMultivariateFlagWithBadWeights() throws Exception {
        String flagBody = objectMapper.writeValueAsString(Map.of(
                "key", "bad-weights", "name", "Bad", "description", "desc",
                "type", "MULTIVARIATE", "enabled", true, "rolloutPercentage", 0,
                "variants", List.of(
                        Map.of("key", "a", "value", "A", "weight", 40),
                        Map.of("key", "b", "value", "B", "weight", 40)),
                "defaultVariantKey", "a"));

        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/flags")
                        .contentType("application/json").content(flagBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnknownFlagReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/flags/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesAFlag() throws Exception {
        createBooleanFlag("toggle-me", true, 10);

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", "Toggle Me v2", "description", "updated", "enabled", false, "rolloutPercentage", 80));

        mockMvc.perform(put("/api/v1/environments/" + environmentKey + "/flags/toggle-me")
                        .contentType("application/json").content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.rolloutPercentage").value(80));
    }

    @Test
    void deletesAFlag() throws Exception {
        createBooleanFlag("delete-me", true, 10);

        mockMvc.perform(delete("/api/v1/environments/" + environmentKey + "/flags/delete-me"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/flags/delete-me"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsOnlyFlagsForThisEnvironment() throws Exception {
        createBooleanFlag("flag-a", true, 0);
        createBooleanFlag("flag-b", true, 0);

        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    private void createBooleanFlag(String key, boolean enabled, int rolloutPercentage) throws Exception {
        String flagBody = objectMapper.writeValueAsString(Map.of(
                "key", key, "name", key, "description", "desc",
                "type", "BOOLEAN", "enabled", enabled, "rolloutPercentage", rolloutPercentage));
        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/flags")
                        .contentType("application/json").content(flagBody))
                .andExpect(status().isCreated());
    }
}
