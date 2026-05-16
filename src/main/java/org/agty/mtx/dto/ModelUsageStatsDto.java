package org.agty.mtx.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class ModelUsageStatsDto {
    private String model;
    private long replies;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long totalDurationNs;
    private long loadDurationNs;
    private long promptEvalDurationNs;
    private long evalDurationNs;
    private double avgPromptTokensPerReply;
    private double avgCompletionTokensPerReply;
    private double avgTotalTokensPerReply;
    private double avgTotalDurationMsPerReply;
    private double avgLoadDurationMsPerReply;
    private double avgPromptEvalDurationMsPerReply;
    private double avgEvalDurationMsPerReply;
    private double outputTokensPerSecond;
    private LocalDateTime firstUsedAt;
    private LocalDateTime lastUsedAt;
    private Map<String, Long> doneReasons;

    public ModelUsageStatsDto() {
    }

    public ModelUsageStatsDto(
            String model,
            long replies,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            long totalDurationNs,
            long loadDurationNs,
            long promptEvalDurationNs,
            long evalDurationNs,
            double avgPromptTokensPerReply,
            double avgCompletionTokensPerReply,
            double avgTotalTokensPerReply,
            double avgTotalDurationMsPerReply,
            double avgLoadDurationMsPerReply,
            double avgPromptEvalDurationMsPerReply,
            double avgEvalDurationMsPerReply,
            double outputTokensPerSecond,
            LocalDateTime firstUsedAt,
            LocalDateTime lastUsedAt,
            Map<String, Long> doneReasons
    ) {
        this.model = model;
        this.replies = replies;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.totalDurationNs = totalDurationNs;
        this.loadDurationNs = loadDurationNs;
        this.promptEvalDurationNs = promptEvalDurationNs;
        this.evalDurationNs = evalDurationNs;
        this.avgPromptTokensPerReply = avgPromptTokensPerReply;
        this.avgCompletionTokensPerReply = avgCompletionTokensPerReply;
        this.avgTotalTokensPerReply = avgTotalTokensPerReply;
        this.avgTotalDurationMsPerReply = avgTotalDurationMsPerReply;
        this.avgLoadDurationMsPerReply = avgLoadDurationMsPerReply;
        this.avgPromptEvalDurationMsPerReply = avgPromptEvalDurationMsPerReply;
        this.avgEvalDurationMsPerReply = avgEvalDurationMsPerReply;
        this.outputTokensPerSecond = outputTokensPerSecond;
        this.firstUsedAt = firstUsedAt;
        this.lastUsedAt = lastUsedAt;
        this.doneReasons = doneReasons;
    }

    public String getModel() {
        return model;
    }

    public long getReplies() {
        return replies;
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public long getTotalDurationNs() {
        return totalDurationNs;
    }

    public long getLoadDurationNs() {
        return loadDurationNs;
    }

    public long getPromptEvalDurationNs() {
        return promptEvalDurationNs;
    }

    public long getEvalDurationNs() {
        return evalDurationNs;
    }

    public double getAvgPromptTokensPerReply() {
        return avgPromptTokensPerReply;
    }

    public double getAvgCompletionTokensPerReply() {
        return avgCompletionTokensPerReply;
    }

    public double getAvgTotalTokensPerReply() {
        return avgTotalTokensPerReply;
    }

    public double getAvgTotalDurationMsPerReply() {
        return avgTotalDurationMsPerReply;
    }

    public double getAvgLoadDurationMsPerReply() {
        return avgLoadDurationMsPerReply;
    }

    public double getAvgPromptEvalDurationMsPerReply() {
        return avgPromptEvalDurationMsPerReply;
    }

    public double getAvgEvalDurationMsPerReply() {
        return avgEvalDurationMsPerReply;
    }

    public double getOutputTokensPerSecond() {
        return outputTokensPerSecond;
    }

    public LocalDateTime getFirstUsedAt() {
        return firstUsedAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public Map<String, Long> getDoneReasons() {
        return doneReasons;
    }
}
