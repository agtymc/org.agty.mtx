package org.agty.mtx.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private ChatThread chat;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "message_order", nullable = false)
    private Integer messageOrder;

    @Column(name = "prompt_tokens", columnDefinition = "INTEGER NOT NULL DEFAULT 0")
    private Long promptTokens = 0L;

    @Column(name = "completion_tokens", columnDefinition = "INTEGER NOT NULL DEFAULT 0")
    private Long completionTokens = 0L;

    @Column(name = "model_name", length = 160)
    private String modelName;

    @Column(name = "total_duration_ns", columnDefinition = "INTEGER NOT NULL DEFAULT 0")
    private Long totalDurationNs = 0L;

    @Column(name = "load_duration_ns", columnDefinition = "INTEGER NOT NULL DEFAULT 0")
    private Long loadDurationNs = 0L;

    @Column(name = "prompt_eval_duration_ns", columnDefinition = "INTEGER NOT NULL DEFAULT 0")
    private Long promptEvalDurationNs = 0L;

    @Column(name = "eval_duration_ns", columnDefinition = "INTEGER NOT NULL DEFAULT 0")
    private Long evalDurationNs = 0L;

    @Column(name = "done_reason", length = 80)
    private String doneReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public ChatThread getChat() {
        return chat;
    }

    public void setChat(ChatThread chat) {
        this.chat = chat;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getMessageOrder() {
        return messageOrder;
    }

    public void setMessageOrder(Integer messageOrder) {
        this.messageOrder = messageOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Long promptTokens) {
        this.promptTokens = promptTokens == null ? 0L : promptTokens;
    }

    public Long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Long completionTokens) {
        this.completionTokens = completionTokens == null ? 0L : completionTokens;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Long getTotalDurationNs() {
        return totalDurationNs;
    }

    public void setTotalDurationNs(Long totalDurationNs) {
        this.totalDurationNs = totalDurationNs == null ? 0L : totalDurationNs;
    }

    public Long getLoadDurationNs() {
        return loadDurationNs;
    }

    public void setLoadDurationNs(Long loadDurationNs) {
        this.loadDurationNs = loadDurationNs == null ? 0L : loadDurationNs;
    }

    public Long getPromptEvalDurationNs() {
        return promptEvalDurationNs;
    }

    public void setPromptEvalDurationNs(Long promptEvalDurationNs) {
        this.promptEvalDurationNs = promptEvalDurationNs == null ? 0L : promptEvalDurationNs;
    }

    public Long getEvalDurationNs() {
        return evalDurationNs;
    }

    public void setEvalDurationNs(Long evalDurationNs) {
        this.evalDurationNs = evalDurationNs == null ? 0L : evalDurationNs;
    }

    public String getDoneReason() {
        return doneReason;
    }

    public void setDoneReason(String doneReason) {
        this.doneReason = doneReason;
    }
}
