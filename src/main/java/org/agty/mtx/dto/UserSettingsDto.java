package org.agty.mtx.dto;

public class UserSettingsDto {
    private String theme;
    private Integer fontSize;
    private Integer menuFontSize;
    private Integer sidebarWidth;
    private String answerNavSide;
    private String selectedModel;

    public UserSettingsDto() {
    }

    public UserSettingsDto(
            String theme,
            Integer fontSize,
            Integer menuFontSize,
            Integer sidebarWidth,
            String answerNavSide,
            String selectedModel
    ) {
        this.theme = theme;
        this.fontSize = fontSize;
        this.menuFontSize = menuFontSize;
        this.sidebarWidth = sidebarWidth;
        this.answerNavSide = answerNavSide;
        this.selectedModel = selectedModel;
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
