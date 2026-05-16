package org.agty.mtx.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.agty.mtx.repository.ChatGroupRepository;
import org.agty.mtx.repository.ChatMessageRepository;
import org.agty.mtx.repository.ChatThreadRepository;
import org.agty.mtx.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:./target/test-group-controller.db",
        "server.servlet.session.store-dir=./target/test-sessions-web"
})
@AutoConfigureMockMvc
class GroupControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ChatThreadRepository chatThreadRepository;
    @Autowired
    private ChatGroupRepository chatGroupRepository;

    @BeforeEach
    void cleanup() {
        chatMessageRepository.deleteAll();
        chatThreadRepository.deleteAll();
        chatGroupRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void groupsEndpoint_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createListAndReorderGroups_worksForAuthorizedUser() throws Exception {
        MockHttpSession session = registerAndGetSession("group-user", "password123");

        String createBody = "{\"name\":\"Team A\"}";
        String created = mockMvc.perform(post("/api/groups")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode createdNode = objectMapper.readTree(created);
        long createdId = createdNode.path("id").asLong();
        assertTrue(createdId > 0);

        String list = mockMvc.perform(get("/api/groups").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode listNode = objectMapper.readTree(list);
        assertTrue(listNode.isArray());
        assertTrue(listNode.size() >= 2); // default + created

        String reorderBody = "{\"ids\":[" + createdId + "]}";
        mockMvc.perform(patch("/api/groups/reorder")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderBody))
                .andExpect(status().isOk());

        String listAfter = mockMvc.perform(get("/api/groups").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode after = objectMapper.readTree(listAfter);
        assertEquals(createdId, after.get(0).path("id").asLong());
    }

    private MockHttpSession registerAndGetSession(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return (MockHttpSession) mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
