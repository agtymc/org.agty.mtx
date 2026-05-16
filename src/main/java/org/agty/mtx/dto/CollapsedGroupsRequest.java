package org.agty.mtx.dto;

import java.util.List;

public class CollapsedGroupsRequest {
    private List<Long> groupIds;

    public List<Long> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(List<Long> groupIds) {
        this.groupIds = groupIds;
    }
}
