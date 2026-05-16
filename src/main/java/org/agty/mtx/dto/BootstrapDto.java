package org.agty.mtx.dto;

import java.util.List;

public class BootstrapDto {
    private String username;
    private String displayName;
    private String email;
    private String defaultModel;
    private List<String> models;
    private List<Long> collapsedGroupIds;
    private String theme;
    private Integer fontSize;
    private Integer menuFontSize;
    private Integer sidebarWidth;
    private String answerNavSide;
    private String selectedModel;

    public BootstrapDto() {
    }

    public BootstrapDto(
            String username,
            String displayName,
            String email,
            String defaultModel,
            List<String> models,
            List<Long> collapsedGroupIds,
            String theme,
            Integer fontSize,
            Integer menuFontSize,
            Integer sidebarWidth,
            String answerNavSide,
            String selectedModel
    ) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.defaultModel = defaultModel;
        this.models = models;
        this.collapsedGroupIds = collapsedGroupIds;
        this.theme = theme;
        this.fontSize = fontSize;
        this.menuFontSize = menuFontSize;
        this.sidebarWidth = sidebarWidth;
        this.answerNavSide = answerNavSide;
        this.selectedModel = selectedModel;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public List<String> getModels() {
        return models;
    }

    public List<Long> getCollapsedGroupIds() {
        return collapsedGroupIds;
    }

    public String getTheme() {
        return theme;
    }

    public Integer getFontSize() {
        return fontSize;
    }

    public Integer getMenuFontSize() {
        return menuFontSize;
    }

    public Integer getSidebarWidth() {
        return sidebarWidth;
    }

    public String getAnswerNavSide() {
        return answerNavSide;
    }

    public String getSelectedModel() {
        return selectedModel;
    }
}
