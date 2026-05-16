package org.agty.mtx.service;

import org.agty.mtx.dto.*;
import org.agty.mtx.entity.ChatGroup;
import org.agty.mtx.entity.ChatMessage;
import org.agty.mtx.entity.ChatThread;
import org.agty.mtx.entity.UserAccount;
import org.agty.mtx.entity.UserPreference;
import org.agty.mtx.repository.ChatGroupRepository;
import org.agty.mtx.repository.ChatMessageRepository;
import org.agty.mtx.repository.ChatThreadRepository;
import org.agty.mtx.repository.UserPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ChatAppService {
    private static final Logger log = LoggerFactory.getLogger(ChatAppService.class);
    private static final String DEFAULT_GROUP_NAME = "Основная группа";
    private static final String PREF_COLLAPSED_GROUPS = "collapsed_groups";
    private static final String PREF_THEME = "ui_theme";
    private static final String PREF_FONT_SIZE = "ui_font_size";
    private static final String PREF_MENU_FONT_SIZE = "ui_menu_font_size";
    private static final String PREF_SIDEBAR_WIDTH = "ui_sidebar_width";
    private static final String PREF_ANSWER_NAV_SIDE = "ui_answer_nav_side";
    private static final String PREF_SELECTED_MODEL = "selected_model";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    private static final String ANSWER_NAV_SIDE_LEFT = "left";
    private static final String ANSWER_NAV_SIDE_RIGHT = "right";
    private static final int DEFAULT_FONT_SIZE = 14;
    private static final int DEFAULT_MENU_FONT_SIZE = 13;
    private static final int DEFAULT_SIDEBAR_WIDTH = 320;

    private final ChatGroupRepository chatGroupRepository;
    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OllamaService ollamaService;
    private final CodexCliService codexCliService;
    private final CodexApiService codexApiService;
    private final ModelCatalogService modelCatalogService;
    private final TransactionTemplate transactionTemplate;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ConcurrentMap<Long, AtomicLong> chatStreamRevisions = new ConcurrentHashMap<>();

    public ChatAppService(
            ChatGroupRepository chatGroupRepository,
            ChatThreadRepository chatThreadRepository,
            ChatMessageRepository chatMessageRepository,
            OllamaService ollamaService,
            CodexCliService codexCliService,
            CodexApiService codexApiService,
            ModelCatalogService modelCatalogService,
            TransactionTemplate transactionTemplate,
            UserPreferenceRepository userPreferenceRepository
    ) {
        this.chatGroupRepository = chatGroupRepository;
        this.chatThreadRepository = chatThreadRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.ollamaService = ollamaService;
        this.codexCliService = codexCliService;
        this.codexApiService = codexApiService;
        this.modelCatalogService = modelCatalogService;
        this.transactionTemplate = transactionTemplate;
        this.userPreferenceRepository = userPreferenceRepository;
    }

    @Transactional
    public void ensureDefaults(UserAccount user) {
        ChatGroup defaultGroup = requireDefaultGroup(user);
        migrateUngroupedChatsToDefault(user, defaultGroup);

        if (!chatThreadRepository.existsByUser(user)) {
            ChatGroup firstGroup = chatGroupRepository.findAllByUserOrderBySortOrderAscUpdatedAtDesc(user)
                    .stream()
                    .findFirst()
                    .orElse(defaultGroup);

            ChatThread chat = new ChatThread();
            chat.setUser(user);
            chat.setGroup(firstGroup);
            chat.setTitle("Новый чат");
            chat.setModelName(modelCatalogService.getDefaultModel());
            chat.setSortOrder(nextChatSortOrder(user, firstGroup));
            chatThreadRepository.save(chat);
        }

        normalizeOrderIndexes(user);
    }

    @Transactional
    public void normalizeOrderIndexes(UserAccount user) {
        List<ChatGroup> groups = chatGroupRepository.findAllByUserOrderBySortOrderAscUpdatedAtDesc(user);
        int groupIndex = 1;
        for (ChatGroup group : groups) {
            if (group.getSortOrder() == null || group.getSortOrder() != groupIndex) {
                group.setSortOrder(groupIndex);
                chatGroupRepository.save(group);
            }
            groupIndex++;
        }

        normalizeChatOrderForGroup(user, null);
        for (ChatGroup group : groups) {
            normalizeChatOrderForGroup(user, group);
        }
    }

    @Transactional(readOnly = true)
    public List<GroupDto> listGroups(UserAccount user) {
        return chatGroupRepository.findAllByUserOrderBySortOrderAscUpdatedAtDesc(user)
                .stream()
                .map(group -> new GroupDto(
                        group.getId(),
                        group.getName(),
                        chatThreadRepository.countByUserAndGroup(user, group),
                        group.getUpdatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> loadCollapsedGroupIds(UserAccount user) {
        String raw = userPreferenceRepository.findByUserAndKey(user, PREF_COLLAPSED_GROUPS)
                .map(pref -> pref.getValue() == null ? "" : pref.getValue().trim())
                .orElse("");
        if (raw.isBlank()) {
            return List.of();
        }

        Set<Long> allowed = chatGroupRepository.findAllByUserOrderBySortOrderAscUpdatedAtDesc(user)
                .stream()
                .map(ChatGroup::getId)
                .collect(Collectors.toSet());

        List<Long> result = new ArrayList<>();
        for (String token : raw.split(",")) {
            String trimmed = token == null ? "" : token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(trimmed);
                if (allowed.contains(id) && !result.contains(id)) {
                    result.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    @Transactional
    public void saveCollapsedGroupIds(UserAccount user, List<Long> groupIds) {
        Set<Long> allowed = chatGroupRepository.findAllByUserOrderBySortOrderAscUpdatedAtDesc(user)
                .stream()
                .map(ChatGroup::getId)
                .collect(Collectors.toSet());

        List<Long> sanitized = new ArrayList<>();
        if (groupIds != null) {
            for (Long id : groupIds) {
                if (id == null || !allowed.contains(id) || sanitized.contains(id)) {
                    continue;
                }
                sanitized.add(id);
            }
        }

        String value = sanitized.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        var pref = userPreferenceRepository.findByUserAndKey(user, PREF_COLLAPSED_GROUPS)
                .orElseGet(() -> {
                    var item = new org.agty.mtx.entity.UserPreference();
                    item.setUser(user);
                    item.setKey(PREF_COLLAPSED_GROUPS);
                    return item;
                });
        pref.setValue(value);
        userPreferenceRepository.save(pref);
    }

    @Transactional(readOnly = true)
    public UserSettingsDto loadUserSettings(UserAccount user) {
        return new UserSettingsDto(
                loadThemePreference(user),
                loadIntPreference(user, PREF_FONT_SIZE, 11, 28, DEFAULT_FONT_SIZE),
                loadIntPreference(user, PREF_MENU_FONT_SIZE, 10, 24, DEFAULT_MENU_FONT_SIZE),
                loadIntPreference(user, PREF_SIDEBAR_WIDTH, 260, 520, DEFAULT_SIDEBAR_WIDTH),
                loadAnswerNavSidePreference(user),
                loadModelPreference(user)
        );
    }

    @Transactional
    public UserSettingsDto saveUserSettings(UserAccount user, UserSettingsUpdateRequest request) {
        UserSettingsDto current = loadUserSettings(user);
        if (request == null) {
            return current;
        }

        if (request.getTheme() != null) {
            savePreference(user, PREF_THEME, normalizeTheme(request.getTheme()));
        }
        if (request.getFontSize() != null) {
            savePreference(user, PREF_FONT_SIZE, String.valueOf(normalizeInt(request.getFontSize(), 11, 28, current.getFontSize())));
        }
        if (request.getMenuFontSize() != null) {
            savePreference(user, PREF_MENU_FONT_SIZE, String.valueOf(normalizeInt(request.getMenuFontSize(), 10, 24, current.getMenuFontSize())));
        }
        if (request.getSidebarWidth() != null) {
            savePreference(user, PREF_SIDEBAR_WIDTH, String.valueOf(normalizeInt(request.getSidebarWidth(), 260, 520, current.getSidebarWidth())));
        }
        if (request.getAnswerNavSide() != null) {
            savePreference(user, PREF_ANSWER_NAV_SIDE, normalizeAnswerNavSide(request.getAnswerNavSide()));
        }
        if (request.getSelectedModel() != null) {
            savePreference(user, PREF_SELECTED_MODEL, normalizeSelectedModel(request.getSelectedModel()));
        }

        return loadUserSettings(user);
    }

    @Transactional
    public GroupDto createGroup(UserAccount user, String name) {
        String safeName = sanitizeName(name, 120, "Новая группа");
        ChatGroup group = new ChatGroup();
        group.setUser(user);
        group.setName(safeName);
        group.setSortOrder(nextGroupSortOrder(user));
        ChatGroup saved = chatGroupRepository.save(group);
        return new GroupDto(saved.getId(), saved.getName(), 0L, saved.getUpdatedAt());
    }

    @Transactional
    public GroupDto updateGroup(UserAccount user, Long groupId, String name) {
        ChatGroup group = requireGroup(user, groupId);
        group.setName(sanitizeName(name, 120, group.getName()));
        group.touch();
        ChatGroup saved = chatGroupRepository.save(group);
        return new GroupDto(
                saved.getId(),
                saved.getName(),
                chatThreadRepository.countByUserAndGroup(user, saved),
                saved.getUpdatedAt()
        );
    }

    @Transactional
    public void reorderGroups(UserAccount user, List<Long> orderedIds) {
        List<ChatGroup> current = chatGroupRepository.findAllByUserOrderBySortOrderAscUpdatedAtDesc(user);
        Map<Long, ChatGroup> byId = current.stream().collect(Collectors.toMap(ChatGroup::getId, g -> g));

        List<ChatGroup> ordered = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : orderedIds) {
            ChatGroup group = byId.get(id);
            if (group != null && seen.add(id)) {
                ordered.add(group);
            }
        }
        for (ChatGroup group : current) {
            if (seen.add(group.getId())) {
                ordered.add(group);
            }
        }

        int index = 1;
        for (ChatGroup group : ordered) {
            group.setSortOrder(index++);
            chatGroupRepository.save(group);
        }
    }

    @Transactional
    public void deleteGroup(UserAccount user, Long groupId) {
        List<ChatGroup> groups = chatGroupRepository.findAllByUserOrderBySortOrderAscUpdatedAtDesc(user);
        if (groups.size() <= 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Нельзя удалить последнюю группу");
        }

        ChatGroup group = requireGroup(user, groupId);
        ChatGroup fallbackGroup = groups.stream()
                .filter(item -> !item.getId().equals(group.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Не удалось выбрать группу для переноса"));

        if (DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getName())
                && !DEFAULT_GROUP_NAME.equalsIgnoreCase(fallbackGroup.getName())) {
            fallbackGroup.setName(DEFAULT_GROUP_NAME);
            fallbackGroup.touch();
            chatGroupRepository.save(fallbackGroup);
        }

        List<ChatThread> chats = chatThreadRepository.findAllByUserAndGroupOrderBySortOrderAscUpdatedAtDesc(user, group);
        int nextGroupOrder = nextChatSortOrder(user, fallbackGroup);
        for (ChatThread chat : chats) {
            chat.setGroup(fallbackGroup);
            chat.setSortOrder(nextGroupOrder++);
            chatThreadRepository.save(chat);
        }
        chatGroupRepository.delete(group);
        normalizeOrderIndexes(user);
    }

    @Transactional(readOnly = true)
    public List<ChatDto> listChats(UserAccount user, Long groupId) {
        List<ChatThread> chats;
        if (groupId == null) {
            chats = chatThreadRepository.findAllByUserOrderBySortOrderAscUpdatedAtDesc(user);
        } else {
            chats = chatThreadRepository.findAllByUserAndGroupOrderBySortOrderAscUpdatedAtDesc(user, requireGroup(user, groupId));
        }
        return chats.stream().map(this::toChatDto).collect(Collectors.toList());
    }

    @Transactional
    public ChatDto createChat(UserAccount user, ChatRequestDto request) {
        ChatGroup group = resolveGroup(user, request.getGroupId());
        shiftChatsDownForTopInsert(user, group);

        ChatThread chat = new ChatThread();
        chat.setUser(user);
        chat.setTitle(sanitizeName(request.getTitle(), 160, "Новый чат"));
        chat.setModelName(modelCatalogService.getDefaultModel());
        chat.setGroup(group);
        chat.setSortOrder(1);

        ChatThread saved = chatThreadRepository.save(chat);
        return toChatDto(saved);
    }

    @Transactional
    public ChatDto updateChat(UserAccount user, Long chatId, ChatUpdateRequest request) {
        ChatThread chat = requireChat(user, chatId);
        Long oldGroupId = chat.getGroup() == null ? null : chat.getGroup().getId();

        if (request.getTitle() != null) {
            chat.setTitle(sanitizeName(request.getTitle(), 160, chat.getTitle()));
        }

        ChatGroup targetGroup = chat.getGroup();
        Long targetGroupId = oldGroupId;
        if (request.getGroupId() != null) {
            targetGroup = resolveGroup(user, request.getGroupId());
            targetGroupId = targetGroup == null ? null : targetGroup.getId();
        }
        if (!sameId(oldGroupId, targetGroupId)) {
            chat.setGroup(targetGroup);
            chat.setSortOrder(nextChatSortOrder(user, targetGroup));
        }

        if (request.getModel() != null) {
            chat.setModelName(resolveModel(request.getModel()));
        }

        chat.touch();
        ChatThread saved = chatThreadRepository.save(chat);

        if (!sameId(oldGroupId, targetGroupId)) {
            normalizeChatOrderForGroup(user, oldGroupId == null ? null : requireGroup(user, oldGroupId));
            normalizeChatOrderForGroup(user, targetGroup);
        }

        return toChatDto(saved);
    }

    @Transactional
    public void moveChatToGroup(UserAccount user, Long chatId, Long targetGroupId) {
        ChatThread chat = requireChat(user, chatId);
        ChatGroup targetGroup = resolveGroup(user, targetGroupId);
        Long oldGroupId = chat.getGroup() == null ? null : chat.getGroup().getId();
        Long newGroupId = targetGroup == null ? null : targetGroup.getId();

        if (sameId(oldGroupId, newGroupId)) {
            return;
        }

        chat.setGroup(targetGroup);
        chat.setSortOrder(nextChatSortOrder(user, targetGroup));
        chat.touch();
        chatThreadRepository.save(chat);

        normalizeChatOrderForGroup(user, oldGroupId == null ? null : requireGroup(user, oldGroupId));
    }

    @Transactional
    public void reorderChats(UserAccount user, Long groupId, List<Long> orderedChatIds) {
        ChatGroup group = resolveGroup(user, groupId);
        List<ChatThread> current = listChatsInGroup(user, group);
        Map<Long, ChatThread> byId = current.stream().collect(Collectors.toMap(ChatThread::getId, c -> c));

        List<ChatThread> ordered = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : orderedChatIds) {
            ChatThread chat = byId.get(id);
            if (chat != null && seen.add(id)) {
                ordered.add(chat);
            }
        }
        for (ChatThread chat : current) {
            if (seen.add(chat.getId())) {
                ordered.add(chat);
            }
        }

        int index = 1;
        for (ChatThread chat : ordered) {
            chat.setSortOrder(index++);
            chatThreadRepository.save(chat);
        }
    }

    @Transactional
    public void clearChatGroup(UserAccount user, Long chatId) {
        ChatThread chat = requireChat(user, chatId);
        ChatGroup defaultGroup = requireDefaultGroup(user);
        if (chat.getGroup() != null && chat.getGroup().getId().equals(defaultGroup.getId())) {
            return;
        }

        ChatGroup oldGroup = chat.getGroup();
        chat.setGroup(defaultGroup);
        chat.setSortOrder(nextChatSortOrder(user, defaultGroup));
        chat.touch();
        chatThreadRepository.save(chat);

        normalizeChatOrderForGroup(user, oldGroup);
    }

    @Transactional
    public void deleteChat(UserAccount user, Long chatId) {
        ChatThread chat = requireChat(user, chatId);
        bumpChatStreamRevision(chatId);
        ChatGroup oldGroup = chat.getGroup();
        chatMessageRepository.deleteAllByChat(chat);
        chatThreadRepository.delete(chat);
        normalizeChatOrderForGroup(user, oldGroup);
    }

    @Transactional(readOnly = true)
    public void stopStream(UserAccount user, Long chatId) {
        requireChat(user, chatId);
        bumpChatStreamRevision(chatId);
    }

    @Transactional(readOnly = true)
    public ChatDetailDto getChatDetail(UserAccount user, Long chatId) {
        ChatThread chat = requireChat(user, chatId);
        List<ChatMessage> messageEntities = chatMessageRepository.findAllByChatOrderByMessageOrderAsc(chat);
        List<ChatMessageDto> messages = messageEntities
                .stream()
                .map(msg -> new ChatMessageDto(msg.getId(), msg.getRole(), msg.getContent(), msg.getCreatedAt()))
                .collect(Collectors.toList());
        long inputTokens = messageEntities.stream()
                .mapToLong(msg -> safeTokens(msg.getPromptTokens()))
                .sum();
        long outputTokens = messageEntities.stream()
                .mapToLong(msg -> safeTokens(msg.getCompletionTokens()))
                .sum();
        long totalTokens = inputTokens + outputTokens;

        return new ChatDetailDto(toChatDto(chat), messages, inputTokens, outputTokens, totalTokens);
    }

    @Transactional
    public AssistantReplyDto sendMessage(UserAccount user, Long chatId, SendMessageRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Сообщение не может быть пустым");
        }

        ChatThread chat = requireChat(user, chatId);
        String modelToUse = resolveModel(request.getModel());
        boolean shouldAutotitle = shouldAutoTitle(chat);

        long currentCount = chatMessageRepository.countByChat(chat);
        ChatMessage userMessage = new ChatMessage();
        userMessage.setChat(chat);
        userMessage.setRole("user");
        userMessage.setContent(message);
        userMessage.setMessageOrder((int) currentCount + 1);
        chatMessageRepository.save(userMessage);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setChat(chat);
        assistantMessage.setRole("assistant");
        assistantMessage.setMessageOrder((int) currentCount + 2);
        assistantMessage.setModelName(modelToUse);

        try {
            List<Map<String, String>> history = buildOllamaMessages(chat);
            if (codexCliService.isCodexModel(modelToUse)) {
                log.info("Using Codex model for sync chat: chatId={}", chatId);
                assistantMessage.setContent(codexCliService.chat(history));
            } else if (codexApiService.isCodexApiModel(modelToUse)) {
                log.info("Using Codex API model for sync chat: chatId={}", chatId);
                OllamaService.ChatResult assistantResponse = codexApiService.chat(history, request.getTemperature());
                assistantMessage.setContent(assistantResponse == null ? "" : assistantResponse.content());
                if (assistantResponse != null) {
                    assistantMessage.setPromptTokens(assistantResponse.promptTokens());
                    assistantMessage.setCompletionTokens(assistantResponse.completionTokens());
                    assistantMessage.setTotalDurationNs(assistantResponse.totalDurationNs());
                    assistantMessage.setLoadDurationNs(assistantResponse.loadDurationNs());
                    assistantMessage.setPromptEvalDurationNs(assistantResponse.promptEvalDurationNs());
                    assistantMessage.setEvalDurationNs(assistantResponse.evalDurationNs());
                    assistantMessage.setDoneReason(sanitizeDoneReason(assistantResponse.doneReason()));
                }
            } else {
                OllamaService.ChatResult assistantResponse = ollamaService.chat(modelToUse, history, request.getTemperature());
                assistantMessage.setContent(assistantResponse == null ? "" : assistantResponse.content());
                if (assistantResponse != null) {
                    assistantMessage.setPromptTokens(assistantResponse.promptTokens());
                    assistantMessage.setCompletionTokens(assistantResponse.completionTokens());
                    assistantMessage.setTotalDurationNs(assistantResponse.totalDurationNs());
                    assistantMessage.setLoadDurationNs(assistantResponse.loadDurationNs());
                    assistantMessage.setPromptEvalDurationNs(assistantResponse.promptEvalDurationNs());
                    assistantMessage.setEvalDurationNs(assistantResponse.evalDurationNs());
                    assistantMessage.setDoneReason(sanitizeDoneReason(assistantResponse.doneReason()));
                }
            }
        } catch (Exception ex) {
            return new AssistantReplyDto(false, null, modelToUse, ex.getMessage());
        }
        chatMessageRepository.save(assistantMessage);

        chat.setModelName(modelToUse);
        if (shouldAutotitle && currentCount == 0) {
            chat.setTitle(generateShortTitle(modelToUse, message));
        }
        chat.touch();
        chatThreadRepository.save(chat);

        return new AssistantReplyDto(true, assistantMessage.getContent(), modelToUse, null);
    }

    public Flux<StreamChunkDto> streamMessage(UserAccount user, Long chatId, SendMessageRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty()) {
            return Flux.just(StreamChunkDto.error("Сообщение не может быть пустым"), StreamChunkDto.done());
        }

        final String modelToUse;
        try {
            modelToUse = resolveModel(request.getModel());
        } catch (Exception ex) {
            return Flux.just(StreamChunkDto.error(ex.getMessage()), StreamChunkDto.done());
        }

        StreamContext context = transactionTemplate.execute(status -> {
            ChatThread chat = requireChat(user, chatId);
            long currentCount = chatMessageRepository.countByChat(chat);
            boolean shouldAutotitle = shouldAutoTitle(chat);

            ChatMessage userMessage = new ChatMessage();
            userMessage.setChat(chat);
            userMessage.setRole("user");
            userMessage.setContent(message);
            userMessage.setMessageOrder((int) currentCount + 1);
            chatMessageRepository.save(userMessage);

            List<Map<String, String>> history = buildOllamaMessages(chat);
            return new StreamContext(chat.getId(), currentCount, history, shouldAutotitle);
        });

        if (context == null) {
            return Flux.just(StreamChunkDto.error("Не удалось инициализировать поток"), StreamChunkDto.done());
        }

        long streamRevision = registerChatStreamRevision(context.chatId());
        StringBuilder assistantBuffer = new StringBuilder();
        AtomicLong promptTokens = new AtomicLong(0);
        AtomicLong completionTokens = new AtomicLong(0);
        AtomicLong totalDurationNs = new AtomicLong(0);
        AtomicLong loadDurationNs = new AtomicLong(0);
        AtomicLong promptEvalDurationNs = new AtomicLong(0);
        AtomicLong evalDurationNs = new AtomicLong(0);
        AtomicReference<String> doneReason = new AtomicReference<>("");

        if (codexCliService.isCodexModel(modelToUse)) {
            log.info("Using Codex model for stream chat: chatId={}", context.chatId());
            return Mono.fromCallable(() -> codexCliService.chat(context.messages()))
                    .timeout(Duration.ofSeconds(50))
                    .flatMapMany(assistantText -> {
                        String finalAssistantText = assistantText == null ? "" : assistantText;
                        transactionTemplate.executeWithoutResult(status -> {
                            if (!isChatStreamRevisionCurrent(context.chatId(), streamRevision)) {
                                return;
                            }
                            ChatThread chat = chatThreadRepository.findByIdAndUser(context.chatId(), user).orElse(null);
                            if (chat == null) {
                                return;
                            }
                            if (!finalAssistantText.isBlank()) {
                                ChatMessage assistantMessage = new ChatMessage();
                                assistantMessage.setChat(chat);
                                assistantMessage.setRole("assistant");
                                assistantMessage.setContent(finalAssistantText);
                                assistantMessage.setMessageOrder((int) context.currentCount() + 2);
                                assistantMessage.setModelName(modelToUse);
                                assistantMessage.setDoneReason("stop");
                                chatMessageRepository.save(assistantMessage);
                            }

                            chat.setModelName(modelToUse);
                            if (context.shouldAutotitle() && context.currentCount() == 0) {
                                chat.setTitle(generateShortTitle(modelToUse, message));
                            }
                            chat.touch();
                            chatThreadRepository.save(chat);
                        });
                        clearChatStreamRevisionIfCurrent(context.chatId(), streamRevision);

                        if (finalAssistantText.isBlank()) {
                            return Flux.just(StreamChunkDto.done());
                        }
                        return Flux.just(StreamChunkDto.chunk(finalAssistantText), StreamChunkDto.done());
                    })
                    .onErrorResume(ex -> {
                        clearChatStreamRevisionIfCurrent(context.chatId(), streamRevision);
                        return Flux.just(StreamChunkDto.error("Ошибка запроса к Codex: " + ex.getMessage()), StreamChunkDto.done());
                    })
                    .doFinally(signalType -> clearChatStreamRevisionIfCurrent(context.chatId(), streamRevision));
        }

        if (codexApiService.isCodexApiModel(modelToUse)) {
            log.info("Using Codex API model for stream chat: chatId={}", context.chatId());
            return codexApiService.streamChat(context.messages(), request.getTemperature())
                    .takeWhile(chunk -> isChatStreamRevisionCurrent(context.chatId(), streamRevision))
                    .flatMap(chunk -> {
                        if (chunk.promptTokens() > 0) {
                            promptTokens.set(chunk.promptTokens());
                        }
                        if (chunk.completionTokens() > 0) {
                            completionTokens.set(chunk.completionTokens());
                        }
                        String chunkDoneReason = sanitizeDoneReason(chunk.doneReason());
                        if (!chunkDoneReason.isBlank()) {
                            doneReason.set(chunkDoneReason);
                        }
                        if (chunk.content() == null || chunk.content().isEmpty()) {
                            return Flux.empty();
                        }
                        assistantBuffer.append(chunk.content());
                        return Flux.just(StreamChunkDto.chunk(chunk.content()));
                    })
                    .onErrorResume(ex -> Flux.just(StreamChunkDto.error("Ошибка запроса к Codex API: " + ex.getMessage())))
                    .concatWith(Flux.defer(() -> {
                        transactionTemplate.executeWithoutResult(status -> {
                            if (!isChatStreamRevisionCurrent(context.chatId(), streamRevision)) {
                                return;
                            }
                            ChatThread chat = chatThreadRepository.findByIdAndUser(context.chatId(), user).orElse(null);
                            if (chat == null) {
                                return;
                            }
                            String assistantText = assistantBuffer.toString();
                            if (!assistantText.isBlank()) {
                                ChatMessage assistantMessage = new ChatMessage();
                                assistantMessage.setChat(chat);
                                assistantMessage.setRole("assistant");
                                assistantMessage.setContent(assistantText);
                                assistantMessage.setMessageOrder((int) context.currentCount() + 2);
                                assistantMessage.setModelName(modelToUse);
                                assistantMessage.setPromptTokens(promptTokens.get());
                                assistantMessage.setCompletionTokens(completionTokens.get());
                                assistantMessage.setDoneReason(doneReason.get());
                                chatMessageRepository.save(assistantMessage);
                            }

                            chat.setModelName(modelToUse);
                            if (context.shouldAutotitle() && context.currentCount() == 0) {
                                chat.setTitle(generateShortTitle(modelToUse, message));
                            }
                            chat.touch();
                            chatThreadRepository.save(chat);
                        });
                        clearChatStreamRevisionIfCurrent(context.chatId(), streamRevision);
                        return Flux.just(StreamChunkDto.done());
                    }))
                    .doFinally(signalType -> clearChatStreamRevisionIfCurrent(context.chatId(), streamRevision));
        }

        return ollamaService.streamChat(modelToUse, context.messages(), request.getTemperature())
                .takeWhile(chunk -> isChatStreamRevisionCurrent(context.chatId(), streamRevision))
                .flatMap(chunk -> {
                    if (chunk.promptTokens() > 0) {
                        promptTokens.set(chunk.promptTokens());
                    }
                    if (chunk.completionTokens() > 0) {
                        completionTokens.set(chunk.completionTokens());
                    }
                    if (chunk.totalDurationNs() > 0) {
                        totalDurationNs.set(chunk.totalDurationNs());
                    }
                    if (chunk.loadDurationNs() > 0) {
                        loadDurationNs.set(chunk.loadDurationNs());
                    }
                    if (chunk.promptEvalDurationNs() > 0) {
                        promptEvalDurationNs.set(chunk.promptEvalDurationNs());
                    }
                    if (chunk.evalDurationNs() > 0) {
                        evalDurationNs.set(chunk.evalDurationNs());
                    }
                    String chunkDoneReason = sanitizeDoneReason(chunk.doneReason());
                    if (!chunkDoneReason.isBlank()) {
                        doneReason.set(chunkDoneReason);
                    }
                    if (chunk.content() == null || chunk.content().isEmpty()) {
                        return Flux.empty();
                    }
                    assistantBuffer.append(chunk.content());
                    return Flux.just(StreamChunkDto.chunk(chunk.content()));
                })
                .onErrorResume(ex -> Flux.just(StreamChunkDto.error("Ошибка запроса к Ollama: " + ex.getMessage())))
                .concatWith(Flux.defer(() -> {
                    transactionTemplate.executeWithoutResult(status -> {
                        if (!isChatStreamRevisionCurrent(context.chatId(), streamRevision)) {
                            return;
                        }

                        ChatThread chat = chatThreadRepository.findByIdAndUser(context.chatId(), user).orElse(null);
                        if (chat == null) {
                            return;
                        }

                        String assistantText = assistantBuffer.toString();
                        if (!assistantText.isBlank()) {
                            ChatMessage assistantMessage = new ChatMessage();
                            assistantMessage.setChat(chat);
                            assistantMessage.setRole("assistant");
                            assistantMessage.setContent(assistantText);
                            assistantMessage.setMessageOrder((int) context.currentCount() + 2);
                            assistantMessage.setModelName(modelToUse);
                            assistantMessage.setPromptTokens(promptTokens.get());
                            assistantMessage.setCompletionTokens(completionTokens.get());
                            assistantMessage.setTotalDurationNs(totalDurationNs.get());
                            assistantMessage.setLoadDurationNs(loadDurationNs.get());
                            assistantMessage.setPromptEvalDurationNs(promptEvalDurationNs.get());
                            assistantMessage.setEvalDurationNs(evalDurationNs.get());
                            assistantMessage.setDoneReason(doneReason.get());
                            chatMessageRepository.save(assistantMessage);
                        }

                        chat.setModelName(modelToUse);
                        if (context.shouldAutotitle() && context.currentCount() == 0) {
                            chat.setTitle(generateShortTitle(modelToUse, message));
                        }
                        chat.touch();
                        chatThreadRepository.save(chat);
                    });
                    clearChatStreamRevisionIfCurrent(context.chatId(), streamRevision);
                    return Flux.just(StreamChunkDto.done());
                }))
                .doFinally(signalType -> clearChatStreamRevisionIfCurrent(context.chatId(), streamRevision));
    }

    public Flux<StreamChunkDto> streamPrivateMessage(PrivateChatRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty()) {
            return Flux.just(StreamChunkDto.error("Сообщение не может быть пустым"), StreamChunkDto.done());
        }

        final String modelToUse;
        try {
            modelToUse = resolveModel(request.getModel());
        } catch (Exception ex) {
            return Flux.just(StreamChunkDto.error(ex.getMessage()), StreamChunkDto.done());
        }

        List<Map<String, String>> history = new ArrayList<>();
        if (request.getHistory() != null) {
            for (PrivateHistoryMessageDto msg : request.getHistory()) {
                if (msg == null) {
                    continue;
                }
                String role = msg.getRole() == null ? "" : msg.getRole().trim().toLowerCase(Locale.ROOT);
                String content = msg.getContent() == null ? "" : msg.getContent().trim();
                if (content.isEmpty()) {
                    continue;
                }
                if (!role.equals("user") && !role.equals("assistant")) {
                    continue;
                }
                Map<String, String> entry = new HashMap<>();
                entry.put("role", role);
                entry.put("content", content);
                history.add(entry);
            }
        }

        Map<String, String> currentUserMessage = new HashMap<>();
        currentUserMessage.put("role", "user");
        currentUserMessage.put("content", message);
        history.add(currentUserMessage);

        if (codexCliService.isCodexModel(modelToUse)) {
            log.info("Using Codex model for private stream chat");
            try {
                String assistantText = codexCliService.chat(history);
                if (assistantText == null || assistantText.isBlank()) {
                    return Flux.just(StreamChunkDto.done());
                }
                return Flux.just(StreamChunkDto.chunk(assistantText), StreamChunkDto.done());
            } catch (Exception ex) {
                return Flux.just(StreamChunkDto.error("Ошибка запроса к Codex: " + ex.getMessage()), StreamChunkDto.done());
            }
        }

        if (codexApiService.isCodexApiModel(modelToUse)) {
            log.info("Using Codex API model for private stream chat");
            return codexApiService.streamChat(history, request.getTemperature())
                    .flatMap(chunk -> {
                        if (chunk.content() == null || chunk.content().isEmpty()) {
                            return Flux.empty();
                        }
                        return Flux.just(StreamChunkDto.chunk(chunk.content()));
                    })
                    .onErrorResume(ex -> Flux.just(StreamChunkDto.error("Ошибка запроса к Codex API: " + ex.getMessage())))
                    .concatWith(Flux.just(StreamChunkDto.done()));
        }

        return ollamaService.streamChat(modelToUse, history, request.getTemperature())
                .flatMap(chunk -> {
                    if (chunk.content() == null || chunk.content().isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.just(StreamChunkDto.chunk(chunk.content()));
                })
                .onErrorResume(ex -> Flux.just(StreamChunkDto.error("Ошибка запроса к Ollama: " + ex.getMessage())))
                .concatWith(Flux.just(StreamChunkDto.done()));
    }

    public String resolveModel(String requestedModel) {
        try {
            return modelCatalogService.resolveModel(requestedModel);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ModelUsageResponseDto loadModelUsage(UserAccount user) {
        List<ChatMessage> assistantMessages = chatMessageRepository.findAllByChatUserAndRoleOrderByCreatedAtAsc(user, "assistant");
        Map<String, MutableUsageStats> byModel = new HashMap<>();
        MutableUsageStats overall = new MutableUsageStats();

        for (ChatMessage msg : assistantMessages) {
            String model = normalizeModelName(msg.getModelName());
            MutableUsageStats modelStats = byModel.computeIfAbsent(model, key -> new MutableUsageStats());
            accumulateUsage(modelStats, msg);
            accumulateUsage(overall, msg);
        }

        List<ModelUsageStatsDto> models = byModel.entrySet().stream()
                .map(entry -> toUsageStatsDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(ModelUsageStatsDto::getTotalTokens).reversed()
                        .thenComparingLong(ModelUsageStatsDto::getReplies).reversed()
                        .thenComparing(ModelUsageStatsDto::getModel))
                .collect(Collectors.toList());

        return new ModelUsageResponseDto(toUsageSummaryDto(overall), models);
    }

    private List<Map<String, String>> buildOllamaMessages(ChatThread chat) {
        List<ChatMessage> history = chatMessageRepository.findAllByChatOrderByMessageOrderAsc(chat);
        List<Map<String, String>> ollamaMessages = new ArrayList<>();
        for (ChatMessage msg : history) {
            Map<String, String> item = new HashMap<>();
            item.put("role", msg.getRole());
            item.put("content", msg.getContent());
            ollamaMessages.add(item);
        }
        return ollamaMessages;
    }

    private int nextGroupSortOrder(UserAccount user) {
        return chatGroupRepository.findTopByUserOrderBySortOrderDesc(user)
                .map(group -> (group.getSortOrder() == null ? 0 : group.getSortOrder()) + 1)
                .orElse(1);
    }

    private int nextChatSortOrder(UserAccount user, ChatGroup group) {
        Optional<ChatThread> top = group == null
                ? chatThreadRepository.findTopByUserAndGroupIsNullOrderBySortOrderDesc(user)
                : chatThreadRepository.findTopByUserAndGroupOrderBySortOrderDesc(user, group);

        return top.map(chat -> (chat.getSortOrder() == null ? 0 : chat.getSortOrder()) + 1).orElse(1);
    }

    private void shiftChatsDownForTopInsert(UserAccount user, ChatGroup group) {
        List<ChatThread> chats = listChatsInGroup(user, group);
        for (ChatThread chat : chats) {
            int current = chat.getSortOrder() == null ? 0 : chat.getSortOrder();
            chat.setSortOrder(current + 1);
            chatThreadRepository.save(chat);
        }
    }

    private void normalizeChatOrderForGroup(UserAccount user, ChatGroup group) {
        List<ChatThread> chats = listChatsInGroup(user, group);
        int index = 1;
        for (ChatThread chat : chats) {
            if (chat.getSortOrder() == null || chat.getSortOrder() != index) {
                chat.setSortOrder(index);
                chatThreadRepository.save(chat);
            }
            index++;
        }
    }

    private List<ChatThread> listChatsInGroup(UserAccount user, ChatGroup group) {
        if (group == null) {
            return chatThreadRepository.findAllByUserAndGroupIsNullOrderBySortOrderAscUpdatedAtDesc(user);
        }
        return chatThreadRepository.findAllByUserAndGroupOrderBySortOrderAscUpdatedAtDesc(user, group);
    }

    private boolean sameId(Long left, Long right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private ChatGroup resolveGroup(UserAccount user, Long groupId) {
        if (groupId == null) {
            return requireDefaultGroup(user);
        }
        return requireGroup(user, groupId);
    }

    private ChatGroup requireDefaultGroup(UserAccount user) {
        List<ChatGroup> groups = chatGroupRepository.findAllByUserOrderBySortOrderAscUpdatedAtDesc(user);
        if (groups.isEmpty()) {
            ChatGroup group = new ChatGroup();
            group.setUser(user);
            group.setName(DEFAULT_GROUP_NAME);
            group.setSortOrder(1);
            return chatGroupRepository.save(group);
        }

        for (ChatGroup group : groups) {
            if (DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getName())) {
                return group;
            }
        }

        ChatGroup first = groups.get(0);
        first.setName(DEFAULT_GROUP_NAME);
        first.touch();
        return chatGroupRepository.save(first);
    }

    private void migrateUngroupedChatsToDefault(UserAccount user, ChatGroup defaultGroup) {
        List<ChatThread> ungrouped = chatThreadRepository.findAllByUserAndGroupIsNullOrderBySortOrderAscUpdatedAtDesc(user);
        if (ungrouped.isEmpty()) {
            return;
        }
        int nextOrder = nextChatSortOrder(user, defaultGroup);
        for (ChatThread chat : ungrouped) {
            chat.setGroup(defaultGroup);
            chat.setSortOrder(nextOrder++);
            chat.touch();
            chatThreadRepository.save(chat);
        }
    }

    private ChatGroup requireGroup(UserAccount user, Long groupId) {
        return chatGroupRepository.findByIdAndUser(groupId, user)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Группа не найдена"));
    }

    private ChatThread requireChat(UserAccount user, Long chatId) {
        return chatThreadRepository.findByIdAndUser(chatId, user)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Чат не найден"));
    }

    private String sanitizeName(String input, int maxLength, String fallback) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            value = fallback;
        }
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
        }
        return value;
    }

    private String loadThemePreference(UserAccount user) {
        String value = loadPreference(user, PREF_THEME);
        if (THEME_LIGHT.equals(value)) {
            return THEME_LIGHT;
        }
        return THEME_DARK;
    }

    private String loadModelPreference(UserAccount user) {
        String selected = loadPreference(user, PREF_SELECTED_MODEL);
        if (selected == null || selected.isBlank()) {
            return modelCatalogService.getDefaultModel();
        }
        return selected;
    }

    private String loadAnswerNavSidePreference(UserAccount user) {
        String side = loadPreference(user, PREF_ANSWER_NAV_SIDE);
        return ANSWER_NAV_SIDE_LEFT.equals(side) ? ANSWER_NAV_SIDE_LEFT : ANSWER_NAV_SIDE_RIGHT;
    }

    private int loadIntPreference(UserAccount user, String key, int min, int max, int fallback) {
        String value = loadPreference(user, key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return normalizeInt(Integer.parseInt(value), min, max, fallback);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int normalizeInt(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        int bounded = value;
        if (bounded < min) bounded = min;
        if (bounded > max) bounded = max;
        return bounded;
    }

    private String normalizeTheme(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return THEME_DARK.equals(normalized) ? THEME_DARK : THEME_LIGHT;
    }

    private String normalizeSelectedModel(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return modelCatalogService.getDefaultModel();
        }
        return normalized;
    }

    private String normalizeAnswerNavSide(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return ANSWER_NAV_SIDE_LEFT.equals(normalized) ? ANSWER_NAV_SIDE_LEFT : ANSWER_NAV_SIDE_RIGHT;
    }

    private String normalizeModelName(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String sanitizeDoneReason(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private String loadPreference(UserAccount user, String key) {
        return userPreferenceRepository.findByUserAndKey(user, key)
                .map(UserPreference::getValue)
                .map(item -> item == null ? null : item.trim())
                .orElse(null);
    }

    private void savePreference(UserAccount user, String key, String value) {
        UserPreference pref = userPreferenceRepository.findByUserAndKey(user, key)
                .orElseGet(() -> {
                    UserPreference item = new UserPreference();
                    item.setUser(user);
                    item.setKey(key);
                    return item;
                });
        pref.setValue(value);
        userPreferenceRepository.save(pref);
    }

    private ChatDto toChatDto(ChatThread chat) {
        Long groupId = chat.getGroup() != null ? chat.getGroup().getId() : null;
        String groupName = chat.getGroup() != null ? chat.getGroup().getName() : null;
        return new ChatDto(
                chat.getId(),
                chat.getTitle(),
                chat.getModelName(),
                groupId,
                groupName,
                chat.getUpdatedAt()
        );
    }

    private boolean shouldAutoTitle(ChatThread chat) {
        if (chat == null) {
            return false;
        }
        String title = chat.getTitle() == null ? "" : chat.getTitle().trim();
        return title.isEmpty() || "Новый чат".equalsIgnoreCase(title);
    }

    private String generateShortTitle(String modelToUse, String userMessage) {
        String fallback = fallbackTitle(userMessage);
        if (codexCliService.isCodexModel(modelToUse) || codexApiService.isCodexApiModel(modelToUse)) {
            return fallback;
        }
        try {
            String prompt = "Сгенерируй короткое название чата на русском по сообщению пользователя. "
                    + "Требования: 2-5 слов, без кавычек, без точки в конце, только итоговое название.\n"
                    + "Сообщение:\n" + userMessage;

            Map<String, String> msg = new HashMap<>();
            msg.put("role", "user");
            msg.put("content", prompt);
            String raw = ollamaService.chat(modelToUse, List.of(msg), 0.2).content();
            String normalized = normalizeTitle(raw);
            return normalized.isBlank() ? fallback : normalized;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String fallbackTitle(String userMessage) {
        String raw = userMessage == null ? "" : userMessage.trim().replaceAll("\\s+", " ");
        if (raw.isEmpty()) {
            return "Новый чат";
        }
        String[] words = raw.split(" ");
        StringBuilder title = new StringBuilder();
        int limit = Math.min(words.length, 5);
        for (int i = 0; i < limit; i++) {
            if (title.length() > 0) {
                title.append(' ');
            }
            title.append(words[i]);
        }
        String out = title.toString();
        if (out.length() > 80) {
            out = out.substring(0, 80).trim();
        }
        return out.isEmpty() ? "Новый чат" : out;
    }

    private String normalizeTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String oneLine = raw.replace('\n', ' ').replace('\r', ' ').trim();
        oneLine = oneLine.replaceAll("^\"+|\"+$", "").trim();
        oneLine = oneLine.replaceAll("\\s+", " ");
        if (oneLine.length() > 80) {
            oneLine = oneLine.substring(0, 80).trim();
        }
        return oneLine;
    }

    private long registerChatStreamRevision(Long chatId) {
        return chatStreamRevisions.computeIfAbsent(chatId, key -> new AtomicLong(0)).incrementAndGet();
    }

    private void bumpChatStreamRevision(Long chatId) {
        chatStreamRevisions.computeIfAbsent(chatId, key -> new AtomicLong(0)).incrementAndGet();
    }

    private boolean isChatStreamRevisionCurrent(Long chatId, long expectedRevision) {
        AtomicLong counter = chatStreamRevisions.get(chatId);
        return counter != null && counter.get() == expectedRevision;
    }

    private void clearChatStreamRevisionIfCurrent(Long chatId, long expectedRevision) {
        chatStreamRevisions.computeIfPresent(chatId, (id, counter) -> counter.get() == expectedRevision ? null : counter);
    }

    private long safeTokens(Long value) {
        return value == null || value < 0 ? 0 : value;
    }

    private long safeNs(Long value) {
        return value == null || value < 0 ? 0 : value;
    }

    private void accumulateUsage(MutableUsageStats stats, ChatMessage msg) {
        if (stats == null || msg == null) {
            return;
        }
        stats.replies++;
        stats.promptTokens += safeTokens(msg.getPromptTokens());
        stats.completionTokens += safeTokens(msg.getCompletionTokens());
        stats.totalDurationNs += safeNs(msg.getTotalDurationNs());
        stats.loadDurationNs += safeNs(msg.getLoadDurationNs());
        stats.promptEvalDurationNs += safeNs(msg.getPromptEvalDurationNs());
        stats.evalDurationNs += safeNs(msg.getEvalDurationNs());

        String reason = sanitizeDoneReason(msg.getDoneReason());
        if (reason.isBlank()) {
            reason = "unknown";
        }
        stats.doneReasons.merge(reason, 1L, Long::sum);

        if (msg.getCreatedAt() != null) {
            if (stats.firstUsedAt == null || msg.getCreatedAt().isBefore(stats.firstUsedAt)) {
                stats.firstUsedAt = msg.getCreatedAt();
            }
            if (stats.lastUsedAt == null || msg.getCreatedAt().isAfter(stats.lastUsedAt)) {
                stats.lastUsedAt = msg.getCreatedAt();
            }
        }
    }

    private ModelUsageStatsDto toUsageStatsDto(String model, MutableUsageStats stats) {
        long totalTokens = stats.promptTokens + stats.completionTokens;
        long replies = stats.replies;
        return new ModelUsageStatsDto(
                model,
                replies,
                stats.promptTokens,
                stats.completionTokens,
                totalTokens,
                stats.totalDurationNs,
                stats.loadDurationNs,
                stats.promptEvalDurationNs,
                stats.evalDurationNs,
                avg(stats.promptTokens, replies),
                avg(stats.completionTokens, replies),
                avg(totalTokens, replies),
                nsToMs(avgNs(stats.totalDurationNs, replies)),
                nsToMs(avgNs(stats.loadDurationNs, replies)),
                nsToMs(avgNs(stats.promptEvalDurationNs, replies)),
                nsToMs(avgNs(stats.evalDurationNs, replies)),
                tokensPerSecond(stats.completionTokens, stats.evalDurationNs),
                stats.firstUsedAt,
                stats.lastUsedAt,
                copyDoneReasons(stats.doneReasons)
        );
    }

    private UsageSummaryDto toUsageSummaryDto(MutableUsageStats stats) {
        long totalTokens = stats.promptTokens + stats.completionTokens;
        return new UsageSummaryDto(
                stats.replies,
                stats.promptTokens,
                stats.completionTokens,
                totalTokens,
                stats.totalDurationNs,
                stats.loadDurationNs,
                stats.promptEvalDurationNs,
                stats.evalDurationNs,
                avg(totalTokens, stats.replies),
                nsToMs(avgNs(stats.totalDurationNs, stats.replies)),
                tokensPerSecond(stats.completionTokens, stats.evalDurationNs),
                copyDoneReasons(stats.doneReasons)
        );
    }

    private Map<String, Long> copyDoneReasons(Map<String, Long> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return source.entrySet().stream()
                .sorted((left, right) -> {
                    int byCount = Long.compare(right.getValue(), left.getValue());
                    if (byCount != 0) {
                        return byCount;
                    }
                    return left.getKey().compareTo(right.getKey());
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private double avg(long total, long count) {
        if (count <= 0) {
            return 0;
        }
        return (double) total / (double) count;
    }

    private double avgNs(long totalNs, long count) {
        if (count <= 0) {
            return 0;
        }
        return (double) totalNs / (double) count;
    }

    private double nsToMs(double valueNs) {
        return valueNs / 1_000_000.0;
    }

    private double tokensPerSecond(long tokens, long durationNs) {
        if (tokens <= 0 || durationNs <= 0) {
            return 0;
        }
        return tokens / (durationNs / 1_000_000_000.0);
    }

    private static final class MutableUsageStats {
        private long replies = 0;
        private long promptTokens = 0;
        private long completionTokens = 0;
        private long totalDurationNs = 0;
        private long loadDurationNs = 0;
        private long promptEvalDurationNs = 0;
        private long evalDurationNs = 0;
        private java.time.LocalDateTime firstUsedAt = null;
        private java.time.LocalDateTime lastUsedAt = null;
        private final Map<String, Long> doneReasons = new HashMap<>();
    }

    private record StreamContext(Long chatId, long currentCount, List<Map<String, String>> messages, boolean shouldAutotitle) {
    }
}
