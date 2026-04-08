package com.ye.decision.service;

import com.ye.decision.domain.dto.NotificationMessage;

public interface NotificationService {
    void send(NotificationMessage message);
}
