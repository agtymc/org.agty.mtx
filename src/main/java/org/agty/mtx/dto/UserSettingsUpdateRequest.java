package org.agty.mtx.dto;

public class UserSettingsUpdateRequest {
    private String theme;
    private Integer fontSize;
    private Integer menuFontSize;
    private Integer sidebarWidth;
    private String answerNavSide;
    private String selectedModel;

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public Integer getFontSize() {
        return fontSize;
    }

    public void setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
    }

    public Integer getMenuFontSize() {
        return menuFontSize;
    }

    public void setMenuFontSize(Integer menuFontSize) {
        this.menuFontSize = menuFontSize;
    }

    public Integer getSidebarWidth() {
        return sidebarWidth;
    }

    public void setSidebarWidth(Integer sidebarWidth) {
        this.sidebarWidth = sidebarWidth;
    }

    public String getAnswerNavSide() {
        return answerNavSide;
    }

    public void setAnswerNavSide(String answerNavSide) {
        this.answerNavSide = answerNavSide;
    }

    public String getSelectedModel() {
        return selectedModel;
    }

    public void setSelectedModel(String selectedModel) {
        this.selectedModel = selectedModel;
    }
}
