package com.ye.decision.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 聊天消息持久化实体，对应 chat_message 表。
 *
 * @author Administrator
 */
@TableName("chat_message")
public class ChatMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private String conversationId;

    /** 消息在对话中的顺序（0-based） */
    @TableField("seq")
    private Integer seq;

    @TableField("message_type")
    private String messageType;

    @TableField("content")
    private String content;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public ChatMessageEntity() {}

    public ChatMessageEntity(String conversationId, Integer seq, String messageType, String content) {
        this.conversationId = conversationId;
        this.seq = seq;
        this.messageType = messageType;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getConversationId() { return conversationId; }
    public Integer getSeq() { return seq; }
    public String getMessageType() { return messageType; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
