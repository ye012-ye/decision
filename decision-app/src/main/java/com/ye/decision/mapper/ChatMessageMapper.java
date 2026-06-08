package com.ye.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ye.decision.domain.dto.ChatSessionVO;
import com.ye.decision.domain.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author Administrator
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

    @Delete("DELETE FROM chat_message WHERE conversation_id = #{conversationId}")
    void deleteByConversationId(@Param("conversationId") String conversationId);

    @Select("""
        SELECT
            conversation_id AS sessionId,
            NULL AS title,
            COUNT(*) AS messageCount,
            MAX(created_at) AS updatedAt
        FROM chat_message
        GROUP BY conversation_id
        ORDER BY updatedAt DESC
        """)
    List<ChatSessionVO> listSessionSummaries();

    @Select("""
        SELECT id, conversation_id, seq, message_type, content, created_at
        FROM chat_message
        WHERE conversation_id = #{conversationId}
        ORDER BY seq ASC, id ASC
        """)
    List<ChatMessageEntity> selectByConversationId(@Param("conversationId") String conversationId);
}
