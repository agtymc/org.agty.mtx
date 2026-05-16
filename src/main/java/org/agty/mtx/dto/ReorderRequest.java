package org.agty.mtx.dto;

import java.util.ArrayList;
import java.util.List;

public class ReorderRequest {
    private List<Long> ids = new ArrayList<>();

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
