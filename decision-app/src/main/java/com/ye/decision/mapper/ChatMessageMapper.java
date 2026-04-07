package com.ye.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ye.decision.domain.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author Administrator
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

    @Delete("DELETE FROM chat_message WHERE conversation_id = #{conversationId}")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}
