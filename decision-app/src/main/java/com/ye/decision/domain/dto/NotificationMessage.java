package com.ye.decision.domain.dto;

public record NotificationMessage(
    String channel,
    String recipient,
    String subject,
    String content
) {}
