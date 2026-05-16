package org.agty.mtx.dto;

import java.time.LocalDateTime;

public class ChatDto {
    private Long id;
    private String title;
    private String model;
    private Long groupId;
    private String groupName;
    private LocalDateTime updatedAt;

    public ChatDto() {
    }

    public ChatDto(Long id, String title, String model, Long groupId, String groupName, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.model = model;
        this.groupId = groupId;
        this.groupName = groupName;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getModel() {
        return model;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
