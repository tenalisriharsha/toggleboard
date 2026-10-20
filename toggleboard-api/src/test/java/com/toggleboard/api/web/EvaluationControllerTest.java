package com.toggleboard.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String environmentKey;
    private String apiKey;

    @BeforeEach
    void setUp() throws Exception {
        environmentKey = "env-" + UUID.randomUUID().toString().substring(0, 8);
        String envBody = objectMapper.writeValueAsString(Map.of("key", environmentKey, "name", "Test Env"));
        String response = mockMvc.perform(post("/api/v1/environments").contentType("application/json").content(envBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        apiKey = objectMapper.readTree(response).get("apiKey").asText();

        String flagBody = objectMapper.writeValueAsString(Map.of(
                "key", "always-on", "name", "Always On", "description", "desc",
                "type", "BOOLEAN", "enabled", true, "rolloutPercentage", 100));
        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/flags")
                        .contentType("application/json").content(flagBody))
                .andExpect(status().isCreated());
    }

    @Test
    void evaluatesWithAValidApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/flags/always-on/evaluate")
                        .param("subjectKey", "user-1")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("ROLLOUT"));
    }

    @Test
    void rejectsEvaluationWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/flags/always-on/evaluate")
                        .param("subjectKey", "user-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsEvaluationWithWrongApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/flags/always-on/evaluate")
                        .param("subjectKey", "user-1")
                        .header("X-API-Key", "tgb_wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsApiKeyBelongingToAnotherEnvironment() throws Exception {
        String otherEnvKey = "env-" + UUID.randomUUID().toString().substring(0, 8);
        String otherEnvBody = objectMapper.writeValueAsString(Map.of("key", otherEnvKey, "name", "Other Env"));
        String response = mockMvc.perform(post("/api/v1/environments").contentType("application/json").content(otherEnvBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String otherApiKey = objectMapper.readTree(response).get("apiKey").asText();

        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/flags/always-on/evaluate")
                        .param("subjectKey", "user-1")
                        .header("X-API-Key", otherApiKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCrudEndpointsDoNotRequireAnApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/flags/always-on"))
                .andExpect(status().isOk());
    }
}
