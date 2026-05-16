package org.agty.mtx.dto;

import java.util.Map;

public class UsageSummaryDto {
    private long replies;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long totalDurationNs;
    private long loadDurationNs;
    private long promptEvalDurationNs;
    private long evalDurationNs;
    private double avgTotalTokensPerReply;
    private double avgTotalDurationMsPerReply;
    private double outputTokensPerSecond;
    private Map<String, Long> doneReasons;

    public UsageSummaryDto() {
    }

    public UsageSummaryDto(
            long replies,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            long totalDurationNs,
            long loadDurationNs,
            long promptEvalDurationNs,
            long evalDurationNs,
            double avgTotalTokensPerReply,
            double avgTotalDurationMsPerReply,
            double outputTokensPerSecond,
            Map<String, Long> doneReasons
    ) {
        this.replies = replies;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.totalDurationNs = totalDurationNs;
        this.loadDurationNs = loadDurationNs;
        this.promptEvalDurationNs = promptEvalDurationNs;
        this.evalDurationNs = evalDurationNs;
        this.avgTotalTokensPerReply = avgTotalTokensPerReply;
        this.avgTotalDurationMsPerReply = avgTotalDurationMsPerReply;
        this.outputTokensPerSecond = outputTokensPerSecond;
        this.doneReasons = doneReasons;
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

    public double getAvgTotalTokensPerReply() {
        return avgTotalTokensPerReply;
    }

    public double getAvgTotalDurationMsPerReply() {
        return avgTotalDurationMsPerReply;
    }

    public double getOutputTokensPerSecond() {
        return outputTokensPerSecond;
    }

    public Map<String, Long> getDoneReasons() {
        return doneReasons;
    }
}
