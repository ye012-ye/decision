package com.ye.decision.service;

import com.ye.decision.domain.dto.NotificationMessage;

/**
 * @author ye
 */
public interface NotificationService {
    void send(NotificationMessage message);
}
