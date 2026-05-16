package org.agty.mtx.service;

import org.agty.mtx.dto.ChatDetailDto;
import org.agty.mtx.dto.ChatDto;
import org.agty.mtx.dto.ChatRequestDto;
import org.agty.mtx.dto.GroupDto;
import org.agty.mtx.dto.UserSettingsDto;
import org.agty.mtx.dto.UserSettingsUpdateRequest;
import org.agty.mtx.entity.ChatMessage;
import org.agty.mtx.entity.ChatThread;
import org.agty.mtx.entity.UserAccount;
import org.agty.mtx.repository.ChatGroupRepository;
import org.agty.mtx.repository.ChatMessageRepository;
import org.agty.mtx.repository.ChatThreadRepository;
import org.agty.mtx.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:./target/test-chat-service.db",
        "server.servlet.session.store-dir=./target/test-sessions-service"
})
class ChatAppServiceIntegrationTest {

    @Autowired
    private ChatAppService chatAppService;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private ChatGroupRepository chatGroupRepository;
    @Autowired
    private ChatThreadRepository chatThreadRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @BeforeEach
    void cleanup() {
        chatMessageRepository.deleteAll();
        chatThreadRepository.deleteAll();
        chatGroupRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void ensureDefaults_createsDefaultGroupAndInitialChat() {
        UserAccount user = createUser("u1");

        chatAppService.ensureDefaults(user);

        List<GroupDto> groups = chatAppService.listGroups(user);
        assertEquals(1, groups.size());
        assertEquals("Основная группа", groups.get(0).getName());

        List<ChatDto> chats = chatAppService.listChats(user, groups.get(0).getId());
        assertEquals(1, chats.size());
        assertEquals("Новый чат", chats.get(0).getTitle());
        assertNotNull(chats.get(0).getModel());
        assertFalse(chats.get(0).getModel().isBlank());
    }

    @Test
    void deleteGroup_rejectsDeletingLastGroup() {
        UserAccount user = createUser("u2");
        chatAppService.ensureDefaults(user);
        Long onlyGroupId = chatAppService.listGroups(user).get(0).getId();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatAppService.deleteGroup(user, onlyGroupId));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("Нельзя удалить последнюю группу", ex.getReason());
    }

    @Test
    void saveUserSettings_normalizesOutOfRangeValues() {
        UserAccount user = createUser("u3");
        chatAppService.ensureDefaults(user);

        UserSettingsUpdateRequest request = new UserSettingsUpdateRequest();
        request.setFontSize(100);
        request.setMenuFontSize(1);
        request.setSidebarWidth(999);
        request.setAnswerNavSide("unexpected");
        request.setTheme("unexpected");

        UserSettingsDto saved = chatAppService.saveUserSettings(user, request);

        assertEquals(28, saved.getFontSize());
        assertEquals(10, saved.getMenuFontSize());
        assertEquals(520, saved.getSidebarWidth());
        assertEquals("right", saved.getAnswerNavSide());
        assertEquals("light", saved.getTheme());
    }

    @Test
    void getChatDetail_sumsTokenCounters() {
        UserAccount user = createUser("u4");
        chatAppService.ensureDefaults(user);
        ChatDto baseChat = chatAppService.listChats(user, null).get(0);

        ChatThread chat = chatThreadRepository.findByIdAndUser(baseChat.getId(), user).orElseThrow();
        chatMessageRepository.deleteAllByChat(chat);

        ChatMessage m1 = new ChatMessage();
        m1.setChat(chat);
        m1.setRole("assistant");
        m1.setContent("a1");
        m1.setMessageOrder(1);
        m1.setPromptTokens(10L);
        m1.setCompletionTokens(20L);
        chatMessageRepository.save(m1);

        ChatMessage m2 = new ChatMessage();
        m2.setChat(chat);
        m2.setRole("assistant");
        m2.setContent("a2");
        m2.setMessageOrder(2);
        m2.setPromptTokens(7L);
        m2.setCompletionTokens(3L);
        chatMessageRepository.save(m2);

        ChatDetailDto detail = chatAppService.getChatDetail(user, chat.getId());
        assertEquals(17L, detail.getInputTokens());
        assertEquals(23L, detail.getOutputTokens());
        assertEquals(40L, detail.getTotalTokens());
        assertEquals(2, detail.getMessages().size());
    }

    @Test
    void createChat_insertsAtTopWithSortOrderOne() {
        UserAccount user = createUser("u5");
        chatAppService.ensureDefaults(user);
        Long groupId = chatAppService.listGroups(user).get(0).getId();

        ChatRequestDto request = new ChatRequestDto();
        request.setTitle("Second");
        request.setGroupId(groupId);
        ChatDto created = chatAppService.createChat(user, request);

        List<ChatDto> chats = chatAppService.listChats(user, groupId);
        assertFalse(chats.isEmpty());
        assertEquals(created.getId(), chats.get(0).getId());
    }

    private UserAccount createUser(String username) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash("test-hash");
        return userAccountRepository.save(user);
    }
}
