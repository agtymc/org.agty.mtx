package org.agty.mtx.dto;

public class AssistantReplyDto {
    private boolean success;
    private String response;
    private String model;
    private String error;

    public AssistantReplyDto() {
    }

    public AssistantReplyDto(boolean success, String response, String model, String error) {
        this.success = success;
        this.response = response;
        this.model = model;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getResponse() {
        return response;
    }

    public String getModel() {
        return model;
    }

    public String getError() {
        return error;
    }
}
