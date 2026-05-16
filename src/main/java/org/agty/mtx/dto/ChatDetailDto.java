package org.agty.mtx.dto;

import java.util.List;

public class ChatDetailDto {
    private ChatDto chat;
    private List<ChatMessageDto> messages;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;

    public ChatDetailDto() {
    }

    public ChatDetailDto(
            ChatDto chat,
            List<ChatMessageDto> messages,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens
    ) {
        this.chat = chat;
        this.messages = messages;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
    }

    public ChatDto getChat() {
        return chat;
    }

    public List<ChatMessageDto> getMessages() {
        return messages;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }
}
