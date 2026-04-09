package com.ye.decision.domain.dto;

/**
 * @author ye
 */
public record NotificationMessage(
    String channel,
    String recipient,
    String subject,
    String content
) {}
