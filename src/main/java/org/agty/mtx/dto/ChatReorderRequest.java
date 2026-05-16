package org.agty.mtx.dto;

import java.util.ArrayList;
import java.util.List;

public class ChatReorderRequest {
    private Long groupId;
    private List<Long> chatIds = new ArrayList<>();

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public List<Long> getChatIds() {
        return chatIds;
    }

    public void setChatIds(List<Long> chatIds) {
        this.chatIds = chatIds;
    }
}
