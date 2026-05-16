package org.agty.mtx.dto;

import java.util.List;

public class PrivateChatRequest {
    private String message;
    private String model;
    private Double temperature;
    private List<PrivateHistoryMessageDto> history;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public List<PrivateHistoryMessageDto> getHistory() {
        return history;
    }

    public void setHistory(List<PrivateHistoryMessageDto> history) {
        this.history = history;
    }
}
