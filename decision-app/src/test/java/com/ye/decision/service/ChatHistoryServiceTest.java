package com.ye.decision.service;

import com.ye.decision.domain.dto.ChatMessageVO;
import com.ye.decision.domain.dto.ChatSessionVO;
import com.ye.decision.domain.entity.ChatMessageEntity;
import com.ye.decision.mapper.ChatMessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHistoryServiceTest {

    private final ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
    private final ChatMemory chatMemory = mock(ChatMemory.class);
    private final ChatHistoryService service = new ChatHistoryService(chatMessageMapper, chatMemory);

    @Test
    void listSessions_usesFirstUserMessageAsTitle() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 6, 7, 10, 30);
        when(chatMessageMapper.listSessionSummaries())
            .thenReturn(List.of(new ChatSessionVO("session-1", null, 3, updatedAt)));
        when(chatMessageMapper.selectByConversationId("session-1"))
            .thenReturn(List.of(
                message("session-1", 0, "USER", "help me query an order", updatedAt.minusMinutes(2)),
                message("session-1", 1, "ASSISTANT", "ok", updatedAt.minusMinutes(1))
            ));

        List<ChatSessionVO> sessions = service.listSessions();

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).sessionId()).isEqualTo("session-1");
        assertThat(sessions.get(0).title()).isEqualTo("help me query an order");
        assertThat(sessions.get(0).messageCount()).isEqualTo(3);
        assertThat(sessions.get(0).updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void getMessages_returnsMessagesInConversationOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 7, 11, 0);
        when(chatMessageMapper.selectByConversationId("session-1"))
            .thenReturn(List.of(
                message("session-1", 0, "USER", "hello", now),
                message("session-1", 1, "ASSISTANT", "hi", now.plusSeconds(1))
            ));

        List<ChatMessageVO> messages = service.getMessages("session-1");

        assertThat(messages).extracting(ChatMessageVO::role).containsExactly("user", "assistant");
        assertThat(messages).extracting(ChatMessageVO::content).containsExactly("hello", "hi");
    }

    @Test
    void deleteSession_clearsMemoryAndPersistentMessages() {
        service.deleteSession("session-1");

        verify(chatMemory).clear("session-1");
        verify(chatMessageMapper).deleteByConversationId("session-1");
    }

    private static ChatMessageEntity message(String conversationId,
                                             int seq,
                                             String type,
                                             String content,
                                             LocalDateTime createdAt) {
        ChatMessageEntity entity = new ChatMessageEntity(conversationId, seq, type, content);
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
