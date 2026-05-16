package org.agty.mtx.controller;

import org.agty.mtx.dto.*;
import org.agty.mtx.entity.UserAccount;
import org.agty.mtx.service.ChatAppService;
import org.agty.mtx.service.CurrentUserService;
import org.agty.mtx.service.ModelCatalogService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
public class ChatController {

    private final ChatAppService chatAppService;
    private final CurrentUserService currentUserService;
    private final ModelCatalogService modelCatalogService;

    public ChatController(
            ChatAppService chatAppService,
            CurrentUserService currentUserService,
            ModelCatalogService modelCatalogService
    ) {
        this.chatAppService = chatAppService;
        this.currentUserService = currentUserService;
        this.modelCatalogService = modelCatalogService;
    }

    @GetMapping("/api/bootstrap")
    public BootstrapDto bootstrap() {
        UserAccount user = currentUserService.requireCurrentUser();
        chatAppService.ensureDefaults(user);
        UserSettingsDto settings = chatAppService.loadUserSettings(user);
        return new BootstrapDto(
                user.getUsername(),
                resolveDisplayName(user),
                resolveEmail(user),
                modelCatalogService.getDefaultModel(),
                modelCatalogService.getModels(),
                chatAppService.loadCollapsedGroupIds(user),
                settings.getTheme(),
                settings.getFontSize(),
                settings.getMenuFontSize(),
                settings.getSidebarWidth(),
                settings.getAnswerNavSide(),
                settings.getSelectedModel()
        );
    }

    @GetMapping("/api/models")
    public List<String> models() {
        return modelCatalogService.getModels();
    }

    @PostMapping("/api/models/refresh")
    public ModelCatalogDto refreshModels() {
        modelCatalogService.refreshModels();
        return new ModelCatalogDto(modelCatalogService.getDefaultModel(), modelCatalogService.getModels());
    }

    @GetMapping("/api/settings")
    public UserSettingsDto settings() {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.loadUserSettings(user);
    }

    @PutMapping("/api/settings")
    public UserSettingsDto updateSettings(@RequestBody UserSettingsUpdateRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.saveUserSettings(user, request);
    }

    @GetMapping("/api/stats/models")
    public ModelUsageResponseDto modelUsageStats() {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.loadModelUsage(user);
    }

    @GetMapping("/api/chats")
    public List<ChatDto> listChats(@RequestParam(required = false) Long groupId) {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.listChats(user, groupId);
    }

    @PostMapping("/api/chats")
    public ChatDto createChat(@RequestBody ChatRequestDto request) {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.createChat(user, request);
    }

    @PutMapping("/api/chats/{chatId}")
    public ChatDto updateChat(@PathVariable Long chatId, @RequestBody ChatUpdateRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.updateChat(user, chatId, request);
    }

    @PatchMapping("/api/chats/{chatId}/move")
    public void moveChat(@PathVariable Long chatId, @RequestBody ChatMoveRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        chatAppService.moveChatToGroup(user, chatId, request.getTargetGroupId());
    }

    @PatchMapping("/api/chats/reorder")
    public void reorderChats(@RequestBody ChatReorderRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        chatAppService.reorderChats(user, request.getGroupId(), request.getChatIds());
    }

    @PutMapping("/api/groups/collapsed")
    public void saveCollapsedGroups(@RequestBody CollapsedGroupsRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        chatAppService.saveCollapsedGroupIds(user, request == null ? null : request.getGroupIds());
    }

    @DeleteMapping("/api/chats/{chatId}")
    public void deleteChat(@PathVariable Long chatId) {
        UserAccount user = currentUserService.requireCurrentUser();
        chatAppService.deleteChat(user, chatId);
    }

    @DeleteMapping("/api/chats/{chatId}/group")
    public void clearChatGroup(@PathVariable Long chatId) {
        UserAccount user = currentUserService.requireCurrentUser();
        chatAppService.clearChatGroup(user, chatId);
    }

    @GetMapping("/api/chats/{chatId}")
    public ChatDetailDto getChat(@PathVariable Long chatId) {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.getChatDetail(user, chatId);
    }

    @PostMapping("/api/chats/{chatId}/messages")
    public AssistantReplyDto sendMessage(@PathVariable Long chatId, @RequestBody SendMessageRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.sendMessage(user, chatId, request);
    }

    @PostMapping(value = "/api/chats/{chatId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamChunkDto> streamMessage(@PathVariable Long chatId, @RequestBody SendMessageRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.streamMessage(user, chatId, request);
    }

    @PostMapping("/api/chats/{chatId}/messages/stop")
    public void stopStream(@PathVariable Long chatId) {
        UserAccount user = currentUserService.requireCurrentUser();
        chatAppService.stopStream(user, chatId);
    }

    @PostMapping(value = "/api/private/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamChunkDto> streamPrivateMessage(@RequestBody PrivateChatRequest request) {
        currentUserService.requireCurrentUser();
        return chatAppService.streamPrivateMessage(request);
    }

    private String resolveDisplayName(UserAccount user) {
        String configuredName = user.getDisplayName() == null ? "" : user.getDisplayName().trim();
        if (!configuredName.isBlank()) {
            return configuredName;
        }

        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        int atIndex = username.indexOf('@');
        if (atIndex > 0) {
            return username.substring(0, atIndex);
        }
        return username;
    }

    private String resolveEmail(UserAccount user) {
        String configuredEmail = user.getEmail() == null ? "" : user.getEmail().trim();
        if (!configuredEmail.isBlank()) {
            return configuredEmail;
        }
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        return username.contains("@") ? username : "";
    }
}
