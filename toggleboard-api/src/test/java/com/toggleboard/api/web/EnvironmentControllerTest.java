package com.toggleboard.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EnvironmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsAnEnvironmentAndReturnsItsApiKey() throws Exception {
        String key = uniqueKey();
        String body = objectMapper.writeValueAsString(new CreateRequest(key, "Production"));

        mockMvc.perform(post("/api/v1/environments").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.apiKey").isNotEmpty());
    }

    @Test
    void rejectsDuplicateEnvironmentKey() throws Exception {
        String key = uniqueKey();
        String body = objectMapper.writeValueAsString(new CreateRequest(key, "Production"));

        mockMvc.perform(post("/api/v1/environments").contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/environments").contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsBlankEnvironmentName() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateRequest(uniqueKey(), ""));

        mockMvc.perform(post("/api/v1/environments").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByKeyReturns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/environments/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listIncludesCreatedEnvironments() throws Exception {
        String key = uniqueKey();
        String body = objectMapper.writeValueAsString(new CreateRequest(key, "Staging"));
        mockMvc.perform(post("/api/v1/environments").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/environments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == '" + key + "')]").exists());
    }

    private String uniqueKey() {
        return "env-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record CreateRequest(String key, String name) {
    }
}
