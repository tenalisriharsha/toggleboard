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
class SegmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String environmentKey;

    @BeforeEach
    void setUp() throws Exception {
        environmentKey = "env-" + UUID.randomUUID().toString().substring(0, 8);
        String body = objectMapper.writeValueAsString(Map.of("key", environmentKey, "name", "Test Env"));
        mockMvc.perform(post("/api/v1/environments").contentType("application/json").content(body)
                        .header("X-Role", "admin"))
                .andExpect(status().isCreated());
    }

    @Test
    void createsAndFetchesASegment() throws Exception {
        String body = segmentBody("beta-users", "Beta Users");

        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/segments")
                        .contentType("application/json").content(body).header("X-Role", "admin"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("beta-users"))
                .andExpect(jsonPath("$.rules", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/segments/beta-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beta Users"));
    }

    @Test
    void creatingASegmentWithoutAdminRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/segments")
                        .contentType("application/json").content(segmentBody("beta-users", "Beta Users"))
                        .header("X-Role", "viewer"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsASegmentWithNoRules() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("key", "empty", "name", "Empty", "rules", List.of()));

        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/segments")
                        .contentType("application/json").content(body).header("X-Role", "admin"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesASegment() throws Exception {
        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/segments")
                        .contentType("application/json").content(segmentBody("beta-users", "Beta Users"))
                        .header("X-Role", "admin"))
                .andExpect(status().isCreated());

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", "Beta Users v2", "description", "updated",
                "rules", List.of(Map.of("attribute", "beta", "operator", "IN", "values", List.of("true", "yes")))));

        mockMvc.perform(put("/api/v1/environments/" + environmentKey + "/segments/beta-users")
                        .contentType("application/json").content(updateBody).header("X-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beta Users v2"));
    }

    @Test
    void deletesASegment() throws Exception {
        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/segments")
                        .contentType("application/json").content(segmentBody("beta-users", "Beta Users"))
                        .header("X-Role", "admin"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/environments/" + environmentKey + "/segments/beta-users")
                        .header("X-Role", "admin"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/segments/beta-users"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsAllSegmentsInAnEnvironment() throws Exception {
        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/segments")
                        .contentType("application/json").content(segmentBody("seg-a", "A"))
                        .header("X-Role", "admin"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/segments")
                        .contentType("application/json").content(segmentBody("seg-b", "B"))
                        .header("X-Role", "admin"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/segments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    private String segmentBody(String key, String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "key", key, "name", name, "description", "desc",
                "rules", List.of(Map.of("attribute", "beta", "operator", "EQUALS", "values", List.of("true")))));
    }
}
