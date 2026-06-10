package com.ye.decision.service;

import com.ye.decision.domain.dto.ChatMessageVO;
import com.ye.decision.domain.dto.ChatSessionVO;
import com.ye.decision.domain.entity.ChatMessageEntity;
import com.ye.decision.mapper.ChatMessageMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ChatHistoryService {

    private static final String DEFAULT_TITLE = "New conversation";
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMemory chatMemory;

    public ChatHistoryService(ChatMessageMapper chatMessageMapper, ChatMemory chatMemory) {
        this.chatMessageMapper = chatMessageMapper;
        this.chatMemory = chatMemory;
    }

    public List<ChatSessionVO> listSessions() {
        return chatMessageMapper.listSessionSummaries().stream()
            .map(summary -> new ChatSessionVO(
                summary.sessionId(),
                titleFor(summary.sessionId()),
                summary.messageCount(),
                summary.updatedAt()
            ))
            .toList();
    }

    public List<ChatMessageVO> getMessages(String sessionId) {
        return chatMessageMapper.selectByConversationId(sessionId).stream()
            .map(this::toMessage)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        chatMemory.clear(sessionId);
        chatMessageMapper.deleteByConversationId(sessionId);
    }

    private String titleFor(String sessionId) {
        return chatMessageMapper.selectByConversationId(sessionId).stream()
            .filter(message -> "USER".equalsIgnoreCase(message.getMessageType()))
            .map(ChatMessageEntity::getContent)
            .filter(content -> content != null && !content.isBlank())
            .findFirst()
            .map(String::strip)
            .orElse(DEFAULT_TITLE);
    }

    private ChatMessageVO toMessage(ChatMessageEntity entity) {
        String id = entity.getId() == null
            ? entity.getConversationId() + "-" + entity.getSeq()
            : String.valueOf(entity.getId());
        return new ChatMessageVO(
            id,
            role(entity.getMessageType()),
            entity.getContent(),
            entity.getCreatedAt()
        );
    }

    private static String role(String messageType) {
        if (messageType == null || messageType.isBlank()) {
            return "assistant";
        }
        return messageType.toLowerCase(Locale.ROOT);
    }

}
