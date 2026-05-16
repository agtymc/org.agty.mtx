package org.agty.mtx.dto;

import java.util.List;

public class ModelUsageResponseDto {
    private UsageSummaryDto overall;
    private List<ModelUsageStatsDto> models;

    public ModelUsageResponseDto() {
    }

    public ModelUsageResponseDto(UsageSummaryDto overall, List<ModelUsageStatsDto> models) {
        this.overall = overall;
        this.models = models;
    }

    public UsageSummaryDto getOverall() {
        return overall;
    }

    public List<ModelUsageStatsDto> getModels() {
        return models;
    }
}
