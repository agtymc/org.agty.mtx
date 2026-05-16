package org.agty.mtx.dto;

import java.util.List;

public class ModelCatalogDto {
    private String defaultModel;
    private List<String> models;

    public ModelCatalogDto() {
    }

    public ModelCatalogDto(String defaultModel, List<String> models) {
        this.defaultModel = defaultModel;
        this.models = models;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public List<String> getModels() {
        return models;
    }
}
