package com.ye.decision.domain.dto;

import java.time.LocalDateTime;

public class ChatSessionVO {

    private String sessionId;
    private String title;
    private Integer messageCount;
    private LocalDateTime updatedAt;

    public ChatSessionVO() {
    }

    public ChatSessionVO(String sessionId, String title, Integer messageCount, LocalDateTime updatedAt) {
        this.sessionId = sessionId;
        this.title = title;
        this.messageCount = messageCount;
        this.updatedAt = updatedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String sessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public Integer messageCount() {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
