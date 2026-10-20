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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerTest {

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
    void environmentCreationIsAudited() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].entityType").value("ENVIRONMENT"))
                .andExpect(jsonPath("$[0].actor").value("system"));
    }

    @Test
    void flagCreateUpdateAndKillSwitchAreAllAudited() throws Exception {
        String flagBody = objectMapper.writeValueAsString(Map.of(
                "key", "new-nav", "name", "New Nav", "description", "desc",
                "type", "BOOLEAN", "enabled", true, "rolloutPercentage", 25));
        mockMvc.perform(post("/api/v1/environments/" + environmentKey + "/flags")
                        .contentType("application/json").content(flagBody)
                        .header("X-Role", "admin").header("X-Actor", "alice"))
                .andExpect(status().isCreated());

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", "New Nav", "description", "desc", "enabled", false, "rolloutPercentage", 25));
        mockMvc.perform(put("/api/v1/environments/" + environmentKey + "/flags/new-nav")
                        .contentType("application/json").content(updateBody)
                        .header("X-Role", "admin").header("X-Actor", "bob"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("KILL_SWITCH_DISABLED"))
                .andExpect(jsonPath("$[0].actor").value("bob"))
                .andExpect(jsonPath("$[1].action").value("CREATE"))
                .andExpect(jsonPath("$[1].actor").value("alice"));
    }

    @Test
    void auditCanBeFilteredByEntityType() throws Exception {
        mockMvc.perform(get("/api/v1/environments/" + environmentKey + "/audit")
                        .param("entityType", "FLAG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }
}
