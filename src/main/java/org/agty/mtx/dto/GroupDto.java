package org.agty.mtx.dto;

import java.time.LocalDateTime;

public class GroupDto {
    private Long id;
    private String name;
    private Long chatCount;
    private LocalDateTime updatedAt;

    public GroupDto() {
    }

    public GroupDto(Long id, String name, Long chatCount, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.chatCount = chatCount;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getChatCount() {
        return chatCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
